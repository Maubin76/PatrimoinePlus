package fr.maubin.patrimoineplus.data.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.BackupRepository
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import fr.maubin.patrimoineplus.data.repo.PlanningRepository
import fr.maubin.patrimoineplus.data.sync.MergeConflict
import fr.maubin.patrimoineplus.data.sync.MergeSide
import fr.maubin.patrimoineplus.data.sync.SyncPlan
import fr.maubin.patrimoineplus.data.sync.planSync

private const val kDebounceMillis = 3_000L										// délai après une modification avant d'écrire le fichier

/*
	État de la synchronisation, affiché dans les Réglages.
*/
sealed interface SyncStatus
{
	// Aucun dossier choisi.
	data object Off : SyncStatus

	// Synchronisation en cours.
	data object Working : SyncStatus

	/*
		À jour.
		at : instant de la dernière synchronisation réussie (ms) ; null avant la première
	*/
	data class Idle(val at: Long?) : SyncStatus

	/*
		Les deux côtés ont changé : l'utilisateur doit choisir.
		localSummary : contenu du téléphone, résumé
		fileSummary  : contenu du fichier, résumé
	*/
	data class Conflict(
		val localSummary : String,
		val fileSummary  : String
	) : SyncStatus

	/*
		Échec ; rien n'a été modifié.
		message     : raison
		newerFormat : format du fichier s'il vient d'une version plus récente (le PC
		              a été mis à jour, pas le téléphone) ; null sinon
	*/
	data class Failed(
		val message     : String,
		val newerFormat : Int? = null
	) : SyncStatus

	/*
		Les deux côtés ont changé les mêmes éléments : choix par élément ;
		tout le reste est déjà fusionné.
		conflicts : éléments en conflit
	*/
	data class Items(val conflicts: List<MergeConflict>) : SyncStatus
}

/*
	Actions autour d'un import (sauvegarde de sécurité avant, assistant du
	premier lancement marqué comme fait après).
*/
interface SyncHooks
{
	/*
		Avant de remplacer les données locales ; faux si la sauvegarde de
		sécurité a échoué (l'import n'a alors pas lieu).
	*/
	suspend fun beforeImport(): Boolean

	/*
		Après avoir remplacé les données locales.
	*/
	suspend fun afterImport()
}

