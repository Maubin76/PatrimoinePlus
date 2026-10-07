package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/*
	Thème Material 3 de l'application, palette fixe « Calculatrice Braun ».
	Les couleurs dynamiques Material You sont volontairement écartées : le
	jaune de la touche « = » doit rester le même quel que soit le fond d'écran.
	Le mode sombre suit le système.
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
		colorScheme = if (inDarkTheme) BraunDarkColors else BraunLightColors,
		typography  = BraunTypography,
		content     = inContent
	)
}
