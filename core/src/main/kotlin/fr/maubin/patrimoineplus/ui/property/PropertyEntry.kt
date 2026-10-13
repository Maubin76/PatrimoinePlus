package fr.maubin.patrimoineplus.ui.property

import fr.maubin.patrimoineplus.domain.calc.PropertyItem
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate
import java.util.UUID

// Saisie des biens (comptes Immobilier et Collection), partagée par le
// téléphone et le PC : achat (déjà possédé ou payé depuis un compte) et
// vente (prix versé sur un compte ou non).

/*
	Nature lisible d'une opération de bien.
	inTx : achat ou vente d'un bien
*/
fun propertyNature(inTx: Transaction): String = if (inTx.kind == TransactionKind.DISPOSAL) "Vente d’un bien" else "Achat d’un bien"

/*
	Icône d'une opération de bien : maison pour l'immobilier, pierre
	précieuse pour un objet de collection.
	inTx   : achat ou vente d'un bien
	inById : comptes par identifiant
*/
fun propertyIcon(
	inTx   : Transaction,
	inById : Map<String, Account>
): String = if (inTx.postings.any { inById[it.accountId]?.type == AccountType.REAL_ESTATE }) "home" else "gem"

/*
	Nom d'un bien selon le type de compte (« bien », « objet »).
	inType : type du compte de biens
*/
fun propertyNoun(inType: AccountType): String = if (inType == AccountType.REAL_ESTATE) "bien" else "objet"

/*
	Achat d'un bien, tel que saisi.
	id         : achat modifié ; null pour un nouveau bien
	postingIds : jambes existantes (bien, puis paiement)
	accountId  : compte de biens
	name       : nom du bien
	date       : date d'achat
	price      : prix d'achat (texte)
	paidFrom   : compte payeur ; null pour un bien déjà possédé
	notes      : notes
*/
data class PurchaseForm(
	val id         : String?      = null,
	val postingIds : List<String> = emptyList(),
	val accountId  : String?,
	val name       : String       = "",
	val date       : LocalDate,
	val price      : String       = "",
	val paidFrom   : String?      = null,
	val notes      : String       = ""
)

/*
	Vente d'un bien, telle que saisie.
	id         : vente modifiée ; null pour une nouvelle vente
	postingIds : jambes existantes (sortie du bien, puis versement)
	purchaseId : achat du bien vendu
	date       : date de vente
	price      : prix de vente (texte)
	soldTo     : compte qui reçoit le prix ; null sans versement (don, perte)
	notes      : notes
*/
data class SaleForm(
	val id         : String?      = null,
	val postingIds : List<String> = emptyList(),
	val purchaseId : String,
	val date       : LocalDate,
	val price      : String       = "",
	val soldTo     : String?      = null,
	val notes      : String       = ""
)

/*
	Erreurs d'une saisie de bien, par champ.
	account : compte de biens, ou compte payeur ou bénéficiaire
	name    : nom
	price   : prix
	date    : date
*/
data class PropertyErrors(
	val account : String? = null,
	val name    : String? = null,
	val price   : String? = null,
	val date    : String? = null
)
{
	// Vrai sans aucune erreur.
	val isEmpty: Boolean get() = listOf(account, name, price, date).all { it == null }
}

/*
	Saisie d'un nouveau bien, datée du jour, déjà possédé.
	inAccountId : compte de biens
	inToday     : date du jour
*/
fun newPurchaseForm(
	inAccountId : String?,
	inToday     : LocalDate
): PurchaseForm = PurchaseForm(accountId = inAccountId, date = inToday)

/*
	Saisie pré-remplie pour modifier un bien.
	inItem : bien
*/
fun editPurchaseForm(inItem: PropertyItem): PurchaseForm
{
	val vTx = inItem.acquisition																// achat
	return PurchaseForm(
		id         = vTx.id,
		postingIds = listOfNotNull(vTx.postings.first { it.accountId == inItem.accountId }.id, vTx.postings.firstOrNull { it.accountId != inItem.accountId }?.id),
		accountId  = inItem.accountId,
		name       = vTx.label,
		date       = vTx.date,
		price      = editableEuros(inItem.cost),
		paidFrom   = inItem.paidFrom,
		notes      = vTx.notes.orEmpty()
	)
}

/*
	Vérifie un achat : compte de biens, nom, prix positif, compte payeur
	distinct d'un compte de biens.
	inForm     : saisie
	inAccounts : comptes
*/
fun checkPurchase(
	inForm     : PurchaseForm,
	inAccounts : List<Account>
): PropertyErrors
{
	val vPrice = parseEuros(inForm.price)															// prix lu
	return PropertyErrors(
		account = when
		{
			inAccounts.none { it.id == inForm.accountId && it.type.isProperty }  -> "Choisissez un compte Immobilier ou Collection."
			inForm.paidFrom != null && inAccounts.none { it.id == inForm.paidFrom && !it.type.isProperty } -> "Choisissez le compte qui a payé."
			else                                                                 -> null
		},
		name    = if (inForm.name.isBlank()) "Nommez le bien." else null,
		price   = when
		{
			inForm.price.isBlank()      -> "Indiquez le prix d’achat."
			vPrice == null || vPrice <= 0 -> "Prix illisible ou nul (ex. 185 000)."
			else                        -> null
		}
	)
}