/*
	Synchronisation par fichier : compare les données du téléphone, le fichier
	d'échange du dossier synchronisé et l'état de la dernière synchronisation
	(empreintes), puis exporte, importe, ou signale un conflit (decideSync).
	Se lance au retour dans l'appli et quelques secondes après chaque
	modification des données.
	inBackup   : instantané et remplacement de la base
	inStore    : mémoire de la synchronisation
	inFolders  : accès au dossier synchronisé
	inHooks    : actions autour d'un import
	inChanges  : signal de modification des données (une valeur à chaque changement)
	inClock    : horloge
*/
class Syncer(
	private val inBackup  : BackupRepository,
	private val inStore   : SyncStore,
	private val inFolders : SyncFolderFactory,
	private val inHooks   : SyncHooks,
	private val inChanges : kotlinx.coroutines.flow.Flow<Any>,
	private val inClock   : Clock
) : DefaultLifecycleObserver
{
	private val fLock    = Mutex()													// une synchronisation à la fois
	private val fScope   = CoroutineScope(SupervisorJob() + Dispatchers.Default)	// portée des synchronisations automatiques
	private val fStatus  = MutableStateFlow<SyncStatus>(SyncStatus.Off)			// état courant
	private var fPending : BackupFile? = null										// contenu du fichier en conflit

	// État de la synchronisation.
	val status: StateFlow<SyncStatus> = fStatus

	/*
		Démarre la synchronisation automatique : à chaque changement de dossier,
		et quelques secondes après chaque modification des données.
	*/
	@OptIn(FlowPreview::class)
	fun start()
	{
		fScope.launch { inStore.folder.distinctUntilChanged().collect { sync() } }
		fScope.launch { inChanges.drop(1).debounce(kDebounceMillis).collect { sync() } }
	}

	/*
		Retour au premier plan : le fichier a peut-être changé ailleurs.
		inOwner : processus
	*/
	override fun onStart(inOwner: LifecycleOwner)
	{
		fScope.launch { sync() }
	}

	/*
		Synchronise maintenant : décide et applique l'action. Un conflit en
		attente n'est pas tranché ici (voir resolve).
	*/
	suspend fun sync(): SyncStatus =
		fLock.withLock {
			val vUri = inStore.folder.first()										// dossier synchronisé
			if (vUri == null)
			{
				fPending = null
				return@withLock SyncStatus.Off.also { fStatus.value = it }
			}
			fStatus.value = SyncStatus.Working
			val vResult = try
			{
				val vFolder = inFolders.open(vUri)									// dossier
				val vBytes  = vFolder.read()										// fichier d'échange
				val vFile   = vBytes?.let { inBytes ->
					when (val vRead = readBackupXlsx(ByteArrayInputStream(inBytes)))
					{
						is BackupRead.Valid   -> vRead.file
						is BackupRead.Invalid -> return@withLock SyncStatus.Failed("Fichier d’échange illisible ou incomplet (${vRead.message}) : rien n’a été modifié.", vRead.newerFormat).also { fStatus.value = it }
					}
				}																	// contenu du fichier
				val vLocal  = inBackup.snapshot()									// données du téléphone
				apply(vFolder, vLocal, vFile, planSync(inStore.fingerprint(), inStore.base(), vLocal, vFile))
			}
			catch (inError: Exception)
			{
				SyncStatus.Failed("Dossier synchronisé inaccessible : ${inError.message ?: "erreur inconnue"}. Choisissez-le à nouveau dans les Réglages.")
			}																		// état obtenu
			fStatus.value = vResult
			vResult
		}

	/*
		Applique une décision de synchronisation.
		inFolder : dossier
		inLocal  : données du téléphone lors de la décision
		inFile   : contenu du fichier ; null s'il est absent
		inPlan   : décision
	*/
	private suspend fun apply(
		inFolder : SyncFolder,
		inLocal  : BackupFile,
		inFile   : BackupFile?,
		inPlan   : SyncPlan
	): SyncStatus =
		when (inPlan)
		{
			SyncPlan.Nothing   -> idle()
			SyncPlan.Record    -> record(syncFingerprint(inLocal), inLocal)
			is SyncPlan.Export -> export(inFolder, inLocal, syncFingerprint(inLocal))
			is SyncPlan.Import -> import(inPlan.file, syncFingerprint(inPlan.file), syncFingerprint(inLocal))
			is SyncPlan.Merge  ->
			{
				val vImported = import(inPlan.file, syncFingerprint(inPlan.file), syncFingerprint(inLocal))	// fusion adoptée sur le téléphone
				if (vImported is SyncStatus.Idle) export(inFolder, inPlan.file, syncFingerprint(inPlan.file)) else vImported
			}
			is SyncPlan.Ask    ->
			{
				fPending = inPlan.remote
				SyncStatus.Items(inPlan.conflicts)
			}
			is SyncPlan.Whole  ->
			{
				fPending = inPlan.remote
				SyncStatus.Conflict(inLocal.contents, inPlan.remote.contents)
			}
		}

	/*
		Tranche les éléments en conflit (choix par élément), puis fusionne. Le
		fichier et le téléphone sont relus : s'ils ont encore changé, la
		décision est reprise (de nouveaux conflits peuvent être présentés).
		inChoices : version gardée pour chaque élément, par clé
	*/
	suspend fun resolveItems(inChoices: Map<String, MergeSide>): SyncStatus =
		fLock.withLock {
			val vUri  = inStore.folder.first() ?: return@withLock SyncStatus.Off	// dossier synchronisé
			val vSeen = fPending															// contenu en conflit, tel que présenté
			fPending = null
			val vResult = try
			{
				val vFolder = inFolders.open(vUri)											// dossier
				val vFile   = vFolder.read()?.let { (readBackupXlsx(ByteArrayInputStream(it)) as? BackupRead.Valid)?.file } ?: vSeen	// contenu actuel du fichier
				val vLocal  = inBackup.snapshot()											// données du téléphone
				apply(vFolder, vLocal, vFile, planSync(inStore.fingerprint(), inStore.base(), vLocal, vFile, inChoices))
			}
			catch (inError: Exception)
			{
				SyncStatus.Failed("Échec de la synchronisation : ${inError.message ?: "erreur inconnue"}.")
			}																		// état obtenu
			fStatus.value = vResult
			vResult
		}

	/*
		Tranche un conflit : garder les données du téléphone (le fichier est
		remplacé) ou celles du fichier (les données du téléphone sont
		remplacées, après une sauvegarde de sécurité).
		inKeepLocal : vrai pour garder le téléphone
	*/
	suspend fun resolve(inKeepLocal: Boolean): SyncStatus =
		fLock.withLock {
			val vUri  = inStore.folder.first() ?: return@withLock SyncStatus.Off	// dossier synchronisé
			val vFile = fPending																// contenu en conflit
			fPending = null
			val vResult = try
			{
				if (inKeepLocal || vFile == null)
				{
					val vLocal = inBackup.snapshot()								// données du téléphone
					export(inFolders.open(vUri), vLocal, syncFingerprint(vLocal))
				}
				else
				{
					import(vFile, syncFingerprint(vFile), null)				// choix explicite : remplacement sans condition
				}
			}
			catch (inError: Exception)
			{
				SyncStatus.Failed("Échec de la synchronisation : ${inError.message ?: "erreur inconnue"}.")
			}																		// état obtenu
			fStatus.value = vResult
			vResult
		}

	/*
		Rien à faire : à jour.
	*/
	private suspend fun idle(): SyncStatus = SyncStatus.Idle(inStore.lastSyncAt.first())

	/*
		Mémorise l'empreinte et le contenu communs (version commune des
		prochaines fusions).
		inFingerprint : empreinte
		inContent     : contenu commun
	*/
	private suspend fun record(
		inFingerprint : String,
		inContent     : BackupFile
	): SyncStatus
	{
		val vNow = inClock.millis()													// instant
		inStore.recordSync(inFingerprint, vNow, inContent)
		return SyncStatus.Idle(vNow)
	}

	/*
		Écrit les données du téléphone dans le fichier d'échange.
		inFolder      : dossier
		inLocal       : données du téléphone
		inFingerprint : leur empreinte
	*/
	private suspend fun export(
		inFolder      : SyncFolder,
		inLocal       : BackupFile,
		inFingerprint : String
	): SyncStatus
	{
		inFolder.write(ByteArrayOutputStream().also { writeBackupXlsx(inLocal, it) }.toByteArray())
		return record(inFingerprint, inLocal)
	}

	/*
		Remplace les données du téléphone par celles du fichier.
		inFile        : contenu du fichier, vérifié
		inFingerprint : son empreinte
		inLocal       : empreinte des données du téléphone lors de la décision ; null : sans contrôle
	*/
	private suspend fun import(
		inFile        : BackupFile,
		inFingerprint : String,
		inLocal       : String?
	): SyncStatus
	{
		if (!inHooks.beforeImport())
		{
			return SyncStatus.Failed("Sauvegarde de sécurité impossible : les données du téléphone n’ont pas été remplacées.")
		}
		if (!inBackup.replaceAll(inFile, inLocal))
		{
			return SyncStatus.Failed("Données modifiées pendant la synchronisation : rien n’a été remplacé, relancez la synchronisation.")
		}
		inHooks.afterImport()
		return record(inFingerprint, inFile)
	}
}

/*
	Signal de modification des données : une valeur à chaque changement des
	comptes, catégories, actifs, opérations, cours ou relevés.
	inAccounts     : comptes
	inCategories   : catégories
	inTransactions : opérations
	inMarket       : actifs, cours et relevés
	inPlanning     : opérations récurrentes et crédits
*/
fun dataChanges(
	inAccounts     : AccountRepository,
	inCategories   : CategoryRepository,
	inTransactions : TransactionRepository,
	inMarket       : MarketRepository,
	inPlanning     : PlanningRepository
): kotlinx.coroutines.flow.Flow<Any> =
	combine(
		combine(
			inAccounts.observeAccounts(), inCategories.observeCategories(), inTransactions.observeTransactions(),
			inMarket.observeAssets(), inMarket.observePrices(), inMarket.observeStatements()
		) { inValues -> inValues.toList() },
		inPlanning.observeRecurrences(),
		inPlanning.observeLoans()
	) { inData, inRecs, inLoans -> listOf(inData, inRecs, inLoans) }
