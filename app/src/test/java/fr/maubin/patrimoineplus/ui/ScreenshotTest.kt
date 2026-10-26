package fr.maubin.patrimoineplus.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import fr.maubin.patrimoineplus.ui.accounts.AccountItem
import fr.maubin.patrimoineplus.ui.accounts.accountWeights
import fr.maubin.patrimoineplus.ui.account.AccountDetailActions
import fr.maubin.patrimoineplus.ui.account.AccountDetailContent
import fr.maubin.patrimoineplus.ui.account.buildAccountDetail
import fr.maubin.patrimoineplus.ui.account.buildAccountExtras
import fr.maubin.patrimoineplus.ui.entry.EntryMode
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.ui.entry.EntryData
import fr.maubin.patrimoineplus.ui.entry.EntryForm
import fr.maubin.patrimoineplus.ui.entry.TransactionSheet
import fr.maubin.patrimoineplus.domain.calc.labelSuggestions
import fr.maubin.patrimoineplus.ui.accounts.AccountSheet
import fr.maubin.patrimoineplus.ui.accounts.AccountsContent
import fr.maubin.patrimoineplus.ui.accounts.AccountsUiState
import fr.maubin.patrimoineplus.ui.accounts.AccountForm
import fr.maubin.patrimoineplus.ui.onboarding.OnboardingScreen
import fr.maubin.patrimoineplus.ui.onboarding.ProposedAccount
import fr.maubin.patrimoineplus.domain.calc.accountValue
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.DefaultCatalog
import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import fr.maubin.patrimoineplus.ui.categories.CategoriesContent
import fr.maubin.patrimoineplus.ui.categories.CategoriesUiState
import fr.maubin.patrimoineplus.ui.categories.CategoryItem
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.home.HomeAccountLine
import fr.maubin.patrimoineplus.data.demo.buildDemoData
import fr.maubin.patrimoineplus.ui.planning.editRecurrenceForm
import fr.maubin.patrimoineplus.ui.planning.RecurrenceSheet
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.wealthAt
import fr.maubin.patrimoineplus.domain.calc.wealthSeries
import fr.maubin.patrimoineplus.ui.home.HomeContent
import fr.maubin.patrimoineplus.ui.home.toHomeUiState
import java.time.LocalDate
import fr.maubin.patrimoineplus.ui.home.HomeGroup
import fr.maubin.patrimoineplus.ui.home.HomeUiState
import fr.maubin.patrimoineplus.ui.home.kPreviewState
import fr.maubin.patrimoineplus.ui.navigation.TopLevelDestination
import fr.maubin.patrimoineplus.ui.lock.LockContent
import fr.maubin.patrimoineplus.ui.settings.SafetyActions
import fr.maubin.patrimoineplus.ui.settings.SyncActions
import fr.maubin.patrimoineplus.ui.settings.SettingsScreen
import fr.maubin.patrimoineplus.ui.shell.AppFrame
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import fr.maubin.patrimoineplus.ui.planning.RecurrencesSection
import fr.maubin.patrimoineplus.ui.accounts.buildAccountsState
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import fr.maubin.patrimoineplus.ui.entry.newEntryForm
import fr.maubin.patrimoineplus.ui.home.buildHomeState
import fr.maubin.patrimoineplus.ui.components.LocalProportionStyle
import fr.maubin.patrimoineplus.ui.components.ProportionStyle
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.components.AppGround
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import fr.maubin.patrimoineplus.data.demo.withDemoFamily
import fr.maubin.patrimoineplus.domain.calc.scoped
import fr.maubin.patrimoineplus.ui.shell.LocalViewChip
import fr.maubin.patrimoineplus.ui.shell.ViewChip
import fr.maubin.patrimoineplus.ui.settings.ProfilesActions

private const val kOutDir = "build/outputs/roborazzi"						// dossier des captures