/*
	Construit l'achat d'une saisie vérifiée : jambe du compte payeur s'il y
	en a un, puis jambe du bien (en dernier, comme à la relecture du classeur,
	où les comptes de biens viennent après les autres).
	inForm : saisie vérifiée
*/
fun buildPurchase(inForm: PurchaseForm): Transaction
{
	val vCost = parseEuros(inForm.price)!!														// prix d'achat
	val vId   = { inIndex: Int -> inForm.postingIds.getOrNull(inIndex) ?: UUID.randomUUID().toString() }	// identifiant de jambe
	return Transaction(
		id       = inForm.id ?: UUID.randomUUID().toString(),
		date     = inForm.date,
		kind     = TransactionKind.ACQUISITION,
		label    = inForm.name.trim(),
		notes    = inForm.notes.trim().ifEmpty { null },
		postings = listOfNotNull(inForm.paidFrom?.let { Posting(vId(1), it, -vCost) }, Posting(vId(0), inForm.accountId!!, vCost))
	)
}

/*
	Saisie d'une vente, datée du jour.
	inItem  : bien vendu
	inToday : date du jour
*/
fun newSaleForm(
	inItem  : PropertyItem,
	inToday : LocalDate
): SaleForm = SaleForm(purchaseId = inItem.acquisition.id, date = maxOf(inToday, inItem.acquisition.date), soldTo = inItem.paidFrom)

/*
	Saisie pré-remplie pour modifier une vente.
	inItem : bien vendu
*/
fun editSaleForm(inItem: PropertyItem): SaleForm
{
	val vTx = inItem.disposal!!																	// vente
	return SaleForm(
		id         = vTx.id,
		postingIds = listOfNotNull(vTx.postings.first { it.accountId == inItem.accountId }.id, vTx.postings.firstOrNull { it.accountId != inItem.accountId }?.id),
		purchaseId = inItem.acquisition.id,
		date       = vTx.date,
		price      = editableEuros(inItem.salePrice ?: 0L),
		soldTo     = inItem.soldTo,
		notes      = vTx.notes.orEmpty()
	)
}

/*
	Vérifie une vente : date au plus tôt celle de l'achat ; prix lisible,
	positif s'il est versé sur un compte (0 possible sans versement : don,
	perte) ; compte bénéficiaire hors comptes de biens.
	inForm     : saisie
	inItem     : bien vendu
	inAccounts : comptes
*/
fun checkSale(
	inForm     : SaleForm,
	inItem     : PropertyItem,
	inAccounts : List<Account>
): PropertyErrors
{
	val vPrice = parseEuros(inForm.price)															// prix lu
	return PropertyErrors(
		account = if (inForm.soldTo != null && inAccounts.none { it.id == inForm.soldTo && !it.type.isProperty }) "Choisissez le compte qui reçoit le prix." else null,
		price   = when
		{
			inForm.price.isBlank()                       -> "Indiquez le prix de vente (0 pour un don)."
			vPrice == null || vPrice < 0                 -> "Prix illisible (ex. 210 000)."
			vPrice == 0L && inForm.soldTo != null        -> "Un prix nul ne se verse sur aucun compte."
			else                                         -> null
		},
		date    = if (inForm.date.isBefore(inItem.acquisition.date)) "La vente ne peut pas précéder l’achat." else null
	)
}

/*
	Plus-value (positive) ou moins-value d'une vente saisie ; null tant que
	le prix est illisible.
	inForm : saisie
	inItem : bien vendu
*/
fun saleGain(
	inForm : SaleForm,
	inItem : PropertyItem
): Long? = parseEuros(inForm.price)?.takeIf { it >= 0 }?.let { it - inItem.cost }

/*
	Construit la vente d'une saisie vérifiée : versement du prix de vente
	s'il y en a un, puis sortie du bien pour son prix d'achat (en dernier).
	inForm : saisie vérifiée
	inItem : bien vendu
*/
fun buildSale(
	inForm : SaleForm,
	inItem : PropertyItem
): Transaction
{
	val vPrice = parseEuros(inForm.price)!!														// prix de vente
	val vId    = { inIndex: Int -> inForm.postingIds.getOrNull(inIndex) ?: UUID.randomUUID().toString() }	// identifiant de jambe
	return Transaction(
		id       = inForm.id ?: UUID.randomUUID().toString(),
		date     = inForm.date,
		kind     = TransactionKind.DISPOSAL,
		label    = inItem.name,
		notes    = inForm.notes.trim().ifEmpty { null },
		postings = listOfNotNull(inForm.soldTo?.takeIf { vPrice > 0 }?.let { Posting(vId(1), it, vPrice) }, Posting(vId(0), inItem.accountId, -inItem.cost)),
		linkedId = inItem.acquisition.id
	)
}
