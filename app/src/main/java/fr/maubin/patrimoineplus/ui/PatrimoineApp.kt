package fr.maubin.patrimoineplus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.maubin.patrimoineplus.ui.home.HomeScreen
import fr.maubin.patrimoineplus.ui.navigation.AccountsRoute
import fr.maubin.patrimoineplus.ui.navigation.CategoriesRoute
import fr.maubin.patrimoineplus.ui.navigation.HomeRoute
import fr.maubin.patrimoineplus.ui.navigation.SettingsRoute
import fr.maubin.patrimoineplus.ui.navigation.TopLevelDestination

/*
	Structure générale de l'interface : barre de navigation du bas et
	graphe de navigation entre les écrans principaux.
*/
@Composable
fun PatrimoineApp()
{
	val vNavController = rememberNavController()							// contrôleur de navigation
	val vBackStack     by vNavController.currentBackStackEntryAsState()	// entrée affichée
	val vCurrent       = vBackStack?.destination							// destination courante

	Scaffold(
		bottomBar = {
			NavigationBar {
				TopLevelDestination.entries.forEach { inDestination ->
					NavigationBarItem(
						selected = vCurrent?.hasRoute(inDestination.route::class) == true,
						onClick  = {
							vNavController.navigate(inDestination.route) {
								popUpTo(vNavController.graph.findStartDestination().id) { saveState = true }
								launchSingleTop = true
								restoreState    = true
							}
						},
						icon     = { Icon(inDestination.icon, contentDescription = null) },
						label    = { Text(inDestination.label) }
					)
				}
			}
		}
	) { inPadding ->
		NavHost(
			navController    = vNavController,
			startDestination = HomeRoute,
			modifier         = Modifier.padding(inPadding)
		) {
			composable<HomeRoute> { HomeScreen() }
			composable<AccountsRoute> { PlaceholderScreen("Comptes", "Gestion des comptes : étape 3.") }
			composable<CategoriesRoute> { PlaceholderScreen("Catégories", "Gestion des catégories : étape 3.") }
			composable<SettingsRoute> { PlaceholderScreen("Réglages", "Verrou et export : étape 7.") }
		}
	}
}
