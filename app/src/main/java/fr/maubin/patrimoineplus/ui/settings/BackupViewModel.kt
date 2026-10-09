package fr.maubin.patrimoineplus.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.BackupRepository
import fr.maubin.patrimoineplus.data.backup.AutoBackup
import fr.maubin.patrimoineplus.data.backup.BackupOutcome
import fr.maubin.patrimoineplus.data.backup.BackupStorage
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import java.io.ByteArrayInputStream
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.ui.lock.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val kExportDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)	// date du dernier export

/*
	Verrou et sauvegarde : active le verrou, sauvegarde à la demande, lit et
	vérifie un fichier à importer, puis remplace les données après
	confirmation.
	inContext    : contexte de l'application (accès aux fichiers choisis)
	inBackup     : dépôt de sauvegarde (remplacement des données)
	inAutoBackup : sauvegarde dans Documents/PatrimoinePlus
	inStorage    : dossier des sauvegardes
	inSettings   : réglages (verrou, dernière sauvegarde, assistant)
	inLock       : verrou de l'application
*/
@HiltViewModel
class BackupViewModel @Inject constructor(
	@ApplicationContext private val inContext : Context,
	private val inBackup     : BackupRepository,
	private val inAutoBackup : AutoBackup,
	private val inStorage    : BackupStorage,
	private val inSettings   : SettingsRepository,
	private val inLock       : AppLock
) : ViewModel()
{
	// Vrai si le verrou est activé.
	val lockEnabled: StateFlow<Boolean> =
		inSettings.lockEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

	// Instant de la dernière sauvegarde (ms) ; null s'il n'y en a jamais eu.
	val lastExportAt: StateFlow<Long?> =
		inSettings.lastExportAt.stateIn(viewModelScope, SharingStarted.Eagerly, null)

	/*
		Active ou désactive le verrou (l'activation suit une authentification
		réussie).
		inEnabled : vrai pour activer
	*/
	fun setLockEnabled(inEnabled: Boolean)
	{
		inLock.setEnabled(inEnabled)
	}

	// Dossier des sauvegardes, tel qu'affiché.
	val backupFolder: String get() = inStorage.displayPath

	/*
		Sauvegarde tout de suite dans Documents/PatrimoinePlus (remplace celle du
		jour) et rend le message à afficher.
		inOnDone : message à afficher
	*/
	fun backupNow(inOnDone: (String) -> Unit)
	{
		viewModelScope.launch {
			inOnDone(
				when (val vOutcome = inAutoBackup.backupNow())
				{
					is BackupOutcome.Saved    -> "Sauvegarde enregistrée : ${vOutcome.path}"
					BackupOutcome.Empty       -> "Rien à sauvegarder : aucun compte."
					BackupOutcome.AlreadyDone -> "Sauvegarde déjà faite aujourd’hui."
					is BackupOutcome.Failed   -> "Échec de la sauvegarde : ${vOutcome.message}"
				}
			)
		}
	}

	/*
		Lit et vérifie un fichier à importer, sans rien modifier : classeur
		(.xlsx) ou ancien export JSON, reconnus à leur contenu.
		inUri    : fichier choisi par l'utilisateur
		inOnRead : résultat de la vérification
	*/
	fun read(
		inUri    : Uri,
		inOnRead : (BackupRead) -> Unit
	)
	{
		viewModelScope.launch {
			val vRead = try
			{
				val vBytes = withContext(Dispatchers.IO) {
					val vStream = inContext.contentResolver.openInputStream(inUri) ?: error("fichier inaccessible")	// flux de lecture
					vStream.use { it.readBytes() }
				}																	// contenu du fichier
				withContext(Dispatchers.Default) { readAnyBackup(vBytes) }
			}
			catch (inError: Exception)
			{
				BackupRead.Invalid("Lecture impossible : ${inError.message ?: "erreur inconnue"}")
			}																		// fichier vérifié ou motif du refus
			inOnRead(vRead)
		}
	}

	/*
		Remplace toutes les données par celles du fichier vérifié.
		inFile   : contenu vérifié
		inOnDone : message à afficher
	*/
	fun replaceAll(
		inFile   : BackupFile,
		inOnDone : (String) -> Unit
	)
	{
		viewModelScope.launch {
			val vMessage = try
			{
				inBackup.replaceAll(inFile)
				inSettings.setOnboardingDone(true)
				"Données importées."
			}
			catch (inError: Exception)
			{
				"Échec de l’import, aucune donnée modifiée : ${inError.message ?: "erreur inconnue"}"
			}																		// message du résultat
			inOnDone(vMessage)
		}
	}
}

/*
	Lit une sauvegarde selon son contenu : classeur (archive ZIP, « PK ») ou
	ancien export JSON.
	inBytes : contenu du fichier
*/
internal fun readAnyBackup(inBytes: ByteArray): BackupRead =
	if (inBytes.size >= 2 && inBytes[0] == 'P'.code.toByte() && inBytes[1] == 'K'.code.toByte()) readBackupXlsx(ByteArrayInputStream(inBytes))
	else decodeBackup(inBytes.toString(Charsets.UTF_8))

/*
	Date d'une sauvegarde, formatée en français (« 8 octobre 2026 »).
	inAt : instant de l'export (ms)
*/
fun formatExportDate(inAt: Long): String =
	Instant.ofEpochMilli(inAt).atZone(ZoneId.systemDefault()).toLocalDate().format(kExportDateFormat)
