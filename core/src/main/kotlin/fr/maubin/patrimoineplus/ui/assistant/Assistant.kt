package fr.maubin.patrimoineplus.ui.assistant

import fr.maubin.patrimoineplus.ui.budget.BudgetView
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.goals.GoalStatus
import fr.maubin.patrimoineplus.ui.goals.GoalView

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
	Remarque de l'assistant : une phrase naturelle, sans chiffres.
	text   : phrase courte, sur le ton de la conversation
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
	// Ouverture de ce qu'il dit, selon l'humeur.
	val headline: String
		get() = when (mood)
		{
			Mood.HAPPY   -> "Bonne nouvelle !"
			Mood.NEUTRAL -> "Tout est calme de mon côté."
			Mood.WORRIED -> if (remarks.count { it.tone == Mood.WORRIED } > 1) "Hmm, quelques petites choses à surveiller…" else "Hmm, un petit point d’attention…"
		}

	// Ce qu'il dit dans sa bulle : l'ouverture, ses remarques, puis un mot de la fin.
	val speech: String
		get()
		{
			val vClosing = when
			{
				mood == Mood.NEUTRAL                                                     -> "Je garde un œil sur vos comptes."
				remarks.any { it.tone == Mood.WORRIED && it.target == AssistantTarget.BUDGET } -> "On lève un peu le pied ?"
				mood == Mood.WORRIED                                                     -> "Rien de grave, mais je préfère vous le dire."
				else                                                                     -> "Continuez comme ça !"
			}																			// mot de la fin
			return (listOf(headline) + remarks.map { it.text } + vClosing).joinToString(" ")
		}
}

/*
	Phrase d'un thème de dépenses dépassé.
	inTheme : thème
*/
private fun overBudget(inTheme: ExpenseTheme): String = when (inTheme)
{
	ExpenseTheme.DAILY         -> "Les dépenses du quotidien dépassent ce que vous aviez prévu ce mois-ci."
	ExpenseTheme.PLEASURE      -> "Vos petits plaisirs ont un peu débordé du budget ce mois-ci."
	ExpenseTheme.SUBSCRIPTIONS -> "Les abonnements coûtent plus que prévu ce mois-ci."
	ExpenseTheme.TRANSPORT     -> "Les transports dépassent le budget du mois."
	ExpenseTheme.HOUSING       -> "Le logement coûte plus que prévu ce mois-ci."
	ExpenseTheme.CREDIT        -> "Les crédits pèsent plus lourd que prévu ce mois-ci."
}

/*
	Humeur de l'assistant pour le mois en cours, dans la vue affichée.
	Points d'attention : thème du budget dépassé (le plus gros dépassement
	d'abord), objectif en retard ou échéance passée, patrimoine en baisse.
	Bonnes nouvelles : patrimoine en hausse, objectif atteint, budget tenu
	jusqu'ici. Un seul point d'attention suffit à l'inquiéter ; sinon, une
	bonne nouvelle le rend content. Phrases naturelles, sans montants (rien
	à masquer en mode discret). Fonction pure.
	inMonthChange : variation du patrimoine net depuis la fin du mois précédent ; null le premier mois
	inBudget      : budget du mois comparé au réel ; null sans budget
	inGoals       : objectifs évalués
*/
fun assistantMood(
	inMonthChange : Long?,
	inBudget      : BudgetView?,
	inGoals       : List<GoalView>
): AssistantMood
{
	val vWorries = mutableListOf<Remark>()												// points d'attention
	val vGood    = mutableListOf<Remark>()												// bonnes nouvelles
	val vThemes  = inBudget?.themes.orEmpty().filter { it.planned > 0 }					// thèmes de dépenses budgétés
	vThemes.filter { it.gap > 0 }.sortedByDescending { it.gap }.forEach { inTheme ->
		vWorries += Remark(overBudget(inTheme.theme), Mood.WORRIED, AssistantTarget.BUDGET)
	}
	inGoals.filter { it.status == GoalStatus.BEHIND || it.status == GoalStatus.OVERDUE }.forEach { inGoal ->
		val vText = if (inGoal.status == GoalStatus.OVERDUE) "L’échéance de « ${inGoal.goal.title} » est passée sans qu’il soit atteint." else "Votre objectif « ${inGoal.goal.title} » prend un peu de retard."	// phrase
		vWorries += Remark(vText, Mood.WORRIED, AssistantTarget.GOALS)
	}
	when
	{
		inMonthChange == null -> Unit
		inMonthChange < 0     -> vWorries += Remark("Votre patrimoine a un peu baissé ce mois-ci.", Mood.WORRIED, AssistantTarget.DASHBOARD)
		inMonthChange > 0     -> vGood += Remark("Votre patrimoine grimpe ce mois-ci.", Mood.HAPPY, AssistantTarget.DASHBOARD)
	}
	inGoals.filter { it.status == GoalStatus.REACHED }.forEach { inGoal ->
		vGood += Remark("L’objectif « ${inGoal.goal.title} » est atteint, bravo !", Mood.HAPPY, AssistantTarget.GOALS)
	}
	if (vThemes.isNotEmpty() && vThemes.none { it.gap > 0 })
	{
		vGood += Remark("Le budget du mois tient bon pour l’instant.", Mood.HAPPY, AssistantTarget.BUDGET)
	}
	val vMood = when																	// humeur
	{
		vWorries.isNotEmpty() -> Mood.WORRIED
		vGood.isNotEmpty()    -> Mood.HAPPY
		else                  -> Mood.NEUTRAL
	}
	return AssistantMood(vMood, (vWorries + vGood).take(kMaxRemarks))
}
