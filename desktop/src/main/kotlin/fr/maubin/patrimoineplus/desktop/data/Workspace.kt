package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.CategoryDto
import fr.maubin.patrimoineplus.data.backup.kBackupFormat
import fr.maubin.patrimoineplus.data.backup.kBackupVersion
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.demo.buildDemoData
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.data.sync.SyncAction
import fr.maubin.patrimoineplus.data.sync.decideSync
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import fr.maubin.patrimoineplus.data.toDataSet
import fr.maubin.patrimoineplus.domain.model.DefaultCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

private const val kPollMillis  = 2_000L											// intervalle de surveillance du fichier
private const val kFlushMillis = 1_500L											// délai entre une modification et l'écriture

/*
	État de la source des données.
*/
sealed interface LoadStatus
{
	// Pas de source.
	data object Idle : LoadStatus

	// Données de démonstration (modifiables, jamais écrites).
	data object Demo : LoadStatus

	// Dossier choisi, mais le fichier d'échange n'y est pas (encore).
	data object Waiting : LoadStatus

	/*
		À jour avec le fichier d'échange.
		at : instant de la dernière lecture ou écriture (ms)
	*/
	data class Synced(val at: Long) : LoadStatus

	// Modifications faites sur le PC, pas encore écrites dans le fichier.
	data object Pending : LoadStatus

	/*
		Fichier illisible ou incohérent : rien n'est écrit tant qu'il l'est ; les
		dernières données valides restent affichées.
		message : raison
	*/
	data class Invalid(val message: String) : LoadStatus

	/*
		Les données du PC et celles du fichier ont changé toutes les deux :
		l'utilisateur doit choisir.
		localSummary : contenu du PC, résumé
		fileSummary  : contenu du fichier, résumé
	*/
	data class Conflict(
		val localSummary : String,
		val fileSummary  : String
	) : LoadStatus

	/*
		Écriture impossible (dossier en lecture seule, disque plein…).
		message : raison
	*/
	data class WriteFailed(val message: String) : LoadStatus
}

/*
	État de l'espace de travail.
	source : source choisie
	data   : données affichées
	status : état de la source
	today  : date de calcul
*/
data class WorkspaceState(
	val source : Source     = Source.None,
	val data   : DataSet    = DataSet.kEmpty,
	val status : LoadStatus = LoadStatus.Idle,
	val today  : LocalDate
)

/*
	Espace de travail de l'appli PC. Démonstration : données en mémoire,
	modifiables, jamais écrites. Dossier synchronisé : le fichier d'échange est
	lu, puis synchronisé comme sur le téléphone (empreintes de contenu,
	decideSync) : une modification du PC est écrite 1,5 s plus tard (écriture
	atomique), une modification du fichier est relue, un conflit attend le
	choix de l'utilisateur. Un fichier illisible n'est jamais écrasé et
	n'efface jamais les données affichées.
	inClock : horloge
*/
class Workspace(private val inClock: Clock)
{
	private val fState    = MutableStateFlow(WorkspaceState(today = LocalDate.now(inClock)))	// état courant
	private var fFile     : BackupFile = emptyFile()								// données du PC (référence)
	private var fLastFp   : String?    = null										// empreinte de la dernière synchronisation
	private var fStamp    : Pair<Long, Long>? = null								// date et taille du fichier lu
	private var fDirty    = false													// modifications pas encore écrites
	private var fConflict : BackupFile? = null										// contenu du fichier en conflit
	private var fScope    : CoroutineScope? = null									// portée de la surveillance
	private var fWatch    : Job? = null												// surveillance du fichier
	private var fFlush    : Job? = null												// écriture programmée

	// État courant.
	val state: StateFlow<WorkspaceState> = fState

	// Contenu complet des données du PC (pour les modifications).
	val file: BackupFile get() = fFile

	/*
		Ouvre une source.
		inSource : source
		inScope  : portée de la surveillance et des écritures différées ; null : à la main (tests)
	*/
	fun open(
		inSource : Source,
		inScope  : CoroutineScope? = null
	)
	{
		fWatch?.cancel()
		fFlush?.cancel()
		fScope    = inScope
		fWatch    = null
		fStamp    = null
		fLastFp   = null
		fDirty    = false
		fConflict = null
		val vToday = LocalDate.now(inClock)										// date de calcul
		when (inSource)
		{
			Source.None      ->
			{
				fFile = emptyFile()
				fState.value = WorkspaceState(today = vToday)
			}
			Source.Demo      ->
			{
				fFile = demoFile(vToday)
				fState.value = WorkspaceState(Source.Demo, fFile.toDataSet(), LoadStatus.Demo, vToday)
			}
			is Source.Folder ->
			{
				fFile = emptyFile()
				fState.value = WorkspaceState(inSource, fFile.toDataSet(), LoadStatus.Waiting, vToday)
				sync()
				if (inScope != null)
				{
					fWatch = inScope.launch {
						while (isActive)
						{
							delay(kPollMillis)
							sync()
						}
					}
				}
			}
		}
	}

