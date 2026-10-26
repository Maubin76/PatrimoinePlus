package fr.maubin.patrimoineplus.data.demo

import fr.maubin.patrimoineplus.domain.calc.kPriceScale
import fr.maubin.patrimoineplus.domain.calc.kQuantityScale
import fr.maubin.patrimoineplus.domain.calc.tradeCashCents
import fr.maubin.patrimoineplus.data.DataSet
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
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.calc.amortization
import fr.maubin.patrimoineplus.domain.calc.remainingAt
import fr.maubin.patrimoineplus.domain.calc.dueOperations
import java.time.LocalDate
import java.util.UUID
import fr.maubin.patrimoineplus.domain.model.Profile
import fr.maubin.patrimoineplus.domain.model.Ownership
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileId
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileName
import fr.maubin.patrimoineplus.domain.calc.withProfiles

private const val kMonths = 6													// mois d'historique générés

/*
	Construit six mois de données de démonstration finissant aujourd'hui, plus
	une opération « à venir ». Pur et déterministe pour une date donnée (hors
	identifiants des opérations) : testable sur la JVM. Comptes, catégories et
	actifs gardent les mêmes identifiants d'un lancement à l'autre : le budget
	et les objectifs du PC, qui les désignent par identifiant, restent liés
	quand la démonstration est régénérée. Toutes les valeurs sont fictives.
	inToday : date du jour
*/
fun buildDemoData(inToday: LocalDate): DataSet
{
	val vCourant = Account(stableId("compte:compte-courant"), "Compte courant", AccountType.CHECKING, "Banque démo", 100, 0)	// compte courant
	val vJoint   = Account(stableId("compte:compte-joint"), "Compte joint", AccountType.CHECKING, "Banque démo", 50, 1)		// compte joint
	val vLivretA = Account(stableId("compte:livret-a"), "Livret A", AccountType.SAVINGS, "Banque démo", 100, 2)			// livret A
	val vLdds    = Account(stableId("compte:ldds"), "LDDS", AccountType.SAVINGS, "Banque démo", 100, 3)				// LDDS
	val vPea     = Account(stableId("compte:pea"), "PEA", AccountType.BROKERAGE, "Courtier démo", 100, 4)			// PEA
	val vHome    = Account(stableId("compte:residence-principale"), "Résidence principale", AccountType.REAL_ESTATE, null, 50, 5)	// immobilier, détenu à moitié
	val vObjects = Account(stableId("compte:collection"), "Collection", AccountType.COLLECTIBLE, null, 100, 6)			// objets de collection
	val vCar     = Account(stableId("compte:credit-auto"), "Crédit auto", AccountType.LOAN, "Banque démo", 100, 7)		// crédit amortissable
	val vMortgage = Account(stableId("compte:pret-immobilier"), "Prêt immobilier", AccountType.LOAN, "Banque démo", 50, 8)	// crédit de la résidence principale, à moitié
	val vCats    = DefaultCatalog.categories().map { it.copy(id = stableId("categorie:${it.kind}:${it.name}")) } +
		Category(stableId("categorie:EXPENSE:Crédit"), "Crédit", "bank", CategoryKind.EXPENSE, 99)	// catégories, identifiants stables, et intérêts d'emprunt
	val vWorld   = Asset(stableId("actif:etf-monde"), "ETF Monde (démo)")							// premier actif
	val vUs      = Asset(stableId("actif:etf-etats-unis"), "ETF États-Unis (démo)")						// second actif
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
	/*
		Ajoute l'achat d'un bien si sa date n'est pas future.
		inDate    : date
		inAccount : compte de biens
		inName    : nom du bien
		inCents   : prix d'achat
		inPayer   : compte payeur ; null pour un bien déjà possédé
		inId      : identifiant de l'achat
		Retour : l'achat ; null s'il est futur
	*/
	fun acquire(inDate: LocalDate, inAccount: Account, inName: String, inCents: Long, inPayer: Account?, inId: String = newId()): Transaction?
	{
		if (inDate.isAfter(inToday)) return null
		return Transaction(inId, inDate, TransactionKind.ACQUISITION, inName,
			postings = listOfNotNull(inPayer?.let { Posting(newId(), it.id, -inCents) }, Posting(newId(), inAccount.id, inCents))).also { vOps += it }
	}

	val vFlat    = acquire(vStart, vHome, "Appartement (démo)", 24_000_000, null, stableId("bien:appartement"))	// résidence principale, déjà possédée au début du suivi (comme son prêt)
	acquire(vStart, vObjects, "Lithographie", 180_000, null)
	acquire(vStart.plusMonths(3).plusDays(7), vObjects, "Montre de collection", 420_000, vCourant)
	acquire(vStart, vObjects, "Vinyles des années 70", 60_000, null)?.let { inVinyls ->
		val vSold = vStart.plusMonths(4).plusDays(19)											// vente des vinyles
		if (!vSold.isAfter(inToday))
		{
			vOps += Transaction(newId(), vSold, TransactionKind.DISPOSAL, inVinyls.label,
				postings = listOf(Posting(newId(), vCourant.id, 85_000), Posting(newId(), vObjects.id, -60_000)), linkedId = inVinyls.id)
		}
	}
	vOps += Transaction(newId(), inToday.plusDays(5), TransactionKind.STANDARD, "Taxe foncière (à venir)", cat("Logement"),
		postings = listOf(Posting(newId(), vCourant.id, -95_000)))

	val vStatement = AccountStatement(newId(), vLivretA.id, inToday.withDayOfMonth(1).minusDays(1),
		1_200_000 + 30_000L * (kMonths - 1) + 3_820)							// relevé avec 38,20 € d'intérêts non saisis
	val vLoan    = Loan(stableId("credit:auto"), vCar.id, 1_500_000, 450, 48, vStart.minusMonths(11).plusDays(4), 1_200, vCourant.id, cat("Crédit"))	// crédit commencé il y a un an
	val vPaid    = vLoan.copy(last = amortization(vLoan).last { it.date.isBefore(vStart) }.date)	// mensualités payées avant la démonstration
	vOps += opening(vStart, vCar, -remainingAt(vPaid, vStart.minusDays(1)))
	val vHomeLoan = Loan(stableId("credit:immobilier"), vMortgage.id, 22_000_000, 150, 300, vStart.minusYears(7).plusDays(4), 3_000, vCourant.id, cat("Crédit"), propertyId = vFlat?.id)	// prêt de l'appartement, commencé il y a sept ans
	val vHomePaid = vHomeLoan.copy(last = amortization(vHomeLoan).last { it.date.isBefore(vStart) }.date)	// mensualités payées avant la démonstration
	vOps += opening(vStart, vMortgage, -remainingAt(vHomePaid, vStart.minusDays(1)))
	val vRecs    = listOf(
		Recurrence(stableId("recurrence:salaire"), "Salaire", vCourant.id, null, cat("Salaire"), 320_000, Frequency.MONTHLY, inToday.withDayOfMonth(1).plusMonths(1)),
		Recurrence(stableId("recurrence:musique"), "Abonnement musique", vCourant.id, null, cat("Abonnements"), -1_599, Frequency.MONTHLY, inToday.withDayOfMonth(10).plusMonths(1)),
		Recurrence(stableId("recurrence:livret"), "Épargne du mois", vCourant.id, vLivretA.id, null, 30_000, Frequency.MONTHLY, inToday.withDayOfMonth(27).plusMonths(1)),
		Recurrence(stableId("recurrence:assurance"), "Assurance auto", vCourant.id, null, cat("Transport"), -48_000, Frequency.YEARLY, inToday.plusMonths(3).withDayOfMonth(15))
	)																		// opérations récurrentes (prochaines échéances à venir)
	val vAccounts = listOf(vCourant, vJoint, vLivretA, vLdds, vPea, vHome, vObjects, vCar, vMortgage)	// comptes
	val vDue     = dueOperations(vAccounts, vOps, emptyList(), listOf(vPaid, vHomePaid), inToday)	// mensualités des crédits depuis le début de la démonstration
	vOps += vDue.transactions
	val vMoved   = vDue.loans.associateBy { it.id }									// crédits avancés
	return DataSet(vAccounts, vCats, listOf(vWorld, vUs), vOps, vPrices, listOf(vStatement), vRecs, listOf(vPaid, vHomePaid).map { vMoved[it.id] ?: it })
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

/*
	Identifiant stable, dérivé d'une clé (même clé, même identifiant à
	chaque génération de la démonstration).
	inKey : clé lisible (« compte:livret-a »)
*/
private fun stableId(inKey: String): String = UUID.nameUUIDFromBytes("patrimoineplus-demo:$inKey".toByteArray(Charsets.UTF_8)).toString()

const val kDemoPartnerId = "profil:demo-alex"									// second profil de la démonstration

/*
	Démonstration d'une famille : le profil « Moi » et « Alex », qui
	partagent à parts égales le compte joint, la résidence principale et son
	crédit ; les autres comptes sont à « Moi ». La vue de « Moi » est la
	démonstration habituelle.
	inData : démonstration
*/
fun withDemoFamily(inData: DataSet): DataSet =
	inData.copy(
		profiles = listOf(Profile(kDefaultProfileId, kDefaultProfileName, 0), Profile(kDemoPartnerId, "Alex", 1)),
		accounts = inData.accounts.map { inAccount ->
			if (inAccount.sharePercent < 100) inAccount.copy(owners = listOf(Ownership(kDefaultProfileId, inAccount.sharePercent), Ownership(kDemoPartnerId, 100 - inAccount.sharePercent)))
			else inAccount.copy(owners = listOf(Ownership(kDefaultProfileId, 100)))
		}
	).withProfiles()

