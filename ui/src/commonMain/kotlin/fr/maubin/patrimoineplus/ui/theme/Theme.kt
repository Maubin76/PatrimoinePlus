package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/*
	Thème de l'application (téléphone et PC) : celui du PC (neutres
	lavande, Outfit, formes arrondies), selon le thème de couleurs choisi
	(Monochrome, Minimal, Coloré), en clair ou en sombre, avec ou sans
	l'effet verre. Les couleurs dynamiques Material You sont volontairement
	écartées. Le fond (AppGround) est posé par l'appelant.
	inDarkTheme  : force le mode sombre ou clair
	inColorTheme : thème de couleurs
	inGlass      : effet verre
	inPalette    : palette d'un thème personnalisé ; null sans
	inContent    : contenu affiché sous ce thème
*/
@Composable
fun PatrimoineTheme(
	inDarkTheme  : Boolean    = isSystemInDarkTheme(),
	inColorTheme : ColorTheme = ColorTheme.MONOCHROME,
	inGlass      : Boolean    = false,
	inPalette    : Palette?   = null,
	inContent    : @Composable () -> Unit
)
{
	DeskTheme(inDarkTheme = inDarkTheme, inColorTheme = inColorTheme, inGlass = inGlass, inPalette = inPalette, inContent = inContent)
}
