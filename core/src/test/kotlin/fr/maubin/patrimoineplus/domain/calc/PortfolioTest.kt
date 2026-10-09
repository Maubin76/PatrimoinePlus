package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.id
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kEtf
import fr.maubin.patrimoineplus.domain.kPea
import fr.maubin.patrimoineplus.domain.kStock
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.price
import fr.maubin.patrimoineplus.domain.qty
import fr.maubin.patrimoineplus.domain.quote
import fr.maubin.patrimoineplus.domain.trade
import fr.maubin.patrimoineplus.domain.transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Tests du compte Bourse : PRU, ventes, plus-values, valorisation.
*/
class PortfolioTest
{
	// Deux achats de l'ETF à des cours différents, frais inclus, puis une vente.
	private val kTrades = listOf(
		transfer("2026-01-02", kChecking, kPea, eur(5_000.0)),
		trade("2026-01-10", kPea, TradeSide.BUY, kEtf, 10.0, 100.0, 2.0),		// coût 1 002 €
		trade("2026-02-10", kPea, TradeSide.BUY, kEtf, 10.0, 120.0, 2.0),		// coût 1 202 €
		trade("2026-03-10", kPea, TradeSide.SELL, kEtf, 5.0, 130.0, 1.0)		// produit net 649 €
	)

	/*
		Le PRU est moyen pondéré, frais d'achat inclus.
	*/
	@Test
	fun averageCostIncludesFees()
	{
		val vValue = valuePortfolio(Ledger(kTrades), kPea.id, d("2026-02-28"), emptyList())	// valorisation
		val vEtf   = vValue.positions.single()										// position ETF
		assertEquals(qty(20.0), vEtf.quantity)
		assertEquals(eur(2_204.0), vEtf.costCents)
		assertEquals(price(110.2), averagePrice(vEtf.costCents, vEtf.quantity))
	}

	/*
		Une vente laisse le PRU inchangé et réalise produit net − coût sorti.
	*/
	@Test
	fun saleKeepsAverageAndRealizesGain()
	{
		val vValue = valuePortfolio(Ledger(kTrades), kPea.id, d("2026-03-31"), emptyList())	// valorisation
		val vEtf   = vValue.positions.single()										// position ETF
		assertEquals(qty(15.0), vEtf.quantity)
		assertEquals(eur(1_653.0), vEtf.costCents)								// 2 204 × 15/20
		assertEquals(price(110.2), averagePrice(vEtf.costCents, vEtf.quantity))
		assertEquals(eur(649.0 - 551.0), vValue.realized.single().gainCents)
	}

	/*
		Les espèces sont le solde du compte (versements − achats + ventes).
	*/
	@Test
	fun cashIsAccountBalance()
	{
		val vValue = valuePortfolio(Ledger(kTrades), kPea.id, d("2026-03-31"), emptyList())	// valorisation
		assertEquals(eur(5_000.0 - 1_002.0 - 1_202.0 + 649.0), vValue.cashCents)
	}

	/*
		La valorisation retient le dernier cours connu à la date, pas un cours
		postérieur ; la plus-value latente en découle.
	*/
	@Test
	fun valuationUsesLastKnownPrice()
	{
		val vPrices = listOf(quote("2026-03-15", kEtf, 125.0), quote("2026-03-31", kEtf, 140.0), quote("2026-04-30", kEtf, 999.0))	// cours
		val vValue  = valuePortfolio(Ledger(kTrades), kPea.id, d("2026-04-01"), vPrices)	// valorisation
		val vEtf    = vValue.positions.single()										// position ETF
		assertEquals(d("2026-03-31"), vEtf.priceDate)
		assertEquals(eur(2_100.0), vEtf.valueCents)							// 15 × 140
		assertEquals(eur(2_100.0 - 1_653.0), vEtf.unrealizedCents)
		assertEquals(vValue.cashCents + eur(2_100.0), vValue.valueCents)
	}

	/*
		Sans cours relevé, la position est valorisée à son prix de revient et
		signalée « non valorisée ».
	*/
	@Test
	fun unpricedPositionFallsBackToCost()
	{
		val vEtf = valuePortfolio(Ledger(kTrades), kPea.id, d("2026-03-31"), emptyList()).positions.single()	// position
		assertTrue(vEtf.isUnpriced)
		assertEquals(vEtf.costCents, vEtf.valueCents)
		assertEquals(0L, vEtf.unrealizedCents)
	}

	/*
		Des positions initiales (solde initial) entrent dans les positions, au
		PRU saisi, sans mouvement d'espèces.
	*/
	@Test
	fun openingPositionsCount()
	{
		val vInitial = opening("2026-01-01", kPea, 0L, listOf(Trade(id("t"), kStock.id, TradeSide.BUY, qty(4.0), price(25.0))))	// positions initiales
		val vValue   = valuePortfolio(Ledger(listOf(vInitial)), kPea.id, d("2026-01-01"), listOf(quote("2026-01-01", kStock, 30.0)))	// valorisation
		val vStock   = vValue.positions.single()									// position
		assertEquals(0L, vValue.cashCents)
		assertEquals(eur(100.0), vStock.costCents)
		assertEquals(eur(120.0), vStock.valueCents)
		assertFalse(vStock.isUnpriced)
	}

	/*
		Quantité détenue à une date : achats − ventes jusqu'à cette date.
	*/
	@Test
	fun heldQuantityAtDate()
	{
		val vLedger = Ledger(kTrades)											// grand livre
		assertEquals(qty(10.0), heldQuantity(vLedger, kPea.id, kEtf.id, d("2026-01-31")))
		assertEquals(qty(15.0), heldQuantity(vLedger, kPea.id, kEtf.id, d("2026-03-10")))
		assertEquals(0L, heldQuantity(vLedger, kPea.id, kEtf.id, d("2026-01-09")))
	}

	/*
		Une position entièrement vendue disparaît de la liste.
	*/
	@Test
	fun closedPositionDisappears()
	{
		val vAll   = kTrades + trade("2026-04-01", kPea, TradeSide.SELL, kEtf, 15.0, 100.0)	// vente du solde
		val vValue = valuePortfolio(Ledger(vAll), kPea.id, d("2026-04-30"), emptyList())	// valorisation
		assertTrue(vValue.positions.isEmpty())
		assertEquals(2, vValue.realized.size)
	}

	/*
		Répartition des titres : de la plus grosse à la plus petite position,
		parts entières qui font toujours 100 au total, position nulle exclue.
	*/
	@Test
	fun allocationSumsToHundred()
	{
		val vPositions = listOf(
			Position("a", qty(1.0), 0L, null, 0L, 1L, 0L),
			Position("b", qty(1.0), 0L, null, 0L, 1L, 0L),
			Position("c", qty(1.0), 0L, null, 0L, 1L, 0L),
			Position("d", 0L, 0L, null, 0L, 0L, 0L),
			Position("e", qty(1.0), 0L, null, 0L, 3L, 0L)
		)																		// 3, 1, 1, 1 et une position soldée
		val vLines = allocation(vPositions)										// répartition
		assertEquals(listOf("e", "a", "b", "c"), vLines.map { it.position.assetId })
		assertEquals(100, vLines.sumOf { it.percent })
		assertEquals(listOf(50, 17, 17, 16), vLines.map { it.percent })
		assertTrue(allocation(emptyList()).isEmpty())
	}
}
