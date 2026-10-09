package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kEtf
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kPea
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.quote
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.trade
import fr.maubin.patrimoineplus.domain.transfer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/*
	Tests de la vue globale d'un compte : totaux depuis l'ouverture et série
	de valeur mois par mois.
*/
class AccountHistoryTest
{
	// Livret : solde initial, deux versements, un retrait, des intérêts, des frais, un versement à venir.
	private val kLivretOps = listOf(
		opening("2026-01-01", kLivret, eur(5_000.0)),
		transfer("2026-02-10", kChecking, kLivret, eur(300.0)),
		transfer("2026-03-10", kChecking, kLivret, eur(300.0)),
		transfer("2026-04-02", kLivret, kChecking, eur(150.0)),
		standard("2026-04-30", kLivret, eur(42.5), kSalary),
		standard("2026-04-30", kLivret, eur(-2.0), kGroceries),
		transfer("2026-06-10", kChecking, kLivret, eur(300.0))
	)

	/*
		Totaux d'un livret : chaque nature à sa place, à venir exclu, et leur
		somme égale au solde calculé par le grand livre.
	*/
	@Test
	fun totalsAddUpToBalance()
	{
		val vLedger = Ledger(kLivretOps)										// grand livre
		val vTotals = accountTotals(vLedger, kLivret.id, d("2026-05-15"))		// totaux au 15 mai
		assertEquals(eur(5_000.0), vTotals.opening)
		assertEquals(eur(300.0 + 300.0 - 150.0), vTotals.transfers)
		assertEquals(eur(42.5), vTotals.income)
		assertEquals(eur(-2.0), vTotals.expenses)
		assertEquals(0L, vTotals.trades)
		assertEquals(vLedger.balanceAt(kLivret.id, d("2026-05-15")), vTotals.balanceCents)
	}

	/*
		Série d'un livret : un point par mois depuis l'ouverture, le mois en
		cours arrêté à la date de fin.
	*/
	@Test
	fun savingsSeriesFollowsBalance()
	{
		val vSeries = accountValueSeries(kLivret, Ledger(kLivretOps), emptyList(), d("2026-06-05"))	// série au 5 juin
		assertEquals(YearMonth.of(2026, 1), vSeries.first().month)
		assertEquals(YearMonth.of(2026, 6), vSeries.last().month)
		assertEquals(eur(5_000.0), vSeries[0].valueCents)
		assertEquals(eur(5_300.0), vSeries[1].valueCents)
		assertEquals(eur(5_490.5), vSeries[3].valueCents)
		assertEquals(eur(5_490.5), vSeries.last().valueCents)					// versement du 10 juin à venir
	}

	/*
		Série d'un compte Bourse : espèces plus positions au dernier cours connu
		en fin de mois (et non les seules espèces).
	*/
	@Test
	fun brokerageSeriesValuesPositions()
	{
		val vOps    = listOf(
			transfer("2026-01-05", kChecking, kPea, eur(1_000.0)),
			trade("2026-01-06", kPea, TradeSide.BUY, kEtf, 8.0, 100.0)
		)																		// versement puis achat
		val vPrices = listOf(quote("2026-01-31", kEtf, 110.0), quote("2026-02-27", kEtf, 90.0))	// cours de fin de mois
		val vSeries = accountValueSeries(kPea, Ledger(vOps), vPrices, d("2026-03-15"))	// série au 15 mars
		assertEquals(listOf(eur(200.0 + 880.0), eur(200.0 + 720.0), eur(200.0 + 720.0)), vSeries.map { it.valueCents })
	}
}
