package fr.maubin.patrimoineplus.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.components.SegmentedChoice
import fr.maubin.patrimoineplus.ui.components.PillChoice
import fr.maubin.patrimoineplus.domain.model.Profile
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import fr.maubin.patrimoineplus.ui.profiles.PendingRemoval
import fr.maubin.patrimoineplus.ui.components.ProfileRemovalDialog
import fr.maubin.patrimoineplus.domain.model.CustomTheme
import fr.maubin.patrimoineplus.ui.components.ThemeSwatches
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background

/*
	Verrou et sauvegarde, vus par l'écran des réglages.
	lockEnabled  : vrai si le verrou est activé
	lastBackup   : date de la dernière sauvegarde, formatée ; null s'il n'y en a jamais eu
	folder       : dossier des sauvegardes, tel qu'affiché
	onLockChange : demande d'activer ou de désactiver le verrou
	onBackupNow  : sauvegarde tout de suite
	onImport     : lance l'import (choix du fichier)
*/
data class SafetyActions(
	val lockEnabled  : Boolean,
	val lastBackup   : String?,
	val folder       : String,
	val onLockChange : (Boolean) -> Unit,
	val onBackupNow  : () -> Unit,
	val onImport     : () -> Unit
)

/*
	Onglet des réglages : apparence, mode discret, verrou et sauvegarde.
	inThemeMode   : apparence choisie
	inOnThemeMode : change l'apparence
	inHidden      : vrai si le mode discret est actif
	inOnHidden    : active ou désactive le mode discret
	inSafety      : verrou et sauvegarde
	inDemo        : actions de démonstration ; null en version release
	inSync        : synchronisation avec le PC ; null pour masquer la section
	inColorTheme   : thème de couleurs choisi
	inOnColorTheme : change le thème de couleurs
	inGlass        : effet verre actif
	inOnGlass      : active ou retire l'effet verre
	inProfiles     : profils de la famille ; null pour masquer la section
	inThemes       : thèmes personnalisés et leurs actions ; null pour masquer la liste
	inUpdates      : mises à jour (vérification, version) ; null pour masquer la section
*/
@Composable
fun SettingsScreen(
	inThemeMode   : ThemeMode,
	inOnThemeMode : (ThemeMode) -> Unit,
	inHidden      : Boolean,
	inOnHidden    : (Boolean) -> Unit,
	inSafety      : SafetyActions,
	inDemo        : DemoActions? = null,
	inSync        : SyncActions? = null,
	inColorTheme   : ColorTheme = ColorTheme.MONOCHROME,
	inOnColorTheme : (ColorTheme) -> Unit = {},
	inGlass        : Boolean = false,
	inOnGlass      : (Boolean) -> Unit = {},
	inProfiles     : ProfilesActions? = null,
	inThemes       : ThemesActions? = null,
	inUpdates      : UpdatesActions? = null
)
{
	ScreenScaffold(inTitle = "Réglages") { inPadding ->
		Column(
			Modifier
				.padding(top = inPadding.calculateTopPadding())
				.verticalScroll(rememberScrollState())
				.padding(start = kScreenMargin, end = kScreenMargin, top = 16.dp, bottom = inPadding.calculateBottomPadding() + 16.dp)
		) {
			SectionHeader(inTitle = "Apparence")
			Spacer(Modifier.height(8.dp))
			ThemeModeSelector(inThemeMode, inOnThemeMode)
			Spacer(Modifier.height(16.dp))
			Text("Thème", style = MaterialTheme.typography.titleMedium)
			Spacer(Modifier.height(8.dp))
			val vCustom = inThemes?.let { inActions -> inActions.themes.firstOrNull { it.id == inActions.selected } }	// thème personnalisé choisi ; null sans
			SegmentedChoice(
				inOptions  = ColorTheme.entries.map<ColorTheme, ColorTheme?> { it },
				inSelected = if (vCustom != null) null else inColorTheme,
				inLabel    = { it?.label.orEmpty() },
				inOnSelect = { it?.let(inOnColorTheme) }
			)
			Spacer(Modifier.height(8.dp))
			Text(vCustom?.let { "Thème personnalisé « ${it.name} » : ses cinq couleurs prennent les rôles du thème Coloré." } ?: inColorTheme.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			inThemes?.let { inActions ->
				Spacer(Modifier.height(12.dp))
				CustomThemes(inActions)
			}
			Spacer(Modifier.height(16.dp))
			BorderedCard {
				SwitchRow(
					inTitle    = "Effet verre",
					inDetail   = "Panneaux translucides sur un fond de formes floues ; en sombre, noir profond et accent violet.",
					inChecked  = inGlass,
					inOnChange = inOnGlass
				)
			}
			Spacer(Modifier.height(32.dp))
			SectionHeader(inTitle = "Confidentialité")
			Spacer(Modifier.height(8.dp))
			BorderedCard {
				SwitchRow(
					inTitle    = "Mode discret",
					inDetail   = "Masque les montants sur tous les écrans. L’œil de la barre du haut fait de même.",
					inChecked  = inHidden,
					inOnChange = inOnHidden
				)
			}
			Spacer(Modifier.height(32.dp))
			SectionHeader(inTitle = "Sécurité")
			Spacer(Modifier.height(8.dp))
			BorderedCard {
				SwitchRow(
					inTitle    = "Verrou biométrique",
					inDetail   = "Empreinte ou code du téléphone à l’ouverture, et au retour après une minute ou plus hors de l’appli.",
					inChecked  = inSafety.lockEnabled,
					inOnChange = inSafety.onLockChange
				)
			}
			inUpdates?.let { inActions ->
				Spacer(Modifier.height(32.dp))
				SectionHeader(inTitle = "Mises à jour")
				Spacer(Modifier.height(8.dp))
				BorderedCard {
					SwitchRow(
						inTitle    = "Vérifier au démarrage",
						inDetail   = "Consulte les versions publiées sur GitHub ; aucune donnée n’est envoyée.",
						inChecked  = inActions.enabled,
						inOnChange = inActions.onEnabled
					)
					HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
					ActionRow(
						inTitle   = "Vérifier maintenant",
						inDetail  = "Version installée : ${inActions.version}.",
						inOnClick = inActions.onCheckNow
					)
				}
			}
			inProfiles?.let { inActions ->
				Spacer(Modifier.height(32.dp))
				SectionHeader(inTitle = "Profils")
				Spacer(Modifier.height(8.dp))
				ProfilesSection(inActions)
			}
			Spacer(Modifier.height(32.dp))
			SectionHeader(inTitle = "Données")
			Spacer(Modifier.height(8.dp))
			BorderedCard {
				ActionRow(
					inTitle   = "Sauvegarder maintenant",
					inDetail  = "Sauvegarde automatique chaque jour dans ${inSafety.folder} : classeur Excel non chiffré, une page par type de compte ; les 3 dernières sont conservées. " +
						(inSafety.lastBackup?.let { "Dernière le $it." } ?: "Aucune pour l’instant."),
					inOnClick = inSafety.onBackupNow
				)
				HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
				ActionRow(
					inTitle   = "Importer",
					inDetail  = "Remplace toutes les données par celles d’une sauvegarde (classeur, ou ancien export JSON), après vérification et confirmation.",
					inOnClick = inSafety.onImport
				)
			}
			if (inSync != null)
			{
				Spacer(Modifier.height(32.dp))
				SectionHeader(inTitle = "Synchronisation")
				Spacer(Modifier.height(8.dp))
				BorderedCard {
					ActionRow(
						inTitle   = inSync.folder?.let { "Dossier : $it" } ?: "Choisir le dossier",
						inDetail  = inSync.status,
						inOnClick = inSync.onChoose
					)
					if (inSync.folder != null)
					{
						HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
						ActionRow(
							inTitle   = "Synchroniser maintenant",
							inDetail  = "Le fichier patrimoineplus-donnees.xlsx est réécrit après chaque modification et relu au retour dans l’appli.",
							inOnClick = inSync.onSyncNow
						)
						HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
						ActionRow(
							inTitle   = "Arrêter la synchronisation",
							inDetail  = "Les données restent sur le téléphone ; le fichier reste dans le dossier.",
							inOnClick = inSync.onStop
						)
					}
				}
			}
			if (inDemo != null)
			{
				Spacer(Modifier.height(32.dp))
				DemoSection(inDemo)
			}
		}
	}
}

/*
	Titre et explication d'un réglage.
	inTitle    : nom du réglage
	inDetail   : explication
	inModifier : modificateur appliqué
*/
@Composable
private fun SettingText(
	inTitle    : String,
	inDetail   : String,
	inModifier : Modifier = Modifier
)
{
	Column(inModifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
		Text(
			text  = inTitle,
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface
		)
		Text(
			text  = inDetail,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

/*
	Réglage à interrupteur : toute la ligne bascule la valeur.
	inTitle    : nom du réglage
	inDetail   : explication
	inChecked  : valeur actuelle
	inOnChange : demande de changement
*/
@Composable
private fun SwitchRow(
	inTitle    : String,
	inDetail   : String,
	inChecked  : Boolean,
	inOnChange : (Boolean) -> Unit
)
{
	Row(
		modifier          = Modifier
			.fillMaxWidth()
			.toggleable(value = inChecked, role = Role.Switch, onValueChange = inOnChange)
			.padding(horizontal = 20.dp, vertical = 16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		SettingText(
			inTitle    = inTitle,
			inDetail   = inDetail,
			inModifier = Modifier.weight(1f)
		)
		Spacer(Modifier.width(16.dp))
		Switch(
			checked         = inChecked,
			onCheckedChange = null,
			colors          = SwitchDefaults.colors(
				checkedTrackColor = MaterialTheme.colorScheme.primary,
				checkedThumbColor = MaterialTheme.colorScheme.onPrimary
			)
		)
	}
}

/*
	Réglage qui lance une action : toute la ligne est cliquable.
	inTitle   : nom de l'action
	inDetail  : explication
	inOnClick : lance l'action
*/
@Composable
private fun ActionRow(
	inTitle   : String,
	inDetail  : String,
	inOnClick : () -> Unit
)
{
	SettingText(
		inTitle    = inTitle,
		inDetail   = inDetail,
		inModifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 64.dp)
			.clickable(role = Role.Button, onClick = inOnClick)
			.padding(horizontal = 20.dp, vertical = 16.dp)
	)
}

/*
	Choix de l'apparence : boutons segmentés Système / Clair / Sombre ; le
	segment actif est plein (noir en clair, blanc en sombre).
	inMode     : apparence choisie
	inOnSelect : change l'apparence
*/
@Composable
private fun ThemeModeSelector(
	inMode     : ThemeMode,
	inOnSelect : (ThemeMode) -> Unit
)
{
	PillChoice(ThemeMode.entries, inMode, { it.label }, inOnSelect)
}

/*
	Actions de démonstration (version debug seulement).
	onLoad  : remplace les données par la démonstration
	onClear : efface toutes les données
*/
data class DemoActions(
	val onLoad  : () -> Unit,
	val onClear : () -> Unit
)

/*
	Section « Démonstration » : charger des données fictives ou tout effacer.
	L'effacement demande une confirmation : il est irréversible.
	inDemo : actions de démonstration
*/
@Composable
private fun DemoSection(inDemo: DemoActions)
{
	var vAsk by remember { mutableStateOf<String?>(null) }						// action à confirmer
	SectionHeader(inTitle = "Démonstration")
	Spacer(Modifier.height(8.dp))
	BorderedCard {
		Column(Modifier.padding(20.dp)) {
			SettingText(
				inTitle  = "Données fictives (version debug)",
				inDetail = "Six mois de comptes et d’opérations inventés, pour essayer l’appli. Remplacent toutes les données et arrêtent la synchronisation."
			)
			Spacer(Modifier.height(16.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedButton(onClick = { vAsk = "load" }) { Text("Charger la démo") }
				OutlinedButton(onClick = { vAsk = "clear" }) { Text("Tout effacer") }
			}
		}
	}
	if (vAsk != null)
	{
		val vLoad = vAsk == "load"												// chargement ou effacement
		AlertDialog(
			onDismissRequest = { vAsk = null },
			title            = { Text(if (vLoad) "Charger la démo ?" else "Tout effacer ?") },
			text             = { Text(if (vLoad) "Toutes les données actuelles seront remplacées par des données fictives." else "Toutes les données seront supprimées. C’est irréversible.") },
			confirmButton    = {
				TextButton(onClick = {
					vAsk = null
					if (vLoad) inDemo.onLoad() else inDemo.onClear()
				}) { Text(if (vLoad) "Charger" else "Effacer") }
			},
			dismissButton    = { TextButton(onClick = { vAsk = null }) { Text("Annuler") } }
		)
	}
}

/*
	Profils de la famille et leurs actions (section des réglages).
	profiles : profils, dans l'ordre
	error    : dernier refus ; null sans refus
	onAdd    : ajoute un profil (nom), puis appelle la suite si réussi
	onRename : renomme un profil (identifiant, nom)
	onRemove  : demande le retrait d'un profil (identifiant) ; récapitulatif s'il a des comptes
	pending   : retrait à confirmer ; null sans demande
	onConfirm : confirme le retrait demandé
	onCancel  : annule le retrait demandé
*/
data class ProfilesActions(
	val profiles  : List<Profile>,
	val error     : String?,
	val onAdd     : (String, () -> Unit) -> Unit,
	val onRename  : (String, String) -> Unit,
	val onRemove  : (String) -> Unit,
	val pending   : PendingRemoval? = null,
	val onConfirm : () -> Unit = {},
	val onCancel  : () -> Unit = {}
)

/*
	Section des profils : nom modifiable (✓ pour l'enregistrer), retrait,
	puis ajout ; le dernier refus s'affiche dessous.
	inActions : profils et actions
*/
@Composable
private fun ProfilesSection(inActions: ProfilesActions)
{
	var vNew by remember { mutableStateOf("") }										// nom du profil à ajouter
	BorderedCard {
		Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
			inActions.profiles.forEach { inProfile ->
				var vName by remember(inProfile.id, inProfile.name) { mutableStateOf(inProfile.name) }	// nom saisi
				Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
					OutlinedTextField(vName, { vName = it }, singleLine = true, modifier = Modifier.weight(1f).semantics { contentDescription = "Nom du profil ${inProfile.name}" })
					IconButton(onClick = { inActions.onRename(inProfile.id, vName) }, enabled = vName.trim() != inProfile.name) {
						Icon(LineIcons.Check, contentDescription = "Renommer ${inProfile.name}")
					}
					IconButton(onClick = { inActions.onRemove(inProfile.id) }, enabled = inActions.profiles.size > 1) {
						Icon(LineIcons.Close, contentDescription = "Retirer ${inProfile.name}")
					}
				}
			}
			Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
				OutlinedTextField(vNew, { vNew = it }, singleLine = true, placeholder = { Text("Nouveau profil (prénom)") }, modifier = Modifier.weight(1f).semantics { contentDescription = "Nom du nouveau profil" })
				Spacer(Modifier.width(8.dp))
				Button(onClick = { inActions.onAdd(vNew) { vNew = "" } }) { Text("Ajouter") }
			}
			inActions.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
		}
	}
	inActions.pending?.let { inPending -> ProfileRemovalDialog(inPending.name, inPending.plan, inActions.onConfirm, inActions.onCancel) }
}

/*
	Thèmes personnalisés et leurs actions (réglages).
	themes   : thèmes créés
	selected : thème choisi sur ce téléphone ; vide sans
	onSelect : choisit un thème
	onSave   : crée (identifiant null) ou modifie un thème ; refus par rappel
	onDelete : supprime un thème
*/
data class ThemesActions(
	val themes   : List<CustomTheme>,
	val selected : String,
	val onSelect : (String) -> Unit,
	val onSave   : (String?, String, List<String>, (String?) -> Unit) -> Unit,
	val onDelete : (String) -> Unit
)

/*
	Liste des thèmes personnalisés : une ligne par thème (pastilles, nom ; un
	toucher le choisit, le crayon l'ouvre), puis « Créer un thème ».
	inActions : thèmes et actions
*/
@Composable
private fun CustomThemes(inActions: ThemesActions)
{
	var vEditing by remember { mutableStateOf<CustomTheme?>(null) }					// thème ouvert
	var vCreating by remember { mutableStateOf(false) }								// création en cours
	val vColors  = MaterialTheme.colorScheme											// palette courante
	BorderedCard {
		Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
			inActions.themes.forEach { inTheme ->
				val vOn = inTheme.id == inActions.selected											// thème choisi
				Row(
					modifier          = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(16.dp))
						.background(if (vOn) vColors.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent)
						.selectable(selected = vOn, role = Role.RadioButton) { inActions.onSelect(inTheme.id) }
						.padding(horizontal = 8.dp, vertical = 4.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					ThemeSwatches(inTheme.colors, 16.dp)
					Spacer(Modifier.width(12.dp))
					Text(inTheme.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
					IconButton(onClick = { vEditing = inTheme }) { Icon(LineIcons.Edit, contentDescription = "Modifier le thème ${inTheme.name}") }
				}
			}
			OutlinedButton(onClick = { vCreating = true }) { Text("Créer un thème") }
		}
	}
	if (vCreating || vEditing != null)
	{
		val vTheme = vEditing																	// thème modifié ; null pour une création
		ThemeSheet(
			inTheme     = vTheme,
			inOnSave    = { inName, inColorsTyped, inOnRefusal -> inActions.onSave(vTheme?.id, inName, inColorsTyped) { inRefusal -> if (inRefusal == null) { vCreating = false; vEditing = null } else inOnRefusal(inRefusal) } },
			inOnDelete  = { vTheme?.let { inActions.onDelete(it.id) }; vEditing = null },
			inOnDismiss = { vCreating = false; vEditing = null }
		)
	}
}

/*
	Mises à jour (réglages).
	enabled    : vérification au lancement
	version    : version installée
	onEnabled  : active ou coupe la vérification
	onCheckNow : vérifie tout de suite
*/
data class UpdatesActions(
	val enabled    : Boolean,
	val version    : String,
	val onEnabled  : (Boolean) -> Unit,
	val onCheckNow : () -> Unit
)
