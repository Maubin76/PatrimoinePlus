package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.defaultPcName
import fr.maubin.patrimoineplus.desktop.ui.LocalAssistantRoom
import fr.maubin.patrimoineplus.desktop.ui.components.FloatingCard
import fr.maubin.patrimoineplus.desktop.ui.tour.tourAnchor
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.width
import fr.maubin.patrimoineplus.ui.components.GhostButton
import fr.maubin.patrimoineplus.desktop.ui.components.PageHeader
import fr.maubin.patrimoineplus.ui.components.LemonButton
import fr.maubin.patrimoineplus.ui.components.GhostPill
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import fr.maubin.patrimoineplus.ui.components.GhostButton

private val kHistoryTime = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm")		// heure d'une modification

/*
	Page « Appareils » : synchronisation avec le téléphone. Pour l'instant
	(phase d'essai) : ce PC (nom montré au téléphone, réseau auquel il est
	relié, adresses), l'historique des modifications envoyées par les appareils
	et le diagnostic réseau, qui vérifie que le téléphone
	trouve ce PC et peut lui parler. L'appairage viendra ensuite ici.
	inController : contrôleur
*/
@Composable
fun DevicesScreen(inController: DesktopController)
{
	val vSpace    by inController.workspace.collectAsState()							// données (historique)
	val vSettings by inController.settings.collectAsState()							// réglages (nom du PC)
	val vState    by inController.diagnostic.state.collectAsState()					// diagnostic
	val vColors   = MaterialTheme.colorScheme											// palette courante
	var vName     by remember { mutableStateOf(vSettings.deviceName) }				// nom saisi
	LaunchedEffect(Unit) { inController.diagnostic.refresh(); inController.refreshPeers() }
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(top = 6.dp, bottom = 28.dp).padding(bottom = LocalAssistantRoom.current)) {
		PageHeader(inTitle = "Appareils", inSubtitle = "Synchronisation directe avec le téléphone, sur le Wi-Fi de la maison", inPrimary = { LemonButton("Nouvelle opération", inController::newOperation, inIcon = LineIcons.Plus) })
		Spacer(Modifier.height(28.dp))
		SectionHeader(inTitle = "Ce PC", inModifier = Modifier.tourAnchor("devices.this"))
		Spacer(Modifier.height(12.dp))
		FloatingCard {
			Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					OutlinedTextField(
						value         = vName,
						onValueChange = { vName = it },
						label         = { Text("Nom montré au téléphone") },
						placeholder   = { Text(defaultPcName()) },
						singleLine    = true,
						modifier      = Modifier.weight(1f)
					)
					GhostButton(onClick = { inController.setDeviceName(vName) }, enabled = vName.trim() != vSettings.deviceName) { Text("Renommer") }
				}
				Text(
					text  = if (vState.networks.isEmpty()) "Réseau : non reconnu (ni Wi-Fi ni box lisibles)." else "Réseau : " + vState.networks.joinToString(" et ") { it.label } + ".",
					style = MaterialTheme.typography.bodyLarge
				)
				Text(
					text  = if (vState.addresses.isEmpty()) "Adresse sur le réseau local : aucune." else "Adresse sur le réseau local : " + vState.addresses.joinToString(", ") + ".",
					style = MaterialTheme.typography.bodyMedium,
					color = vColors.onSurfaceVariant
				)
			}
		}
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Synchronisation avec le téléphone", inModifier = Modifier.tourAnchor("devices.sync"))
		Spacer(Modifier.height(12.dp))
		PhoneSyncCard(inController)
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Écosystème", inModifier = Modifier.tourAnchor("devices.ecosystem"))
		Spacer(Modifier.height(12.dp))
		FloatingCard {
			Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text("Tous les couples connus, réunis par le fichier d’échange : chaque PC ne connaît que le téléphone, le téléphone relaie entre les PC.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				EcosystemDiagram(vSpace.data.devices, vSettings.deviceId, inController.now())
			}
		}
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Historique des modifications", inModifier = Modifier.tourAnchor("devices.history"))
		Spacer(Modifier.height(12.dp))
		FloatingCard {
			Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				if (vSpace.data.history.isEmpty())
				{
					Text("Aucune modification envoyée pour l’instant : chaque synchronisation qui envoie des changements y laisse une ligne (appareil, heure, changements).", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				}
				vSpace.data.history.forEach { inEntry ->
					Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
						Text(Instant.ofEpochMilli(inEntry.at).atZone(ZoneId.systemDefault()).format(kHistoryTime), style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant, modifier = Modifier.width(152.dp))
						Text(inEntry.device, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(180.dp))
						Text(inEntry.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
					}
				}
			}
		}
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Diagnostic réseau", inModifier = Modifier.tourAnchor("devices.diagnostic"))
		Spacer(Modifier.height(12.dp))
		FloatingCard {
			Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(
					text  = "Vérifie que le téléphone trouve ce PC et peut lui parler, avant la synchronisation. Lancez-le ici, puis, sur le téléphone : Réglages › Diagnostic réseau.",
					style = MaterialTheme.typography.bodyMedium,
					color = vColors.onSurfaceVariant
				)
				Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					if (vState.running) GhostButton(onClick = inController.diagnostic::stop) { Text("Arrêter") }
					else GhostButton(onClick = inController.diagnostic::start) { Text("Lancer le diagnostic") }
				}
				vState.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = vColors.error) }
				if (vState.running)
				{
					Text("En attente du téléphone (port ${vState.port}${if (vState.shared) ", serveur de la synchronisation Wi-Fi" else ""}). Si Windows demande d’autoriser PatrimoinePlus, cochez « Réseaux privés ».", style = MaterialTheme.typography.bodyLarge)
				}
				if (vState.visits.isEmpty())
				{
					if (vState.running) Text("Aucun appareil ne s’est encore présenté.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				}
				else
				{
					Text("Appareils qui se sont présentés", style = MaterialTheme.typography.titleMedium)
					vState.visits.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
				}
			}
		}
	}
}
