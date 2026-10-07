package fr.maubin.patrimoineplus.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import kotlinx.serialization.Serializable

/*
	Route de l'écran principal : les quatre onglets, posés côte à côte dans un
	pager horizontal. Les écrans de détail (saisie, compte…) seront d'autres
	routes du graphe de navigation.
*/
@Serializable
data object MainRoute

/*
	Onglets de l'écran principal, dans l'ordre des pages du pager.
	label    : nom de l'onglet, affiché quand il est actif et lu par TalkBack
	icon     : icône au trait fin
	hasEntry : l'onglet montre le bouton de saisie
*/
enum class TopLevelDestination(
	val label    : String,
	val icon     : ImageVector,
	val hasEntry : Boolean
)
{
	Home("Accueil", LineIcons.Home, true),
	Accounts("Comptes", LineIcons.Wallet, true),
	Categories("Catégories", LineIcons.Grid, false),
	Settings("Réglages", LineIcons.Sliders, false)
}
