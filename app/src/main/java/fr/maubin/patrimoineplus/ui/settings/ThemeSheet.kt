package fr.maubin.patrimoineplus.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.calc.contrastWarning
import fr.maubin.patrimoineplus.domain.calc.kThemeRoles
import fr.maubin.patrimoineplus.domain.calc.parseHexColor
import fr.maubin.patrimoineplus.domain.model.CustomTheme
import fr.maubin.patrimoineplus.ui.components.FormSheet
import fr.maubin.patrimoineplus.ui.components.TextInput
import fr.maubin.patrimoineplus.ui.components.ThemePreview
import fr.maubin.patrimoineplus.ui.components.ThemeSwatches
import fr.maubin.patrimoineplus.ui.theme.Palette

// Couleurs de départ d'un nouveau thème : celles du thème Coloré, dans l'ordre des rôles.
private val kStarterColors = listOf("#4B3FCE", "#0F9A80", "#B47D0A", "#B8294F", "#5B86E6")

/*
	Feuille de création ou de modification d'un thème de couleurs : nom, une
	couleur par rôle en hexadécimal (pastille, code illisible signalé, alerte
	si peu lisible sur le fond clair ou sombre), aperçu en direct, puis
	enregistrement ou suppression.
	inTheme     : thème modifié ; null pour en créer un
	inOnSave    : enregistre (nom, couleurs) et renvoie le refus par rappel (null si enregistré)
	inOnDelete  : supprime le thème modifié
	inOnDismiss : fermeture
*/
@Composable
fun ThemeSheet(
	inTheme     : CustomTheme?,
	inOnSave    : (String, List<String>, (String?) -> Unit) -> Unit,
	inOnDelete  : () -> Unit,
	inOnDismiss : () -> Unit
)
{
	var vName   by remember(inTheme) { mutableStateOf(inTheme?.name.orEmpty()) }				// nom saisi
	var vColors by remember(inTheme) { mutableStateOf(inTheme?.colors ?: kStarterColors) }		// codes saisis
	var vError  by remember(inTheme) { mutableStateOf<String?>(null) }						// refus affiché
	val vPalette = Palette.of(vColors)																// palette prévisualisée ; null si un code est illisible
	FormSheet(if (inTheme == null) "Nouveau thème" else "Modifier le thème", inOnDismiss, inDirty = vName != inTheme?.name.orEmpty() || vColors != (inTheme?.colors ?: kStarterColors)) {
		vError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		TextInput(vName, { vName = it }, "Nom du thème")
		kThemeRoles.forEachIndexed { inIndex, inRole ->
			val vText  = vColors[inIndex]															// code saisi
			val vColor = parseHexColor(vText)														// couleur lue ; null si illisible
			Row(verticalAlignment = Alignment.CenterVertically) {
				ThemeSwatches(listOf(vText), 28.dp)
				Spacer(Modifier.width(12.dp))
				Box(Modifier.weight(1f)) {
					TextInput(
						inValue    = vText,
						inOnChange = { inNew -> vColors = vColors.toMutableList().also { it[inIndex] = inNew.take(9) } },
						inLabel    = inRole,
						inError    = if (vColor == null) "Code illisible (ex. #219EBC)." else null,
						inSupport  = vColor?.let(::contrastWarning)
					)
				}
			}
		}
		Text("Aperçu", style = MaterialTheme.typography.titleMedium)
		if (vPalette != null) ThemePreview(vPalette)
		else Text("Corrigez les codes illisibles pour voir l’aperçu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
			if (inTheme != null) OutlinedButton(onClick = inOnDelete) { Text("Supprimer") }
			Button(onClick = { inOnSave(vName, vColors) { vError = it } }) { Text("Enregistrer") }
		}
	}
}
