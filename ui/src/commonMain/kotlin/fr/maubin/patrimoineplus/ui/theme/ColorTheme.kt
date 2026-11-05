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
	COLORFUL("Coloré", "Indigo, menthe, ambre, corail et pervenche dans toute l’appli, assortis au logo ; hausses en menthe, baisses en corail.")
}

// Thème de couleurs en cours, fourni par PatrimoineTheme.
val LocalColorTheme = staticCompositionLocalOf { ColorTheme.MONOCHROME }


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
	// Teintes des répartitions, dans l'ordre des rangs (la plus grosse part
	// d'abord) : orange, bleu, vert, bleu ciel, rouge ; l'orange et le vert,
	// proches pour un daltonien, ne sont jamais voisins.
	val ranks: List<Color>
		get() = listOf(invest, main, rise, second, fall)

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

// Palette de la brume lumineuse, tirée de la référence : bleu (principale,
// immobilier, courbes), vert (hausses, comptes courants), orange (placements,
// épargne), rouge (baisses), bleu ciel (secondaire, bourse). Calculée dans
// l'ordre des répartitions (voir ranks) : voisins séparés d'un ΔE OKLab ≥ 20
// en vision normale et ≥ 19 en deutéranopie et protanopie (seule paire faible :
// bleu et vert en tritanopie, très rare) ; en sombre, tous ≥ 5:1 sur les
// panneaux. Chaque teinte reste doublée d'un libellé ou d'un signe.
val kColorfulLight = Palette(
	main   = Color(0xFF2F7FD9),
	rise   = Color(0xFF2A9149),
	invest = Color(0xFFE46E12),
	fall   = Color(0xFFD8433B),
	second = Color(0xFF2FA3DB)
)																				// fonds clairs
val kColorfulDark  = Palette(
	main   = Color(0xFF5EA2F0),
	rise   = Color(0xFF4CC774),
	invest = Color(0xFFF7984A),
	fall   = Color(0xFFF06A60),
	second = Color(0xFF7FCDF2)
)																				// fonds sombres

/*
	Palette en cours dans le thème Coloré : celle du thème personnalisé
	choisi, sinon la palette Indigo du mode clair ou sombre.
*/
@Composable
fun colorfulPalette(): Palette =
	LocalPalette.current ?: if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) kColorfulDark else kColorfulLight

/*
	Accent principal du thème Coloré ou du thème personnalisé : chiffre mis en
	avant, courbes ; indépendant de l'encre des boutons.
*/
@Composable
fun colorfulMain(): Color = colorfulPalette().main
