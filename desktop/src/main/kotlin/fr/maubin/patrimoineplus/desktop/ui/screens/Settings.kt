package fr.maubin.patrimoineplus.desktop.ui.screens

import fr.maubin.patrimoineplus.ui.theme.ThemeMode
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.Source
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import java.nio.file.Path
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.desktop.ui.panels.Choice
import fr.maubin.patrimoineplus.desktop.ui.components.FloatingCard
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import fr.maubin.patrimoineplus.domain.model.Profile
import fr.maubin.patrimoineplus.data.edit.EditResult
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import fr.maubin.patrimoineplus.ui.components.ProfileRemovalDialog

/*
	Réglages de l'appli PC : source des données (dossier synchronisé,
	démonstration), apparence et thème de couleurs, mode discret, catégories.
	inSpace        : données (catégories)
	inController   : contrôleur
	inOnPickFolder : ouvre le sélecteur de dossier
*/
@Composable
fun SettingsScreen(
	inSpace        : WorkspaceState,
	inController   : DesktopController,
	inOnPickFolder : () -> Path?
)
{
	val vSettings by inController.settings.collectAsState()				// réglages
	val vColors   = MaterialTheme.colorScheme									// palette courante
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp).widthIn(max = 720.dp)) {
		Text("Réglages", style = MaterialTheme.typography.displaySmall)
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Données")
		Spacer(Modifier.height(12.dp))
		FloatingCard {
			Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(
					text  = when (val vSource = vSettings.source)
					{
						Source.Demo      -> "Démonstration : données fictives en mémoire, rien n’est lu ni écrit sur le disque."
						is Source.Folder -> "Dossier synchronisé : ${vSource.path}\nFichier lu : $kSyncFileName, relu automatiquement à chaque changement."
						Source.None      -> "Aucune source."
					},
					style = MaterialTheme.typography.bodyLarge
				)
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					OutlinedButton(onClick = { inOnPickFolder()?.let(inController::openFolder) }) { Text("Choisir un dossier…") }
					if (vSettings.source != Source.Demo)
					{
						OutlinedButton(onClick = inController::openDemo) { Text("Passer en démonstration") }
					}
					OutlinedButton(onClick = inController::closeSource) { Text("Fermer") }
				}
			}
		}
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Apparence")
		Spacer(Modifier.height(12.dp))
		Choice(ThemeMode.entries.map { it to it.label }, vSettings.appearance, inController::setAppearance)
		Spacer(Modifier.height(16.dp))
		Text("Thème", style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(8.dp))
		Choice(ColorTheme.entries.map { it to it.label }, vSettings.colors, inController::setColorTheme)
		Spacer(Modifier.height(8.dp))
		Text(vSettings.colors.detail, style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
		Spacer(Modifier.height(16.dp))
		FloatingCard {
			Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text("Effet verre", style = MaterialTheme.typography.titleMedium)
					Text("Panneaux translucides sur un fond de formes floues ; en sombre, noir profond et accent violet.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				}
				Switch(checked = vSettings.glass, onCheckedChange = inController::setGlass, modifier = Modifier.semantics { contentDescription = "Effet verre" })
			}
		}
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Confidentialité")
		Spacer(Modifier.height(12.dp))
		FloatingCard {
			Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text("Mode discret", style = MaterialTheme.typography.titleMedium)
					Text("Masque les montants sur tous les écrans.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				}
				Switch(checked = vSettings.hidden, onCheckedChange = { inController.toggleHidden() })
			}
		}
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Profils")
		Spacer(Modifier.height(12.dp))
		ProfilesSection(inSpace.data.profiles, inController)
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Catégories")
		Spacer(Modifier.height(12.dp))
		CategoriesSection(inSpace, inController)
	}
}

/*
	Profils de la famille : nom modifiable (✓ pour l'enregistrer), retrait
	(récapitulatif à confirmer s'il a des comptes : ceux dont il est seul
	propriétaire sont supprimés, sa part des autres est retirée), puis ajout.
	Chaque profil a sa vue ; la vue Famille rassemble tous les comptes.
	inProfiles   : profils
	inController : contrôleur
*/
@Composable
private fun ProfilesSection(
	inProfiles   : List<Profile>,
	inController : DesktopController
)
{
	var vError by remember { mutableStateOf<String?>(null) }						// refus affiché
	var vNew   by remember { mutableStateOf("") }									// nom du profil à ajouter
	var vAsk   by remember { mutableStateOf<Profile?>(null) }						// profil dont la suppression est à confirmer
	val vShow  = { inResult: EditResult -> vError = (inResult as? EditResult.Refused)?.messages?.joinToString(" ") }	// affiche un refus
	FloatingCard {
		Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
			inProfiles.forEach { inProfile ->
				var vName by remember(inProfile.id, inProfile.name) { mutableStateOf(inProfile.name) }	// nom saisi
				Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
					OutlinedTextField(
						value         = vName,
						onValueChange = { vName = it },
						singleLine    = true,
						modifier      = Modifier.weight(1f).semantics { contentDescription = "Nom du profil ${inProfile.name}" }
					)
					Spacer(Modifier.width(8.dp))
					IconButton(onClick = { vShow(inController.renameProfile(inProfile.id, vName)) }, enabled = vName.trim() != inProfile.name) {
						Icon(LineIcons.Check, contentDescription = "Renommer ${inProfile.name}")
					}
					IconButton(onClick = { if (inController.profileRemoval(inProfile.id).isEmpty) vShow(inController.removeProfile(inProfile.id)) else vAsk = inProfile }, enabled = inProfiles.size > 1) {
						Icon(LineIcons.Close, contentDescription = "Retirer ${inProfile.name}")
					}
				}
			}
			Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
				OutlinedTextField(
					value         = vNew,
					onValueChange = { vNew = it },
					singleLine    = true,
					placeholder   = { Text("Nouveau profil (prénom)") },
					modifier      = Modifier.weight(1f).semantics { contentDescription = "Nom du nouveau profil" }
				)
				Spacer(Modifier.width(8.dp))
				Button(onClick = {
					val vResult = inController.addProfile(vNew)						// ajout
					vShow(vResult)
					if (vResult is EditResult.Done) vNew = ""
				}) { Text("Ajouter") }
			}
			vError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
		}
	}
	vAsk?.let { inProfile ->
		ProfileRemovalDialog(
			inName      = inProfile.name,
			inPlan      = inController.profileRemoval(inProfile.id),
			inOnConfirm = { vShow(inController.removeProfile(inProfile.id)); vAsk = null },
			inOnDismiss = { vAsk = null }
		)
	}
}

