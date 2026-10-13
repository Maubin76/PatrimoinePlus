package fr.maubin.patrimoineplus.domain.model

import java.time.LocalDate

// Modèles métier : Kotlin pur, sans dépendance Android ni Room. Les montants
// sont en centimes (Long) ; quantités et cours en dix-millièmes (Long).

/*
	Type d'un compte ; fixe la manière dont sa valeur se calcule.
	label : libellé affiché
*/
enum class AccountType(val label: String)
{
	CHECKING("Courant"),
	SAVINGS("Épargne"),
	BROKERAGE("Bourse"),
	REAL_ESTATE("Immobilier"),												// biens immobiliers, comptés à leur prix d'achat
	COLLECTIBLE("Collection"),												// objets de collection, comptés à leur prix d'achat
	LOAN("Crédit");															// crédit : solde négatif, capital restant dû (déduit du patrimoine)

	// Vrai pour un compte de biens (immobilier, collection) : pas un vrai compte
	// en banque, il ne reçoit que des achats et des ventes de biens et vaut le
	// prix d'achat des biens détenus.
	val isProperty: Boolean get() = this == REAL_ESTATE || this == COLLECTIBLE
}

/*
	Compte, créé et géré dans l'application.
	id           : identifiant (UUID)
	name         : nom choisi par l'utilisateur
	type         : Courant, Épargne ou Bourse
	institution  : établissement (banque, courtier), facultatif
	sharePercent : part détenue, de 1 à 100 (50 pour un compte joint)
	sortOrder    : ordre d'affichage
	archived     : masqué des saisies, conservé dans l'historique et le patrimoine
*/
data class Account(
	val id           : String,
	val name         : String,
	val type         : AccountType,
	val institution  : String?  = null,
	val sharePercent : Int      = 100,
	val sortOrder    : Int      = 0,
	val archived     : Boolean  = false
)

/*
	Nature d'une catégorie.
*/
enum class CategoryKind
{
	EXPENSE,
	INCOME
}

/*
	Catégorie de dépense ou de revenu.
	id        : identifiant (UUID)
	name      : nom
	icon      : clé d'icône
	kind      : dépense ou revenu
	sortOrder : ordre d'affichage
	archived  : masquée des saisies, conservée dans l'historique
*/
data class Category(
	val id        : String,
	val name      : String,
	val icon      : String,
	val kind      : CategoryKind,
	val sortOrder : Int     = 0,
	val archived  : Boolean = false
)

/*
	Nature d'une opération ; fixe ses invariants (voir TransactionValidator).
*/
enum class TransactionKind
{
	STANDARD,																// dépense ou revenu, une jambe, une catégorie
	TRANSFER,																// virement entre deux de mes comptes, deux jambes
	TRADE,																	// ordre de bourse, une jambe et un ordre
	OPENING,																// solde initial d'un compte
	ACQUISITION,															// achat d'un bien : jambe du compte de biens, et du compte payeur s'il y en a un
	DISPOSAL																// vente d'un bien : sortie au prix d'achat, et prix de vente versé sur un compte s'il y en a un
}

/*
	Jambe d'une opération : mouvement sur un compte.
	id          : identifiant (UUID)
	accountId   : compte concerné
	amountCents : montant signé, positif = entrée sur le compte
*/
data class Posting(
	val id          : String,
	val accountId   : String,
	val amountCents : Long
)

/*
	Sens d'un ordre de bourse.
*/
enum class TradeSide
{
	BUY,
	SELL
}

/*
	Ordre de bourse (ou position initiale d'un compte Bourse).
	id        : identifiant (UUID)
	assetId   : actif concerné
	side      : achat ou vente
	quantity  : quantité, en dix-millièmes d'unité
	unitPrice : cours, en dix-millièmes d'euro
	feesCents : frais, en centimes
*/
data class Trade(
	val id        : String,
	val assetId   : String,
	val side      : TradeSide,
	val quantity  : Long,
	val unitPrice : Long,
	val feesCents : Long = 0
)

/*
	Opération : un événement daté, composé de jambes et d'ordres éventuels.
	id         : identifiant (UUID)
	date       : date de l'opération
	kind       : nature
	label      : libellé
	categoryId : catégorie (STANDARD seulement)
	assetId    : actif rattaché (dividende), facultatif
	notes      : notes libres
	postings   : jambes
	trades     : ordres de bourse
	linkedId   : opération liée (vente d'un bien : son achat) ; null sinon
*/
data class Transaction(
	val id         : String,
	val date       : LocalDate,
	val kind       : TransactionKind,
	val label      : String,
	val categoryId : String?       = null,
	val assetId    : String?       = null,
	val notes      : String?       = null,
	val postings   : List<Posting> = emptyList(),
	val trades     : List<Trade>   = emptyList(),
	val linkedId   : String?       = null
)
{
	/*
		Vrai si l'opération est « à venir » : datée après aujourd'hui.
		inToday : date du jour
	*/
	fun isUpcoming(inToday: LocalDate): Boolean = date.isAfter(inToday)
}

/*
	Actif coté (titre, ETF), partagé entre les comptes Bourse.
	id       : identifiant (UUID)
	name     : nom
	isin     : code ISIN, facultatif
	archived : masqué des saisies
	ticker   : symbole de cotation en euros (Yahoo Finance, ex. CW8.PA) ; null si les cours sont saisis à la main
*/
data class Asset(
	val id       : String,
	val name     : String,
	val isin     : String? = null,
	val archived : Boolean = false,
	val ticker   : String? = null
)

/*
	Relevé de solde d'un compte, saisi depuis la banque : sert de contrôle.
	id           : identifiant (UUID)
	accountId    : compte relevé
	date         : date du relevé
	balanceCents : solde indiqué par la banque
*/
data class AccountStatement(
	val id           : String,
	val accountId    : String,
	val date         : LocalDate,
	val balanceCents : Long
)

/*
	Cours relevé d'un actif à une date.
	id        : identifiant (UUID)
	assetId   : actif
	date      : date du cours
	unitPrice : cours, en dix-millièmes d'euro
*/
data class AssetPrice(
	val id        : String,
	val assetId   : String,
	val date      : LocalDate,
	val unitPrice : Long
)

const val kOpeningLabel = "Solde initial"										// libellé des soldes initiaux
