package fr.maubin.patrimoineplus

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.maubin.patrimoineplus.data.settings.ThemeMode
import fr.maubin.patrimoineplus.ui.AppViewModel
import fr.maubin.patrimoineplus.ui.PatrimoineApp
import fr.maubin.patrimoineplus.ui.lock.AppLock
import fr.maubin.patrimoineplus.ui.lock.LockScreen
import fr.maubin.patrimoineplus.ui.lock.LockState
import fr.maubin.patrimoineplus.ui.lock.authenticate
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme
import javax.inject.Inject

/*
	Activité unique : héberge toute l'interface Compose. Activité « fragment »
	pour pouvoir afficher la demande d'authentification du système.
*/
@AndroidEntryPoint
class MainActivity : FragmentActivity()
{
	private val fAppViewModel: AppViewModel by viewModels()					// état partagé (mode discret, apparence)
	@Inject lateinit var fAppLock: AppLock										// verrou de l'application

	/*
		Affiche l'interface en plein écran (bord à bord) avec l'apparence
		choisie ; les icônes des barres système suivent cette apparence.
		L'écran de verrouillage se pose par-dessus le contenu (qui garde son
		état) tant que le verrou l'exige.
	*/
	override fun onCreate(inSavedInstanceState: Bundle?)
	{
		super.onCreate(inSavedInstanceState)
		enableEdgeToEdge()
		setContent {
			val vMode    by fAppViewModel.themeMode.collectAsStateWithLifecycle()	// apparence choisie
			val vLock    by fAppLock.state.collectAsStateWithLifecycle()			// état du verrou
			var vFailure by remember { mutableStateOf<String?>(null) }			// dernier échec d'authentification
			val vSystem  = isSystemInDarkTheme()									// apparence du téléphone
			val vDark    = when (vMode)											// mode sombre effectif
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
				Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
					if (vLock != LockState.UNKNOWN)
					{
						Box(if (vLock == LockState.LOCKED) Modifier.clearAndSetSemantics {} else Modifier) {
							PatrimoineApp(fAppViewModel)
						}
					}
					if (vLock == LockState.LOCKED)
					{
						LockScreen(vFailure) {
							authenticate(
								inActivity  = this@MainActivity,
								inTitle     = "Déverrouiller PatrimoinePlus",
								inOnSuccess = {
									vFailure = null
									fAppLock.unlock()
								},
								inOnFailure = { vFailure = it }
							)
						}
					}
				}
			}
		}
	}
}
