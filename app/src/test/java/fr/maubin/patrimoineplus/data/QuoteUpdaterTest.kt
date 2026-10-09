package fr.maubin.patrimoineplus.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.market.Chart
import fr.maubin.patrimoineplus.data.market.QuoteSource
import fr.maubin.patrimoineplus.data.market.QuoteUpdater
import fr.maubin.patrimoineplus.data.market.startDate
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
	Tests de la mise à jour automatique des cours, sur une base en mémoire et
	des réponses Yahoo Finance enregistrées.
*/
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class QuoteUpdaterTest
{
	private val kClock = Clock.fixed(Instant.parse("2026-10-09T15:00:00Z"), ZoneId.of("Europe/Paris"))	// jour des enregistrements
	private lateinit var fDb       : PatrimoineDatabase							// base en mémoire
	private lateinit var fAccounts : AccountRepository							// comptes
	private lateinit var fMarket   : MarketRepository							// actifs et cours

	/*
		Base vierge avec un PEA détenant trois actifs depuis le 15 janvier :
		CW8 (ISIN, cotée en euros), IWDA (ISIN, cotée en dollars dans la
		recherche) et un fonds sans ISIN.
	*/
	@Before
	fun open() = runTest {
		fDb       = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PatrimoineDatabase::class.java).allowMainThreadQueries().build()
		fAccounts = AccountRepository(fDb, kClock)
		fMarket   = MarketRepository(fDb, kClock)
		val vPea  = (fAccounts.create("PEA", AccountType.BROKERAGE, null, 100) as SaveResult.Saved).id	// compte Bourse
		listOf("ETF Monde" to "LU1681043599", "iShares World" to "IE00B4L5Y983", "Fonds maison" to null).forEach { (inName, inIsin) ->
			val vAsset = (fMarket.createAsset(inName, inIsin) as SaveResult.Saved).id	// actif
			fAccounts.addOpeningPosition(vPea, vAsset, 10_0000, 500_0000, LocalDate.of(2026, 1, 15))
		}
	}

	/*
		Ferme la base.
	*/
	@After
	fun close()
	{
		fDb.close()
	}

	/*
		Mise à jour : le symbole de CW8 est trouvé et enregistré, ses fins de
		mois et son dernier cours aussi ; IWDA (pas de correspondance directe
		en euros) et le fonds sans ISIN restent à compléter à la main.
	*/
	@Test
	fun resolvesAndStoresPrices() = runTest {
		val vUpdater = QuoteUpdater(FixtureQuoteSource(), fMarket, TransactionRepository(fDb, kClock), kClock)	// mise à jour
		val vOutcome = vUpdater.update()										// résultat
		assertNull(vOutcome.error)
		assertEquals(1, vOutcome.updated)
		assertEquals(setOf("iShares World", "Fonds maison"), vOutcome.unresolved.toSet())
		val vCw8    = fMarket.observeAssets().first().single { it.name == "ETF Monde" }	// actif résolu
		assertEquals("CW8.PA", vCw8.ticker)
		val vPrices = fMarket.observePrices().first().filter { it.assetId == vCw8.id }.map { it.date to it.unitPrice }	// cours enregistrés
		assertEquals(
			listOf(LocalDate.of(2026, 1, 30) to 6_111_150L, LocalDate.of(2026, 2, 27) to 6_184_261L, LocalDate.of(2026, 3, 31) to 5_892_282L, LocalDate.of(2026, 10, 9) to 7_157_000L),
			vPrices
		)
		assertNull(fMarket.observeAssets().first().single { it.name == "iShares World" }.ticker)
		vUpdater.update()
		assertEquals(4, fMarket.observePrices().first().count { it.assetId == vCw8.id })
	}

	/*
		Service injoignable : l'erreur est rendue, rien n'est cassé, aucun cours
		n'est enregistré.
	*/
	@Test
	fun offlineIsReported() = runTest {
		val vOffline = object : QuoteSource
		{
			override suspend fun search(inIsin: String): List<String> = throw IOException("pas de réseau")
			override suspend fun chart(inSymbol: String, inFrom: LocalDate): Chart? = throw IOException("pas de réseau")
		}																		// source injoignable
		val vOutcome = QuoteUpdater(vOffline, fMarket, TransactionRepository(fDb, kClock), kClock).update()	// résultat
		assertNotNull(vOutcome.error)
		assertEquals(0, fMarket.observePrices().first().size)
	}

	/*
		Premier jour à demander : début du premier mois sans cours depuis le
		premier ordre ; sinon le jour du dernier cours.
	*/
	@Test
	fun startDateFillsGaps()
	{
		val vToday = LocalDate.of(2026, 10, 9)									// date du jour
		val vFirst = LocalDate.of(2026, 7, 10)									// premier ordre
		/*
			Cours d'un jour.
			inDate : jour
		*/
		fun price(inDate: String) = AssetPrice("p$inDate", "a", LocalDate.parse(inDate), 1L)
		assertEquals(LocalDate.of(2026, 7, 1), startDate(vFirst, emptyList(), vToday))
		assertEquals(LocalDate.of(2026, 8, 1), startDate(vFirst, listOf(price("2026-07-31"), price("2026-09-30")), vToday))
		assertEquals(LocalDate.of(2026, 10, 2), startDate(vFirst, listOf(price("2026-07-31"), price("2026-08-31"), price("2026-09-30"), price("2026-10-02")), vToday))
	}
}
