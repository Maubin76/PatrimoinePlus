package fr.maubin.patrimoineplus.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import fr.maubin.patrimoineplus.domain.model.CustomTheme
import fr.maubin.patrimoineplus.ui.budget.CashFlowDiagram
import fr.maubin.patrimoineplus.ui.components.PillChoice
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.RadioButton
import fr.maubin.patrimoineplus.ui.theme.Palette
import fr.maubin.patrimoineplus.ui.theme.kColorfulLight
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.widthIn

private val kExportThemes = listOf(ColorTheme.MINIMAL, ColorTheme.COLORFUL)		// thèmes proposés (Monochrome : même schéma que Minimal)

/*
	Prévisualisation avant l'export du cashflow en image : l'image telle
	qu'elle sera enregistrée, et le choix de son apparence (clair ou sombre,
	thème Minimal ou Coloré, Monochrome donnant le même schéma que Minimal ; effet verre, palette par défaut ou thème enregistré), proposée
	d'après les réglages de l'appli. Choisir un thème enregistré passe au thème
	Coloré, dont il prend les rôles. L'image est recalculée à chaque choix.
	inDiagram     : diagramme
	inCenterTitle : nom du nœud du budget
	inHeading     : titre de l'image
	inStart       : apparence proposée (celle de l'appli)
	inThemes      : thèmes enregistrés
	inOnExport    : export dans l'apparence choisie
	inOnDismiss   : fermeture sans exporter
*/
@Composable
fun SankeyExportDialog(
	inDiagram     : CashFlowDiagram,
	inCenterTitle : String,
	inHeading     : String,
	inStart       : SankeyStyle,
	inThemes      : List<CustomTheme>,
	inOnExport    : (SankeyStyle) -> Unit,
	inOnDismiss   : () -> Unit
)
{
	var vStyle   by remember { mutableStateOf(if (inStart.theme == ColorTheme.MONOCHROME) inStart.copy(theme = ColorTheme.MINIMAL) else inStart) }	// apparence choisie (Monochrome : même schéma que Minimal)
	var vPreview by remember { mutableStateOf<ImageBitmap?>(null) }							// image prévisualisée (gardée pendant le calcul suivant)
	var vBusy    by remember { mutableStateOf(true) }											// image en cours de calcul
	LaunchedEffect(vStyle) {
		vBusy    = true
		vPreview = runCatching { withContext(Dispatchers.Default) { renderSankey(inDiagram, inCenterTitle, inHeading, vStyle, 1f).toComposeImageBitmap() } }.getOrNull() ?: vPreview
		vBusy    = false
	}
	Dialog(onDismissRequest = inOnDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
		Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 1080.dp).fillMaxWidth(0.92f)) {
			Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
				Text("Exporter le cashflow", style = MaterialTheme.typography.headlineSmall)
				Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
					Box(
						modifier         = Modifier.weight(1f).height(460.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).semantics { contentDescription = "Aperçu de l’image" },
						contentAlignment = Alignment.Center
					) {
						vPreview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
						if (vBusy && vPreview == null) CircularProgressIndicator()
					}
					Column(Modifier.width(320.dp).height(460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
						Text("Apparence", style = MaterialTheme.typography.titleMedium)
						PillChoice(listOf(false, true), vStyle.dark, { if (it) "Sombre" else "Clair" }, { vStyle = vStyle.copy(dark = it) })
						Spacer(Modifier.height(6.dp))
						Text("Thème", style = MaterialTheme.typography.titleMedium)
						PillChoice(kExportThemes, if (vStyle.palette != null) ColorTheme.COLORFUL else vStyle.theme, { it.label }, { vStyle = vStyle.copy(theme = it, palette = if (it == ColorTheme.COLORFUL) vStyle.palette else null) })
						Spacer(Modifier.height(6.dp))
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text("Effet verre", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
							Switch(checked = vStyle.glass, onCheckedChange = { vStyle = vStyle.copy(glass = it) }, modifier = Modifier.semantics { contentDescription = "Effet verre de l’image" })
						}
						Spacer(Modifier.height(6.dp))
						Text("Palette", style = MaterialTheme.typography.titleMedium)
						PaletteChoice(null, "Par défaut", vStyle.palette == null) { vStyle = vStyle.copy(palette = null) }
						inThemes.forEach { inTheme ->
							PaletteChoice(inTheme, inTheme.name, vStyle.palette?.id == inTheme.id) { vStyle = vStyle.copy(theme = ColorTheme.COLORFUL, palette = inTheme) }
						}
						if (inThemes.isEmpty()) Text("Les thèmes créés dans Réglages › Apparence apparaîtront ici.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
				Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
					TextButton(onClick = inOnDismiss) { Text("Annuler") }
					Button(onClick = { inOnExport(vStyle) }) { Text("Exporter…") }
				}
			}
		}
	}
}

/*
	Choix d'une palette : pastilles de ses couleurs et son nom ; la palette
	choisie est cochée.
	inTheme    : thème enregistré ; null : palette par défaut
	inLabel    : nom affiché
	inSelected : palette choisie
	inOnClick  : choix
*/
@Composable
private fun PaletteChoice(
	inTheme    : CustomTheme?,
	inLabel    : String,
	inSelected : Boolean,
	inOnClick  : () -> Unit
)
{
	Row(
		modifier          = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).selectable(selected = inSelected, onClick = inOnClick, role = Role.RadioButton).padding(horizontal = 8.dp, vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		RadioButton(selected = inSelected, onClick = null)
		Spacer(Modifier.width(8.dp))
		Text(inLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
		paletteSwatches(inTheme).forEach { inColor ->
			Box(Modifier.padding(start = 4.dp).width(14.dp).height(14.dp).clip(RoundedCornerShape(50)).background(inColor))
		}
	}
}

/*
	Couleurs à montrer pour une palette : celles du thème enregistré, sinon
	la palette Indigo du thème Coloré.
	inTheme : thème enregistré ; null : palette par défaut
*/
private fun paletteSwatches(inTheme: CustomTheme?): List<Color> =
	(inTheme?.let { Palette.of(it.colors) } ?: kColorfulLight).ranks
