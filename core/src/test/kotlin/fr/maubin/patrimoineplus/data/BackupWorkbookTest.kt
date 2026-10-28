package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.backup.xlsx.readXlsx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
	Tests de la sauvegarde au format classeur : aller-retour exact, onglets
	par type de compte, lecture d'un classeur réenregistré par un tableur, et
	refus des fichiers incomplets.
*/
class BackupWorkbookTest
{
	/*
		Classeur écrit en mémoire.
		inFile : contenu
	*/
	private fun workbook(inFile: BackupFile): ByteArray =
		ByteArrayOutputStream().also { writeBackupXlsx(inFile, it) }.toByteArray()

	/*
		Forme comparable d'un contenu : listes triées par identifiant (l'ordre
		des lignes du classeur n'est pas celui de la base).
		inFile : contenu
	*/
	private fun normalized(inFile: BackupFile): BackupFile =
		inFile.copy(
			accounts     = inFile.accounts.sortedBy { it.id },
			categories   = inFile.categories.sortedBy { it.id },
			assets       = inFile.assets.sortedBy { it.id },
			transactions = inFile.transactions.sortedBy { it.id }.map { inTx -> inTx.copy(postings = inTx.postings.sortedBy { it.id }, trades = inTx.trades.sortedBy { it.id }) },
			statements   = inFile.statements.sortedBy { it.id },
			prices       = inFile.prices.sortedBy { it.id },
			recurrences  = inFile.recurrences.sortedBy { it.id },
			loans        = inFile.loans.sortedBy { it.id }
		)

	/*
		Aller-retour : le classeur relu redonne exactement la démonstration
		(montants, quantités, cours, dates, horodatages, identifiants). Le
		fichier est aussi déposé dans build/ pour être ouvert à la main.
	*/
	@Test
	fun roundTripIsExact()
	{
		val vFile  = demoBackupFile()											// démonstration
		val vBytes = workbook(vFile)											// classeur
		File("build/outputs/sauvegarde-demo.xlsx").apply { parentFile?.mkdirs() }.writeBytes(vBytes)
		val vRead  = readBackupXlsx(ByteArrayInputStream(vBytes))				// relecture vérifiée
		assertTrue("lecture refusée : $vRead", vRead is BackupRead.Valid)
		assertEquals(normalized(vFile), normalized((vRead as BackupRead.Valid).file))
	}

	/*
		Onglets : un par type de compte, puis les données de référence ; un
		virement entre un compte Courant et un livret apparaît sur les deux
		onglets, mais n'est relu qu'une fois.
	*/
	@Test
	fun sheetsPerAccountTypeAndTransfersMerged()
	{
		val vFile   = demoBackupFile()											// démonstration
		val vSheets = readXlsx(ByteArrayInputStream(workbook(vFile)))			// onglets relus
		assertEquals(listOf("Courant", "Épargne", "Bourse", "Immobilier", "Collection", "Crédit", "Comptes", "Catégories", "Actifs", "Cours", "Relevés", "Récurrences", "Crédits", "Profils", "Thèmes", "Budget", "Objectifs", "Objectifs du mois", "Fichier"), vSheets.map { it.name })
		val vTypes    = vFile.accounts.associate { it.id to it.type }			// type de chaque compte
		val vTransfer = vFile.transactions.first { inTx -> inTx.kind == "TRANSFER" && inTx.postings.map { vTypes[it.accountId] }.toSet() == setOf("CHECKING", "SAVINGS") }	// virement Courant → Épargne
		val vOnSheets = vSheets.filter { inSheet -> inSheet.rows.any { it["ID opération"] == vTransfer.id } }.map { it.name }	// onglets qui le montrent
		assertEquals(listOf("Courant", "Épargne"), vOnSheets)
		val vRead = readBackupXlsx(ByteArrayInputStream(workbook(vFile))) as BackupRead.Valid	// relecture
		assertEquals(1, vRead.file.transactions.count { it.id == vTransfer.id })
		assertEquals(2, vRead.file.transactions.single { it.id == vTransfer.id }.postings.size)
	}

