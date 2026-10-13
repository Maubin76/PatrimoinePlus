package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate

// Biens d'un compte Immobilier ou Collection : chaque bien est son achat
// (ACQUISITION), éventuellement suivi de sa vente (DISPOSAL, liée à l'achat).
// Le solde du compte vaut le prix d'achat des biens détenus : un achat y
// entre son prix, une vente l'en sort au même prix ; le prix de vente va sur
// le compte choisi, si bien que la plus- ou moins-value se lit dans le
// patrimoine.

/*
	Bien d'un compte Immobilier ou Collection.
	acquisition : achat
	accountId   : compte de biens
	cost        : prix d'achat, en centimes (positif)
	paidFrom    : compte payeur ; null pour un bien déjà possédé
	disposal    : vente ; null tant que le bien est détenu
	salePrice   : prix de vente, en centimes ; null sans vente
	soldTo      : compte qui a reçu le prix de vente ; null sans vente ou sans versement
*/
data class PropertyItem(
	val acquisition : Transaction,
	val accountId   : String,
	val cost        : Long,
	val paidFrom    : String?,
	val disposal    : Transaction?,
	val salePrice   : Long?,
	val soldTo      : String?
)
{
	// Nom du bien (libellé de l'achat).
	val name: String get() = acquisition.label

	// Plus-value (positive) ou moins-value (négative) ; null sans vente.
	val gain: Long? get() = salePrice?.let { it - cost }

	/*
		Vrai si le bien est détenu à une date : acheté, pas encore vendu.
		inDate : date
	*/
	fun heldAt(inDate: LocalDate): Boolean = !acquisition.date.isAfter(inDate) && (disposal == null || disposal.date.isAfter(inDate))
}

/*
	Jambe d'une opération de bien sur un compte de biens.
	inTx         : achat ou vente d'un bien
	inProperties : identifiants des comptes de biens
*/
fun propertyPosting(
	inTx         : Transaction,
	inProperties : Set<String>
) = inTx.postings.firstOrNull { it.accountId in inProperties }

/*
	Biens d'un compte (ou de tous les comptes de biens), du plus récent achat
	au plus ancien, chacun avec sa vente éventuelle.
	inTransactions : toutes les opérations
	inAccounts     : comptes (pour reconnaître les comptes de biens)
	inAccountId    : compte voulu ; null pour tous les comptes de biens
*/
fun propertyItems(
	inTransactions : List<Transaction>,
	inAccounts     : List<Account>,
	inAccountId    : String? = null
): List<PropertyItem>
{
	val vProperties = inAccounts.filter { it.type.isProperty }.map { it.id }.toSet()	// comptes de biens
	val vSales      = inTransactions.filter { it.kind == TransactionKind.DISPOSAL && it.linkedId != null }.associateBy { it.linkedId!! }	// ventes par achat
	return inTransactions
		.filter { it.kind == TransactionKind.ACQUISITION }
		.mapNotNull { inTx ->
			val vLeg  = propertyPosting(inTx, vProperties) ?: return@mapNotNull null		// jambe du bien
			if (inAccountId != null && vLeg.accountId != inAccountId) return@mapNotNull null
			val vSale = vSales[inTx.id]														// vente
			PropertyItem(
				acquisition = inTx,
				accountId   = vLeg.accountId,
				cost        = vLeg.amountCents,
				paidFrom    = inTx.postings.firstOrNull { it.accountId !in vProperties }?.accountId,
				disposal    = vSale,
				salePrice   = vSale?.let { inSale -> inSale.postings.filter { it.accountId !in vProperties }.sumOf { it.amountCents } },
				soldTo      = vSale?.postings?.firstOrNull { it.accountId !in vProperties }?.accountId
			)
		}
		.sortedWith(compareByDescending<PropertyItem> { it.acquisition.date }.thenBy { it.name })
}

/*
	Première vente dont l'achat manque (supprimé sans elle) ; null si toutes
	les ventes ont leur achat.
	inTransactions : opérations restantes
*/
fun orphanedSale(inTransactions: List<Transaction>): Transaction?
{
	val vPurchases = inTransactions.filter { it.kind == TransactionKind.ACQUISITION }.map { it.id }.toSet()	// achats restants
	return inTransactions.firstOrNull { it.kind == TransactionKind.DISPOSAL && it.linkedId !in vPurchases }
}

/*
	Effet de la suppression définitive d'un compte sur les opérations.
	removed : opérations supprimées
	trimmed : ventes de biens gardées sans leur versement (le compte supprimé
	          avait reçu le prix : elles deviennent des ventes sans versement)
*/
data class AccountRemoval(
	val removed : Set<String>,
	val trimmed : List<Transaction>
)

/*
	Opérations à supprimer ou à corriger quand un compte est supprimé
	définitivement : toutes celles qui le touchent, sauf la vente d'un bien
	dont seul le versement le touchait (gardée sans versement) ; et les ventes
	dont l'achat disparaît (une vente ne reste jamais sans son achat).
	inTransactions : toutes les opérations
	inAccounts     : comptes (pour reconnaître les comptes de biens)
	inAccountId    : compte supprimé
*/
fun accountRemoval(
	inTransactions : List<Transaction>,
	inAccounts     : List<Account>,
	inAccountId    : String
): AccountRemoval
{
	val vProperties = inAccounts.filter { it.type.isProperty }.map { it.id }.toSet()	// comptes de biens
	val vTouched    = inTransactions.filter { inTx -> inTx.postings.any { it.accountId == inAccountId } }	// opérations du compte
	val vPaidOnly   = vTouched.filter { inTx -> inTx.kind == TransactionKind.DISPOSAL && propertyPosting(inTx, vProperties)?.accountId != inAccountId }	// ventes dont seul le versement touche le compte
	val vRemoved    = (vTouched - vPaidOnly.toSet()).map { it.id }.toMutableSet()		// opérations supprimées
	vRemoved += inTransactions.filter { it.kind == TransactionKind.DISPOSAL && it.linkedId in vRemoved }.map { it.id }
	return AccountRemoval(vRemoved, vPaidOnly.filter { it.id !in vRemoved }.map { inTx -> inTx.copy(postings = inTx.postings.filter { it.accountId != inAccountId }) })
}
