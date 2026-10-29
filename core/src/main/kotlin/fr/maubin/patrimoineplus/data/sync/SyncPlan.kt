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
		Seules les données locales ont changé : les envoyer à l'autre côté.
		file : données à envoyer (historique complété de l'envoi)
	*/
	data class Export(val file: BackupFile) : SyncPlan

	/*
		Seules les données de l'autre côté ont changé : les adopter.
		file : données à adopter
	*/
	data class Import(val file: BackupFile) : SyncPlan

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
	Décide d'une synchronisation : compare les empreintes (voir decideSync) ;
	si les deux côtés ont changé, fusionne élément par élément à partir de la
	dernière version commune (voir mergeBackups). Une fusion n'est retenue que
	si son résultat est valide (validateBackup) ; sinon, ou sans version
	commune connue, le choix d'une version entière est demandé.
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
	return when (decideSync(inLastFp, vLocalFp, vRemoteFp, inLocal.accounts.isEmpty()))
	{
		SyncAction.NOTHING  -> SyncPlan.Nothing
		SyncAction.RECORD   -> SyncPlan.Record
		SyncAction.EXPORT   -> SyncPlan.Export(withAuthor(inLocal, inBase, inAuthor, inNow).let { it.copy(devices = joinDevices(it.devices, inRemote?.devices.orEmpty())) })
		SyncAction.IMPORT   -> SyncPlan.Import(inRemote!!.copy(devices = joinDevices(inLocal.devices, inRemote.devices)))
		SyncAction.CONFLICT ->
		{
			val vRemote = inRemote!!															// données de l'autre côté
			if (inBase == null || syncFingerprint(inBase) != inLastFp) return SyncPlan.Whole(vRemote, "dernière version commune inconnue")
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
