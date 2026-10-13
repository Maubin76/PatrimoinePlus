package fr.maubin.patrimoineplus.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.market.QuoteRefresher
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.domain.calc.LabelSuggestion
import fr.maubin.patrimoineplus.domain.calc.labelSuggestions
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.Category
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
import fr.maubin.patrimoineplus.domain.calc.PropertyItem
import fr.maubin.patrimoineplus.domain.calc.propertyItems
import fr.maubin.patrimoineplus.data.repo.PlanningRepository
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.ui.planning.recurrenceFromEntry

/*
	ViewModel de la saisie d'une opération (dépense, revenu, virement, ordre) :
	formulaire, suggestions de libellés, enregistrement validé par le domaine,
	suppression.
	inAccounts     : dépôt des comptes
	inCategories   : dépôt des catégories
	inMarket       : dépôt des actifs et des cours
	inTransactions : dépôt des opérations
	inSettings     : réglages (dernier compte utilisé)
	inQuotes       : mise à jour des cours (nouvel actif avec ISIN)
	inClock        : horloge
*/
@HiltViewModel
class EntryViewModel @Inject constructor(
	inAccounts                 : AccountRepository,
	inCategories               : CategoryRepository,
	private val inMarket       : MarketRepository,
	private val inTransactions : TransactionRepository,
	private val inSettings     : SettingsRepository,
	private val inQuotes       : QuoteRefresher,
	private val inClock        : Clock,
	private val inPlanning     : PlanningRepository
) : ViewModel()
{
	private var fSaving = false														// enregistrement en cours

	// Tous les comptes et toutes les catégories, archivés compris.
	private val fAll: StateFlow<Pair<List<Account>, List<Category>>> =
		combine(inAccounts.observeAccounts(), inCategories.observeCategories(), ::Pair)
			.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Account>() to emptyList())

	// Comptes, catégories, actifs, cours, opérations et dernier compte, à jour.
	val data: StateFlow<EntryData> =
		combine(
			combine(inAccounts.observeAccounts(), inCategories.observeCategories(), inMarket.observeAssets(), ::Triple),
			combine(inMarket.observePrices(), inTransactions.observeTransactions(), inSettings.lastAccountId, ::Triple)
		) { (inList, inCats, inAssets), (inPrices, inTxs, inLast) ->
			EntryData(inList.filterNot { it.archived || it.type.isProperty }, inCats.filterNot { it.archived }.sortedBy { it.sortOrder }, inAssets, inPrices, inTxs, inLast)
		}.stateIn(viewModelScope, SharingStarted.Eagerly, EntryData())

	/*
		Données de saisie pour une saisie donnée : les comptes et la catégorie
		archivés qu'elle utilise déjà restent disponibles (modification d'une
		opération ancienne).
		inForm : saisie
	*/
	fun dataFor(inForm: EntryForm): EntryData = data.value.withReferences(inForm, fAll.value.first, fAll.value.second)

	/*
		Formulaire vide, daté d'aujourd'hui, sur le compte voulu, sinon le
		dernier utilisé, sinon le premier de la liste.
		inAccountId : compte imposé (vue d'un compte) ; null sinon
	*/
	fun newForm(inAccountId: String? = null): EntryForm = newEntryForm(data.value, inAccountId, LocalDate.now(inClock))

	/*
		Formulaire pré-rempli pour modifier une opération (sauf solde initial).
		inTx : opération
	*/
	fun editForm(inTx: Transaction): EntryForm = editEntryForm(inTx, data.value.categories)

	/*
		Suggestions de libellés pour un début de saisie.
		inPrefix : texte saisi
	*/
	fun suggestions(inPrefix: String): List<LabelSuggestion> = labelSuggestions(data.value.transactions, inPrefix)

	/*
		Enregistre la saisie ; renvoie les erreurs (vides si enregistrée).
		Un second appui pendant l'enregistrement est ignoré (pas de doublon).
		Avec une répétition, une opération récurrente reprend la saisie à
		partir de la période suivante.
		inForm   : saisie
		inOnDone : reçoit les erreurs
		inRepeat : répétition demandée ; null sans répétition
	*/
	fun save(
		inForm   : EntryForm,
		inOnDone : (EntryErrors) -> Unit,
		inRepeat : Frequency? = null
	)
	{
		if (fSaving)
		{
			return
		}
		val vChecks = checkEntry(inForm, dataFor(inForm))												// erreurs de saisie
		if (!vChecks.isEmpty)
		{
			inOnDone(vChecks)
			return
		}
		fSaving = true
		viewModelScope.launch {
			try
			{
				saveChecked(inForm, inOnDone, inRepeat)
			}
			finally
			{
				fSaving = false
			}
		}
	}

	/*
		Enregistre une saisie déjà contrôlée ; un nouvel actif n'est créé que
		si l'opération est acceptée.
		inForm   : saisie
		inOnDone : reçoit les erreurs
	*/
	private suspend fun saveChecked(
		inForm   : EntryForm,
		inOnDone : (EntryErrors) -> Unit,
		inRepeat : Frequency?
	)
	{
		val vNewAsset = if (inForm.mode == EntryMode.TRADE && inForm.assetId == kNewAsset)
			Asset(UUID.randomUUID().toString(), inForm.newAsset.trim(), inForm.newIsin.trim().uppercase().ifEmpty { null }) else null	// actif à créer
		val vTx = buildEntry(inForm, dataFor(inForm), vNewAsset?.id)											// opération construite
		when (val vResult = inTransactions.save(vTx, vNewAsset))			// résultat
		{
			is SaveResult.Saved    ->
			{
				inSettings.setLastAccountId(vTx.postings.first().accountId)
				inRepeat?.takeIf { inForm.id == null }?.let { recurrenceFromEntry(inForm, vTx.label, it) }?.let { inPlanning.saveRecurrence(it) }
				inOnDone(EntryErrors())
				if (inForm.mode == EntryMode.TRADE && inForm.assetId == kNewAsset && inForm.newIsin.isNotBlank())
				{
					inQuotes.refreshNow()												// cours du nouvel actif, sans attendre
				}
			}
			is SaveResult.Rejected -> inOnDone(EntryErrors(general = vResult.errors.joinToString(" ") { it.message }))
		}
	}

	/*
		Tous les comptes, archivés et comptes de biens compris (saisie des biens).
	*/
	fun allAccounts(): List<Account> = fAll.value.first

	/*
		Bien concerné par un achat ou une vente ; null s'il est introuvable.
		inTx : achat ou vente d'un bien
	*/
	fun propertyOf(inTx: Transaction): PropertyItem? =
		propertyItems(data.value.transactions, fAll.value.first).firstOrNull { it.acquisition.id == inTx.id || it.disposal?.id == inTx.id }

	/*
		Enregistre l'achat ou la vente d'un bien, déjà vérifié par sa saisie ;
		renvoie le refus des règles, ou null s'il est enregistré.
		inTx     : opération construite
		inOnDone : reçoit le refus (null : enregistré)
	*/
	fun saveProperty(
		inTx     : Transaction,
		inOnDone : (String?) -> Unit
	)
	{
		viewModelScope.launch {
			when (val vResult = inTransactions.save(inTx))							// résultat
			{
				is SaveResult.Saved    -> inOnDone(null)
				is SaveResult.Rejected -> inOnDone(vResult.errors.joinToString(" ") { it.message })
			}
		}
	}

	/*
		Supprime des opérations ; un refus (vente rendue impossible) est
		signalé.
		inIds     : identifiants
		inOnRefus : reçoit le motif d'un refus
	*/
	fun delete(
		inIds     : List<String>,
		inOnRefus : (String) -> Unit = {}
	)
	{
		viewModelScope.launch { inTransactions.delete(inIds)?.let(inOnRefus) }
	}
}
