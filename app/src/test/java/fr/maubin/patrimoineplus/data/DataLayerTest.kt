package fr.maubin.patrimoineplus.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.demo.DemoRepository
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
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
		assertEquals(1, fAccounts.usage(vA.id))
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
		assertEquals(5, fAccounts.observeAccounts().first().size)
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
}
