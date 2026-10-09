package fr.maubin.patrimoineplus.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.maubin.patrimoineplus.data.sync.SyncStatus
import kotlinx.coroutines.launch

// Dossier proposé à l'ouverture du sélecteur : Documents/PatrimoinePlus (celui des sauvegardes).
private val kSuggestedFolder: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/PatrimoinePlus")

/*
	Synchronisation, vue par l'écran des Réglages.
	folder    : dossier choisi, tel qu'affiché ; null sans dossier
	status    : état, en clair
	onChoose  : ouvre le sélecteur de dossier
	onSyncNow : synchronise tout de suite
	onStop    : arrête la synchronisation
*/
data class SyncActions(
	val folder    : String?,
	val status    : String,
	val onChoose  : () -> Unit,
	val onSyncNow : () -> Unit,
	val onStop    : () -> Unit
)

/*
	Branche la synchronisation des Réglages : sélecteur de dossier du système
	(ouvert sur Documents/PatrimoinePlus), messages éphémères.
	inSnackbar  : hôte des messages éphémères
	inViewModel : ViewModel de la synchronisation
*/
@Composable
fun rememberSyncActions(
	inSnackbar  : SnackbarHostState,
	inViewModel : SyncViewModel = hiltViewModel()
): SyncActions
{
	val vScope  = rememberCoroutineScope()										// portée des messages
	val vStatus by inViewModel.status.collectAsStateWithLifecycle()			// état
	val vFolder by inViewModel.folder.collectAsStateWithLifecycle()			// dossier choisi
	val vPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { inUri ->
		if (inUri != null) inViewModel.chooseFolder(inUri)
	}																			// sélecteur de dossier
	return SyncActions(
		folder    = vFolder,
		status    = syncStatusText(vStatus),
		onChoose  = { vPicker.launch(kSuggestedFolder) },
		onSyncNow = { inViewModel.syncNow { inMessage -> vScope.launch { inSnackbar.showSnackbar(inMessage) } } },
		onStop    = inViewModel::stop
	)
}

/*
	Fenêtre de conflit, affichée par-dessus l'appli quand les données du
	téléphone et celles du fichier synchronisé ont changé toutes les deux.
	Rien n'est modifié tant qu'aucun choix n'est fait.
	inViewModel : ViewModel de la synchronisation
*/
@Composable
fun SyncConflictDialog(inViewModel: SyncViewModel = hiltViewModel())
{
	val vStatus by inViewModel.status.collectAsStateWithLifecycle()			// état
	val vConflict = vStatus as? SyncStatus.Conflict ?: return					// conflit en attente
	AlertDialog(
		onDismissRequest = {},
		title            = { Text("Quelle version garder ?") },
		text             = {
			Text(
				"Les données ont changé à la fois sur ce téléphone et dans le fichier synchronisé (PC).\n\n" +
				"Ce téléphone : ${vConflict.localSummary}.\n\nFichier : ${vConflict.fileSummary}.\n\n" +
				"La version écartée est remplacée ; si c’est celle du téléphone, elle est d’abord sauvegardée dans Documents/PatrimoinePlus."
			)
		},
		confirmButton    = { TextButton(onClick = { inViewModel.resolve(inKeepLocal = true) }) { Text("Ce téléphone") } },
		dismissButton    = { TextButton(onClick = { inViewModel.resolve(inKeepLocal = false) }) { Text("Le fichier") } }
	)
}
