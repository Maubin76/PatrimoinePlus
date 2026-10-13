package fr.maubin.patrimoineplus.ui.entry

import fr.maubin.patrimoineplus.domain.calc.buildStandard
import fr.maubin.patrimoineplus.domain.calc.buildTrade
import fr.maubin.patrimoineplus.domain.calc.buildTransfer
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
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.calc.amortization
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.budget.themeOfCategory
import java.time.YearMonth


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
	TRADE("Ordre"),
	LOAN("Crédit")
}

/*
	Données utiles à la saisie.
	accounts      : comptes actifs, dans l'ordre d'affichage
	categories    : catégories actives
	assets        : actifs connus
	prices        : cours relevés
	transactions  : opérations connues (suggestions, quantités détenues)
	lastAccountId : compte de la dernière saisie
	loans         : crédits (mensualité proposée d'après le tableau d'amortissement)
*/
data class EntryData(
	val accounts      : List<Account>     = emptyList(),
	val categories    : List<Category>    = emptyList(),
	val assets        : List<Asset>       = emptyList(),
	val prices        : List<AssetPrice>  = emptyList(),
	val transactions  : List<Transaction> = emptyList(),
	val lastAccountId : String?           = null,
	val loans         : List<Loan>        = emptyList()
)

/*
	Modes de saisie proposés : « Crédit » seulement s'il existe un compte
	Crédit, ou si la saisie est déjà une mensualité.
	inData : données de saisie
	inForm : saisie en cours
*/
fun entryModes(
	inData : EntryData,
	inForm : EntryForm
): List<EntryMode> =
	EntryMode.entries.filter { it != EntryMode.LOAN || inForm.mode == EntryMode.LOAN || inData.accounts.any { inAccount -> inAccount.type == AccountType.LOAN } }

/*
	Saisie d'une opération, telle que tapée.
	id          : opération modifiée ; null pour une création
	postingIds  : jambes existantes (conservées en modification)
	tradeId     : ordre existant
	mode        : dépense, revenu, virement, ordre ou mensualité de crédit
	amount      : montant (texte, sans signe) ; pas pour un ordre ; total payé pour une mensualité
	accountId   : compte (de départ pour un virement, payeur pour une mensualité)
	toAccountId : compte d'arrivée d'un virement ; compte Crédit d'une mensualité
	categoryId  : catégorie (dépense, revenu ; intérêts et assurance d'une mensualité)
	capital     : capital remboursé d'une mensualité (texte) ; le reste du total est la dépense
	assetId     : actif de l'ordre, ou du dividende ; kNewAsset pour en créer un
	newAsset    : nom de l'actif à créer
	newIsin     : ISIN de l'actif à créer (facultatif, pour les cours automatiques)
	refund      : remboursement (dépense : montant reçu, positif, dans une catégorie de dépense)
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
	val capital     : String       = "",
	val assetId     : String?      = null,
	val newAsset    : String       = "",
	val newIsin     : String       = "",
	val refund      : Boolean      = false,
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
	capital  : capital d'une mensualité
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
	val capital  : String? = null,
	val general  : String? = null
)
{
	// Vrai sans aucune erreur.
	val isEmpty: Boolean get() = listOf(amount, account, target, category, asset, quantity, price, fees, label, capital, general).all { it == null }
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
		EntryMode.LOAN     -> "Mensualité ${inData.accounts.firstOrNull { it.id == inForm.toAccountId }?.name ?: "…"}"
		else               -> ""
	}

/*
	Données de saisie complétées des comptes et catégories archivés que la
	saisie utilise déjà (modification d'une opération ancienne) : ils restent
	choisis et la saisie reste valable ; ils ne sont pas proposés ailleurs.
	inForm          : saisie
	inAllAccounts   : tous les comptes, archivés compris
	inAllCategories : toutes les catégories, archivées comprises
*/
fun EntryData.withReferences(
	inForm          : EntryForm,
	inAllAccounts   : List<Account>,
	inAllCategories : List<Category>
): EntryData
{
	val vAccountIds = setOfNotNull(inForm.accountId, inForm.toAccountId)			// comptes de la saisie
	val vExtraAcc   = inAllAccounts.filter { it.id in vAccountIds && accounts.none { inKnown -> inKnown.id == it.id } }	// comptes archivés utilisés
	val vExtraCat   = inAllCategories.filter { it.id == inForm.categoryId && categories.none { inKnown -> inKnown.id == it.id } }	// catégorie archivée utilisée
	return if (vExtraAcc.isEmpty() && vExtraCat.isEmpty()) this else copy(accounts = accounts + vExtraAcc, categories = categories + vExtraCat)
}

