package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLeisure
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.transfer
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetLine
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.cashFlow
import fr.maubin.patrimoineplus.ui.budget.compareBudget
import fr.maubin.patrimoineplus.ui.budget.decodeBudget
import fr.maubin.patrimoineplus.ui.budget.encodeBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.budget.themeOfCategory

/*
	Budget prévisionnel (appli PC) : totaux, comparaison au réel du mois,
	diagramme de cashflow équilibré, fichier.
*/
class BudgetTest
{
	private val kBudget = Budget(listOf(
		BudgetLine("1", BudgetSection.INCOME, "Salaire", eur(3_000.0), categoryId = kSalary.id),
		BudgetLine("2", BudgetSection.INCOME, "Prime", eur(200.0)),
		BudgetLine("3", BudgetSection.EXPENSE, "Courses", eur(450.0), theme = ExpenseTheme.DAILY),
		BudgetLine("4", BudgetSection.EXPENSE, "Loyer", eur(1_000.0), theme = ExpenseTheme.HOUSING),
		BudgetLine("5", BudgetSection.INVESTMENT, "Livret A", eur(500.0), accountId = kLivret.id)
	))																					// budget

	/*
		Totaux par rubrique et reste prévu.
	*/
	@Test
	fun totals()
	{
		assertEquals(eur(3_200.0), kBudget.total(BudgetSection.INCOME))
		assertEquals(eur(1_450.0), kBudget.total(BudgetSection.EXPENSE))
		assertEquals(eur(1_250.0), kBudget.remaining)
	}

	/*
		Réel du mois : catégorie d'une entrée, versements reçus par le compte
		suivi ; dépenses par thème (catégories du thème, remboursement déduit),
		pas sur leurs lignes ; autres mois ignorés.
	*/
	@Test
	fun comparedToActual()
	{
		val vData = DataSet(listOf(kChecking, kLivret), listOf(kGroceries, kSalary), emptyList(), listOf(
			standard("2026-10-01", kChecking, eur(3_100.0), kSalary),
			standard("2026-10-05", kChecking, eur(-300.0), kGroceries),
			standard("2026-10-08", kChecking, eur(20.0), kGroceries),
			standard("2026-09-05", kChecking, eur(-999.0), kGroceries),
			transfer("2026-10-10", kChecking, kLivret, eur(400.0))
		), emptyList(), emptyList())														// données
		val vView = compareBudget(kBudget, vData, YearMonth.of(2026, 10))				// budget comparé
		assertEquals(eur(3_100.0), vView.lines.single { it.line.id == "1" }.actual)
		assertNull(vView.lines.single { it.line.id == "3" }.actual)
		assertEquals(listOf(ExpenseTheme.DAILY, ExpenseTheme.HOUSING), vView.themes.map { it.theme })
		assertEquals(eur(280.0), vView.themes[0].actual)
		assertEquals(eur(-170.0), vView.themes[0].gap)
		assertEquals(0L, vView.themes[1].actual)											// aucune dépense de logement ce mois-ci
		assertEquals(eur(400.0), vView.lines.single { it.line.id == "5" }.actual)
		assertEquals(eur(3_100.0 - 280.0 - 400.0), vView.actualRemaining)
		val vReal = cashFlow(vView, inActual = true)										// diagramme du réel
		assertEquals(setOf("Vie quotidienne", "Livret A", "Reste"), vReal.outputs.map { it.name }.toSet())	// logement nul : absent du réel
		assertEquals(vReal.sources.sumOf { it.amount }, vReal.outputs.sumOf { it.amount })
	}

	/*
		Diagramme : sorties = entrées (reste ajouté) ; déficit ajouté aux
		sources quand les sorties dépassent les entrées ; lignes nulles ignorées.
	*/
	@Test
	fun cashFlowBalances()
	{
		val vPlan = cashFlow(compareBudget(kBudget, DataSet.kEmpty, YearMonth.of(2026, 10)), inActual = false)	// diagramme prévu
		assertEquals(eur(3_200.0), vPlan.total)
		assertFalse(vPlan.deficit)
		assertEquals("Reste", vPlan.outputs.last().name)
		assertEquals(eur(1_250.0), vPlan.outputs.last().amount)
		assertEquals(vPlan.sources.sumOf { it.amount }, vPlan.outputs.sumOf { it.amount })
		val vOver = Budget(kBudget.lines + BudgetLine("6", BudgetSection.EXPENSE, "Voyage", eur(2_000.0)) + BudgetLine("7", BudgetSection.EXPENSE, "Vide", 0))	// budget en déficit
		val vDeficit = cashFlow(compareBudget(vOver, DataSet.kEmpty, YearMonth.of(2026, 10)), inActual = false)	// diagramme en déficit
		assertTrue(vDeficit.deficit)
		assertEquals(eur(750.0), vDeficit.sources.single { it.name == "Déficit" }.amount)
		assertTrue(vDeficit.outputs.none { it.name == "Vide" || it.name == "Reste" })
	}

