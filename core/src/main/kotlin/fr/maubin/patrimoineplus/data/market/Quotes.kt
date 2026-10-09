package fr.maubin.patrimoineplus.data.market

import fr.maubin.patrimoineplus.domain.calc.kPriceScale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Places de la zone euro essayées quand la recherche par ISIN ne trouve pas de cotation en euros.
val kEuroSuffixes = listOf(".PA", ".AS", ".DE", ".MI", ".BR", ".MC", ".LS", ".VI", ".HE", ".IR")

private val kSearchTypes = setOf("ETF", "EQUITY", "MUTUALFUND")				// types de titres retenus

/*
	Historique d'une cotation.
	symbol    : symbole (CW8.PA)
	currency  : devise de cotation (EUR, USD…)
	name      : nom long du titre, s'il est connu
	exchange  : place de cotation, telle qu'affichée
	lastPrice : dernier cours
	lastDate  : jour du dernier cours, à l'heure de la place
	closes    : clôtures quotidiennes connues (jour, cours), dans l'ordre
*/
data class Chart(
	val symbol    : String,
	val currency  : String,
	val name      : String?,
	val exchange  : String?,
	val lastPrice : Double,
	val lastDate  : LocalDate,
	val closes    : List<Pair<LocalDate, Double>>
)

/*
	Cotation trouvée pour un actif.
	symbol   : symbole retenu
	name     : nom long du titre
	exchange : place de cotation
	price    : dernier cours, en euros
	guessed  : trouvée en essayant d'autres places (à confirmer par l'utilisateur)
*/
data class Listing(
	val symbol   : String,
	val name     : String?,
	val exchange : String?,
	val price    : Double,
	val guessed  : Boolean
)

/*
	Source de cours (Yahoo Finance en vrai, une imitation dans les tests).
*/
interface QuoteSource
{
	/*
		Symboles trouvés pour un ISIN, dans l'ordre de pertinence.
		inIsin : code ISIN
	*/
	suspend fun search(inIsin: String): List<String>

	/*
		Historique d'une cotation depuis une date ; null si le symbole est inconnu.
		inSymbol : symbole
		inFrom   : premier jour voulu
	*/
	suspend fun chart(
		inSymbol : String,
		inFrom   : LocalDate
	): Chart?
}

/*
	Lit la réponse de recherche : symboles des titres (ETF, actions, fonds).
	inJson : réponse JSON
*/
fun parseSearch(inJson: String): List<String> =
	(Json.parseToJsonElement(inJson).jsonObject["quotes"] as? JsonArray).orEmpty()
		.map { it.jsonObject }
		.filter { it["quoteType"]?.jsonPrimitive?.content in kSearchTypes }
		.mapNotNull { it["symbol"]?.jsonPrimitive?.content }

