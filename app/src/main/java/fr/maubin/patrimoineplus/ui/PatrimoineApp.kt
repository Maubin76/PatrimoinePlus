package fr.maubin.patrimoineplus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.maubin.patrimoineplus.ui.accounts.AccountsScreen
import fr.maubin.patrimoineplus.ui.categories.CategoriesScreen
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.home.HomeScreen
import fr.maubin.patrimoineplus.ui.navigation.MainRoute
import fr.maubin.patrimoineplus.ui.navigation.TopLevelDestination
import fr.maubin.patrimoineplus.BuildConfig
import fr.maubin.patrimoineplus.ui.settings.DemoActions
import fr.maubin.patrimoineplus.ui.settings.DemoViewModel
import fr.maubin.patrimoineplus.ui.settings.SettingsScreen
import fr.maubin.patrimoineplus.ui.shell.AppFrame
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private val kPageSpring = spring<Float>(									// défilement vers un onglet touché
	dampingRatio = Spring.DampingRatioNoBouncy,
	stiffness    = Spring.StiffnessMediumLow
)

/*
	Coque de l'application : mode discret partagé et graphe de navigation.
	Pour l'instant, le graphe ne contient que l'écran principal à onglets.
	inAppViewModel : état partagé (mode discret, apparence)
*/
@Composable
fun PatrimoineApp(inAppViewModel: AppViewModel = hiltViewModel())
{
	val vNavController = rememberNavController()							// contrôleur de navigation
	val vHidden        by inAppViewModel.amountsHidden.collectAsStateWithLifecycle()	// mode discret

	CompositionLocalProvider(LocalAmountsHidden provides vHidden) {
		NavHost(navController = vNavController, startDestination = MainRoute) {
			composable<MainRoute> { MainTabs(inAppViewModel) }
		}
	}
}

/*
	Écran principal : les quatre onglets côte à côte dans un pager horizontal.
	On passe de l'un à l'autre en glissant ou en touchant la pilule, qui suit
	la position du pager au pixel près. Le retour système ramène à l'Accueil.
	inAppViewModel : état partagé (mode discret, apparence)
*/
@Composable
private fun MainTabs(inAppViewModel: AppViewModel)
{
	val vPager     = rememberPagerState { TopLevelDestination.entries.size }	// position des pages
	val vScope     = rememberCoroutineScope()									// portée des défilements
	val vHaptics   = LocalHapticFeedback.current								// retour haptique
	val vSnackbar  = remember { SnackbarHostState() }						// messages éphémères
	val vHidden    by inAppViewModel.amountsHidden.collectAsStateWithLifecycle()	// mode discret
	val vThemeMode by inAppViewModel.themeMode.collectAsStateWithLifecycle()	// apparence choisie
	val vToggle    = inAppViewModel::toggleAmountsHidden					// bascule du mode discret
	val vOpen      = { inDestination: TopLevelDestination ->				// défile vers un onglet
		vScope.launch { vPager.animateScrollToPage(inDestination.ordinal, animationSpec = kPageSpring) }
		Unit
	}

	LaunchedEffect(vPager) {
		snapshotFlow { vPager.settledPage }.drop(1).collect {
			vHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
		}
	}
	BackHandler(enabled = vPager.currentPage != TopLevelDestination.Home.ordinal) {
		vOpen(TopLevelDestination.Home)
	}

	AppFrame(
		inPosition = { pagerPosition(vPager) },
		inSelected = TopLevelDestination.entries[vPager.currentPage],
		inOnSelect = vOpen,
		inSnackbar = vSnackbar
	) {
		HorizontalPager(
			state                   = vPager,
			beyondViewportPageCount = 1,
			key                     = { inPage -> TopLevelDestination.entries[inPage].name }
		) { inPage ->
			when (TopLevelDestination.entries[inPage])
			{
				TopLevelDestination.Home       -> HomeScreen(
					inOnToggleHidden   = vToggle,
					inOnManageAccounts = { vOpen(TopLevelDestination.Accounts) }
				)
				TopLevelDestination.Accounts   -> AccountsScreen(vToggle)
				TopLevelDestination.Categories -> CategoriesScreen()
				TopLevelDestination.Settings   -> SettingsScreen(
					inThemeMode   = vThemeMode,
					inOnThemeMode = inAppViewModel::setThemeMode,
					inHidden      = vHidden,
					inOnHidden    = inAppViewModel::setAmountsHidden,
					inDemo        = demoActions(vSnackbar)
				)
			}
		}
	}
}

/*
	Position continue du pager : numéro de page plus fraction du défilement
	en cours (1,5 = à mi-chemin entre les pages 1 et 2).
	inPager : état du pager
*/
private fun pagerPosition(inPager: PagerState): Float =
	inPager.currentPage + inPager.currentPageOffsetFraction

/*
	Actions de démonstration, en version debug seulement ; null en release.
	inSnackbar : hôte où confirmer l'action
*/
@Composable
private fun demoActions(inSnackbar: SnackbarHostState): DemoActions?
{
	if (!BuildConfig.DEBUG)
	{
		return null
	}
	val vDemo  = hiltViewModel<DemoViewModel>()								// actions de démo
	val vScope = rememberCoroutineScope()										// portée des messages
	return DemoActions(
		onLoad  = { vDemo.loadDemo { vScope.launch { inSnackbar.showSnackbar("Données de démonstration chargées.") } } },
		onClear = { vDemo.clearAll { vScope.launch { inSnackbar.showSnackbar("Toutes les données ont été effacées.") } } }
	)
}
