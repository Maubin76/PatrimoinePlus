package fr.maubin.patrimoineplus.domain

import fr.maubin.patrimoineplus.domain.calc.kPriceScale
import fr.maubin.patrimoineplus.domain.calc.kQuantityScale
import fr.maubin.patrimoineplus.domain.calc.tradeCashCents
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate

// Fabriques de données pour les tests : écritures courtes et lisibles.

private var gNextId = 0															// compteur d'identifiants de test

/*
	Identifiant unique de test.
	inPrefix : préfixe lisible
*/
fun id(inPrefix: String): String = "$inPrefix-${gNextId++}"

/*
	Date courte.
	inText : date ISO, « 2026-01-15 »
*/
fun d(inText: String): LocalDate = LocalDate.parse(inText)

/*
	Euros en centimes.
	inEuros : montant en euros
*/
fun eur(inEuros: Double): Long = Math.round(inEuros * 100)

/*
	Quantité en dix-millièmes.
	inUnits : nombre d'unités
*/
fun qty(inUnits: Double): Long = Math.round(inUnits * kQuantityScale)

/*
	Cours en dix-millièmes d'euro.
	inEuros : cours en euros
*/
fun price(inEuros: Double): Long = Math.round(inEuros * kPriceScale)

val kChecking  = Account("courant", "Compte courant", AccountType.CHECKING)			// compte courant
val kJoint     = Account("joint", "Compte joint", AccountType.CHECKING, sharePercent = 50)	// compte joint à 50 %
val kLivret    = Account("livret", "Livret A", AccountType.SAVINGS)					// livret
val kPea       = Account("pea", "PEA", AccountType.BROKERAGE)							// compte Bourse
val kGroceries = Category("courses", "Alimentation", "cart", CategoryKind.EXPENSE)	// catégorie de dépense
val kLeisure   = Category("loisirs", "Loisirs", "ticket", CategoryKind.EXPENSE)		// autre dépense
val kSalary    = Category("salaire", "Salaire", "work", CategoryKind.INCOME)			// catégorie de revenu
val kEtf       = Asset("etf", "ETF Monde", "FR0000000001")								// actif
val kStock     = Asset("action", "Action X")											// autre actif

/*
	Dépense ou revenu sur un compte.
	inDate     : date
	inAccount  : compte
	inCents    : montant signé (négatif = dépense)
	inCategory : catégorie
*/
fun standard(
	inDate     : String,
	inAccount  : Account,
	inCents    : Long,
	inCategory : Category
): Transaction =
	Transaction(id("tx"), d(inDate), TransactionKind.STANDARD, "Opération", categoryId = inCategory.id,
		postings = listOf(Posting(id("p"), inAccount.id, inCents)))

/*
	Virement entre deux comptes.
	inDate  : date
	inFrom  : compte débité
	inTo    : compte crédité
	inCents : montant positif
*/
fun transfer(
	inDate  : String,
	inFrom  : Account,
	inTo    : Account,
	inCents : Long
): Transaction =
	Transaction(id("tx"), d(inDate), TransactionKind.TRANSFER, "Virement",
		postings = listOf(Posting(id("p"), inFrom.id, -inCents), Posting(id("p"), inTo.id, inCents)))

/*
	Solde initial en espèces.
	inDate    : date
	inAccount : compte
	inCents   : solde
	inTrades  : positions initiales éventuelles
*/
fun opening(
	inDate    : String,
	inAccount : Account,
	inCents   : Long,
	inTrades  : List<Trade> = emptyList()
): Transaction =
	Transaction(id("tx"), d(inDate), TransactionKind.OPENING, "Solde initial",
		postings = listOf(Posting(id("p"), inAccount.id, inCents)), trades = inTrades)

/*
	Ordre de bourse cohérent (jambe calculée à partir de l'ordre).
	inDate    : date
	inAccount : compte Bourse
	inSide    : achat ou vente
	inAsset   : actif
	inUnits   : quantité en unités
	inPrice   : cours en euros
	inFees    : frais en euros
*/
fun trade(
	inDate    : String,
	inAccount : Account,
	inSide    : TradeSide,
	inAsset   : Asset,
	inUnits   : Double,
	inPrice   : Double,
	inFees    : Double = 0.0
): Transaction
{
	val vTrade = Trade(id("t"), inAsset.id, inSide, qty(inUnits), price(inPrice), eur(inFees))	// l'ordre
	val vCash  = tradeCashCents(inSide, vTrade.quantity, vTrade.unitPrice, vTrade.feesCents)		// jambe espèces
	return Transaction(id("tx"), d(inDate), TransactionKind.TRADE, "Ordre",
		postings = listOf(Posting(id("p"), inAccount.id, vCash)), trades = listOf(vTrade))
}

/*
	Cours relevé.
	inDate  : date
	inAsset : actif
	inPrice : cours en euros
*/
fun quote(
	inDate  : String,
	inAsset : Asset,
	inPrice : Double
): AssetPrice =
	AssetPrice(id("q"), inAsset.id, d(inDate), price(inPrice))
