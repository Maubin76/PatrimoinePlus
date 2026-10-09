package fr.maubin.patrimoineplus.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.market.Listing
import fr.maubin.patrimoineplus.data.market.QuoteRefresher
import fr.maubin.patrimoineplus.data.market.QuoteSource
import fr.maubin.patrimoineplus.data.market.findEuroListing
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
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
import fr.maubin.patrimoineplus.ui.navigation.AccountRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
	ViewModel de la vue d'un compte : mois par mois (Courant) ou globale
	(Épargne, Bourse) ; tout est calculé par le domaine à partir de la base.
	inState        : arguments de navigation (identifiant du compte)
	inAccounts     : dépôt des comptes
	inCategories   : dépôt des catégories
	inTransactions : dépôt des opérations
	inMarket       : dépôt des actifs, cours et relevés
	inQuotes       : mise à jour automatique des cours
	inSource       : source de cours (recherche d'une cotation)
	inClock        : horloge
*/
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
	inState                : SavedStateHandle,
	private val inAccounts : AccountRepository,
	inCategories           : CategoryRepository,
	inTransactions         : TransactionRepository,
	private val inMarket   : MarketRepository,
	private val inQuotes   : QuoteRefresher,
	private val inSource   : QuoteSource,
	private val inClock    : Clock
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

	// Relevés ou portefeuille du compte, à jour.
	val extras: StateFlow<AccountExtras> =
		combine(
			combine(inAccounts.observeAccounts(), inTransactions.observeTransactions(), ::Pair),
			combine(inMarket.observeStatements(), inMarket.observePrices(), inMarket.observeAssets(), ::Triple)
		) { (inList, inTxs), (inStatements, inPrices, inAssets) ->
			buildAccountExtras(accountId, inList, inTxs, inStatements, inPrices, inAssets, LocalDate.now(inClock))
		}
			.flowOn(Dispatchers.Default)
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountExtras())

	// Vrai pendant une mise à jour des cours.
	val refreshing = MutableStateFlow(false)

	// Instant de la dernière mise à jour automatique des cours (ms) ; null avant la première.
	val quotesUpdatedAt: StateFlow<Long?> = inQuotes.lastUpdate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

	/*
		Met à jour les cours : à la demande (geste « tirer », bouton), ou à
		l'ouverture du compte si les cours datent de plus de quatre heures.
		Une mise à jour automatique ne montre de message qu'en cas de titre
		sans cotation.
		inForce     : vrai pour une demande explicite
		inOnMessage : message à afficher
	*/
	fun refreshQuotes(
		inForce     : Boolean,
		inOnMessage : (String) -> Unit
	)
	{
		if (refreshing.value)
		{
			return
		}
		refreshing.value = true
		viewModelScope.launch {
			val vOutcome = if (inForce) inQuotes.refreshNow() else inQuotes.refreshIfStale()	// résultat
			refreshing.value = false
			when
			{
				vOutcome == null                                 -> Unit
				vOutcome.error != null                           -> if (inForce) inOnMessage("Cours non mis à jour : ${vOutcome.error}. Le dernier cours connu reste utilisé.")
				vOutcome.unresolved.isNotEmpty()                 -> inOnMessage("Sans cours automatique : ${vOutcome.unresolved.joinToString(", ")}. Touchez le titre pour indiquer son ISIN.")
				inForce                                          -> inOnMessage("Cours mis à jour.")
			}
		}
	}

	/*
		Cherche la cotation en euros d'un ISIN, en essayant d'autres places si
		besoin (le résultat est montré pour confirmation).
		inIsin     : code ISIN
		inOnResult : cotation trouvée (null si aucune) ou message d'erreur
	*/
	fun lookupListing(
		inIsin     : String,
		inOnResult : (Listing?, String?) -> Unit
	)
	{
		viewModelScope.launch {
			try
			{
				inOnResult(findEuroListing(inSource, inIsin, inGuess = true, inToday = today()), null)
			}
			catch (inError: Exception)
			{
				inOnResult(null, "Recherche impossible : ${inError.message ?: "service injoignable"}.")
			}
		}
	}

	/*
		Enregistre la fiche d'un actif, puis met à jour les cours (l'historique
		est récupéré pour un nouveau symbole).
		inId        : actif
		inName      : nom
		inIsin      : ISIN
		inTicker    : symbole de cotation
		inOnMessage : message à afficher
	*/
	fun saveAsset(
		inId        : String,
		inName      : String,
		inIsin      : String,
		inTicker    : String,
		inOnMessage : (String) -> Unit
	)
	{
		viewModelScope.launch {
			inMarket.updateAsset(inId, inName, inIsin, inTicker)
			if (inTicker.isNotBlank())
			{
				refreshQuotes(inForce = true, inOnMessage = inOnMessage)
			}
		}
	}

	/*
		Date du jour, selon l'horloge de l'application.
	*/
	fun today(): LocalDate = LocalDate.now(inClock)

	/*
		Enregistre un relevé de la banque.
		inDate  : date du relevé
		inCents : solde indiqué par la banque
	*/
	fun addStatement(
		inDate  : LocalDate,
		inCents : Long
	)
	{
		viewModelScope.launch { inMarket.putStatement(accountId, inDate, inCents) }
	}

	/*
		Supprime un relevé.
		inId : relevé
	*/
	fun deleteStatement(inId: String)
	{
		viewModelScope.launch { inMarket.deleteStatement(inId) }
	}

	/*
		Enregistre les cours saisis, tous à la même date.
		inDate   : date des cours
		inPrices : cours par actif, en dix-millièmes d'euro
	*/
	fun updatePrices(
		inDate   : LocalDate,
		inPrices : Map<String, Long>
	)
	{
		viewModelScope.launch { inMarket.putPrices(inDate, inPrices) }
	}

	/*
		Ajoute une position initiale (actif existant ou nouveau) au solde initial.
		inAssetId   : actif existant ; null pour en créer un
		inNewName   : nom de l'actif à créer
		inQuantity  : quantité, en dix-millièmes
		inUnitPrice : prix de revient unitaire, en dix-millièmes d'euro
		inDate      : date du solde initial s'il faut le créer
	*/
	fun addOpeningPosition(
		inAssetId   : String?,
		inNewName   : String,
		inQuantity  : Long,
		inUnitPrice : Long,
		inDate      : LocalDate
	)
	{
		viewModelScope.launch {
			val vAsset = inAssetId ?: (inMarket.createAsset(inNewName) as? SaveResult.Saved)?.id ?: return@launch	// actif
			inAccounts.addOpeningPosition(accountId, vAsset, inQuantity, inUnitPrice, inDate)
		}
	}

	/*
		Retire une position initiale.
		inTradeId : position
	*/
	fun removeOpeningPosition(inTradeId: String)
	{
		viewModelScope.launch { inAccounts.removeOpeningPosition(accountId, inTradeId) }
	}

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
