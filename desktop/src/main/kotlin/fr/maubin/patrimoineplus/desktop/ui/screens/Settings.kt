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

/*
	Réglages de l'appli PC : source des données (dossier synchronisé,
	démonstration), apparence, mode discret, catégories.
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
		Text("Réglages", style = MaterialTheme.typography.headlineMedium)
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Données")
		Spacer(Modifier.height(12.dp))
		BorderedCard {
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
		SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
			ThemeMode.entries.forEachIndexed { inIndex, inEntry ->
				SegmentedButton(
					selected = inEntry == vSettings.appearance,
					onClick  = { inController.setAppearance(inEntry) },
					shape    = SegmentedButtonDefaults.itemShape(index = inIndex, count = ThemeMode.entries.size),
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
		Spacer(Modifier.height(12.dp))
		BorderedCard {
			Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text("Hausses en vert, baisses en rouge", style = MaterialTheme.typography.titleMedium)
					Text("Colore les variations et les plus-values. Sinon, tout reste en noir et blanc ; le signe + ou − est toujours affiché.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				}
				Switch(checked = vSettings.trend, onCheckedChange = inController::setTrend)
			}
		}
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Confidentialité")
		Spacer(Modifier.height(12.dp))
		BorderedCard {
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
