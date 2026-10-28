package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BudgetLineDto
import fr.maubin.patrimoineplus.data.backup.GoalDto
import fr.maubin.patrimoineplus.data.backup.MonthGoalDto
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetLine
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.goals.Goal
import fr.maubin.patrimoineplus.ui.goals.GoalKind
import fr.maubin.patrimoineplus.ui.goals.Goals
import fr.maubin.patrimoineplus.ui.goals.MonthGoal
import fr.maubin.patrimoineplus.ui.goals.MonthGoalKind
import fr.maubin.patrimoineplus.ui.goals.MonthPlan

/*
	Budget de la famille rangé dans le fichier d'échange (format 8) : un seul
	budget, commun à tous les profils. Lève IllegalArgumentException sur une
	valeur inconnue (voir validateBackup).
*/
fun BackupFile.budgetOf(): Budget =
	Budget(budget.map { inLine ->
		BudgetLine(
			id         = inLine.id,
			section    = BudgetSection.valueOf(inLine.section),
			name       = inLine.name,
			amount     = inLine.amount,
			categoryId = inLine.categoryId,
			accountId  = inLine.accountId,
			theme      = inLine.theme?.let { ExpenseTheme.valueOf(it) },
			profileId  = inLine.profileId
		)
	})

/*
	Objectifs de chaque vue rangés dans le fichier d'échange (format 8), par
	vue : identifiant du profil, ou kFamilyView pour la famille. Lève
	IllegalArgumentException sur une valeur inconnue.
*/
fun BackupFile.goalsByScope(): Map<String, Goals>
{
	val vScopes = (goals.map { it.scope } + monthGoals.map { it.scope }).distinct()	// vues qui ont des objectifs
	return vScopes.associateWith { inScope ->
		Goals(
			goals  = goals.filter { it.scope == inScope }.map { inGoal ->
				Goal(
					id          = inGoal.id,
					kind        = GoalKind.valueOf(inGoal.kind),
					title       = inGoal.title,
					target      = inGoal.target,
					accountId   = inGoal.accountId,
					accountType = inGoal.accountType?.let { AccountType.valueOf(it) },
					current     = inGoal.current,
					deadline    = inGoal.deadline,
					done        = inGoal.done,
					note        = inGoal.note
				)
			},
			months = monthGoals.filter { it.scope == inScope }.groupBy { it.month }.map { (inMonth, inRows) ->
				MonthPlan(
					month     = inMonth,
					confirmed = inRows.first().confirmed,
					goals     = inRows.filter { it.kind != null }.map { inRow ->
						MonthGoal(inRow.id.orEmpty(), MonthGoalKind.valueOf(inRow.kind!!), inRow.target, inRow.theme?.let { ExpenseTheme.valueOf(it) })
					}
				)
			}.sortedBy { it.month }
		)
	}
}

/*
	Fichier dont le budget de la famille est remplacé.
	inBudget : budget
*/
fun BackupFile.withBudget(inBudget: Budget): BackupFile =
	copy(budget = inBudget.lines.map { BudgetLineDto(it.id, it.section.name, it.name, it.amount, it.categoryId, it.accountId, it.theme?.name, it.profileId) })

/*
	Fichier dont les objectifs d'une vue sont remplacés ; ceux des autres vues
	sont gardés.
	inScope : vue (identifiant du profil, ou kFamilyView)
	inGoals : objectifs de la vue
*/
fun BackupFile.withGoals(
	inScope : String,
	inGoals : Goals
): BackupFile =
	copy(
		goals      = goals.filter { it.scope != inScope } + inGoals.goals.map {
			GoalDto(inScope, it.id, it.kind.name, it.title, it.target, it.accountId, it.accountType?.name, it.current, it.deadline, it.done, it.note)
		},
		monthGoals = monthGoals.filter { it.scope != inScope } + inGoals.months.flatMap { inPlan ->
			if (inPlan.goals.isEmpty()) listOf(MonthGoalDto(inScope, inPlan.month, inPlan.confirmed))	// mois fixé sans objectif
			else inPlan.goals.map { MonthGoalDto(inScope, inPlan.month, inPlan.confirmed, it.id, it.kind.name, it.target, it.theme?.name) }
		}
	)
