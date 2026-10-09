package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.AccountDto
import fr.maubin.patrimoineplus.data.sync.SyncAction
import fr.maubin.patrimoineplus.data.sync.decideSync
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/*
	Tests de la logique de synchronisation : empreinte du contenu et décision
	(exporter, importer, mémoriser, conflit) pour chaque situation.
*/
class SyncLogicTest
{
	/*
		Empreinte : indépendante de l'ordre des listes et de la date d'export ;
		change dès qu'une donnée change.
	*/
	@Test
	fun fingerprintFollowsContentOnly()
	{
		val vFile = demoBackupFile()											// démonstration
		val vSame = vFile.copy(
			exportedAt   = "2030-01-01T00:00:00Z",
			accounts     = vFile.accounts.reversed(),
			transactions = vFile.transactions.reversed().map { it.copy(postings = it.postings.reversed()) }
		)																		// même contenu, autre ordre et autre date
		assertEquals(syncFingerprint(vFile), syncFingerprint(vSame))
		val vEdited = vFile.copy(accounts = vFile.accounts.map { if (it == vFile.accounts.first()) it.copy(name = "Renommé") else it })	// un compte renommé
		assertNotEquals(syncFingerprint(vFile), syncFingerprint(vEdited))
		assertNotEquals(syncFingerprint(vFile), syncFingerprint(vFile.copy(accounts = vFile.accounts + AccountDto("x", "X", "CHECKING", null, 100, 9, false, 1, 2))))
	}

	/*
		Décision, cas par cas : L = dernière synchronisation, l = local, f = fichier.
	*/
	@Test
	fun decisionForEverySituation()
	{
		// Pas de fichier : exporter, sauf données vides.
		assertEquals(SyncAction.EXPORT, decideSync(null, "a", null, inLocalEmpty = false))
		assertEquals(SyncAction.NOTHING, decideSync(null, "a", null, inLocalEmpty = true))
		assertEquals(SyncAction.EXPORT, decideSync("a", "a", null, inLocalEmpty = false))
		// Première synchronisation avec un fichier.
		assertEquals(SyncAction.RECORD, decideSync(null, "a", "a", inLocalEmpty = false))
		assertEquals(SyncAction.IMPORT, decideSync(null, "vide", "f", inLocalEmpty = true))
		assertEquals(SyncAction.CONFLICT, decideSync(null, "a", "f", inLocalEmpty = false))
		// Ensuite.
		assertEquals(SyncAction.NOTHING, decideSync("a", "a", "a", inLocalEmpty = false))
		assertEquals(SyncAction.EXPORT, decideSync("a", "b", "a", inLocalEmpty = false))
		assertEquals(SyncAction.IMPORT, decideSync("a", "a", "c", inLocalEmpty = false))
		assertEquals(SyncAction.RECORD, decideSync("a", "b", "b", inLocalEmpty = false))
		assertEquals(SyncAction.CONFLICT, decideSync("a", "b", "c", inLocalEmpty = false))
		// Données locales effacées volontairement : l'effacement se propage.
		assertEquals(SyncAction.EXPORT, decideSync("a", "vide", "a", inLocalEmpty = true))
	}
}
