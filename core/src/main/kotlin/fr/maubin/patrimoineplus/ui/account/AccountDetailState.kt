package fr.maubin.patrimoineplus.ui.account

import fr.maubin.patrimoineplus.domain.calc.CategoryLine
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.MonthSummary
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint
import fr.maubin.patrimoineplus.domain.calc.AccountTotals
import fr.maubin.patrimoineplus.domain.calc.accountTotals
import fr.maubin.patrimoineplus.domain.calc.accountValueSeries
import fr.maubin.patrimoineplus.domain.calc.amountOn
import fr.maubin.patrimoineplus.domain.calc.balanceSeries
import fr.maubin.patrimoineplus.domain.calc.monthTransactions
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate
import java.time.YearMonth

private const val kSeriesMonths = 12											// mois de la courbe d'évolution

/*
	Opération affichée dans la liste d'un mois.
	transaction : opération
	amount      : montant vu du compte (signé)
	title       : libellé
	subtitle    : catégorie, ou nature (virement, ordre, solde initial)
	icon        : clé d'icône
	upcoming    : opération à venir (datée après aujourd'hui)
	editable    : modifiable par la feuille de saisie (tout sauf le solde initial)
*/
data class OperationItem(
	val transaction : Transaction,
	val amount      : Long,
	val title       : String,
	val subtitle    : String,
	val icon        : String,
	val upcoming    : Boolean,
	val editable    : Boolean
)

/*
	Opérations d'un même jour.
	date  : jour
	items : opérations du jour
*/
data class DayGroup(
	val date  : LocalDate,
	val items : List<OperationItem>
)

/*
	Opérations d'un même mois (vue globale des comptes Épargne et Bourse).
	month : mois
	items : opérations du mois, de la plus récente à la plus ancienne
*/
data class MonthGroup(
	val month : YearMonth,
	val items : List<OperationItem>
)

/*
	État de la vue d'un compte : mois par mois pour un compte Courant, vue
	globale (depuis l'ouverture) pour un compte Épargne ou Bourse.
	account    : compte ; null s'il n'existe plus
	month      : mois affiché
	firstMonth : premier mois consultable
	lastMonth  : dernier mois consultable
	summary    : synthèse du mois
	expenses   : dépenses nettes par catégorie
	series     : soldes de clôture des douze derniers mois (au plus), depuis l'ouverture du compte
	upcoming   : opérations à venir (du mois, ou toutes en vue globale)
	days       : autres opérations du mois, par jour décroissant (vue mensuelle)
	months     : autres opérations, par mois décroissant (vue globale)
	totals     : mouvements depuis l'ouverture (vue globale)
*/
data class AccountDetailState(
	val account    : Account?            = null,
	val month      : YearMonth,
	val firstMonth : YearMonth,
	val lastMonth  : YearMonth,
	val summary    : MonthSummary?       = null,
	val expenses   : List<CategoryLine>  = emptyList(),
	val series     : List<SeriesPoint>   = emptyList(),
	val upcoming   : List<OperationItem> = emptyList(),
	val days       : List<DayGroup>      = emptyList(),
	val months     : List<MonthGroup>    = emptyList(),
	val totals     : AccountTotals?      = null
)
{
	// Vrai pour la vue globale : comptes Épargne et Bourse.
	val isGlobal: Boolean get() = account != null && account.type != AccountType.CHECKING

	// Toutes les opérations affichées (sélection multiple).
	val allItems: List<OperationItem> get() = upcoming + days.flatMap { it.items } + months.flatMap { it.items }
}


