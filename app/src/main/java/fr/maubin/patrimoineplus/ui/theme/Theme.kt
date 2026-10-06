package fr.maubin.patrimoineplus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val kFallbackLight = lightColorScheme(primary = Color(0xFF2E6B4F))	// palette claire de repli (vert sapin)
private val kFallbackDark  = darkColorScheme(primary = Color(0xFF8FD5B0))	// palette sombre de repli

/*
	Thème Material 3 de l'application : couleurs dynamiques du téléphone
	(Android 12 et plus), palette fixe sinon ; mode sombre selon le système.
	inDarkTheme : force le mode sombre ou clair
	inContent   : contenu affiché sous ce thème
*/
@Composable
fun PatrimoineTheme(
	inDarkTheme : Boolean = isSystemInDarkTheme(),
	inContent   : @Composable () -> Unit
)
{
	val vContext     = LocalContext.current								// contexte, requis par les couleurs dynamiques
	val vColorScheme = when												// palette retenue
	{
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && inDarkTheme -> dynamicDarkColorScheme(vContext)
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.S                -> dynamicLightColorScheme(vContext)
		inDarkTheme                                                   -> kFallbackDark
		else                                                          -> kFallbackLight
	}
	MaterialTheme(colorScheme = vColorScheme, content = inContent)
}
