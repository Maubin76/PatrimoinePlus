package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/*
	Thème Material 3 de l'application, selon le thème de couleurs choisi :
	Monochrome (noir sur blanc, blanc sur noir), Minimal (même palette, hausses
	en vert et baisses en rouge) ou Coloré (palette « Refreshing Summer Fun »
	sur toute l'interface). Chacun existe en clair et en sombre. Les couleurs
	dynamiques Material You sont volontairement écartées.
	inDarkTheme  : force le mode sombre ou clair
	inColorTheme : thème de couleurs
	inContent    : contenu affiché sous ce thème
*/
@Composable
fun PatrimoineTheme(
	inDarkTheme  : Boolean    = isSystemInDarkTheme(),
	inColorTheme : ColorTheme = ColorTheme.MONOCHROME,
	inContent    : @Composable () -> Unit
)
{
	MaterialTheme(
		colorScheme = when
		{
			inColorTheme == ColorTheme.COLORFUL -> if (inDarkTheme) ColorfulDarkColors else ColorfulLightColors
			else                                -> if (inDarkTheme) MonoDarkColors else MonoLightColors
		},
		typography  = monoTypography()
	) {
		CompositionLocalProvider(LocalColorTheme provides inColorTheme, content = inContent)
	}
}
