package fr.maubin.patrimoineplus

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.maubin.patrimoineplus.data.settings.ThemeMode
import fr.maubin.patrimoineplus.ui.AppViewModel
import fr.maubin.patrimoineplus.ui.PatrimoineApp
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme

/*
	Activité unique : héberge toute l'interface Compose.
*/
@AndroidEntryPoint
class MainActivity : ComponentActivity()
{
	private val fAppViewModel: AppViewModel by viewModels()					// état partagé (mode discret, apparence)

	/*
		Affiche l'interface en plein écran (bord à bord) avec l'apparence
		choisie ; les icônes des barres système suivent cette apparence.
	*/
	override fun onCreate(inSavedInstanceState: Bundle?)
	{
		super.onCreate(inSavedInstanceState)
		enableEdgeToEdge()
		setContent {
			val vMode   by fAppViewModel.themeMode.collectAsStateWithLifecycle()	// apparence choisie
			val vSystem = isSystemInDarkTheme()									// apparence du téléphone
			val vDark   = when (vMode)											// mode sombre effectif
			{
				ThemeMode.SYSTEM -> vSystem
				ThemeMode.LIGHT  -> false
				ThemeMode.DARK   -> true
			}
			DisposableEffect(vDark) {
				val vStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { vDark }	// barres transparentes
				enableEdgeToEdge(statusBarStyle = vStyle, navigationBarStyle = vStyle)
				onDispose {}
			}
			PatrimoineTheme(inDarkTheme = vDark) {
				PatrimoineApp(fAppViewModel)
			}
		}
	}
}
