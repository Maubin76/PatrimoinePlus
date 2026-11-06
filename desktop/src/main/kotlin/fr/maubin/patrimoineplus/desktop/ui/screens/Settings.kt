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
import fr.maubin.patrimoineplus.desktop.ui.LocalAssistantRoom
import fr.maubin.patrimoineplus.desktop.ui.screens.WifiSyncSwitch
import fr.maubin.patrimoineplus.desktop.ui.screens.BackgroundOptions
import fr.maubin.patrimoineplus.ui.components.GhostButton
import fr.maubin.patrimoineplus.desktop.ui.components.PageHeader
import fr.maubin.patrimoineplus.ui.components.LemonButton
import fr.maubin.patrimoineplus.ui.components.GhostPill
import fr.maubin.patrimoineplus.ui.components.GhostButton
import fr.maubin.patrimoineplus.ui.components.BackdropSettings
import fr.maubin.patrimoineplus.desktop.ui.LocalPhotoPicker
import androidx.compose.foundation.layout.Box
import fr.maubin.patrimoineplus.desktop.data.NavPosition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import fr.maubin.patrimoineplus.desktop.SettingsSection
import fr.maubin.patrimoineplus.desktop.data.DesktopSettings
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.components.kBackdropTints
import fr.maubin.patrimoineplus.ui.components.backdropCode
import fr.maubin.patrimoineplus.domain.model.CategoryKind

/*
	Réglages de l'appli PC : grille de tuiles, une par rubrique (icône, nom,
	résumé de l'état) ; un clic ouvre la rubrique, « Tous les réglages »
	revient à la grille.
	inSpace        : données (profils, catégories, thèmes)
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
	val vNav by inController.navigation.collectAsState()					// rubrique ouverte
	AnimatedContent(targetState = vNav.section, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "rubrique") { inSection ->
		Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(top = 6.dp, bottom = 28.dp).padding(bottom = LocalAssistantRoom.current)) {
			if (inSection == null) SettingsTiles(inSpace, inController)
			else SettingsDetail(inSection, inSpace, inController, inOnPickFolder)
		}
	}
}

/*
	Grille des rubriques : trois tuiles par rangée, de même hauteur.
	inSpace      : données (profils, catégories)
	inController : contrôleur
*/
@Composable
private fun SettingsTiles(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vSettings by inController.settings.collectAsState()				// réglages
	PageHeader(inTitle = "Réglages", inSubtitle = "Une tuile par rubrique ; un clic l’ouvre", inPrimary = { LemonButton("Nouvelle opération", inController::newOperation, inIcon = LineIcons.Plus) })
	Spacer(Modifier.height(28.dp))
	Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
		SettingsSection.entries.chunked(3).forEach { inRow ->
			Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
				inRow.forEach { inSection ->
					val vAnchor = kSectionAnchors[inSection]?.let { Modifier.tourAnchor(it) } ?: Modifier		// repère de la visite guidée
					SectionTile(inSection, sectionSummary(inSection, inSpace, vSettings, inController), Modifier.weight(1f).fillMaxHeight().then(vAnchor)) { inController.openSettings(inSection) }
				}
			}
		}
	}
}

private val kSectionAnchors = mapOf(
	SettingsSection.DATA       to "settings.data",
	SettingsSection.APPEARANCE to "settings.appearance",
	SettingsSection.PROFILES   to "settings.profiles",
	SettingsSection.TOURS      to "settings.tours"
)																				// tuiles montrées par la visite guidée

/*
	Tuile d'une rubrique : icône dans un rond, nom, chevron, puis résumé de
	l'état en deux lignes au plus.
	inSection  : rubrique
	inSummary  : résumé de l'état
	inModifier : modificateur appliqué
	inOnClick  : ouvre la rubrique
*/
@Composable
private fun SectionTile(
	inSection  : SettingsSection,
	inSummary  : String,
	inModifier : Modifier,
	inOnClick  : () -> Unit
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	Tile(inModifier = inModifier, inOnClick = inOnClick, inClickLabel = "Ouvrir la rubrique ${inSection.label}") {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Box(Modifier.size(40.dp).clip(CircleShape).background(vColors.onSurface.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
				Icon(sectionIcon(inSection), contentDescription = null, modifier = Modifier.size(20.dp))
			}
			Spacer(Modifier.width(12.dp))
			Text(inSection.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f))
			Icon(LineIcons.ChevronRight, contentDescription = null, tint = vColors.onSurfaceVariant, modifier = Modifier.size(18.dp))
		}
		Spacer(Modifier.height(12.dp))
		Text(inSummary, style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
	}
}

