package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.validateBackup
import fr.maubin.patrimoineplus.data.edit.DataEditor
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.data.edit.OpeningBalance
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
	Tests du moteur de modification sans base (appli PC) : mêmes règles que les
	dépôts Android. Après chaque modification acceptée, le contenu doit
	toujours passer la vérification complète d'un import ; un refus ne change
	rien.
*/
class DataEditorTest
{
	private val kNow    = Instant.parse("2026-10-12T09:00:00Z")					// instant des modifications
	private val kEditor = DataEditor(Clock.fixed(kNow, ZoneId.of("Europe/Paris")))	// moteur testé
	private val kDay    = LocalDate.of(2026, 10, 12)								// date du jour

	/*
		Contenu modifié, après avoir vérifié qu'il passe la vérification d'import.
		inResult : résultat d'une modification acceptée
	*/
	private fun done(inResult: EditResult): BackupFile
	{
		assertTrue("modification refusée : $inResult", inResult is EditResult.Done)
		val vFile = (inResult as EditResult.Done).file								// nouveau contenu
		val vRead = validateBackup(vFile)											// vérification d'import
		assertTrue("contenu incohérent : $vRead", vRead is BackupRead.Valid)
		return vFile
	}

	/*
		Vérifie un refus.
		inResult : résultat
	*/
	private fun refused(inResult: EditResult)
	{
		assertTrue("refus attendu : $inResult", inResult is EditResult.Refused)
	}

	/*
		Identifiant d'un compte de la démonstration.
		inFile : contenu
		inName : nom du compte
	*/
	private fun account(inFile: BackupFile, inName: String): String = inFile.accounts.single { it.name == inName }.id

	/*
		Comptes : création en fin de liste avec solde initial ; nom vide refusé ;
		type modifiable tant qu'il n'y a que le solde initial, verrouillé
		ensuite ; archivage ; ordre.
	*/
	@Test
	fun accounts()
	{
		val vBase = demoBackupFile()												// démonstration
		refused(kEditor.createAccount(vBase, "  ", AccountType.SAVINGS, null, 100, null))
		val vResult = kEditor.createAccount(vBase, " LEP ", AccountType.SAVINGS, " Banque ", 100, OpeningBalance(50_000, kDay))	// création
		val vFile = done(vResult)													// contenu avec le LEP
		val vId   = (vResult as EditResult.Done).id!!								// nouveau compte
		val vLep  = vFile.accounts.single { it.id == vId }						// compte créé
		assertEquals("LEP", vLep.name)
		assertEquals("Banque", vLep.institution)
		assertEquals(vBase.accounts.maxOf { it.sortOrder } + 1, vLep.sortOrder)
		assertEquals(kNow.toEpochMilli(), vLep.createdAt)
		assertEquals(OpeningBalance(50_000, kDay), kEditor.openingOf(vFile, vId))
		val vAsChecking = done(kEditor.updateAccount(vFile, vFile.toDataSet().accounts.single { it.id == vId }.copy(type = AccountType.CHECKING)))	// type changé
		assertEquals("CHECKING", vAsChecking.accounts.single { it.id == vId }.type)
		val vCourant = account(vBase, "Compte courant")							// compte avec opérations
		refused(kEditor.updateAccount(vBase, vBase.toDataSet().accounts.single { it.id == vCourant }.copy(type = AccountType.SAVINGS)))
		assertTrue(done(kEditor.setArchived(vBase, vCourant, true)).accounts.single { it.id == vCourant }.archived)
		val vOrder = vBase.accounts.sortedBy { it.sortOrder }.map { it.id }.reversed()	// ordre inversé
		assertEquals(vOrder, done(kEditor.reorderAccounts(vBase, vOrder)).accounts.sortedBy { it.sortOrder }.map { it.id })
	}

