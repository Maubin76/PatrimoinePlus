package fr.maubin.patrimoineplus.ui.goals

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.accountValue
import fr.maubin.patrimoineplus.domain.calc.shareOf
import fr.maubin.patrimoineplus.domain.calc.tradeCashCents
import fr.maubin.patrimoineplus.domain.calc.wealthAt
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.ui.analysis.buildExpenseAnalysis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private val kJson             = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }	// format du fichier des objectifs
private const val kPaceDays   = 28L													// recul minimal pour estimer un rythme, en jours
private const val kPaceMonths = 12L													// recul maximal du rythme, en mois

/*
	Nature d'un objectif : ce qu'il suit et comment son avancement se calcule.
	label   : libellé affiché
	numeric : objectif chiffré (cible en euros ou en %)
*/
enum class GoalKind(val label: String, val numeric: Boolean)
{
	WEALTH("Patrimoine total", true),												// patrimoine compté (part détenue appliquée)
	ACCOUNT("Compte", true),														// valeur entière d'un compte (plafond d'un livret)
	TYPE("Type de placement", true),												// valeur comptée des comptes d'un type
	MONTHLY("Épargne mensuelle", true),												// mis de côté chaque mois (moyenne)
	RATE("Taux d'épargne", true),													// taux d'épargne moyen
	MANUAL("Montant à la main", true),												// avancement saisi par l'utilisateur
	TASK("À cocher", false)															// objectif non chiffré
}

