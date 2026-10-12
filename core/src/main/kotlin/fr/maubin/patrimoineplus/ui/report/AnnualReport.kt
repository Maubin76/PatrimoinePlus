package fr.maubin.patrimoineplus.ui.report

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint
import fr.maubin.patrimoineplus.domain.calc.amountOn
import fr.maubin.patrimoineplus.domain.calc.valuePortfolio
import fr.maubin.patrimoineplus.domain.calc.wealthAt
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate
import java.time.YearMonth

/*
	Valeur d'un compte au début et à la fin de l'année.
	account : compte
	start   : valeur au 31 décembre de l'année précédente
	end     : valeur à la fin de la période
	share   : part détenue, en % (la valeur comptée dans le patrimoine en découle)
*/
data class AccountYear(
	val account : Account,
	val start   : Long,
	val end     : Long,
	val share   : Int
)

/*
	Montant d'une catégorie sur l'année.
	category : catégorie
	cents    : montant net (dépense : positif = dépensé ; revenu : positif = reçu)
*/
data class CategoryYear(
	val category : Category,
	val cents    : Long
)

/*
	Plus-value réalisée sur un titre pendant l'année.
	assetName : nom du titre
	cents     : plus-value (négative = moins-value)
*/
data class RealizedYear(
	val assetName : String,
	val cents     : Long
)

/*
	Bilan d'une année : patrimoine au début et à la fin, comptes, dépenses et
	revenus par catégorie, intérêts, dividendes et plus-values réalisées.
	year        : année
	from        : 1er janvier
	to          : 31 décembre, ou date du jour pour l'année en cours
	partial     : année en cours (bilan arrêté à aujourd'hui)
	wealthStart : patrimoine compté au 31 décembre de l'année précédente
	wealthEnd   : patrimoine compté à la fin de la période
	accounts    : comptes qui ont une valeur ou un mouvement sur l'année
	expenses    : dépenses nettes par catégorie, de la plus forte à la plus faible
	incomes     : revenus par catégorie
	interest    : revenus reçus sur les comptes Épargne
	dividends   : revenus reçus sur les comptes Bourse
	realized    : plus-values réalisées, par titre
	months      : patrimoine à la fin de chaque mois de la période
*/
data class AnnualReport(
	val year        : Int,
	val from        : LocalDate,
	val to          : LocalDate,
	val partial     : Boolean,
	val wealthStart : Long,
	val wealthEnd   : Long,
	val accounts    : List<AccountYear>,
	val expenses    : List<CategoryYear>,
	val incomes     : List<CategoryYear>,
	val interest    : Long,
	val dividends   : Long,
	val realized    : List<RealizedYear>,
	val months      : List<SeriesPoint>
)
{
	// Total des dépenses nettes.
	val totalExpenses: Long get() = expenses.sumOf { it.cents }

	// Total des revenus.
	val totalIncomes: Long get() = incomes.sumOf { it.cents }

	// Total des plus-values réalisées.
	val totalRealized: Long get() = realized.sumOf { it.cents }

	// Variation du patrimoine sur la période.
	val wealthChange: Long get() = wealthEnd - wealthStart

	// Taux d'épargne de l'année, en pour mille ; null sans revenu.
	val savingsRate: Long? get() = totalIncomes.takeIf { it > 0 }?.let { (it - totalExpenses) * 1000 / it }
}

/*
	Années pour lesquelles un bilan a un sens : de la première opération à
	l'année en cours, la plus récente d'abord.
	inData  : données
	inToday : date du jour
*/
fun reportYears(
	inData  : DataSet,
	inToday : LocalDate
): List<Int>
{
	val vFirst = inData.transactions.minOfOrNull { it.date.year }?.coerceAtMost(inToday.year) ?: inToday.year	// première année
	return (inToday.year downTo vFirst).toList()
}

