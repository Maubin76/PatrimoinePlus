package fr.maubin.patrimoineplus.data.market

import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

private const val kStaleMillis = 4L * 60 * 60 * 1000							// âge au-delà duquel les cours sont redemandés (4 h)

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
	Mise à jour automatique des cours depuis la source publique : pour chaque
	actif ayant au moins un ordre, trouve son symbole à partir de l'ISIN s'il
	manque (correspondance directe en euros seulement), puis enregistre les
	cours de fin de mois manquants depuis le premier achat et le dernier cours.
	inSource       : source de cours
	inMarket       : actifs et cours
	inTransactions : opérations (ordres)
	inClock        : horloge
*/
@Singleton
class QuoteUpdater @Inject constructor(
	private val inSource       : QuoteSource,
	private val inMarket       : MarketRepository,
	private val inTransactions : TransactionRepository,
	private val inClock        : Clock
)
{
	private val fLock = Mutex()													// une seule mise à jour à la fois

	/*
		Met à jour les cours de tous les actifs détenus ou l'ayant été.
	*/
	suspend fun update(): QuoteOutcome =
		fLock.withLock {
			val vToday   = LocalDate.now(inClock)								// date du jour
			val vTxs     = inTransactions.observeTransactions().first()			// opérations
			val vFirst   = vTxs.flatMap { inTx -> inTx.trades.map { it.assetId to inTx.date } }
				.groupBy({ it.first }, { it.second })
				.mapValues { (_, inDates) -> inDates.min() }					// premier ordre de chaque actif
			val vPrices  = inMarket.observePrices().first().groupBy { it.assetId }	// cours déjà enregistrés
			val vAssets  = inMarket.observeAssets().first().filter { !it.archived && it.id in vFirst }	// actifs à suivre
			var vUpdated = 0															// actifs mis à jour
			val vMissing = mutableListOf<String>()										// actifs sans cotation
			try
			{
				vAssets.forEach { inAsset ->
					val vTicker = inAsset.ticker ?: resolve(inAsset, vToday)			// symbole de cotation
					if (vTicker == null)
					{
						vMissing += inAsset.name
						return@forEach
					}
					val vFrom  = startDate(vFirst.getValue(inAsset.id), vPrices[inAsset.id].orEmpty(), vToday)	// premier jour à demander
					val vChart = inSource.chart(vTicker, vFrom.minusDays(7))			// historique
					if (vChart == null || vChart.currency != "EUR")
					{
						vMissing += inAsset.name
						return@forEach
					}
					inMarket.putPriceSeries(inAsset.id, pricesToStore(vChart, vFrom))
					vUpdated++
				}
				QuoteOutcome(vUpdated, vMissing, null)
			}
			catch (inError: Exception)
			{
				QuoteOutcome(vUpdated, vMissing, inError.message ?: "service de cours injoignable")
			}
		}

	/*
		Trouve et enregistre le symbole d'un actif à partir de son ISIN, si la
		correspondance en euros est directe ; null sinon.
		inAsset : actif
		inToday : date du jour
	*/
	private suspend fun resolve(
		inAsset : Asset,
		inToday : LocalDate
	): String?
	{
		val vIsin    = inAsset.isin ?: return null									// ISIN connu
		val vListing = findEuroListing(inSource, vIsin, inGuess = false, inToday = inToday) ?: return null	// cotation trouvée
		inMarket.updateAsset(inAsset.id, inAsset.name, inAsset.isin, vListing.symbol)
		return vListing.symbol
	}
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

/*
	Mise à jour des cours avec un rythme raisonnable : au plus une fois toutes
	les quatre heures, sauf à la demande. Mémorise l'instant de la dernière
	mise à jour réussie.
	inUpdater  : mise à jour des cours
	inSettings : instant de la dernière mise à jour
	inClock    : horloge
*/
@Singleton
class QuoteRefresher @Inject constructor(
	private val inUpdater  : QuoteUpdater,
	private val inSettings : SettingsRepository,
	private val inClock    : Clock
)
{
	// Instant de la dernière mise à jour réussie (ms) ; null avant la première.
	val lastUpdate = inSettings.lastQuotesAt

	/*
		Met à jour si la dernière mise à jour date de plus de quatre heures ;
		null si rien n'a été demandé.
	*/
	suspend fun refreshIfStale(): QuoteOutcome?
	{
		val vLast = inSettings.lastQuotesAt.first()								// dernière mise à jour
		return if (vLast != null && inClock.millis() - vLast < kStaleMillis) null else refreshNow()
	}

	/*
		Met à jour tout de suite.
	*/
	suspend fun refreshNow(): QuoteOutcome
	{
		val vOutcome = inUpdater.update()										// résultat
		if (vOutcome.error == null)
		{
			inSettings.setLastQuotesAt(inClock.millis())
		}
		return vOutcome
	}
}
