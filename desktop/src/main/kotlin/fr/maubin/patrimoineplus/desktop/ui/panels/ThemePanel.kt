package fr.maubin.patrimoineplus.desktop.ui.panels

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.domain.calc.contrastWarning
import fr.maubin.patrimoineplus.domain.calc.kThemeRoles
import fr.maubin.patrimoineplus.domain.calc.parseHexColor
import fr.maubin.patrimoineplus.ui.components.ThemePreview
import fr.maubin.patrimoineplus.ui.components.ThemeSwatches
import fr.maubin.patrimoineplus.ui.theme.Palette

// Couleurs de départ d'un nouveau thème : celles du thème Coloré, dans l'ordre des rôles.
val kStarterColors = listOf("#4B3FCE", "#0F9A80", "#B47D0A", "#B8294F", "#5B86E6")

/*
	Création ou modification d'un thème de couleurs : nom, puis une couleur
	par rôle en hexadécimal (pastille, code illisible signalé, alerte si peu
	lisible sur le fond clair ou sombre) ; aperçu en direct dès que les cinq
	codes sont lisibles ; suppression d'un thème existant.
	inController : contrôleur
	inThemeId    : thème modifié ; null pour en créer un
*/
@Composable
fun ThemePanel(
	inController : DesktopController,
	inThemeId    : String?
)
{
	val vSpace  by inController.workspace.collectAsState()							// données
	val vTheme  = inThemeId?.let { inId -> vSpace.data.themes.firstOrNull { it.id == inId } }	// thème modifié
	var vName   by remember(inThemeId) { mutableStateOf(vTheme?.name.orEmpty()) }			// nom saisi
	var vColors by remember(inThemeId) { mutableStateOf(vTheme?.colors ?: kStarterColors) }	// codes saisis
	var vError  by remember(inThemeId) { mutableStateOf<String?>(null) }				// refus affiché
	val vPalette = Palette.of(vColors)													// palette prévisualisée ; null si un code est illisible

	/*
		Enregistre le thème.
	*/
	fun save()
	{
		if (inController.panel.value == null) return
		when (val vResult = inController.saveTheme(inThemeId, vName, vColors))
		{
			is EditResult.Done    -> inController.closePanel()
			is EditResult.Refused -> vError = vResult.messages.joinToString(" ")
		}
	}

	SidePanel(
		inTitle   = if (vTheme == null) "Nouveau thème" else "Modifier le thème",
		inOnClose = inController::closePanel,
		inDirty   = vName != vTheme?.name.orEmpty() || vColors != (vTheme?.colors ?: kStarterColors),
		inOnEnter = ::save,
		inActions = {
			if (vTheme != null)
			{
				OutlinedButton(onClick = { inController.deleteTheme(vTheme.id); inController.closePanel() }) { Text("Supprimer") }
			}
			Button(onClick = ::save) { Text("Enregistrer") }
		}
	) {
		FormError(vError)
		FormField(vName, { vName = it }, "Nom du thème")
		Spacer(Modifier.height(4.dp))
		kThemeRoles.forEachIndexed { inIndex, inRole ->
			val vText  = vColors[inIndex]															// code saisi
			val vColor = parseHexColor(vText)														// couleur lue ; null si illisible
			Row(verticalAlignment = Alignment.CenterVertically) {
				ThemeSwatches(listOf(vText), 28.dp)
				Spacer(Modifier.width(12.dp))
				Row(Modifier.weight(1f)) {
					FormField(
						inValue    = vText,
						inOnChange = { inNew -> vColors = vColors.toMutableList().also { it[inIndex] = inNew.take(9) } },
						inLabel    = inRole,
						inError    = if (vColor == null) "Code illisible (ex. #219EBC)." else null,
						inSupport  = vColor?.let(::contrastWarning)
					)
				}
			}
		}
		Spacer(Modifier.height(8.dp))
		Text("Aperçu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
		if (vPalette != null) ThemePreview(vPalette)
		else Text("Corrigez les codes illisibles pour voir l’aperçu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}