/*
	Lit la réponse d'historique ; null si le symbole est inconnu ou la réponse
	incomplète. Les jours sans clôture sont ignorés.
	inJson : réponse JSON
*/
fun parseChart(inJson: String): Chart?
{
	val vChart  = Json.parseToJsonElement(inJson).jsonObject["chart"]?.jsonObject ?: return null	// enveloppe
	val vResult = (vChart["result"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null		// premier résultat
	val vMeta   = vResult["meta"]?.jsonObject ?: return null							// description de la cotation
	val vZone   = runCatching { ZoneId.of(vMeta.text("exchangeTimezoneName")) }.getOrDefault(ZoneId.of("Europe/Paris"))	// fuseau de la place
	val vTimes  = (vResult["timestamp"] as? JsonArray).orEmpty().map { it.jsonPrimitive.longOrNull }	// instants des séances
	val vCloses = (vResult["indicators"]?.jsonObject?.get("quote") as? JsonArray)?.firstOrNull()?.jsonObject?.get("close") as? JsonArray	// clôtures
	val vPoints = vTimes.indices.mapNotNull { inIndex ->
		val vTime  = vTimes[inIndex] ?: return@mapNotNull null							// instant de la séance
		val vClose = vCloses?.getOrNull(inIndex)?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null	// clôture
		Instant.ofEpochSecond(vTime).atZone(vZone).toLocalDate() to vClose
	}																					// séances connues
	val vPrice  = vMeta["regularMarketPrice"]?.jsonPrimitive?.doubleOrNull ?: vPoints.lastOrNull()?.second ?: return null	// dernier cours
	val vTime   = vMeta["regularMarketTime"]?.jsonPrimitive?.longOrNull					// instant du dernier cours
	return Chart(
		symbol    = vMeta.text("symbol") ?: return null,
		currency  = vMeta.text("currency") ?: return null,
		name      = vMeta.text("longName") ?: vMeta.text("shortName"),
		exchange  = vMeta.text("fullExchangeName") ?: vMeta.text("exchangeName"),
		lastPrice = vPrice,
		lastDate  = vTime?.let { Instant.ofEpochSecond(it).atZone(vZone).toLocalDate() } ?: vPoints.lastOrNull()?.first ?: return null,
		closes    = vPoints
	)
}

/*
	Texte d'un champ ; null s'il manque.
	inKey : nom du champ
*/
private fun JsonObject.text(inKey: String): String? = this[inKey]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

/*
	Cherche la cotation en euros d'un ISIN : d'abord les résultats de la
	recherche, puis, si aucun n'est en euros et si inGuess est vrai, le même
	symbole sur les places de la zone euro. Rend null si rien n'est trouvé.
	inSource : source de cours
	inIsin   : code ISIN
	inGuess  : autorise l'essai d'autres places (confirmation par l'utilisateur)
	inToday  : date du jour
*/
suspend fun findEuroListing(
	inSource : QuoteSource,
	inIsin   : String,
	inGuess  : Boolean,
	inToday  : LocalDate
): Listing?
{
	val vFound = inSource.search(inIsin.trim().uppercase())						// symboles trouvés
	vFound.forEach { inSymbol ->
		inSource.chart(inSymbol, inToday.minusDays(7))?.takeIf { it.currency == "EUR" }?.let { return it.toListing(false) }
	}
	if (!inGuess)
	{
		return null
	}
	vFound.map { it.substringBefore('.') }.distinct().forEach { inBase ->
		kEuroSuffixes.map { inBase + it }.filter { it !in vFound }.forEach { inSymbol ->
			inSource.chart(inSymbol, inToday.minusDays(7))?.takeIf { it.currency == "EUR" }?.let { return it.toListing(true) }
		}
	}
	return null
}

/*
	Cotation correspondant à un historique.
	inGuessed : trouvée par essai d'autres places
*/
private fun Chart.toListing(inGuessed: Boolean): Listing = Listing(symbol, name, exchange, lastPrice, inGuessed)

/*
	Cours à enregistrer à partir d'un historique : la dernière clôture de
	chaque mois passé (fin de mois), puis le dernier cours au jour de sa
	cotation ; en dix-millièmes d'euro. Les jours antérieurs à inFrom sont
	ignorés.
	inChart : historique
	inFrom  : premier jour à enregistrer
*/
fun pricesToStore(
	inChart : Chart,
	inFrom  : LocalDate
): List<Pair<LocalDate, Long>>
{
	val vCurrent = YearMonth.from(inChart.lastDate)								// mois du dernier cours
	val vMonthEnds = inChart.closes
		.filter { !it.first.isBefore(inFrom) && YearMonth.from(it.first).isBefore(vCurrent) }
		.groupBy { YearMonth.from(it.first) }
		.map { (_, inDays) -> inDays.maxBy { it.first } }						// dernière séance de chaque mois passé
	return (vMonthEnds + (inChart.lastDate to inChart.lastPrice))
		.filter { !it.first.isBefore(inFrom) }
		.map { (inDate, inPrice) -> inDate to toPriceUnits(inPrice) }
}

/*
	Cours en dix-millièmes d'euro, arrondi.
	inEuros : cours en euros
*/
fun toPriceUnits(inEuros: Double): Long =
	BigDecimal(inEuros.toString()).multiply(BigDecimal(kPriceScale)).setScale(0, RoundingMode.HALF_UP).longValueExact()
