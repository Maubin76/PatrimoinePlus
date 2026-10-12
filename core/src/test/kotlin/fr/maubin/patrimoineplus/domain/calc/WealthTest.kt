package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.id
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kEtf
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kJoint
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kPea
import fr.maubin.patrimoineplus.domain.model.AccountStatement
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.quote
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.trade
import fr.maubin.patrimoineplus.domain.transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/*
	Tests du patrimoine : part détenue, compte Bourse valorisé, séries, relevés.
*/
class WealthTest
{
	private val kArchived = kLivret.copy(id = "ancien", name = "Ancien livret", archived = true)	// compte archivé
	private val kAccounts = listOf(kChecking, kJoint, kLivret, kPea, kArchived)		// comptes

	// Soldes initiaux, un virement vers le joint, un versement et un achat sur le PEA.
	private val kOps = listOf(
		opening("2026-01-01", kChecking, eur(2_000.0)),
		opening("2026-01-01", kJoint, eur(1_001.0)),
		opening("2026-01-01", kLivret, eur(10_000.0)),
		opening("2026-01-01", kArchived, eur(50.0)),
		transfer("2026-02-01", kChecking, kPea, eur(1_000.0)),
		trade("2026-02-02", kPea, TradeSide.BUY, kEtf, 8.0, 100.0),
		standard("2026-03-05", kJoint, eur(-200.0), kGroceries)
	)

	/*
		Patrimoine : chaque compte pour sa part détenue (joint à 50 %, arrondi au
		centime), compte Bourse à sa valeur, compte archivé compris.
	*/
	@Test
	fun wealthAppliesShareAndValuesBrokerage()
	{
		val vWealth = wealthAt(kAccounts, Ledger(kOps), d("2026-02-28"), listOf(quote("2026-02-27", kEtf, 110.0)))	// patrimoine
		val vJoint  = vWealth.accounts.single { it.account.id == kJoint.id }		// valeur du joint
		val vPea    = vWealth.accounts.single { it.account.id == kPea.id }			// valeur du PEA
		assertEquals(eur(1_001.0), vJoint.valueCents)
		assertEquals(eur(500.5), vJoint.countedCents)
		assertEquals(eur(200.0 + 880.0), vPea.valueCents)						// espèces + 8 × 110
		assertEquals(eur(1_000.0 + 500.5 + 10_000.0 + 1_080.0 + 50.0), vWealth.totalCents)
	}

	/*
		Un virement entre deux comptes détenus à 100 % ne change pas le patrimoine.
	*/
	@Test
	fun transferBetweenOwnAccountsIsNeutral()
	{
		val vLedger = Ledger(kOps)												// grand livre
		val vBefore = wealthAt(kAccounts, vLedger, d("2026-01-31"), emptyList()).totalCents	// avant le versement
		val vAfter  = wealthAt(kAccounts, vLedger, d("2026-02-01"), emptyList()).totalCents	// après
		assertEquals(vBefore, vAfter)
	}

	/*
		Répartition par type, sur les valeurs comptées.
	*/
	@Test
	fun repartitionByType()
	{
		val vByType = wealthAt(kAccounts, Ledger(kOps), d("2026-01-31"), emptyList()).byType	// répartition
		assertEquals(eur(2_000.0 + 500.5), vByType[AccountType.CHECKING])
		assertEquals(eur(10_050.0), vByType[AccountType.SAVINGS])
		assertTrue(AccountType.BROKERAGE !in vByType)
	}

	/*
		Série mensuelle : un point par mois depuis la première opération ; le mois
		en cours s'arrête à la date de fin et ignore les opérations à venir.
	*/
	@Test
	fun monthlySeries()
	{
		val vFuture = standard("2026-03-25", kChecking, eur(-999.0), kGroceries)	// opération à venir
		val vSeries = wealthSeries(kAccounts, Ledger(kOps + vFuture), emptyList(), d("2026-03-10"))	// série
		assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)), vSeries.map { it.month })
		assertEquals(eur(12_550.5), vSeries[0].valueCents)
		assertEquals(eur(12_450.5), vSeries[2].valueCents)						// joint −200 compté à 50 %
		assertTrue(wealthSeries(kAccounts, Ledger(emptyList()), emptyList(), d("2026-03-10")).isEmpty())
	}

	/*
		Écart de relevé : solde de la banque − solde calculé à la date du relevé.
	*/
	@Test
	fun statementGapShowsMissingInterest()
	{
		val vStatement = AccountStatement(id("s"), kLivret.id, d("2026-01-31"), eur(10_012.5))	// relevé
		assertEquals(eur(12.5), statementGap(vStatement, Ledger(kOps)))
	}

	/*
		Variation du mois : dernier point moins le précédent ; aucune le premier
		mois (les soldes de départ ne sont pas une variation) ni sans série.
	*/
	@Test
	fun monthChangeFromSeries()
	{
		val vSeries = wealthSeries(kAccounts, Ledger(kOps), emptyList(), d("2026-03-10"))	// série
		assertEquals(eur(-100.0), monthChange(vSeries))							// joint −200 compté à 50 %
		assertEquals(null, monthChange(vSeries.take(1)))						// premier mois : soldes de départ, pas une variation
		assertEquals(null, monthChange(emptyList()))
	}
}
