package fr.maubin.patrimoineplus.desktop

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.data.FixtureQuoteSource
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.backupFileOf
import fr.maubin.patrimoineplus.data.market.Chart
import fr.maubin.patrimoineplus.data.market.QuoteSource
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/*
	Cours automatiques du PC (réponses enregistrées, sans réseau) : à
	l'ouverture d'un dossier synchronisé, cours récupérés et rangés dans le
	fichier d'échange, instant mémorisé ; pas de nouvelle demande avant
	quatre heures ; « Actualiser les cours » à la main avec son résultat ;
	rien en démonstration.
*/
class DesktopQuotesTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// réglages et dossier jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-09T15:00:00Z"), ZoneId.of("Europe/Paris"))	// jour des enregistrements
	private val fScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)			// portée des tâches de fond
	private val fCalls = AtomicInteger()												// demandes d'historique

	/*
		Arrête les tâches de fond.
	*/
	@After
	fun stop()
	{
		fScope.cancel()
	}

	/*
		Source enregistrée qui compte les demandes d'historique.
	*/
	private fun source(): QuoteSource
	{
		val vFixture = FixtureQuoteSource()												// réponses enregistrées
		return object : QuoteSource
		{
			override suspend fun search(inIsin: String): List<String> = vFixture.search(inIsin)
			override suspend fun chart(inSymbol: String, inFrom: LocalDate): Chart? = vFixture.chart(inSymbol, inFrom).also { fCalls.incrementAndGet() }
		}
	}

	/*
		Dossier synchronisé dont le fichier d'échange a un PEA : un ETF coté en
		euros, un ETF sans correspondance directe, un fonds sans ISIN.
	*/
	private fun folder(): java.nio.file.Path
	{
		val vPea    = Account("pea", "PEA", AccountType.BROKERAGE)							// compte Bourse
		val vAssets = listOf(Asset("cw8", "ETF Monde", "LU1681043599"), Asset("iwda", "iShares World", "IE00B4L5Y983"), Asset("maison", "Fonds maison"))	// actifs
		val vTxs    = vAssets.map { inAsset ->
			Transaction("t-${inAsset.id}", LocalDate.of(2026, 1, 15), TransactionKind.OPENING, "Position initiale",
				postings = listOf(Posting("p-${inAsset.id}", vPea.id, 5_000_00)), trades = listOf(Trade("o-${inAsset.id}", inAsset.id, TradeSide.BUY, 10_0000, 500_0000)))
		}																				// positions initiales
		val vFolder = fTemp.newFolder("Synchro").toPath()									// dossier synchronisé
		Files.newOutputStream(vFolder.resolve(kSyncFileName)).use { writeBackupXlsx(backupFileOf(DataSet(listOf(vPea), emptyList(), vAssets, vTxs, emptyList(), emptyList())), it) }
		return vFolder
	}

	/*
		Contrôleur avec la source de cours enregistrée.
	*/
	private fun controller(): DesktopController =
		DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), fScope, kClock, null, source())

	/*
		Attend une condition (tâches de fond).
		inCondition : condition attendue
	*/
	private fun waitFor(inCondition: () -> Boolean)
	{
		val vEnd = System.currentTimeMillis() + 10_000										// délai maximal
		while (!inCondition())
		{
			assertTrue("délai dépassé", System.currentTimeMillis() < vEnd)
			Thread.sleep(50)
		}
	}

	/*
		Ouverture du dossier : cours rangés, instant mémorisé ; pas de nouvelle
		demande avant quatre heures ; à la main, résultat affiché.
	*/
	@Test
	fun quotesOnOpenAndOnDemand()
	{
		val vApp = controller()																// lancement
		assertFalse(vApp.canQuote)															// aucune source ouverte
		vApp.openFolder(folder())
		assertTrue(vApp.canQuote)
		waitFor { vApp.workspace.value.data.prices.count { it.assetId == "cw8" } == 4 && !vApp.quoting.value }
		assertEquals("CW8.PA", vApp.workspace.value.data.assets.single { it.id == "cw8" }.ticker)
		waitFor { vApp.settings.value.quotesAt == kClock.millis() }
		val vCalls = fCalls.get()															// demandes faites
		vApp.refreshQuotesIfStale()
		assertEquals(vCalls, fCalls.get())													// moins de quatre heures : rien de demandé
		assertTrue(vApp.flush())
		vApp.refreshQuotes()
		waitFor { vApp.notice.value != null }
		assertEquals("Cours mis à jour (1 titre(s)). Sans cotation automatique : Fonds maison, iShares World.", vApp.notice.value)
		assertEquals(4, vApp.workspace.value.data.prices.count { it.assetId == "cw8" })	// cours du jour remplacé, pas doublé
		assertEquals(kClock.millis(), controller().settings.value.quotesAt)					// mémorisé pour le lancement suivant
	}

	/*
		Démonstration : pas de cours automatiques.
	*/
	@Test
	fun noQuotesInDemo()
	{
		val vApp = controller()																// lancement
		vApp.openDemo()
		assertFalse(vApp.canQuote)
		vApp.refreshQuotes()
		Thread.sleep(300)
		assertEquals(0, fCalls.get())
	}
}
