package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.market.Chart
import fr.maubin.patrimoineplus.data.market.QuoteSource
import fr.maubin.patrimoineplus.data.market.findEuroListing
import fr.maubin.patrimoineplus.data.market.parseChart
import fr.maubin.patrimoineplus.data.market.parseSearch
import fr.maubin.patrimoineplus.data.market.pricesToStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/*
	Source de cours imitée à partir de réponses réelles de Yahoo Finance
	enregistrées le 9 octobre 2026 (test/resources/yahoo).
*/
class FixtureQuoteSource : QuoteSource
{
	val requested = mutableListOf<String>()										// symboles demandés, dans l'ordre

	/*
		Réponse enregistrée.
		inName : nom du fichier, sans extension
	*/
	private fun fixture(inName: String): String? = javaClass.getResource("/yahoo/$inName.json")?.readText()

	/*
		Recherche par ISIN : CW8 et IWDA connus, rien sinon.
		inIsin : code ISIN
	*/
	override suspend fun search(inIsin: String): List<String> =
		when (inIsin)
		{
			"LU1681043599" -> parseSearch(fixture("search-cw8")!!)
			"IE00B4L5Y983" -> parseSearch(fixture("search-iwda")!!)
			else           -> parseSearch(fixture("search-none")!!)
		}

	/*
		Historique : CW8.PA (euros), IWDA.L (dollars), IWDA.AS (euros), inconnu sinon.
		inSymbol : symbole
		inFrom   : premier jour voulu (ignoré : la réponse est figée)
	*/
	override suspend fun chart(
		inSymbol : String,
		inFrom   : LocalDate
	): Chart?
	{
		requested += inSymbol
		return when (inSymbol)
		{
			"CW8.PA"  -> parseChart(fixture("chart-cw8")!!)
			"IWDA.L"  -> parseChart(fixture("chart-iwda-usd")!!)
			"IWDA.AS" -> parseChart(fixture("chart-iwda-eur")!!)
			else      -> parseChart(fixture("chart-unknown")!!)
		}
	}
}

/*
	Tests des cours automatiques : lecture des réponses, choix de la cotation
	en euros, cours de fin de mois à enregistrer.
*/
class QuotesTest
{
	private val kToday = LocalDate.of(2026, 10, 9)								// jour des enregistrements

	/*
		Lecture : symboles d'une recherche, historique (devise, nom, place,
		dernier cours, séances), symbole inconnu.
	*/
	@Test
	fun parsesResponses()
	{
		val vSource = FixtureQuoteSource()										// réponses enregistrées
		val vChart  = parseChart(javaClass.getResource("/yahoo/chart-cw8.json")!!.readText())!!	// historique de CW8
		assertEquals(listOf("CW8.PA"), parseSearch(javaClass.getResource("/yahoo/search-cw8.json")!!.readText()))
		assertEquals("EUR", vChart.currency)
		assertEquals("Paris", vChart.exchange)
		assertTrue(vChart.name!!.contains("MSCI World"))
		assertEquals(715.7, vChart.lastPrice, 0.0)
		assertEquals(kToday, vChart.lastDate)
		assertEquals(63, vChart.closes.size)
		assertEquals(LocalDate.of(2026, 1, 2), vChart.closes.first().first)
		assertNull(parseChart(javaClass.getResource("/yahoo/chart-unknown.json")!!.readText()))
		assertEquals("USD", parseChart(javaClass.getResource("/yahoo/chart-iwda-usd.json")!!.readText())!!.currency)
		assertTrue(vSource.requested.isEmpty())
	}

	/*
		Choix de la cotation : CW8 trouvée directement en euros ; IWDA n'a
		qu'une cotation en dollars dans la recherche, donc rien sans essai,
		et IWDA.AS (Amsterdam, euros) avec essai, marquée « à confirmer » ;
		ISIN inconnu : rien.
	*/
	@Test
	fun findsEuroListing() = runTest {
		val vCw8 = findEuroListing(FixtureQuoteSource(), "lu1681043599 ", inGuess = false, inToday = kToday)	// CW8, saisie négligée
		assertEquals("CW8.PA", vCw8?.symbol)
		assertEquals(false, vCw8?.guessed)
		assertNull(findEuroListing(FixtureQuoteSource(), "IE00B4L5Y983", inGuess = false, inToday = kToday))
		val vSource = FixtureQuoteSource()										// suivi des requêtes
		val vIwda   = findEuroListing(vSource, "IE00B4L5Y983", inGuess = true, inToday = kToday)	// IWDA avec essai
		assertEquals("IWDA.AS", vIwda?.symbol)
		assertEquals(true, vIwda?.guessed)
		assertEquals("Amsterdam", vIwda?.exchange)
		assertEquals(listOf("IWDA.L", "IWDA.PA", "IWDA.AS"), vSource.requested)
		assertNull(findEuroListing(FixtureQuoteSource(), "XX0000000000", inGuess = true, inToday = kToday))
	}

	/*
		Cours à enregistrer : dernière séance de chaque mois passé, puis le
		dernier cours au jour de sa cotation, en dix-millièmes ; rien avant la
		date de départ.
	*/
	@Test
	fun storesMonthEndsAndLastPrice()
	{
		val vChart = parseChart(javaClass.getResource("/yahoo/chart-cw8.json")!!.readText())!!	// janvier à mars 2026, puis le 9 octobre
		assertEquals(
			listOf(
				LocalDate.of(2026, 1, 30) to 6_111_150L,
				LocalDate.of(2026, 2, 27) to 6_184_261L,
				LocalDate.of(2026, 3, 31) to 5_892_282L,
				kToday to 7_157_000L
			),
			pricesToStore(vChart, LocalDate.of(2026, 1, 1))
		)
		assertEquals(listOf(LocalDate.of(2026, 3, 31), kToday), pricesToStore(vChart, LocalDate.of(2026, 3, 1)).map { it.first })
	}
}
