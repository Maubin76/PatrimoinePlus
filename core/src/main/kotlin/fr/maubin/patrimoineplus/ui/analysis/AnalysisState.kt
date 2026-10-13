package fr.maubin.patrimoineplus.ui.analysis

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate
import java.time.YearMonth

const val kAnalysisMonths = 12														// mois du tableau d'analyse

/*
	Ligne du tableau d'analyse : une catégorie, mois par mois.
	category  : catégorie
	values    : montant net de chaque mois (dépense : positif = dépensé ; revenu : positif = reçu)
	average   : moyenne des mois complets suivis (hors mois en cours, hors mois
	            antérieurs à la première opération)
	lastGap   : dernier mois complet moins la moyenne des mois complets précédents ; null sans historique
*/
data class AnalysisRow(
	val category : Category,
	val values   : List<Long>,
	val average  : Long,
	val lastGap  : Long?
)
{
	// Total sur la période affichée.
	val total: Long get() = values.sum()
}

/*
	Tableau d'analyse des dépenses et des revenus, sur tous les comptes
	(opérations ordinaires ; virements et ordres exclus).
	months       : mois affichés, du plus ancien au mois en cours
	expenses     : catégories de dépense, de la plus dépensière à la moins dépensière
	incomes      : catégories de revenu, de la plus forte à la plus faible
	totalExpense : dépenses nettes de chaque mois
	totalIncome  : revenus de chaque mois
	savingsRate  : taux d'épargne de chaque mois, en pour mille ((revenus − dépenses) / revenus) ; null sans revenu
	rises        : catégories de dépense dont le dernier mois complet dépasse le plus la moyenne
	firstIndex   : premier mois suivi (première opération, hors achats et
	               ventes de biens, souvent anciens) ; les moyennes partent de là
*/
data class ExpenseAnalysis(
	val months       : List<YearMonth>,
	val expenses     : List<AnalysisRow>,
	val incomes      : List<AnalysisRow>,
	val totalExpense : List<Long>,
	val totalIncome  : List<Long>,
	val savingsRate  : List<Long?>,
	val rises        : List<AnalysisRow>,
	val firstIndex   : Int = 0
)
{
	// Rang du mois en cours (dernière colonne, incomplète).
	val currentIndex: Int get() = months.lastIndex

	// Dépenses moyennes des mois complets.
	val averageExpense: Long get() = averageOf(totalExpense.subList(firstIndex, currentIndex))

	// Revenus moyens des mois complets.
	val averageIncome: Long get() = averageOf(totalIncome.subList(firstIndex, currentIndex))

	// Taux d'épargne moyen des mois complets, en pour mille ; null sans revenu.
	val averageRate: Long? get() = averageIncome.takeIf { it > 0 }?.let { (it - averageExpense) * 1000 / it }
}

/*
	Calcule le tableau d'analyse des douze derniers mois (mois en cours
	compris). Fonction pure.
	inData  : données
	inToday : date du jour
*/
fun buildExpenseAnalysis(
	inData  : DataSet,
	inToday : LocalDate
): ExpenseAnalysis
{
	val vLast   = YearMonth.from(inToday)												// mois en cours
	val vMonths = (kAnalysisMonths - 1 downTo 0).map { vLast.minusMonths(it.toLong()) }	// mois affichés
	val vIndex  = vMonths.withIndex().associate { it.value to it.index }				// rang de chaque mois
	val vNet    = mutableMapOf<String, LongArray>()										// montant net par catégorie et par mois
	val vByCat  = inData.categories.associateBy { it.id }								// catégories par identifiant
	val vFirst  = inData.transactions.filterNot { it.kind == TransactionKind.ACQUISITION || it.kind == TransactionKind.DISPOSAL }.minOfOrNull { YearMonth.from(it.date) }?.let { inStart -> vIndex[inStart] ?: if (inStart.isBefore(vMonths.first())) 0 else vMonths.lastIndex } ?: vMonths.lastIndex	// premier mois suivi
	inData.transactions
		.filter { it.kind == TransactionKind.STANDARD && !it.date.isAfter(inToday) && it.categoryId in vByCat }
		.forEach { inTx ->
			val vColumn = vIndex[YearMonth.from(inTx.date)] ?: return@forEach			// mois hors tableau
			val vSign   = if (vByCat.getValue(inTx.categoryId!!).kind == CategoryKind.EXPENSE) -1L else 1L	// une dépense compte en positif
			vNet.getOrPut(inTx.categoryId) { LongArray(vMonths.size) }[vColumn] += vSign * inTx.postings.sumOf { it.amountCents }
		}
	val vRows = vNet.map { (inId, inValues) -> row(vByCat.getValue(inId), inValues.toList(), vFirst) }	// lignes non vides
	val vExpenses = vRows.filter { it.category.kind == CategoryKind.EXPENSE }.sortedByDescending { it.total }	// dépenses
	val vIncomes  = vRows.filter { it.category.kind == CategoryKind.INCOME }.sortedByDescending { it.total }	// revenus
	val vSpent    = vMonths.indices.map { inColumn -> vExpenses.sumOf { it.values[inColumn] } }	// dépenses du mois
	val vEarned   = vMonths.indices.map { inColumn -> vIncomes.sumOf { it.values[inColumn] } }	// revenus du mois
	return ExpenseAnalysis(
		months       = vMonths,
		expenses     = vExpenses,
		incomes      = vIncomes,
		totalExpense = vSpent,
		totalIncome  = vEarned,
		savingsRate  = vMonths.indices.map { inColumn -> vEarned[inColumn].takeIf { it > 0 }?.let { (it - vSpent[inColumn]) * 1000 / it } },
		rises        = vExpenses.filter { (it.lastGap ?: 0L) > 0L }.sortedByDescending { it.lastGap }.take(3),
		firstIndex   = vFirst
	)
}

/*
	Ligne d'une catégorie : moyenne des mois complets et écart du dernier mois
	complet à la moyenne des mois complets précédents.
	inCategory : catégorie
	inValues   : montant de chaque mois (le dernier est le mois en cours)
	inFirst    : premier mois suivi
*/
private fun row(
	inCategory : Category,
	inValues   : List<Long>,
	inFirst    : Int
): AnalysisRow
{
	val vComplete = inValues.subList(inFirst, inValues.lastIndex)						// mois complets suivis
	val vBefore   = vComplete.dropLast(1)												// mois complets avant le dernier
	return AnalysisRow(
		category = inCategory,
		values   = inValues,
		average  = averageOf(vComplete),
		lastGap  = if (vBefore.isEmpty()) null else vComplete.last() - averageOf(vBefore)	// au moins deux mois complets
	)
}

/*
	Moyenne arrondie à l'entier ; 0 sans valeur.
	inValues : valeurs
*/
private fun averageOf(inValues: List<Long>): Long = if (inValues.isEmpty()) 0L else Math.round(inValues.sum().toDouble() / inValues.size)
