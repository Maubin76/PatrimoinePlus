package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.transfer
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetLine
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.goals.Goal
import fr.maubin.patrimoineplus.ui.goals.GoalKind
import fr.maubin.patrimoineplus.ui.goals.Goals
import fr.maubin.patrimoineplus.ui.goals.MonthGoalKind
import fr.maubin.patrimoineplus.ui.goals.MonthGoalStatus
import fr.maubin.patrimoineplus.ui.goals.decodeGoals
import fr.maubin.patrimoineplus.ui.goals.encodeGoals
import fr.maubin.patrimoineplus.ui.goals.evaluateMonth
import fr.maubin.patrimoineplus.ui.goals.planMonth
import fr.maubin.patrimoineplus.ui.goals.withPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/*
	Objectifs du mois : fixés depuis le budget (un par thème de dépenses
	prévu, plus l'épargne des investissements prévus), remplacés si on les
	refixe, gardés mois par mois, évalués sur les opérations (dépassé, tenu,
	atteint, manqué, en cours), relus à l'identique ; ancien fichier sans
	mois lisible.
*/
class MonthGoalsTest
{
	private val kHousing = Category("logement", "Logement", "home", CategoryKind.EXPENSE)	// catégorie du thème Logement
	private val kToday   = d("2026-10-12")													// date du jour
	private val kBudget  = Budget(listOf(
		BudgetLine("1", BudgetSection.INCOME, "Salaire", eur(3_000.0)),
		BudgetLine("2", BudgetSection.EXPENSE, "Courses", eur(400.0), theme = ExpenseTheme.DAILY),
		BudgetLine("3", BudgetSection.EXPENSE, "Restaurants", eur(150.0), theme = ExpenseTheme.DAILY),
		BudgetLine("4", BudgetSection.EXPENSE, "Loyer", eur(900.0), theme = ExpenseTheme.HOUSING),
		BudgetLine("5", BudgetSection.INVESTMENT, "Livret A", eur(300.0), accountId = kLivret.id)
	))																					// budget prévisionnel
	private val kData    = DataSet(
		listOf(kChecking, kLivret), listOf(kGroceries, kHousing), emptyList(),
		listOf(
			opening("2026-08-01", kChecking, eur(5_000.0)),
			standard("2026-09-03", kChecking, -eur(620.0), kGroceries),				// septembre : courses au-delà de 550 €
			standard("2026-09-05", kChecking, -eur(900.0), kHousing),				// loyer tenu
			transfer("2026-09-27", kChecking, kLivret, eur(200.0)),					// épargne sous 300 €
			standard("2026-10-04", kChecking, -eur(300.0), kGroceries),				// octobre : courses en cours
			transfer("2026-10-08", kChecking, kLivret, eur(300.0)),					// épargne atteinte
			standard("2026-10-20", kChecking, -eur(500.0), kGroceries)				// à venir : pas encore compté
		),
		emptyList(), emptyList()
	)																					// opérations

	/*
		Objectifs tirés du budget : un par thème prévu (Vie quotidienne = 550 €,
		Logement = 900 €) puis l'épargne (300 €) ; identifiants stables ; refixer
		un mois le remplace, les mois restent rangés.
	*/
	@Test
	fun planFromBudget()
	{
		val vPlan  = planMonth(kBudget, kData, YearMonth.of(2026, 10), kToday)			// objectifs d'octobre
		assertEquals("2026-10", vPlan.month)
		assertEquals("2026-10-12", vPlan.confirmed)
		assertEquals(listOf(ExpenseTheme.DAILY to eur(550.0), ExpenseTheme.HOUSING to eur(900.0)), vPlan.goals.filter { it.kind == MonthGoalKind.SPENDING }.map { it.theme to it.target })
		assertEquals(eur(300.0), vPlan.goals.single { it.kind == MonthGoalKind.SAVING }.target)
		assertEquals(vPlan.goals.map { it.id }, planMonth(kBudget, kData, YearMonth.of(2026, 10), kToday).goals.map { it.id })
		val vSept  = planMonth(kBudget, kData, YearMonth.of(2026, 9), d("2026-09-01"))	// objectifs de septembre
		val vGoals = Goals().withPlan(vPlan).withPlan(vSept)								// deux mois
		assertEquals(listOf("2026-09", "2026-10"), vGoals.months.map { it.month })
		val vNew   = planMonth(Budget(kBudget.lines.dropLast(1)), kData, YearMonth.of(2026, 10), kToday)	// budget sans investissement, refixé
		assertTrue(vGoals.withPlan(vNew).plan(YearMonth.of(2026, 10))!!.goals.none { it.kind == MonthGoalKind.SAVING })
		assertEquals(2, vGoals.withPlan(vNew).months.size)
		assertTrue(planMonth(Budget(), kData, YearMonth.of(2026, 10), kToday).goals.isEmpty())
	}

	/*
		Évaluation : septembre écoulé (Vie quotidienne dépassée à 620 €, Logement
		tenu, épargne manquée à 200 €) ; octobre en cours (Vie quotidienne en
		cours à 300 €, l'opération à venir n'est pas comptée ; épargne atteinte).
	*/
	@Test
	fun evaluation()
	{
		val vSept = evaluateMonth(planMonth(kBudget, kData, YearMonth.of(2026, 9), d("2026-09-01")), kData, kBudget, kToday)	// septembre
		assertEquals(listOf(MonthGoalStatus.EXCEEDED, MonthGoalStatus.KEPT, MonthGoalStatus.MISSED), vSept.map { it.status })
		assertEquals(listOf(eur(620.0), eur(900.0), eur(200.0)), vSept.map { it.actual })
		assertEquals(112, vSept[0].percent)
		assertEquals(1, vSept.count { it.success })
		val vOct  = evaluateMonth(planMonth(kBudget, kData, YearMonth.of(2026, 10), kToday), kData, kBudget, kToday)	// octobre
		assertEquals(listOf(MonthGoalStatus.IN_PROGRESS, MonthGoalStatus.IN_PROGRESS, MonthGoalStatus.REACHED), vOct.map { it.status })
		assertEquals(eur(300.0), vOct[0].actual)
	}

	/*
		Fichier des objectifs : globaux et mois relus à l'identique ; un ancien
		fichier sans mois se lit avec un historique vide.
	*/
	@Test
	fun fileRoundTrip()
	{
		val vGoals = Goals(listOf(Goal("g", GoalKind.WEALTH, "Patrimoine", target = eur(100_000.0)))).withPlan(planMonth(kBudget, kData, YearMonth.of(2026, 10), kToday))	// objectifs
		assertEquals(vGoals, decodeGoals(encodeGoals(vGoals)))
		val vOld   = decodeGoals("""{"goals":[{"id":"g","kind":"WEALTH","title":"Patrimoine","target":10000000}]}""")	// fichier d'avant
		assertEquals(1, vOld.goals.size)
		assertTrue(vOld.months.isEmpty())
		assertNull(vOld.plan(YearMonth.of(2026, 10)))
	}
}
