package fr.maubin.patrimoineplus.ui

import androidx.activity.compose.BackHandler
import fr.maubin.patrimoineplus.ui.entry.TransactionSheet
import fr.maubin.patrimoineplus.ui.entry.EntryViewModel
import fr.maubin.patrimoineplus.ui.entry.EntryForm
import fr.maubin.patrimoineplus.ui.navigation.AccountRoute
import fr.maubin.patrimoineplus.ui.account.AccountDetailScreen
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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
import fr.maubin.patrimoineplus.ui.onboarding.OnboardingScreen
import fr.maubin.patrimoineplus.ui.onboarding.OnboardingViewModel
import fr.maubin.patrimoineplus.ui.navigation.MainRoute
import fr.maubin.patrimoineplus.ui.navigation.TopLevelDestination
import fr.maubin.patrimoineplus.BuildConfig
import fr.maubin.patrimoineplus.ui.settings.DemoActions
import fr.maubin.patrimoineplus.ui.settings.DemoViewModel
import fr.maubin.patrimoineplus.ui.settings.SettingsScreen
import fr.maubin.patrimoineplus.ui.settings.rememberSafetyActions
import fr.maubin.patrimoineplus.ui.settings.rememberSyncActions
import fr.maubin.patrimoineplus.ui.settings.SyncConflictDialog
import fr.maubin.patrimoineplus.ui.shell.AppFrame
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import fr.maubin.patrimoineplus.domain.model.Frequency

private const val kSlideMillis = 260									// entrée et sortie de la vue d'un compte

private val kPageSpring = spring<Float>(									// défilement vers un onglet touché
	dampingRatio = Spring.DampingRatioNoBouncy,
	stiffness    = Spring.StiffnessMediumLow
)

/*
	Coque de l'application : mode discret partagé et graphe de navigation
	(écran principal à onglets, vue d'un compte qui glisse par-dessus).
	inAppViewModel : état partagé (mode discret, apparence)
*/
@Composable
fun PatrimoineApp(inAppViewModel: AppViewModel = hiltViewModel())
{
	val vNavController = rememberNavController()							// contrôleur de navigation
	val vHidden        by inAppViewModel.amountsHidden.collectAsStateWithLifecycle()	// mode discret

	CompositionLocalProvider(LocalAmountsHidden provides vHidden) {
		NavHost(
			navController      = vNavController,
			startDestination   = MainRoute,
			enterTransition    = { slideInHorizontally(tween(kSlideMillis)) { it / 6 } + fadeIn(tween(kSlideMillis)) },
			exitTransition     = { fadeOut(tween(kSlideMillis)) },
			popEnterTransition = { fadeIn(tween(kSlideMillis)) },
			popExitTransition  = { slideOutHorizontally(tween(kSlideMillis)) { it / 6 } + fadeOut(tween(kSlideMillis)) }
		) {
			composable<MainRoute> {
				MainOrOnboarding(inAppViewModel, inOnOpenAccount = { inId -> vNavController.navigate(AccountRoute(inId)) })
			}
			composable<AccountRoute> {
				AccountDetailScreen(
					inOnBack         = { vNavController.popBackStack(MainRoute, inclusive = false) },
					inOnToggleHidden = inAppViewModel::toggleAmountsHidden
				)
			}
		}
		SyncConflictDialog()
	}
}

/*
	Assistant du premier lancement s'il est dû, écran principal sinon ; fond
	vide le temps de lire la base, pour éviter un clignotement.
	inAppViewModel  : état partagé (mode discret, apparence)
	inOnOpenAccount : ouvre la vue d'un compte
	inOnboarding    : ViewModel de l'assistant
*/
@Composable
private fun MainOrOnboarding(
	inAppViewModel  : AppViewModel,
	inOnOpenAccount : (String) -> Unit,
	inOnboarding    : OnboardingViewModel = hiltViewModel()
)
{
	val vNeeds by inOnboarding.needsOnboarding.collectAsStateWithLifecycle()	// assistant à afficher
	when (vNeeds)
	{
		true  -> OnboardingScreen(
			inProposals     = inOnboarding.proposals,
			inCategoryNames = inOnboarding.categoryNames,
			inOnFinish      = inOnboarding::finish,
			inOnSkip        = inOnboarding::skip
		)
		false -> MainTabs(inAppViewModel, inOnOpenAccount)
		null  -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
	}
}

/*
	Écran principal : les quatre onglets côte à côte dans un pager horizontal.
	On passe de l'un à l'autre en glissant ou en touchant la pilule, qui suit
	la position du pager au pixel près. Le retour système ramène à l'Accueil.
	Le bouton « + » ouvre la saisie d'une opération.
	inAppViewModel  : état partagé (mode discret, apparence)
	inOnOpenAccount : ouvre la vue d'un compte
	inEntry         : ViewModel de la saisie
*/
@Composable
private fun MainTabs(
	inAppViewModel  : AppViewModel,
	inOnOpenAccount : (String) -> Unit,
	inEntry         : EntryViewModel = hiltViewModel()
)
{
	val vPager     = rememberPagerState { TopLevelDestination.entries.size }	// position des pages
	val vScope     = rememberCoroutineScope()									// portée des défilements
	val vHaptics   = LocalHapticFeedback.current								// retour haptique
	val vSnackbar  = remember { SnackbarHostState() }						// messages éphémères
	val vHidden    by inAppViewModel.amountsHidden.collectAsStateWithLifecycle()	// mode discret
	val vThemeMode by inAppViewModel.themeMode.collectAsStateWithLifecycle()	// apparence choisie
	val vTrend     by inAppViewModel.trendColors.collectAsStateWithLifecycle()	// couleurs des hausses et baisses
	val vToggle    = inAppViewModel::toggleAmountsHidden					// bascule du mode discret
	val vData      by inEntry.data.collectAsStateWithLifecycle()			// données de saisie
	var vEntry     by remember { mutableStateOf<EntryForm?>(null) }		// saisie ouverte
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
		inSnackbar = vSnackbar,
		inOnEntry  = { vEntry = inEntry.newForm() }
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
				TopLevelDestination.Accounts   -> AccountsScreen(vToggle, inOnOpenAccount)
				TopLevelDestination.Categories -> CategoriesScreen()
				TopLevelDestination.Settings   -> SettingsScreen(
					inThemeMode   = vThemeMode,
					inOnThemeMode = inAppViewModel::setThemeMode,
					inHidden      = vHidden,
					inOnHidden    = inAppViewModel::setAmountsHidden,
					inSafety      = rememberSafetyActions(vSnackbar),
					inSync        = rememberSyncActions(vSnackbar),
					inDemo        = demoActions(vSnackbar),
					inTrend       = vTrend,
					inOnTrend     = inAppViewModel::setTrendColors
				)
			}
		}
	}

	vEntry?.let { inForm ->
		var vRepeat by remember(inForm) { mutableStateOf<Frequency?>(null) }		// répétition demandée
		TransactionSheet(
			inInitial     = inForm,
			inData        = vData.let { inEntry.dataFor(inForm) },	// relu à chaque changement des données
			inSuggestions = inEntry::suggestions,
			inOnSave      = { inSaved, inOnErrors -> inEntry.save(inSaved, { if (it.isEmpty) vEntry = null else inOnErrors(it) }, vRepeat) },
			inOnDelete    = { vEntry = null },
			inOnDismiss   = { vEntry = null },
			inOnRepeat    = { vRepeat = it }
		)
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
