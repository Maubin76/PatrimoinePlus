package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

// Teintes de répartition du thème Coloré, dans l'ordre des rangs (la plus grosse
// part d'abord) ; validées : voisins séparés (ΔE ≥ 15, daltonisme compris), les
// deux orangés jamais côte à côte ; chaque teinte est doublée d'une légende.
val kColorfulLight = listOf(kDeepSpace, kAmber, kBlueGreen, kTiger, kSkyBlue)		// mode clair
val kColorfulDark  = listOf(kSkyBlue, kTiger, kBlueGreen, kAmber, Color(0xFFE8F3F9))	// mode sombre (toutes ≥ 3:1 sur le fond)
