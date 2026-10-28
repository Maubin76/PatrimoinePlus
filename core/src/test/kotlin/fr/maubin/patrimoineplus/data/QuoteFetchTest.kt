package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.PriceDto
import fr.maubin.patrimoineplus.data.backup.validateBackup
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.market.Chart
import fr.maubin.patrimoineplus.data.market.QuoteSource
import fr.maubin.patrimoineplus.data.market.fetchQuotes
import fr.maubin.patrimoineplus.data.market.withQuotes
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/*
	Cours automatiques sur les données du fichier d'échange (PC) : mêmes
	règles que le téléphone (symbole trouvé par l'ISIN en euros, fins de mois
	depuis le premier ordre, dernier cours), rangés dans le fichier (cours du
	même jour remplacé, actif supprimé entre-temps ignoré) ; service
	injoignable signalé sans rien changer.
*/
class QuoteFetchTest
{
	private val kToday = LocalDate.of(2026, 10, 9)									// jour des enregistrements
	private val kPea   = Account("pea", "PEA", AccountType.BROKERAGE)				// compte Bourse

	/*
		PEA détenant depuis le 15 janvier un ETF coté en euros, un ETF sans
		correspondance directe en euros et un fonds sans ISIN.
	*/
	private fun data(): DataSet
	{
		val vAssets = listOf(Asset("cw8", "ETF Monde", "LU1681043599"), Asset("iwda", "iShares World", "IE00B4L5Y983"), Asset("maison", "Fonds maison"))	// actifs
		val vTrades = vAssets.map { inAsset ->
			Transaction("t-${inAsset.id}", LocalDate.of(2026, 1, 15), TransactionKind.OPENING, "Position initiale",
				postings = listOf(Posting("p-${inAsset.id}", kPea.id, 5_000_00)), trades = listOf(Trade("o-${inAsset.id}", inAsset.id, TradeSide.BUY, 10_0000, 500_0000)))
		}																			// positions initiales
		return DataSet(listOf(kPea), emptyList(), vAssets, vTrades, emptyList(), emptyList())
	}

	/*
		Récupération puis rangement dans le fichier : symbole noté, fins de mois
		et dernier cours ; un cours saisi le même jour est remplacé.
	*/
	@Test
	fun fetchesAndStores() = runBlocking {
		val vFetch = fetchQuotes(FixtureQuoteSource(), data(), kToday)					// cours récupérés
		assertNull(vFetch.outcome.error)
		assertEquals(1, vFetch.outcome.updated)
		assertEquals(setOf("iShares World", "Fonds maison"), vFetch.outcome.unresolved.toSet())
		assertEquals(mapOf("cw8" to "CW8.PA"), vFetch.tickers)
		val vFile  = backupFileOf(data()).let { it.copy(prices = it.prices + PriceDto("manuel", "cw8", "2026-10-09", 7_000_000)) }	// cours saisi à la main le même jour
		val vNew   = vFile.withQuotes(vFetch, 42L)											// fichier complété
		assertEquals("CW8.PA", vNew.assets.single { it.id == "cw8" }.ticker)
		assertEquals(42L, vNew.assets.single { it.id == "cw8" }.updatedAt)
		assertEquals(
			listOf("2026-01-30" to 6_111_150L, "2026-02-27" to 6_184_261L, "2026-03-31" to 5_892_282L, "2026-10-09" to 7_157_000L),
			vNew.prices.filter { it.assetId == "cw8" }.sortedBy { it.date }.map { it.date to it.unitPrice }
		)
		assertTrue(validateBackup(vNew) is BackupRead.Valid)
		val vGone  = vFile.copy(assets = vFile.assets.filter { it.id != "cw8" }, transactions = vFile.transactions.filter { it.id != "t-cw8" }, prices = emptyList())	// actif supprimé entre-temps
		assertTrue(vGone.withQuotes(vFetch, 42L).prices.isEmpty())
	}

	/*
		Service injoignable : erreur rendue, rien à ranger.
	*/
	@Test
	fun offline() = runBlocking {
		val vOffline = object : QuoteSource
		{
			override suspend fun search(inIsin: String): List<String> = throw IOException("pas de réseau")
			override suspend fun chart(inSymbol: String, inFrom: LocalDate): Chart? = throw IOException("pas de réseau")
		}																				// source injoignable
		val vFetch = fetchQuotes(vOffline, data(), kToday)								// résultat
		assertNotNull(vFetch.outcome.error)
		assertTrue(vFetch.series.isEmpty())
	}
}