/*
	Icône d'une rubrique.
	inSection : rubrique
*/
private fun sectionIcon(inSection: SettingsSection): ImageVector =
	when (inSection)
	{
		SettingsSection.DATA       -> LineIcons.Folder
		SettingsSection.APPEARANCE -> LineIcons.Drop
		SettingsSection.SYNC       -> LineIcons.Wifi
		SettingsSection.PRIVACY    -> LineIcons.Lock
		SettingsSection.PROFILES   -> LineIcons.Family
		SettingsSection.TOURS      -> LineIcons.Help
		SettingsSection.ASSISTANT  -> LineIcons.Smile
		SettingsSection.UPDATES    -> LineIcons.Download
		SettingsSection.CATEGORIES -> LineIcons.Tag
	}

/*
	Résumé de l'état d'une rubrique, pour sa tuile.
	inSection    : rubrique
	inSpace      : données (profils, catégories)
	inSettings   : réglages
	inController : contrôleur (version installée)
*/
private fun sectionSummary(
	inSection    : SettingsSection,
	inSpace      : WorkspaceState,
	inSettings   : DesktopSettings,
	inController : DesktopController
): String =
	when (inSection)
	{
		SettingsSection.DATA       -> when (val vSource = inSettings.source)
		{
			Source.Demo      -> "Démonstration : données fictives, jamais enregistrées"
			is Source.Folder -> vSource.path.toString()
			Source.None      -> "Aucune source"
		}
		SettingsSection.APPEARANCE -> "${inSettings.appearance.label} · barre ${navPlace(inSettings.nav)} · ${backdropName(inSettings.background)}"
		SettingsSection.SYNC       -> if (inSettings.wifiSync) "Wi-Fi de la maison activé" else "Wi-Fi désactivé"
		SettingsSection.PRIVACY    -> if (inSettings.hidden) "Mode discret : montants masqués" else "Montants visibles"
		SettingsSection.PROFILES   -> inSpace.data.profiles.let { inList -> "${inList.size} profil${if (inList.size > 1) "s" else ""} : ${inList.joinToString(", ") { it.name }}" }
		SettingsSection.TOURS      -> if (inSettings.toursOff) "Visites coupées" else "${Tours.keys.count { it in inSettings.toursSeen }} sur ${Tours.keys.size} vues"
		SettingsSection.ASSISTANT  -> when
		{
			!inSettings.assistant -> "Masqué"
			inSettings.calm       -> "Affiché, en mode calme"
			else                  -> "Affiché"
		}
		SettingsSection.UPDATES    -> "Version ${inController.appVersion ?: "inconnue"}${if (inSettings.updates) " · vérifiée au démarrage" else ""}"
		SettingsSection.CATEGORIES -> inSpace.data.categories.let { inList -> "${inList.count { it.kind == CategoryKind.EXPENSE }} de dépense, ${inList.count { it.kind == CategoryKind.INCOME }} de revenu" }
	}

/*
	Place de la barre de navigation, en toutes lettres.
	inPosition : position
*/
private fun navPlace(inPosition: NavPosition): String =
	when (inPosition)
	{
		NavPosition.TOP    -> "en haut"
		NavPosition.BOTTOM -> "en bas"
		NavPosition.LEFT   -> "à gauche"
		NavPosition.RIGHT  -> "à droite"
	}

/*
	Nom du fond choisi : par défaut, photo, teinte proposée ou code libre.
	inCode : réglage du fond (vide, « photo » ou « #RRGGBB »)
*/
private fun backdropName(inCode: String): String =
	when (inCode)
	{
		""   -> "fond par défaut"
		"photo" -> "fond photo"
		else -> kBackdropTints.firstOrNull { backdropCode(it.color) == inCode }?.let { "fond ${it.name.lowercase()}" } ?: "fond $inCode"
	}