/*
	Calcule le bilan d'une année. Fonction pure.
	inData  : données
	inYear  : année
	inToday : date du jour (fin du bilan de l'année en cours)
*/
fun buildAnnualReport(
	inData  : DataSet,
	inYear  : Int,
	inToday : LocalDate
): AnnualReport
{
	val vLedger  = Ledger(inData.transactions)										// grand livre
	val vFrom    = LocalDate.of(inYear, 1, 1)										// début
	val vPartial = inYear == inToday.year											// année en cours
	val vTo      = if (vPartial) inToday else LocalDate.of(inYear, 12, 31)		// fin
	val vStart   = wealthAt(inData.accounts, vLedger, vFrom.minusDays(1), inData.prices)	// patrimoine au début
	val vEnd     = wealthAt(inData.accounts, vLedger, vTo, inData.prices)		// patrimoine à la fin
	val vByCat   = inData.categories.associateBy { it.id }						// catégories par identifiant
	val vYearTxs = inData.transactions.filter { !it.date.isBefore(vFrom) && !it.date.isAfter(vTo) }	// opérations de la période
	val vNet     = vYearTxs
		.filter { it.kind == TransactionKind.STANDARD && it.categoryId in vByCat }
		.groupBy { it.categoryId!! }
		.map { (inId, inTxs) ->
			val vCategory = vByCat.getValue(inId)									// catégorie
			val vSign     = if (vCategory.kind == CategoryKind.EXPENSE) -1L else 1L	// une dépense compte en positif
			CategoryYear(vCategory, vSign * inTxs.sumOf { inTx -> inTx.postings.sumOf { it.amountCents } })
		}
		.filter { it.cents != 0L }													// montants par catégorie
	val vTypes   = inData.accounts.associate { it.id to it.type }					// type de chaque compte
	val vIncome  = { inType: AccountType ->
		vYearTxs.filter { it.kind == TransactionKind.STANDARD && vByCat[it.categoryId]?.kind == CategoryKind.INCOME }
			.sumOf { inTx -> inTx.postings.filter { vTypes[it.accountId] == inType }.sumOf { it.amountCents } }
	}																				// revenus reçus sur un type de compte
	val vNames   = inData.assets.associate { it.id to it.name }					// noms des titres
	val vGains   = inData.accounts.filter { it.type == AccountType.BROKERAGE }
		.flatMap { inAccount -> valuePortfolio(vLedger, inAccount.id, vTo, inData.prices).realized }
		.filter { !it.date.isBefore(vFrom) && !it.date.isAfter(vTo) }
		.groupBy { it.assetId }
		.map { (inAsset, inGains) -> RealizedYear(vNames[inAsset] ?: "Titre supprimé", inGains.sumOf { it.gainCents }) }
		.sortedByDescending { it.cents }											// plus-values par titre
	return AnnualReport(
		year        = inYear,
		from        = vFrom,
		to          = vTo,
		partial     = vPartial,
		wealthStart = vStart.totalCents,
		wealthEnd   = vEnd.totalCents,
		accounts    = vEnd.accounts.mapNotNull { inEndValue ->
			val vBegin = vStart.accounts.first { it.account.id == inEndValue.account.id }.valueCents	// valeur au début
			val vMoved = vYearTxs.any { inTx -> amountOn(inTx, inEndValue.account.id) != 0L || inTx.postings.any { it.accountId == inEndValue.account.id } }	// mouvement dans l'année
			if (vBegin == 0L && inEndValue.valueCents == 0L && !vMoved) null
			else AccountYear(inEndValue.account, vBegin, inEndValue.valueCents, inEndValue.account.sharePercent)
		},
		expenses    = vNet.filter { it.category.kind == CategoryKind.EXPENSE }.sortedByDescending { it.cents },
		incomes     = vNet.filter { it.category.kind == CategoryKind.INCOME }.sortedByDescending { it.cents },
		interest    = vIncome(AccountType.SAVINGS),
		dividends   = vIncome(AccountType.BROKERAGE),
		realized    = vGains,
		months      = (1..YearMonth.from(vTo).monthValue).map { inMonth ->
			val vMonth = YearMonth.of(inYear, inMonth)								// mois
			val vDay   = if (vMonth == YearMonth.from(vTo)) vTo else vMonth.atEndOfMonth()	// fin du mois (ou aujourd'hui)
			SeriesPoint(vMonth, wealthAt(inData.accounts, vLedger, vDay, inData.prices).totalCents)
		}
	)
}
