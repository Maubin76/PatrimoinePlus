package fr.maubin.patrimoineplus

import android.graphics.Color
import android.os.Build
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.maubin.patrimoineplus.ui.theme.isDark
import fr.maubin.patrimoineplus.ui.AppViewModel
import fr.maubin.patrimoineplus.ui.PatrimoineApp
import fr.maubin.patrimoineplus.ui.lock.AppLock
import fr.maubin.patrimoineplus.ui.lock.LockScreen
import fr.maubin.patrimoineplus.ui.lock.LockState
import fr.maubin.patrimoineplus.ui.lock.authenticate
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme
import javax.inject.Inject
import fr.maubin.patrimoineplus.ui.theme.DeskTheme
import fr.maubin.patrimoineplus.ui.components.AppGround
import fr.maubin.patrimoineplus.ui.theme.Palette

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
		(1,5 s au plus). Tant que l'écran de connexion est affiché, le contenu
		n'est pas composé : aucune feuille ni boîte de dialogue (fenêtres à
		part) ne peut rester visible ou utilisable par-dessus le verrou. Son
		état sauvegardable (navigation…) est conservé, et il réapparaît sous
		l'écran de connexion qui s'efface en fondu. Verrou activé : pas d'aperçu
		du contenu dans les applis récentes.
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
			val vColors  by fAppViewModel.colorTheme.collectAsStateWithLifecycle()	// thème de couleurs
			val vGlass   by fAppViewModel.glassEffect.collectAsStateWithLifecycle()	// effet verre
			val vThemes  by fAppViewModel.themes.collectAsStateWithLifecycle()		// thèmes créés
			val vCustom  by fAppViewModel.customTheme.collectAsStateWithLifecycle()	// thème personnalisé choisi
			val vPalette = vThemes.firstOrNull { it.id == vCustom }?.let { Palette.of(it.colors) }	// sa palette ; null sans
			val vLock    by fAppLock.state.collectAsStateWithLifecycle()			// état du verrou
			val vAuth    by fAppLock.authRequired.collectAsStateWithLifecycle()		// verrou activé
			val vIntro   by fAppLock.introPending.collectAsStateWithLifecycle()		// animation du lancement à jouer
			val vKept    = rememberSaveableStateHolder()							// état du contenu pendant le verrouillage
			var vFailure by remember { mutableStateOf<String?>(null) }			// dernier échec d'authentification
			val vDark    = vMode.isDark()											// mode sombre effectif
			DisposableEffect(vDark) {
				val vStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { vDark }	// barres transparentes
				enableEdgeToEdge(statusBarStyle = vStyle, navigationBarStyle = vStyle)
				onDispose {}
			}
			DisposableEffect(vAuth) {
				setRecentsScreenshotEnabled(!vAuth)
				onDispose {}
			}
			DeskTheme(inDarkTheme = vDark, inColorTheme = vColors, inGlass = vGlass, inPalette = vPalette) {
				Box(Modifier.fillMaxSize()) {
					AppGround(vDark, vGlass)												// fond pastel, ou formes floues derrière le verre
					if (vLock == LockState.UNLOCKED)
					{
						vKept.SaveableStateProvider("contenu") {
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
