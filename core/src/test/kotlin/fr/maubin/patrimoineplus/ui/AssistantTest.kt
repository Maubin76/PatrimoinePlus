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
		Rien de notable : neutre, sans remarque ; il dit qu'il veille.
	*/
	@Test
	fun neutral()
	{
		val vMood = assistantMood(null, null, listOf(goal("Voyage", GoalStatus.ON_TRACK)))	// humeur
		assertEquals(Mood.NEUTRAL, vMood.mood)
		assertTrue(vMood.remarks.isEmpty())
		assertEquals("Tout est calme de mon côté. Je garde un œil sur vos comptes.", vMood.speech)
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
		assertEquals("Bonne nouvelle ! Votre patrimoine grimpe ce mois-ci. L’objectif « Matelas » est atteint, bravo ! Le budget du mois tient bon pour l’instant. Continuez comme ça !", vMood.speech)
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
		assertEquals(listOf("Vos petits plaisirs ont un peu débordé du budget ce mois-ci.", "Les dépenses du quotidien dépassent ce que vous aviez prévu ce mois-ci.", "L’échéance de « Voyage » est passée sans qu’il soit atteint."), vMood.remarks.map { it.text })
		assertEquals(listOf(AssistantTarget.BUDGET, AssistantTarget.BUDGET, AssistantTarget.GOALS), vMood.remarks.map { it.target })
		assertTrue(vMood.speech.startsWith("Hmm, quelques petites choses à surveiller…"))
		assertTrue(vMood.speech.endsWith("On lève un peu le pied ?"))
		val vDown = assistantMood(-12_000, null, listOf(goal("Voyage", GoalStatus.BEHIND)))	// baisse et retard
		assertEquals("Hmm, quelques petites choses à surveiller… Votre objectif « Voyage » prend un peu de retard. Votre patrimoine a un peu baissé ce mois-ci. Rien de grave, mais je préfère vous le dire.", vDown.speech)
		assertEquals("Hmm, un petit point d’attention…", assistantMood(-1, null, emptyList()).headline)
	}

	/*
		Jamais de chiffres dans ce qu'il dit (rien à masquer en mode discret).
	*/
	@Test
	fun noFigures()
	{
		val vMood = assistantMood(-1_234_567, budget(theme(ExpenseTheme.TRANSPORT, 100, 98_765)), listOf(goal("Voyage", GoalStatus.BEHIND)))	// humeur
		assertTrue(vMood.speech, vMood.speech.none { it.isDigit() })
	}
}
