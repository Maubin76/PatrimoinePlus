package fr.maubin.patrimoineplus.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.domain.calc.LabelSuggestion
import fr.maubin.patrimoineplus.domain.calc.buildStandard
import fr.maubin.patrimoineplus.domain.calc.labelSuggestions
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Transaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/*
	Données utiles à la saisie.
	accounts      : comptes actifs, dans l'ordre d'affichage
	categories    : catégories actives
	transactions  : opérations connues (suggestions de libellés)
	lastAccountId : compte de la dernière saisie
*/
data class EntryData(
	val accounts      : List<Account>     = emptyList(),
	val categories    : List<Category>    = emptyList(),
	val transactions  : List<Transaction> = emptyList(),
	val lastAccountId : String?           = null
)

/*
	Saisie d'une dépense ou d'un revenu, telle que tapée.
	id         : opération modifiée ; null pour une création
	postingId  : jambe modifiée ; null pour une création
	kind       : dépense ou revenu
	amount     : montant (texte, sans signe)
	accountId  : compte
	categoryId : catégorie
	label      : libellé
	date       : date
	notes      : notes
*/
data class EntryForm(
	val id         : String?      = null,
	val postingId  : String?      = null,
	val kind       : CategoryKind = CategoryKind.EXPENSE,
	val amount     : String       = "",
	val accountId  : String?      = null,
	val categoryId : String?      = null,
	val label      : String       = "",
	val date       : LocalDate,
	val notes      : String       = ""
)

/*
	Erreurs de la saisie, par champ.
	amount   : montant
	account  : compte
	category : catégorie
	label    : libellé
	general  : autre règle violée
*/
data class EntryErrors(
	val amount   : String? = null,
	val account  : String? = null,
	val category : String? = null,
	val label    : String? = null,
	val general  : String? = null
)
{
	// Vrai sans aucune erreur.
	val isEmpty: Boolean get() = listOf(amount, account, category, label, general).all { it == null }
}

/*
	ViewModel de la saisie d'une opération (dépense ou revenu) : formulaire,
	suggestions de libellés, enregistrement validé par le domaine, suppression.
	inAccounts     : dépôt des comptes
	inCategories   : dépôt des catégories
	inTransactions : dépôt des opérations
	inSettings     : réglages (dernier compte utilisé)
	inClock        : horloge
*/
@HiltViewModel
class EntryViewModel @Inject constructor(
	inAccounts                 : AccountRepository,
	inCategories               : CategoryRepository,
	private val inTransactions : TransactionRepository,
	private val inSettings     : SettingsRepository,
	private val inClock        : Clock
) : ViewModel()
{
	// Comptes, catégories, opérations et dernier compte, à jour.
	val data: StateFlow<EntryData> =
		combine(inAccounts.observeAccounts(), inCategories.observeCategories(), inTransactions.observeTransactions(), inSettings.lastAccountId) { inList, inCats, inTxs, inLast ->
			EntryData(inList.filterNot { it.archived }, inCats.filterNot { it.archived }.sortedBy { it.sortOrder }, inTxs, inLast)
		}.stateIn(viewModelScope, SharingStarted.Eagerly, EntryData())

	/*
		Formulaire vide, daté d'aujourd'hui, sur le compte voulu, sinon le
		dernier utilisé, sinon le premier de la liste.
		inAccountId : compte imposé (vue d'un compte) ; null sinon
	*/
	fun newForm(inAccountId: String? = null): EntryForm
	{
		val vData = data.value													// données courantes
		val vIds  = vData.accounts.map { it.id }								// comptes actifs
		val vPick = listOf(inAccountId, vData.lastAccountId).firstOrNull { it in vIds } ?: vIds.firstOrNull()	// compte retenu
		return EntryForm(accountId = vPick, date = LocalDate.now(inClock))
	}

	/*
		Formulaire pré-rempli pour modifier une dépense ou un revenu.
		inTx : opération
	*/
	fun editForm(inTx: Transaction): EntryForm
	{
		val vPosting  = inTx.postings.single()									// jambe unique
		val vCategory = data.value.categories.firstOrNull { it.id == inTx.categoryId }	// catégorie
		return EntryForm(
			id         = inTx.id,
			postingId  = vPosting.id,
			kind       = vCategory?.kind ?: if (vPosting.amountCents < 0) CategoryKind.EXPENSE else CategoryKind.INCOME,
			amount     = editableEuros(abs(vPosting.amountCents)),
			accountId  = vPosting.accountId,
			categoryId = inTx.categoryId,
			label      = inTx.label,
			date       = inTx.date,
			notes      = inTx.notes.orEmpty()
		)
	}

	/*
		Suggestions de libellés pour un début de saisie.
		inPrefix : texte saisi
	*/
	fun suggestions(inPrefix: String): List<LabelSuggestion> = labelSuggestions(data.value.transactions, inPrefix)

	/*
		Enregistre la saisie ; renvoie les erreurs (vides si enregistrée).
		inForm   : saisie
		inOnDone : reçoit les erreurs
	*/
	fun save(
		inForm   : EntryForm,
		inOnDone : (EntryErrors) -> Unit
	)
	{
		val vCents  = parseEuros(inForm.amount)									// montant lu
		val vChecks = EntryErrors(
			amount   = when
			{
				inForm.amount.isBlank()   -> "Indiquez un montant."
				vCents == null            -> "Montant illisible (ex. 12,50)."
				vCents == 0L              -> "Le montant ne peut pas être nul."
				else                      -> null
			},
			account  = if (inForm.accountId == null) "Choisissez un compte." else null,
			category = if (inForm.categoryId == null) "Choisissez une catégorie." else null,
			label    = if (inForm.label.isBlank()) "Le libellé est obligatoire." else null
		)																		// erreurs de saisie
		if (!vChecks.isEmpty)
		{
			inOnDone(vChecks)
			return
		}
		val vTx = buildStandard(
			inId         = inForm.id ?: UUID.randomUUID().toString(),
			inPostingId  = inForm.postingId ?: UUID.randomUUID().toString(),
			inDate       = inForm.date,
			inAccountId  = inForm.accountId!!,
			inCents      = vCents!!,
			inKind       = inForm.kind,
			inCategoryId = inForm.categoryId,
			inLabel      = inForm.label,
			inNotes      = inForm.notes
		)																		// opération construite
		viewModelScope.launch {
			when (val vResult = inTransactions.save(vTx))						// résultat
			{
				is SaveResult.Saved    ->
				{
					inSettings.setLastAccountId(vTx.postings.single().accountId)
					inOnDone(EntryErrors())
				}
				is SaveResult.Rejected -> inOnDone(EntryErrors(general = vResult.errors.joinToString(" ") { it.message }))
			}
		}
	}

	/*
		Supprime des opérations.
		inIds : identifiants
	*/
	fun delete(inIds: List<String>)
	{
		viewModelScope.launch { inTransactions.delete(inIds) }
	}

}
