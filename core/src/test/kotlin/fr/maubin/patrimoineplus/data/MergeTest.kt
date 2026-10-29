package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.BudgetLineDto
import fr.maubin.patrimoineplus.data.backup.GoalDto
import fr.maubin.patrimoineplus.data.backup.TransactionDto
import fr.maubin.patrimoineplus.data.backup.validateBackup
import fr.maubin.patrimoineplus.data.sync.MergeSide
import fr.maubin.patrimoineplus.data.sync.mergeBackups
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import fr.maubin.patrimoineplus.data.sync.SyncPlan
import fr.maubin.patrimoineplus.data.sync.planSync
import fr.maubin.patrimoineplus.data.sync.ChangeCount
import fr.maubin.patrimoineplus.data.sync.HistoryAuthor
import fr.maubin.patrimoineplus.data.sync.countChanges
import fr.maubin.patrimoineplus.data.backup.HistoryDto
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.backup.DeviceLinkDto
import fr.maubin.patrimoineplus.data.sync.joinDevices

/*
	Fusion élément par élément à partir de la dernière version commune :
	ajouts des deux côtés gardés, changements d'un seul côté repris, même
	changement des deux côtés sans conflit ; même élément changé différemment,
	ou supprimé d'un côté et modifié de l'autre : conflit, tranché par le choix ;
	compte supprimé d'un côté mais utilisé par une opération ajoutée de l'autre :
	rétabli ; budget, objectifs et objectifs du mois ; résultat toujours valide.
*/
class MergeTest
{
	private val kBase = demoBackupFile()													// version commune

	/*
		Dépense courante de la démonstration, servant de modèle.
	*/
	private fun model(): TransactionDto = kBase.transactions.first { it.kind == "STANDARD" && it.postings.size == 1 && it.postings.single().amountCents < 0 && it.categoryId != null }

	/*
		Nouvelle dépense, copie du modèle.
		inId    : identifiant
		inLabel : libellé
	*/
	private fun expense(
		inId    : String,
		inLabel : String
	): TransactionDto = model().let { it.copy(id = inId, label = inLabel, postings = it.postings.map { inPosting -> inPosting.copy(id = "$inId-p") }) }

	/*
		Contenu avec une opération modifiée.
		inFile  : contenu
		inId    : opération
		inLabel : nouveau libellé
	*/
	private fun relabel(
		inFile  : BackupFile,
		inId    : String,
		inLabel : String
	): BackupFile = inFile.copy(transactions = inFile.transactions.map { if (it.id == inId) it.copy(label = inLabel, updatedAt = it.updatedAt + 1) else it })

	/*
		Vérifie qu'un contenu est valide.
		inFile : contenu
	*/
	private fun assertValid(inFile: BackupFile)
	{
		val vRead = validateBackup(inFile)														// vérification
		assertTrue("contenu refusé : $vRead", vRead is BackupRead.Valid)
	}

	/*
		Ajouts des deux côtés, modification d'un côté, suppression de l'autre :
		tout est gardé, sans conflit.
	*/
	@Test
	fun independentChangesAreKept()
	{
		val vOther  = kBase.transactions.first { it.id != model().id && it.kind == "STANDARD" }	// opération supprimée de l'autre côté
		val vMine   = relabel(kBase.copy(transactions = kBase.transactions + expense("t-pc", "Courses PC")), model().id, "Courses (corrigé)")	// PC : ajout et modification
		val vTheirs = kBase.copy(transactions = kBase.transactions.filter { it.id != vOther.id } + expense("t-tel", "Boulangerie"))	// téléphone : ajout et suppression
		val vResult = mergeBackups(kBase, vMine, vTheirs)										// fusion
		assertTrue(vResult.conflicts.isEmpty())
		val vIds    = vResult.file.transactions.map { it.id }.toSet()							// opérations fusionnées
		assertTrue("t-pc" in vIds && "t-tel" in vIds && vOther.id !in vIds)
		assertEquals("Courses (corrigé)", vResult.file.transactions.single { it.id == model().id }.label)
		assertEquals(2, vResult.merged)															// ajout et suppression du téléphone
		assertValid(vResult.file)
		assertEquals(syncFingerprint(vResult.file), syncFingerprint(mergeBackups(kBase, vTheirs, vMine).file))	// même résultat dans les deux sens
	}

