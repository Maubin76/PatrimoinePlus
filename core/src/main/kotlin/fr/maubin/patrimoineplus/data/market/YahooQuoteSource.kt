package fr.maubin.patrimoineplus.data.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset

private const val kHost      = "https://query1.finance.yahoo.com"				// serveur des cours
private const val kAgent     = "Mozilla/5.0"									// agent accepté par le serveur
private const val kTimeoutMs = 15_000											// délai maximal d'une requête

/*
	Cours publics de Yahoo Finance (API non officielle, sans clé). Seuls des
	ISIN et des symboles sont envoyés : aucune quantité, aucun montant.
*/
class YahooQuoteSource : QuoteSource
{
	/*
		Symboles trouvés pour un ISIN.
		inIsin : code ISIN
	*/
	override suspend fun search(inIsin: String): List<String> =
		get("$kHost/v1/finance/search?q=${URLEncoder.encode(inIsin, "UTF-8")}&quotesCount=10&newsCount=0")?.let(::parseSearch).orEmpty()

	/*
		Clôtures quotidiennes d'un symbole depuis une date, et dernier cours.
		inSymbol : symbole
		inFrom   : premier jour voulu
	*/
	override suspend fun chart(
		inSymbol : String,
		inFrom   : LocalDate
	): Chart?
	{
		val vFrom = inFrom.atStartOfDay().toEpochSecond(ZoneOffset.UTC)			// début, en secondes
		val vTo   = System.currentTimeMillis() / 1000 + 86_400						// fin : demain
		return get("$kHost/v8/finance/chart/${URLEncoder.encode(inSymbol, "UTF-8")}?period1=$vFrom&period2=$vTo&interval=1d")?.let(::parseChart)
	}

	/*
		Requête GET ; null si la ressource n'existe pas (404). Lève IOException
		si le service est injoignable ou refuse la requête.
		inUrl : adresse
	*/
	private suspend fun get(inUrl: String): String? =
		withContext(Dispatchers.IO) {
			val vConnection = URL(inUrl).openConnection() as HttpURLConnection	// connexion
			try
			{
				vConnection.connectTimeout = kTimeoutMs
				vConnection.readTimeout    = kTimeoutMs
				vConnection.setRequestProperty("User-Agent", kAgent)
				vConnection.setRequestProperty("Accept", "application/json")
				when (val vCode = vConnection.responseCode)
				{
					HttpURLConnection.HTTP_OK        -> vConnection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
					HttpURLConnection.HTTP_NOT_FOUND -> null
					else                             -> throw IOException("réponse $vCode du service de cours")
				}
			}
			finally
			{
				vConnection.disconnect()
			}
		}
}
