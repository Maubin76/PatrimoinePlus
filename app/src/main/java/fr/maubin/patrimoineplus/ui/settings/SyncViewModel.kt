package fr.maubin.patrimoineplus.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.maubin.patrimoineplus.data.sync.SyncStatus
import fr.maubin.patrimoineplus.data.sync.SyncStore
import fr.maubin.patrimoineplus.data.sync.Syncer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val kSyncTime = DateTimeFormatter.ofPattern("d MMM 'à' HH:mm", Locale.FRANCE)	// instant d'une synchronisation

/*
	Synchronisation par fichier, vue des Réglages et de la fenêtre de conflit :
	choix du dossier (autorisation durable du système), synchronisation à la
	demande, arrêt, arbitrage d'un conflit.
	inContext : contexte de l'application (autorisations sur le dossier)
	inSyncer  : service de synchronisation
	inStore   : mémoire de la synchronisation
*/
@HiltViewModel
class SyncViewModel @Inject constructor(
	@ApplicationContext private val inContext : Context,
	private val inSyncer : Syncer,
	private val inStore  : SyncStore
) : ViewModel()
{
	// État de la synchronisation.
	val status: StateFlow<SyncStatus> = inSyncer.status

	// Dossier choisi, tel qu'affiché (« Documents/PatrimoinePlus ») ; null sans dossier.
	val folder: StateFlow<String?> =
		inStore.folder.map { it?.let(::folderLabel) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

	/*
		Adopte le dossier choisi : l'autorisation est rendue durable, puis
		celle de l'ancien dossier est rendue ; la synchronisation part aussitôt.
		Le même dossier choisi à nouveau garde la mémoire de la dernière
		synchronisation (pas de faux conflit). Une autorisation refusée laisse
		l'ancien dossier en place.
		inUri : dossier choisi
	*/
	fun chooseFolder(inUri: Uri)
	{
		viewModelScope.launch {
			val vOld = inStore.folder.first()										// dossier actuel
			val vGranted = runCatching {
				inContext.contentResolver.takePersistableUriPermission(inUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
			}.isSuccess																// autorisation obtenue
			if (!vGranted)
			{
				return@launch
			}
			if (vOld != inUri.toString())
			{
				release(vOld)
				inStore.setFolder(inUri.toString())
			}
			inSyncer.sync()
		}
	}

	/*
		Arrête la synchronisation (le fichier d'échange reste dans le dossier).
	*/
	fun stop()
	{
		viewModelScope.launch {
			release(inStore.folder.first())
			inStore.setFolder(null)
			inSyncer.sync()
		}
	}

	/*
		Synchronise tout de suite et rend le message à afficher.
		inOnMessage : message
	*/
	fun syncNow(inOnMessage: (String) -> Unit)
	{
		viewModelScope.launch {
			inOnMessage(
				when (val vStatus = inSyncer.sync())
				{
					is SyncStatus.Idle     -> "Synchronisation à jour."
					is SyncStatus.Conflict -> "Les deux côtés ont changé : choisissez la version à garder."
					is SyncStatus.Failed   -> vStatus.message
					SyncStatus.Off         -> "Aucun dossier synchronisé."
					SyncStatus.Working     -> "Synchronisation en cours…"
				}
			)
		}
	}

	/*
		Tranche un conflit.
		inKeepLocal : vrai pour garder les données du téléphone
	*/
	fun resolve(inKeepLocal: Boolean)
	{
		viewModelScope.launch { inSyncer.resolve(inKeepLocal) }
	}

	/*
		Rend l'autorisation durable d'un ancien dossier.
		inUri : ancien dossier ; null s'il n'y en avait pas
	*/
	private fun release(inUri: String?)
	{
		inUri ?: return
		runCatching {
			inContext.contentResolver.releasePersistableUriPermission(Uri.parse(inUri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
		}
	}
}

/*
	Nom lisible d'un dossier choisi : « primary:Documents/PatrimoinePlus » →
	« Documents/PatrimoinePlus ».
	inUri : adresse du dossier
*/
internal fun folderLabel(inUri: String): String =
	runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(inUri)).substringAfter(':').ifEmpty { "Stockage du téléphone" } }.getOrDefault(inUri)

/*
	Texte d'état de la synchronisation pour les Réglages.
	inStatus : état
*/
fun syncStatusText(inStatus: SyncStatus): String =
	when (inStatus)
	{
		SyncStatus.Off         -> "Aucun dossier choisi. Choisissez le dossier partagé avec votre PC (par Syncthing), par exemple Documents/PatrimoinePlus."
		SyncStatus.Working     -> "Synchronisation en cours…"
		is SyncStatus.Idle     -> inStatus.at?.let { "À jour. Dernière synchronisation le ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(kSyncTime)}." } ?: "À jour."
		is SyncStatus.Conflict -> "Conflit : les deux côtés ont changé. Choisissez la version à garder."
		is SyncStatus.Failed   -> inStatus.message
	}
