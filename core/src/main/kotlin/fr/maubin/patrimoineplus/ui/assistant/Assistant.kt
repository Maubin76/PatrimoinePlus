package fr.maubin.patrimoineplus.ui.assistant

import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.ui.budget.BudgetView
import fr.maubin.patrimoineplus.ui.goals.GoalStatus
import fr.maubin.patrimoineplus.ui.goals.GoalView
import kotlin.math.abs

private const val kMaxRemarks = 3													// remarques gardées pour la bulle

/*
	Humeur de l'assistant.
*/
enum class Mood
{
	HAPPY,																			// tout va bien ce mois-ci
	NEUTRAL,																		// rien de notable
	WORRIED																			// quelque chose demande attention
}

/*
	Page où mène une remarque de l'assistant.
*/
enum class AssistantTarget
{
	DASHBOARD,																		// tableau de bord (patrimoine)
	BUDGET,																			// budget du mois
	GOALS																			// objectifs
}

/*
	Remarque de l'assistant, expliquée dans sa bulle.
	text   : phrase courte et sobre
	tone   : bonne nouvelle (HAPPY) ou point d'attention (WORRIED)
	target : page où elle mène
*/
data class Remark(
	val text   : String,
	val tone   : Mood,
	val target : AssistantTarget
)

/*
	Humeur de l'assistant et ses raisons.
	mood    : humeur
	remarks : remarques, les points d'attention d'abord ; au plus trois
*/
data class AssistantMood(
	val mood    : Mood,
	val remarks : List<Remark>
)
{
	// Phrase qui résume l'humeur (en tête de la bulle).
	val headline: String
		get() = when (mood)
		{
			Mood.HAPPY   -> "Tout va bien ce mois-ci."
			Mood.NEUTRAL -> "Rien à signaler ce mois-ci."
			Mood.WORRIED -> if (remarks.count { it.tone == Mood.WORRIED } > 1) "Quelques points à surveiller ce mois-ci." else "Un point à surveiller ce mois-ci."
		}
}

/*
	Humeur de l'assistant pour le mois en cours, dans la vue affichée.
	Points d'attention : thème du budget dépassé (le plus gros dépassement
	d'abord), objectif en retard ou échéance passée, patrimoine en baisse.
	Bonnes nouvelles : patrimoine en hausse, objectif atteint, budget tenu
	jusqu'ici. Un seul point d'attention suffit à l'inquiéter ; sinon, une
	bonne nouvelle le rend content. Fonction pure.
	inMonthChange : variation du patrimoine net depuis la fin du mois précédent ; null le premier mois
	inBudget      : budget du mois comparé au réel ; null sans budget
	inGoals       : objectifs évalués
	inMoney       : écriture d'un montant (masqué en mode discret)
*/
fun assistantMood(
	inMonthChange : Long?,
	inBudget      : BudgetView?,
	inGoals       : List<GoalView>,
	inMoney       : (Long) -> String = { formatEuros(it) }
): AssistantMood
{
	val vWorries = mutableListOf<Remark>()												// points d'attention
	val vGood    = mutableListOf<Remark>()												// bonnes nouvelles
	val vThemes  = inBudget?.themes.orEmpty().filter { it.planned > 0 }					// thèmes de dépenses budgétés
	vThemes.filter { it.gap > 0 }.sortedByDescending { it.gap }.forEach { inTheme ->
		vWorries += Remark("Budget « ${inTheme.theme.label} » dépassé de ${inMoney(inTheme.gap)}.", Mood.WORRIED, AssistantTarget.BUDGET)
	}
	inGoals.filter { it.status == GoalStatus.BEHIND || it.status == GoalStatus.OVERDUE }.forEach { inGoal ->
		val vWhy = if (inGoal.status == GoalStatus.OVERDUE) "échéance passée" else "en retard sur son rythme"	// raison
		vWorries += Remark("Objectif « ${inGoal.goal.title} » : $vWhy.", Mood.WORRIED, AssistantTarget.GOALS)
	}
	when
	{
		inMonthChange == null -> Unit
		inMonthChange < 0     -> vWorries += Remark("Patrimoine en baisse de ${inMoney(abs(inMonthChange))} ce mois-ci.", Mood.WORRIED, AssistantTarget.DASHBOARD)
		inMonthChange > 0     -> vGood += Remark("Patrimoine en hausse de ${inMoney(inMonthChange)} ce mois-ci.", Mood.HAPPY, AssistantTarget.DASHBOARD)
	}
	inGoals.filter { it.status == GoalStatus.REACHED }.forEach { inGoal ->
		vGood += Remark("Objectif « ${inGoal.goal.title} » atteint.", Mood.HAPPY, AssistantTarget.GOALS)
	}
	if (vThemes.isNotEmpty() && vThemes.none { it.gap > 0 })
	{
		vGood += Remark("Budget du mois tenu jusqu’ici.", Mood.HAPPY, AssistantTarget.BUDGET)
	}
	val vMood = when																	// humeur
	{
		vWorries.isNotEmpty() -> Mood.WORRIED
		vGood.isNotEmpty()    -> Mood.HAPPY
		else                  -> Mood.NEUTRAL
	}
	return AssistantMood(vMood, (vWorries + vGood).take(kMaxRemarks))
}
