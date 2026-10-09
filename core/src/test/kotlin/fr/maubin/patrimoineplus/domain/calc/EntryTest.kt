package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLeisure
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/*
	Tests des fonctions de saisie et de la vue mensuelle d'un compte.
*/
class EntryTest
{
	/*
		Le signe découle de la nature : dépense négative, revenu positif, quel
		que soit le signe tapé ; libellé et notes nettoyés.
	*/
	@Test
	fun buildStandardSignsByKind()
	{
		val vSpend  = buildStandard("t", "p", d("2026-10-07"), kChecking.id, 1_230, CategoryKind.EXPENSE, kGroceries.id, "  Marché ", " ")	// dépense
		val vIncome = buildStandard("t", "p", d("2026-10-07"), kChecking.id, -50_000, CategoryKind.INCOME, kSalary.id, "Paie", "octobre")	// revenu
		assertEquals(-1_230L, vSpend.postings.single().amountCents)
		assertEquals("Marché", vSpend.label)
		assertNull(vSpend.notes)
		assertEquals(TransactionKind.STANDARD, vSpend.kind)
		assertEquals(50_000L, vIncome.postings.single().amountCents)
		assertEquals("octobre", vIncome.notes)
	}

	/*
		Suggestions : sans accents ni majuscules, par début de libellé ou de
		mot, classées par fréquence puis récence, avec la dernière catégorie ;
		jamais le texte exact déjà tapé ; jamais les virements.
	*/
	@Test
	fun labelSuggestionsRankAndCarryCategory()
	{
		val vTxs = listOf(
			standard("2026-09-01", kChecking, eur(-10.0), kGroceries).copy(label = "Supérette"),
			standard("2026-09-08", kChecking, eur(-12.0), kGroceries).copy(label = "Supérette"),
			standard("2026-09-20", kChecking, eur(-30.0), kLeisure).copy(label = "Supermarché du coin"),
			standard("2026-10-01", kChecking, eur(-11.0), kLeisure).copy(label = "supérette"),
			standard("2026-10-02", kChecking, eur(-40.0), kLeisure).copy(label = "Cinéma Super 8"),
			transfer("2026-10-03", kChecking, kLivret, eur(100.0)).copy(label = "Super épargne")
		)																		// historique
		val vFound = labelSuggestions(vTxs, "supe")								// suggestions
		assertEquals(listOf("supérette", "Cinéma Super 8", "Supermarché du coin"), vFound.map { it.label })
		assertEquals(kLeisure.id, vFound.first().categoryId)
		assertTrue(labelSuggestions(vTxs, "").isEmpty())
		assertTrue(labelSuggestions(vTxs, "Supérette").none { it.label.equals("supérette", true) })
	}

	/*
		Série des soldes : un point par mois, se terminant au mois demandé,
		mois sans opération compris (solde reporté).
	*/
	@Test
	fun balanceSeriesCarriesBalances()
	{
		val vLedger = Ledger(listOf(
			standard("2026-08-05", kChecking, eur(100.0), kSalary),
			standard("2026-10-05", kChecking, eur(-30.0), kGroceries)
		))																		// grand livre
		val vSeries = balanceSeries(vLedger, kChecking.id, YearMonth.of(2026, 10), 4)	// série
		assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), vSeries.map { it.month })
		assertEquals(listOf(0L, eur(100.0), eur(100.0), eur(70.0)), vSeries.map { it.valueCents })
	}

	/*
		Opérations d'un mois sur un compte, de la plus récente à la plus
		ancienne, montant vu du compte (virement compris).
	*/
	@Test
	fun monthTransactionsAndAmountOn()
	{
		val vXfer   = transfer("2026-10-03", kChecking, kLivret, eur(100.0))	// virement
		val vLedger = Ledger(listOf(
			standard("2026-09-30", kChecking, eur(-1.0), kGroceries),
			standard("2026-10-01", kChecking, eur(-2.0), kGroceries),
			vXfer,
			standard("2026-10-09", kLivret, eur(5.0), kSalary)
		))																		// grand livre
		val vOct = monthTransactions(vLedger, kChecking.id, YearMonth.of(2026, 10))	// opérations d'octobre
		assertEquals(listOf(d("2026-10-03"), d("2026-10-01")), vOct.map { it.date })
		assertEquals(eur(-100.0), amountOn(vXfer, kChecking.id))
		assertEquals(eur(100.0), amountOn(vXfer, kLivret.id))
	}

	/*
		Virement : deux jambes opposées sur les deux comptes, quel que soit le
		signe tapé ; ordre : jambe = cours × quantité ± frais.
	*/
	@Test
	fun buildTransferAndTrade()
	{
		val vXfer = buildTransfer("v", "o", "i", d("2026-10-08"), kChecking.id, kLivret.id, -15_000, "Épargne", "")	// virement
		assertEquals(listOf(kChecking.id to -15_000L, kLivret.id to 15_000L), vXfer.postings.map { it.accountId to it.amountCents })
		assertEquals(TransactionKind.TRANSFER, vXfer.kind)
		val vBuy  = buildTrade("o", "p", "t", d("2026-10-08"), "pea", TradeSide.BUY, "etf", 30_000, 1_000_000, 199, "Achat", "")	// achat de 3 à 100 €
		val vSell = buildTrade("o", "p", "t", d("2026-10-08"), "pea", TradeSide.SELL, "etf", 30_000, 1_000_000, 199, "Vente", "")	// vente
		assertEquals(-30_199L, vBuy.postings.single().amountCents)
		assertEquals(29_801L, vSell.postings.single().amountCents)
		assertEquals(30_000L, vBuy.trades.single().quantity)
	}
}
