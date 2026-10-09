package fr.maubin.patrimoineplus.data.backup

import java.time.LocalDate

const val kBackupPrefix   = "patrimoineplus-"									// début du nom des sauvegardes
const val kBackupKept     = 3													// sauvegardes conservées
const val kBackupMime     = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"	// type d'un classeur .xlsx
const val kBackupFolder   = "PatrimoinePlus"									// dossier sous Documents

private val kDatedBackup = Regex("""patrimoineplus-\d{4}-\d{2}-\d{2}\.xlsx""")	// nom d'une sauvegarde quotidienne

/*
	Nom du fichier de sauvegarde d'un jour : patrimoineplus-AAAA-MM-JJ.xlsx
	(l'ordre alphabétique est l'ordre chronologique).
	inDate : jour de la sauvegarde
*/
fun backupFileName(inDate: LocalDate): String = "$kBackupPrefix$inDate.xlsx"

/*
	Sauvegardes à supprimer pour n'en garder que les plus récentes : les noms
	sont triés du plus récent au plus ancien et ceux au-delà du nombre gardé
	sont rendus. Seuls les noms datés comptent : les autres fichiers du
	dossier (fichier d'échange de la synchronisation, copies de conflit,
	anciens exports) ne sont jamais touchés.
	inNames : noms des fichiers présents dans le dossier
	inKeep  : nombre de sauvegardes à garder
*/
fun backupsToDelete(
	inNames : List<String>,
	inKeep  : Int = kBackupKept
): List<String> =
	inNames
		.filter { kDatedBackup.matches(it) }
		.sortedDescending()
		.drop(inKeep)
