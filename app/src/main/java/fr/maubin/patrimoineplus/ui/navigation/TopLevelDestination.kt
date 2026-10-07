package fr.maubin.patrimoineplus.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import fr.maubin.patrimoineplus.ui.icons.LineIcons
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
	route    : route de navigation associée
	label    : nom de l'onglet, affiché quand il est actif et lu par TalkBack
	icon     : icône au trait fin
	hasEntry : l'onglet montre le bouton de saisie
*/
enum class TopLevelDestination(
	val route    : Any,
	val label    : String,
	val icon     : ImageVector,
	val hasEntry : Boolean
)
{
	Home(HomeRoute, "Accueil", LineIcons.Home, true),
	Accounts(AccountsRoute, "Comptes", LineIcons.Wallet, true),
	Categories(CategoriesRoute, "Catégories", LineIcons.Grid, false),
	Settings(SettingsRoute, "Réglages", LineIcons.Sliders, false)
}
