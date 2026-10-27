package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

const val kPcBackupFolder = "Sauvegardes PC"										// sous-dossier du dossier synchronisé
const val kPcBackupKept   = 7														// sauvegardes du PC gardées (une semaine)
private const val kPcBackupPrefix = "patrimoineplus-pc-"							// début du nom des sauvegardes du PC
private val kPcBackupName = Regex("""patrimoineplus-pc-\d{4}-\d{2}-\d{2}\.xlsx""")	// nom d'une sauvegarde du PC

/*
	Nom d'une sauvegarde du PC : patrimoineplus-pc-AAAA-MM-JJ.xlsx (distinct de
	celles du téléphone : aucun conflit dans le dossier synchronisé ; l'ordre
	alphabétique est l'ordre chronologique).
	inDate : jour de la sauvegarde
*/
fun pcBackupFileName(inDate: LocalDate): String = "$kPcBackupPrefix$inDate.xlsx"

/*
	Sauvegardes du PC à supprimer pour n'en garder que les plus récentes ;
	les autres fichiers ne sont jamais touchés.
	inNames : noms des fichiers du dossier des sauvegardes
	inKeep  : nombre de sauvegardes à garder
*/
fun pcBackupsToDelete(
	inNames : List<String>,
	inKeep  : Int = kPcBackupKept
): List<String> = inNames.filter { kPcBackupName.matches(it) }.sortedDescending().drop(inKeep)

/*
	Résultat d'une sauvegarde du PC.
*/
sealed interface PcBackupResult
{
	// Sauvegarde écrite (ou déjà faite aujourd'hui).
	data class Done(val file: Path) : PcBackupResult

	// Rien à sauvegarder (aucun compte).
	data object Empty : PcBackupResult

	// Écriture impossible.
	data class Failed(val message: String) : PcBackupResult
}

/*
	Sauvegarde quotidienne du PC, dans le sous-dossier « Sauvegardes PC » du
	dossier synchronisé : classeur complet (même format que le fichier
	d'échange), écrit dans un fichier temporaire puis renommé (une coupure
	n'abîme jamais une sauvegarde) ; les sept plus récentes gardées. Une
	source sans compte n'est jamais sauvegardée (elle évincerait de bonnes
	sauvegardes).
	inClock : horloge (date de la sauvegarde)
*/
class PcBackup(private val inClock: Clock)
{
	/*
		Dossier des sauvegardes du PC dans un dossier synchronisé.
		inSynced : dossier synchronisé
	*/
	fun folder(inSynced: Path): Path = inSynced.resolve(kPcBackupFolder)

	/*
		Jour de la dernière sauvegarde du PC ; null s'il n'y en a pas.
		inSynced : dossier synchronisé
	*/
	fun last(inSynced: Path): LocalDate? =
		runCatching {
			Files.list(folder(inSynced)).use { inList ->
				inList.map { it.fileName.toString() }.filter { kPcBackupName.matches(it) }.toList()
			}.maxOrNull()?.removePrefix(kPcBackupPrefix)?.removeSuffix(".xlsx")?.let(LocalDate::parse)
		}.getOrNull()

	/*
		Sauvegarde du jour : écrite si elle n'existe pas encore (ou si elle est
		demandée), puis rotation.
		inSynced : dossier synchronisé
		inFile   : données à sauvegarder
		inForce  : réécrire même si celle du jour existe (« Sauvegarder maintenant »)
	*/
	fun backup(
		inSynced : Path,
		inFile   : BackupFile,
		inForce  : Boolean = false
	): PcBackupResult
	{
		if (inFile.accounts.isEmpty()) return PcBackupResult.Empty
		val vFolder = folder(inSynced)																// dossier des sauvegardes
		val vTarget = vFolder.resolve(pcBackupFileName(LocalDate.now(inClock)))					// sauvegarde du jour
		return try
		{
			Files.createDirectories(vFolder)
			if (inForce || !Files.exists(vTarget))
			{
				val vTemp = vFolder.resolve(".${vTarget.fileName}.tmp")								// écriture provisoire (ignorée par Syncthing : nom en point)
				Files.newOutputStream(vTemp).use { writeBackupXlsx(inFile.copy(exportedAt = Instant.now(inClock).toString()), it) }
				Files.move(vTemp, vTarget, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
			}
			val vNames = Files.list(vFolder).use { inList -> inList.map { it.fileName.toString() }.toList() }	// fichiers présents
			pcBackupsToDelete(vNames).forEach { Files.deleteIfExists(vFolder.resolve(it)) }
			PcBackupResult.Done(vTarget)
		}
		catch (inError: Exception)
		{
			PcBackupResult.Failed(inError.message ?: "erreur inconnue")
		}
	}
}
