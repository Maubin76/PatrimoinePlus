package fr.maubin.patrimoineplus.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold

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
	val vSnackbar = remember { SnackbarHostState() }						// messages éphémères
	val vColors   = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)	// listes sur le fond
	ScreenScaffold(inTitle = "Réglages", inSnackbar = vSnackbar) { inPadding ->
		Column(Modifier.padding(inPadding).verticalScroll(rememberScrollState())) {
			SectionTitle("Confidentialité")
			ListItem(
				headlineContent   = { Text("Mode discret") },
				supportingContent = { Text("Masque les montants sur tous les écrans. L'œil de la barre du haut fait de même.") },
				trailingContent   = { Switch(checked = inHidden, onCheckedChange = null) },
				colors            = vColors,
				modifier          = Modifier.toggleable(value = inHidden, role = Role.Switch, onValueChange = inOnHidden)
			)
			HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
			SectionTitle("Sécurité")
			UpcomingItem("Verrou biométrique", "Empreinte ou code du téléphone, re-verrouillage après une minute (étape 7).", vColors)
			HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
			SectionTitle("Données")
			UpcomingItem("Exporter", "Fichier JSON complet, rangé où vous voulez (étape 7).", vColors)
			UpcomingItem("Importer", "Remplace toutes les données par celles d'un export (étape 7).", vColors)
		}
	}
}

/*
	Titre d'un groupe de réglages.
	inText : libellé du groupe
*/
@Composable
private fun SectionTitle(inText: String)
{
	Text(
		text     = inText,
		style    = MaterialTheme.typography.titleSmall,
		color    = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier
			.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
			.semantics { heading() }
	)
}

/*
	Réglage pas encore disponible : affiché atténué, sans interaction.
	inTitle  : nom du réglage
	inDetail : ce qu'il fera, et quand
	inColors : couleurs des éléments de liste
*/
@Composable
private fun UpcomingItem(
	inTitle  : String,
	inDetail : String,
	inColors : ListItemColors
)
{
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant				// texte atténué
	ListItem(
		headlineContent   = { Text(inTitle, color = vMuted) },
		supportingContent = { Text(inDetail, color = vMuted) },
		colors            = inColors
	)
}
