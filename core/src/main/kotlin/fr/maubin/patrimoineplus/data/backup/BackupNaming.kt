package fr.maubin.patrimoineplus.data.backup

import java.time.LocalDate

const val kBackupPrefix   = "patrimoineplus-"									// début du nom des sauvegardes
const val kBackupKept     = 3													// sauvegardes conservées
const val kBackupMime     = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"	// type d'un classeur .xlsx
const val kBackupFolder   = "PatrimoinePlus"									// dossier sous Documents

/*
	Nom du fichier de sauvegarde d'un jour : patrimoineplus-AAAA-MM-JJ.xlsx
	(l'ordre alphabétique est l'ordre chronologique).
	inDate : jour de la sauvegarde
*/
fun backupFileName(inDate: LocalDate): String = "$kBackupPrefix$inDate.xlsx"

/*
	Sauvegardes à supprimer pour n'en garder que les plus récentes : les noms
	sont triés du plus récent au plus ancien et ceux au-delà du nombre gardé
	sont rendus. Les fichiers qui ne suivent pas le modèle sont ignorés.
	inNames : noms des fichiers présents dans le dossier
	inKeep  : nombre de sauvegardes à garder
*/
fun backupsToDelete(
	inNames : List<String>,
	inKeep  : Int = kBackupKept
): List<String> =
	inNames
		.filter { it.startsWith(kBackupPrefix) && it.endsWith(".xlsx") }
		.sortedDescending()
		.drop(inKeep)
