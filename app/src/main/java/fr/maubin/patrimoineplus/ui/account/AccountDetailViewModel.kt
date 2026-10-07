package fr.maubin.patrimoineplus.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.domain.calc.CategoryLine
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.MonthSummary
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint
import fr.maubin.patrimoineplus.domain.calc.amountOn
import fr.maubin.patrimoineplus.domain.calc.balanceSeries
import fr.maubin.patrimoineplus.domain.calc.monthTransactions
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.ui.navigation.AccountRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

private const val kSeriesMonths = 12											// mois de la courbe d'évolution

/*
	Opération affichée dans la liste d'un mois.
	transaction : opération
	amount      : montant vu du compte (signé)
	title       : libellé
	subtitle    : catégorie, ou nature (virement, ordre, solde initial)
	icon        : clé d'icône
	upcoming    : opération à venir (datée après aujourd'hui)
	editable    : modifiable par la feuille de saisie (dépense ou revenu)
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
	État de la vue d'un compte pour un mois.
	account    : compte ; null s'il n'existe plus
	month      : mois affiché
	firstMonth : premier mois consultable
	lastMonth  : dernier mois consultable
	summary    : synthèse du mois
	expenses   : dépenses nettes par catégorie
	series     : soldes de clôture des douze derniers mois (au plus), depuis l'ouverture du compte
	upcoming   : opérations à venir du mois
	days       : autres opérations du mois, par jour décroissant
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
	val days       : List<DayGroup>      = emptyList()
)

/*
	ViewModel de la vue d'un compte, mois par mois : synthèse, dépenses par
	catégorie, évolution du solde et opérations ; tout est calculé par le
	domaine à partir de la base.
	inState        : arguments de navigation (identifiant du compte)
	inAccounts     : dépôt des comptes
	inCategories   : dépôt des catégories
	inTransactions : dépôt des opérations
	inClock        : horloge
*/
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
	inState             : SavedStateHandle,
	inAccounts          : AccountRepository,
	inCategories        : CategoryRepository,
	inTransactions      : TransactionRepository,
	private val inClock : Clock
) : ViewModel()
{
	// Identifiant du compte affiché.
	val accountId: String = inState.toRoute<AccountRoute>().accountId

	private val fThisMonth = YearMonth.now(inClock)							// mois courant
	private val fMonth     = MutableStateFlow(fThisMonth)						// mois affiché

	// État exposé à l'écran.
	val uiState: StateFlow<AccountDetailState> =
		combine(inAccounts.observeAccounts(), inCategories.observeCategories(), inTransactions.observeTransactions(), fMonth) { inList, inCats, inTxs, inMonth ->
			buildAccountDetail(accountId, inList, inCats, inTxs, inMonth, LocalDate.now(inClock))
		}
			.flowOn(Dispatchers.Default)
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailState(month = fThisMonth, firstMonth = fThisMonth, lastMonth = fThisMonth))

	/*
		Affiche un autre mois, borné aux mois consultables.
		inMonth : mois voulu
	*/
	fun showMonth(inMonth: YearMonth)
	{
		val vState = uiState.value												// bornes courantes
		fMonth.value = inMonth.coerceIn(vState.firstMonth, vState.lastMonth)
	}
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
internal fun buildAccountDetail(
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
	val vItems     = monthTransactions(vLedger, inAccountId, inMonth).map { inTx ->
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
			editable    = inTx.kind == TransactionKind.STANDARD
		)
	}																			// opérations du mois
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
