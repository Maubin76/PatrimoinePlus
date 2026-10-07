package fr.maubin.patrimoineplus.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

private const val kFadeInMillis  = 220										// apparition d'un onglet
private const val kFadeOutMillis = 90										// disparition d'un onglet

/*
	Coque de l'application : mode discret partagé, barre de navigation du bas
	séparée du contenu par un filet, et graphe de navigation entre les onglets.
	inAppViewModel : état partagé (mode discret)
*/
@Composable
fun PatrimoineApp(inAppViewModel: AppViewModel = hiltViewModel())
{
	val vNavController = rememberNavController()							// contrôleur de navigation
	val vBackStack     by vNavController.currentBackStackEntryAsState()	// entrée affichée
	val vCurrent       = vBackStack?.destination							// destination courante
	val vHidden        by inAppViewModel.amountsHidden.collectAsStateWithLifecycle()	// mode discret
	val vToggle        = inAppViewModel::toggleAmountsHidden				// bascule du mode discret

	CompositionLocalProvider(LocalAmountsHidden provides vHidden) {
		Scaffold(
			containerColor = MaterialTheme.colorScheme.surface,
			bottomBar      = {
				PatrimoineBottomBar(
					inIsSelected = { inDestination -> vCurrent?.hasRoute(inDestination.route::class) == true },
					inOnSelect   = { inDestination -> navigateTopLevel(vNavController, inDestination) }
				)
			}
		) { inPadding ->
			NavHost(
				navController      = vNavController,
				startDestination   = HomeRoute,
				modifier           = Modifier.padding(inPadding).consumeWindowInsets(inPadding),
				enterTransition    = { fadeIn(tween(kFadeInMillis, delayMillis = kFadeOutMillis)) },
				exitTransition     = { fadeOut(tween(kFadeOutMillis)) },
				popEnterTransition = { fadeIn(tween(kFadeInMillis, delayMillis = kFadeOutMillis)) },
				popExitTransition  = { fadeOut(tween(kFadeOutMillis)) }
			) {
				composable<HomeRoute> {
					HomeScreen(
						inOnToggleHidden   = vToggle,
						inOnCreateAccounts = { navigateTopLevel(vNavController, TopLevelDestination.Accounts) }
					)
				}
				composable<AccountsRoute> { AccountsScreen(vToggle) }
				composable<CategoriesRoute> { CategoriesScreen() }
				composable<SettingsRoute> {
					SettingsScreen(inHidden = vHidden, inOnHidden = inAppViewModel::setAmountsHidden)
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

/*
	Barre de navigation du bas, posée sur le fond de l'écran et séparée du
	contenu par un filet plutôt que par une teinte.
	inIsSelected : indique si un onglet est l'onglet courant
	inOnSelect   : ouvre un onglet
*/
@Composable
fun PatrimoineBottomBar(
	inIsSelected : (TopLevelDestination) -> Boolean,
	inOnSelect   : (TopLevelDestination) -> Unit
)
{
	Column {
		HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
		NavigationBar(
			containerColor = MaterialTheme.colorScheme.surface,
			tonalElevation = 0.dp
		) {
			TopLevelDestination.entries.forEach { inDestination ->
				NavigationBarItem(
					selected = inIsSelected(inDestination),
					onClick  = { inOnSelect(inDestination) },
					icon     = { Icon(inDestination.icon, contentDescription = null) },
					label    = { Text(inDestination.label) },
					colors   = NavigationBarItemDefaults.colors(
						indicatorColor    = MaterialTheme.colorScheme.secondaryContainer,
						selectedIconColor = MaterialTheme.colorScheme.onSurface,
						selectedTextColor = MaterialTheme.colorScheme.onSurface
					)
				)
			}
		}
	}
}
