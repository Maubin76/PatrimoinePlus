package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.backupFileOf
import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.data.edit.DataEditor
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.data.edit.OpeningBalance
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.transfer
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.rules.TransactionValidator
import fr.maubin.patrimoineplus.domain.rules.ValidationError
import fr.maubin.patrimoineplus.ui.property.SaleForm
import fr.maubin.patrimoineplus.ui.property.buildPurchase
import fr.maubin.patrimoineplus.ui.property.buildSale
import fr.maubin.patrimoineplus.ui.property.checkPurchase
import fr.maubin.patrimoineplus.ui.property.checkSale
import fr.maubin.patrimoineplus.ui.property.editPurchaseForm
import fr.maubin.patrimoineplus.ui.property.editSaleForm
import fr.maubin.patrimoineplus.ui.property.newPurchaseForm
import fr.maubin.patrimoineplus.ui.property.newSaleForm
import fr.maubin.patrimoineplus.ui.property.saleGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Comptes de biens (Immobilier, Collection) : achat déjà possédé ou payé
	depuis un compte, vente avec ou sans versement, valeur au prix d'achat,
	plus-value dans le patrimoine, règles de saisie, suppression, fichier.
*/
class PropertyTest
{
	private val kHome    = Account("maison", "Résidence principale", AccountType.REAL_ESTATE, sharePercent = 50)	// immobilier à 50 %
	private val kObjects = Account("objets", "Collection", AccountType.COLLECTIBLE)							// objets de collection
	private val kToday   = d("2026-10-12")																	// date du jour

	/*
		Achat construit par la saisie.
		inAccount : compte de biens
		inName    : nom
		inDate    : date
		inPrice   : prix (texte)
		inPayer   : compte payeur ; null : déjà possédé
	*/
	private fun buy(inAccount: Account, inName: String, inDate: String, inPrice: String, inPayer: Account?): Transaction =
		buildPurchase(newPurchaseForm(inAccount.id, d(inDate)).copy(name = inName, price = inPrice, paidFrom = inPayer?.id))

	private val kFlat   = buy(kHome, "Appartement", "2019-06-15", "240000", null)							// déjà possédé
	private val kWatch  = buy(kObjects, "Montre", "2026-03-10", "4200", kChecking)						// payée depuis le courant
	private val kVinyls = buy(kObjects, "Vinyles", "2026-01-05", "600", null)							// déjà possédés
	private val kSale   = buildSale(SaleForm(purchaseId = kVinyls.id, date = d("2026-09-20"), price = "850", soldTo = kChecking.id), item(kVinyls))	// vinyles vendus 850 €

	private val kData = DataSet(
		listOf(kChecking, kLivret, kHome, kObjects), listOf(kGroceries), emptyList(),
		listOf(opening("2026-01-01", kChecking, eur(10_000.0)), kFlat, kWatch, kVinyls, kSale),
		emptyList(), emptyList()
	)																					// données

	/*
		Bien d'un achat, sans vente.
		inTx : achat
	*/
	private fun item(inTx: Transaction): PropertyItem = propertyItems(listOf(inTx), listOf(kChecking, kHome, kObjects)).single()

	/*
		Règles de saisie, sur toutes les autres opérations.
		inTx : opération vérifiée
	*/
	private fun errors(inTx: Transaction): List<ValidationError>
	{
		val vValidator = TransactionValidator(kData.accounts.associateBy { it.id }, kData.categories.associateBy { it.id }, emptyMap())	// règles
		return vValidator.validate(inTx, Ledger(kData.transactions.filter { it.id != inTx.id }), kData.transactions.firstOrNull { it.id == inTx.id })
	}

	/*
		Valeur au prix d'achat des biens détenus (part détenue appliquée au
		patrimoine) ; un achat payé depuis un compte ne change pas le
		patrimoine ; la vente sort le bien et verse son prix : la plus-value
		apparaît dans le patrimoine.
	*/
	@Test
	fun valueAndWealth()
	{
		val vLedger = Ledger(kData.transactions)											// grand livre
		val vWealth = wealthAt(kData.accounts, vLedger, kToday, emptyList())				// patrimoine aujourd'hui
		assertEquals(eur(240_000.0), vWealth.accounts.single { it.account.id == kHome.id }.valueCents)
		assertEquals(eur(120_000.0), vWealth.accounts.single { it.account.id == kHome.id }.countedCents)
		assertEquals(eur(4_200.0), vWealth.accounts.single { it.account.id == kObjects.id }.valueCents)		// vinyles vendus
		assertEquals(eur(10_000.0 - 4_200.0 + 850.0), vLedger.balanceAt(kChecking.id, kToday))
		val vBefore = wealthAt(kData.accounts, vLedger, d("2026-09-19"), emptyList()).totalCents	// veille de la vente
		assertEquals(eur(250.0), vWealth.totalCents - vBefore)							// plus-value de 250 €
		val vBuyDay = wealthAt(kData.accounts, vLedger, d("2026-03-10"), emptyList()).totalCents	// jour de l'achat de la montre
		assertEquals(vBuyDay, wealthAt(kData.accounts, vLedger, d("2026-03-09"), emptyList()).totalCents)
	}

