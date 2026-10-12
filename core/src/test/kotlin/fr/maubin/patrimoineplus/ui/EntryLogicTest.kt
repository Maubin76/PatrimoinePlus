package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.data.demo.buildDemoData
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.ui.entry.EntryData
import fr.maubin.patrimoineplus.ui.entry.EntryForm
import fr.maubin.patrimoineplus.ui.entry.EntryMode
import fr.maubin.patrimoineplus.ui.entry.buildEntry
import fr.maubin.patrimoineplus.ui.entry.checkEntry
import fr.maubin.patrimoineplus.ui.entry.editEntryForm
import fr.maubin.patrimoineplus.ui.entry.kNewAsset
import fr.maubin.patrimoineplus.ui.entry.newEntryForm
import fr.maubin.patrimoineplus.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/*
	Tests de la logique de saisie partagée par l'appli Android et l'appli PC :
	formulaire par défaut, contrôles, libellé proposé, aller-retour
	opération → formulaire → opération.
*/
class EntryLogicTest
{
	private val kToday = LocalDate.of(2026, 10, 12)								// date du jour
	private val kDemo  = buildDemoData(kToday)									// démonstration
	private val kData  = EntryData(kDemo.accounts, kDemo.categories, kDemo.assets, kDemo.prices, kDemo.transactions, lastAccountId = kDemo.accounts[1].id)	// données de saisie

	/*
		Formulaire vide : compte imposé s'il existe, sinon le dernier utilisé,
		sinon le premier ; daté du jour.
	*/
	@Test
	fun newFormPicksAccount()
	{
		assertEquals(kDemo.accounts[2].id, newEntryForm(kData, kDemo.accounts[2].id, kToday).accountId)
		assertEquals(kDemo.accounts[1].id, newEntryForm(kData, "inconnu", kToday).accountId)
		assertEquals(kDemo.accounts[0].id, newEntryForm(kData.copy(lastAccountId = null), null, kToday).accountId)
		assertEquals(kToday, newEntryForm(kData, null, kToday).date)
		val vBourse = kDemo.accounts.first { it.type == AccountType.BROKERAGE }			// compte Bourse
		assertEquals(EntryMode.TRADE, newEntryForm(kData, vBourse.id, kToday).mode)	// ordre proposé d'office
		assertEquals(EntryMode.EXPENSE, newEntryForm(kData, kDemo.accounts.first { it.type == AccountType.CHECKING }.id, kToday).mode)
	}

	/*
		Contrôles : montant vide, illisible ou nul ; catégorie et libellé
		obligatoires ; virement vers le même compte ; ordre hors compte Bourse,
		nouvel actif sans nom, quantité nulle.
	*/
	@Test
	fun checksEveryField()
	{
		val vEmpty = checkEntry(EntryForm(date = kToday), kData)					// tout vide
		assertEquals("Indiquez un montant.", vEmpty.amount)
		assertNotNull(vEmpty.account)
		assertNotNull(vEmpty.category)
		assertNotNull(vEmpty.label)
		assertEquals("Montant illisible (ex. 12,50).", checkEntry(EntryForm(amount = "12,5,0", date = kToday), kData).amount)
		assertEquals("Le montant ne peut pas être nul.", checkEntry(EntryForm(amount = "0", date = kToday), kData).amount)
		val vSame = kDemo.accounts[0].id											// même compte des deux côtés
		assertEquals("Choisissez deux comptes différents.", checkEntry(EntryForm(mode = EntryMode.TRANSFER, amount = "10", accountId = vSame, toAccountId = vSame, date = kToday), kData).target)
		val vTrade = checkEntry(EntryForm(mode = EntryMode.TRADE, accountId = vSame, assetId = kNewAsset, quantity = "0", price = "10", date = kToday), kData)	// ordre incohérent
		assertEquals("Choisissez un compte Bourse.", vTrade.account)
		assertEquals("Nommez le nouvel actif.", vTrade.asset)
		assertNotNull(vTrade.quantity)
		assertTrue(checkEntry(EntryForm(amount = "12,50", accountId = vSame, categoryId = kDemo.categories[0].id, label = "Pain", date = kToday), kData).isEmpty)
	}

	/*
		Aller-retour : chaque opération de la démonstration (hors soldes
		initiaux), mise en formulaire puis reconstruite, redonne la même
		opération (mêmes identifiants, montants, comptes, ordres).
	*/
	@Test
	fun editThenBuildIsIdentity()
	{
		val vTxs = kDemo.transactions.filter { it.kind != TransactionKind.OPENING }	// opérations modifiables
		assertTrue(vTxs.any { it.kind == TransactionKind.TRANSFER } && vTxs.any { it.kind == TransactionKind.TRADE })
		vTxs.forEach { inTx ->
			val vForm = editEntryForm(inTx, kDemo.categories)						// formulaire
			assertTrue("$inTx : ${checkEntry(vForm, kData)}", checkEntry(vForm, kData).isEmpty || vForm.mode != EntryMode.EXPENSE && vForm.mode != EntryMode.INCOME)
			assertEquals(inTx, buildEntry(vForm, kData))
		}
	}

	/*
		Libellé proposé quand le champ est vide : virement vers le compte
		d'arrivée, achat ou vente de l'actif (nouveau compris).
	*/
	@Test
	fun proposedLabels()
	{
		val vFrom = kDemo.accounts[0]												// compte de départ
		val vTo   = kDemo.accounts[2]												// compte d'arrivée
		val vTransfer = buildEntry(EntryForm(mode = EntryMode.TRANSFER, amount = "100", accountId = vFrom.id, toAccountId = vTo.id, date = kToday), kData)	// virement
		assertEquals("Virement vers ${vTo.name}", vTransfer.label)
		val vPea  = kDemo.accounts.first { it.name == "PEA" }						// compte Bourse
		val vBuy  = buildEntry(EntryForm(mode = EntryMode.TRADE, accountId = vPea.id, assetId = kNewAsset, newAsset = "ETF Asie", side = TradeSide.BUY, quantity = "2", price = "50", date = kToday), kData, inNewAssetId = "asie")	// achat
		assertEquals("Achat ETF Asie", vBuy.label)
		assertEquals("asie", vBuy.trades.single().assetId)
	}
}
