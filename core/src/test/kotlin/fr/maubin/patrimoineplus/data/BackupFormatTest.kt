package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.AccountDto
import fr.maubin.patrimoineplus.data.backup.AssetDto
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.CategoryDto
import fr.maubin.patrimoineplus.data.backup.PostingDto
import fr.maubin.patrimoineplus.data.backup.PriceDto
import fr.maubin.patrimoineplus.data.backup.StatementDto
import fr.maubin.patrimoineplus.data.backup.TradeDto
import fr.maubin.patrimoineplus.data.backup.TransactionDto
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.backup.kBackupFormat
import fr.maubin.patrimoineplus.data.backup.kBackupVersion
import fr.maubin.patrimoineplus.data.demo.buildDemoData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/*
	Tests du format d'export : aller-retour complet et refus de tout fichier
	incohérent (rien n'est alors importé).
*/
class BackupFormatTest
{
	/*
		Vérifie qu'un fichier est refusé avec un message contenant un mot donné.
		inText : contenu du fichier
		inWord : mot attendu dans le message
	*/
	private fun assertRefused(
		inText : String,
		inWord : String
	)
	{
		val vRead = decodeBackup(inText)											// lecture
		assertTrue("refus attendu : $vRead", vRead is BackupRead.Invalid)
		assertTrue((vRead as BackupRead.Invalid).message, vRead.message.contains(inWord))
	}

	/*
		Aller-retour : un export relu est identique, au champ près.
	*/
	@Test
	fun roundTrip()
	{
		val vFile = demoBackupFile()														// fichier d'origine
		val vRead = decodeBackup(encodeBackup(vFile))								// fichier relu
		assertEquals(BackupRead.Valid(vFile), vRead)
		assertTrue(vFile.summary.startsWith("9 compte(s)"))
	}

	/*
		Refus : JSON cassé, autre format, version plus récente.
	*/
	@Test
	fun refusesForeignOrNewerFiles()
	{
		assertRefused("{ pas du json", "illisible")
		assertRefused(encodeBackup(demoBackupFile().copy(format = "autre")), "n’est pas un export")
		assertRefused(encodeBackup(demoBackupFile().copy(version = kBackupVersion + 1)), "plus récente")
	}

	/*
		Refus : valeur inconnue, date illisible, identifiant en double.
	*/
	@Test
	fun refusesBadValues()
	{
		val vFile = demoBackupFile()														// fichier valide
		assertRefused(encodeBackup(vFile.copy(accounts = vFile.accounts.mapIndexed { i, a -> if (i == 0) a.copy(type = "LIVRET") else a })), "Valeur inconnue")
		assertRefused(encodeBackup(vFile.copy(statements = vFile.statements.map { it.copy(date = "30/09/2026") })), "Date illisible")
		assertRefused(encodeBackup(vFile.copy(categories = vFile.categories + vFile.categories.first())), "double")
	}

	/*
		Refus : référence vers un compte ou un actif absent, opération qui viole
		les règles de saisie (virement déséquilibré).
	*/
	@Test
	fun refusesBrokenReferencesAndRules()
	{
		val vFile = demoBackupFile()														// fichier valide
		assertRefused(encodeBackup(vFile.copy(accounts = vFile.accounts.drop(1))), "Compte inconnu")
		assertRefused(encodeBackup(vFile.copy(assets = emptyList(), prices = emptyList())), "Actif inconnu")
		val vBroken = vFile.transactions.map { inTx ->
			if (inTx.kind == "TRANSFER") inTx.copy(postings = listOf(inTx.postings[0], inTx.postings[1].copy(amountCents = inTx.postings[1].amountCents + 1))) else inTx
		}																			// virements déséquilibrés
		assertRefused(encodeBackup(vFile.copy(transactions = vBroken)), "Opération invalide")
	}
}
