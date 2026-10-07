package fr.maubin.patrimoineplus.domain.rules

import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.id
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kEtf
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kPea
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.price
import fr.maubin.patrimoineplus.domain.qty
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.trade
import fr.maubin.patrimoineplus.domain.transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Tests des règles de saisie : chaque nature d'opération et chaque violation.
*/
class TransactionValidatorTest
{
	private val kValidator = TransactionValidator(									// validateur de test
		listOf(kChecking, kLivret, kPea).associateBy { it.id },
		listOf(kGroceries, kSalary).associateBy { it.id },
		listOf(kEtf).associateBy { it.id }
	)
	private val kEmpty = Ledger(emptyList())										// aucun historique

	/*
		Vérifie qu'une opération ne produit que les violations attendues.
		inTx       : opération
		inExpected : violations attendues
		inLedger   : autres opérations
	*/
	private fun check(
		inTx       : fr.maubin.patrimoineplus.domain.model.Transaction,
		inExpected : List<ValidationError>,
		inLedger   : Ledger = kEmpty
	)
	{
		assertEquals(inExpected, kValidator.validate(inTx, inLedger))
	}

	/*
		Les opérations bien formées de chaque nature sont acceptées.
	*/
	@Test
	fun validOperationsPass()
	{
		check(standard("2026-01-01", kChecking, eur(-12.0), kGroceries), emptyList())
		check(standard("2026-01-01", kChecking, eur(12.0), kGroceries), emptyList())	// remboursement
		check(transfer("2026-01-01", kChecking, kLivret, eur(100.0)), emptyList())
		check(opening("2026-01-01", kChecking, eur(500.0)), emptyList())
		check(trade("2026-01-01", kPea, TradeSide.BUY, kEtf, 3.0, 99.9, 1.5), emptyList())
	}

	/*
		Dépense : libellé, montant non nul, catégorie connue, une seule jambe.
	*/
	@Test
	fun standardRules()
	{
		val vBase = standard("2026-01-01", kChecking, eur(-12.0), kGroceries)	// dépense valide
		check(vBase.copy(label = " "), listOf(ValidationError.BlankLabel))
		check(vBase.copy(postings = listOf(Posting(id("p"), kChecking.id, 0))), listOf(ValidationError.ZeroAmount))
		check(vBase.copy(categoryId = null), listOf(ValidationError.MissingCategory))
		check(vBase.copy(categoryId = "inconnue"), listOf(ValidationError.MissingCategory))
		check(vBase.copy(postings = vBase.postings + Posting(id("p"), kLivret.id, 5)), listOf(ValidationError.PostingCount(1, 2)))
		check(vBase.copy(postings = listOf(Posting(id("p"), "fantome", -5))), listOf(ValidationError.UnknownAccount("fantome")))
	}

	/*
		Virement : deux comptes distincts, somme nulle, sans catégorie.
	*/
	@Test
	fun transferRules()
	{
		val vBase = transfer("2026-01-01", kChecking, kLivret, eur(100.0))		// virement valide
		check(vBase.copy(postings = listOf(Posting(id("p"), kChecking.id, -100), Posting(id("p"), kLivret.id, 90))),
			listOf(ValidationError.UnbalancedTransfer))
		check(vBase.copy(postings = listOf(Posting(id("p"), kChecking.id, -100), Posting(id("p"), kChecking.id, 100))),
			listOf(ValidationError.SameAccount))
		check(vBase.copy(categoryId = kGroceries.id), listOf(ValidationError.UnexpectedCategory))
		check(vBase.copy(postings = vBase.postings.take(1)), listOf(ValidationError.PostingCount(2, 1)))
	}

	/*
		Ordre : compte Bourse, montant cohérent avec cours × quantité ± frais,
		valeurs positives, actif connu.
	*/
	@Test
	fun tradeRules()
	{
		val vBase = trade("2026-01-01", kPea, TradeSide.BUY, kEtf, 2.0, 50.0, 1.0)	// achat valide (−101 €)
		check(vBase.copy(postings = listOf(Posting(id("p"), kPea.id, eur(-100.0)))), listOf(ValidationError.TradeAmountMismatch(eur(-101.0))))
		check(vBase.copy(postings = listOf(Posting(id("p"), kChecking.id, eur(-101.0)))), listOf(ValidationError.NotBrokerage))
		check(vBase.copy(trades = listOf(vBase.trades.single().copy(quantity = 0))), listOf(ValidationError.InvalidTradeValues))
		check(vBase.copy(trades = listOf(vBase.trades.single().copy(assetId = "inconnu"))), listOf(ValidationError.UnknownAsset))
		check(vBase.copy(trades = emptyList()), listOf(ValidationError.TradeCount(0)))
	}

	/*
		Une vente ne peut pas dépasser la quantité détenue à sa date.
	*/
	@Test
	fun cannotOversell()
	{
		val vBuy     = trade("2026-01-01", kPea, TradeSide.BUY, kEtf, 2.0, 50.0)	// achat de 2
		val vHistory = Ledger(listOf(vBuy))										// historique
		check(trade("2026-02-01", kPea, TradeSide.SELL, kEtf, 2.0, 60.0), emptyList(), vHistory)
		check(trade("2026-02-01", kPea, TradeSide.SELL, kEtf, 3.0, 60.0), listOf(ValidationError.OversoldPosition(qty(2.0))), vHistory)
		check(trade("2025-12-31", kPea, TradeSide.SELL, kEtf, 1.0, 60.0), listOf(ValidationError.OversoldPosition(0)), vHistory)
	}

	/*
		Solde initial : positions initiales seulement en achat et sur un compte Bourse.
	*/
	@Test
	fun openingRules()
	{
		val vPosition = Trade(id("t"), kEtf.id, TradeSide.BUY, qty(1.0), price(10.0))	// position initiale
		check(opening("2026-01-01", kPea, 0L, listOf(vPosition)), emptyList())
		check(opening("2026-01-01", kChecking, 0L, listOf(vPosition)), listOf(ValidationError.NotBrokerage))
		check(opening("2026-01-01", kPea, 0L, listOf(vPosition.copy(side = TradeSide.SELL))), listOf(ValidationError.OpeningMustBuy))
		assertTrue(kValidator.validate(opening("2026-01-01", kChecking, eur(10.0)).copy(categoryId = kSalary.id), kEmpty)
			.contains(ValidationError.UnexpectedCategory))
	}
}
