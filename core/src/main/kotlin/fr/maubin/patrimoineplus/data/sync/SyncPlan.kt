package fr.maubin.patrimoineplus.data.sync

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.validateBackup

/*
	Ce qu'une synchronisation doit faire, quel que soit le moyen d'échange
	(dossier des données du PC, Wi-Fi).
*/
sealed interface SyncPlan
{
	// Rien n'a changé (ou rien à partager).
	data object Nothing : SyncPlan

	// Les deux côtés sont identiques : mémoriser la version commune.
	data object Record : SyncPlan

	/*
		Seules les données locales ont changé (ou l'autre côté n'a aucun compte,
		ou, sans version commune, les données locales sont les plus récentes) :
		les envoyer à l'autre côté.
		file   : données à envoyer (historique complété de l'envoi)
		newer  : vrai si, sans version commune, les données locales l'emportent
		         parce qu'elles sont les plus récentes (l'autre côté est écrasé)
	*/
	data class Export(
		val file  : BackupFile,
		val newer : Boolean = false
	) : SyncPlan

	/*
		Seules les données de l'autre côté ont changé (ou les données locales
		n'ont aucun compte, ou, sans version commune, celles de l'autre côté sont
		les plus récentes) : les adopter.
		file  : données à adopter
		newer : vrai si, sans version commune, l'autre côté l'emporte parce que
		        ses données sont les plus récentes (les données locales sont écrasées)
	*/
	data class Import(
		val file  : BackupFile,
		val newer : Boolean = false
	) : SyncPlan

	/*
		Les deux côtés ont changé, sans conflit (ou conflits tranchés) : adopter
		la fusion et l'envoyer à l'autre côté.
		file     : contenu fusionné, vérifié
		restored : éléments rétablis, en clair
		merged   : changements repris de l'autre côté
	*/
	data class Merge(
		val file     : BackupFile,
		val restored : List<String>,
		val merged   : Int
	) : SyncPlan

	/*
		Les deux côtés ont changé le même élément : choix par élément à
		demander, puis nouvelle décision avec les choix.
		conflicts : éléments en conflit
		remote    : données de l'autre côté
	*/
	data class Ask(
		val conflicts : List<MergeConflict>,
		val remote    : BackupFile
	) : SyncPlan

	/*
		Les deux côtés ont changé et la fusion est impossible (pas de version
		commune connue, ou fusion incohérente) : choisir une version entière.
		remote : données de l'autre côté
		reason : raison, en clair
	*/
	data class Whole(
		val remote : BackupFile,
		val reason : String
	) : SyncPlan
}

/*
	Instant de la dernière modification connue des données (ms) : la plus
	récente des mises à jour des comptes, catégories, actifs et opérations, et
	des envois de l'historique ; 0 sans aucune. Une suppression seule n'a pas
	de date.
	inFile : données
*/
fun lastModified(inFile: BackupFile): Long =
	maxOf(
		inFile.accounts.maxOfOrNull { it.updatedAt } ?: 0L,
		inFile.categories.maxOfOrNull { it.updatedAt } ?: 0L,
		inFile.assets.maxOfOrNull { it.updatedAt } ?: 0L,
		inFile.transactions.maxOfOrNull { it.updatedAt } ?: 0L,
		inFile.history.maxOfOrNull { it.at } ?: 0L
	)

/*
	Décide d'une synchronisation. Priorités :
	- un côté sans aucun compte n'écrase jamais l'autre : le côté qui a des
	  données les transmet (deux côtés vides : rien) ;
	- sinon, les empreintes sont comparées (voir decideSync) ; si les deux
	  côtés ont changé, fusion élément par élément à partir de la dernière
	  version commune (voir mergeBackups), retenue seulement si son résultat
	  est valide (validateBackup) ;
	- sans version commune connue (premier échange d'un couple), les données
	  modifiées le plus récemment (lastModified) écrasent les autres ; à
	  égalité, ou si la fusion est incohérente, le choix d'une version entière
	  est demandé.
	inLastFp  : empreinte de la dernière version commune ; null avant la première synchronisation
	inBase    : dernière version commune ; null si elle n'est pas connue
	inLocal   : données locales
	inRemote  : données de l'autre côté ; null s'il n'en a pas encore
	inChoices : versions choisies pour les conflits, par clé
	inAuthor  : auteur des changements envoyés (historique) ; null : pas d'historique
	inNow     : instant de l'envoi (ms)
*/
fun planSync(
	inLastFp  : String?,
	inBase    : BackupFile?,
	inLocal   : BackupFile,
	inRemote  : BackupFile?,
	inChoices : Map<String, MergeSide> = emptyMap(),
	inAuthor  : HistoryAuthor?         = null,
	inNow     : Long                   = 0
): SyncPlan
{
	val vLocalFp  = syncFingerprint(inLocal)											// empreinte locale
	val vRemoteFp = inRemote?.let { syncFingerprint(it) }								// empreinte de l'autre côté
	val vExport   = { inNewer: Boolean -> SyncPlan.Export(withAuthor(inLocal, inBase, inAuthor, inNow).let { it.copy(devices = joinDevices(it.devices, inRemote?.devices.orEmpty())) }, inNewer) }	// envoi des données locales
	val vImport   = { inNewer: Boolean -> SyncPlan.Import(inRemote!!.copy(devices = joinDevices(inLocal.devices, inRemote.devices)), inNewer) }	// adoption de l'autre côté
	if (inRemote != null && vLocalFp != vRemoteFp)
	{
		val vLocalEmpty  = inLocal.accounts.isEmpty()										// aucun compte ici
		val vRemoteEmpty = inRemote.accounts.isEmpty()										// aucun compte de l'autre côté
		when
		{
			vLocalEmpty && vRemoteEmpty -> return SyncPlan.Nothing
			vRemoteEmpty                -> return vExport(false)
			vLocalEmpty                 -> return vImport(false)
		}
	}
	return when (decideSync(inLastFp, vLocalFp, vRemoteFp, inLocal.accounts.isEmpty()))
	{
		SyncAction.NOTHING  -> SyncPlan.Nothing
		SyncAction.RECORD   -> SyncPlan.Record
		SyncAction.EXPORT   -> vExport(false)
		SyncAction.IMPORT   -> vImport(false)
		SyncAction.CONFLICT ->
		{
			val vRemote = inRemote!!															// données de l'autre côté
			if (inBase == null || syncFingerprint(inBase) != inLastFp)
			{
				val vMine   = lastModified(inLocal)												// dernière modification ici
				val vTheirs = lastModified(vRemote)												// dernière modification de l'autre côté
				return when
				{
					vMine > vTheirs -> vExport(true)
					vTheirs > vMine -> vImport(true)
					else            -> SyncPlan.Whole(vRemote, "dernière version commune inconnue, modifiées au même instant")
				}
			}
			val vMerge  = mergeBackups(inBase, inLocal, vRemote, inChoices)					// fusion
			when
			{
				vMerge.conflicts.isNotEmpty() -> SyncPlan.Ask(vMerge.conflicts, vRemote)
				else                          ->
				{
					when (val vRead = validateBackup(vMerge.file))
					{
						is BackupRead.Valid   ->
						{
							val vMine = withAuthor(inLocal, inBase, inAuthor, inNow).history				// historique avec les changements de cet appareil
							SyncPlan.Merge(vRead.file.copy(history = joinHistory(vMine, vRead.file.history)), vMerge.restored, vMerge.merged)
						}
						is BackupRead.Invalid -> SyncPlan.Whole(vRemote, "fusion incohérente : ${vRead.message}")
					}
				}
			}
		}
	}
}