	/*
		Même changement des deux côtés : pas de conflit ; aucun changement :
		résultat identique.
	*/
	@Test
	fun sameChangeIsNoConflict()
	{
		val vBoth = relabel(kBase, model().id, "Courses")											// même modification
		assertTrue(mergeBackups(kBase, vBoth, vBoth).conflicts.isEmpty())
		assertEquals(syncFingerprint(kBase), syncFingerprint(mergeBackups(kBase, kBase, kBase).file))
		assertEquals(kBase, mergeBackups(kBase, kBase, kBase).file)							// éléments d'origine, dans leur ordre
	}

	/*
		Même opération modifiée différemment : conflit ; le choix tranche.
	*/
	@Test
	fun conflictingEditsAreAsked()
	{
		val vMine   = relabel(kBase, model().id, "Courses Carrefour")							// PC
		val vTheirs = relabel(kBase, model().id, "Courses Lidl")									// téléphone
		val vResult = mergeBackups(kBase, vMine, vTheirs)										// fusion
		val vConflict = vResult.conflicts.single()												// conflit
		assertEquals("Opération", vConflict.kind)
		assertTrue(vConflict.mine!!.contains("Courses Carrefour"))
		assertTrue(vConflict.theirs!!.contains("Courses Lidl"))
		assertEquals("Courses Carrefour", vResult.file.transactions.single { it.id == model().id }.label)	// en attendant : version de cet appareil
		assertEquals("Courses Lidl", mergeBackups(kBase, vMine, vTheirs, mapOf(vConflict.key to MergeSide.THEIRS)).file.transactions.single { it.id == model().id }.label)
		assertTrue(mergeBackups(kBase, vMine, vTheirs, mapOf(vConflict.key to MergeSide.MINE)).conflicts.isEmpty())
	}

	/*
		Supprimée d'un côté, modifiée de l'autre : conflit (version supprimée
		décrite comme absente) ; garder la suppression la retire.
	*/
	@Test
	fun deleteAgainstEditIsAsked()
	{
		val vMine   = kBase.copy(transactions = kBase.transactions.filter { it.id != model().id })	// PC : supprimée
		val vTheirs = relabel(kBase, model().id, "Courses Lidl")									// téléphone : modifiée
		val vConflict = mergeBackups(kBase, vMine, vTheirs).conflicts.single()					// conflit
		assertNull(vConflict.mine)
		assertTrue(vConflict.theirs!!.contains("Courses Lidl"))
		assertTrue(mergeBackups(kBase, vMine, vTheirs, mapOf(vConflict.key to MergeSide.MINE)).file.transactions.none { it.id == model().id })
		assertTrue(mergeBackups(kBase, vMine, vTheirs, mapOf(vConflict.key to MergeSide.THEIRS)).file.transactions.any { it.id == model().id })
	}

	/*
		Compte supprimé (avec ses opérations) d'un côté, opération ajoutée sur
		ce compte de l'autre : le compte est rétabli, le résultat est valide.
	*/
	@Test
	fun usedAccountIsRestored()
	{
		val vAccount = model().postings.single().accountId										// compte de l'opération modèle
		val vName    = kBase.accounts.single { it.id == vAccount }.name							// son nom
		val vMine    = kBase.copy(
			accounts     = kBase.accounts.filter { it.id != vAccount },
			transactions = kBase.transactions.filter { inTx -> inTx.postings.none { it.accountId == vAccount } },
			statements   = kBase.statements.filter { it.accountId != vAccount },
			recurrences  = kBase.recurrences.filter { it.accountId != vAccount && it.toAccountId != vAccount },
			loans        = kBase.loans.filter { it.accountId != vAccount && it.payFrom != vAccount }
		)																						// PC : compte supprimé
		val vTheirs  = kBase.copy(transactions = kBase.transactions + expense("t-tel", "Boulangerie"))	// téléphone : opération ajoutée sur ce compte
		val vResult  = mergeBackups(kBase, vMine, vTheirs)										// fusion
		assertTrue(vResult.conflicts.isEmpty())
		assertTrue(vResult.file.accounts.any { it.id == vAccount })
		assertEquals(listOf("Compte « $vName »"), vResult.restored)
		assertTrue(vResult.file.transactions.any { it.id == "t-tel" })
		assertValid(vResult.file)
	}