	/*
		Suppression d'un compte : simple refusée s'il a des opérations, acceptée
		s'il n'a que son solde initial (supprimé avec lui) ; définitive : toutes
		ses opérations (virements en entier) et ses relevés disparaissent.
	*/
	@Test
	fun accountDeletion()
	{
		val vBase    = demoBackupFile()											// démonstration
		val vLivret  = account(vBase, "Livret A")									// compte avec opérations et relevé
		refused(kEditor.deleteAccount(vBase, vLivret, inPermanent = false))
		val vFresh   = kEditor.createAccount(vBase, "Neuf", AccountType.CHECKING, null, 100, OpeningBalance(1_000, kDay)) as EditResult.Done	// compte neuf
		val vNoFresh = done(kEditor.deleteAccount(vFresh.file, vFresh.id!!, inPermanent = false))	// suppression simple
		assertTrue(vNoFresh.transactions.none { inTx -> inTx.postings.any { it.accountId == vFresh.id } })
		val vTransfers = vBase.transactions.filter { inTx -> inTx.kind == "TRANSFER" && inTx.postings.any { it.accountId == vLivret } }.map { it.id }	// virements du livret
		val vNoLivret  = done(kEditor.deleteAccount(vBase, vLivret, inPermanent = true))	// suppression définitive
		assertTrue(vNoLivret.transactions.none { it.id in vTransfers })
		assertTrue(vNoLivret.statements.none { it.accountId == vLivret })
		assertTrue(vNoLivret.accounts.none { it.id == vLivret })
	}

	/*
		Solde initial et positions initiales : création, modification, retrait à
		zéro ; position sur un compte Bourse (solde initial créé sans espèces),
		retrait de la dernière position (solde initial supprimé) ; refus sur un
		compte Courant.
	*/
	@Test
	fun openingAndPositions()
	{
		val vBase  = demoBackupFile()												// démonstration
		val vNew   = kEditor.createAccount(vBase, "Neuf", AccountType.CHECKING, null, 100, null) as EditResult.Done	// compte sans solde initial
		val vId    = vNew.id!!														// compte
		val vSet   = done(kEditor.setOpening(vNew.file, vId, OpeningBalance(2_000, kDay)))	// solde créé
		assertEquals(OpeningBalance(2_000, kDay), kEditor.openingOf(vSet, vId))
		val vMoved = done(kEditor.setOpening(vSet, vId, OpeningBalance(-500, kDay.minusDays(3))))	// solde modifié
		assertEquals(OpeningBalance(-500, kDay.minusDays(3)), kEditor.openingOf(vMoved, vId))
		assertNull(kEditor.openingOf(done(kEditor.setOpening(vMoved, vId, OpeningBalance(0, kDay))), vId))
		val vPea   = kEditor.createAccount(vBase, "PEA 2", AccountType.BROKERAGE, null, 100, null) as EditResult.Done	// compte Bourse neuf
		val vAsset = vBase.assets.first().id										// actif
		val vAdded = kEditor.addOpeningPosition(vPea.file, vPea.id!!, vAsset, 100_000, 800_000, kDay) as EditResult.Done	// position ajoutée
		val vWith  = done(vAdded)													// contenu avec la position
		assertEquals(OpeningBalance(0, kDay), kEditor.openingOf(vWith, vPea.id!!))
		assertNull(kEditor.openingOf(done(kEditor.removeOpeningPosition(vWith, vPea.id!!, vAdded.id!!)), vPea.id!!))
		refused(kEditor.addOpeningPosition(vBase, account(vBase, "Compte courant"), vAsset, 1, 1, kDay))
	}

	/*
		Catégories : création en fin de sa nature ; suppression d'une catégorie
		utilisée refusée sans remplacement ou avec une autre nature, acceptée
		avec une catégorie de même nature (opérations réaffectées).
	*/
	@Test
	fun categories()
	{
		val vBase    = demoBackupFile()												// démonstration
		val vCreated = kEditor.createCategory(vBase, "Cadeaux", "gift", CategoryKind.EXPENSE) as EditResult.Done	// nouvelle catégorie
		val vWith    = done(vCreated)												// contenu avec la catégorie
		assertEquals(vBase.categories.filter { it.kind == "EXPENSE" }.maxOf { it.sortOrder } + 1, vWith.categories.single { it.id == vCreated.id }.sortOrder)
		val vUsed    = vBase.categories.first { inCat -> inCat.kind == "EXPENSE" && vBase.transactions.any { it.categoryId == inCat.id } }	// catégorie utilisée
		val vIncome  = vBase.categories.first { it.kind == "INCOME" }				// autre nature
		refused(kEditor.deleteCategory(vBase, vUsed.id, null))
		refused(kEditor.deleteCategory(vBase, vUsed.id, vIncome.id))
		val vMoved   = done(kEditor.deleteCategory(vWith, vUsed.id, vCreated.id))	// suppression avec réaffectation
		assertTrue(vMoved.categories.none { it.id == vUsed.id })
		assertEquals(kEditor.categoryUsage(vBase, vUsed.id), kEditor.categoryUsage(vMoved, vCreated.id!!))
	}

