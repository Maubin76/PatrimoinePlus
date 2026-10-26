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
		SectionHeader(inTitle = "Catégories")
		Spacer(Modifier.height(4.dp))
		Text("Dépenses et revenus : ajout, modification (clic), ordre (flèches).", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
		Spacer(Modifier.height(12.dp))
		CategoriesSection(inSpace, inController)
	}
}
