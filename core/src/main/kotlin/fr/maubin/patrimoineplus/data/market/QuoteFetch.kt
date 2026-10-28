package fr.maubin.patrimoineplus.data.market

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.PriceDto
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

const val kQuotesStaleMillis = 4L * 60 * 60 * 1000							// âge au-delà duquel les cours sont redemandés (4 h)

/*
	Résultat d'une mise à jour des cours.
	updated    : actifs dont le cours a été mis à jour
	unresolved : noms des actifs sans cotation automatique (ISIN manquant ou introuvable)
	error      : message si le service n'a pas pu être joint ; null sinon
*/
data class QuoteOutcome(
	val updated    : Int,
	val unresolved : List<String>,
	val error      : String?
)

/*
	Cours récupérés pour des données, à ranger ensuite (voir withQuotes).
	tickers : symboles trouvés à partir de l'ISIN, par actif
	series  : cours à enregistrer (jour, cours en dix-millièmes), par actif
	outcome : résultat
*/
data class QuoteFetch(
	val tickers : Map<String, String>,
	val series  : Map<String, List<Pair<LocalDate, Long>>>,
	val outcome : QuoteOutcome
)

/*
	Récupère les cours de tous les actifs qui ont au moins un ordre (comme le
	téléphone) : symbole trouvé à partir de l'ISIN s'il manque (correspondance
	directe en euros seulement), puis cours de fin de mois manquants depuis le
	premier achat et dernier cours. Les données ne sont pas modifiées.
	inSource : source de cours
	inData   : données
	inToday  : date du jour
*/
suspend fun fetchQuotes(
	inSource : QuoteSource,
	inData   : DataSet,
	inToday  : LocalDate
): QuoteFetch
{
	val vFirst   = inData.transactions.flatMap { inTx -> inTx.trades.map { it.assetId to inTx.date } }
		.groupBy({ it.first }, { it.second })
		.mapValues { (_, inDates) -> inDates.min() }								// premier ordre de chaque actif
	val vPrices  = inData.prices.groupBy { it.assetId }							// cours déjà enregistrés
	val vAssets  = inData.assets.filter { !it.archived && it.id in vFirst }		// actifs à suivre
	val vTickers = mutableMapOf<String, String>()									// symboles trouvés
	val vSeries  = mutableMapOf<String, List<Pair<LocalDate, Long>>>()			// cours à enregistrer
	val vMissing = mutableListOf<String>()											// actifs sans cotation
	return try
	{
		vAssets.forEach { inAsset ->
			val vTicker = inAsset.ticker ?: inAsset.isin?.let { inIsin -> findEuroListing(inSource, inIsin, inGuess = false, inToday = inToday)?.symbol?.also { vTickers[inAsset.id] = it } }	// symbole de cotation
			if (vTicker == null)
			{
				vMissing += inAsset.name
				return@forEach
			}
			val vFrom  = startDate(vFirst.getValue(inAsset.id), vPrices[inAsset.id].orEmpty(), inToday)	// premier jour à demander
			val vChart = inSource.chart(vTicker, vFrom.minusDays(7))				// historique
			if (vChart == null || vChart.currency != "EUR")
			{
				vMissing += inAsset.name
				return@forEach
			}
			vSeries[inAsset.id] = pricesToStore(vChart, vFrom)
		}
		QuoteFetch(vTickers, vSeries, QuoteOutcome(vSeries.size, vMissing, null))
	}
	catch (inError: Exception)
	{
		QuoteFetch(vTickers, vSeries, QuoteOutcome(vSeries.size, vMissing, inError.message ?: "service de cours injoignable"))
	}
}

/*
	Fichier complété des cours récupérés : symboles trouvés notés sur les
	actifs, cours ajoutés (un cours déjà présent le même jour est remplacé).
	inFetch : cours récupérés
	inNow   : instant de la modification (ms)
*/
fun BackupFile.withQuotes(
	inFetch : QuoteFetch,
	inNow   : Long
): BackupFile
{
	val vKnown = assets.map { it.id }.toSet()										// actifs encore présents (supprimés pendant la récupération : ignorés)
	val vNew = inFetch.series.filterKeys { it in vKnown }.flatMap { (inAsset, inSeries) -> inSeries.map { (inDate, inPrice) -> PriceDto(UUID.randomUUID().toString(), inAsset, inDate.toString(), inPrice) } }	// cours ajoutés
	val vDays = vNew.map { it.assetId to it.date }.toSet()							// jours remplacés
	return copy(
		assets = assets.map { inAsset -> inFetch.tickers[inAsset.id]?.let { inAsset.copy(ticker = it, updatedAt = inNow) } ?: inAsset },
		prices = prices.filter { (it.assetId to it.date) !in vDays } + vNew
	)
}

/*
	Premier jour à demander pour un actif : le début du premier mois, depuis
	son premier ordre, auquel aucun cours n'est enregistré (historique
	incomplet), sinon le jour de son dernier cours.
	inFirstTrade : date du premier ordre
	inPrices     : cours enregistrés de l'actif
	inToday      : date du jour
*/
fun startDate(
	inFirstTrade : LocalDate,
	inPrices     : List<AssetPrice>,
	inToday      : LocalDate
): LocalDate
{
	val vMonths  = inPrices.map { YearMonth.from(it.date) }.toSet()				// mois couverts
	val vMissing = generateSequence(YearMonth.from(inFirstTrade)) { it.plusMonths(1) }
		.takeWhile { it.isBefore(YearMonth.from(inToday)) }
		.firstOrNull { it !in vMonths }											// premier mois sans cours
	return vMissing?.atDay(1) ?: inPrices.maxOfOrNull { it.date } ?: inFirstTrade
}
