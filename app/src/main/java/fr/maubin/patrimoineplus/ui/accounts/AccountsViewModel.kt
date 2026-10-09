package fr.maubin.patrimoineplus.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.Opening
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.accountValue
import fr.maubin.patrimoineplus.domain.calc.wholePercents
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.domain.rules.SetupError
import fr.maubin.patrimoineplus.domain.rules.kMaxShare
import fr.maubin.patrimoineplus.domain.rules.kMinShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/*
	Compte affiché dans la liste.
	account    : compte
	valueCents : valeur entière aujourd'hui
	operations : nombre d'opérations, hors solde initial
	weight     : part du compte dans le patrimoine (part détenue appliquée) ; null s'il n'y compte pas (valeur nulle ou négative)
*/
data class AccountItem(
	val account    : Account,
	val valueCents : Long,
	val operations : Int,
	val weight     : AccountWeight? = null
)

/*
	Part d'un compte dans le patrimoine.
	percent : part entière en pour cent (les parts de tous les comptes font 100)
	rank    : rang de valeur comptée (0 : le plus gros), qui fixe sa teinte
	counted : valeur comptée dans le patrimoine, en centimes (largeur dans la barre)
*/
data class AccountWeight(
	val percent : Int,
	val rank    : Int,
	val counted : Long
)

/*
	État de l'écran des comptes.
	loaded   : vrai une fois la base lue
	active   : comptes actifs, dans l'ordre d'affichage
	archived : comptes archivés
*/
data class AccountsUiState(
	val loaded   : Boolean           = false,
	val active   : List<AccountItem> = emptyList(),
	val archived : List<AccountItem> = emptyList()
)

/*
	Saisie du formulaire de compte, telle que tapée.
	name        : nom
	type        : type
	institution : établissement
	share       : part détenue, en % (texte)
	opening     : solde initial (texte) ; vide = aucun
	openingDate : date du solde initial
*/
data class AccountForm(
	val name        : String      = "",
	val type        : AccountType = AccountType.CHECKING,
	val institution : String      = "",
	val share       : String      = "100",
	val opening     : String      = "",
	val openingDate : LocalDate
)

/*
	Erreurs du formulaire de compte, par champ.
	name    : erreur sur le nom
	share   : erreur sur la part détenue
	opening : erreur sur le solde initial
	general : erreur globale
*/
data class AccountFormErrors(
	val name    : String? = null,
	val share   : String? = null,
	val opening : String? = null,
	val general : String? = null
)
{
	// Vrai sans aucune erreur.
	val isEmpty: Boolean get() = name == null && share == null && opening == null && general == null
}

