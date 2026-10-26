package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import fr.maubin.patrimoineplus.domain.calc.parseHexColor
import fr.maubin.patrimoineplus.domain.calc.kThemeColors

/*
	Thème de couleurs de l'application (téléphone et PC), indépendant de
	l'apparence claire ou sombre.
	label  : libellé affiché dans les réglages
	detail : explication affichée sous le choix
*/
enum class ColorTheme(
	val label  : String,
	val detail : String
)
{
	MONOCHROME("Monochrome", "Tout en noir et blanc ; le signe + ou − des variations reste écrit."),
	MINIMAL("Minimal", "Noir et blanc, avec les hausses en vert et les baisses en rouge."),
	COLORFUL("Coloré", "Bleu ciel, bleu-vert, bleu nuit, ambre et orange dans toute l’appli ; hausses en bleu-vert, baisses en orange.")
}

// Thème de couleurs en cours, fourni par PatrimoineTheme.
val LocalColorTheme = staticCompositionLocalOf { ColorTheme.MONOCHROME }

// Palette « Refreshing Summer Fun » du thème Coloré.
val kSkyBlue    = Color(0xFF8ECAE6)												// bleu ciel
val kBlueGreen  = Color(0xFF219EBC)												// bleu-vert
val kDeepSpace  = Color(0xFF023047)												// bleu nuit
val kAmber      = Color(0xFFFFB703)												// ambre
val kTiger      = Color(0xFFFB8500)												// orange

/*
	Palette d'un thème personnalisé : une couleur par rôle (voir
	kThemeRoles), utilisée comme celle du thème Coloré, en clair comme en
	sombre.
	main   : principale (chiffre mis en avant, immobilier, courbes)
	rise   : hausses et entrées (comptes courants)
	invest : investissements (épargne)
	fall   : baisses et sorties (bourse)
	second : secondaire (collection)
*/
data class Palette(
	val main   : Color,
	val rise   : Color,
	val invest : Color,
	val fall   : Color,
	val second : Color
)
{
	// Teintes des répartitions, dans l'ordre des rangs (comme le thème Coloré).
	val ranks: List<Color>
		get() = listOf(main, invest, rise, fall, second)

	companion object
	{
		/*
			Palette tirée des cinq couleurs d'un thème ; null si l'une est illisible.
			inColors : couleurs « #RRGGBB », dans l'ordre des rôles
		*/
		fun of(inColors: List<String>): Palette?
		{
			val vColors = inColors.map { parseHexColor(it)?.let { inArgb -> Color(inArgb.toInt()) } ?: return null }	// couleurs lues
			return if (vColors.size == kThemeColors) Palette(vColors[0], vColors[1], vColors[2], vColors[3], vColors[4]) else null
		}
	}
}

// Palette d'un thème personnalisé actif ; null : palette du thème Coloré.
val LocalPalette = staticCompositionLocalOf<Palette?> { null }

/*
	Accent principal du thème Coloré (bleu nuit, bleu ciel sur fond sombre) ou
	du thème personnalisé : chiffre mis en avant, courbes ; indépendant de
	l'encre des boutons.
*/
@Composable
fun colorfulMain(): Color =
	LocalPalette.current?.main ?: if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) kSkyBlue else kDeepSpace

// Teintes de répartition du thème Coloré, dans l'ordre des rangs (la plus grosse
// part d'abord) ; validées : voisins séparés (ΔE ≥ 15, daltonisme compris), les
// deux orangés jamais côte à côte ; chaque teinte est doublée d'une légende.
val kColorfulLight = listOf(kDeepSpace, kAmber, kBlueGreen, kTiger, kSkyBlue)		// mode clair
val kColorfulDark  = listOf(kSkyBlue, kTiger, kBlueGreen, kAmber, Color(0xFFE8F3F9))	// mode sombre (toutes ≥ 3:1 sur le fond)