/*
	Captures d'écran rendues sur la JVM (Robolectric), sans émulateur : elles
	servent à relire l'interface. Les données affichées sont synthétiques.
	Lancement : ./gradlew :app:recordRoborazziDebug
*/
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class ScreenshotTest
{
	@get:Rule
	val fCompose = createComposeRule()											// pour les captures d'écran entier (feuilles)

	private val kToday = LocalDate.parse("2026-10-07")							// date des captures
	private val kDemo  = buildDemoData(kToday)									// démonstration

	/*
		État de l'écran des comptes calculé depuis la démonstration ; un compte archivé ajouté.
	*/
	private fun demoAccounts(): AccountsUiState
	{
		val vItems = buildAccountsState(kDemo.accounts, kDemo.transactions, kDemo.prices, kToday, kDemo.loans).active	// comptes valorisés, en net
		val vOld   = AccountItem(kDemo.accounts[2].copy(id = "ancien", name = "Ancien livret", archived = true), 0, 3)	// compte archivé
		return AccountsUiState(true, vItems, listOf(vOld))
	}

	/*
		État de l'écran des catégories calculé depuis la démonstration.
	*/
	private fun demoCategories(): CategoriesUiState
	{
		val vUsage = kDemo.transactions.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()	// utilisations
		val vItems = kDemo.categories.map { CategoryItem(it, vUsage[it.id] ?: 0) }						// catégories
		return CategoriesUiState(true, vItems.filter { it.category.kind == CategoryKind.EXPENSE }, vItems.filter { it.category.kind == CategoryKind.INCOME })
	}

	/*
		Animation du lancement, image par image : pastille seule (relais de
		l'écran de démarrage système), chevrons en route, logo qui remonte,
		écran posé avec verrou ; puis écran sans verrou, en sombre.
	*/
	@Test
	fun launchSequence()
	{
		listOf(0f to "1-debut", 0.3f to "2-chevrons", 0.66f to "3-remontee", 1f to "4-pose").forEach { (inProgress, inName) ->
			captureRoboImage("$kOutDir/lancement-$inName.png") {
				Themed(inDarkTheme = false) { LockContent(inProgress, inAuth = true, inMessage = null, inOnUnlock = {}) }
			}
		}
		captureRoboImage("$kOutDir/lancement-sans-verrou-sombre.png") {
			Themed(inDarkTheme = true) { LockContent(1f, inAuth = false, inMessage = null, inOnUnlock = {}) }
		}
	}

	/*
		Assistant du premier lancement.
	*/
	@Test
	fun onboarding() = captureRoboImage("$kOutDir/onboarding.png") {
		Themed(inDarkTheme = false) {
			OnboardingScreen(
				listOf(ProposedAccount("Compte courant", AccountType.CHECKING, opening = "2 450,12"), ProposedAccount("Livret A", AccountType.SAVINGS),
					ProposedAccount("LDDS", AccountType.SAVINGS, selected = false), ProposedAccount("PEA", AccountType.BROKERAGE)),
				DefaultCatalog.categories().map { it.name }, { _, _, _ -> }, {}
			)
		}
	}

	/*
		Profils : accueil de la vue Famille (sélecteur dans la barre du haut,
		démonstration à deux profils), réglages avec la section Profils, et
		feuille du compte joint avec une part par profil.
	*/
	@Test
	fun profiles()
	{
		val vToday   = LocalDate.parse("2026-10-07")								// date de calcul
		val vDemo    = withDemoFamily(buildDemoData(vToday))						// démonstration à deux profils
		val vFamily  = vDemo.scoped(null)											// vue Famille
		val vState   = buildHomeState(vFamily.accounts, vFamily.transactions, vFamily.prices, vToday, vFamily.loans)	// accueil de la famille
		val vChip    = @Composable { ViewChip(vDemo.profiles, null, {}) }		// sélecteur sur la famille
		captureRoboImage("$kOutDir/profils-accueil-famille.png", content = shell(false, false, TopLevelDestination.Home) {
			CompositionLocalProvider(LocalViewChip provides vChip) { HomeContent(vState, {}, {}) }
		})
		captureRoboImage("$kOutDir/profils-reglages.png", content = shell(false, false, TopLevelDestination.Settings) {
			SettingsScreen(ThemeMode.LIGHT, {}, false, {}, SafetyActions(true, "8 octobre 2026", "Documents/PatrimoinePlus", {}, {}, {}), null, null,
				inProfiles = ProfilesActions(vDemo.profiles, "Ce profil est seul propriétaire d’un compte : donnez d’abord ce compte à un autre profil.", { _, _ -> }, { _, _ -> }, {}))
		})
	}

	/*
		Feuille du compte joint d'un couple : une part par profil.
	*/
	@Test
	fun accountSheetOwners()
	{
		val vDemo = withDemoFamily(buildDemoData(LocalDate.parse("2026-10-07")))	// démonstration à deux profils
		fCompose.setContent {
			Themed(inDarkTheme = false) {
				AccountsContent(demoAccounts(), {}, {}, {}, {})
				AccountSheet(AccountForm(name = "Compte joint", institution = "Banque démo", opening = "1 200", openingDate = kToday, owners = vDemo.profiles.associate { it.id to "50" }),
					null, false, { _, _ -> }, {}, {}, {}, inProfiles = vDemo.profiles)
			}
		}
		fCompose.waitForIdle()
		captureScreenRoboImage("$kOutDir/profils-compte-joint.png")
	}

	/*
		Feuille de création d'un compte, au-dessus de l'écran des comptes.
	*/
	@Test
	fun accountSheet()
	{
		fCompose.setContent {
			Themed(inDarkTheme = false) {
				AccountsContent(demoAccounts(), {}, {}, {}, {})
				AccountSheet(AccountForm(name = "Compte joint", institution = "Banque démo", share = "50", opening = "1 200", openingDate = kToday),
					null, false, { _, _ -> }, {}, {}, {})
			}
		}
		fCompose.waitForIdle()
		captureScreenRoboImage("$kOutDir/account-sheet.png")
	}

	/*
		Thème de l'appli et son fond (pastel, ou formes floues avec le verre),
		comme dans l'appli.
		inDarkTheme  : mode sombre
		inColorTheme : thème de couleurs
		inGlass      : effet verre
		inContent    : contenu
	*/
	@Composable
	private fun Themed(
		inDarkTheme  : Boolean,
		inColorTheme : ColorTheme = ColorTheme.MONOCHROME,
		inGlass      : Boolean    = false,
		inContent    : @Composable () -> Unit
	)
	{
		PatrimoineTheme(inDarkTheme = inDarkTheme, inColorTheme = inColorTheme, inGlass = inGlass) {
			Box(Modifier.fillMaxSize()) {
				AppGround(inDarkTheme, inGlass)
				inContent()
			}
		}
	}

	/*
		Enveloppe un écran dans le thème et le cadre réel (pilule, bouton de saisie).
		inDark     : mode sombre
		inHidden   : mode discret
		inSelected : onglet courant
		inScreen   : écran à afficher
		inPosition : position du pager ; par défaut, l'onglet courant
		inColors   : thème de couleurs
		inGlass    : effet verre
	*/
	private fun shell(
		inDark     : Boolean,
		inHidden   : Boolean,
		inSelected : TopLevelDestination,
		inPosition : Float = inSelected.ordinal.toFloat(),
		inColors   : ColorTheme = ColorTheme.MONOCHROME,
		inGlass    : Boolean    = false,
		inScreen   : @Composable () -> Unit
	): @Composable () -> Unit = {
		Themed(inDarkTheme = inDark, inColorTheme = inColors, inGlass = inGlass) {
			CompositionLocalProvider(LocalAmountsHidden provides inHidden) {
				AppFrame(
					inPosition = { inPosition },
					inSelected = inSelected,
					inOnSelect = {},
					inSnackbar = remember { SnackbarHostState() }
				) {
					inScreen()
				}
			}
		}
	}

	/*
		Accueil avec des comptes, en clair.
	*/
	@Test
	fun homeLight() = captureRoboImage("$kOutDir/home-light.png", content = shell(false, false, TopLevelDestination.Home) {
		HomeContent(kPreviewState, {}, {})
	})

	/*
		Accueil avec des comptes, en sombre.
	*/
	@Test
	fun homeDark() = captureRoboImage("$kOutDir/home-dark.png", content = shell(true, false, TopLevelDestination.Home) {
		HomeContent(kPreviewState, {}, {})
	})

	/*
		Accueil en mode discret.
	*/
	@Test
	fun homeHidden() = captureRoboImage("$kOutDir/home-hidden.png", content = shell(false, true, TopLevelDestination.Home) {
		HomeContent(kPreviewState, {}, {})
	})

	/*
		Accueil sans compte : l'état réel tant que la base est vide.
	*/
	@Test
	fun homeEmpty() = captureRoboImage("$kOutDir/home-empty.png", content = shell(false, false, TopLevelDestination.Home) {
		HomeContent(HomeUiState("7 octobre 2026", emptyList()), {}, {})
	})

	/*
		Onglet des comptes, vide.
	*/
	@Test
	@Config(qualifiers = "w360dp-h1000dp-xxhdpi")
	fun accounts() = captureRoboImage("$kOutDir/accounts.png", content = shell(false, false, TopLevelDestination.Accounts) {
		AccountsContent(demoAccounts(), {}, {}, {}, {})
	})

	/*
		Onglet des comptes de la démonstration avec ses opérations récurrentes.
	*/
	@Test
	@Config(qualifiers = "w360dp-h2200dp-xxhdpi")
	fun accountsWithRecurrences() = captureRoboImage("$kOutDir/accounts-recurrences.png", content = shell(false, false, TopLevelDestination.Accounts) {
		AccountsContent(buildAccountsState(kDemo.accounts, kDemo.transactions, kDemo.prices, kToday).copy(loaded = true), {}, {}, {}, {}) {
			androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))
			RecurrencesSection(kDemo.recurrences, kDemo.accounts, kDemo.categories, kToday, {}, {})
		}
	})

	/*
		Feuille de modification d'une opération récurrente de la
		démonstration, avec sa date de fin facultative.
	*/
	@Test
	@Config(qualifiers = "w360dp-h1600dp-xxhdpi")
	fun recurrenceSheet()
	{
		fCompose.setContent {
			Themed(inDarkTheme = false) {
				AccountsContent(demoAccounts(), {}, {}, {}, {})
				RecurrenceSheet(editRecurrenceForm(kDemo.recurrences.first(), kDemo.categories).copy(end = kToday.plusYears(1)), kDemo.accounts, kDemo.categories, { _, _ -> }, {}, {})
			}
		}
		fCompose.waitForIdle()
		captureScreenRoboImage("$kOutDir/recurrence-sheet.png")
	}

	/*
		Vue du crédit auto de la démonstration : capital restant dû, prochaines
		mensualités, courbe.
	*/
	@Test
	@Config(qualifiers = "w360dp-h2000dp-xxhdpi")
	fun loanDetail() = captureRoboImage("$kOutDir/account-credit.png") {
		Themed(inDarkTheme = false) {
			AccountDetailContent(demoDetail(7), demoExtras(7), emptySet(), remember { SnackbarHostState() }, AccountDetailActions())
		}
	}

	/*
		Réglages, en sombre.
	*/
	@Test
	@Config(qualifiers = "w360dp-h1700dp-xxhdpi")
	fun settingsDark() = captureRoboImage("$kOutDir/settings-dark.png", content = shell(true, true, TopLevelDestination.Settings) {
		SettingsScreen(ThemeMode.DARK, {}, true, {}, SafetyActions(true, "8 octobre 2026", "Documents/PatrimoinePlus", {}, {}, {}), null,
			SyncActions("Documents/PatrimoinePlus", "À jour. Dernière synchronisation le 9 oct. à 17:35.", {}, {}, {}))
	})

	/*
		Accueil avec un total à sept chiffres et une police système agrandie à
		130 % : le total doit tenir sur une ligne sans être coupé.
	*/
	@Test
	@Config(fontScale = 1.3f)
	fun homeLargeFont() = captureRoboImage("$kOutDir/home-fontscale-130.png", content = shell(false, false, TopLevelDestination.Home) {
		HomeContent(
			HomeUiState(
				asOf   = "7 octobre 2026",
				groups = listOf(
					HomeGroup(AccountType.CHECKING, listOf(HomeAccountLine("Compte joint du ménage", "Courant · 50 %", 2_568_624, 1_284_312))),
					HomeGroup(AccountType.BROKERAGE, listOf(HomeAccountLine("PEA", "Bourse", 123_456_789)))
				)
			),
			{},
			{}
		)
	})

	/*
		Onglet des catégories : libellé actif le plus long de la pilule.
	*/
	@Test
	fun categories() = captureRoboImage("$kOutDir/categories.png", content = shell(false, false, TopLevelDestination.Categories) {
		CategoriesContent(demoCategories(), {}, {}, {})
	})

	/*
		Pilule à mi-glissement entre Accueil et Comptes : la capsule se répartit
		entre les deux onglets.
	*/
	@Test
	fun navMidSwipe() = captureRoboImage("$kOutDir/nav-mi-glissement.png", content = shell(false, false, TopLevelDestination.Home, 0.5f) {
		HomeContent(kPreviewState, {}, {})
	})

	/*
		Accueil calculé de bout en bout à partir des données de démonstration
		(domaine réel : soldes, part détenue, valorisation du PEA).
	*/
	@Test
	@Config(qualifiers = "w360dp-h1300dp-xxhdpi")
	fun homeFromDemo()
	{
		val vToday = LocalDate.parse("2026-10-07")								// date de calcul
		val vDemo  = buildDemoData(vToday)										// démonstration
		val vState = buildHomeState(vDemo.accounts, vDemo.transactions, vDemo.prices, vToday, vDemo.loans)	// état calculé, en net
		captureRoboImage("$kOutDir/home-demo.png", content = shell(false, false, TopLevelDestination.Home) {
			HomeContent(vState, {}, {})
		})
	}

	/*
		Accueil de démonstration en thème Coloré, clair puis sombre : fonds
		teintés, encre bleu nuit, répartition aux couleurs de la palette,
		variation du mois en bleu-vert.
	*/
	@Test
	@Config(qualifiers = "w360dp-h1500dp-xxhdpi")
	fun homeColorful()
	{
		val vToday = LocalDate.parse("2026-10-07")								// date de calcul
		val vDemo  = buildDemoData(vToday)										// démonstration
		val vState = buildHomeState(vDemo.accounts, vDemo.transactions, vDemo.prices, vToday, vDemo.loans)	// état calculé, en net
		captureRoboImage("$kOutDir/home-colore.png", content = shell(false, false, TopLevelDestination.Home, inColors = ColorTheme.COLORFUL) {
			CompositionLocalProvider(LocalProportionStyle provides ProportionStyle(setOf("accueil"))) { HomeContent(vState, {}, {}) }
		})
	}

	/*
		Accueil de démonstration en thème Coloré sombre.
	*/
	@Test
	@Config(qualifiers = "w360dp-h1500dp-xxhdpi")
	fun homeColorfulDark()
	{
		val vToday = LocalDate.parse("2026-10-07")								// date de calcul
		val vDemo  = buildDemoData(vToday)										// démonstration
		val vState = buildHomeState(vDemo.accounts, vDemo.transactions, vDemo.prices, vToday, vDemo.loans)	// état calculé, en net
		captureRoboImage("$kOutDir/home-colore-sombre.png", content = shell(true, false, TopLevelDestination.Home, inColors = ColorTheme.COLORFUL) { HomeContent(vState, {}, {}) })
	}

	/*
		Effet verre : accueil en clair et en sombre (noir profond, violet),
		réglages en verre sombre avec l'interrupteur actif.
	*/
	@Test
	fun glassEffect()
	{
		val vToday = LocalDate.parse("2026-10-07")								// date de calcul
		val vDemo  = buildDemoData(vToday)										// démonstration
		val vState = buildHomeState(vDemo.accounts, vDemo.transactions, vDemo.prices, vToday, vDemo.loans)	// état calculé, en net
		captureRoboImage("$kOutDir/home-verre.png", content = shell(false, false, TopLevelDestination.Home, inGlass = true) { HomeContent(vState, {}, {}) })
		captureRoboImage("$kOutDir/home-verre-sombre.png", content = shell(true, false, TopLevelDestination.Home, inGlass = true) { HomeContent(vState, {}, {}) })
		captureRoboImage("$kOutDir/settings-verre-sombre.png", content = shell(true, false, TopLevelDestination.Settings, inGlass = true) {
			SettingsScreen(ThemeMode.DARK, {}, false, {}, SafetyActions(true, "8 octobre 2026", "Documents/PatrimoinePlus", {}, {}, {}), null, null, inGlass = true)
		})
	}

	/*
		Accueil de démonstration avec les répartitions en cercle (réglage
		basculé par l'icône) : anneau dans l'ordre et les teintes de la légende.
	*/
	@Test
	@Config(qualifiers = "w360dp-h1500dp-xxhdpi")
	fun homePie()
	{
		val vToday = LocalDate.parse("2026-10-07")								// date de calcul
		val vDemo  = buildDemoData(vToday)										// démonstration
		val vState = buildHomeState(vDemo.accounts, vDemo.transactions, vDemo.prices, vToday, vDemo.loans)	// état calculé, en net
		captureRoboImage("$kOutDir/home-pie.png", content = shell(false, false, TopLevelDestination.Home) {
			CompositionLocalProvider(LocalProportionStyle provides ProportionStyle(setOf("accueil"))) { HomeContent(vState, {}, {}) }
		})
	}

	/*
		État de la vue du compte courant de démonstration, mois en cours.
	*/
	private fun demoDetail(inIndex: Int = 0) = buildAccountDetail(kDemo.accounts[inIndex].id, kDemo.accounts, kDemo.categories, kDemo.transactions, java.time.YearMonth.from(kToday), kToday)

	/*
		Relevés ou portefeuille d'un compte de démonstration.
		inIndex : rang du compte dans la démonstration
	*/
	private fun demoExtras(inIndex: Int = 0) = buildAccountExtras(kDemo.accounts[inIndex].id, kDemo.accounts, kDemo.transactions, kDemo.statements, kDemo.prices, kDemo.assets, kToday, kDemo.loans)

	/*
		Vue d'un compte, mois en cours (synthèse, dépenses, courbe, opérations).
	*/
	@Test
	@Config(qualifiers = "w360dp-h2200dp-xxhdpi")
	fun accountDetail() = captureRoboImage("$kOutDir/account-detail.png") {
		Themed(inDarkTheme = false) {
			AccountDetailContent(demoDetail(), demoExtras(), emptySet(), remember { SnackbarHostState() }, AccountDetailActions())
		}
	}

	/*
		Vue d'un compte en mode sélection (deux opérations choisies).
	*/
	@Test
	fun accountDetailSelection()
	{
		val vState = demoDetail()												// état du mois
		val vIds   = vState.days.first().items.take(1).map { it.transaction.id } + vState.days[1].items.take(1).map { it.transaction.id }	// sélection
		captureRoboImage("$kOutDir/account-detail-selection.png") {
			Themed(inDarkTheme = true) {
				AccountDetailContent(vState.copy(), demoExtras(), vIds.toSet(), remember { SnackbarHostState() }, AccountDetailActions())
			}
		}
	}

	/*
		Feuille de saisie d'une dépense, suggestions de libellés affichées.
	*/
	@Test
	fun entrySheet()
	{
		val vCats = kDemo.categories.sortedBy { it.sortOrder }					// catégories
		fCompose.setContent {
			Themed(inDarkTheme = false) {
				AccountDetailContent(demoDetail(), demoExtras(), emptySet(), remember { SnackbarHostState() }, AccountDetailActions())
				TransactionSheet(
					EntryForm(amount = "42,90", accountId = kDemo.accounts.first().id, categoryId = vCats.first { it.name == "Alimentation" }.id, label = "Su", date = kToday),
					EntryData(kDemo.accounts, vCats, kDemo.assets, kDemo.prices, kDemo.transactions), { inPrefix -> labelSuggestions(kDemo.transactions, inPrefix) }, { _, _ -> }, {}, {}
				)
			}
		}
		fCompose.waitForIdle()
		captureScreenRoboImage("$kOutDir/entry-sheet.png")
	}

	/*
		Vue du PEA de démonstration : valeur, plus-values, positions, puis le mois.
	*/
	@Test
	@Config(qualifiers = "w360dp-h2000dp-xxhdpi")
	fun brokerageDetail() = captureRoboImage("$kOutDir/account-bourse.png") {
		Themed(inDarkTheme = false) {
			AccountDetailContent(demoDetail(4), demoExtras(4), emptySet(), remember { SnackbarHostState() }, AccountDetailActions())
		}
	}

	/*
		Vue du Livret A de démonstration : relevé avec écart et ajustement proposé.
	*/
	@Test
	@Config(qualifiers = "w360dp-h1600dp-xxhdpi")
	fun savingsDetail() = captureRoboImage("$kOutDir/account-livret.png") {
		Themed(inDarkTheme = false) {
			AccountDetailContent(demoDetail(2), demoExtras(2), emptySet(), remember { SnackbarHostState() }, AccountDetailActions())
		}
	}

	/*
		Vue du compte Collection de démonstration : valeur au prix d'achat,
		plus-value des ventes, objets détenus (« Vendre ») et vendus.
	*/
	@Test
	@Config(qualifiers = "w360dp-h1800dp-xxhdpi")
	fun collectionDetail() = captureRoboImage("$kOutDir/account-collection.png") {
		Themed(inDarkTheme = false) {
			AccountDetailContent(demoDetail(6), demoExtras(6), emptySet(), remember { SnackbarHostState() }, AccountDetailActions())
		}
	}

	/*
		Feuille de saisie en mode ordre (vente) : quantité détenue, montant calculé.
	*/
	@Test
	@Config(qualifiers = "w360dp-h1400dp-xxhdpi")
	fun tradeSheet()
	{
		val vPea = kDemo.accounts[4]											// compte Bourse
		fCompose.setContent {
			Themed(inDarkTheme = false) {
				TransactionSheet(
					EntryForm(mode = EntryMode.TRADE, accountId = vPea.id, assetId = kDemo.assets.first().id, side = TradeSide.SELL, quantity = "10", price = "94,50", fees = "1,99", date = kToday),
					EntryData(kDemo.accounts, kDemo.categories, kDemo.assets, kDemo.prices, kDemo.transactions), { emptyList() }, { _, _ -> }, {}, {}
				)
			}
		}
		fCompose.waitForIdle()
		captureScreenRoboImage("$kOutDir/entry-ordre.png")
	}

	/*
		Feuille de saisie en mode « Crédit » depuis le crédit auto : mensualité
		du mois proposée (total, capital, intérêts et assurance).
	*/
	@Test
	@Config(qualifiers = "w360dp-h1400dp-xxhdpi")
	fun loanPaymentSheet()
	{
		val vData = EntryData(kDemo.accounts.filterNot { it.type.isProperty }, kDemo.categories, kDemo.assets, kDemo.prices, kDemo.transactions, loans = kDemo.loans)	// données de saisie
		fCompose.setContent {
			Themed(inDarkTheme = false) {
				TransactionSheet(newEntryForm(vData, kDemo.accounts[7].id, kToday), vData, { emptyList() }, { _, _ -> }, {}, {})
			}
		}
		fCompose.waitForIdle()
		captureScreenRoboImage("$kOutDir/entry-credit.png")
	}

	/*
		Prêt immobilier de la démonstration : bien financé (appartement),
		valeur nette ; et la résidence principale avec sa valeur nette.
	*/
	@Test
	@Config(qualifiers = "w360dp-h2000dp-xxhdpi")
	fun mortgageDetail() = captureRoboImage("$kOutDir/account-pret-immobilier.png") {
		Themed(inDarkTheme = false) {
			AccountDetailContent(demoDetail(8), demoExtras(8), emptySet(), remember { SnackbarHostState() }, AccountDetailActions())
		}
	}

	/*
		Résidence principale financée par le prêt immobilier : valeur nette.
	*/
	@Test
	@Config(qualifiers = "w360dp-h1400dp-xxhdpi")
	fun financedHome() = captureRoboImage("$kOutDir/account-residence.png") {
		Themed(inDarkTheme = false) {
			AccountDetailContent(demoDetail(5), demoExtras(5), emptySet(), remember { SnackbarHostState() }, AccountDetailActions())
		}
	}

	/*
		Feuille de saisie en mode virement.
	*/
	@Test
	fun transferSheet()
	{
		fCompose.setContent {
			Themed(inDarkTheme = false) {
				TransactionSheet(
					EntryForm(mode = EntryMode.TRANSFER, amount = "300", accountId = kDemo.accounts[0].id, toAccountId = kDemo.accounts[2].id, date = kToday),
					EntryData(kDemo.accounts, kDemo.categories, kDemo.assets, kDemo.prices, kDemo.transactions), { emptyList() }, { _, _ -> }, {}, {}
				)
			}
		}
		fCompose.waitForIdle()
		captureScreenRoboImage("$kOutDir/entry-virement.png")
	}
}
