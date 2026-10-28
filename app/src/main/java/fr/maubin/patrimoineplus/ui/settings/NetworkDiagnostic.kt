package fr.maubin.patrimoineplus.ui.settings

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.maubin.patrimoineplus.data.link.LinkClient
import fr.maubin.patrimoineplus.data.link.PhoneNetwork
import fr.maubin.patrimoineplus.data.link.currentPhoneNetwork
import fr.maubin.patrimoineplus.data.link.discoverPcs
import fr.maubin.patrimoineplus.data.link.kLinkPort
import fr.maubin.patrimoineplus.data.link.phoneInfo
import fr.maubin.patrimoineplus.ui.components.FormSheet
import fr.maubin.patrimoineplus.ui.components.TextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/*
	Résultat de l'essai d'un PC.
	name    : nom du PC (ou adresse si inconnu)
	address : adresse essayée
	detail  : résultat, en clair
	ok      : le PC a répondu
*/
data class PcProbe(
	val name    : String,
	val address : String,
	val detail  : String,
	val ok      : Boolean
)

/*
	État du diagnostic réseau du téléphone.
	network   : réseau du téléphone ; null avant la première lecture
	searching : recherche en cours
	searched  : une recherche a eu lieu
	probes    : PC essayés
*/
data class PhoneDiagnosticState(
	val network   : PhoneNetwork?  = null,
	val searching : Boolean        = false,
	val searched  : Boolean        = false,
	val probes    : List<PcProbe>  = emptyList()
)

/*
	Diagnostic réseau du téléphone (phase d'essai de la synchronisation) :
	lit le Wi-Fi, cherche les PC du réseau local (dont le diagnostic est
	lancé, page Appareils), puis se présente à chacun et mesure la réponse ;
	une adresse peut aussi être essayée à la main. Aucune donnée n'est
	échangée.
	inContext : contexte
*/
@HiltViewModel
class NetworkDiagnosticViewModel @Inject constructor(
	@ApplicationContext private val inContext: Context
) : ViewModel()
{
	private val fState  = MutableStateFlow(PhoneDiagnosticState())					// état
	private val fClient = LinkClient(phoneInfo(inContext, "diagnostic-" + UUID.randomUUID()))	// présentation (identifiant jetable)

	// État du diagnostic.
	val state: StateFlow<PhoneDiagnosticState> = fState

	/*
		Relit le réseau du téléphone (après l'autorisation, au retour).
	*/
	fun refresh()
	{
		fState.value = fState.value.copy(network = currentPhoneNetwork(inContext))
	}

	/*
		Cherche les PC du réseau local et se présente à chacun.
	*/
	fun search()
	{
		if (fState.value.searching) return
		fState.value = fState.value.copy(searching = true, network = currentPhoneNetwork(inContext))
		viewModelScope.launch {
			val vProbes = withContext(Dispatchers.IO) {
				runCatching { discoverPcs() }.getOrDefault(emptyList()).map { probe(it.address, it.beacon.port, it.beacon.name) }
			}																		// PC trouvés et essayés
			fState.value = fState.value.copy(searching = false, searched = true, probes = vProbes)
		}
	}

	/*
		Essaie une adresse saisie à la main (« 192.168.1.20 » ou
		« 192.168.1.20:47801 »).
		inText : adresse
	*/
	fun tryAddress(inText: String)
	{
		val vHost = inText.trim().substringBefore(':')									// adresse
		val vPort = inText.trim().substringAfter(':', "").toIntOrNull() ?: kLinkPort	// port
		if (vHost.isEmpty() || fState.value.searching) return
		fState.value = fState.value.copy(searching = true)
		viewModelScope.launch {
			val vProbe = withContext(Dispatchers.IO) { probe(vHost, vPort, null) }		// essai
			fState.value = fState.value.copy(searching = false, searched = true, probes = listOf(vProbe) + fState.value.probes.filter { it.address != vHost })
		}
	}

	/*
		Se présente à un PC et mesure sa réponse.
		inHost : adresse
		inPort : port
		inName : nom annoncé par la découverte ; null si inconnu
	*/
	private fun probe(
		inHost : String,
		inPort : Int,
		inName : String?
	): PcProbe
	{
		val vStart = System.nanoTime()														// début de l'essai
		return runCatching { fClient.hello(inHost, inPort) }.fold(
			onSuccess = { inPc -> PcProbe(inPc.name, inHost, "Répond en ${(System.nanoTime() - vStart) / 1_000_000} ms (version ${inPc.appVersion}).", true) },
			onFailure = { inError -> PcProbe(inName ?: inHost, inHost, "Ne répond pas : ${inError.message ?: "erreur inconnue"}.", false) }
		)
	}
}