/*
	Objectif sur le patrimoine (enregistré sur le PC).
	id          : identifiant
	kind        : nature
	title       : intitulé libre
	target      : cible ; en centimes, ou en pour mille pour un taux d'épargne
	accountId   : compte suivi (ACCOUNT)
	accountType : type de placement suivi (TYPE)
	current     : avancement saisi à la main, en centimes (MANUAL)
	deadline    : échéance facultative, date ISO (« 2028-12-31 »)
	done        : fait (TASK)
	note        : note libre
*/
@Serializable
data class Goal(
	val id          : String,
	val kind        : GoalKind,
	val title       : String,
	val target      : Long         = 0,
	val accountId   : String?      = null,
	val accountType : AccountType? = null,
	val current     : Long         = 0,
	val deadline    : String?      = null,
	val done        : Boolean      = false,
	val note        : String       = ""
)
{
	// Échéance lue ; null sans échéance ou si elle est illisible.
	val due: LocalDate? get() = deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/*
	Objectifs, dans l'ordre de saisie.
	goals : objectifs
*/
@Serializable
data class Goals(val goals: List<Goal> = emptyList())

/*
	Objectifs en texte (fichier du PC).
	inGoals : objectifs
*/
fun encodeGoals(inGoals: Goals): String = kJson.encodeToString(Goals.serializer(), inGoals)

/*
	Objectifs lus d'un texte ; aucun s'il est illisible.
	inText : contenu du fichier
*/
fun decodeGoals(inText: String): Goals = runCatching { kJson.decodeFromString(Goals.serializer(), inText) }.getOrDefault(Goals())

/*
	État d'un objectif.
*/
enum class GoalStatus
{
	REACHED,																		// cible atteinte (chiffré)
	ON_TRACK,																		// au rythme actuel, atteint avant l'échéance
	BEHIND,																			// rythme insuffisant pour l'échéance, ou moyenne sous la cible
	OVERDUE,																		// échéance dépassée sans être atteint ni fait
	OPEN,																			// en cours, sans élément pour juger
	DONE,																			// fait (à cocher)
	TODO																			// à faire (à cocher)
}

/*
	Objectif évalué sur les données.
	goal      : objectif
	current   : valeur actuelle (centimes ; pour mille pour un taux ; moyenne
	            des mois complets pour l'épargne mensuelle et le taux)
	progress  : avancement, de 0 à 100
	remaining : reste à atteindre (0 si atteint)
	pace      : rythme de progression, en centimes par mois, sur les douze
	            derniers mois au plus (soldes initiaux exclus) ; null
	            s'il est inconnu (moins de 28 jours de recul, montant à la main)
	eta       : mois estimé d'atteinte au rythme actuel ; null si le rythme est
	            nul, négatif ou inconnu, ou si la cible est atteinte
	needed    : effort mensuel nécessaire pour tenir l'échéance ; null sans
	            échéance à venir, ou pour un objectif mensuel
	last      : dernier mois complet (épargne mensuelle, taux) ; null sinon
	hits      : mois complets qui atteignent la cible (épargne mensuelle, taux)
	months    : mois complets comptés (épargne mensuelle, taux)
	status    : état
*/
data class GoalView(
	val goal      : Goal,
	val current   : Long,
	val progress  : Int,
	val remaining : Long,
	val pace      : Long?,
	val eta       : YearMonth?,
	val needed    : Long?,
	val last      : Long?,
	val hits      : Int,
	val months    : Int,
	val status    : GoalStatus
)

/*
	Évalue chaque objectif sur les données du jour. Fonction pure. Ordre :
	objectifs en cours d'abord, par échéance (sans échéance à la fin), puis
	atteints et faits ; ordre de saisie à égalité.
	inGoals : objectifs
	inData  : données
	inToday : date du jour
*/
fun evaluateGoals(
	inGoals : Goals,
	inData  : DataSet,
	inToday : LocalDate
): List<GoalView>
{
	val vLedger   = Ledger(inData.transactions)												// grand livre
	val vAnalysis by lazy { buildExpenseAnalysis(inData, inToday) }						// revenus et dépenses mensuels
	val vSaved    by lazy { monthlySaved(inData, vLedger, inToday) }						// mis de côté par mois complet
	val vViews    = inGoals.goals.map { inGoal ->
		when (inGoal.kind)
		{
			GoalKind.TASK    -> task(inGoal, inToday)
			GoalKind.MONTHLY -> monthly(inGoal, vSaved, inToday)
			GoalKind.RATE    -> monthly(inGoal, vAnalysis.savingsRate.subList(vAnalysis.firstIndex, vAnalysis.currentIndex).filterNotNull(), inToday, vAnalysis.averageRate)
			GoalKind.MANUAL  -> accumulation(inGoal, inGoal.current, null, inToday)
			else             ->
			{
				val vValue = valueOf(inGoal, inData, vLedger)										// valeur suivie à une date
				accumulation(inGoal, vValue(inToday), pace(inGoal, inData, inToday, vValue), inToday)
			}
		}
	}
	val vFinished = setOf(GoalStatus.REACHED, GoalStatus.DONE)							// objectifs terminés
	return vViews.sortedWith(compareBy<GoalView> { it.status in vFinished }.thenBy { it.goal.due ?: LocalDate.MAX })
}

/*
	Valeur suivie par un objectif chiffré sur les comptes, à une date.
	inGoal   : objectif (patrimoine, compte ou type)
	inData   : données
	inLedger : grand livre
*/
private fun valueOf(
	inGoal   : Goal,
	inData   : DataSet,
	inLedger : Ledger
): (LocalDate) -> Long = { inDate ->
	when (inGoal.kind)
	{
		GoalKind.ACCOUNT -> inData.accounts.firstOrNull { it.id == inGoal.accountId }?.let { accountValue(it, inLedger, inDate, inData.prices).valueCents } ?: 0L
		GoalKind.TYPE    -> wealthAt(inData.accounts.filter { it.type == inGoal.accountType }, inLedger, inDate, inData.prices).totalCents
		else             -> wealthAt(inData.accounts, inLedger, inDate, inData.prices).totalCents
	}
}

/*
	Rythme de progression d'une valeur, en centimes par mois : écart entre
	aujourd'hui et le début de la période, ramené à un mois. La période couvre
	les douze derniers mois au plus, depuis la première opération des comptes
	suivis ; les soldes initiaux saisis ensuite (compte ouvert dans
	l'application) sont retirés de l'écart, car ce n'est pas une progression.
	null avec moins de 28 jours de recul.
	inGoal  : objectif (patrimoine, compte ou type)
	inData  : données
	inToday : date du jour
	inValue : valeur suivie à une date
*/
private fun pace(
	inGoal  : Goal,
	inData  : DataSet,
	inToday : LocalDate,
	inValue : (LocalDate) -> Long
): Long?
{
	val vTracked = when (inGoal.kind)
	{
		GoalKind.ACCOUNT -> inData.accounts.filter { it.id == inGoal.accountId }
		GoalKind.TYPE    -> inData.accounts.filter { it.type == inGoal.accountType }
		else             -> inData.accounts
	}.associateBy { it.id }																	// comptes suivis
	val vPast    = inData.transactions.filter { !it.date.isAfter(inToday) && it.postings.any { inPosting -> inPosting.accountId in vTracked } }	// opérations passées des comptes suivis
	val vFirst   = vPast.minOfOrNull { it.date } ?: return null								// première opération suivie
	val vStart   = maxOf(inToday.minusMonths(kPaceMonths), vFirst)							// début de la période
	val vDays    = ChronoUnit.DAYS.between(vStart, inToday)								// recul
	if (vDays < kPaceDays) return null
	val vOpened  = vPast.filter { it.kind == TransactionKind.OPENING && it.date.isAfter(vStart) }.sumOf { inTx ->
		inTx.postings.filter { it.accountId in vTracked }.sumOf { inPosting ->
			val vCash  = inPosting.amountCents + inTx.trades.sumOf { -tradeCashCents(TradeSide.BUY, it.quantity, it.unitPrice, 0) }	// espèces et positions initiales
			if (inGoal.kind == GoalKind.ACCOUNT) vCash else shareOf(vCash, vTracked.getValue(inPosting.accountId).sharePercent)
		}
	}																						// soldes initiaux saisis dans la période
	return Math.round((inValue(inToday) - inValue(vStart) - vOpened) * 365.25 / 12 / vDays)
}

/*
	Objectif à atteindre (patrimoine, compte, type, montant à la main) :
	avancement, date estimée au rythme actuel, effort nécessaire pour
	l'échéance.
	inGoal    : objectif
	inCurrent : valeur actuelle
	inPace    : rythme mensuel ; null s'il est inconnu
	inToday   : date du jour
*/
private fun accumulation(
	inGoal    : Goal,
	inCurrent : Long,
	inPace    : Long?,
	inToday   : LocalDate
): GoalView
{
	val vRemaining = (inGoal.target - inCurrent).coerceAtLeast(0L)						// reste à atteindre
	val vReached   = inGoal.target > 0 && vRemaining == 0L								// cible atteinte
	val vMonth     = YearMonth.from(inToday)												// mois en cours
	val vDue       = inGoal.due?.takeIf { !it.isBefore(inToday) }							// échéance à venir
	val vLeft      = vDue?.let { ChronoUnit.MONTHS.between(vMonth, YearMonth.from(it)).coerceAtLeast(1L) }	// mois restants
	val vNeeded    = if (vReached) null else vLeft?.let { ceilDiv(vRemaining, it) }		// effort mensuel
	val vEta       = if (vReached || inPace == null || inPace <= 0) null else vMonth.plusMonths(ceilDiv(vRemaining, inPace))	// atteinte estimée
	val vStatus    = when
	{
		vReached                                    -> GoalStatus.REACHED
		inGoal.due != null && vDue == null          -> GoalStatus.OVERDUE
		vNeeded != null && inPace != null           -> if (inPace >= vNeeded) GoalStatus.ON_TRACK else GoalStatus.BEHIND
		else                                        -> GoalStatus.OPEN
	}																						// état
	return GoalView(inGoal, inCurrent, percent(inCurrent, inGoal.target), vRemaining, inPace, vEta, vNeeded, null, 0, 0, vStatus)
}

/*
	Objectif mensuel (épargne mensuelle, taux d'épargne) : moyenne des mois
	complets comparée à la cible, dernier mois, mois qui l'atteignent.
	inGoal    : objectif
	inValues  : valeur de chaque mois complet, du plus ancien au plus récent
	inToday   : date du jour
	inAverage : moyenne à retenir ; par défaut celle des valeurs
*/
private fun monthly(
	inGoal    : Goal,
	inValues  : List<Long>,
	inToday   : LocalDate,
	inAverage : Long? = null
): GoalView
{
	val vAverage = inAverage ?: if (inValues.isEmpty()) 0L else Math.round(inValues.average())	// moyenne
	val vReached = inValues.isNotEmpty() && inGoal.target > 0 && vAverage >= inGoal.target		// cible tenue en moyenne
	val vStatus  = when
	{
		vReached                                              -> GoalStatus.REACHED
		inGoal.due?.isBefore(inToday) == true                 -> GoalStatus.OVERDUE
		inValues.isEmpty()                                    -> GoalStatus.OPEN
		else                                                  -> GoalStatus.BEHIND
	}																						// état
	return GoalView(
		goal      = inGoal,
		current   = vAverage,
		progress  = percent(vAverage, inGoal.target),
		remaining = (inGoal.target - vAverage).coerceAtLeast(0L),
		pace      = null,
		eta       = null,
		needed    = null,
		last      = inValues.lastOrNull(),
		hits      = inValues.count { it >= inGoal.target },
		months    = inValues.size,
		status    = vStatus
	)
}

/*
	Objectif à cocher : fait, à faire, ou en retard.
	inGoal  : objectif
	inToday : date du jour
*/
private fun task(
	inGoal  : Goal,
	inToday : LocalDate
): GoalView
{
	val vStatus = when
	{
		inGoal.done                           -> GoalStatus.DONE
		inGoal.due?.isBefore(inToday) == true -> GoalStatus.OVERDUE
		else                                  -> GoalStatus.TODO
	}																						// état
	return GoalView(inGoal, 0, if (inGoal.done) 100 else 0, 0, null, null, null, null, 0, 0, vStatus)
}

/*
	Mis de côté de chaque mois complet (comme « Mis de côté » du tableau de
	bord : virements nets des comptes Courant vers l'épargne et la bourse),
	sur les douze derniers mois au plus, depuis le mois de la première
	opération.
	inData   : données
	inLedger : grand livre
	inToday  : date du jour
*/
private fun monthlySaved(
	inData   : DataSet,
	inLedger : Ledger,
	inToday  : LocalDate
): List<Long>
{
	val vFirst    = inData.transactions.minOfOrNull { YearMonth.from(it.date) } ?: return emptyList()	// premier mois suivi
	val vById     = inData.accounts.associateBy { it.id }									// comptes par identifiant
	val vExpenses = inData.categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()	// catégories de dépense
	val vChecking = inData.accounts.filter { it.type == AccountType.CHECKING }				// comptes Courant
	val vCurrent  = YearMonth.from(inToday)													// mois en cours (incomplet)
	return (kPaceMonths downTo 1).map { vCurrent.minusMonths(it) }
		.filter { !it.isBefore(vFirst) }
		.map { inMonth -> -vChecking.sumOf { inLedger.monthSummary(it, inMonth, vById, vExpenses).savings } }
}

/*
	Avancement en pour cent, de 0 à 100 ; 0 sans cible.
	inValue  : valeur
	inTarget : cible
*/
private fun percent(
	inValue  : Long,
	inTarget : Long
): Int = if (inTarget <= 0) 0 else (inValue.coerceAtLeast(0L) * 100 / inTarget).coerceAtMost(100L).toInt()

/*
	Division entière arrondie au-dessus (valeurs positives).
	inA : dividende
	inB : diviseur (positif)
*/
private fun ceilDiv(
	inA : Long,
	inB : Long
): Long = (inA + inB - 1) / inB
