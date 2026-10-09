package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.price
import fr.maubin.patrimoineplus.domain.qty
import org.junit.Assert.assertEquals
import org.junit.Test

/*
	Tests des calculs monétaires : arrondis, part détenue, montants d'ordres.
*/
class MoneyMathTest
{
	/*
		L'arrondi est commercial et symétrique : les demis s'éloignent de zéro.
	*/
	@Test
	fun roundDivRoundsHalfAwayFromZero()
	{
		assertEquals(3L, roundDiv(5, 2))
		assertEquals(-3L, roundDiv(-5, 2))
		assertEquals(2L, roundDiv(7, 4))
		assertEquals(-2L, roundDiv(-7, 4))
		assertEquals(0L, roundDiv(0, 7))
	}

	/*
		La part détenue s'applique au centime près, y compris en négatif.
	*/
	@Test
	fun shareOfAppliesOwnership()
	{
		assertEquals(1_001L, shareOf(1_001, 100))
		assertEquals(501L, shareOf(1_001, 50))
		assertEquals(-501L, shareOf(-1_001, 50))
		assertEquals(330L, shareOf(1_000, 33))
		assertEquals(1L, shareOf(3, 33))										// 0,99 → 1
		assertEquals(0L, shareOf(1_000, 0))
	}

	/*
		Montant brut = quantité × cours, arrondi au centime.
	*/
	@Test
	fun grossCentsMultipliesAndRounds()
	{
		assertEquals(1_000L, grossCents(qty(1.0), price(10.0)))
		assertEquals(1_550L, grossCents(qty(1.5), price(10.3333)))			// 15,49995 € → 15,50 €
		assertEquals(1L, grossCents(qty(0.0001), price(100.0)))				// 0,01 €
	}

	/*
		Les grands produits ne débordent pas (calcul en précision exacte).
	*/
	@Test
	fun grossCentsHandlesLargeValues()
	{
		assertEquals(10_000_000_000_000L, grossCents(qty(1_000_000.0), price(100_000.0)))
	}

	/*
		Achat = −(brut + frais) ; vente = brut − frais.
	*/
	@Test
	fun tradeCashSignsAndFees()
	{
		assertEquals(-10_250L, tradeCashCents(TradeSide.BUY, qty(10.0), price(10.0), 250))
		assertEquals(9_750L, tradeCashCents(TradeSide.SELL, qty(10.0), price(10.0), 250))
	}
}
