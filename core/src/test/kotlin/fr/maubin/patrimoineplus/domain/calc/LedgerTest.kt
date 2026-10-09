package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kEtf
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kJoint
import fr.maubin.patrimoineplus.domain.kLeisure
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kPea
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.trade
import fr.maubin.patrimoineplus.domain.transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/*
	Tests du grand livre : soldes, synthèse mensuelle, synthèse par catégorie.
*/
class LedgerTest
{
	private val kAccounts = listOf(kChecking, kJoint, kLivret, kPea).associateBy { it.id }	// comptes connus

	// Scénario de référence sur trois mois, toutes natures d'opérations confondues.
	private val kScenario = listOf(
		opening("2026-01-01", kChecking, eur(1_000.0)),
		opening("2026-01-01", kLivret, eur(5_000.0)),
		standard("2026-01-05", kChecking, eur(2_500.0), kSalary),
		standard("2026-01-10", kChecking, eur(-120.0), kGroceries),
		standard("2026-01-31", kChecking, eur(-30.0), kLeisure),
		transfer("2026-01-31", kChecking, kLivret, eur(400.0)),
		standard("2026-02-01", kChecking, eur(-80.0), kGroceries),
		standard("2026-02-03", kChecking, eur(15.0), kGroceries),				// remboursement
		transfer("2026-02-10", kChecking, kJoint, eur(300.0)),
		transfer("2026-02-15", kLivret, kChecking, eur(100.0)),
		transfer("2026-02-20", kChecking, kPea, eur(1_000.0)),
		trade("2026-02-21", kPea, TradeSide.BUY, kEtf, 10.0, 50.0, 1.0),
		standard("2026-03-02", kChecking, eur(-45.5), kLeisure)
	)

	/*
		Le solde inclut les opérations du jour même et ignore les suivantes.
	*/
	@Test
	fun balanceIncludesSameDayOnly()
	{
		val vLedger = Ledger(kScenario)											// grand livre
		assertEquals(eur(1_000.0), vLedger.balanceAt(kChecking.id, d("2026-01-01")))
		assertEquals(eur(3_500.0), vLedger.balanceAt(kChecking.id, d("2026-01-05")))
		assertEquals(0L, vLedger.balanceAt(kChecking.id, d("2025-12-31")))
		assertEquals(eur(5_400.0), vLedger.balanceAt(kLivret.id, d("2026-01-31")))
	}

	/*
		Le solde d'ouverture d'un mois est la clôture calculée du mois précédent.
	*/
	@Test
	fun openingIsPreviousClosing()
	{
		val vLedger = Ledger(kScenario)											// grand livre
		val vJan    = vLedger.monthSummary(kChecking, YearMonth.of(2026, 1), kAccounts)	// janvier
		val vFeb    = vLedger.monthSummary(kChecking, YearMonth.of(2026, 2), kAccounts)	// février
		val vMar    = vLedger.monthSummary(kChecking, YearMonth.of(2026, 3), kAccounts)	// mars
		assertEquals(0L, vJan.opening)
		assertEquals(vJan.closing, vFeb.opening)
		assertEquals(vFeb.closing, vMar.opening)
	}

	/*
		Invariant : ouverture + ventilation = clôture, pour chaque compte et chaque mois.
	*/
	@Test
	fun monthBreakdownAddsUpToClosing()
	{
		val vLedger = Ledger(kScenario)											// grand livre
		kAccounts.values.forEach { inAccount ->
			(1..3).forEach { inMonth ->
				val vSum = vLedger.monthSummary(inAccount, YearMonth.of(2026, inMonth), kAccounts)	// synthèse
				assertEquals(
					"${inAccount.name}, mois $inMonth",
					vSum.closing,
					vSum.opening + vSum.openings + vSum.income + vSum.expenses + vSum.savings + vSum.transfers + vSum.trades
				)
			}
		}
	}

	/*
		Ventilation de janvier sur le compte courant : entrées, dépenses, épargne.
	*/
	@Test
	fun januaryBreakdown()
	{
		val vJan = Ledger(kScenario).monthSummary(kChecking, YearMonth.of(2026, 1), kAccounts)	// janvier
		assertEquals(eur(1_000.0), vJan.openings)
		assertEquals(eur(2_500.0), vJan.income)
		assertEquals(eur(-150.0), vJan.expenses)
		assertEquals(eur(-400.0), vJan.savings)
		assertEquals(0L, vJan.transfers)
		assertEquals(eur(2_950.0), vJan.closing)
	}