	/*
		Liste des biens : plus récent d'abord, payeur, vente, plus-value ;
		détention à une date.
	*/
	@Test
	fun items()
	{
		val vItems = propertyItems(kData.transactions, kData.accounts, kObjects.id)		// objets
		assertEquals(listOf("Montre", "Vinyles"), vItems.map { it.name })
		assertEquals(kChecking.id, vItems[0].paidFrom)
		assertNull(vItems[0].disposal)
		assertEquals(eur(850.0), vItems[1].salePrice)
		assertEquals(eur(250.0), vItems[1].gain)
		assertEquals(kChecking.id, vItems[1].soldTo)
		assertTrue(vItems[1].heldAt(d("2026-09-19")))
		assertTrue(!vItems[1].heldAt(d("2026-09-20")))
		assertEquals(3, propertyItems(kData.transactions, kData.accounts).size)
	}

	/*
		Règles : les opérations du jeu sont valides ; un compte de biens ne
		reçoit ni dépense ni virement ; un achat se fait sur un compte de biens,
		au prix positif, le payeur débité du même prix ; une vente vise un achat
		connu, non vendu, à une date au moins égale, au prix d'achat ; un achat
		vendu ne change plus de prix.
	*/
	@Test
	fun rules()
	{
		kData.transactions.forEach { assertEquals("${it.label}", emptyList<ValidationError>(), errors(it)) }
		assertTrue(ValidationError.PropertyOnly in errors(standard("2026-10-01", kObjects, eur(-10.0), kGroceries)))
		assertTrue(ValidationError.PropertyOnly in errors(transfer("2026-10-01", kChecking, kObjects, eur(10.0))))
		assertTrue(ValidationError.NotProperty in errors(buy(kLivret, "Faux", "2026-10-01", "10", null)))
		assertTrue(ValidationError.NotProperty in errors(buy(kObjects, "Payé par un bien", "2026-10-01", "10", kHome)))
		assertTrue(ValidationError.InvalidPurchase in errors(kWatch.copy(postings = listOf(kWatch.postings[0].copy(amountCents = eur(-4_000.0)), kWatch.postings[1]))))
		assertTrue(ValidationError.AlreadySold in errors(kSale.copy(id = "autre-vente")))
		assertTrue(ValidationError.MissingPurchase in errors(kSale.copy(linkedId = "inconnu")))
		assertTrue(ValidationError.SaleBeforePurchase in errors(kSale.copy(date = d("2026-01-01"))))
		assertTrue(ValidationError.InvalidSale in errors(kSale.copy(postings = listOf(kSale.postings[0], kSale.postings[1].copy(amountCents = eur(-500.0))))))
		assertEquals(listOf(ValidationError.SoldPurchase(d("2026-09-20"))), errors(kVinyls.copy(postings = listOf(kVinyls.postings[0].copy(amountCents = eur(700.0))))))
		assertEquals(emptyList<ValidationError>(), errors(kVinyls.copy(label = "Vinyles rares")))	// renommer reste possible
	}

	/*
		Saisie : champs obligatoires, aller-retour exact d'un achat et d'une
		vente modifiés, plus-value affichée, vente avant l'achat refusée, prix
		nul seulement sans versement.
	*/
	@Test
	fun forms()
	{
		val vEmpty = checkPurchase(newPurchaseForm(null, kToday), kData.accounts)			// saisie vide
		assertTrue(vEmpty.account != null && vEmpty.name != null && vEmpty.price != null)
		val vItems = propertyItems(kData.transactions, kData.accounts)						// biens
		vItems.forEach { inItem ->
			assertEquals(inItem.acquisition, buildPurchase(editPurchaseForm(inItem)))
			inItem.disposal?.let { assertEquals(it, buildSale(editSaleForm(inItem), inItem)) }
		}
		val vWatch = vItems.single { it.name == "Montre" }									// montre détenue
		val vSale  = newSaleForm(vWatch, kToday).copy(price = "5000")						// vente saisie
		assertEquals(kChecking.id, vSale.soldTo)
		assertEquals(eur(800.0), saleGain(vSale, vWatch))
		assertTrue(checkSale(vSale, vWatch, kData.accounts).isEmpty)
		assertTrue(checkSale(vSale.copy(date = d("2026-01-01")), vWatch, kData.accounts).date != null)
		assertTrue(checkSale(vSale.copy(price = "0"), vWatch, kData.accounts).price != null)
		assertTrue(checkSale(vSale.copy(price = "0", soldTo = null), vWatch, kData.accounts).isEmpty)	// don
		assertEquals(1, buildSale(vSale.copy(price = "0", soldTo = null), vWatch).postings.size)
	}

	/*
		Modifications sur le fichier : pas de solde initial sur un compte de
		biens ; supprimer un achat vendu sans sa vente est refusé ; les deux
		ensemble passent ; le fichier v3 se relit à l'identique.
	*/
	@Test
	fun editorAndFile()
	{
		val vEditor = DataEditor(Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris")))	// moteur
		val vFile   = backupFileOf(kData)													// fichier
		assertTrue(vEditor.createAccount(vFile, "Maison", AccountType.REAL_ESTATE, null, 100, OpeningBalance(eur(1.0), kToday)) is EditResult.Refused)
		assertTrue(vEditor.setOpening(vFile, kObjects.id, OpeningBalance(eur(1.0), kToday)) is EditResult.Refused)
		val vAlone = vEditor.deleteTransactions(vFile, listOf(kVinyls.id))					// achat seul
		assertTrue(vAlone is EditResult.Refused && "vendu" in (vAlone as EditResult.Refused).messages.single())
		assertTrue(vEditor.deleteTransactions(vFile, listOf(kVinyls.id, kSale.id)) is EditResult.Done)
		assertEquals(BackupRead.Valid(vFile), decodeBackup(encodeBackup(vFile)))
		assertEquals(kSale.linkedId, (decodeBackup(encodeBackup(vFile)) as BackupRead.Valid).file.transactions.single { it.id == kSale.id }.linkedId)
	}
}
