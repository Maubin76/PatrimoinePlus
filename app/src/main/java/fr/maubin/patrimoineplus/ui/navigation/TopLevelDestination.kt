package fr.maubin.patrimoineplus.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
	Destinations de la pilule de navigation.
	route        : route de navigation associée
	label        : nom de l'onglet, lu par TalkBack
	icon         : icône au repos (contour)
	selectedIcon : icône de l'onglet actif (pleine)
	hasEntry     : l'onglet montre le bouton de saisie
*/
enum class TopLevelDestination(
	val route        : Any,
	val label        : String,
	val icon         : ImageVector,
	val selectedIcon : ImageVector,
	val hasEntry     : Boolean
)
{
	Home(HomeRoute, "Accueil", Icons.Outlined.Home, Icons.Filled.Home, true),
	Accounts(AccountsRoute, "Comptes", Icons.Outlined.AccountBalance, Icons.Filled.AccountBalance, true),
	Categories(CategoriesRoute, "Catégories", Icons.Outlined.Category, Icons.Filled.Category, false),
	Settings(SettingsRoute, "Réglages", Icons.Outlined.Settings, Icons.Filled.Settings, false)
}
