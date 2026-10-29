package fr.maubin.patrimoineplus.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.link.PairingOffer
import fr.maubin.patrimoineplus.data.link.PhonePeers
import fr.maubin.patrimoineplus.data.link.WifiStatus
import fr.maubin.patrimoineplus.data.link.WifiSyncer
import fr.maubin.patrimoineplus.data.link.NetworkId
import fr.maubin.patrimoineplus.data.link.hasWifiNamePermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val kSyncTime = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm")				// heure d'une synchronisation

/*
	Réglages de la synchronisation Wi-Fi : état, appairage par QR code, PC
	appairés, réseaux familiaux, synchronisation à la demande.
	inSyncer : synchronisation Wi-Fi du téléphone
*/
@HiltViewModel
class WifiSyncViewModel @Inject constructor(
	private val inSyncer: WifiSyncer
) : ViewModel()
{
	private val fMessage = MutableStateFlow<String?>(null)								// résultat du dernier appairage ou de la dernière demande

	// État de la synchronisation Wi-Fi.
	val status: StateFlow<WifiStatus> = inSyncer.status

	// PC appairés et réseaux familiaux.
	val peers: StateFlow<PhonePeers> = inSyncer.peers

	// Résultat du dernier appairage, en clair ; null sans.
	val message: StateFlow<String?> = fMessage

	/*
		Appaire avec le PC d'un QR code scanné, puis synchronise.
		inText : contenu du QR code
	*/
	fun pair(inText: String)
	{
		val vOffer = PairingOffer.decode(inText)											// offre
		if (vOffer == null)
		{
			fMessage.value = "Ce QR code n’est pas celui de l’appairage de PatrimoinePlus (page Appareils du PC)."
			return
		}
		fMessage.value = "Appairage avec ${vOffer.pcName}…"
		viewModelScope.launch {
			fMessage.value = inSyncer.pair(vOffer)?.let { "Appairage impossible : $it" } ?: "${vOffer.pcName} est appairé."
			inSyncer.sync()
		}
	}

	/*
		Synchronise tout de suite.
	*/
	fun syncNow()
	{
		viewModelScope.launch { inSyncer.sync() }
	}

	/*
		Retire un PC appairé.
		inId : PC
	*/
	fun remove(inId: String) = inSyncer.remove(inId)

	/*
		Retire un réseau familial.
		inNetwork : réseau
	*/
	fun removeHome(inNetwork: NetworkId) = inSyncer.removeHome(inNetwork)
}

/*
	État de la synchronisation Wi-Fi, en clair.
	inStatus : état
*/
fun wifiStatusText(inStatus: WifiStatus): String =
	when (inStatus)
	{
		WifiStatus.Unpaired   -> "Aucun PC appairé : affichez le QR code sur un PC (page Appareils), puis scannez-le ici."
		is WifiStatus.Away    -> inStatus.reason
		WifiStatus.Working    -> "Synchronisation en cours…"
		is WifiStatus.Idle    -> "À jour avec ${inStatus.pcs.joinToString(", ")} (${Instant.ofEpochMilli(inStatus.at).atZone(ZoneId.systemDefault()).format(kSyncTime)})."
		is WifiStatus.Waiting -> inStatus.message
		is WifiStatus.Failed  -> inStatus.message
	}

/*
	Section « Synchronisation Wi-Fi » des réglages (contenu d'une carte) :
	état, scan du QR code du PC (autorisations de localisation, pour le nom du
	Wi-Fi, puis de l'appareil photo), PC appairés, réseaux familiaux.
	inViewModel : réglages de la synchronisation Wi-Fi
*/
@Composable
fun WifiSyncSection(inViewModel: WifiSyncViewModel = hiltViewModel())
{
	val vStatus  by inViewModel.status.collectAsStateWithLifecycle()						// état
	val vPeers   by inViewModel.peers.collectAsStateWithLifecycle()						// appareils
	val vMessage by inViewModel.message.collectAsStateWithLifecycle()						// résultat de l'appairage
	var vScan    by remember { mutableStateOf(false) }										// lecteur ouvert
	val vContext = androidx.compose.ui.platform.LocalContext.current						// contexte
	val vAsk     = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vScan = true }	// autorisation de localisation, puis lecteur
	val vMuted   = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
		Text(wifiStatusText(vStatus), style = MaterialTheme.typography.bodyLarge)
		vMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = vMuted) }
		Text("Seulement sur le Wi-Fi de la maison ; ailleurs, rien n’est tenté. L’autorisation de localisation sert à reconnaître ce Wi-Fi (son nom), jamais votre position.", style = MaterialTheme.typography.bodyMedium, color = vMuted)
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			TextButton(onClick = {
				if (hasWifiNamePermission(vContext)) vScan = true
				else vAsk.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
			}) { Text("Scanner le QR code du PC") }
			if (vPeers.pcs.isNotEmpty()) TextButton(onClick = inViewModel::syncNow) { Text("Synchroniser maintenant") }
		}
		if (vPeers.pcs.isNotEmpty())
		{
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			Text("PC appairés", style = MaterialTheme.typography.titleSmall)
			vPeers.pcs.forEach { inPc ->
				Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
					Column(Modifier.weight(1f)) {
						Text(inPc.name, style = MaterialTheme.typography.bodyLarge)
						Text(inPc.lastSync?.let { "Synchronisé le ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(kSyncTime)}" } ?: "Pas encore synchronisé", style = MaterialTheme.typography.bodySmall, color = vMuted)
					}
					TextButton(onClick = { inViewModel.remove(inPc.id) }) { Text("Retirer") }
				}
			}
			Text("Réseau familial", style = MaterialTheme.typography.titleSmall)
			vPeers.home.forEach { inNetwork ->
				Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
					Text(inNetwork.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
					TextButton(onClick = { inViewModel.removeHome(inNetwork) }) { Text("Retirer") }
				}
			}
		}
	}
	if (vScan)
	{
		QrScannerDialog(
			inHint     = "Visez le QR code affiché par le PC (page Appareils › Appairer le téléphone).",
			inOnResult = { inText -> vScan = false; inViewModel.pair(inText) },
			inOnClose  = { vScan = false }
		)
	}
}
