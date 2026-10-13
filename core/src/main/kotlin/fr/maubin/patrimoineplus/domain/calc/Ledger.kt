package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate
import java.time.YearMonth

/*
	Synthèse d'un compte sur un mois. L'invariant
	opening + openings + income + expenses + savings + transfers + trades = closing
	est garanti par construction et vérifié par les tests.
	month     : mois concerné
	opening   : solde d'ouverture = clôture calculée du mois précédent
	closing   : solde de clôture (opérations à venir du mois comprises)
	income    : entrées (opérations STANDARD positives, hors remboursements)
	expenses  : dépenses nettes (opérations STANDARD négatives, diminuées des
	            remboursements : montants positifs d'une catégorie de dépense)
	savings   : virements nets entre un compte Courant et un compte Épargne ou Bourse
	transfers : autres virements nets, achats et ventes de biens
	trades    : mouvements d'espèces des ordres de bourse
	openings  : soldes initiaux saisis dans le mois
*/
data class MonthSummary(
	val month     : YearMonth,
	val opening   : Long,
	val closing   : Long,
	val income    : Long,
	val expenses  : Long,
	val savings   : Long,
	val transfers : Long,
	val trades    : Long,
	val openings  : Long
)

/*
	Ligne d'une synthèse par catégorie.
	category : catégorie
	netCents : montant net sur la période ; pour une dépense, positif = dépensé
	percent  : part dans le total de même nature, arrondie à l'entier
*/
data class CategoryLine(
	val category : Category,
	val netCents : Long,
	val percent  : Int
)

/*
	Rang d'une opération dans sa journée : soldes initiaux, puis achats
	(titres, biens), puis le reste, puis ventes. Un achat et une vente du même jour se
	traitent donc toujours dans cet ordre, quel que soit l'ordre de saisie ou
	du fichier (quantités détenues, PRU, plus-values).
	inTx : opération
*/
internal fun dayRank(inTx: Transaction): Int =
	when
	{
		inTx.kind == TransactionKind.OPENING            -> 0
		inTx.kind == TransactionKind.ACQUISITION        -> 1
		inTx.kind == TransactionKind.DISPOSAL           -> 3
		inTx.trades.any { it.side == TradeSide.BUY }  -> 1
		inTx.trades.any { it.side == TradeSide.SELL } -> 3
		else                                          -> 2
	}

// Ordre canonique des opérations : date, rang dans la journée, identifiant.
val kChronological: Comparator<Transaction> = compareBy<Transaction>({ it.date }, { dayRank(it) }, { it.id })

/*
	Grand livre : toutes les opérations, indexées par compte. Calcule soldes et
	synthèses à la demande ; aucun solde n'est stocké.
	inTransactions : toutes les opérations
*/
class Ledger(inTransactions: List<Transaction>)
{
	private val fTransactions = inTransactions.sortedWith(kChronological)	// opérations dans l'ordre canonique

	/*
		Solde d'un compte à une date : somme de ses jambes datées au plus tard
		ce jour-là.
		inAccountId : compte
		inDate      : date incluse
	*/
	fun balanceAt(
		inAccountId : String,
		inDate      : LocalDate
	): Long =
		fTransactions
			.asSequence()
			.filter { !it.date.isAfter(inDate) }
			.flatMap { it.postings.asSequence() }
			.filter { it.accountId == inAccountId }
			.sumOf { it.amountCents }

