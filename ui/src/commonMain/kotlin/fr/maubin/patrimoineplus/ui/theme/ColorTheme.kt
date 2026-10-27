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
	// d'abord) : voisins bien séparés, daltonisme compris, pour la palette du
	// thème Coloré.
	val ranks: List<Color>
		get() = listOf(main, rise, invest, fall, second)

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

// Palette « Indigo » du thème Coloré, assortie au violet du logo et aux fonds
// lavande et nuit indigo : indigo (principale), menthe (hausses), ambre
// (placements), corail (baisses), pervenche (secondaire). Validée : toutes
// ≥ 3:1 sur le blanc (clair) et sur les panneaux sombres ; voisins de
// répartition séparés (ΔE OKLab ≥ 10 sous daltonisme, ≥ 18 sinon) ; menthe et
// corail aussi séparés en deutéranopie (luminosités écartées). Chaque teinte
// reste doublée d'un libellé ou d'un signe.
val kColorfulLight = Palette(
	main   = Color(0xFF4B3FCE),
	rise   = Color(0xFF0F9A80),
	invest = Color(0xFFB47D0A),
	fall   = Color(0xFFB8294F),
	second = Color(0xFF5B86E6)
)																				// fonds clairs
val kColorfulDark  = Palette(
	main   = Color(0xFFB0A6FF),
	rise   = Color(0xFF4FDDB8),
	invest = Color(0xFFF2BE5C),
	fall   = Color(0xFFE0577E),
	second = Color(0xFF86A9FF)
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
