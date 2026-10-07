package fr.maubin.patrimoineplus.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.maubin.patrimoineplus.ui.accounts.AccountsScreen
import fr.maubin.patrimoineplus.ui.categories.CategoriesScreen
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.home.HomeScreen
import fr.maubin.patrimoineplus.ui.navigation.AccountsRoute
import fr.maubin.patrimoineplus.ui.navigation.CategoriesRoute
import fr.maubin.patrimoineplus.ui.navigation.HomeRoute
import fr.maubin.patrimoineplus.ui.navigation.SettingsRoute
import fr.maubin.patrimoineplus.ui.navigation.TopLevelDestination
import fr.maubin.patrimoineplus.ui.settings.SettingsScreen
import fr.maubin.patrimoineplus.ui.shell.AppFrame

private const val kFadeMillis = 200											// fondu enchaîné entre deux écrans

/*
	Coque de l'application : mode discret partagé, cadre avec la pilule de
	navigation flottante, et graphe de navigation entre les onglets.
	inAppViewModel : état partagé (mode discret)
*/
@Composable
fun PatrimoineApp(inAppViewModel: AppViewModel = hiltViewModel())
{
	val vNavController = rememberNavController()							// contrôleur de navigation
	val vBackStack     by vNavController.currentBackStackEntryAsState()	// entrée affichée
	val vCurrent       = vBackStack?.destination							// destination courante
	val vSelected      = TopLevelDestination.entries.firstOrNull { vCurrent?.hasRoute(it.route::class) == true }	// onglet actif
	val vHidden        by inAppViewModel.amountsHidden.collectAsStateWithLifecycle()	// mode discret
	val vToggle        = inAppViewModel::toggleAmountsHidden				// bascule du mode discret
	val vThemeMode     by inAppViewModel.themeMode.collectAsStateWithLifecycle()	// apparence choisie
	val vSnackbar      = remember { SnackbarHostState() }					// messages éphémères

	CompositionLocalProvider(LocalAmountsHidden provides vHidden) {
		AppFrame(
			inSelected = vSelected,
			inOnSelect = { inDestination -> navigateTopLevel(vNavController, inDestination) },
			inSnackbar = vSnackbar
		) {
			NavHost(
				navController      = vNavController,
				startDestination   = HomeRoute,
				enterTransition    = { fadeIn(tween(kFadeMillis)) },
				exitTransition     = { fadeOut(tween(kFadeMillis)) },
				popEnterTransition = { fadeIn(tween(kFadeMillis)) },
				popExitTransition  = { fadeOut(tween(kFadeMillis)) }
			) {
				composable<HomeRoute> {
					HomeScreen(
						inOnToggleHidden   = vToggle,
						inOnManageAccounts = { navigateTopLevel(vNavController, TopLevelDestination.Accounts) }
					)
				}
				composable<AccountsRoute> { AccountsScreen(vToggle) }
				composable<CategoriesRoute> { CategoriesScreen() }
				composable<SettingsRoute> {
					SettingsScreen(
						inThemeMode   = vThemeMode,
						inOnThemeMode = inAppViewModel::setThemeMode,
						inHidden      = vHidden,
						inOnHidden    = inAppViewModel::setAmountsHidden
					)
				}
			}
		}
	}
}

/*
	Ouvre un onglet en conservant l'état des autres, sans empiler de doublon.
	inNavController : contrôleur de navigation
	inDestination   : onglet à ouvrir
*/
private fun navigateTopLevel(
	inNavController : NavHostController,
	inDestination   : TopLevelDestination
)
{
	inNavController.navigate(inDestination.route) {
		popUpTo(inNavController.graph.findStartDestination().id) { saveState = true }
		launchSingleTop = true
		restoreState    = true
	}
}
