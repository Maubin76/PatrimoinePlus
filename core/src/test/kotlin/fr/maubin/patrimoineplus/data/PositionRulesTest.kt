package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.validateBackup
import fr.maubin.patrimoineplus.data.edit.DataEditor
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.data.edit.OpeningBalance
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.grossCents
import fr.maubin.patrimoineplus.domain.calc.kMaxTradeCents
import fr.maubin.patrimoineplus.domain.calc.valuePortfolio
import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kEtf
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kPea
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.price
import fr.maubin.patrimoineplus.domain.qty
import fr.maubin.patrimoineplus.domain.rules.TransactionValidator
import fr.maubin.patrimoineplus.domain.rules.ValidationError
import fr.maubin.patrimoineplus.domain.trade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Ordres de bourse : ordre des opérations d'une même journée et ventes
	rendues impossibles par une saisie, une modification ou une suppression.
	Bugs trouvés à la revue de code du 2026-10-12.
*/
class PositionRulesTest
{
	private val kEditor = DataEditor(Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris")))	// moteur testé

	/*
		Contenu de test : comptes, catégories et l'actif, plus les opérations.
		inTxs : opérations
	*/
	private fun file(vararg inTxs: Transaction): BackupFile =
		backupFileOf(DataSet(listOf(kChecking, kLivret, kPea), listOf(kGroceries, kSalary), listOf(kEtf), inTxs.toList(), emptyList(), emptyList()))

	/*
		Contenu d'une modification acceptée.
		inResult : résultat
	*/
	private fun done(inResult: EditResult): BackupFile
	{
		assertTrue("modification refusée : $inResult", inResult is EditResult.Done)
		return (inResult as EditResult.Done).file
	}

	/*
		Message d'une modification refusée.
		inResult : résultat
	*/
	private fun refusal(inResult: EditResult): String
	{
		assertTrue("refus attendu : $inResult", inResult is EditResult.Refused)
		return (inResult as EditResult.Refused).messages.single()
	}

	/*
		Achat et vente le même jour : quel que soit l'ordre des opérations
		(identifiants, ordre du fichier), le fichier est valide et le PRU et la
		plus-value réalisée sont justes. Avant : la vente passait parfois avant
		l'achat (fichier refusé, plus-value de 440 € au lieu de 40 €).
	*/
	@Test
	fun sameDayBuyThenSell()
	{
		val vBuy  = trade("2026-03-02", kPea, TradeSide.BUY, kEtf, 10.0, 100.0).copy(id = "zz-achat")	// achat, id trié après
		val vSell = trade("2026-03-02", kPea, TradeSide.SELL, kEtf, 4.0, 110.0).copy(id = "aa-vente")	// vente, id trié avant
		val vCash = opening("2026-03-01", kPea, eur(2_000.0))						// espèces
		listOf(listOf(vCash, vBuy, vSell), listOf(vSell, vBuy, vCash)).forEach { inOrder ->
			assertTrue(validateBackup(file(*inOrder.toTypedArray())) is BackupRead.Valid)
			val vValue = valuePortfolio(Ledger(inOrder), kPea.id, d("2026-03-31"), emptyList())	// valorisation
			assertEquals(eur(40.0), vValue.realized.single().gainCents)
			assertEquals(qty(6.0), vValue.positions.single().quantity)
			assertEquals(eur(600.0), vValue.positions.single().costCents)
		}
	}

	/*
		Une vente saisie avant une vente existante ne doit pas rendre celle-ci
		impossible ; la vente fautive (ou la suivante) est désignée.
	*/
	@Test
	fun earlierSaleCannotBreakLaterSale()
	{
		val vBuy      = trade("2026-01-01", kPea, TradeSide.BUY, kEtf, 10.0, 50.0)	// achat de 10
		val vSell     = trade("2026-01-03", kPea, TradeSide.SELL, kEtf, 10.0, 55.0).copy(label = "Vente totale")	// vente de 10
		val vValidator = TransactionValidator(listOf(kPea).associateBy { it.id }, emptyMap(), listOf(kEtf).associateBy { it.id })	// règles
		val vErrors   = vValidator.validate(trade("2026-01-02", kPea, TradeSide.SELL, kEtf, 5.0, 52.0), Ledger(listOf(vBuy, vSell)))	// vente de 5 intercalée
		assertEquals(listOf(ValidationError.BreaksLaterSale("Vente totale", d("2026-01-03"))), vErrors)
		assertEquals("La vente « Vente totale » du 03/01/2026 dépasserait alors la quantité détenue.", vErrors.single().message)
	}

	/*
		PC : supprimer l'achat, retirer la position initiale, réduire l'achat ou
		déplacer le solde initial après la vente est refusé ; rien ne change.
	*/
	@Test
	fun editsThatBreakSalesAreRefused()
	{
		val vBuy  = trade("2026-01-01", kPea, TradeSide.BUY, kEtf, 10.0, 50.0)		// achat de 10
		val vSell = trade("2026-01-03", kPea, TradeSide.SELL, kEtf, 10.0, 55.0)		// vente de 10
		val vBase = file(opening("2025-12-31", kPea, eur(1_000.0)), vBuy, vSell)	// contenu valide
		assertTrue(refusal(kEditor.deleteTransactions(vBase, listOf(vBuy.id))).contains("dépasserait"))
		val vSmaller = vBuy.copy(trades = listOf(vBuy.trades.single().copy(quantity = qty(5.0))), postings = listOf(vBuy.postings.single().copy(amountCents = eur(-250.0))))	// achat réduit à 5
		assertTrue(refusal(kEditor.saveTransaction(vBase, vSmaller)).contains("dépasserait"))
		done(kEditor.deleteTransactions(vBase, listOf(vSell.id, vBuy.id)))		// les deux ensemble : accepté

		val vPosition = Trade("pos", kEtf.id, TradeSide.BUY, qty(10.0), price(40.0))	// position initiale de 10
		val vWithPos  = file(opening("2025-12-31", kPea, 0L, listOf(vPosition)), vSell)	// vente adossée à la position
		assertTrue(validateBackup(vWithPos) is BackupRead.Valid)
		refusal(kEditor.removeOpeningPosition(vWithPos, kPea.id, "pos"))
		refusal(kEditor.setOpening(vWithPos, kPea.id, OpeningBalance(0, d("2026-02-01"))))
		done(kEditor.setOpening(vWithPos, kPea.id, OpeningBalance(eur(10.0), d("2025-12-01"))))
	}

	/*
		Des positions initiales verrouillent le type Bourse (avant : le compte
		pouvait devenir « Courant », et le fichier était ensuite refusé).
	*/
	@Test
	fun openingPositionsLockType()
	{
		val vPosition = Trade("pos", kEtf.id, TradeSide.BUY, qty(1.0), price(40.0))	// position initiale
		val vFile     = file(opening("2025-12-31", kPea, 0L, listOf(vPosition)))	// PEA sans autre opération
		assertEquals(1, kEditor.operationCount(vFile, kPea.id))
		refusal(kEditor.updateAccount(vFile, kPea.copy(type = AccountType.CHECKING)))
		assertEquals(0, kEditor.operationCount(file(opening("2025-12-31", kLivret, eur(5.0))), kLivret.id))
	}

	/*
		Un ordre démesuré ne fait pas planter le calcul ; il est refusé.
	*/
	@Test
	fun hugeTradeIsRefusedNotCrashing()
	{
		assertEquals(kMaxTradeCents, grossCents(999_999_999_999L * 10_000, 999_999_999_999L * 10_000))
		val vValidator = TransactionValidator(listOf(kPea).associateBy { it.id }, emptyMap(), listOf(kEtf).associateBy { it.id })	// règles
		val vHuge      = Trade("t", kEtf.id, TradeSide.BUY, 999_999_999_999L * 10_000, 999_999_999_999L * 10_000, 0)	// ordre démesuré
		val vTx        = trade("2026-01-01", kPea, TradeSide.BUY, kEtf, 1.0, 1.0).let { it.copy(trades = listOf(vHuge)) }	// opération
		assertTrue(ValidationError.InvalidTradeValues in vValidator.validate(vTx, Ledger(emptyList())))
	}

	/*
		Valeurs absurdes refusées par le moteur du PC : position initiale ou
		cours nuls, cours d'un actif inconnu.
	*/
	@Test
	fun editorRefusesInvalidValues()
	{
		val vBase = file()															// contenu vide d'opérations
		refusal(kEditor.addOpeningPosition(vBase, kPea.id, kEtf.id, 0, price(10.0), d("2026-01-01")))
		refusal(kEditor.addOpeningPosition(vBase, kPea.id, kEtf.id, qty(1.0), 0, d("2026-01-01")))
		refusal(kEditor.putPrices(vBase, d("2026-01-01"), mapOf(kEtf.id to 0L)))
		refusal(kEditor.putPrices(vBase, d("2026-01-01"), mapOf("inconnu" to price(1.0))))
		done(kEditor.putPrices(vBase, d("2026-01-01"), mapOf(kEtf.id to price(1.0))))
	}

	/*
		Réordonner les comptes actifs laisse les archivés après eux, sans rang
		en double (avant : rangs en double, ordre indéfini).
	*/
	@Test
	fun reorderKeepsUniqueRanks()
	{
		val vArchived = Account("vieux", "Ancien livret", AccountType.SAVINGS, sortOrder = 1, archived = true)	// compte archivé
		val vData     = DataSet(listOf(kChecking.copy(sortOrder = 0), vArchived, kLivret.copy(sortOrder = 2), kPea.copy(sortOrder = 3)), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())	// comptes
		val vFile     = done(kEditor.reorderAccounts(backupFileOf(vData), listOf(kPea.id, kLivret.id, kChecking.id)))	// nouvel ordre
		val vRanks    = vFile.accounts.associate { it.id to it.sortOrder }			// rangs
		assertEquals(listOf(0, 1, 2, 3), vRanks.values.sorted())
		assertEquals(listOf(kPea.id, kLivret.id, kChecking.id, "vieux"), vFile.accounts.sortedBy { it.sortOrder }.map { it.id })
	}
}