/*
	Change le mode de la saisie en gardant ce qui reste valable : le compte
	passe sur un compte Bourse pour un ordre, la catégorie disparaît si elle
	n'est plus de la bonne nature ; l'actif (ordre, dividende), le nouvel
	actif et le remboursement sont remis à zéro, pour qu'aucune valeur
	invisible dans le nouveau mode ne soit enregistrée (un revenu ne devient
	pas un dividende caché).
	inForm : saisie
	inMode : nouveau mode
	inData : données
*/
fun switchEntryMode(
	inForm : EntryForm,
	inMode : EntryMode,
	inData : EntryData
): EntryForm
{
	if (inMode == inForm.mode)
	{
		return inForm
	}
	val vKind    = if (inMode == EntryMode.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE	// nature des catégories
	val vKeepCat = inData.categories.firstOrNull { it.id == inForm.categoryId }?.kind == vKind && inMode in listOf(EntryMode.EXPENSE, EntryMode.INCOME)	// catégorie encore valable
	val vAccount = if (inMode == EntryMode.TRADE && inData.accounts.none { it.id == inForm.accountId && it.type == AccountType.BROKERAGE })
		inData.accounts.firstOrNull { it.type == AccountType.BROKERAGE }?.id else inForm.accountId	// compte retenu
	if (inMode == EntryMode.LOAN)
	{
		val vLoan  = inData.accounts.firstOrNull { it.id == inForm.accountId && it.type == AccountType.LOAN }?.id
			?: inData.accounts.firstOrNull { it.type == AccountType.LOAN }?.id							// compte Crédit (celui affiché s'il en est un)
		val vPayer = inData.loans.firstOrNull { it.accountId == vLoan }?.payFrom?.takeIf { inId -> inData.accounts.any { it.id == inId } }
			?: inForm.accountId.takeIf { inId -> inData.accounts.any { it.id == inId && it.type != AccountType.LOAN && !it.type.isProperty } }
			?: inData.accounts.firstOrNull { it.type == AccountType.CHECKING }?.id									// compte payeur
		return suggestLoanPayment(inForm.copy(mode = inMode, accountId = vPayer, toAccountId = vLoan, categoryId = null, assetId = null, newAsset = "", newIsin = "", refund = false), inData)
	}
	return inForm.copy(
		mode       = inMode,
		categoryId = if (vKeepCat) inForm.categoryId else null,
		accountId  = vAccount,
		assetId    = null,
		newAsset   = "",
		newIsin    = "",
		refund     = false
	)
}

/*
	Formulaire vide, daté du jour, sur le compte voulu, sinon le dernier
	utilisé, sinon le premier de la liste ; en ordre de bourse sur un compte
	Bourse, en dépense sinon.
	inData      : données de saisie
	inAccountId : compte imposé (vue d'un compte) ; null sinon
	inToday     : date du jour
*/
fun newEntryForm(
	inData      : EntryData,
	inAccountId : String?,
	inToday     : LocalDate
): EntryForm
{
	val vIds  = inData.accounts.map { it.id }									// comptes actifs
	val vPick = listOf(inAccountId, inData.lastAccountId).firstOrNull { it in vIds } ?: vIds.firstOrNull()	// compte retenu
	val vType = inData.accounts.firstOrNull { it.id == vPick }?.type			// type du compte retenu
	val vForm = EntryForm(accountId = vPick, date = inToday, mode = if (vType == AccountType.BROKERAGE) EntryMode.TRADE else EntryMode.EXPENSE)	// saisie vide
	return if (vType == AccountType.LOAN) switchEntryMode(vForm, EntryMode.LOAN, inData) else vForm
}

/*
	Mensualité proposée pour le compte Crédit et la date de la saisie : total,
	capital et catégorie de la mensualité du tableau d'amortissement tombant
	le même mois ; sans tableau ou hors échéance, seule la catégorie est
	reprise (et le montant reste à taper).
	inForm : saisie en mode Crédit
	inData : données (crédits)
*/
fun suggestLoanPayment(
	inForm : EntryForm,
	inData : EntryData
): EntryForm
{
	val vLoan = inData.loans.firstOrNull { it.accountId == inForm.toAccountId }	// crédit du compte
	val vPart = vLoan?.let { inLoan -> amortization(inLoan).firstOrNull { YearMonth.from(it.date) == YearMonth.from(inForm.date) } }	// mensualité du mois
	val vCategory = vLoan?.categoryId?.takeIf { inId -> inData.categories.any { it.id == inId } }
		?: inForm.categoryId
		?: inData.categories.firstOrNull { it.kind == CategoryKind.EXPENSE && themeOfCategory(it.name) == ExpenseTheme.CREDIT }?.id	// catégorie des intérêts
	return if (vPart == null) inForm.copy(categoryId = vCategory)
		else inForm.copy(categoryId = vCategory, amount = editableEuros(vPart.total), capital = editableEuros(vPart.capital))
}

/*
	Formulaire pré-rempli pour modifier une opération (sauf solde initial).
	inTx         : opération
	inCategories : catégories (nature d'une opération ordinaire)
*/
fun editEntryForm(
	inTx         : Transaction,
	inCategories : List<Category>
): EntryForm
{
	val vBase = EntryForm(id = inTx.id, postingIds = inTx.postings.map { it.id }, label = inTx.label, date = inTx.date, notes = inTx.notes.orEmpty())	// champs communs
	return when (inTx.kind)
	{
		TransactionKind.TRANSFER ->
		{
			val vOut = inTx.postings.first { it.amountCents < 0 }				// jambe de départ
			val vIn  = inTx.postings.first { it.amountCents > 0 }				// jambe d'arrivée
			vBase.copy(mode = EntryMode.TRANSFER, postingIds = listOf(vOut.id, vIn.id), amount = editableEuros(vIn.amountCents), accountId = vOut.accountId, toAccountId = vIn.accountId)
		}
		TransactionKind.LOAN_PAYMENT ->
		{
			val vPayer = inTx.postings[0]										// jambe du payeur
			val vLoan  = inTx.postings[1]										// jambe du crédit
			vBase.copy(mode = EntryMode.LOAN, amount = editableEuros(-vPayer.amountCents), capital = editableEuros(vLoan.amountCents),
				accountId = vPayer.accountId, toAccountId = vLoan.accountId, categoryId = inTx.categoryId)
		}
		TransactionKind.TRADE    ->
		{
			val vTrade = inTx.trades.single()									// l'ordre
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
			val vPosting  = inTx.postings.single()								// jambe unique
			val vCategory = inCategories.firstOrNull { it.id == inTx.categoryId }	// catégorie
			val vKind     = vCategory?.kind ?: if (vPosting.amountCents < 0) CategoryKind.EXPENSE else CategoryKind.INCOME	// nature
			vBase.copy(
				mode       = if (vKind == CategoryKind.EXPENSE) EntryMode.EXPENSE else EntryMode.INCOME,
				refund     = vKind == CategoryKind.EXPENSE && vPosting.amountCents > 0,
				amount     = editableEuros(abs(vPosting.amountCents)),
				accountId  = vPosting.accountId,
				categoryId = inTx.categoryId,
				assetId    = inTx.assetId
			)
		}
	}
}

/*
	Vérifie une saisie avant de construire l'opération (champs obligatoires,
	montants lisibles et non nuls, comptes cohérents).
	inForm : saisie
	inData : données de saisie
*/
fun checkEntry(
	inForm : EntryForm,
	inData : EntryData
): EntryErrors =
	when (inForm.mode)
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
				inForm.toAccountId == null              -> "Choisissez le compte d’arrivée."
				inForm.toAccountId == inForm.accountId -> "Choisissez deux comptes différents."
				else                                   -> null
			}
		)
		EntryMode.TRADE                     -> EntryErrors(
			account  = if (inData.accounts.none { it.id == inForm.accountId && it.type == AccountType.BROKERAGE }) "Choisissez un compte Bourse." else null,
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
		EntryMode.LOAN                      ->
		{
			val vTotal   = parseEuros(inForm.amount)							// total lu
			val vCapital = if (inForm.capital.isBlank()) vTotal else parseEuros(inForm.capital)	// capital lu (vide : tout en capital)
			val vPayer   = inData.accounts.firstOrNull { it.id == inForm.accountId }?.type		// type du payeur
			EntryErrors(
				amount   = amountError(inForm.amount) ?: if (vTotal != null && vTotal < 0) "Le montant doit être positif." else null,
				account  = if (vPayer == null || vPayer == AccountType.LOAN || vPayer.isProperty) "Choisissez le compte qui paie." else null,
				target   = if (inData.accounts.none { it.id == inForm.toAccountId && it.type == AccountType.LOAN }) "Choisissez le compte Crédit." else null,
				capital  = when
				{
					vCapital == null || vCapital < 0                  -> "Capital illisible (ex. 612,40)."
					vTotal != null && vCapital > vTotal               -> "Le capital ne peut pas dépasser le total."
					else                                              -> null
				},
				category = if (vTotal != null && vCapital != null && vCapital < vTotal && inData.categories.none { it.id == inForm.categoryId && it.kind == CategoryKind.EXPENSE }) "Choisissez la catégorie des intérêts." else null
			)
		}
	}

