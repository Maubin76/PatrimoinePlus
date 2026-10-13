package fr.maubin.patrimoineplus.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.PlanningRepository
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.domain.rules.kMaxShare
import fr.maubin.patrimoineplus.domain.rules.kMinShare
import fr.maubin.patrimoineplus.ui.accounts.AccountForm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import fr.maubin.patrimoineplus.domain.calc.PropertyItem
import fr.maubin.patrimoineplus.domain.calc.propertyItems
import fr.maubin.patrimoineplus.data.repo.TransactionRepository

/*
	État des opérations récurrentes et des crédits.
	recurrences : opérations récurrentes
	loans       : crédits
	accounts    : comptes (tous)
	categories  : catégories (toutes)
	properties  : biens détenus (qu'un nouveau crédit peut financer)
*/
data class PlanningState(
	val recurrences : List<Recurrence>   = emptyList(),
	val loans       : List<Loan>         = emptyList(),
	val accounts    : List<Account>      = emptyList(),
	val categories  : List<Category>     = emptyList(),
	val properties  : List<PropertyItem> = emptyList()
)

/*
	ViewModel des opérations récurrentes et de la création d'un crédit.
	inPlanning   : récurrences et crédits
	inAccounts   : comptes
	inCategories : catégories
	inClock      : horloge
	inTransactions : opérations (biens détenus)
*/
@HiltViewModel
class PlanningViewModel @Inject constructor(
	private val inPlanning : PlanningRepository,
	inAccounts             : AccountRepository,
	inCategories           : CategoryRepository,
	private val inClock    : Clock,
	inTransactions         : TransactionRepository
) : ViewModel()
{
	private var fSaving = false															// enregistrement en cours (un second appui est ignoré)

	// État à afficher.
	val state: StateFlow<PlanningState> =
		combine(inPlanning.observeRecurrences(), inPlanning.observeLoans(), inAccounts.observeAccounts(), inCategories.observeCategories(), inTransactions.observeTransactions()) { inRecs, inLoans, inList, inCats, inTxs ->
			PlanningState(inRecs, inLoans, inList, inCats.sortedBy { it.sortOrder }, propertyItems(inTxs, inList).filter { it.heldAt(LocalDate.now(inClock)) })
		}.stateIn(viewModelScope, SharingStarted.Eagerly, PlanningState())

	// Date du jour.
	fun today(): LocalDate = LocalDate.now(inClock)

	/*
		Saisie d'une nouvelle récurrence : première échéance demain, sur le
		premier compte courant.
	*/
	fun newForm(): RecurrenceForm =
		RecurrenceForm(start = today().plusDays(1), accountId = state.value.accounts.firstOrNull { !it.archived && !it.type.isProperty }?.id)

	/*
		Enregistre une récurrence ; renvoie les erreurs de saisie ou le refus.
		inForm   : saisie
		inOnDone : erreurs (vides et motif null : enregistrée)
	*/
	fun save(
		inForm   : RecurrenceForm,
		inOnDone : (RecurrenceErrors, String?) -> Unit
	)
	{
		val vErrors = checkRecurrence(inForm, state.value.accounts, state.value.categories)	// erreurs de saisie
		if (!vErrors.isEmpty)
		{
			inOnDone(vErrors, null)
			return
		}
		if (fSaving) return
		fSaving = true
		viewModelScope.launch {
			try { inOnDone(RecurrenceErrors(), inPlanning.saveRecurrence(buildRecurrence(inForm))) } finally { fSaving = false }
		}
	}

	/*
		Supprime une récurrence (les opérations créées restent).
		inId : récurrence
	*/
	fun delete(inId: String)
	{
		viewModelScope.launch { inPlanning.deleteRecurrence(inId) }
	}

	/*
		Crée un crédit et son compte ; renvoie le motif d'un refus (null : créé).
		inAccount : saisie du compte (nom, établissement, part)
		inLoan    : saisie du crédit, vérifiée
		inOnDone  : motif du refus ; null si créé
	*/
	fun createLoan(
		inAccount : AccountForm,
		inLoan    : LoanForm,
		inOnDone  : (String?) -> Unit
	)
	{
		val vShare = inAccount.share.trim().toIntOrNull()?.takeIf { it in kMinShare..kMaxShare } ?: run { inOnDone("Part détenue : de $kMinShare à $kMaxShare %."); return }	// part lue
		if (fSaving) return
		fSaving = true
		viewModelScope.launch {
			try { inOnDone(inPlanning.createLoan(inAccount.name, inAccount.institution, vShare, buildLoan(inLoan))) } finally { fSaving = false }
		}
	}
}
