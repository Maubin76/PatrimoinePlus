package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.ui.assistant.AssistantTarget
import fr.maubin.patrimoineplus.ui.assistant.Mood
import fr.maubin.patrimoineplus.ui.assistant.assistantMood
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetLine
import fr.maubin.patrimoineplus.ui.budget.BudgetLineView
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.BudgetView
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.budget.ThemeView
import fr.maubin.patrimoineplus.ui.goals.Goal
import fr.maubin.patrimoineplus.ui.goals.GoalKind
import fr.maubin.patrimoineplus.ui.goals.GoalStatus
import fr.maubin.patrimoineplus.ui.goals.GoalView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/*
	Humeur de l'assistant : neutre sans rien de notable, content (patrimoine
	en hausse, objectif atteint, budget tenu), inquiet dès un point
	d'attention (thème dépassé, le plus gros d'abord ; objectif en retard ou
	échu ; patrimoine en baisse), trois remarques au plus, montants masqués
	en mode discret.
*/
class AssistantTest
{
	/*
		Thème de dépenses budgété et son réel.
		inTheme   : thème
		inPlanned : prévu (centimes)
		inActual  : réel (centimes)
	*/
	private fun theme(
		inTheme   : ExpenseTheme,
		inPlanned : Long,
		inActual  : Long
	) = ThemeView(inTheme, listOf(BudgetLineView(BudgetLine(inTheme.name, BudgetSection.EXPENSE, inTheme.label, inPlanned, theme = inTheme), null, inTheme)), inActual)

	/*
		Budget du mois réduit à ses thèmes.
		inThemes : thèmes
	*/
	private fun budget(vararg inThemes: ThemeView) = BudgetView(YearMonth.of(2026, 10), inThemes.flatMap { it.lines }, Budget(inThemes.flatMap { it.lines.map { inLine -> inLine.line } }), inThemes.toList())

	/*
		Objectif évalué dans un état.
		inTitle  : titre
		inStatus : état
	*/
	private fun goal(
		inTitle  : String,
		inStatus : GoalStatus
	) = GoalView(Goal(inTitle, GoalKind.WEALTH, inTitle, 100_000), 0, 0, 0, null, null, null, null, 0, 0, inStatus)

	/*
		Rien de notable : neutre, sans remarque.
	*/
	@Test
	fun neutral()
	{
		val vMood = assistantMood(null, null, listOf(goal("Voyage", GoalStatus.ON_TRACK)))	// humeur
		assertEquals(Mood.NEUTRAL, vMood.mood)
		assertTrue(vMood.remarks.isEmpty())
		assertEquals("Rien à signaler ce mois-ci.", vMood.headline)
		assertEquals(Mood.NEUTRAL, assistantMood(0, null, emptyList()).mood)
	}

	/*
		Bonnes nouvelles seules : content.
	*/
	@Test
	fun happy()
	{
		val vMood = assistantMood(209_338, budget(theme(ExpenseTheme.DAILY, 50_000, 30_000)), listOf(goal("Matelas", GoalStatus.REACHED)))	// humeur
		assertEquals(Mood.HAPPY, vMood.mood)
		assertEquals(listOf("Patrimoine en hausse de 2 093,38 € ce mois-ci.", "Objectif « Matelas » atteint.", "Budget du mois tenu jusqu’ici."), vMood.remarks.map { it.text })
		assertEquals("Tout va bien ce mois-ci.", vMood.headline)
	}

	/*
		Un point d'attention suffit : inquiet ; points d'attention d'abord, le
		plus gros dépassement en tête, trois remarques au plus.
	*/
	@Test
	fun worried()
	{
		val vMood = assistantMood(
			50_000,
			budget(theme(ExpenseTheme.DAILY, 40_000, 44_200), theme(ExpenseTheme.PLEASURE, 10_000, 25_000), theme(ExpenseTheme.HOUSING, 90_000, 90_000)),
			listOf(goal("Voyage", GoalStatus.OVERDUE), goal("Matelas", GoalStatus.REACHED))
		)																				// humeur
		assertEquals(Mood.WORRIED, vMood.mood)
		assertEquals(listOf("Budget « Plaisirs » dépassé de 150,00 €.", "Budget « Vie quotidienne » dépassé de 42,00 €.", "Objectif « Voyage » : échéance passée."), vMood.remarks.map { it.text })
		assertEquals(listOf(AssistantTarget.BUDGET, AssistantTarget.BUDGET, AssistantTarget.GOALS), vMood.remarks.map { it.target })
		assertEquals("Quelques points à surveiller ce mois-ci.", vMood.headline)
		val vDown = assistantMood(-12_000, null, listOf(goal("Voyage", GoalStatus.BEHIND)))	// baisse et retard
		assertEquals(listOf("Objectif « Voyage » : en retard sur son rythme.", "Patrimoine en baisse de 120,00 € ce mois-ci."), vDown.remarks.map { it.text })
		assertEquals("Un point à surveiller ce mois-ci.", assistantMood(-1, null, emptyList()).headline)
	}

	/*
		Mode discret : les montants passent par l'écriture fournie.
	*/
	@Test
	fun maskedAmounts()
	{
		val vMood = assistantMood(-12_000, budget(theme(ExpenseTheme.DAILY, 100, 200)), emptyList()) { "•••" }	// humeur, montants masqués
		assertEquals(listOf("Budget « Vie quotidienne » dépassé de •••.", "Patrimoine en baisse de ••• ce mois-ci."), vMood.remarks.map { it.text })
	}
}
