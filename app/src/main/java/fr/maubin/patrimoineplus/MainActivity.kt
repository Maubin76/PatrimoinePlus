package fr.maubin.patrimoineplus

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.SystemClock
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

private const val kSplashMaxMillis = 1_500L									// attente maximale sous l'écran de démarrage système
private const val kFadeMillis      = 320										// effacement de l'écran de connexion

/*
	Activité unique : héberge toute l'interface Compose. Activité « fragment »
	pour pouvoir afficher la demande d'authentification du système.
*/
@AndroidEntryPoint
class MainActivity : FragmentActivity()
{
	private val fAppViewModel: AppViewModel by viewModels()					// état partagé (mode discret, apparence)
	@Inject lateinit var fAppLock: AppLock										// verrou de l'application
	@Volatile private var fIntroReady = false									// animation du lancement prête à prendre le relais

	/*
		Affiche l'interface en plein écran (bord à bord) avec l'apparence
		choisie ; les icônes des barres système suivent cette apparence.
		L'écran de démarrage système (pastille du logo) reste affiché jusqu'à ce
		que l'animation du lancement puisse prendre le relais au même endroit
		(1,5 s au plus). L'écran de connexion se pose par-dessus le contenu (qui
		garde son état) et s'efface en fondu une fois franchi.
	*/
	override fun onCreate(inSavedInstanceState: Bundle?)
	{
		val vSplash = installSplashScreen()										// écran de démarrage système
		val vStart  = SystemClock.uptimeMillis()								// début du lancement
		vSplash.setKeepOnScreenCondition {
			val vWaiting = fAppLock.state.value == LockState.UNKNOWN || (fAppLock.introPending.value && !fIntroReady)	// relais pas encore prêt
			vWaiting && SystemClock.uptimeMillis() - vStart < kSplashMaxMillis
		}
		vSplash.setOnExitAnimationListener { it.remove() }
		super.onCreate(inSavedInstanceState)
		enableEdgeToEdge()
		setContent {
			val vMode    by fAppViewModel.themeMode.collectAsStateWithLifecycle()	// apparence choisie
			val vLock    by fAppLock.state.collectAsStateWithLifecycle()			// état du verrou
			val vAuth    by fAppLock.authRequired.collectAsStateWithLifecycle()		// verrou activé
			val vIntro   by fAppLock.introPending.collectAsStateWithLifecycle()		// animation du lancement à jouer
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
					AnimatedVisibility(visible = vLock == LockState.LOCKED, enter = EnterTransition.None, exit = fadeOut(tween(kFadeMillis))) {
						LockScreen(
							inAuth        = vAuth,
							inIntro       = vIntro,
							inMessage     = vFailure,
							inOnUnlock    = {
								if (vAuth)
								{
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
								else
								{
									fAppLock.unlock()
								}
							},
							inOnIntroDone = fAppLock::introDone,
							inOnReady     = { fIntroReady = true }
						)
					}
				}
			}
		}
	}
}
