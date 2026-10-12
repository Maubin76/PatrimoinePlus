package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.transfer
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

/*
	Budget prévisionnel (appli PC) : totaux, comparaison au réel du mois,
	diagramme de cashflow équilibré, fichier.
*/
class BudgetTest
{
	private val kBudget = Budget(listOf(
		BudgetLine("1", BudgetSection.INCOME, "Salaire", eur(3_000.0), categoryId = kSalary.id),
		BudgetLine("2", BudgetSection.INCOME, "Prime", eur(200.0)),
		BudgetLine("3", BudgetSection.EXPENSE, "Courses", eur(450.0), categoryId = kGroceries.id),
		BudgetLine("4", BudgetSection.EXPENSE, "Loyer", eur(1_000.0)),
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
		Réel du mois : catégorie (remboursement déduit), versements reçus par
		le compte suivi ; lignes non suivies sans réel ; autres mois ignorés.
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
		assertEquals(eur(280.0), vView.lines.single { it.line.id == "3" }.actual)
		assertEquals(eur(-170.0), vView.lines.single { it.line.id == "3" }.gap)
		assertEquals(eur(400.0), vView.lines.single { it.line.id == "5" }.actual)
		assertNull(vView.lines.single { it.line.id == "4" }.actual)
		assertEquals(eur(3_100.0 - 280.0 - 400.0), vView.actualRemaining)
		val vReal = cashFlow(vView, inActual = true)										// diagramme du réel
		assertEquals(setOf("Courses", "Livret A", "Reste"), vReal.outputs.map { it.name }.toSet())		// loyer non suivi : absent du réel
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
		Thèmes : investissements en premier (thème « Investissements » par
		défaut), puis dépenses regroupées par thème de la plus forte à la plus
		faible (détail trié), ligne sans thème seule et sans détail, reste en
		dernier.
	*/
	@Test
	fun groupsByTheme()
	{
		val vBudget = Budget(listOf(
			BudgetLine("1", BudgetSection.INCOME, "Salaire", eur(3_000.0)),
			BudgetLine("2", BudgetSection.EXPENSE, "Loyer", eur(850.0), group = "Logement"),
			BudgetLine("3", BudgetSection.EXPENSE, "Électricité", eur(50.0), group = "Logement"),
			BudgetLine("4", BudgetSection.EXPENSE, "Courses", eur(150.0), group = "Vie quotidienne"),
			BudgetLine("5", BudgetSection.EXPENSE, "Restaurants", eur(300.0), group = "Vie quotidienne"),
			BudgetLine("6", BudgetSection.EXPENSE, "Transports", eur(160.0)),
			BudgetLine("7", BudgetSection.INVESTMENT, "Actions", eur(500.0)),
			BudgetLine("8", BudgetSection.INVESTMENT, "Crypto", eur(50.0))
		))																					// budget à thèmes
		val vDiagram = cashFlow(compareBudget(vBudget, DataSet.kEmpty, YearMonth.of(2026, 10)), inActual = false)	// diagramme
		assertEquals(listOf("Investissements", "Logement", "Vie quotidienne", "Transports", "Reste"), vDiagram.groups.map { it.name })
		assertEquals(listOf("Actions", "Crypto"), vDiagram.groups[0].items.map { it.name })
		assertEquals(listOf("Restaurants", "Courses"), vDiagram.groups[2].items.map { it.name })
		assertTrue(vDiagram.groups[3].items.isEmpty())
		assertEquals(eur(940.0), vDiagram.groups.last().amount)
		assertEquals(vDiagram.total, vDiagram.groups.sumOf { it.amount })
		assertEquals(vDiagram.groups.sumOf { it.amount }, vDiagram.outputs.sumOf { it.amount })
	}
}