/*
	Rubrique ouverte : titre et ligne d'aide, « Tous les réglages » pour
	revenir à la grille, puis ses réglages (largeur limitée, sauf les
	catégories sur deux colonnes).
	inSection      : rubrique
	inSpace        : données
	inController   : contrôleur
	inOnPickFolder : ouvre le sélecteur de dossier
*/
@Composable
private fun SettingsDetail(
	inSection      : SettingsSection,
	inSpace        : WorkspaceState,
	inController   : DesktopController,
	inOnPickFolder : () -> Path?
)
{
	val vSettings by inController.settings.collectAsState()				// réglages
	PageHeader(
		inTitle    = inSection.label,
		inSubtitle = inSection.hint,
		inActions  = { GhostPill("Tous les réglages", inController::back, inIcon = LineIcons.Back) },
		inPrimary  = { LemonButton("Nouvelle opération", inController::newOperation, inIcon = LineIcons.Plus) }
	)
	Spacer(Modifier.height(28.dp))
	if (inSection == SettingsSection.CATEGORIES)
	{
		CategoriesSection(inSpace, inController)
		return
	}
	Column(Modifier.widthIn(max = 920.dp)) {
		when (inSection)
		{
			SettingsSection.DATA       -> DataSection(vSettings.source, inController, inOnPickFolder)
			SettingsSection.APPEARANCE -> AppearanceSection(inSpace, vSettings, inController)
			SettingsSection.SYNC       -> SyncSection(vSettings.wifiSync, inController)
			SettingsSection.PRIVACY    -> PrivacySection(vSettings.hidden, inController)
			SettingsSection.PROFILES   -> ProfilesSection(inSpace.data.profiles, inController)
			SettingsSection.TOURS      -> ToursSection(vSettings.toursSeen, vSettings.toursOff, inController)
			SettingsSection.ASSISTANT  -> AssistantSection(vSettings.assistant, vSettings.calm, inController)
			SettingsSection.UPDATES    -> UpdatesSection(vSettings.updates, inController)
			SettingsSection.CATEGORIES -> Unit
		}
	}
}

/*
	Données : source affichée, sauvegardes du PC, choix d'un dossier,
	démonstration et fermeture.
	inSource       : source des données
	inController   : contrôleur
	inOnPickFolder : ouvre le sélecteur de dossier
*/
@Composable
private fun DataSection(
	inSource       : Source,
	inController   : DesktopController,
	inOnPickFolder : () -> Path?
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	FloatingCard {
		Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			Text(
				text  = when (inSource)
				{
					Source.Demo      -> "Démonstration : données fictives en mémoire, rien n’est lu ni écrit sur le disque."
					is Source.Folder -> "Dossier des données : ${inSource.path}\nFichier : $kSyncFileName, enregistré à chaque modification."
					Source.None      -> "Aucune source."
				},
				style = MaterialTheme.typography.bodyLarge
			)
			val vBackup by inController.backupInfo.collectAsState()								// sauvegardes du PC
			vBackup?.let { inInfo ->
				Text(inInfo, style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
			}
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				if (vBackup != null) GhostButton(onClick = { inController.backupIfDue(inForce = true) }) { Text("Sauvegarder maintenant") }
				GhostButton(onClick = { inOnPickFolder()?.let(inController::openFolder) }) { Text("Choisir un dossier…") }
				if (inSource != Source.Demo)
				{
					GhostButton(onClick = inController::openDemo) { Text("Passer en démonstration") }
				}
				GhostButton(onClick = inController::closeSource) { Text("Fermer") }
			}
		}
	}
}

/*
	Apparence : clair ou sombre, position de la barre de navigation,
	couleurs des données (thèmes personnalisés), fond.
	inSpace      : données (thèmes personnalisés)
	inSettings   : réglages
	inController : contrôleur
*/
@Composable
private fun AppearanceSection(
	inSpace      : WorkspaceState,
	inSettings   : DesktopSettings,
	inController : DesktopController
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	Text("Mode", style = MaterialTheme.typography.titleMedium)
	Spacer(Modifier.height(8.dp))
	Choice(ThemeMode.entries.map { it to it.label }, inSettings.appearance, inController::setAppearance)
	Spacer(Modifier.height(20.dp))
	Text("Barre de navigation", style = MaterialTheme.typography.titleMedium)
	Spacer(Modifier.height(8.dp))
	Choice(NavPosition.entries.map { it to it.label }, inSettings.nav, inController::setNavPosition)
	Spacer(Modifier.height(20.dp))
	Text("Couleurs des données", style = MaterialTheme.typography.titleMedium)
	Spacer(Modifier.height(8.dp))
	val vCustom = inSpace.data.themes.firstOrNull { it.id == inSettings.palette }	// thème personnalisé choisi ; null sans
	Text(
		vCustom?.let { "Thème personnalisé « ${it.name} » : ses cinq couleurs remplacent celles de la brume (hausses, baisses, épargne, immobilier, secondaire)." }
			?: "Brume lumineuse : orange, bleu, vert, bleu ciel et rouge pour les données ; un thème personnalisé peut les remplacer.",
		style = MaterialTheme.typography.bodyMedium,
		color = vColors.onSurfaceVariant
	)
	if (vCustom != null)
	{
		Spacer(Modifier.height(12.dp))
		GhostPill("Revenir aux couleurs de la brume", { inController.setColorTheme(ColorTheme.COLORFUL) })
	}
	Spacer(Modifier.height(12.dp))
	CustomThemes(inSpace.data.themes, vCustom?.id, inController)
	Spacer(Modifier.height(24.dp))
	val vPick = LocalPhotoPicker.current																// sélecteur de photo
	Box(Modifier.tourAnchor("settings.backdrop")) {
		FloatingCard {
			Box(Modifier.padding(24.dp)) {
				BackdropSettings(
					inCode          = inSettings.background,
					inBlur          = inSettings.blur,
					inVeil          = inSettings.veil,
					inOnColor       = inController::setBackgroundColor,
					inOnPickPhoto   = { vPick()?.let(inController::setBackgroundPhoto) },
					inOnRemovePhoto = inController::removeBackgroundPhoto,
					inOnBlur        = inController::setBackgroundBlur,
					inOnVeil        = inController::setBackgroundVeil
				)
			}
		}
	}
}

