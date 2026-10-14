package fr.maubin.patrimoineplus.ui.goals

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.budget.compareBudget
import fr.maubin.patrimoineplus.ui.budget.themeSpending
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

// Objectifs du mois : fixés depuis le budget (« Fixer les objectifs du mois »),
// une dépense maximale par thème et une épargne minimale (investissements
// prévus), suivis sur les opérations du mois et gardés mois par mois
// (historique consultable).

/*
	Nature d'un objectif du mois.
*/
enum class MonthGoalKind
{
	SPENDING,																			// dépenses d'un thème, au plus la cible
	SAVING																				// mis de côté dans le mois, au moins la cible
}

/*
	Objectif d'un mois.
	id     : identifiant
	kind   : nature
	target : cible, en centimes (positive)
	theme  : thème des dépenses suivies ; null pour l'épargne
*/
@Serializable
data class MonthGoal(
	val id     : String,
	val kind   : MonthGoalKind,
	val target : Long,
	val theme  : ExpenseTheme? = null
)
{
	// Intitulé : nom du thème, ou « Épargne du mois ».
	val label: String get() = theme?.label ?: "Épargne du mois"
}

/*
	Objectifs fixés pour un mois.
	month     : mois (AAAA-MM)
	confirmed : date à laquelle ils ont été fixés (AAAA-MM-JJ)
	goals     : objectifs : dépenses par thème, puis épargne
*/
@Serializable
data class MonthPlan(
	val month     : String,
	val confirmed : String,
	val goals     : List<MonthGoal>
)
{
	// Mois des objectifs.
	val yearMonth: YearMonth get() = YearMonth.parse(month)
}

/*
	État d'un objectif du mois.
	label : libellé affiché
*/
enum class MonthGoalStatus(val label: String)
{
	IN_PROGRESS("En cours"),																// mois en cours, objectif encore tenable ou pas encore atteint
	KEPT("Tenu"),																		// dépenses restées sous la cible sur le mois écoulé
	EXCEEDED("Dépassé"),																	// dépenses au-delà de la cible
	REACHED("Atteint"),																	// épargne au moins égale à la cible
	MISSED("Manqué")																		// épargne sous la cible, mois écoulé
}

/*
	Objectif du mois évalué.
	goal   : objectif
	actual : réel du mois (dépenses du thème, ou mis de côté), à la date du jour pour le mois en cours
	status : état
*/
data class MonthGoalView(
	val goal   : MonthGoal,
	val actual : Long,
	val status : MonthGoalStatus
)
{
	// Avancement : part de la cible consommée (dépenses) ou atteinte (épargne), en % (0 à 100, au-delà pour un dépassement).
	val percent: Int get() = if (goal.target <= 0) 0 else (actual.coerceAtLeast(0L) * 100 / goal.target).toInt()

	// Vrai si l'objectif est réussi (tenu ou atteint).
	val success: Boolean get() = status == MonthGoalStatus.KEPT || status == MonthGoalStatus.REACHED
}

/*
	Objectifs d'un mois tirés du budget : pour chaque thème de dépenses prévu,
	« au plus le total du thème » ; « mettre de côté au moins le total des
	investissements prévus » s'il y en a. Identifiants stables d'un appel à
	l'autre (mois et thème).
	inBudget : budget prévisionnel
	inData   : données (thèmes des lignes d'un ancien budget)
	inMonth  : mois
	inToday  : date de la confirmation
*/
fun planMonth(
	inBudget : Budget,
	inData   : DataSet,
	inMonth  : YearMonth,
	inToday  : LocalDate
): MonthPlan
{
	val vView    = compareBudget(inBudget, inData, inMonth)							// budget rangé par thème
	val vSpend   = vView.themes.filter { it.planned > 0 }.map { MonthGoal("$inMonth:${it.theme.name}", MonthGoalKind.SPENDING, it.planned, it.theme) }	// dépenses par thème
	val vInvest  = inBudget.total(BudgetSection.INVESTMENT)								// investissements prévus
	val vSave    = if (vInvest > 0) listOf(MonthGoal("$inMonth:EPARGNE", MonthGoalKind.SAVING, vInvest)) else emptyList()	// épargne du mois
	return MonthPlan(inMonth.toString(), inToday.toString(), vSpend + vSave)
}

/*
	Objectifs avec les objectifs d'un mois fixés (ceux déjà fixés pour ce mois
	sont remplacés) ; mois rangés du plus ancien au plus récent.
	inPlan : objectifs du mois
*/
fun Goals.withPlan(inPlan: MonthPlan): Goals =
	copy(months = (months.filterNot { it.month == inPlan.month } + inPlan).sortedBy { it.month })

/*
	Mis de côté dans un mois : virements nets des comptes Courant vers
	l'épargne et la bourse (comme « Mis de côté » du tableau de bord).
	inData  : données
	inMonth : mois
*/
fun monthSaved(
	inData  : DataSet,
	inMonth : YearMonth
): Long
{
	val vLedger   = Ledger(inData.transactions)											// grand livre
	val vById     = inData.accounts.associateBy { it.id }								// comptes par identifiant
	val vExpenses = inData.categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()	// catégories de dépense
	return -inData.accounts.filter { it.type == AccountType.CHECKING }.sumOf { vLedger.monthSummary(it, inMonth, vById, vExpenses).savings }
}

/*
	Évalue les objectifs d'un mois sur les opérations : dépenses du thème (au
	plus la cible) ou mis de côté (au moins la cible) ; le mois en cours est
	arrêté à aujourd'hui (opérations à venir exclues).
	inPlan   : objectifs du mois
	inData   : données
	inBudget : budget (comptes suivis en investissement, exclus du thème Crédit)
	inToday  : date du jour
*/
fun evaluateMonth(
	inPlan   : MonthPlan,
	inData   : DataSet,
	inBudget : Budget,
	inToday  : LocalDate
): List<MonthGoalView>
{
	val vMonth = inPlan.yearMonth																// mois des objectifs
	val vPast  = vMonth.isBefore(YearMonth.from(inToday))									// mois écoulé
	val vData  = inData.copy(transactions = inData.transactions.filter { !it.isUpcoming(inToday) })	// opérations passées
	val vSpent = themeSpending(vData, vMonth, inBudget.trackedAccounts)						// dépenses par thème
	val vSaved = monthSaved(vData, vMonth)													// mis de côté
	return inPlan.goals.map { inGoal ->
		when (inGoal.kind)
		{
			MonthGoalKind.SPENDING ->
			{
				val vActual = vSpent[inGoal.theme] ?: 0L									// dépenses du thème
				MonthGoalView(inGoal, vActual, when
				{
					vActual > inGoal.target -> MonthGoalStatus.EXCEEDED
					vPast                   -> MonthGoalStatus.KEPT
					else                    -> MonthGoalStatus.IN_PROGRESS
				})
			}
			MonthGoalKind.SAVING   -> MonthGoalView(inGoal, vSaved, when
			{
				vSaved >= inGoal.target -> MonthGoalStatus.REACHED
				vPast                   -> MonthGoalStatus.MISSED
				else                    -> MonthGoalStatus.IN_PROGRESS
			})
		}
	}
}
