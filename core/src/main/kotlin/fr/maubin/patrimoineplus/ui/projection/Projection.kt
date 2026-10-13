package fr.maubin.patrimoineplus.ui.projection

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.wealthAt
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.analysis.buildExpenseAnalysis
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToLong

const val kProjectionSpreadSavings   = 1.0											// écart des scénarios, épargne (points par an)
const val kProjectionSpreadBrokerage = 3.0											// écart des scénarios, bourse (points par an)

/*
	Hypothèses d'une projection du patrimoine.
	years          : horizon, en années
	monthlySavings : épargne mensuelle versée, en centimes
	savingsRate    : rendement annuel de l'épargne, en %
	brokerageRate  : rendement annuel de la bourse, en %
	stockShare     : part de l'épargne mensuelle placée en bourse, en %
*/
data class ProjectionInput(
	val years          : Int,
	val monthlySavings : Long,
	val savingsRate    : Double,
	val brokerageRate  : Double,
	val stockShare     : Int
)

/*
	Point annuel d'une projection.
	year        : années écoulées (0 : aujourd'hui)
	prudent     : patrimoine du scénario prudent (rendements diminués)
	central     : patrimoine du scénario central
	optimistic  : patrimoine du scénario favorable (rendements augmentés)
	contributed : patrimoine sans rendement (départ + versements)
*/
data class ProjectionPoint(
	val year        : Int,
	val prudent     : Long,
	val central     : Long,
	val optimistic  : Long,
	val contributed : Long
)

/*
	Projection du patrimoine.
	input  : hypothèses
	start  : patrimoine compté aujourd'hui
	points : une valeur par année, de 0 à l'horizon
*/
data class Projection(
	val input  : ProjectionInput,
	val start  : Long,
	val points : List<ProjectionPoint>
)
{
	// Dernier point (horizon).
	val last: ProjectionPoint get() = points.last()
}

/*
	Hypothèses par défaut : dix ans ; épargne mensuelle égale à l'épargne
	moyenne des douze derniers mois (revenus − dépenses, jamais négative) ;
	2,5 % pour l'épargne, 6 % pour la bourse ; part en bourse égale à la
	répartition actuelle entre épargne et bourse.
	inData  : données
	inToday : date du jour
*/
fun defaultProjectionInput(
	inData  : DataSet,
	inToday : LocalDate
): ProjectionInput
{
	val vTable  = buildExpenseAnalysis(inData, inToday)								// dépenses et revenus moyens
	val vWealth = wealthAt(inData.accounts, Ledger(inData.transactions), inToday, inData.prices).byType	// patrimoine par type
	val vSaved  = vWealth[AccountType.SAVINGS] ?: 0L									// épargne
	val vStock  = vWealth[AccountType.BROKERAGE] ?: 0L								// bourse
	return ProjectionInput(
		years          = 10,
		monthlySavings = (vTable.averageIncome - vTable.averageExpense).coerceAtLeast(0L),
		savingsRate    = 2.5,
		brokerageRate  = 6.0,
		stockShare     = if (vSaved + vStock > 0) (vStock * 100 / (vSaved + vStock)).toInt() else 0
	)
}

/*
	Projette le patrimoine mois par mois : les comptes Courant et les biens
	(immobilier, collection, à leur prix d'achat) restent stables, l'épargne et la bourse capitalisent à leur rendement, l'épargne
	mensuelle est versée chaque mois selon la part en bourse. Trois
	scénarios : rendements diminués, tels quels, augmentés. Ce n'est qu'une
	estimation : rendements constants, sans impôts ni inflation.
	inData  : données (patrimoine de départ, part détenue appliquée)
	inToday : date du jour
	inInput : hypothèses
*/
fun projectWealth(
	inData  : DataSet,
	inToday : LocalDate,
	inInput : ProjectionInput
): Projection
{
	val vWealth = wealthAt(inData.accounts, Ledger(inData.transactions), inToday, inData.prices)	// patrimoine actuel
	val vTypes  = vWealth.byType														// par type
	val vCash   = (vWealth.totalCents - (vTypes[AccountType.SAVINGS] ?: 0L) - (vTypes[AccountType.BROKERAGE] ?: 0L)).toDouble()	// comptes Courant et biens (immobilier, collection), stables
	val vSaved  = (vTypes[AccountType.SAVINGS] ?: 0L).toDouble()						// épargne
	val vStock  = (vTypes[AccountType.BROKERAGE] ?: 0L).toDouble()					// bourse
	val vShare  = inInput.stockShare.coerceIn(0, 100) / 100.0							// part versée en bourse
	val vMonthly = inInput.monthlySavings.toDouble()									// versement mensuel

	/*
		Patrimoine à la fin de chaque année pour des rendements donnés.
		inSavings   : rendement de l'épargne, en %
		inBrokerage : rendement de la bourse, en %
	*/
	fun run(
		inSavings   : Double,
		inBrokerage : Double
	): List<Long>
	{
		val vRateS = (1 + inSavings.coerceAtLeast(-99.0) / 100).pow(1.0 / 12) - 1		// rendement mensuel de l'épargne
		val vRateB = (1 + inBrokerage.coerceAtLeast(-99.0) / 100).pow(1.0 / 12) - 1	// rendement mensuel de la bourse
		var vS     = vSaved																// épargne simulée
		var vB     = vStock																// bourse simulée
		val vYears = mutableListOf((vCash + vS + vB).roundToLong())					// valeur de départ
		repeat(inInput.years * 12) { inMonth ->
			vS = vS * (1 + vRateS) + vMonthly * (1 - vShare)
			vB = vB * (1 + vRateB) + vMonthly * vShare
			if ((inMonth + 1) % 12 == 0) vYears += (vCash + vS + vB).roundToLong()
		}
		return vYears
	}

	val vPrudent  = run(inInput.savingsRate - kProjectionSpreadSavings, inInput.brokerageRate - kProjectionSpreadBrokerage)	// scénario prudent
	val vCentral  = run(inInput.savingsRate, inInput.brokerageRate)							// scénario central
	val vOptimist = run(inInput.savingsRate + kProjectionSpreadSavings, inInput.brokerageRate + kProjectionSpreadBrokerage)	// scénario favorable
	val vFlat     = run(0.0, 0.0)															// sans rendement
	return Projection(
		input  = inInput,
		start  = vWealth.totalCents,
		points = (0..inInput.years).map { ProjectionPoint(it, vPrudent[it], vCentral[it], vOptimist[it], vFlat[it]) }
	)
}
