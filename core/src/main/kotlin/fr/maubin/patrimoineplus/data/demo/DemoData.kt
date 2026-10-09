package fr.maubin.patrimoineplus.data.demo

import fr.maubin.patrimoineplus.domain.calc.kPriceScale
import fr.maubin.patrimoineplus.domain.calc.kQuantityScale
import fr.maubin.patrimoineplus.domain.calc.tradeCashCents
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountStatement
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.DefaultCatalog
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate
import java.util.UUID

private const val kMonths = 6													// mois d'historique générés

/*
	Jeu de données de démonstration complet (synthétique).
	accounts     : comptes
	categories   : catégories
	assets       : actifs
	transactions : opérations
	prices       : cours relevés
	statements   : relevés de solde
*/
data class DemoBundle(
	val accounts     : List<Account>,
	val categories   : List<Category>,
	val assets       : List<Asset>,
	val transactions : List<Transaction>,
	val prices       : List<AssetPrice>,
	val statements   : List<AccountStatement>
)

/*
	Construit six mois de données de démonstration finissant aujourd'hui, plus
	une opération « à venir ». Pur et déterministe pour une date donnée (hors
	identifiants) : testable sur la JVM. Toutes les valeurs sont fictives.
	inToday : date du jour
*/
fun buildDemoData(inToday: LocalDate): DemoBundle
{
	val vCourant = Account(newId(), "Compte courant", AccountType.CHECKING, "Banque démo", 100, 0)	// compte courant
	val vJoint   = Account(newId(), "Compte joint", AccountType.CHECKING, "Banque démo", 50, 1)		// compte joint
	val vLivretA = Account(newId(), "Livret A", AccountType.SAVINGS, "Banque démo", 100, 2)			// livret A
	val vLdds    = Account(newId(), "LDDS", AccountType.SAVINGS, "Banque démo", 100, 3)				// LDDS
	val vPea     = Account(newId(), "PEA", AccountType.BROKERAGE, "Courtier démo", 100, 4)			// PEA
	val vCats    = DefaultCatalog.categories()								// catégories
	val vWorld   = Asset(newId(), "ETF Monde (démo)")							// premier actif
	val vUs      = Asset(newId(), "ETF États-Unis (démo)")						// second actif
	val vStart   = inToday.minusMonths(kMonths - 1L).withDayOfMonth(1)		// début de l'historique
	val vOps     = mutableListOf<Transaction>()								// opérations générées
	val vPrices  = mutableListOf<AssetPrice>()								// cours générés

	/*
		Catégorie du catalogue par nom.
		inName : nom
	*/
	fun cat(inName: String): String = vCats.first { it.name == inName }.id

	/*
		Ajoute une dépense ou un revenu si sa date n'est pas future.
		inDate     : date
		inAccount  : compte
		inCents    : montant signé
		inLabel    : libellé
		inCategory : nom de catégorie
	*/
	fun std(inDate: LocalDate, inAccount: Account, inCents: Long, inLabel: String, inCategory: String)
	{
		if (!inDate.isAfter(inToday))
		{
			vOps += Transaction(newId(), inDate, TransactionKind.STANDARD, inLabel, cat(inCategory),
				postings = listOf(Posting(newId(), inAccount.id, inCents)))
		}
	}

	/*
		Ajoute un virement si sa date n'est pas future.
		inDate  : date
		inFrom  : compte débité
		inTo    : compte crédité
		inCents : montant positif
		inLabel : libellé
	*/
	fun xfer(inDate: LocalDate, inFrom: Account, inTo: Account, inCents: Long, inLabel: String)
	{
		if (!inDate.isAfter(inToday))
		{
			vOps += Transaction(newId(), inDate, TransactionKind.TRANSFER, inLabel,
				postings = listOf(Posting(newId(), inFrom.id, -inCents), Posting(newId(), inTo.id, inCents)))
		}
	}

	/*
		Ajoute un achat sur le PEA si sa date n'est pas future.
		inDate  : date
		inAsset : actif
		inUnits : quantité, en unités
		inPrice : cours, en dix-millièmes d'euro
	*/
	fun buy(inDate: LocalDate, inAsset: Asset, inUnits: Long, inPrice: Long)
	{
		if (!inDate.isAfter(inToday))
		{
			val vTrade = Trade(newId(), inAsset.id, TradeSide.BUY, inUnits * kQuantityScale, inPrice, 199)	// ordre, 1,99 € de frais
			vOps += Transaction(newId(), inDate, TransactionKind.TRADE, "Achat ${inAsset.name}",
				postings = listOf(Posting(newId(), vPea.id, tradeCashCents(TradeSide.BUY, vTrade.quantity, vTrade.unitPrice, vTrade.feesCents))),
				trades   = listOf(vTrade))
		}
	}

	vOps += opening(vStart, vCourant, 250_000)
	vOps += opening(vStart, vJoint, 120_000)
	vOps += opening(vStart, vLivretA, 1_200_000)
	vOps += opening(vStart, vLdds, 400_000)
	vOps += opening(vStart, vPea, 50_000, listOf(Trade(newId(), vWorld.id, TradeSide.BUY, 40 * kQuantityScale, 85 * kPriceScale)))

	(0 until kMonths).forEach { inMonth ->
		val vFirst = vStart.plusMonths(inMonth.toLong())						// premier jour du mois
		val vWorldPrice = (85 + inMonth * 1.6).toPrice()						// cours du mois, premier actif
		val vUsPrice    = (450 + inMonth * 6.0).toPrice()						// cours du mois, second actif
		std(vFirst, vCourant, 320_000, "Salaire", "Salaire")
		xfer(vFirst.plusDays(1), vCourant, vJoint, 50_000, "Participation compte joint")
		std(vFirst.plusDays(3), vCourant, -95_000, "Loyer", "Logement")
		std(vFirst.plusDays(5), vCourant, -8_640, "Pass transport", "Transport")
		std(vFirst.plusDays(9), vCourant, -1_599, "Abonnement musique", "Abonnements")
		std(vFirst.plusDays(13), vCourant, -4_200, "Restaurant", "Loisirs")
		std(vFirst.plusDays(17), vCourant, -7_990, "Vêtements", "Shopping")
		std(vFirst.plusDays(19), vCourant, -2_500, "Médecin", "Santé")
		std(vFirst.plusDays(21), vCourant, -6_500, "Supérette", "Alimentation")
		std(vFirst.plusDays(25), vCourant, 1_750, "Remboursement sécu", "Santé")
		(0 until 4).forEach { inWeek -> std(vFirst.plusDays(2L + inWeek * 7), vJoint, -9_000 - inWeek * 350L, "Courses", "Alimentation") }
		std(vFirst.plusDays(10), vJoint, -8_000, "Électricité", "Logement")
		std(vFirst.plusDays(15), vJoint, -4_500, "Assurance habitation", "Divers")
		xfer(vFirst.plusDays(26), vCourant, vLivretA, 30_000, "Épargne du mois")
		xfer(vFirst.plusDays(27), vCourant, vPea, 40_000, "Versement PEA")
		buy(vFirst.plusDays(28), vWorld, 4, vWorldPrice)
		if (inMonth == 2)
		{
			xfer(vFirst.plusDays(11), vLdds, vPea, 100_000, "Versement PEA")
			buy(vFirst.plusDays(12), vUs, 2, vUsPrice)
		}
		val vEnd = vFirst.plusMonths(1).minusDays(1)							// dernier jour du mois
		if (!vEnd.isAfter(inToday))
		{
			vPrices += AssetPrice(newId(), vWorld.id, vEnd, vWorldPrice + 5_000)
			vPrices += AssetPrice(newId(), vUs.id, vEnd, vUsPrice + 30_000)
		}
	}
	vOps += Transaction(newId(), inToday.plusDays(5), TransactionKind.STANDARD, "Loyer (à venir)", cat("Logement"),
		postings = listOf(Posting(newId(), vCourant.id, -95_000)))

	val vStatement = AccountStatement(newId(), vLivretA.id, inToday.withDayOfMonth(1).minusDays(1),
		1_200_000 + 30_000L * (kMonths - 1) + 3_820)							// relevé avec 38,20 € d'intérêts non saisis
	return DemoBundle(listOf(vCourant, vJoint, vLivretA, vLdds, vPea), vCats, listOf(vWorld, vUs), vOps, vPrices, listOf(vStatement))
}

/*
	Solde initial d'un compte, avec positions initiales éventuelles.
	inDate    : date
	inAccount : compte
	inCents   : espèces
	inTrades  : positions initiales
*/
private fun opening(
	inDate    : LocalDate,
	inAccount : Account,
	inCents   : Long,
	inTrades  : List<Trade> = emptyList()
): Transaction =
	Transaction(newId(), inDate, TransactionKind.OPENING, "Solde initial",
		postings = listOf(Posting(newId(), inAccount.id, inCents)), trades = inTrades)

/*
	Cours en euros vers dix-millièmes d'euro.
*/
private fun Double.toPrice(): Long = Math.round(this * kPriceScale)

/*
	Nouvel identifiant.
*/
private fun newId(): String = UUID.randomUUID().toString()
