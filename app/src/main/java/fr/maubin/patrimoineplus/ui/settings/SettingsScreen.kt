package fr.maubin.patrimoineplus.ui.settings

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
import fr.maubin.patrimoineplus.data.settings.ThemeMode
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin

/*
	Onglet des réglages : apparence et mode discret fonctionnent dès
	maintenant ; verrou et sauvegarde arrivent à l'étape 7.
	inThemeMode   : apparence choisie
	inOnThemeMode : change l'apparence
	inHidden      : vrai si le mode discret est actif
	inOnHidden    : active ou désactive le mode discret
	inDemo        : actions de démonstration ; null en version release
*/
@Composable
fun SettingsScreen(
	inThemeMode   : ThemeMode,
	inOnThemeMode : (ThemeMode) -> Unit,
	inHidden      : Boolean,
	inOnHidden    : (Boolean) -> Unit,
	inDemo        : DemoActions? = null
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
			Spacer(Modifier.height(32.dp))
			SectionHeader(inTitle = "Confidentialité")
			Spacer(Modifier.height(8.dp))
			BorderedCard {
				Row(
					modifier          = Modifier
						.fillMaxWidth()
						.toggleable(value = inHidden, role = Role.Switch, onValueChange = inOnHidden)
						.padding(horizontal = 20.dp, vertical = 16.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					SettingText(
						inTitle    = "Mode discret",
						inDetail   = "Masque les montants sur tous les écrans. L’œil de la barre du haut fait de même.",
						inMuted    = false,
						inModifier = Modifier.weight(1f)
					)
					Spacer(Modifier.width(16.dp))
					Switch(
						checked         = inHidden,
						onCheckedChange = null,
						colors          = SwitchDefaults.colors(
							checkedTrackColor = MaterialTheme.colorScheme.primary,
							checkedThumbColor = MaterialTheme.colorScheme.onPrimary
						)
					)
				}
			}
			Spacer(Modifier.height(32.dp))
			SectionHeader(inTitle = "Sécurité")
			Spacer(Modifier.height(8.dp))
			BorderedCard {
				UpcomingRow("Verrou biométrique", "Empreinte ou code du téléphone, re-verrouillage après une minute (étape 7).")
			}
			Spacer(Modifier.height(32.dp))
			SectionHeader(inTitle = "Données")
			Spacer(Modifier.height(8.dp))
			BorderedCard {
				UpcomingRow("Exporter", "Fichier JSON complet, rangé où vous voulez (étape 7).")
				HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
				UpcomingRow("Importer", "Remplace toutes les données par celles d’un export (étape 7).")
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
	inMuted    : atténue le titre (réglage pas encore disponible)
	inModifier : modificateur appliqué
*/
@Composable
private fun SettingText(
	inTitle    : String,
	inDetail   : String,
	inMuted    : Boolean,
	inModifier : Modifier = Modifier
)
{
	Column(inModifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
		Text(
			text  = inTitle,
			style = MaterialTheme.typography.titleMedium,
			color = if (inMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
		)
		Text(
			text  = inDetail,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

/*
	Réglage pas encore disponible : atténué, sans interaction.
	inTitle  : nom du réglage
	inDetail : ce qu'il fera, et quand
*/
@Composable
private fun UpcomingRow(
	inTitle  : String,
	inDetail : String
)
{
	SettingText(
		inTitle    = inTitle,
		inDetail   = inDetail,
		inMuted    = true,
		inModifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 16.dp)
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
	val vColors  = MaterialTheme.colorScheme								// palette courante
	val vEntries = ThemeMode.entries										// choix proposés
	SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
		vEntries.forEachIndexed { inIndex, inEntry ->
			SegmentedButton(
				selected = inEntry == inMode,
				onClick  = { inOnSelect(inEntry) },
				shape    = SegmentedButtonDefaults.itemShape(index = inIndex, count = vEntries.size),
				icon     = {},
				colors   = SegmentedButtonDefaults.colors(
					activeContainerColor   = vColors.primary,
					activeContentColor     = vColors.onPrimary,
					activeBorderColor      = vColors.primary,
					inactiveContainerColor = vColors.surface,
					inactiveContentColor   = vColors.onSurface,
					inactiveBorderColor    = vColors.outlineVariant
				),
				label    = { Text(inEntry.label) }
			)
		}
	}
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
				inDetail = "Six mois de comptes et d’opérations inventés, pour essayer l’appli. Remplacent toutes les données.",
				inMuted  = false
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