	/*
		Applique une modification aux données du PC. Acceptée : affichée tout de
		suite, écrite dans le fichier 1,5 s plus tard (dossier) ; refusée : rien
		ne change.
		inChange : modification (voir DataEditor)
	*/
	fun edit(inChange: (BackupFile) -> EditResult): EditResult
	{
		val vResult = inChange(fFile)												// résultat
		if (vResult is EditResult.Done)
		{
			fFile  = vResult.file
			fDirty = true
			val vFolder = fState.value.source is Source.Folder						// données synchronisées
			fState.value = fState.value.copy(
				data   = fFile.toDataSet(),
				status = if (vFolder && fState.value.status !is LoadStatus.Conflict) LoadStatus.Pending else fState.value.status
			)
			if (vFolder)
			{
				fFlush?.cancel()
				fFlush = fScope?.launch {
					delay(kFlushMillis)
					sync()
				}
			}
		}
		return vResult
	}

	/*
		Synchronise avec le fichier d'échange si quelque chose a changé (fichier
		modifié ailleurs, ou modifications du PC en attente) ; sans effet hors
		dossier ou pendant un conflit.
	*/
	fun sync()
	{
		val vSource = fState.value.source as? Source.Folder ?: return			// dossier ouvert
		if (fConflict != null) return
		val vPath  = vSource.path.resolve(kSyncFileName)							// fichier d'échange
		val vStamp = stampOf(vPath)													// date et taille
		if (vStamp == fStamp && !fDirty)
		{
			return
		}
		val vOnDisk = if (vStamp == null) null else readFile(vPath) ?: return	// contenu du fichier (null : absent)
		fStamp = vStamp
		val vLocalFp = syncFingerprint(fFile)										// empreinte du PC
		val vFileFp  = vOnDisk?.let { syncFingerprint(it) }						// empreinte du fichier
		when (decideSync(fLastFp, vLocalFp, vFileFp, fFile.accounts.isEmpty()))
		{
			SyncAction.NOTHING  -> settle(if (vOnDisk == null) LoadStatus.Waiting else LoadStatus.Synced(inClock.millis()))
			SyncAction.RECORD   -> record(vLocalFp)
			SyncAction.EXPORT   -> export(vPath, vLocalFp)
			SyncAction.IMPORT   -> adopt(vOnDisk!!, vFileFp!!)
			SyncAction.CONFLICT ->
			{
				fConflict = vOnDisk
				fState.value = fState.value.copy(status = LoadStatus.Conflict(fFile.summary, vOnDisk!!.summary))
			}
		}
	}

	/*
		Tranche un conflit : garder les données du PC (le fichier est réécrit) ou
		celles du fichier (les données du PC sont remplacées).
		inKeepLocal : vrai pour garder le PC
	*/
	fun resolve(inKeepLocal: Boolean)
	{
		val vSource = fState.value.source as? Source.Folder ?: return			// dossier ouvert
		val vFile   = fConflict ?: return											// contenu en conflit
		fConflict = null
		if (inKeepLocal)
		{
			export(vSource.path.resolve(kSyncFileName), syncFingerprint(fFile))
		}
		else
		{
			adopt(vFile, syncFingerprint(vFile))
		}
	}

	/*
		Écrit tout de suite les modifications en attente (fermeture de l'appli).
	*/
	fun flush()
	{
		fFlush?.cancel()
		if (fDirty) sync()
	}

	/*
		Relit le fichier (alias de sync, conservé pour les appels existants).
	*/
	fun reload() = sync()