	/*
		Fichier : aller-retour exact ; fichier illisible = budget vide.
	*/
	@Test
	fun fileRoundTrip()
	{
		assertEquals(kBudget, decodeBudget(encodeBudget(kBudget)))
		assertEquals(Budget(), decodeBudget("pas du JSON"))
	}

	/*
		Thèmes : investissements en premier, puis dépenses par thème de la plus
		forte à la plus faible (détail trié), thème d'une seule ligne de son nom
		sans détail, reste en dernier ; ligne d'un ancien budget rangée d'après
		sa catégorie ; réel par thème d'après le nom des catégories.
	*/
	@Test
	fun groupsByTheme()
	{
		val vHousing = Category("logement", "Logement", "home", CategoryKind.EXPENSE)		// catégorie Logement
		val vLoan    = Category("pret", "Prêt immobilier", "bank", CategoryKind.EXPENSE)	// catégorie de crédit
		val vData    = DataSet(listOf(kChecking), listOf(vHousing, vLoan, kGroceries, kLeisure), emptyList(), listOf(
			standard("2026-10-03", kChecking, eur(-900.0), vHousing),
			standard("2026-10-04", kChecking, eur(-650.0), vLoan),
			standard("2026-10-05", kChecking, eur(-120.0), kGroceries),
			standard("2026-10-06", kChecking, eur(-80.0), kLeisure)
		), emptyList(), emptyList())														// opérations du mois
		val vBudget  = Budget(listOf(
			BudgetLine("1", BudgetSection.INCOME, "Salaire", eur(3_000.0)),
			BudgetLine("2", BudgetSection.EXPENSE, "Loyer", eur(850.0), theme = ExpenseTheme.HOUSING),
			BudgetLine("3", BudgetSection.EXPENSE, "Électricité", eur(50.0), categoryId = vHousing.id),	// ancien budget : thème tiré de la catégorie
			BudgetLine("4", BudgetSection.EXPENSE, "Courses", eur(150.0), theme = ExpenseTheme.DAILY),
			BudgetLine("5", BudgetSection.EXPENSE, "Restaurants", eur(300.0), theme = ExpenseTheme.DAILY),
			BudgetLine("6", BudgetSection.EXPENSE, "Crédit", eur(650.0), theme = ExpenseTheme.CREDIT),
			BudgetLine("7", BudgetSection.INVESTMENT, "Actions", eur(500.0)),
			BudgetLine("8", BudgetSection.INVESTMENT, "Crypto", eur(50.0))
		))																					// budget à thèmes
		val vView    = compareBudget(vBudget, vData, YearMonth.of(2026, 10))				// budget comparé
		val vDiagram = cashFlow(vView, inActual = false)									// diagramme prévu
		assertEquals(listOf("Investissements", "Logement", "Crédit", "Vie quotidienne", "Reste"), vDiagram.groups.map { it.name })
		assertEquals(listOf("Actions", "Crypto"), vDiagram.groups[0].items.map { it.name })
		assertEquals(listOf("Loyer", "Électricité"), vDiagram.groups[1].items.map { it.name })
		assertTrue(vDiagram.groups[2].items.isEmpty())										// une ligne du nom du thème
		assertEquals(listOf("Restaurants", "Courses"), vDiagram.groups[3].items.map { it.name })
		assertEquals(vDiagram.total, vDiagram.groups.sumOf { it.amount })
		assertEquals(listOf(ExpenseTheme.DAILY, ExpenseTheme.HOUSING, ExpenseTheme.CREDIT), vView.themes.map { it.theme })	// ordre des thèmes
		assertEquals(listOf(eur(200.0), eur(900.0), eur(650.0)), vView.themes.map { it.actual })	// courses + loisirs, logement, prêt
		val vReal    = cashFlow(vView, inActual = true)										// diagramme du réel
		assertEquals(listOf("Logement", "Crédit", "Vie quotidienne"), vReal.groups.map { it.name })	// salaire non suivi : déficit, pas de reste
		assertTrue(vReal.groups.all { it.items.isEmpty() })									// réel par thème, sans détail
		assertEquals(ExpenseTheme.TRANSPORT, themeOfCategory("Transports"))
		assertEquals(ExpenseTheme.SUBSCRIPTIONS, themeOfCategory("abonnement"))
		assertEquals(ExpenseTheme.CREDIT, themeOfCategory("Crédit auto"))
		assertEquals(ExpenseTheme.DAILY, themeOfCategory("Santé"))
	}
}
