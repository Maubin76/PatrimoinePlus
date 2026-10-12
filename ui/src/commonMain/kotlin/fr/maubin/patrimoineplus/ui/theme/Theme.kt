package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/*
	Thème Material 3 de l'application : monochrome, noir sur blanc, blanc sur
	noir en mode sombre (qui suit le système). Les couleurs dynamiques Material
	You sont volontairement écartées.
	Hausses et baisses en vert et rouge seulement sur demande (inTrendColors).
	inDarkTheme   : force le mode sombre ou clair
	inTrendColors : colore les hausses et les baisses
	inContent     : contenu affiché sous ce thème
*/
@Composable
fun PatrimoineTheme(
	inDarkTheme   : Boolean = isSystemInDarkTheme(),
	inTrendColors : Boolean = false,
	inContent     : @Composable () -> Unit
)
{
	MaterialTheme(
		colorScheme = if (inDarkTheme) MonoDarkColors else MonoLightColors,
		typography  = monoTypography()
	) {
		CompositionLocalProvider(LocalTrendColors provides inTrendColors, content = inContent)
	}
}