/*
	Calcule l'état de la vue d'un compte pour un mois. Fonction pure, utilisée
	par le ViewModel et par les captures d'écran.
	inAccountId    : compte affiché
	inAccounts     : tous les comptes
	inCategories   : toutes les catégories
	inTransactions : toutes les opérations
	inMonth        : mois affiché
	inToday        : date du jour
*/
fun buildAccountDetail(
	inAccountId    : String,
	inAccounts     : List<Account>,
	inCategories   : List<Category>,
	inTransactions : List<Transaction>,
	inMonth        : YearMonth,
	inToday        : LocalDate
): AccountDetailState
{
	val vThisMonth = YearMonth.from(inToday)									// mois courant
	val vByAccount = inAccounts.associateBy { it.id }							// comptes par identifiant
	val vAccount   = vByAccount[inAccountId]									// compte affiché
	val vLedger    = Ledger(inTransactions)										// grand livre
	val vMine      = inTransactions.filter { inTx -> inTx.postings.any { it.accountId == inAccountId } }	// opérations du compte
	val vFirst     = vMine.minOfOrNull { YearMonth.from(it.date) }?.coerceAtMost(vThisMonth) ?: vThisMonth	// premier mois
	val vLast      = vMine.maxOfOrNull { YearMonth.from(it.date) }?.coerceAtLeast(vThisMonth) ?: vThisMonth	// dernier mois
	val vCatById   = inCategories.associateBy { it.id }							// catégories par identifiant
	val vGlobal    = vAccount != null && vAccount.type != AccountType.CHECKING	// vue globale
	val vShown     = if (vGlobal) vMine.sortedByDescending { it.date } else monthTransactions(vLedger, inAccountId, inMonth)	// opérations affichées
	val vItems     = vShown.map { inTx ->
		val vOther = inTx.postings.firstOrNull { it.accountId != inAccountId }?.let { vByAccount[it.accountId]?.name }	// compte opposé
		val vCat   = inTx.categoryId?.let { vCatById[it] }						// catégorie
		val vAmt   = amountOn(inTx, inAccountId)								// montant vu du compte
		OperationItem(
			transaction = inTx,
			amount      = vAmt,
			title       = inTx.label,
			subtitle    = when (inTx.kind)
			{
				TransactionKind.STANDARD -> vCat?.name ?: "Sans catégorie"
				TransactionKind.TRANSFER -> if (vAmt < 0) "Virement vers ${vOther ?: "un compte supprimé"}" else "Virement depuis ${vOther ?: "un compte supprimé"}"
				TransactionKind.TRADE    -> "Ordre de bourse"
				TransactionKind.OPENING  -> "Solde initial"
			},
			icon        = when (inTx.kind)
			{
				TransactionKind.STANDARD -> vCat?.icon ?: "dots"
				TransactionKind.TRANSFER -> "repeat"
				TransactionKind.TRADE    -> "chart"
				TransactionKind.OPENING  -> "plus"
			},
			upcoming    = inTx.isUpcoming(inToday),
			editable    = inTx.kind != TransactionKind.OPENING
		)
	}																			// opérations affichées
	if (vAccount != null && vAccount.type != AccountType.CHECKING)
	{
		return AccountDetailState(
			account    = vAccount,
			month      = vThisMonth,
			firstMonth = vThisMonth,
			lastMonth  = vThisMonth,
			series     = accountValueSeries(vAccount, vLedger, emptyList(), inToday),
			upcoming   = vItems.filter { it.upcoming },
			months     = vItems.filterNot { it.upcoming }.groupBy { YearMonth.from(it.transaction.date) }.map { (inMonth, inList) -> MonthGroup(inMonth, inList) },
			totals     = accountTotals(vLedger, inAccountId, inToday)
		)
	}
	val vSeriesEnd = if (inMonth.isBefore(vThisMonth.minusMonths(kSeriesMonths - 1L))) inMonth else maxOf(vThisMonth, inMonth)	// fin de la courbe
	return AccountDetailState(
		account    = vAccount,
		month      = inMonth,
		firstMonth = vFirst,
		lastMonth  = vLast,
		summary    = vAccount?.let { vLedger.monthSummary(it, inMonth, vByAccount) },
		expenses   = vLedger.categorySummary(inAccountId, inMonth.atDay(1), inMonth.atEndOfMonth(), inCategories, CategoryKind.EXPENSE),
		series     = balanceSeries(vLedger, inAccountId, vSeriesEnd, kSeriesMonths).filter { !it.month.isBefore(vFirst) || it.month == inMonth },
		upcoming   = vItems.filter { it.upcoming },
		days       = vItems.filterNot { it.upcoming }.groupBy { it.transaction.date }.map { (inDate, inList) -> DayGroup(inDate, inList) }
	)
}
