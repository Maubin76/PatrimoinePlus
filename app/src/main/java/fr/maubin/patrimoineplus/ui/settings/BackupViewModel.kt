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
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.ui.lock.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val kExportDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)	// date du dernier export

/*
	Verrou et sauvegarde : active le verrou, écrit l'export dans le fichier
	choisi, lit et vérifie un fichier à importer, puis remplace les données
	après confirmation.
	inContext  : contexte de l'application (accès aux fichiers choisis)
	inBackup   : dépôt d'export et d'import
	inSettings : réglages (verrou, dernier export, assistant)
	inLock     : verrou de l'application
	inClock    : horloge
*/
@HiltViewModel
class BackupViewModel @Inject constructor(
	@ApplicationContext private val inContext : Context,
	private val inBackup   : BackupRepository,
	private val inSettings : SettingsRepository,
	private val inLock     : AppLock,
	private val inClock    : Clock
) : ViewModel()
{
	// Vrai si le verrou est activé.
	val lockEnabled: StateFlow<Boolean> =
		inSettings.lockEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

	// Instant du dernier export (ms) ; null s'il n'y en a jamais eu.
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

	/*
		Nom proposé pour le fichier d'export : patrimoineplus-AAAA-MM-JJ.json.
	*/
	fun exportFileName(): String = "patrimoineplus-${LocalDate.now(inClock)}.json"

	/*
		Écrit l'export complet dans le fichier choisi et mémorise l'instant.
		inUri    : fichier choisi par l'utilisateur
		inOnDone : message à afficher (réussite ou échec)
	*/
	fun export(
		inUri    : Uri,
		inOnDone : (String) -> Unit
	)
	{
		viewModelScope.launch {
			val vMessage = try
			{
				val vJson = inBackup.exportJson()									// contenu du fichier
				withContext(Dispatchers.IO) {
					val vStream = inContext.contentResolver.openOutputStream(inUri, "wt") ?: error("fichier inaccessible")	// flux d'écriture
					vStream.use { it.write(vJson.toByteArray(Charsets.UTF_8)) }
				}
				inSettings.setLastExportAt(inClock.millis())
				"Export enregistré."
			}
			catch (inError: Exception)
			{
				"Échec de l’export : ${inError.message ?: "erreur inconnue"}"
			}																		// message du résultat
			inOnDone(vMessage)
		}
	}

	/*
		Lit et vérifie un fichier à importer, sans rien modifier.
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
				val vText = withContext(Dispatchers.IO) {
					val vStream = inContext.contentResolver.openInputStream(inUri) ?: error("fichier inaccessible")	// flux de lecture
					vStream.use { it.readBytes().toString(Charsets.UTF_8) }
				}																	// contenu du fichier
				withContext(Dispatchers.Default) { decodeBackup(vText) }
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
	Date d'un export, formatée en français (« 8 octobre 2026 »).
	inAt : instant de l'export (ms)
*/
fun formatExportDate(inAt: Long): String =
	Instant.ofEpochMilli(inAt).atZone(ZoneId.systemDefault()).toLocalDate().format(kExportDateFormat)
