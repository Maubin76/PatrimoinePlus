package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.link.PairingOffer
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.PeerState
import fr.maubin.patrimoineplus.desktop.data.Source
import fr.maubin.patrimoineplus.desktop.ui.components.FloatingCard
import fr.maubin.patrimoineplus.desktop.ui.components.QrCode
import fr.maubin.patrimoineplus.ui.components.MergeConflictsDialog
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val kSyncTime = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm")		// heure d'une synchronisation

/*
	Carte « Synchronisation avec le téléphone » de la page Appareils : état
	(réseau familial ou non, écoute), appairage par QR code, téléphones
	appairés (dernière synchronisation, retrait), réseaux familiaux (ajout du
	réseau actuel, retrait). Rien en démonstration.
	inController : contrôleur
*/
@Composable
fun PhoneSyncCard(inController: DesktopController)
{
	val vState  by inController.peers.state.collectAsState()								// synchronisation Wi-Fi
	val vSpace  by inController.workspace.collectAsState()								// source des données
	val vColors = MaterialTheme.colorScheme													// palette courante
	val vFolder = vSpace.source is Source.Folder											// dossier de données ouvert
	FloatingCard {
		Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			Text(statusText(vState, vFolder), style = MaterialTheme.typography.bodyLarge)
			vState.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = vColors.error) }
			if (vFolder) Button(onClick = inController.peers::startPairing) { Text(if (vState.phones.isEmpty()) "Appairer le téléphone" else "Appairer un autre téléphone") }
			if (vState.phones.isNotEmpty())
			{
				HorizontalDivider(color = vColors.outlineVariant)
				Text("Téléphones appairés", style = MaterialTheme.typography.titleMedium)
				vState.phones.forEach { inPhone ->
					Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
						Column(Modifier.weight(1f)) {
							Text(inPhone.name, style = MaterialTheme.typography.bodyLarge)
							Text(
								text  = inPhone.lastSync?.let { "Dernière synchronisation le ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(kSyncTime)}" } ?: "Pas encore synchronisé",
								style = MaterialTheme.typography.bodyMedium,
								color = vColors.onSurfaceVariant
							)
						}
						TextButton(onClick = { inController.peers.removePhone(inPhone.id) }) { Text("Retirer") }
					}
				}
			}
			if (vFolder)
			{
				HorizontalDivider(color = vColors.outlineVariant)
				Text("Réseau familial", style = MaterialTheme.typography.titleMedium)
				Text("La synchronisation ne fonctionne que sur ces réseaux ; ailleurs, rien n’est tenté.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				if (vState.home.isEmpty()) Text("Aucun pour l’instant : le réseau actuel sera enregistré au premier appairage.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				vState.home.forEach { inNetwork ->
					Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
						Text(inNetwork.label + if (inNetwork in vState.networks) " (actuel)" else "", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
						TextButton(onClick = { inController.peers.setHome(inNetwork, false) }) { Text("Retirer") }
					}
				}
				vState.networks.filter { it !in vState.home }.forEach { inNetwork ->
					OutlinedButton(onClick = { inController.peers.setHome(inNetwork, true) }) { Text("Ajouter « ${inNetwork.label} »") }
				}
			}
		}
	}
	vState.offer?.let { inOffer -> PairingDialog(inOffer, inController) }
}

/*
	État de la synchronisation Wi-Fi, en clair.
	inState  : état
	inFolder : dossier de données ouvert
*/
private fun statusText(
	inState  : PeerState,
	inFolder : Boolean
): String =
	when
	{
		!inFolder                -> "Ouvrez votre dossier de données (Réglages) pour synchroniser le téléphone : la démonstration ne se synchronise pas."
		inState.phones.isEmpty() -> "Aucun téléphone appairé. Appairez-le une fois : il se synchronisera ensuite tout seul sur le Wi-Fi de la maison."
		!inState.onHome          -> "Hors du réseau familial : la synchronisation est en pause, elle reprendra à la maison."
		inState.running          -> "Sur le réseau familial : le téléphone peut se synchroniser avec ce PC."
		else                     -> "Sur le réseau familial, mais ce PC n’écoute pas encore."
	}

/*
	Fenêtre d'appairage : QR code à scanner avec le téléphone, temps restant ;
	se ferme d'elle-même une fois le téléphone appairé ou l'offre expirée.
	inOffer      : offre affichée
	inController : contrôleur
*/
@Composable
private fun PairingDialog(
	inOffer      : PairingOffer,
	inController : DesktopController
)
{
	var vNow by remember { mutableLongStateOf(inController.now()) }							// instant affiché (horloge de l'appli)
	LaunchedEffect(inOffer) {
		while (vNow < inOffer.expires)
		{
			delay(1_000)
			vNow = inController.now()
		}
		inController.peers.cancelPairing()
	}
	val vLeft = ((inOffer.expires - vNow).coerceAtLeast(0) / 1_000).toInt()					// secondes restantes
	AlertDialog(
		onDismissRequest = inController.peers::cancelPairing,
		title            = { Text("Appairer le téléphone") },
		text             = {
			Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
				Text("Sur le téléphone, sur le Wi-Fi de la maison : Réglages › Synchronisation Wi-Fi › « Scanner le QR code du PC ».", style = MaterialTheme.typography.bodyMedium)
				QrCode(inOffer.encode(), 240.dp, "QR code d’appairage")
				Text("Valable encore ${vLeft / 60} min ${"%02d".format(vLeft % 60)} s · adresse ${inOffer.addresses.firstOrNull() ?: "inconnue"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		},
		confirmButton    = { TextButton(onClick = inController.peers::cancelPairing) { Text("Annuler") } }
	)
}

/*
	Choix attendus sur le PC après une synchronisation avec un téléphone :
	éléments modifiés des deux côtés, ou version entière (fusion impossible).
	Affichés par-dessus n'importe quelle page.
	inController : contrôleur
*/
@Composable
fun PhoneChoiceDialogs(inController: DesktopController)
{
	val vState  by inController.peers.state.collectAsState()								// synchronisation Wi-Fi
	val vChoice = vState.choice ?: return														// choix attendu
	if (vChoice.conflicts.isNotEmpty())
	{
		MergeConflictsDialog(vChoice.conflicts, "Ce PC", vChoice.peerName) { inController.peers.resolve(it) }
		return
	}
	AlertDialog(
		onDismissRequest = {},
		title            = { Text("Quelle version garder ?") },
		text             = { Text("Les données de ce PC et celles de ${vChoice.peerName} ne peuvent pas être fusionnées (${vChoice.reason}). La version écartée reste dans les sauvegardes.") },
		confirmButton    = { TextButton(onClick = { inController.peers.resolve(inKeepPc = true) }) { Text("Ce PC") } },
		dismissButton    = { TextButton(onClick = { inController.peers.resolve(inKeepPc = false) }) { Text(vChoice.peerName) } }
	)
}
