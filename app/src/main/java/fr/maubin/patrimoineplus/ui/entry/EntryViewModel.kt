package fr.maubin.patrimoineplus.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.domain.calc.LabelSuggestion
import fr.maubin.patrimoineplus.domain.calc.buildStandard
import fr.maubin.patrimoineplus.domain.calc.buildTrade
import fr.maubin.patrimoineplus.domain.calc.buildTransfer
import fr.maubin.patrimoineplus.domain.calc.labelSuggestions
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.editableScaled
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.format.parseScaled
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
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

const val kNewAsset = "__nouvel_actif__"										// choix « Nouvel actif… » dans la liste des actifs

/*
	Nature d'une saisie.
	label : libellé du bouton segmenté
*/
enum class EntryMode(val label: String)
{
	EXPENSE("Dépense"),
	INCOME("Revenu"),
	TRANSFER("Virement"),
	TRADE("Ordre")
}

/*
	Données utiles à la saisie.
	accounts      : comptes actifs, dans l'ordre d'affichage
	categories    : catégories actives
	assets        : actifs connus
	prices        : cours relevés
	transactions  : opérations connues (suggestions, quantités détenues)
	lastAccountId : compte de la dernière saisie
*/
data class EntryData(
	val accounts      : List<Account>     = emptyList(),
	val categories    : List<Category>    = emptyList(),
	val assets        : List<Asset>       = emptyList(),
	val prices        : List<AssetPrice>  = emptyList(),
	val transactions  : List<Transaction> = emptyList(),
	val lastAccountId : String?           = null
)

/*
	Saisie d'une opération, telle que tapée.
	id          : opération modifiée ; null pour une création
	postingIds  : jambes existantes (conservées en modification)
	tradeId     : ordre existant
	mode        : dépense, revenu, virement ou ordre
	amount      : montant (texte, sans signe) ; pas pour un ordre
	accountId   : compte (de départ pour un virement)
	toAccountId : compte d'arrivée d'un virement
	categoryId  : catégorie (dépense, revenu)
	assetId     : actif de l'ordre, ou du dividende ; kNewAsset pour en créer un
	newAsset    : nom de l'actif à créer
	side        : achat ou vente
	quantity    : quantité (texte)
	price       : cours (texte)
	fees        : frais (texte)
	label       : libellé ; vide pour un virement ou un ordre = libellé proposé
	date        : date
	notes       : notes
*/
data class EntryForm(
	val id          : String?      = null,
	val postingIds  : List<String> = emptyList(),
	val tradeId     : String?      = null,
	val mode        : EntryMode    = EntryMode.EXPENSE,
	val amount      : String       = "",
	val accountId   : String?      = null,
	val toAccountId : String?      = null,
	val categoryId  : String?      = null,
	val assetId     : String?      = null,
	val newAsset    : String       = "",
	val side        : TradeSide    = TradeSide.BUY,
	val quantity    : String       = "",
	val price       : String       = "",
	val fees        : String       = "",
	val label       : String       = "",
	val date        : LocalDate,
	val notes       : String       = ""
)

/*
	Erreurs de la saisie, par champ.
	amount   : montant
	account  : compte
	target   : compte d'arrivée
	category : catégorie
	asset    : actif
	quantity : quantité
	price    : cours
	fees     : frais
	label    : libellé
	general  : autre règle violée
*/
data class EntryErrors(
	val amount   : String? = null,
	val account  : String? = null,
	val target   : String? = null,
	val category : String? = null,
	val asset    : String? = null,
	val quantity : String? = null,
	val price    : String? = null,
	val fees     : String? = null,
	val label    : String? = null,
	val general  : String? = null
)
{
	// Vrai sans aucune erreur.
	val isEmpty: Boolean get() = listOf(amount, account, target, category, asset, quantity, price, fees, label, general).all { it == null }
}

/*
	Libellé proposé pour un virement ou un ordre quand le champ est vide.
	inForm : saisie
	inData : données (noms des comptes et des actifs)
*/
fun proposedLabel(
	inForm : EntryForm,
	inData : EntryData
): String =
	when (inForm.mode)
	{
		EntryMode.TRANSFER -> "Virement vers ${inData.accounts.firstOrNull { it.id == inForm.toAccountId }?.name ?: "…"}"
		EntryMode.TRADE    ->
		{
			val vAsset = if (inForm.assetId == kNewAsset) inForm.newAsset.trim() else inData.assets.firstOrNull { it.id == inForm.assetId }?.name.orEmpty()	// nom de l'actif
			"${if (inForm.side == TradeSide.BUY) "Achat" else "Vente"} ${vAsset.ifEmpty { "…" }}"
		}
		else               -> ""
	}

