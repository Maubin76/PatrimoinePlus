package fr.maubin.patrimoineplus.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.maubin.patrimoineplus.data.backup.newerFormatText
import fr.maubin.patrimoineplus.data.link.WifiStatus

/*
	Fenêtre « Mise à jour nécessaire », par-dessus l'appli, quand le PC envoie
	des données d'une version plus récente (le PC a été mis à jour, pas le
	téléphone) : explication et recherche de la mise à jour. Fermée, elle ne
	revient pas pour ce format avant le prochain lancement ; rien n'est
	modifié en attendant.
	inOnCheck : recherche de la mise à jour
	inWifi    : ViewModel de la synchronisation Wi-Fi
*/
@Composable
fun SyncVersionDialog(
	inOnCheck : () -> Unit,
	inWifi    : WifiSyncViewModel = hiltViewModel()
)
{
	val vStatus by inWifi.status.collectAsStateWithLifecycle()				// état de la synchronisation
	val vFormat = (vStatus as? WifiStatus.Failed)?.newerFormat ?: return		// format plus récent ; rien sinon
	var vSeen   by rememberSaveable { mutableStateOf<Int?>(null) }			// format dont la fenêtre a été fermée
	if (vSeen == vFormat) return
	AlertDialog(
		onDismissRequest = { vSeen = vFormat },
		title            = { Text("Mise à jour nécessaire") },
		text             = { Text(newerFormatText(vFormat, "le PC")) },
		confirmButton    = { TextButton(onClick = { vSeen = vFormat; inOnCheck() }) { Text("Rechercher la mise à jour") } },
		dismissButton    = { TextButton(onClick = { vSeen = vFormat }) { Text("Plus tard") } }
	)
}
