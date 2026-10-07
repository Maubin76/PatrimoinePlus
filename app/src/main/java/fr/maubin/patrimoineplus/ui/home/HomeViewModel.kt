package fr.maubin.patrimoineplus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.Wealth
import fr.maubin.patrimoineplus.domain.calc.wealthAt
import fr.maubin.patrimoineplus.domain.model.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val kDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)	// format de la date de calcul

/*
	Ligne d'un compte dans l'accueil.
	name         : nom du compte
	detail       : type, établissement, part détenue si elle n'est pas de 100 %
	valueCents   : valeur entière du compte
	countedCents : valeur comptée dans le patrimoine (part détenue appliquée)
*/
data class HomeAccountLine(
	val name         : String,
	val detail       : String,
	val valueCents   : Long,
	val countedCents : Long = valueCents
)

/*
	Groupe de comptes d'un même type.
	type  : type des comptes du groupe
	lines : comptes du groupe, dans l'ordre d'affichage
*/
data class HomeGroup(
	val type  : AccountType,
	val lines : List<HomeAccountLine>
)
{
	// Sous-total compté dans le patrimoine, en centimes.
	val subtotalCents: Long get() = lines.sumOf { it.countedCents }
}

/*
	État de l'écran d'accueil.
	asOf   : date à laquelle le patrimoine est calculé, formatée
	groups : groupes de comptes non vides, dans l'ordre des types
*/
data class HomeUiState(
	val asOf   : String,
	val groups : List<HomeGroup>
)
{
	// Patrimoine total, en centimes : somme des valeurs comptées.
	val totalCents: Long get() = groups.sumOf { it.subtotalCents }

	// Vrai tant qu'aucun compte n'existe.
	val isEmpty: Boolean get() = groups.isEmpty()
}

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
	private val inClock : Clock
) : ViewModel()
{
	// État exposé à l'écran, recalculé à chaque changement de la base.
	val uiState: StateFlow<HomeUiState> =
		combine(inAccounts.observeAccounts(), inTransactions.observeTransactions(), inMarket.observePrices()) { inAccountList, inTxs, inPrices ->
			val vToday = LocalDate.now(inClock)									// date de calcul
			toHomeUiState(wealthAt(inAccountList, Ledger(inTxs), vToday, inPrices))
		}
			.flowOn(Dispatchers.Default)
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today(), emptyList()))

	/*
		Date du jour formatée.
	*/
	private fun today(): String = LocalDate.now(inClock).format(kDateFormat)
}

/*
	Met en forme le patrimoine calculé : groupes par type ; un compte archivé
	n'apparaît que s'il a encore une valeur.
	inWealth : patrimoine calculé
*/
internal fun toHomeUiState(inWealth: Wealth): HomeUiState =
	HomeUiState(
		asOf   = inWealth.date.format(kDateFormat),
		groups = AccountType.entries.mapNotNull { inType ->
			val vLines = inWealth.accounts
				.filter { it.account.type == inType && (!it.account.archived || it.valueCents != 0L) }
				.map { inValue ->
					HomeAccountLine(inValue.account.name, accountDetail(inValue.account), inValue.valueCents, inValue.countedCents)
				}																	// lignes du type
			if (vLines.isEmpty()) null else HomeGroup(inType, vLines)
		}
	)

/*
	Ligne de détail d'un compte : type, établissement, part détenue, archivage.
	inAccount : compte
*/
internal fun accountDetail(inAccount: fr.maubin.patrimoineplus.domain.model.Account): String =
	listOfNotNull(
		inAccount.type.label,
		inAccount.institution,
		if (inAccount.sharePercent != 100) "${inAccount.sharePercent}\u202F%" else null,
		if (inAccount.archived) "archivé" else null
	).joinToString(" · ")