/*
	ViewModel de la saisie d'une opération (dépense, revenu, virement, ordre) :
	formulaire, suggestions de libellés, enregistrement validé par le domaine,
	suppression.
	inAccounts     : dépôt des comptes
	inCategories   : dépôt des catégories
	inMarket       : dépôt des actifs et des cours
	inTransactions : dépôt des opérations
	inSettings     : réglages (dernier compte utilisé)
	inClock        : horloge
*/
@HiltViewModel
class EntryViewModel @Inject constructor(
	inAccounts                 : AccountRepository,
	inCategories               : CategoryRepository,
	private val inMarket       : MarketRepository,
	private val inTransactions : TransactionRepository,
	private val inSettings     : SettingsRepository,
	private val inClock        : Clock
) : ViewModel()
{
	// Comptes, catégories, actifs, cours, opérations et dernier compte, à jour.
	val data: StateFlow<EntryData> =
		combine(
			combine(inAccounts.observeAccounts(), inCategories.observeCategories(), inMarket.observeAssets(), ::Triple),
			combine(inMarket.observePrices(), inTransactions.observeTransactions(), inSettings.lastAccountId, ::Triple)
		) { (inList, inCats, inAssets), (inPrices, inTxs, inLast) ->
			EntryData(inList.filterNot { it.archived }, inCats.filterNot { it.archived }.sortedBy { it.sortOrder }, inAssets, inPrices, inTxs, inLast)
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
		Formulaire pré-rempli pour modifier une opération (sauf solde initial).
		inTx : opération
	*/
	fun editForm(inTx: Transaction): EntryForm
	{
		val vBase = EntryForm(id = inTx.id, postingIds = inTx.postings.map { it.id }, label = inTx.label, date = inTx.date, notes = inTx.notes.orEmpty())	// champs communs
		return when (inTx.kind)
		{
			TransactionKind.TRANSFER ->
			{
				val vOut = inTx.postings.first { it.amountCents < 0 }			// jambe de départ
				val vIn  = inTx.postings.first { it.amountCents > 0 }			// jambe d'arrivée
				vBase.copy(mode = EntryMode.TRANSFER, postingIds = listOf(vOut.id, vIn.id), amount = editableEuros(vIn.amountCents), accountId = vOut.accountId, toAccountId = vIn.accountId)
			}
			TransactionKind.TRADE    ->
			{
				val vTrade = inTx.trades.single()								// l'ordre
				vBase.copy(
					mode      = EntryMode.TRADE,
					tradeId   = vTrade.id,
					accountId = inTx.postings.single().accountId,
					assetId   = vTrade.assetId,
					side      = vTrade.side,
					quantity  = editableScaled(vTrade.quantity),
					price     = editableScaled(vTrade.unitPrice),
					fees      = if (vTrade.feesCents == 0L) "" else editableEuros(vTrade.feesCents)
				)
			}
			else                     ->
			{
				val vPosting  = inTx.postings.single()							// jambe unique
				val vCategory = data.value.categories.firstOrNull { it.id == inTx.categoryId }	// catégorie
				val vKind     = vCategory?.kind ?: if (vPosting.amountCents < 0) CategoryKind.EXPENSE else CategoryKind.INCOME	// nature
				vBase.copy(
					mode       = if (vKind == CategoryKind.EXPENSE) EntryMode.EXPENSE else EntryMode.INCOME,
					amount     = editableEuros(abs(vPosting.amountCents)),
					accountId  = vPosting.accountId,
					categoryId = inTx.categoryId,
					assetId    = inTx.assetId
				)
			}
		}
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
		val vChecks = check(inForm)												// erreurs de saisie
		if (!vChecks.isEmpty)
		{
			inOnDone(vChecks)
			return
		}
		viewModelScope.launch {
			val vTx = build(inForm)												// opération construite
			when (val vResult = inTransactions.save(vTx))						// résultat
			{
				is SaveResult.Saved    ->
				{
					inSettings.setLastAccountId(vTx.postings.first().accountId)
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

	/*
		Vérifie la saisie avant de construire l'opération.
		inForm : saisie
	*/
	private fun check(inForm: EntryForm): EntryErrors
	{
		val vData = data.value													// données courantes
		return when (inForm.mode)
		{
			EntryMode.EXPENSE, EntryMode.INCOME -> EntryErrors(
				amount   = amountError(inForm.amount),
				account  = if (inForm.accountId == null) "Choisissez un compte." else null,
				category = if (inForm.categoryId == null) "Choisissez une catégorie." else null,
				label    = if (inForm.label.isBlank()) "Le libellé est obligatoire." else null
			)
			EntryMode.TRANSFER                  -> EntryErrors(
				amount  = amountError(inForm.amount),
				account = if (inForm.accountId == null) "Choisissez le compte de départ." else null,
				target  = when
				{
					inForm.toAccountId == null                -> "Choisissez le compte d’arrivée."
					inForm.toAccountId == inForm.accountId   -> "Choisissez deux comptes différents."
					else                                     -> null
				}
			)
			EntryMode.TRADE                     -> EntryErrors(
				account  = if (vData.accounts.none { it.id == inForm.accountId && it.type == AccountType.BROKERAGE }) "Choisissez un compte Bourse." else null,
				asset    = when
				{
					inForm.assetId == null                                  -> "Choisissez un actif."
					inForm.assetId == kNewAsset && inForm.newAsset.isBlank() -> "Nommez le nouvel actif."
					else                                                    -> null
				},
				quantity = parseScaled(inForm.quantity).let { if (it == null || it == 0L) "Quantité illisible ou nulle (ex. 12,5)." else null },
				price    = parseScaled(inForm.price).let { if (it == null || it == 0L) "Cours illisible ou nul (ex. 144,91)." else null },
				fees     = if (inForm.fees.isNotBlank() && (parseEuros(inForm.fees) ?: -1L) < 0L) "Frais illisibles (ex. 1,99)." else null
			)
		}
	}

	/*
		Construit l'opération à enregistrer (crée l'actif s'il est nouveau).
		inForm : saisie vérifiée
	*/
	private suspend fun build(inForm: EntryForm): Transaction
	{
		val vId     = inForm.id ?: UUID.randomUUID().toString()					// identifiant de l'opération
		val vLabel  = inForm.label.ifBlank { proposedLabel(inForm, data.value) }	// libellé retenu

		/*
			Identifiant de jambe : l'existant, sinon un nouveau.
			inIndex : rang de la jambe
		*/
		fun postingId(inIndex: Int): String = inForm.postingIds.getOrNull(inIndex) ?: UUID.randomUUID().toString()

		return when (inForm.mode)
		{
			EntryMode.EXPENSE, EntryMode.INCOME -> buildStandard(
				inId         = vId,
				inPostingId  = postingId(0),
				inDate       = inForm.date,
				inAccountId  = inForm.accountId!!,
				inCents      = parseEuros(inForm.amount)!!,
				inKind       = if (inForm.mode == EntryMode.EXPENSE) CategoryKind.EXPENSE else CategoryKind.INCOME,
				inCategoryId = inForm.categoryId,
				inLabel      = vLabel,
				inNotes      = inForm.notes
			).copy(assetId = inForm.assetId.takeIf { inForm.mode == EntryMode.INCOME && it != kNewAsset })
			EntryMode.TRANSFER                  -> buildTransfer(vId, postingId(0), postingId(1), inForm.date, inForm.accountId!!, inForm.toAccountId!!, parseEuros(inForm.amount)!!, vLabel, inForm.notes)
			EntryMode.TRADE                     ->
			{
				val vAsset = if (inForm.assetId == kNewAsset) (inMarket.createAsset(inForm.newAsset) as SaveResult.Saved).id else inForm.assetId!!	// actif
				buildTrade(
					inId        = vId,
					inPostingId = postingId(0),
					inTradeId   = inForm.tradeId ?: UUID.randomUUID().toString(),
					inDate      = inForm.date,
					inAccountId = inForm.accountId!!,
					inSide      = inForm.side,
					inAssetId   = vAsset,
					inQuantity  = parseScaled(inForm.quantity)!!,
					inUnitPrice = parseScaled(inForm.price)!!,
					inFeesCents = if (inForm.fees.isBlank()) 0L else parseEuros(inForm.fees)!!,
					inLabel     = vLabel,
					inNotes     = inForm.notes
				)
			}
		}
	}

	/*
		Erreur sur un montant saisi ; null s'il est valide.
		inText : texte saisi
	*/
	private fun amountError(inText: String): String?
	{
		val vCents = parseEuros(inText)											// montant lu
		return when
		{
			inText.isBlank() -> "Indiquez un montant."
			vCents == null   -> "Montant illisible (ex. 12,50)."
			vCents == 0L     -> "Le montant ne peut pas être nul."
			else             -> null
		}
	}
}
