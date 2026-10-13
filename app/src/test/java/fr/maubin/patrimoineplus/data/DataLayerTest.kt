package fr.maubin.patrimoineplus.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.BackupRepository
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.demo.DemoRepository
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.Opening
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.valuePortfolio
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.domain.rules.SetupError
import fr.maubin.patrimoineplus.domain.rules.ValidationError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import fr.maubin.patrimoineplus.data.repo.PlanningRepository
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.domain.calc.remainingAt
import fr.maubin.patrimoineplus.domain.calc.dueOperations
import fr.maubin.patrimoineplus.domain.calc.dueId
import fr.maubin.patrimoineplus.domain.calc.loanPayment
import fr.maubin.patrimoineplus.domain.calc.amortization
import fr.maubin.patrimoineplus.data.db.toHeaderEntity
import fr.maubin.patrimoineplus.data.db.toPostingEntities
import fr.maubin.patrimoineplus.data.db.toTradeEntities

/*
	Tests de la couche données sur une base Room en mémoire (Robolectric) :
	dépôts, contraintes SQL, aller-retour complet des opérations.
*/
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DataLayerTest
{
	private val kClock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private lateinit var fDb           : PatrimoineDatabase					// base en mémoire
	private lateinit var fAccounts     : AccountRepository					// dépôt des comptes
	private lateinit var fCategories   : CategoryRepository					// dépôt des catégories
	private lateinit var fTransactions : TransactionRepository				// dépôt des opérations
	private lateinit var fMarket       : MarketRepository						// dépôt des actifs et cours
	private lateinit var fPlanning     : PlanningRepository					// dépôt des récurrences et crédits

	/*
		Ouvre une base vierge avant chaque test.
	*/
	@Before
	fun open()
	{
		fDb           = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PatrimoineDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		fAccounts     = AccountRepository(fDb, kClock)
		fCategories   = CategoryRepository(fDb, kClock)
		fTransactions = TransactionRepository(fDb, kClock)
		fMarket       = MarketRepository(fDb, kClock)
		fPlanning     = PlanningRepository(fDb, kClock)
	}

	/*
		Ferme la base après chaque test.
	*/
	@After
	fun close()
	{
		fDb.close()
	}

	/*
		Crée un compte et renvoie sa version enregistrée.
		inName  : nom
		inType  : type
		inShare : part détenue
	*/
	private suspend fun account(
		inName  : String,
		inType  : AccountType,
		inShare : Int = 100
	): Account
	{
		val vId = (fAccounts.create(inName, inType, "Banque", inShare) as SaveResult.Saved).id	// identifiant
		return fAccounts.observeAccounts().first().single { it.id == vId }
	}

	/*
		Biens : un compte Immobilier ou Collection refuse un solde initial ;
		achat payé depuis le compte courant (débité), vente liée (prix versé,
		bien sorti au prix d'achat) ; supprimer l'achat seul est refusé, avec
		sa vente accepté ; une dépense sur un compte de biens est refusée.
	*/
	@Test
	fun propertyPurchaseAndSale() = runTest {
		val vChecking = account("Courant", AccountType.CHECKING)					// compte payeur
		assertEquals(SaveResult.Rejected(listOf(SetupError.PropertyOpening)), fAccounts.create("Maison", AccountType.REAL_ESTATE, null, 100, Opening(100, LocalDate.parse("2026-10-01"))))
		val vObjects  = account("Collection", AccountType.COLLECTIBLE)				// compte de biens
		val vBuy      = Transaction("b1", LocalDate.parse("2026-09-01"), TransactionKind.ACQUISITION, "Montre",
			postings = listOf(Posting("bp1", vObjects.id, 420_000), Posting("bp2", vChecking.id, -420_000)))	// achat payé
		assertTrue(fTransactions.save(vBuy) is SaveResult.Saved)
		val vSell     = Transaction("s1", LocalDate.parse("2026-10-01"), TransactionKind.DISPOSAL, "Montre",
			postings = listOf(Posting("sp1", vObjects.id, -420_000), Posting("sp2", vChecking.id, 500_000)), linkedId = "b1")	// vente liée
		assertTrue(fTransactions.save(vSell) is SaveResult.Saved)
		val vLedger   = Ledger(fTransactions.observeTransactions().first())		// grand livre relu
		assertEquals(0L, vLedger.balanceAt(vObjects.id, LocalDate.parse("2026-10-12")))
		assertEquals(80_000L, vLedger.balanceAt(vChecking.id, LocalDate.parse("2026-10-12")))
		assertEquals("b1", fTransactions.observeTransactions().first().single { it.id == "s1" }.linkedId)
		assertEquals(ValidationError.SaleWithoutPurchase("Montre").message, fTransactions.delete(listOf("b1")))
		assertEquals(null, fTransactions.delete(listOf("b1", "s1")))
		val vSpend    = Transaction("x1", LocalDate.parse("2026-10-02"), TransactionKind.STANDARD, "Réparation", category("Divers"),
			postings = listOf(Posting("xp1", vObjects.id, -5_000)))					// dépense sur le compte de biens
		assertTrue((fTransactions.save(vSpend) as SaveResult.Rejected).errors.contains(ValidationError.PropertyOnly))
	}

	/*
		Corrections de la relecture : supprimer le compte qui a reçu le prix
		d'une vente garde la vente, sans versement ; un compte utilisé par une
		récurrence ne change plus de type ; un import ne remplace pas des
		données modifiées depuis la décision de synchronisation.
	*/
	@Test
	fun reviewFixesOnPlanningAndSync() = runTest {
		val vChecking = account("Courant", AccountType.CHECKING)					// compte payeur
		val vLivret   = account("Livret", AccountType.SAVINGS)						// compte qui reçoit le prix
		val vObjects  = account("Collection", AccountType.COLLECTIBLE)				// compte de biens
		fTransactions.save(Transaction("b1", LocalDate.parse("2026-09-01"), TransactionKind.ACQUISITION, "Montre",
			postings = listOf(Posting("bp1", vObjects.id, 420_000), Posting("bp2", vChecking.id, -420_000))))
		fTransactions.save(Transaction("s1", LocalDate.parse("2026-10-01"), TransactionKind.DISPOSAL, "Montre",
			postings = listOf(Posting("sp1", vObjects.id, -420_000), Posting("sp2", vLivret.id, 500_000)), linkedId = "b1"))
		assertEquals(0, fAccounts.deletePermanently(vLivret.id))
		assertEquals(listOf(vObjects.id), fTransactions.observeTransactions().first().single { it.id == "s1" }.postings.map { it.accountId })	// vente gardée sans versement
		val vFood     = category("Courses")												// catégorie de dépense
		val vJoint    = account("Joint", AccountType.CHECKING)						// compte sans opération, utilisé par une récurrence
		assertEquals(null, fPlanning.saveRecurrence(Recurrence("r", "Cantine", vJoint.id, null, vFood, -6_000, Frequency.MONTHLY, LocalDate.parse("2026-12-01"))))
		assertEquals(SaveResult.Rejected(listOf(SetupError.TypeUsed)), fAccounts.update(vJoint.copy(type = AccountType.SAVINGS)))
		val vBackup   = BackupRepository(fDb, kClock)										// sauvegarde
		val vFile     = vBackup.snapshot()													// contenu au moment de la décision
		val vExpected = syncFingerprint(vFile)												// son empreinte
		fPlanning.deleteRecurrence("r")														// modification pendant la synchronisation
		assertFalse(vBackup.replaceAll(vFile.copy(recurrences = emptyList(), accounts = emptyList(), transactions = emptyList()), vExpected))
		assertEquals(3, fAccounts.observeAccounts().first().size)						// rien n'a été remplacé
		assertTrue(vBackup.replaceAll(vFile, syncFingerprint(vBackup.snapshot())))
		assertEquals(1, fPlanning.observeRecurrences().first().size)
	}

	/*
		Mensualités en une opération sur le téléphone : les anciennes paires
		(virement du capital, dépense des intérêts) sont fusionnées par
		applyDue, sans changer les soldes ; une mensualité saisie à la main
		passe les règles ; crédit lié à un bien, lien refusé vers un bien
		inconnu, retiré quand l'achat est supprimé.
	*/
	@Test
	fun loanPaymentsAndProperty() = runTest {
		val vChecking = account("Courant", AccountType.CHECKING)					// compte payeur
		val vObjects  = account("Collection", AccountType.COLLECTIBLE)				// compte de biens
		val vStart    = Loan("", "", 1_000_000, 300, 12, LocalDate.parse("2026-09-10"), 500, vChecking.id, "")	// crédit commencé le mois dernier
		assertEquals(null, fPlanning.createLoan("Crédit auto", null, 100, vStart))
		val vLoan     = fPlanning.observeLoans().first().single()					// crédit enregistré
		val vPart     = amortization(vLoan).first()									// mensualité de septembre (tenue pour payée)
		val vKey      = "${vLoan.id}:1"												// clé de l'échéance
		listOf(
			Transaction(dueId("$vKey:capital"), vPart.date, TransactionKind.TRANSFER, "Mensualité Crédit auto (1/12)",
				postings = listOf(Posting(dueId("$vKey:capital:out"), vChecking.id, -vPart.capital), Posting(dueId("$vKey:capital:in"), vLoan.accountId, vPart.capital))),
			Transaction(dueId("$vKey:frais"), vPart.date, TransactionKind.STANDARD, "Intérêts et assurance Crédit auto (1/12)", vLoan.categoryId,
				postings = listOf(Posting(dueId("$vKey:frais:p"), vChecking.id, -(vPart.interest + vPart.insurance))))
		).forEach { fDb.transactionDao().save(it.toHeaderEntity(1, 1), it.toPostingEntities(), it.toTradeEntities()) }	// paire écrite par une ancienne version
		val vBefore   = Ledger(fTransactions.observeTransactions().first())			// soldes avant fusion
		assertEquals(1, fPlanning.applyDue())
		val vAll      = fTransactions.observeTransactions().first()					// opérations après fusion
		assertTrue(vAll.none { it.id == dueId("$vKey:capital") || it.id == dueId("$vKey:frais") })
		assertEquals(listOf(-vPart.total, vPart.capital), vAll.single { it.kind == TransactionKind.LOAN_PAYMENT }.postings.map { it.amountCents })
		listOf(vChecking.id, vLoan.accountId).forEach { assertEquals(vBefore.balanceAt(it, LocalDate.parse("2026-10-07")), Ledger(vAll).balanceAt(it, LocalDate.parse("2026-10-07"))) }
		assertEquals(0, fPlanning.applyDue())
		val vEarly    = loanPayment("anticipe", LocalDate.parse("2026-10-06"), "Remboursement anticipé", vChecking.id, vLoan.accountId, 200_000, 0, null)	// remboursement anticipé
		assertTrue(fTransactions.save(vEarly) is SaveResult.Saved)
		val vWatch    = Transaction("montre", LocalDate.parse("2026-09-01"), TransactionKind.ACQUISITION, "Montre",
			postings = listOf(Posting("mp", vObjects.id, 420_000)))					// bien déjà possédé
		assertTrue(fTransactions.save(vWatch) is SaveResult.Saved)
		assertEquals("Bien introuvable.", fPlanning.setLoanProperty(vLoan.id, "inconnu"))
		assertEquals(null, fPlanning.setLoanProperty(vLoan.id, vWatch.id))
		assertEquals(vWatch.id, fPlanning.observeLoans().first().single().propertyId)
		assertEquals(null, fTransactions.delete(listOf(vWatch.id)))
		assertEquals(null, fPlanning.observeLoans().first().single().propertyId)
	}

	/*
		Récurrences et crédits : récurrence commencée dans le passé, ses
		échéances créées dès l'enregistrement et une seule fois ; récurrence
		invalide refusée ; crédit créé (compte Crédit, catégorie « Crédit »,
		solde du jour = capital restant dû) ; mensualités créées plus tard,
		identiques au calcul du PC ; aller-retour par le classeur ; compte
		supprimé : ses récurrences disparaissent.
	*/
	@Test
	fun recurrencesAndLoans() = runTest {
		val vChecking = account("Courant", AccountType.CHECKING)						// compte courant
		val vFood     = category("Courses")												// catégorie de dépense
		assertTrue(fPlanning.saveRecurrence(Recurrence("r", "Cantine", vChecking.id, null, null, -6_000, Frequency.MONTHLY, LocalDate.parse("2026-09-01"))) != null)	// sans catégorie : refusée
		assertEquals(null, fPlanning.saveRecurrence(Recurrence("r", "Cantine", vChecking.id, null, vFood, -6_000, Frequency.MONTHLY, LocalDate.parse("2026-09-01"))))
		assertEquals(2, fTransactions.observeTransactions().first().count { it.label == "Cantine" })	// 1er septembre et 1er octobre
		assertEquals(0, fPlanning.applyDue())
		assertEquals(LocalDate.parse("2026-10-01"), fPlanning.observeRecurrences().first().single().last)
		val vLoan = Loan("", "", 1_500_000, 450, 48, LocalDate.parse("2025-11-05"), 1_200, vChecking.id, "")	// crédit commencé il y a onze mois
		assertEquals(null, fPlanning.createLoan("Crédit auto", null, 100, vLoan))
		val vSaved = fPlanning.observeLoans().first().single()								// crédit enregistré
		assertEquals(AccountType.LOAN, fAccounts.observeAccounts().first().single { it.id == vSaved.accountId }.type)
		assertEquals("Crédit", fCategories.observeCategories().first().single { it.id == vSaved.categoryId }.name)
		assertEquals(LocalDate.parse("2026-10-05"), vSaved.last)
		assertEquals(-remainingAt(vSaved, LocalDate.parse("2026-10-07")), Ledger(fTransactions.observeTransactions().first()).balanceAt(vSaved.accountId, LocalDate.parse("2026-10-07")))
		val vLater = PlanningRepository(fDb, Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneId.of("Europe/Paris")))	// un mois plus tard
		assertEquals(1 + 1, vLater.applyDue())											// mensualité (une seule opération) et cantine
		val vPc = dueOperations(fAccounts.observeAccounts().first(), emptyList(), emptyList(), listOf(vSaved), LocalDate.parse("2026-11-07")).transactions.map { it.id }.toSet()	// calcul du PC
		assertTrue(fTransactions.observeTransactions().first().map { it.id }.containsAll(vPc))
		val vBackup = BackupRepository(fDb, kClock)											// sauvegarde
		val vBytes  = java.io.ByteArrayOutputStream().also { writeBackupXlsx(vBackup.snapshot(), it) }.toByteArray()	// classeur
		val vRecs   = fPlanning.observeRecurrences().first()								// récurrences avant
		vBackup.replaceAll((readBackupXlsx(java.io.ByteArrayInputStream(vBytes)) as BackupRead.Valid).file)
		assertEquals(vRecs, fPlanning.observeRecurrences().first())
		assertEquals(listOf(vSaved.copy(last = LocalDate.parse("2026-11-05"))), fPlanning.observeLoans().first())
		fAccounts.deletePermanently(vChecking.id)
		assertTrue(fPlanning.observeRecurrences().first().isEmpty())
		assertTrue(fPlanning.observeLoans().first().isEmpty())
	}

	/*
		Crée une catégorie et renvoie son identifiant.
		inName : nom
		inKind : nature
	*/
	private suspend fun category(
		inName : String,
		inKind : CategoryKind = CategoryKind.EXPENSE
	): String =
		(fCategories.create(inName, "icon", inKind) as SaveResult.Saved).id

	/*
		Virement construit.
		inFrom  : compte débité
		inTo    : compte crédité
		inCents : montant
	*/
	private fun transferTx(
		inFrom  : Account,
		inTo    : Account,
		inCents : Long
	): Transaction =
		Transaction("v-${inFrom.id}-${inTo.id}-$inCents", LocalDate.parse("2026-10-01"), TransactionKind.TRANSFER, "Virement",
			postings = listOf(Posting("${inFrom.id}-out-$inCents", inFrom.id, -inCents), Posting("${inTo.id}-in-$inCents", inTo.id, inCents)))

	/*
		Création de comptes : règles appliquées, ordre d'affichage croissant,
		champs enregistrés (établissement, part détenue).
	*/
	@Test
	fun accountsAreCreatedInApp() = runTest {
		assertEquals(SaveResult.Rejected(listOf(SetupError.BlankName)), fAccounts.create(" ", AccountType.CHECKING))
		assertEquals(SaveResult.Rejected(listOf(SetupError.InvalidShare)), fAccounts.create("Joint", AccountType.CHECKING, null, 0))
		val vFirst  = account("Courant", AccountType.CHECKING)					// premier compte
		val vSecond = account("Joint", AccountType.CHECKING, 50)					// second compte
		assertEquals(0, vFirst.sortOrder)
		assertEquals(1, vSecond.sortOrder)
		assertEquals(50, vSecond.sharePercent)
		assertEquals("Banque", vSecond.institution)
		fAccounts.update(vSecond.copy(name = "Compte joint", sharePercent = 40))
		fAccounts.reorder(listOf(vSecond.id, vFirst.id))
		val vAll = fAccounts.observeAccounts().first()							// comptes relus
		assertEquals(listOf("Compte joint", "Courant"), vAll.map { it.name })
		assertEquals(40, vAll.first().sharePercent)
	}

	/*
		Aller-retour complet : une dépense, un virement et un ordre enregistrés
		sont relus à l'identique.
	*/
	@Test
	fun transactionsRoundTrip() = runTest {
		val vCourant = account("Courant", AccountType.CHECKING)					// compte courant
		val vPea     = account("PEA", AccountType.BROKERAGE)					// compte Bourse
		val vFood    = category("Alimentation")									// catégorie
		val vEtf     = (fMarket.createAsset("ETF", "fr0000000001") as SaveResult.Saved).id	// actif
		val vSpend   = Transaction("d1", LocalDate.parse("2026-10-02"), TransactionKind.STANDARD, "Courses", vFood,
			notes = "marché", postings = listOf(Posting("p1", vCourant.id, -4_250)))		// dépense
		val vBuy     = Transaction("o1", LocalDate.parse("2026-10-03"), TransactionKind.TRADE, "Achat ETF",
			postings = listOf(Posting("p2", vPea.id, -10_199)),
			trades   = listOf(Trade("t1", vEtf, TradeSide.BUY, 10_000, 1_000_000, 199)))		// achat d'une part à 100 €
		listOf(transferTx(vCourant, vPea, 20_000), vSpend, vBuy).forEach {
			assertTrue(fTransactions.save(it) is SaveResult.Saved)
		}
		val vRead = fTransactions.observeTransactions().first()					// opérations relues
		assertEquals(3, vRead.size)
		assertEquals(vSpend, vRead.single { it.id == "d1" })
		assertEquals(vBuy, vRead.single { it.id == "o1" })
		assertEquals("FR0000000001", fMarket.observeAssets().first().single().isin)
		assertEquals(20_000L - 10_199L, Ledger(vRead).balanceAt(vPea.id, LocalDate.parse("2026-10-31")))
	}

	/*
		Une opération invalide est refusée et n'atteint pas la base.
	*/
	@Test
	fun invalidTransactionIsRejected() = runTest {
		val vA     = account("A", AccountType.CHECKING)						// premier compte
		val vB     = account("B", AccountType.SAVINGS)							// second compte
		val vBad   = transferTx(vA, vB, 100).let { it.copy(postings = listOf(it.postings[0], it.postings[1].copy(amountCents = 90))) }	// déséquilibré
		val vSaved = fTransactions.save(vBad)									// résultat
		assertEquals(SaveResult.Rejected(listOf(ValidationError.UnbalancedTransfer)), vSaved)
		assertTrue(fTransactions.observeTransactions().first().isEmpty())
	}

	/*
		Revue du 2026-10-12 : un nouvel actif saisi avec un ordre refusé n'est
		pas créé ; supprimer l'achat dont dépend une vente est refusé (rien
		n'est supprimé) ; un cours inchangé n'est pas réécrit (identifiant
		conservé) ; des positions initiales verrouillent le type Bourse.
	*/
	@Test
	fun reviewFixesOnDataLayer() = runTest {
		val vPea   = account("PEA", AccountType.BROKERAGE)						// compte Bourse
		val vNew   = Asset("nouvel", "Nouvel ETF", null)							// actif saisi avec l'ordre
		val vSell  = Transaction("v0", LocalDate.parse("2026-10-03"), TransactionKind.TRADE, "Vente",
			postings = listOf(Posting("pv0", vPea.id, 10_000)),
			trades   = listOf(Trade("tv0", vNew.id, TradeSide.SELL, 10_000, 1_000_000, 0)))	// vente sans titre détenu
		assertTrue(fTransactions.save(vSell, vNew) is SaveResult.Rejected)
		assertTrue(fMarket.observeAssets().first().isEmpty())

		val vBuy  = Transaction("a1", LocalDate.parse("2026-10-02"), TransactionKind.TRADE, "Achat",
			postings = listOf(Posting("pa1", vPea.id, -10_000)),
			trades   = listOf(Trade("ta1", vNew.id, TradeSide.BUY, 10_000, 1_000_000, 0)))	// achat d'une part
		assertTrue(fTransactions.save(vBuy, vNew) is SaveResult.Saved)
		assertTrue(fTransactions.save(vSell) is SaveResult.Saved)
		val vRefusal = fTransactions.delete(listOf(vBuy.id))						// suppression refusée
		assertTrue(vRefusal!!.contains("Vente"))
		assertEquals(2, fTransactions.observeTransactions().first().size)
		assertEquals(null, fTransactions.delete(listOf(vBuy.id, vSell.id)))

		fMarket.putPrice(vNew.id, LocalDate.parse("2026-10-05"), 1_000_000)
		val vFirst = fMarket.observePrices().first().single()						// cours enregistré
		fMarket.putPrice(vNew.id, LocalDate.parse("2026-10-05"), 1_000_000)
		fMarket.putPrice(vNew.id, LocalDate.parse("2026-10-05"), 1_100_000)
		val vAfter = fMarket.observePrices().first().single()						// cours modifié
		assertEquals(vFirst.id, vAfter.id)
		assertEquals(1_100_000L, vAfter.unitPrice)

		val vPea2 = account("PEA 2", AccountType.BROKERAGE)						// second compte Bourse
		fTransactions.save(Transaction("o2", LocalDate.parse("2026-10-01"), TransactionKind.OPENING, "Solde initial",
			postings = listOf(Posting("po2", vPea2.id, 0)),
			trades   = listOf(Trade("to2", vNew.id, TradeSide.BUY, 10_000, 1_000_000, 0))))	// position initiale seule
		assertEquals(1, fAccounts.operationCount(vPea2.id))
		assertEquals(SaveResult.Rejected(listOf(SetupError.TypeLocked)), fAccounts.update(vPea2.copy(type = AccountType.CHECKING)))
	}

	/*
		Modifier une opération remplace ses jambes ; la date de création est conservée.
	*/
	@Test
	fun editingReplacesPostings() = runTest {
		val vA  = account("A", AccountType.CHECKING)							// premier compte
		val vB  = account("B", AccountType.SAVINGS)								// second compte
		val vC  = account("C", AccountType.SAVINGS)								// troisième compte
		val vTx = transferTx(vA, vB, 100)										// virement initial
		fTransactions.save(vTx)
		fTransactions.save(vTx.copy(postings = listOf(Posting("n1", vA.id, -300), Posting("n2", vC.id, 300))))
		val vRead = fTransactions.observeTransactions().first().single()		// opération relue
		assertEquals(setOf(vA.id, vC.id), vRead.postings.map { it.accountId }.toSet())
		assertEquals(300L, vRead.postings.single { it.accountId == vC.id }.amountCents)
	}

	/*
		Suppression d'un compte : refusée s'il a des opérations ; la base elle-
		même interdit d'orphéliner des jambes ; l'archivage le garde.
	*/
	@Test
	fun usedAccountCannotBeDeletedSimply() = runTest {
		val vA = account("A", AccountType.CHECKING)								// compte utilisé
		val vB = account("B", AccountType.SAVINGS)								// autre compte
		val vC = account("C", AccountType.SAVINGS)								// compte vide
		fTransactions.save(transferTx(vA, vB, 500))
		assertEquals(1, fAccounts.operationCount(vA.id))
		assertFalse(fAccounts.deleteIfUnused(vA.id))
		assertTrue(fAccounts.deleteIfUnused(vC.id))
		var vRefused = false													// refus de la base
		try
		{
			fDb.accountDao().deleteById(vA.id)
		}
		catch (inError: SQLiteConstraintException)
		{
			vRefused = true
		}
		assertTrue(vRefused)
		fAccounts.setArchived(vA.id, true)
		assertTrue(fAccounts.observeAccounts().first().single { it.id == vA.id }.archived)
	}

	/*
		Suppression définitive : le compte, ses relevés et ses opérations
		disparaissent ; un virement part en entier et l'autre compte s'en trouve
		modifié, comme annoncé dans la double confirmation.
	*/
	@Test
	fun permanentDeletionRemovesWholeTransfers() = runTest {
		val vA = account("A", AccountType.CHECKING)								// compte supprimé
		val vB = account("B", AccountType.SAVINGS)								// autre compte
		val vF = category("Divers")												// catégorie
		fTransactions.save(transferTx(vA, vB, 500))
		fTransactions.save(Transaction("b1", LocalDate.parse("2026-10-02"), TransactionKind.STANDARD, "Achat", vF,
			postings = listOf(Posting("bp", vB.id, -100))))
		fMarket.putStatement(vA.id, LocalDate.parse("2026-10-05"), 1_000)
		assertEquals(1, fAccounts.deletePermanently(vA.id))
		val vLeft = fTransactions.observeTransactions().first()				// opérations restantes
		assertEquals(listOf("b1"), vLeft.map { it.id })
		assertEquals(-100L, Ledger(vLeft).balanceAt(vB.id, LocalDate.parse("2026-10-31")))
		assertTrue(fMarket.observeStatements().first().isEmpty())
		assertEquals(listOf(vB.id), fAccounts.observeAccounts().first().map { it.id })
	}

	/*
		Suppression d'une catégorie utilisée : refusée sans remplacement ou vers
		une autre nature ; sinon ses opérations sont réaffectées.
	*/
	@Test
	fun categoryDeletionReassigns() = runTest {
		val vA      = account("A", AccountType.CHECKING)						// compte
		val vFood   = category("Alimentation")									// catégorie supprimée
		val vMisc   = category("Divers")										// remplacement
		val vSalary = category("Salaire", CategoryKind.INCOME)					// autre nature
		val vUnused = category("Inutile")										// catégorie libre
		fTransactions.save(Transaction("c1", LocalDate.parse("2026-10-02"), TransactionKind.STANDARD, "Courses", vFood,
			postings = listOf(Posting("cp", vA.id, -100))))
		assertEquals(SaveResult.Rejected(listOf(SetupError.CategoryInUse)), fCategories.delete(vFood, null))
		assertEquals(SaveResult.Rejected(listOf(SetupError.KindMismatch)), fCategories.delete(vFood, vSalary))
		assertTrue(fCategories.delete(vUnused, null) is SaveResult.Saved)
		assertTrue(fCategories.delete(vFood, vMisc) is SaveResult.Saved)
		assertEquals(vMisc, fTransactions.observeTransactions().first().single().categoryId)
		assertEquals(setOf("Divers", "Salaire"), fCategories.observeCategories().first().map { it.name }.toSet())
	}

	/*
		Cours : un seul par actif et par jour (le dernier saisi l'emporte).
	*/
	@Test
	fun onePricePerAssetAndDay() = runTest {
		val vEtf = (fMarket.createAsset("ETF") as SaveResult.Saved).id			// actif
		fMarket.putPrice(vEtf, LocalDate.parse("2026-10-01"), 1_000_000)
		fMarket.putPrice(vEtf, LocalDate.parse("2026-10-01"), 1_050_000)
		fMarket.putPrice(vEtf, LocalDate.parse("2026-10-02"), 1_100_000)
		assertEquals(listOf(1_050_000L, 1_100_000L), fMarket.observePrices().first().map { it.unitPrice })
	}

	/*
		Démonstration : le chargement remplit la base, l'effacement la vide.
	*/
	@Test
	fun demoLoadsAndClears() = runTest {
		val vDemo = DemoRepository(fDb, kClock)									// démonstration
		vDemo.loadDemo()
		assertEquals(9, fAccounts.observeAccounts().first().size)
		assertTrue(fTransactions.observeTransactions().first().size > 100)
		vDemo.clearAll()
		assertTrue(fAccounts.observeAccounts().first().isEmpty())
		assertTrue(fTransactions.observeTransactions().first().isEmpty())
	}

	/*
		Le schéma de la version 1 est exporté dans app/schemas (base des futures
		migrations).
	*/
	@Test
	fun schemaIsExported()
	{
		assertTrue(File("schemas/fr.maubin.patrimoineplus.data.db.PatrimoineDatabase/1.json").exists())
	}

	/*
		Solde initial : créé avec le compte, modifiable, supprimé à zéro ; il ne
		compte pas comme une opération et part avec une suppression simple.
	*/
	@Test
	fun openingBalanceLifecycle() = runTest {
		val vDay  = LocalDate.parse("2026-10-01")								// date du solde
		val vId   = (fAccounts.create("Courant", AccountType.CHECKING, null, 100, Opening(150_000, vDay)) as SaveResult.Saved).id	// compte
		assertEquals(Opening(150_000, vDay), fAccounts.openingOf(vId))
		assertEquals(0, fAccounts.operationCount(vId))
		fAccounts.setOpening(vId, Opening(-2_500, vDay.plusDays(2)))
		assertEquals(Opening(-2_500, vDay.plusDays(2)), fAccounts.openingOf(vId))
		assertEquals(1, fTransactions.observeTransactions().first().size)
		fAccounts.setOpening(vId, Opening(0, vDay))
		assertEquals(null, fAccounts.openingOf(vId))
		assertTrue(fTransactions.observeTransactions().first().isEmpty())
		fAccounts.setOpening(vId, Opening(1_000, vDay))
		assertTrue(fAccounts.deleteIfUnused(vId))
		assertTrue(fTransactions.observeTransactions().first().isEmpty())
		assertTrue(fAccounts.observeAccounts().first().isEmpty())
	}

	/*
		Le type d'un compte ne change plus dès qu'il a une opération (hors solde
		initial).
	*/
	@Test
	fun typeLockedOnceUsed() = runTest {
		val vA = account("A", AccountType.CHECKING)								// compte modifié
		val vB = account("B", AccountType.SAVINGS)								// autre compte
		assertTrue(fAccounts.update(vA.copy(type = AccountType.SAVINGS)) is SaveResult.Saved)
		fTransactions.save(transferTx(vA.copy(type = AccountType.SAVINGS), vB, 500))
		assertEquals(SaveResult.Rejected(listOf(SetupError.TypeLocked)), fAccounts.update(vA.copy(type = AccountType.CHECKING)))
		assertTrue(fAccounts.update(vA.copy(type = AccountType.SAVINGS, name = "Renommé")) is SaveResult.Saved)
	}

	/*
		Ordre des catégories : enregistré tel que choisi.
	*/
	@Test
	fun categoriesReorder() = runTest {
		val vIds = listOf("Logement", "Courses", "Loisirs").map { category(it) }	// catégories
		fCategories.reorder(vIds.reversed())
		assertEquals(listOf("Loisirs", "Courses", "Logement"), fCategories.observeCategories().first().sortedBy { it.sortOrder }.map { it.name })
	}

	/*
		Positions initiales : ajoutées au solde initial (créé sans espèces s'il
		n'existe pas), valorisées comme des achats, retirées une à une ; le solde
		initial disparaît quand il ne reste ni espèces ni position.
	*/
	@Test
	fun openingPositionsLifecycle() = runTest {
		val vPea = account("PEA", AccountType.BROKERAGE)						// compte Bourse
		val vEtf = (fMarket.createAsset("ETF") as SaveResult.Saved).id			// actif
		val vDay = LocalDate.parse("2026-10-01")								// date du solde initial
		fAccounts.addOpeningPosition(vPea.id, vEtf, 400_000, 850_000, vDay)
		fAccounts.addOpeningPosition(vPea.id, vEtf, 100_000, 900_000, vDay)
		val vPositions = fAccounts.openingPositions(vPea.id)					// positions initiales
		assertEquals(2, vPositions.size)
		assertEquals(Opening(0, vDay), fAccounts.openingOf(vPea.id))
		val vValue = valuePortfolio(Ledger(fTransactions.observeTransactions().first()), vPea.id, vDay, emptyList())	// valorisation
		assertEquals(430_000L, vValue.positions.single().costCents)				// 40 × 85 + 10 × 90 = 4 300 €
		fAccounts.removeOpeningPosition(vPea.id, vPositions.first().id)
		assertEquals(1, fAccounts.openingPositions(vPea.id).size)
		fAccounts.removeOpeningPosition(vPea.id, vPositions.last().id)
		assertTrue(fTransactions.observeTransactions().first().isEmpty())
	}

	/*
		Mise à jour groupée des cours et suppression d'un relevé.
	*/
	@Test
	fun groupedPricesAndStatementDeletion() = runTest {
		val vA   = (fMarket.createAsset("A") as SaveResult.Saved).id			// premier actif
		val vB   = (fMarket.createAsset("B") as SaveResult.Saved).id			// second actif
		val vDay = LocalDate.parse("2026-10-08")								// date des cours
		fMarket.putPrices(vDay, mapOf(vA to 1_000_000L, vB to 2_000_000L))
		fMarket.putPrices(vDay, mapOf(vA to 1_100_000L))
		assertEquals(mapOf(vA to 1_100_000L, vB to 2_000_000L), fMarket.observePrices().first().associate { it.assetId to it.unitPrice })
		val vLivret = account("Livret", AccountType.SAVINGS)					// compte relevé
		fMarket.putStatement(vLivret.id, vDay, 12_000)
		fMarket.deleteStatement(fMarket.observeStatements().first().single().id)
		assertTrue(fMarket.observeStatements().first().isEmpty())
	}

	/*
		Export puis import : la base revient exactement à l'état exporté, les
		données ajoutées entre-temps disparaissent ; un fichier refusé ne touche
		à rien.
	*/
	@Test
	fun backupThenImportRestoresEverything() = runTest {
		val vBackup = BackupRepository(fDb, kClock)								// sauvegarde
		DemoRepository(fDb, kClock).loadDemo()
		val vBytes   = java.io.ByteArrayOutputStream().also { writeBackupXlsx(vBackup.snapshot(), it) }.toByteArray()	// classeur
		val vBefore  = fTransactions.observeTransactions().first().sortedBy { it.id }	// opérations sauvegardées
		val vShape   = vBackup.snapshot().copy(exportedAt = "")				// contenu sauvegardé
		account("Ajouté après la sauvegarde", AccountType.CHECKING)
		assertTrue(decodeBackup("{}") is BackupRead.Invalid)
		assertEquals(10, fAccounts.observeAccounts().first().size)
		vBackup.replaceAll((readBackupXlsx(java.io.ByteArrayInputStream(vBytes)) as BackupRead.Valid).file)
		assertEquals(9, fAccounts.observeAccounts().first().size)
		assertEquals(vBefore, fTransactions.observeTransactions().first().sortedBy { it.id })
		assertEquals(sorted(vShape), sorted(vBackup.snapshot().copy(exportedAt = "")))
	}

	/*
		Contenu de sauvegarde trié par identifiant (l'ordre de lecture du
		classeur n'est pas celui de la base).
		inFile : contenu
	*/
	private fun sorted(inFile: BackupFile): BackupFile =
		inFile.copy(
			accounts     = inFile.accounts.sortedBy { it.id },
			categories   = inFile.categories.sortedBy { it.id },
			assets       = inFile.assets.sortedBy { it.id },
			transactions = inFile.transactions.sortedBy { it.id }.map { inTx -> inTx.copy(postings = inTx.postings.sortedBy { it.id }, trades = inTx.trades.sortedBy { it.id }) },
			statements   = inFile.statements.sortedBy { it.id },
			prices       = inFile.prices.sortedBy { it.id },
			recurrences  = inFile.recurrences.sortedBy { it.id },
			loans        = inFile.loans.sortedBy { it.id }
		)
}