	/*
		Février : un virement vers un autre compte courant n'est pas de l'épargne ;
		un retrait du livret et un versement au PEA en sont ; un remboursement est
		une entrée sur le compte.
	*/
	@Test
	fun februaryClassifiesTransfers()
	{
		val vFeb = Ledger(kScenario).monthSummary(kChecking, YearMonth.of(2026, 2), kAccounts)	// février
		assertEquals(eur(-300.0), vFeb.transfers)
		assertEquals(eur(100.0 - 1_000.0), vFeb.savings)
		assertEquals(eur(15.0), vFeb.income)
		assertEquals(eur(-80.0), vFeb.expenses)
	}

	/*
		Côté livret, les virements avec le compte courant sont aussi de l'épargne.
	*/
	@Test
	fun savingsSideSeesSavings()
	{
		val vLedger = Ledger(kScenario)											// grand livre
		assertEquals(eur(400.0), vLedger.monthSummary(kLivret, YearMonth.of(2026, 1), kAccounts).savings)
		assertEquals(eur(-100.0), vLedger.monthSummary(kLivret, YearMonth.of(2026, 2), kAccounts).savings)
		assertEquals(eur(-501.0), vLedger.monthSummary(kPea, YearMonth.of(2026, 2), kAccounts).trades)
	}

	/*
		Une opération à venir compte dans la clôture de son mois, pas dans le
		solde du jour.
	*/
	@Test
	fun upcomingOperationsCountInMonthNotToday()
	{
		val vRent   = standard("2026-03-28", kChecking, eur(-700.0), kLeisure)	// loyer à venir
		val vLedger = Ledger(kScenario + vRent)									// grand livre
		val vToday  = d("2026-03-10")											// aujourd'hui
		assertTrue(vRent.isUpcoming(vToday))
		assertFalse(kScenario.last().isUpcoming(vToday))
		assertEquals(vLedger.balanceAt(kChecking.id, vToday) - eur(700.0),
			vLedger.monthSummary(kChecking, YearMonth.of(2026, 3), kAccounts).closing)
	}

	/*
		Synthèse des dépenses par catégorie : remboursements déduits, tri
		décroissant, parts en pourcentage.
	*/
	@Test
	fun categorySummaryNetsRefunds()
	{
		val vLines = Ledger(kScenario).categorySummary(
			kChecking.id, d("2026-01-01"), d("2026-03-31"), listOf(kGroceries, kLeisure, kSalary), CategoryKind.EXPENSE
		)																		// lignes de dépense
		assertEquals(listOf(kGroceries, kLeisure), vLines.map { it.category })
		assertEquals(eur(185.0), vLines[0].netCents)							// 120 + 80 − 15
		assertEquals(eur(75.5), vLines[1].netCents)
		assertEquals(71, vLines[0].percent)
		assertEquals(29, vLines[1].percent)
	}

	/*
		La synthèse est faite compte par compte, à 100 % : les dépenses d'un
		compte joint ne sont ni réduites à la part détenue ni mêlées à un autre compte.
	*/
	@Test
	fun categorySummaryIsPerAccountAtFullAmount()
	{
		val vJointShop = standard("2026-02-12", kJoint, eur(-100.0), kGroceries)	// courses sur le joint
		val vLedger    = Ledger(kScenario + vJointShop)							// grand livre
		val vJoint     = vLedger.categorySummary(kJoint.id, d("2026-02-01"), d("2026-02-28"), listOf(kGroceries), CategoryKind.EXPENSE)	// joint
		val vOwn       = vLedger.categorySummary(kChecking.id, d("2026-02-01"), d("2026-02-28"), listOf(kGroceries), CategoryKind.EXPENSE)	// courant
		assertEquals(eur(100.0), vJoint.single().netCents)
		assertEquals(eur(65.0), vOwn.single().netCents)
	}

	/*
		Synthèse des revenus et bornes de période incluses.
	*/
	@Test
	fun incomeSummaryAndPeriodBounds()
	{
		val vLedger = Ledger(kScenario)											// grand livre
		val vIncome = vLedger.categorySummary(kChecking.id, d("2026-01-05"), d("2026-01-05"), listOf(kSalary, kGroceries), CategoryKind.INCOME)	// revenus
		assertEquals(eur(2_500.0), vIncome.single().netCents)
		assertEquals(100, vIncome.single().percent)
		assertTrue(vLedger.categorySummary(kChecking.id, d("2026-01-06"), d("2026-01-09"), listOf(kGroceries), CategoryKind.EXPENSE).isEmpty())
	}
}