	/*
		Synthèse d'un compte sur un mois, ventilée par nature de mouvement.
		inAccount      : compte
		inMonth        : mois
		inAccountsById : tous les comptes, pour qualifier les virements
		inExpenseIds   : catégories de dépense : un montant positif dans l'une
		                 d'elles est un remboursement, qui réduit les dépenses
	*/
	fun monthSummary(
		inAccount      : Account,
		inMonth        : YearMonth,
		inAccountsById : Map<String, Account>,
		inExpenseIds   : Set<String> = emptySet()
	): MonthSummary
	{
		var vIncome    = 0L													// entrées
		var vExpenses  = 0L													// dépenses
		var vSavings   = 0L													// épargne
		var vTransfers = 0L													// autres virements
		var vTrades    = 0L													// ordres
		var vOpenings  = 0L													// soldes initiaux
		fTransactions
			.filter { YearMonth.from(it.date) == inMonth }
			.forEach { inTx ->
				inTx.postings.filter { it.accountId == inAccount.id }.forEach { inPosting ->
					val vAmount = inPosting.amountCents						// montant de la jambe
					when (inTx.kind)
					{
						TransactionKind.STANDARD -> if (vAmount < 0 || inTx.categoryId in inExpenseIds) vExpenses += vAmount else vIncome += vAmount
						TransactionKind.TRADE    -> vTrades += vAmount
						TransactionKind.OPENING  -> vOpenings += vAmount
						TransactionKind.ACQUISITION, TransactionKind.DISPOSAL -> vTransfers += vAmount
						TransactionKind.LOAN_PAYMENT ->
						{
							val vCharge = if (vAmount < 0) inTx.categoryAmount() else 0L		// intérêts et assurance, portés par le payeur
							vExpenses  += vCharge
							vTransfers += vAmount - vCharge
						}
						TransactionKind.TRANSFER ->
						{
							val vOther = inTx.postings.firstOrNull { it.accountId != inAccount.id }	// jambe opposée
							val vType  = vOther?.let { inAccountsById[it.accountId]?.type }		// type du compte opposé
							if (isSavingsFlow(inAccount.type, vType)) vSavings += vAmount else vTransfers += vAmount
						}
					}
				}
			}
		return MonthSummary(
			month     = inMonth,
			opening   = balanceAt(inAccount.id, inMonth.minusMonths(1).atEndOfMonth()),
			closing   = balanceAt(inAccount.id, inMonth.atEndOfMonth()),
			income    = vIncome,
			expenses  = vExpenses,
			savings   = vSavings,
			transfers = vTransfers,
			trades    = vTrades,
			openings  = vOpenings
		)
	}

	/*
		Synthèse par catégorie d'un compte sur une période, à 100 % (la part
		détenue ne s'applique qu'au patrimoine). Les remboursements réduisent la
		dépense de leur catégorie. Triée par montant décroissant.
		inAccountId  : compte
		inFrom       : début de période, inclus
		inTo         : fin de période, incluse
		inCategories : catégories connues
		inKind       : nature de catégorie à synthétiser
	*/
	fun categorySummary(
		inAccountId  : String,
		inFrom       : LocalDate,
		inTo         : LocalDate,
		inCategories : List<Category>,
		inKind       : CategoryKind
	): List<CategoryLine>
	{
		val vSign   = if (inKind == CategoryKind.EXPENSE) -1L else 1L		// une dépense compte en positif
		val vByCat  = inCategories.filter { it.kind == inKind }.associateBy { it.id }	// catégories de la nature voulue
		val vTotals = fTransactions
			.filter { it.hasCategory && !it.date.isBefore(inFrom) && !it.date.isAfter(inTo) }
			.filter { it.categoryId in vByCat }
			.groupBy { it.categoryId!! }
			.mapValues { (_, inTxs) ->
				vSign * inTxs.sumOf { it.categoryAmount(inAccountId) }
			}
			.filterValues { it != 0L }											// sommes nettes par catégorie
		val vBase = vTotals.values.filter { it > 0 }.sum()						// base des pourcentages
		return vTotals
			.map { (inId, inNet) ->
				CategoryLine(vByCat.getValue(inId), inNet, if (vBase > 0 && inNet > 0) roundDiv(inNet * 100, vBase).toInt() else 0)
			}
			.sortedByDescending { it.netCents }
	}

	/*
		Toutes les opérations, dans l'ordre canonique (kChronological).
	*/
	fun transactions(): List<Transaction> = fTransactions
}

/*
	Vrai si un virement compte comme de l'épargne : entre un compte Courant et
	un compte Épargne ou Bourse, dans un sens ou dans l'autre.
	inOwnType   : type du compte dont on fait la synthèse
	inOtherType : type du compte opposé ; null s'il est inconnu
*/
internal fun isSavingsFlow(
	inOwnType   : AccountType,
	inOtherType : AccountType?
): Boolean
{
	val vPlacement = setOf(AccountType.SAVINGS, AccountType.BROKERAGE)		// types de placement
	return (inOwnType == AccountType.CHECKING && inOtherType in vPlacement) ||
		(inOwnType in vPlacement && inOtherType == AccountType.CHECKING)
}