/*
	Synchronisation avec le téléphone : Wi-Fi de la maison, fonctionnement en
	arrière-plan, lien vers la page Appareils.
	inWifi       : synchronisation Wi-Fi active
	inController : contrôleur
*/
@Composable
private fun SyncSection(
	inWifi       : Boolean,
	inController : DesktopController
)
{
	FloatingCard {
		Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			WifiSyncSwitch(inWifi, inController::setWifiSync)
			BackgroundOptions(inController)
			TextButton(onClick = { inController.show(fr.maubin.patrimoineplus.desktop.Screen.DEVICES) }) { Text("Appairage et appareils : page Appareils") }
		}
	}
}

/*
	Confidentialité : mode discret.
	inHidden     : montants masqués
	inController : contrôleur
*/
@Composable
private fun PrivacySection(
	inHidden     : Boolean,
	inController : DesktopController
)
{
	FloatingCard {
		Row(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Mode discret", style = MaterialTheme.typography.titleMedium)
				Text("Masque les montants sur tous les écrans.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			Switch(checked = inHidden, onCheckedChange = { inController.toggleHidden() })
		}
	}
}

/*
	Assistant : affiché ou non, mode calme.
	inOn         : assistant affiché
	inCalm       : mode calme
	inController : contrôleur
*/
@Composable
private fun AssistantSection(
	inOn         : Boolean,
	inCalm       : Boolean,
	inController : DesktopController
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	FloatingCard {
		Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text("Afficher l’assistant", style = MaterialTheme.typography.titleMedium)
					Text("En haut à gauche : son humeur résume le mois (budget, objectifs, patrimoine) ; survolez-le ou cliquez dessus pour le détail.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				}
				Switch(checked = inOn, onCheckedChange = inController::setAssistant, modifier = Modifier.semantics { contentDescription = "Afficher l’assistant" })
			}
			Spacer(Modifier.height(12.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text("Mode calme", style = MaterialTheme.typography.titleMedium)
					Text("Il ne fait plus que suivre le pointeur des yeux : ni réactions, ni bulle qui s’ouvre d’elle-même.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				}
				Switch(checked = inCalm, enabled = inOn, onCheckedChange = inController::setAssistantCalm, modifier = Modifier.semantics { contentDescription = "Mode calme de l’assistant" })
			}
		}
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
				GhostButton(onClick = {
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
		Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
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
				GhostButton(onClick = inController::checkUpdatesNow, enabled = inController.appVersion != null) { Text("Vérifier maintenant") }
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
			GhostButton(onClick = inController::resetTours, enabled = inOff || inSeen.isNotEmpty()) { Text("Réactiver tous les tutoriels") }
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
						.padding(horizontal = 10.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					ThemeSwatches(inTheme.colors, 18.dp)
					Spacer(Modifier.width(16.dp))
					Text(inTheme.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
					if (vOn) Text("Choisi", style = MaterialTheme.typography.labelLarge, color = vColors.onSurfaceVariant)
					IconButton(onClick = { inController.openPanel(Panel.ThemeSheet(inTheme.id)) }) {
						Icon(LineIcons.Edit, contentDescription = "Modifier le thème ${inTheme.name}", modifier = Modifier.size(18.dp))
					}
				}
			}
			GhostButton(onClick = { inController.openPanel(Panel.ThemeSheet(null)) }, modifier = Modifier.padding(top = if (inThemes.isEmpty()) 0.dp else 8.dp)) { Text("Créer un thème") }
		}
	}
}

