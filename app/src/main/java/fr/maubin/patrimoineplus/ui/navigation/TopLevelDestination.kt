package fr.maubin.patrimoineplus.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/*
	Route de l'écran d'accueil.
*/
@Serializable
data object HomeRoute

/*
	Route de la liste des comptes.
*/
@Serializable
data object AccountsRoute

/*
	Route de la liste des catégories.
*/
@Serializable
data object CategoriesRoute

/*
	Route des réglages.
*/
@Serializable
data object SettingsRoute

/*
	Destinations de la barre de navigation du bas.
	route : route de navigation associée
	label : libellé affiché sous l'icône
	icon  : icône de l'onglet
*/
enum class TopLevelDestination(
	val route : Any,
	val label : String,
	val icon  : ImageVector
)
{
	Home(HomeRoute, "Accueil", Icons.Outlined.Home),
	Accounts(AccountsRoute, "Comptes", Icons.Outlined.AccountBalance),
	Categories(CategoriesRoute, "Catégories", Icons.Outlined.Category),
	Settings(SettingsRoute, "Réglages", Icons.Outlined.Settings)
}