	/*
		Écrit les données du PC dans le fichier (fichier temporaire puis
		remplacement, pour que Syncthing ne voie jamais un fichier à moitié écrit).
		inPath        : fichier d'échange
		inFingerprint : empreinte des données écrites
	*/
	private fun export(
		inPath        : Path,
		inFingerprint : String
	)
	{
		val vTemp = inPath.resolveSibling(".$kSyncFileName.tmp")					// fichier temporaire
		try
		{
			Files.newOutputStream(vTemp).use { writeBackupXlsx(fFile.copy(exportedAt = Instant.now(inClock).toString()), it) }
			try
			{
				Files.move(vTemp, inPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
			}
			catch (inError: AtomicMoveNotSupportedException)
			{
				Files.move(vTemp, inPath, StandardCopyOption.REPLACE_EXISTING)
			}
			fStamp = stampOf(inPath)
			record(inFingerprint)
		}
		catch (inError: Exception)
		{
			runCatching { Files.deleteIfExists(vTemp) }
			fState.value = fState.value.copy(status = LoadStatus.WriteFailed(inError.message ?: "erreur inconnue"))
		}
	}

	/*
		Adopte le contenu du fichier comme données du PC.
		inFile        : contenu vérifié
		inFingerprint : son empreinte
	*/
	private fun adopt(
		inFile        : BackupFile,
		inFingerprint : String
	)
	{
		fFile = inFile
		fState.value = fState.value.copy(data = inFile.toDataSet(), today = LocalDate.now(inClock))
		record(inFingerprint)
	}

	/*
		Mémorise une synchronisation réussie.
		inFingerprint : empreinte commune
	*/
	private fun record(inFingerprint: String)
	{
		fLastFp = inFingerprint
		settle(LoadStatus.Synced(inClock.millis()))
	}

	/*
		Plus rien en attente : état final.
		inStatus : état
	*/
	private fun settle(inStatus: LoadStatus)
	{
		fDirty = false
		fState.value = fState.value.copy(status = inStatus)
	}

	/*
		Lit et vérifie le fichier ; null (et état « illisible ») s'il est refusé.
		inPath : fichier d'échange
	*/
	private fun readFile(inPath: Path): BackupFile?
	{
		val vRead = runCatching { Files.newInputStream(inPath).use { readBackupXlsx(it) } }
			.getOrElse { BackupRead.Invalid("Lecture impossible : ${it.message}") }	// fichier vérifié
		return when (vRead)
		{
			is BackupRead.Valid   -> vRead.file
			is BackupRead.Invalid ->
			{
				fStamp = stampOf(inPath)
				fState.value = fState.value.copy(status = LoadStatus.Invalid(vRead.message))
				null
			}
		}
	}

	/*
		Date de modification et taille d'un fichier ; null s'il n'existe pas.
		inPath : fichier
	*/
	private fun stampOf(inPath: Path): Pair<Long, Long>? =
		if (!Files.isRegularFile(inPath)) null else runCatching { Files.getLastModifiedTime(inPath).toMillis() to Files.size(inPath) }.getOrNull()

	/*
		Contenu vide (aucune donnée), avec le catalogue de catégories par défaut.
	*/
	private fun emptyFile(): BackupFile =
		BackupFile(kBackupFormat, kBackupVersion, "", emptyList(), defaultCategories(), emptyList(), emptyList(), emptyList(), emptyList())

	/*
		Catalogue de catégories par défaut, pour pouvoir saisir sur un PC sans
		fichier d'échange.
	*/
	private fun defaultCategories(): List<CategoryDto>
	{
		val vNow = inClock.millis()													// horodatage
		return DefaultCatalog.categories().map { CategoryDto(it.id, it.name, it.icon, it.kind.name, it.sortOrder, it.archived, vNow, vNow) }
	}

	/*
		Démonstration sous forme de contenu modifiable.
		inToday : date du jour
	*/
	private fun demoFile(inToday: LocalDate): BackupFile
	{
		val vDemo = buildDemoData(inToday)											// démonstration
		val vNow  = inClock.millis()												// horodatage
		return BackupFile(
			format       = kBackupFormat,
			version      = kBackupVersion,
			exportedAt   = "",
			accounts     = vDemo.accounts.map { fr.maubin.patrimoineplus.data.backup.AccountDto(it.id, it.name, it.type.name, it.institution, it.sharePercent, it.sortOrder, it.archived, vNow, vNow) },
			categories   = vDemo.categories.map { CategoryDto(it.id, it.name, it.icon, it.kind.name, it.sortOrder, it.archived, vNow, vNow) },
			assets       = vDemo.assets.map { fr.maubin.patrimoineplus.data.backup.AssetDto(it.id, it.name, it.isin, it.archived, vNow, vNow, it.ticker) },
			transactions = vDemo.transactions.map { inTx ->
				fr.maubin.patrimoineplus.data.backup.TransactionDto(inTx.id, inTx.date.toString(), inTx.kind.name, inTx.label, inTx.categoryId, inTx.assetId, inTx.notes, vNow, vNow,
					inTx.postings.map { fr.maubin.patrimoineplus.data.backup.PostingDto(it.id, it.accountId, it.amountCents) },
					inTx.trades.map { fr.maubin.patrimoineplus.data.backup.TradeDto(it.id, it.assetId, it.side.name, it.quantity, it.unitPrice, it.feesCents) })
			},
			statements   = vDemo.statements.map { fr.maubin.patrimoineplus.data.backup.StatementDto(it.id, it.accountId, it.date.toString(), it.balanceCents) },
			prices       = vDemo.prices.map { fr.maubin.patrimoineplus.data.backup.PriceDto(it.id, it.assetId, it.date.toString(), it.unitPrice) }
		)
	}
}