	/*
		Opérations : création validée (horodatages), modification qui garde la
		date de création, refus d'une opération incohérente (rien ne change),
		suppression d'un virement en entier.
	*/
	@Test
	fun transactions()
	{
		val vBase    = demoBackupFile()												// démonstration
		val vCourant = account(vBase, "Compte courant")							// compte
		val vFood    = vBase.categories.first { it.kind == "EXPENSE" }.id			// catégorie de dépense
		val vTx      = Transaction("nouvelle", kDay, TransactionKind.STANDARD, "Boulangerie", vFood, postings = listOf(Posting("p1", vCourant, -450)))	// dépense
		val vSaved   = done(kEditor.saveTransaction(vBase, vTx))					// enregistrée
		val vDto     = vSaved.transactions.single { it.id == "nouvelle" }			// version enregistrée
		assertEquals(kNow.toEpochMilli(), vDto.createdAt)
		val vLater   = DataEditor(Clock.fixed(kNow.plusSeconds(60), ZoneId.of("Europe/Paris")))	// une minute plus tard
		val vEdited  = done(vLater.saveTransaction(vSaved, vTx.copy(label = "Boulangerie du coin")))	// modifiée
		val vEditDto = vEdited.transactions.single { it.id == "nouvelle" }		// version modifiée
		assertEquals(kNow.toEpochMilli(), vEditDto.createdAt)
		assertEquals(kNow.plusSeconds(60).toEpochMilli(), vEditDto.updatedAt)
		assertEquals(vBase.transactions.size + 1, vEdited.transactions.size)
		val vBad     = vTx.copy(id = "mauvaise", kind = TransactionKind.TRANSFER, categoryId = null)	// virement à une seule jambe
		refused(kEditor.saveTransaction(vBase, vBad))
		val vTransfer = vBase.transactions.first { it.kind == "TRANSFER" }		// virement
		val vNoTransfer = done(kEditor.deleteTransactions(vBase, listOf(vTransfer.id)))	// supprimé
		assertTrue(vNoTransfer.transactions.none { it.id == vTransfer.id })
	}

	/*
		Actifs, cours, relevés : actif créé et modifié (ISIN et symbole
		nettoyés) ; un cours par actif et par jour ; un relevé par compte et par
		jour ; suppression d'un relevé.
	*/
	@Test
	fun assetsPricesStatements()
	{
		val vBase    = demoBackupFile()												// démonstration
		val vCreated = kEditor.createAsset(vBase, "ETF Monde", " lu1681043599 ", " cw8.pa ") as EditResult.Done	// nouvel actif
		val vAsset   = done(vCreated).assets.single { it.id == vCreated.id }		// actif créé
		assertEquals("LU1681043599", vAsset.isin)
		assertEquals("CW8.PA", vAsset.ticker)
		assertNull(done(kEditor.updateAsset(vCreated.file, vCreated.id!!, "ETF Monde", "", "")).assets.single { it.id == vCreated.id }.ticker)
		val vOnce    = done(kEditor.putPrices(vCreated.file, kDay, mapOf(vCreated.id!! to 7_000_000L)))	// premier cours
		val vTwice   = done(kEditor.putPrices(vOnce, kDay, mapOf(vCreated.id!! to 7_100_000L)))	// même jour
		assertEquals(listOf(7_100_000L), vTwice.prices.filter { it.assetId == vCreated.id }.map { it.unitPrice })
		val vLivret  = account(vBase, "Livret A")									// compte
		val vSt1     = done(kEditor.putStatement(vBase, vLivret, kDay, 1_000_000))	// relevé
		val vSt2     = kEditor.putStatement(vSt1, vLivret, kDay, 1_000_500) as EditResult.Done	// même jour
		assertEquals(listOf(1_000_500L), done(vSt2).statements.filter { it.accountId == vLivret && it.date == kDay.toString() }.map { it.balanceCents })
		assertTrue(done(kEditor.deleteStatement(vSt2.file, vSt2.id!!)).statements.none { it.id == vSt2.id })
	}
}