/*
	ViewModel de l'écran des comptes : liste avec valeurs, création,
	modification, archivage, ordre et suppression.
	inAccounts     : dépôt des comptes
	inTransactions : dépôt des opérations
	inMarket       : dépôt des cours
	inClock        : horloge
*/
@HiltViewModel
class AccountsViewModel @Inject constructor(
	private val inAccounts : AccountRepository,
	inTransactions         : TransactionRepository,
	inMarket               : MarketRepository,
	private val inClock    : Clock
) : ViewModel()
{
	// État exposé à l'écran, recalculé à chaque changement de la base.
	val uiState: StateFlow<AccountsUiState> =
		combine(inAccounts.observeAccounts(), inTransactions.observeTransactions(), inMarket.observePrices()) { inList, inTxs, inPrices ->
			val vLedger = Ledger(inTxs)											// grand livre
			val vToday  = LocalDate.now(inClock)								// date de valorisation
			val vValues = inList.associateWith { accountValue(it, vLedger, vToday, inPrices) }	// valeur de chaque compte
			val vWeights = accountWeights(vValues.values.associate { it.account.id to it.countedCents })	// part de chaque compte
			val vItems  = inList.map { inAccount ->
				AccountItem(
					account    = inAccount,
					valueCents = vValues.getValue(inAccount).valueCents,
					operations = inTxs.count { inTx -> inTx.kind != TransactionKind.OPENING && inTx.postings.any { it.accountId == inAccount.id } },
					weight     = vWeights[inAccount.id]
				)
			}																	// comptes valorisés
			AccountsUiState(true, vItems.filterNot { it.account.archived }, vItems.filter { it.account.archived })
		}
			.flowOn(Dispatchers.Default)
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

	/*
		Formulaire vide pour un nouveau compte (solde initial daté d'aujourd'hui).
	*/
	fun newForm(): AccountForm = AccountForm(openingDate = LocalDate.now(inClock))

	/*
		Prépare le formulaire d'un compte existant (solde initial compris).
		inAccount : compte
		inOnReady : reçoit le formulaire pré-rempli
	*/
	fun editForm(
		inAccount : Account,
		inOnReady : (AccountForm) -> Unit
	)
	{
		viewModelScope.launch {
			val vOpening = inAccounts.openingOf(inAccount.id)					// solde initial
			inOnReady(
				AccountForm(
					name        = inAccount.name,
					type        = inAccount.type,
					institution = inAccount.institution.orEmpty(),
					share       = inAccount.sharePercent.toString(),
					opening     = vOpening?.let { editableEuros(it.cents) }.orEmpty(),
					openingDate = vOpening?.date ?: LocalDate.now(inClock)
				)
			)
		}
	}

	/*
		Crée ou modifie un compte ; renvoie les erreurs (vides si enregistré).
		inForm    : saisie
		inEditing : compte modifié ; null pour une création
		inOnDone  : reçoit les erreurs
	*/
	fun save(
		inForm    : AccountForm,
		inEditing : Account?,
		inOnDone  : (AccountFormErrors) -> Unit
	)
	{
		val vShare   = inForm.share.trim().toIntOrNull()							// part détenue lue
		val vCents   = if (inForm.opening.isBlank()) 0L else parseEuros(inForm.opening)	// solde initial lu
		val vChecked = AccountFormErrors(
			share   = if (vShare == null || vShare !in kMinShare..kMaxShare) "Entre $kMinShare et $kMaxShare %." else null,
			opening = if (vCents == null) "Montant illisible (ex. 1 234,56)." else null
		)																		// erreurs de lecture
		if (!vChecked.isEmpty)
		{
			inOnDone(vChecked)
			return
		}
		viewModelScope.launch {
			val vOpening = Opening(vCents!!, inForm.openingDate)				// solde initial
			val vResult  = if (inEditing == null)
			{
				inAccounts.create(inForm.name, inForm.type, inForm.institution, vShare!!, vOpening)
			}
			else
			{
				inAccounts.update(inEditing.copy(name = inForm.name, type = inForm.type, institution = inForm.institution, sharePercent = vShare!!))
					.also { if (it is SaveResult.Saved) inAccounts.setOpening(inEditing.id, vOpening) }
			}																	// résultat
			inOnDone(toErrors(vResult))
		}
	}

	/*
		Archive ou désarchive un compte.
		inAccount  : compte
		inArchived : vrai pour archiver
	*/
	fun setArchived(
		inAccount  : Account,
		inArchived : Boolean
	)
	{
		viewModelScope.launch { inAccounts.setArchived(inAccount.id, inArchived) }
	}

	/*
		Supprime un compte : simplement s'il n'a pas d'opération, sinon
		définitivement avec ses opérations (après double confirmation).
		inAccount   : compte
		inPermanent : vrai pour la suppression définitive
	*/
	fun delete(
		inAccount   : Account,
		inPermanent : Boolean
	)
	{
		viewModelScope.launch {
			if (inPermanent) inAccounts.deletePermanently(inAccount.id) else inAccounts.deleteIfUnused(inAccount.id)
		}
	}

	/*
		Enregistre un nouvel ordre d'affichage des comptes actifs, suivis des archivés.
		inActive : comptes actifs, dans l'ordre voulu
	*/
	fun reorder(inActive: List<AccountItem>)
	{
		val vIds = inActive.map { it.account.id } + uiState.value.archived.map { it.account.id }	// ordre complet
		viewModelScope.launch { inAccounts.reorder(vIds) }
	}

	/*
		Traduit le résultat d'un enregistrement en erreurs de formulaire.
		inResult : résultat
	*/
	private fun toErrors(inResult: SaveResult<SetupError>): AccountFormErrors =
		when (inResult)
		{
			is SaveResult.Saved    -> AccountFormErrors()
			is SaveResult.Rejected -> AccountFormErrors(
				name    = inResult.errors.firstOrNull { it == SetupError.BlankName }?.message,
				share   = inResult.errors.firstOrNull { it == SetupError.InvalidShare }?.message,
				general = inResult.errors.firstOrNull { it == SetupError.TypeLocked || it == SetupError.NotFound }?.message
			)
		}
}

/*
	Part de chaque compte dans le patrimoine : seules les valeurs comptées
	positives entrent dans le total ; rangées de la plus grosse à la plus
	petite, parts entières qui font 100.
	inCounted : valeur comptée (part détenue appliquée) par compte
*/
internal fun accountWeights(inCounted: Map<String, Long>): Map<String, AccountWeight>
{
	val vPositive = inCounted.entries.filter { it.value > 0 }.sortedByDescending { it.value }	// comptes qui comptent
	val vPercents = wholePercents(vPositive.map { it.value })						// parts entières
	return vPositive.mapIndexed { inIndex, inEntry -> inEntry.key to AccountWeight(vPercents[inIndex], inIndex, inEntry.value) }.toMap()
}
