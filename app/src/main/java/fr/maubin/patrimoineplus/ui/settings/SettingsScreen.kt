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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin

/*
	Onglet des réglages : le mode discret fonctionne dès maintenant ; verrou
	et sauvegarde arrivent à l'étape 7.
	inHidden   : vrai si le mode discret est actif
	inOnHidden : active ou désactive le mode discret
*/
@Composable
fun SettingsScreen(
	inHidden   : Boolean,
	inOnHidden : (Boolean) -> Unit
)
{
	ScreenScaffold(inTitle = "Réglages") { inPadding ->
		Column(
			Modifier
				.padding(top = inPadding.calculateTopPadding())
				.verticalScroll(rememberScrollState())
				.padding(start = kScreenMargin, end = kScreenMargin, top = 16.dp, bottom = inPadding.calculateBottomPadding() + 16.dp)
		) {
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