/*
	Ligne « Diagnostic réseau » des réglages et sa feuille : réseau du
	téléphone (nom du Wi-Fi après autorisation), recherche des PC, essai d'une
	adresse saisie.
	inViewModel : ViewModel du diagnostic
*/
@Composable
fun NetworkDiagnosticEntry(inViewModel: NetworkDiagnosticViewModel = hiltViewModel())
{
	var vOpen by remember { mutableStateOf(false) }									// feuille ouverte
	ActionRow(
		inTitle   = "Diagnostic réseau",
		inDetail  = "Vérifie que ce téléphone trouve les PC de la maison, avant la synchronisation Wi-Fi.",
		inOnClick = { vOpen = true }
	)
	if (vOpen) NetworkDiagnosticSheet(inViewModel) { vOpen = false }
}

/*
	Feuille du diagnostic réseau.
	inViewModel : ViewModel du diagnostic
	inOnDismiss : fermeture
*/
@Composable
internal fun NetworkDiagnosticSheet(
	inViewModel : NetworkDiagnosticViewModel,
	inOnDismiss : () -> Unit
)
{
	val vState   by inViewModel.state.collectAsStateWithLifecycle()					// état
	var vAddress by remember { mutableStateOf("") }									// adresse saisie
	val vAsk     = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { inViewModel.refresh() }	// autorisation de localisation
	val vMuted   = MaterialTheme.colorScheme.onSurfaceVariant							// texte secondaire
	LaunchedEffect(Unit) { inViewModel.refresh() }
	FormSheet(inTitle = "Diagnostic réseau", inOnDismiss = inOnDismiss) {
		val vNet = vState.network																// réseau du téléphone
		Text(
			text  = when
			{
				vNet == null        -> "Lecture du réseau…"
				!vNet.onWifi        -> "Pas de Wi-Fi : reliez le téléphone au Wi-Fi de la maison."
				vNet.wifi != null   -> "Wi-Fi : « ${vNet.wifi.value} »" + if (vNet.unmetered) "." else " (facturé à l’usage)."
				!vNet.permission    -> "Wi-Fi connecté ; son nom n’est lisible qu’avec l’autorisation de localisation."
				else                -> "Wi-Fi connecté ; nom illisible (activez la localisation du téléphone)."
			},
			style = MaterialTheme.typography.bodyLarge
		)
		if (vNet != null && vNet.onWifi && !vNet.permission)
		{
			Text("Android exige l’autorisation de localisation pour lire le nom du Wi-Fi. PatrimoinePlus ne lit que ce nom, jamais votre position : il servira à ne synchroniser que sur le Wi-Fi de la maison.", style = MaterialTheme.typography.bodyMedium, color = vMuted)
			OutlinedButton(onClick = { vAsk.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Autoriser") }
		}
		Text("Lancez d’abord le diagnostic sur le PC (page Appareils), puis cherchez-le ici.", style = MaterialTheme.typography.bodyMedium, color = vMuted)
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
			Button(onClick = inViewModel::search, enabled = !vState.searching) { Text("Chercher les PC") }
			if (vState.searching) CircularProgressIndicator()
		}
		if (vState.searched && vState.probes.isEmpty() && !vState.searching)
		{
			Text("Aucun PC trouvé. Vérifiez que le diagnostic est lancé sur le PC, que les deux appareils sont sur le même Wi-Fi, et que Windows autorise PatrimoinePlus sur les réseaux privés. Sinon, essayez l’adresse affichée par le PC :", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
		}
		vState.probes.forEach { inProbe ->
			Column(Modifier.fillMaxWidth()) {
				Text("${inProbe.name} (${inProbe.address})", style = MaterialTheme.typography.titleMedium)
				Text(inProbe.detail, style = MaterialTheme.typography.bodyMedium, color = if (inProbe.ok) vMuted else MaterialTheme.colorScheme.error)
			}
		}
		TextInput(vAddress, { vAddress = it }, "Adresse du PC (ex. 192.168.1.20)")
		OutlinedButton(onClick = { inViewModel.tryAddress(vAddress) }, enabled = vAddress.isNotBlank() && !vState.searching) { Text("Essayer cette adresse") }
	}
}