/*
	Intérêts et assurance d'une mensualité saisie (total moins capital) ;
	null si l'un des montants est illisible.
	inForm : saisie en mode Crédit
*/
fun loanCharge(inForm: EntryForm): Long?
{
	val vTotal   = parseEuros(inForm.amount) ?: return null						// total
	val vCapital = if (inForm.capital.isBlank()) vTotal else parseEuros(inForm.capital) ?: return null	// capital
	return vTotal - vCapital
}

/*
	Construit l'opération d'une saisie vérifiée. Un nouvel actif doit avoir été
	créé avant : son identifiant est passé ici.
	inForm       : saisie vérifiée
	inData       : données de saisie (libellé proposé)
	inNewAssetId : identifiant de l'actif créé si la saisie en crée un ; null sinon
*/
fun buildEntry(
	inForm       : EntryForm,
	inData       : EntryData,
	inNewAssetId : String? = null
): Transaction
{
	val vId    = inForm.id ?: UUID.randomUUID().toString()						// identifiant de l'opération
	val vLabel = inForm.label.ifBlank { proposedLabel(inForm, inData) }			// libellé retenu

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
			inKind       = if (inForm.mode == EntryMode.EXPENSE && !inForm.refund) CategoryKind.EXPENSE else CategoryKind.INCOME,
			inCategoryId = inForm.categoryId,
			inLabel      = vLabel,
			inNotes      = inForm.notes
		).copy(assetId = inForm.assetId.takeIf { inForm.mode == EntryMode.INCOME && it != kNewAsset })
		EntryMode.TRANSFER                  -> buildTransfer(vId, postingId(0), postingId(1), inForm.date, inForm.accountId!!, inForm.toAccountId!!, parseEuros(inForm.amount)!!, vLabel, inForm.notes)
		EntryMode.LOAN                      ->
		{
			val vTotal   = parseEuros(inForm.amount)!!								// total payé
			val vCapital = if (inForm.capital.isBlank()) vTotal else parseEuros(inForm.capital)!!	// capital remboursé
			Transaction(vId, inForm.date, TransactionKind.LOAN_PAYMENT, vLabel, inForm.categoryId.takeIf { vCapital < vTotal || it != null },
				notes = inForm.notes.trim().ifEmpty { null },
				postings = listOf(Posting(postingId(0), inForm.accountId!!, -vTotal), Posting(postingId(1), inForm.toAccountId!!, vCapital)))
		}
		EntryMode.TRADE                     -> buildTrade(
			inId        = vId,
			inPostingId = postingId(0),
			inTradeId   = inForm.tradeId ?: UUID.randomUUID().toString(),
			inDate      = inForm.date,
			inAccountId = inForm.accountId!!,
			inSide      = inForm.side,
			inAssetId   = if (inForm.assetId == kNewAsset) checkNotNull(inNewAssetId) { "actif à créer avant" } else inForm.assetId!!,
			inQuantity  = parseScaled(inForm.quantity)!!,
			inUnitPrice = parseScaled(inForm.price)!!,
			inFeesCents = if (inForm.fees.isBlank()) 0L else parseEuros(inForm.fees)!!,
			inLabel     = vLabel,
			inNotes     = inForm.notes
		)
	}
}

/*
	Erreur sur un montant saisi ; null s'il est valide.
	inText : texte saisi
*/
fun amountError(inText: String): String?
{
	val vCents = parseEuros(inText)												// montant lu
	return when
	{
		inText.isBlank() -> "Indiquez un montant."
		vCents == null   -> "Montant illisible (ex. 12,50)."
		vCents == 0L     -> "Le montant ne peut pas être nul."
		else             -> null
	}
}