	/*
		Budget, objectifs et objectifs du mois : fusion par élément ; un mois
		fixé des deux côtés différemment est un seul conflit.
	*/
	@Test
	fun plansAreMerged()
	{
		val vBase   = kBase.copy(budget = listOf(BudgetLineDto("b1", "INCOME", "Salaire", 300_000)), goals = listOf(GoalDto("*", "g1", "TASK", "Faire le point")))	// version commune
		val vMine   = vBase.copy(budget = vBase.budget + BudgetLineDto("b2", "EXPENSE", "Courses", 50_000, theme = "DAILY"),
			monthGoals = listOf(fr.maubin.patrimoineplus.data.backup.MonthGoalDto("*", "2026-10", "2026-10-01", "m1", "SAVING", 20_000)))	// PC
		val vTheirs = vBase.copy(goals = listOf(GoalDto("*", "g1", "TASK", "Faire le point", done = true)),
			monthGoals = listOf(fr.maubin.patrimoineplus.data.backup.MonthGoalDto("*", "2026-10", "2026-10-02", "m1", "SAVING", 25_000)))	// téléphone
		val vResult = mergeBackups(vBase, vMine, vTheirs)										// fusion
		assertEquals(listOf("b1", "b2"), vResult.file.budget.map { it.id })
		assertTrue(vResult.file.goals.single().done)
		assertEquals(listOf("mois/*/2026-10"), vResult.conflicts.map { it.key })
		assertEquals(25_000L, mergeBackups(vBase, vMine, vTheirs, mapOf("mois/*/2026-10" to MergeSide.THEIRS)).file.monthGoals.single().target)
	}
	/*
		Décision de synchronisation : export, import, fusion avec la version
		commune, choix par élément, version entière sans version commune.
	*/
	@Test
	fun planDecides()
	{
		val vFp     = syncFingerprint(kBase)													// empreinte commune
		val vMine   = kBase.copy(transactions = kBase.transactions + expense("t-pc", "Courses PC"))	// PC : ajout
		val vTheirs = kBase.copy(transactions = kBase.transactions + expense("t-tel", "Boulangerie"))	// téléphone : ajout
		assertTrue(planSync(vFp, kBase, vMine, kBase) is SyncPlan.Export)
		assertTrue(planSync(vFp, kBase, kBase, vTheirs) is SyncPlan.Import)
		assertTrue(planSync(vFp, kBase, kBase, kBase) is SyncPlan.Nothing)
		val vMerge  = planSync(vFp, kBase, vMine, vTheirs) as SyncPlan.Merge					// fusion
		assertTrue(vMerge.file.transactions.any { it.id == "t-pc" } && vMerge.file.transactions.any { it.id == "t-tel" })
		assertTrue(planSync(vFp, null, vMine, vTheirs) is SyncPlan.Whole)						// version commune inconnue
		val vAsk    = planSync(vFp, kBase, relabel(kBase, model().id, "A"), relabel(kBase, model().id, "B")) as SyncPlan.Ask	// même opération
		val vDone   = planSync(vFp, kBase, relabel(kBase, model().id, "A"), relabel(kBase, model().id, "B"), mapOf(vAsk.conflicts.single().key to MergeSide.THEIRS)) as SyncPlan.Merge	// choix fait
		assertEquals("B", vDone.file.transactions.single { it.id == model().id }.label)
	}
	/*
		Historique : changements comptés (ajouts, modifications, suppressions) ;
		l'envoi ajoute une entrée au nom de l'appareil, la fusion réunit les deux
		historiques ; l'historique ne change pas l'empreinte ; il traverse le
		classeur.
	*/
	@Test
	fun history()
	{
		val vFp     = syncFingerprint(kBase)													// empreinte commune
		val vMine   = relabel(kBase.copy(transactions = kBase.transactions + expense("t-pc", "Courses PC")), model().id, "Corrigé")	// PC : un ajout, une modification
		assertEquals(ChangeCount(1, 1, 0), countChanges(kBase, vMine))
		assertEquals("1 ajout, 1 modification", countChanges(kBase, vMine).label)
		val vPc     = HistoryAuthor("pc-1", "PC du bureau")										// auteur
		val vSent   = (planSync(vFp, kBase, vMine, kBase, inAuthor = vPc, inNow = 1_000) as SyncPlan.Export).file	// envoi
		assertEquals(listOf(HistoryDto("pc-1", "PC du bureau", 1_000, "1 ajout, 1 modification")), vSent.history)
		assertEquals(syncFingerprint(vMine), syncFingerprint(vSent))								// historique hors empreinte
		val vPhone  = kBase.copy(transactions = kBase.transactions + expense("t-tel", "Boulangerie"), history = listOf(HistoryDto("tel-1", "Pixel", 900, "1 ajout")))	// téléphone
		val vMerged = (planSync(vFp, kBase, vMine, vPhone, inAuthor = vPc, inNow = 1_000) as SyncPlan.Merge).file	// fusion
		assertEquals(listOf("PC du bureau", "Pixel"), vMerged.history.map { it.device })
		val vRead   = readBackupXlsx(java.io.ByteArrayInputStream(java.io.ByteArrayOutputStream().also { writeBackupXlsx(vMerged, it) }.toByteArray())) as BackupRead.Valid	// classeur relu
		assertEquals(vMerged.history, vRead.file.history)
		assertTrue(planSync(vFp, kBase, kBase, kBase, inAuthor = vPc, inNow = 2_000) is SyncPlan.Nothing)
	}
	/*
		Couples d'appareils : la ligne la plus récente l'emporte (retrait
		compris) ; réunis par la décision, hors de l'empreinte ; aller-retour
		par le classeur.
	*/
	@Test
	fun deviceLinks()
	{
		val vOld    = DeviceLinkDto("pc-1", "PC du bureau", "tel-1", "Pixel", 100, 200, updatedAt = 200)	// couple synchronisé
		val vGone   = vOld.copy(removed = true, updatedAt = 300)								// puis retiré
		val vOther  = DeviceLinkDto("pc-2", "PC du salon", "tel-1", "Pixel", 150, updatedAt = 150)	// autre PC
		assertEquals(listOf(vGone, vOther), joinDevices(listOf(vOld, vOther), listOf(vGone)))
		assertEquals(syncFingerprint(kBase), syncFingerprint(kBase.copy(devices = listOf(vOld))))
		val vFp     = syncFingerprint(kBase)													// empreinte commune
		val vMine   = kBase.copy(transactions = kBase.transactions + expense("t-pc", "Courses PC"), devices = listOf(vOld))	// PC
		val vSent   = (planSync(vFp, kBase, vMine, kBase.copy(devices = listOf(vOther))) as SyncPlan.Export).file	// envoi
		assertEquals(setOf("pc-1|tel-1", "pc-2|tel-1"), vSent.devices.map { it.key }.toSet())
		val vRead   = readBackupXlsx(java.io.ByteArrayInputStream(java.io.ByteArrayOutputStream().also { writeBackupXlsx(vSent, it) }.toByteArray())) as BackupRead.Valid	// classeur relu
		assertEquals(vSent.devices, vRead.file.devices)
	}
}
