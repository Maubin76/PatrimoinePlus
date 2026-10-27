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
import fr.maubin.patrimoineplus.desktop.ui.tour.tourAnchor
import androidx.compose.foundation.layout.Box
import fr.maubin.patrimoineplus.desktop.ui.tour.Tours
import androidx.compose.material3.TextButton
import fr.maubin.patrimoineplus.domain.model.CustomTheme
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.ui.components.ThemeSwatches
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size

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
		SectionHeader(inTitle = "Données", inModifier = Modifier.tourAnchor("settings.data"))
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
		SectionHeader(inTitle = "Apparence", inModifier = Modifier.tourAnchor("settings.appearance"))
		Spacer(Modifier.height(12.dp))
		Choice(ThemeMode.entries.map { it to it.label }, vSettings.appearance, inController::setAppearance)
		Spacer(Modifier.height(16.dp))
		Text("Thème", style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(8.dp))
		val vCustom = inSpace.data.themes.firstOrNull { it.id == vSettings.palette }	// thème personnalisé choisi ; null sans
		Choice(ColorTheme.entries.map { it as ColorTheme? to it.label }, if (vCustom != null) null else vSettings.colors, { it?.let(inController::setColorTheme) })
		Spacer(Modifier.height(8.dp))
		Text(vCustom?.let { "Thème personnalisé « ${it.name} » : ses cinq couleurs prennent les rôles du thème Coloré." } ?: vSettings.colors.detail, style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
		Spacer(Modifier.height(12.dp))
		CustomThemes(inSpace.data.themes, vCustom?.id, inController)
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
		SectionHeader(inTitle = "Profils", inModifier = Modifier.tourAnchor("settings.profiles"))
		Spacer(Modifier.height(12.dp))
		ProfilesSection(inSpace.data.profiles, inController)
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Tutoriels", inModifier = Modifier.tourAnchor("settings.tours"))
		Spacer(Modifier.height(12.dp))
		ToursSection(vSettings.toursSeen, vSettings.toursOff, inController)
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Mises à jour")
		Spacer(Modifier.height(12.dp))
		UpdatesSection(vSettings.updates, inController)
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

/*
	Mises à jour : vérification au lancement (interrupteur), version
	installée et « Vérifier maintenant ». La vérification ne contacte que
	GitHub, sans rien envoyer.
	inOn         : vérification au lancement
	inController : contrôleur
*/
@Composable
private fun UpdatesSection(
	inOn         : Boolean,
	inController : DesktopController
)
{
	FloatingCard {
		Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text("Vérifier les mises à jour au démarrage", style = MaterialTheme.typography.titleMedium)
					Text("Consulte les versions publiées sur GitHub ; aucune donnée n’est envoyée.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				Switch(checked = inOn, onCheckedChange = inController::setUpdates, modifier = Modifier.semantics { contentDescription = "Vérifier les mises à jour au démarrage" })
			}
			Spacer(Modifier.height(12.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text("Version installée : ${inController.appVersion ?: "inconnue"}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
				OutlinedButton(onClick = inController::checkUpdatesNow, enabled = inController.appVersion != null) { Text("Vérifier maintenant") }
			}
		}
	}
}

/*
	Tutoriels : une ligne par page, avec son état (vu ou à venir) et
	« Revoir », qui ouvre la page et lance sa visite tout de suite ;
	« Réactiver tous les tutoriels » les fait réapparaître à la prochaine
	venue sur chaque page.
	inSeen       : visites vues ou passées
	inOff        : visites coupées
	inController : contrôleur
*/
@Composable
private fun ToursSection(
	inSeen       : Set<String>,
	inOff        : Boolean,
	inController : DesktopController
)
{
	FloatingCard {
		Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
			Tours.keys.forEach { inKey ->
				Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
					Text(Tours.label(inKey), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
					Text(if (inOff || inKey in inSeen) "Vu" else "À la prochaine visite", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					Spacer(Modifier.width(12.dp))
					TextButton(onClick = { inController.replayTour(inKey) }, modifier = Modifier.semantics { contentDescription = "Revoir le tutoriel ${Tours.label(inKey)}" }) { Text("Revoir") }
				}
			}
			Spacer(Modifier.height(8.dp))
			OutlinedButton(onClick = inController::resetTours, enabled = inOff || inSeen.isNotEmpty()) { Text("Réactiver tous les tutoriels") }
		}
	}
}

/*
	Thèmes personnalisés : une ligne par thème (pastilles, nom ; un clic le
	choisit, le crayon l'ouvre), puis « Créer un thème ». Partagés avec le
	téléphone par le fichier d'échange ; le thème choisi reste propre à ce PC.
	inThemes     : thèmes créés
	inSelected   : thème choisi ; null sans
	inController : contrôleur
*/
@Composable
private fun CustomThemes(
	inThemes     : List<CustomTheme>,
	inSelected   : String?,
	inController : DesktopController
)
{
	val vColors = MaterialTheme.colorScheme												// palette courante
	FloatingCard {
		Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
			inThemes.forEach { inTheme ->
				val vOn = inTheme.id == inSelected													// thème choisi
				Row(
					modifier          = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(16.dp))
						.background(if (vOn) vColors.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent)
						.selectable(selected = vOn, role = Role.RadioButton) { inController.setCustomTheme(inTheme.id) }
						.padding(horizontal = 10.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					ThemeSwatches(inTheme.colors, 18.dp)
					Spacer(Modifier.width(14.dp))
					Text(inTheme.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
					if (vOn) Text("Choisi", style = MaterialTheme.typography.labelLarge, color = vColors.onSurfaceVariant)
					IconButton(onClick = { inController.openPanel(Panel.ThemeSheet(inTheme.id)) }) {
						Icon(LineIcons.Edit, contentDescription = "Modifier le thème ${inTheme.name}", modifier = Modifier.size(18.dp))
					}
				}
			}
			OutlinedButton(onClick = { inController.openPanel(Panel.ThemeSheet(null)) }, modifier = Modifier.padding(top = if (inThemes.isEmpty()) 0.dp else 8.dp)) { Text("Créer un thème") }
		}
	}
}

