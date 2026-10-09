package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.market.Chart
import fr.maubin.patrimoineplus.data.market.QuoteSource
import fr.maubin.patrimoineplus.data.market.parseChart
import fr.maubin.patrimoineplus.data.market.parseSearch
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
