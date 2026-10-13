package fr.maubin.patrimoineplus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import fr.maubin.patrimoineplus.data.repo.PlanningRepository

/*
	ViewModel de l'accueil : lit comptes, opérations et cours dans la base et
	confie tous les calculs au domaine. Rien n'est stocké ni mis en cache.
	inAccounts     : dépôt des comptes
	inTransactions : dépôt des opérations
	inMarket       : dépôt des cours
	inClock        : horloge de l'application
*/
@HiltViewModel
class HomeViewModel @Inject constructor(
	inAccounts     : AccountRepository,
	inTransactions : TransactionRepository,
	inMarket       : MarketRepository,
	private val inClock : Clock,
	inPlanning     : PlanningRepository
) : ViewModel()
{
	// État exposé à l'écran, recalculé à chaque changement de la base.
	val uiState: StateFlow<HomeUiState> =
		combine(inAccounts.observeAccounts(), inTransactions.observeTransactions(), inMarket.observePrices(), inPlanning.observeLoans()) { inAccountList, inTxs, inPrices, inLoans ->
			buildHomeState(inAccountList, inTxs, inPrices, LocalDate.now(inClock), inLoans)
		}
			.flowOn(Dispatchers.Default)
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today(), emptyList()))

	/*
		Date du jour formatée.
	*/
	private fun today(): String = homeDate(LocalDate.now(inClock))
}
