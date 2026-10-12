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
