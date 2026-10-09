package fr.maubin.patrimoineplus.data.backup

import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/*
	Résultat d'une sauvegarde.
*/
sealed interface BackupOutcome
{
	/*
		Sauvegarde écrite.
		path : chemin affiché (dossier et nom du fichier)
	*/
	data class Saved(val path: String) : BackupOutcome

	// Rien à sauvegarder (aucun compte) : les sauvegardes existantes sont préservées.
	data object Empty : BackupOutcome

	// Déjà faite aujourd'hui (sauvegarde automatique seulement).
	data object AlreadyDone : BackupOutcome

	/*
		Échec ; rien n'a été remplacé.
		message : raison
	*/
	data class Failed(val message: String) : BackupOutcome
}

/*
	Sauvegarde quotidienne : un classeur par jour dans Documents/PatrimoinePlus,
	les trois derniers conservés. Une base sans compte n'est jamais sauvegardée,
	pour ne pas évincer de bonnes sauvegardes par des fichiers vides.
	inBackup   : lecture de la base
	inStorage  : dossier des sauvegardes
	inSettings : date de la dernière sauvegarde
	inClock    : horloge
*/
@Singleton
class AutoBackup @Inject constructor(
	private val inBackup   : BackupRepository,
	private val inStorage  : BackupStorage,
	private val inSettings : SettingsRepository,
	private val inClock    : Clock
)
{
	private val fLock = Mutex()													// une seule sauvegarde à la fois

	/*
		Sauvegarde si celle du jour n'a pas encore été faite (tâche quotidienne
		et rattrapage au démarrage).
	*/
	suspend fun backupIfDue(): BackupOutcome
	{
		val vLast = inSettings.lastExportAt.first()								// dernière sauvegarde
		val vDay  = vLast?.let { Instant.ofEpochMilli(it).atZone(inClock.zone).toLocalDate() }	// son jour
		return if (vDay == LocalDate.now(inClock)) BackupOutcome.AlreadyDone else backupNow()
	}

	/*
		Sauvegarde tout de suite (remplace celle du jour si elle existe).
	*/
	suspend fun backupNow(): BackupOutcome =
		fLock.withLock {
			try
			{
				val vFile = inBackup.snapshot()									// contenu de la base
				if (vFile.accounts.isEmpty())
				{
					return@withLock BackupOutcome.Empty
				}
				val vName = backupFileName(LocalDate.now(inClock))				// nom du fichier du jour
				withContext(Dispatchers.IO) { inStorage.write(vName) { writeBackupXlsx(vFile, it) } }
				inSettings.setLastExportAt(inClock.millis())
				BackupOutcome.Saved("${inStorage.displayPath}/$vName")
			}
			catch (inError: Exception)
			{
				BackupOutcome.Failed(inError.message ?: "erreur inconnue")
			}
		}
}
