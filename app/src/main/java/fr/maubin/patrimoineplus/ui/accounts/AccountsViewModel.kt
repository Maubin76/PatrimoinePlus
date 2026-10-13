package fr.maubin.patrimoineplus.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.Opening
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.model.Account
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
import fr.maubin.patrimoineplus.data.repo.PlanningRepository

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
	private val inClock    : Clock,
	inPlanning             : PlanningRepository
) : ViewModel()
{
	// État exposé à l'écran, recalculé à chaque changement de la base.
	val uiState: StateFlow<AccountsUiState> =
		combine(inAccounts.observeAccounts(), inTransactions.observeTransactions(), inMarket.observePrices(), inPlanning.observeLoans()) { inList, inTxs, inPrices, inLoans ->
			buildAccountsState(inList, inTxs, inPrices, LocalDate.now(inClock), inLoans)
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
		val vCents   = if (inForm.opening.isBlank() || inForm.type.isProperty) 0L else parseEuros(inForm.opening)	// solde initial lu (aucun pour un compte de biens)
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
				if (inForm.type.isProperty && inForm.type != inEditing.type) inAccounts.setOpening(inEditing.id, Opening(0, inForm.openingDate))	// un compte de biens n'a pas de solde initial
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