	/*
		Classeur réenregistré par un tableur : chaînes partagées, cellules sans
		référence, colonnes dans un autre ordre, nombres et dates en texte à la
		française. Il doit être lu comme l'original.
	*/
	@Test
	fun readsSpreadsheetStyleFile()
	{
		val vShared = listOf("Date", "Libellé", "ID opération", "Montant", "Compte")	// chaînes partagées
		val vSheet  = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" +
			"""<row r="1"><c t="s"><v>0</v></c><c t="s"><v>1</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c></row>""" +
			"""<row r="2"><c><v>46304</v></c><c t="inlineStr"><is><t>Courses</t></is></c><c r="D2"><v>-12.5</v></c><c r="E2" t="str"><v>Compte courant</v></c></row>""" +
			"""<row r="3"><c t="str"><v>09/10/2026</v></c><c t="str"><v>Loyer</v></c><c r="D3" t="str"><v>-1 234,56 €</v></c></row>""" +
			"</sheetData></worksheet>"											// feuille au style tableur
		val vBytes  = ByteArrayOutputStream().also { inOut ->
			ZipOutputStream(inOut).use { inZip ->
				/*
					Ajoute une partie.
					inPath : chemin
					inXml  : contenu
				*/
				fun put(inPath: String, inXml: String)
				{
					inZip.putNextEntry(ZipEntry(inPath))
					inZip.write(inXml.toByteArray())
					inZip.closeEntry()
				}
				put("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Courant" sheetId="1" r:id="rId7"/></sheets></workbook>""")
				put("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Type="worksheet" Target="/xl/worksheets/feuille.xml"/></Relationships>""")
				put("xl/sharedStrings.xml", """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" + vShared.joinToString("") { "<si><r><t>${it.take(2)}</t></r><r><t>${it.drop(2)}</t></r></si>" } + "</sst>")
				put("xl/worksheets/feuille.xml", vSheet)
			}
		}.toByteArray()															// classeur au style tableur
		val vRows = readXlsx(ByteArrayInputStream(vBytes)).single().rows		// lignes lues
		assertEquals("46304", vRows[0]["Date"])
		assertEquals("Courses", vRows[0]["Libellé"])
		assertEquals("-12.5", vRows[0]["Montant"])
		assertEquals("Compte courant", vRows[0]["Compte"])
		assertEquals("-1 234,56 €", vRows[1]["Montant"])
		assertEquals(java.time.LocalDate.of(2026, 10, 9), fr.maubin.patrimoineplus.data.backup.date(vRows[0]["Date"]!!))
		assertEquals(java.time.LocalDate.of(2026, 10, 9), fr.maubin.patrimoineplus.data.backup.date(vRows[1]["Date"]!!))
		assertEquals(java.math.BigDecimal("-1234.56"), fr.maubin.patrimoineplus.data.backup.decimal(vRows[1]["Montant"]!!))
	}

	/*
		Refus : fichier qui n'est pas un classeur, onglet manquant, signature
		étrangère ; rien n'est alors importé.
	*/
	@Test
	fun refusesForeignOrIncompleteFiles()
	{
		val vNotXlsx = readBackupXlsx(ByteArrayInputStream("pas un classeur".toByteArray()))	// texte brut
		assertTrue(vNotXlsx is BackupRead.Invalid)
		val vForeign = readBackupXlsx(ByteArrayInputStream(workbook(demoBackupFile().copy(format = "autre-appli"))))	// signature étrangère
		assertTrue((vForeign as BackupRead.Invalid).message.contains("PatrimoinePlus"))
		val vNoFile  = readBackupXlsx(ByteArrayInputStream(workbook(demoBackupFile()).let { stripSheet(it, "Fichier") }))	// sans page Fichier
		assertTrue((vNoFile as BackupRead.Invalid).message.contains("Fichier"))
	}

	/*
		Retire un onglet d'un classeur (en le renommant dans workbook.xml).
		inBytes : classeur
		inName  : onglet à retirer
	*/
	private fun stripSheet(
		inBytes : ByteArray,
		inName  : String
	): ByteArray
	{
		val vOut = ByteArrayOutputStream()										// classeur modifié
		java.util.zip.ZipInputStream(ByteArrayInputStream(inBytes)).use { inZip ->
			ZipOutputStream(vOut).use { inNew ->
				var vEntry = inZip.nextEntry										// partie courante
				while (vEntry != null)
				{
					var vData = inZip.readBytes()									// contenu
					if (vEntry.name == "xl/workbook.xml")
					{
						vData = String(vData).replace("name=\"$inName\"", "name=\"Autre\"").toByteArray()
					}
					inNew.putNextEntry(ZipEntry(vEntry.name))
					inNew.write(vData)
					inNew.closeEntry()
					vEntry = inZip.nextEntry
				}
			}
		}
		return vOut.toByteArray()
	}
}
