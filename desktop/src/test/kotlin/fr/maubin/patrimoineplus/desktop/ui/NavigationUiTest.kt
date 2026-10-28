package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.ReportTab
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.search.SearchQuery
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import fr.maubin.patrimoineplus.ui.goals.withPlan
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff

/*
	Volet latéral du PC (sans écran) : huit entrées dans l'ordre voulu ;
	écran Opérations (saisie rapide puis récurrences), Bilan à deux onglets
	(mois par mois, bilan annuel), catégories dans les réglages ; une case
	de l'analyse ouvre la Recherche. Captures `22-*.png`.
*/
class NavigationUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Contrôleur en démonstration, en clair, sur les réglages jetables.
	*/
	private fun demo(): DesktopController =
		DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }

	/*
		Rend l'appli dans un PNG (relecture humaine).
		inApp  : contrôleur
		inName : nom du fichier
	*/
	private fun capture(inApp: DesktopController, inName: String)
	{
		val vScene = ImageComposeScene(width = 1440, height = 1500, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		vScene.render()
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Entrées du volet, pages fusionnées et passage de l'analyse à la
		recherche.
	*/
	@Test
	fun sidebarAndMergedPages()
	{
		assertEquals(listOf("Tableau de bord", "Objectifs", "Recherche", "Opérations", "Budget", "Projection", "Bilan", "Réglages"), Screen.entries.map { it.label })
		val vApp = demo()																	// démonstration
		fCompose.setContent { App(vApp) { null } }
		Screen.entries.forEach { inScreen -> fCompose.onNodeWithContentDescription(inScreen.label).assertExists() }	// rail d'icônes : nom en description
		fCompose.onNodeWithContentDescription("Tableau de bord").assertIsSelected()
		fCompose.onNodeWithContentDescription("Opérations").performClick()
		fCompose.waitForIdle()
		assertEquals(Screen.OPERATIONS, vApp.navigation.value.screen)
		fCompose.onNodeWithContentDescription("Opérations").assertIsSelected()
		fCompose.onNodeWithContentDescription("Tableau de bord").assertIsNotSelected()
		fCompose.onNodeWithText("Saisie rapide").assertExists()
		fCompose.onNodeWithText("Récurrences").assertExists()
		capture(vApp, "22-operations")
		fCompose.onNodeWithContentDescription("Bilan").performClick()
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Bilan annuel").assertExists()
		capture(vApp, "22-bilan-annuel")
		fCompose.onNodeWithText("Mois par mois").performClick()
		fCompose.waitForIdle()
		assertEquals(ReportTab.MONTHLY, vApp.reportTab.value)
		fCompose.onNodeWithText("Par catégorie").assertExists()
		capture(vApp, "22-bilan-mois")
		vApp.openSearch(SearchQuery())
		assertEquals(Screen.SEARCH, vApp.navigation.value.screen)
		vApp.show(Screen.SETTINGS)
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Catégories").assertExists()
		fCompose.onNodeWithText("Ajouter une catégorie de dépense").assertExists()
		capture(vApp, "22-reglages")
	}

	/*
		Bascule barre ↔ cercle des répartitions : une icône dans la tuile ;
		chaque diagramme a sa forme, indépendante des autres (tableau de bord,
		titres du PEA), mémorisée dans les réglages et retrouvée au prochain
		passage ; la tuile garde sa taille (le titre « Comptes » sous elle ne
		bouge pas) ; légende présente une seule fois. Captures `23-*.png` en
		cercle.
	*/
	@Test
	fun proportionToggle()
	{
		val vApp = demo()																	// démonstration
		val vPea = vApp.workspace.value.data.accounts.single { it.name == "PEA" }.id		// compte Bourse
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Afficher en diagramme circulaire").performScrollTo()
		val vBefore = fCompose.onNodeWithText("Comptes").fetchSemanticsNode().positionInRoot	// position sous les tuiles
		fCompose.onNodeWithContentDescription("Afficher en diagramme circulaire").performScrollTo().performClick()	// tuile sous la partie visible de la fenêtre de test
		fCompose.waitForIdle()
		assertEquals(setOf("tableau-de-bord"), vApp.settings.value.pies)
		assertEquals(vBefore, fCompose.onNodeWithText("Comptes").fetchSemanticsNode().positionInRoot)
		fCompose.onNodeWithText("Immobilier").assertExists()									// légende unique (la forme cachée n'est pas lue)
		capture(vApp, "23-tableau-de-bord-cercle")
		vApp.openAccount(vPea)
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Afficher en diagramme circulaire").assertExists()	// le PEA garde sa barre
		fCompose.onNodeWithContentDescription("Afficher en diagramme circulaire").performScrollTo().performClick()
		fCompose.waitForIdle()
		assertEquals(setOf("tableau-de-bord", "titres:$vPea"), vApp.settings.value.pies)
		capture(vApp, "23-pea-cercle")
		vApp.back()
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Afficher en barre").performScrollTo().performClick()	// le tableau de bord, retrouvé en cercle, repasse en barre
		fCompose.waitForIdle()
		assertEquals(setOf("titres:$vPea"), vApp.settings.value.pies)							// le PEA reste en cercle
		assertEquals(setOf("titres:$vPea"), DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load().pies)	// mémorisé pour la prochaine fois
	}

	/*
		Thème Coloré sur le PC : choix dans les réglages (mémorisé), tableau de
		bord, budget (cashflow) et réglages en clair. Captures `24-*.png`.
	*/
	@Test
	fun colorfulTheme()
	{
		val vApp = demo()																	// démonstration
		vApp.show(Screen.SETTINGS)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Coloré").performScrollTo().performClick()
		fCompose.waitForIdle()
		assertEquals(fr.maubin.patrimoineplus.ui.theme.ColorTheme.COLORFUL, vApp.settings.value.colors)
		assertEquals(fr.maubin.patrimoineplus.ui.theme.ColorTheme.COLORFUL, DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load().colors)
		capture(vApp, "24-reglages-colore")
		vApp.show(Screen.DASHBOARD)
		capture(vApp, "24-tableau-de-bord-colore")
		val vData = vApp.workspace.value.data												// données
		val vCat  = { inName: String -> vData.categories.first { it.name == inName }.id }	// catégorie par nom
		vApp.setBudget(fr.maubin.patrimoineplus.ui.budget.Budget(listOf(
			fr.maubin.patrimoineplus.ui.budget.BudgetLine("1", fr.maubin.patrimoineplus.ui.budget.BudgetSection.INCOME, "Salaire", 320_000, categoryId = vCat("Salaire")),
			fr.maubin.patrimoineplus.ui.budget.BudgetLine("2", fr.maubin.patrimoineplus.ui.budget.BudgetSection.EXPENSE, "Loyer", 95_000, theme = fr.maubin.patrimoineplus.ui.budget.ExpenseTheme.HOUSING),
			fr.maubin.patrimoineplus.ui.budget.BudgetLine("3", fr.maubin.patrimoineplus.ui.budget.BudgetSection.EXPENSE, "Courses", 45_000, theme = fr.maubin.patrimoineplus.ui.budget.ExpenseTheme.DAILY),
			fr.maubin.patrimoineplus.ui.budget.BudgetLine("4", fr.maubin.patrimoineplus.ui.budget.BudgetSection.EXPENSE, "Prêt", 90_000, theme = fr.maubin.patrimoineplus.ui.budget.ExpenseTheme.CREDIT),
			fr.maubin.patrimoineplus.ui.budget.BudgetLine("5", fr.maubin.patrimoineplus.ui.budget.BudgetSection.INVESTMENT, "Livret A", 50_000, accountId = vData.accounts.first { it.name == "Livret A" }.id)
		)))
		vApp.show(Screen.BUDGET)
		capture(vApp, "24-budget-colore")
	}

	/*
		Effet verre : interrupteur des réglages, mémorisé ; captures `27-*.png`
		en clair et en sombre (noir profond et violet), Monochrome et Coloré.
	*/
	@Test
	fun glassEffect()
	{
		val vApp = demo()																	// démonstration
		vApp.show(Screen.SETTINGS)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Effet verre").performScrollTo().assertIsOff().performClick()
		fCompose.waitForIdle()
		assertEquals(true, vApp.settings.value.glass)
		assertEquals(true, DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load().glass)
		fCompose.onNodeWithContentDescription("Effet verre").assertIsOn()
		capture(vApp, "27-reglages-verre")
		vApp.show(Screen.REPORT); vApp.showReportTab(ReportTab.MONTHLY)
		capture(vApp, "27-bilan-mois-verre")
		vApp.showReportTab(ReportTab.ANNUAL)
		capture(vApp, "27-bilan-annuel-verre")
		vApp.show(Screen.SEARCH)
		capture(vApp, "27-recherche-verre")
		vApp.show(Screen.DASHBOARD)
		capture(vApp, "27-tableau-de-bord-verre")
		vApp.setAppearance(ThemeMode.DARK)
		capture(vApp, "27-tableau-de-bord-verre-sombre")
		vApp.show(Screen.BUDGET)
		capture(vApp, "27-budget-verre-sombre")
		vApp.setColorTheme(fr.maubin.patrimoineplus.ui.theme.ColorTheme.COLORFUL)
		vApp.show(Screen.DASHBOARD)
		capture(vApp, "27-tableau-de-bord-verre-sombre-colore")
		vApp.newOperation()
		capture(vApp, "27-saisie-verre-sombre")
		vApp.show(Screen.SETTINGS)
		capture(vApp, "27-reglages-verre-sombre")
		vApp.show(Screen.REPORT); vApp.showReportTab(ReportTab.MONTHLY)
		capture(vApp, "27-bilan-mois-verre-sombre")
		vApp.show(Screen.SEARCH)
		capture(vApp, "27-recherche-verre-sombre")
	}

	/*
		Thème Coloré sur les autres pages : vue d'un compte (couleur de son
		type), bilan (mois par mois et annuel), budget, projection, objectifs,
		opérations et recherche. Captures `25-*.png` (relecture humaine).
	*/
	@Test
	fun colorfulPages()
	{
		val vApp  = demo()																	// démonstration
		val vData = vApp.workspace.value.data												// données
		vApp.setColorTheme(fr.maubin.patrimoineplus.ui.theme.ColorTheme.COLORFUL)
		vApp.setGoals(fr.maubin.patrimoineplus.ui.goals.Goals(listOf(
			fr.maubin.patrimoineplus.ui.goals.Goal("g1", fr.maubin.patrimoineplus.ui.goals.GoalKind.WEALTH, "Patrimoine de 100 000 €", target = 10_000_000),
			fr.maubin.patrimoineplus.ui.goals.Goal("g2", fr.maubin.patrimoineplus.ui.goals.GoalKind.ACCOUNT, "Livret A plein", target = 2_295_000, accountId = vData.accounts.first { it.name == "Livret A" }.id),
			fr.maubin.patrimoineplus.ui.goals.Goal("g3", fr.maubin.patrimoineplus.ui.goals.GoalKind.RATE, "Épargner 30 %", target = 300),
			fr.maubin.patrimoineplus.ui.goals.Goal("g4", fr.maubin.patrimoineplus.ui.goals.GoalKind.TYPE, "Bourse à 20 000 €", target = 2_000_000, accountType = fr.maubin.patrimoineplus.domain.model.AccountType.BROKERAGE)
		)))
		vApp.openAccount(vData.accounts.first { it.name == "Compte courant" }.id)
		capture(vApp, "25-compte-courant")
		vApp.openAccount(vData.accounts.first { it.name == "PEA" }.id)
		capture(vApp, "25-pea")
		vApp.back()
		vApp.show(Screen.REPORT)
		capture(vApp, "25-bilan-annuel")
		vApp.showReportTab(fr.maubin.patrimoineplus.desktop.ReportTab.MONTHLY)
		capture(vApp, "25-bilan-mois")
		vApp.show(Screen.PROJECTION)
		capture(vApp, "25-projection")
		vApp.show(Screen.GOALS)
		capture(vApp, "25-objectifs")
		vApp.show(Screen.OPERATIONS)
		capture(vApp, "25-operations")
		vApp.show(Screen.SEARCH)
		capture(vApp, "25-recherche")
	}

	/*
		Objectifs du mois : « Fixer les objectifs du mois » dans le budget crée
		un objectif par thème de dépenses prévu et l'épargne des investissements
		prévus ; la page Objectifs les montre sous « Objectifs du mois », à côté
		des objectifs globaux ; refixer remplace ; un mois écoulé apparaît dans
		l'historique ; modifier un objectif global garde l'historique.
		Captures `26-*.png`.
	*/
	@Test
	fun monthGoals()
	{
		val vApp   = demo()																	// démonstration
		val vData  = vApp.workspace.value.data												// données
		val vMonth = java.time.YearMonth.from(vApp.today())									// mois en cours
		vApp.setBudget(fr.maubin.patrimoineplus.ui.budget.Budget(listOf(
			fr.maubin.patrimoineplus.ui.budget.BudgetLine("1", fr.maubin.patrimoineplus.ui.budget.BudgetSection.INCOME, "Salaire", 320_000),
			fr.maubin.patrimoineplus.ui.budget.BudgetLine("2", fr.maubin.patrimoineplus.ui.budget.BudgetSection.EXPENSE, "Courses", 60_000, theme = fr.maubin.patrimoineplus.ui.budget.ExpenseTheme.DAILY),
			fr.maubin.patrimoineplus.ui.budget.BudgetLine("3", fr.maubin.patrimoineplus.ui.budget.BudgetSection.EXPENSE, "Transports", 10_000, theme = fr.maubin.patrimoineplus.ui.budget.ExpenseTheme.TRANSPORT),
			fr.maubin.patrimoineplus.ui.budget.BudgetLine("4", fr.maubin.patrimoineplus.ui.budget.BudgetSection.INVESTMENT, "Livret A", 30_000, accountId = vData.accounts.first { it.name == "Livret A" }.id)
		)))
		vApp.setGoals(vApp.goals.value.withPlan(fr.maubin.patrimoineplus.ui.goals.planMonth(vApp.budget.value, vData, vMonth.minusMonths(1), vApp.today().minusMonths(1))))	// mois précédent, déjà fixé
		vApp.show(Screen.BUDGET)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Fixer les objectifs du mois").performScrollTo().performClick()
		fCompose.waitForIdle()
		val vPlan  = vApp.goals.value.plan(vMonth)!!										// objectifs fixés
		assertEquals(listOf("Vie quotidienne", "Transports", "Épargne du mois"), vPlan.goals.map { it.label })
		fCompose.onNodeWithText("Mettre à jour").assertExists()
		capture(vApp, "26-budget-objectifs")
		vApp.show(Screen.GOALS)
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Objectifs globaux").assertExists()
		fCompose.onNodeWithText("Objectifs du mois").assertExists()
		fCompose.onNodeWithText("Historique").performScrollTo().assertExists()
		capture(vApp, "26-objectifs")
		vApp.setGoals(vApp.goals.value.copy(goals = listOf(fr.maubin.patrimoineplus.ui.goals.Goal("g", fr.maubin.patrimoineplus.ui.goals.GoalKind.WEALTH, "Patrimoine", target = 10_000_000))))
		assertEquals(2, vApp.goals.value.months.size)														// l'historique reste
		assertEquals(2, vApp.currentFile().monthGoals.map { it.month }.distinct().size)					// rangé dans les données (fichier d'échange)
	}
}
