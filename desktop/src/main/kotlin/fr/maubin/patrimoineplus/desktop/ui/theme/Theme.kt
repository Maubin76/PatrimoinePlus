package fr.maubin.patrimoineplus.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/*
	Thème Material 3 de l'application : monochrome, noir sur blanc, blanc sur
	noir en mode sombre (qui suit le système). Les couleurs dynamiques Material
	You sont volontairement écartées.
	inDarkTheme : force le mode sombre ou clair
	inContent   : contenu affiché sous ce thème
*/
@Composable
fun PatrimoineTheme(
	inDarkTheme : Boolean = isSystemInDarkTheme(),
	inContent   : @Composable () -> Unit
)
{
	MaterialTheme(
		colorScheme = if (inDarkTheme) MonoDarkColors else MonoLightColors,
		typography  = MonoTypography,
		content     = inContent
	)
}
