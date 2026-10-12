package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.bank.BankLine
import fr.maubin.patrimoineplus.data.bank.BankRead
import fr.maubin.patrimoineplus.data.bank.bankEntryForm
import fr.maubin.patrimoineplus.data.bank.cleanLabel
import fr.maubin.patrimoineplus.data.bank.readBankCsv
import fr.maubin.patrimoineplus.data.bank.reconcile
import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLeisure
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.transfer
import fr.maubin.patrimoineplus.ui.entry.EntryData
import fr.maubin.patrimoineplus.ui.entry.EntryMode
import fr.maubin.patrimoineplus.ui.entry.buildEntry
import fr.maubin.patrimoineplus.ui.entry.checkEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

/*
	Relevés exportés par les banques (CSV) et rapprochement avec les
	opérations saisies. Les exemples reprennent la forme des exports des
	grandes banques françaises (séparateur, en-têtes, montants) avec des
	données inventées.
*/
class BankStatementTest
{
	/*
		Lignes d'un relevé lu ; échec si le fichier est refusé.
		inText    : contenu du fichier
		inCharset : encodage du fichier
	*/
	private fun lines(
		inText    : String,
		inCharset : Charset = Charsets.UTF_8
	): List<BankLine>
	{
		val vRead = readBankCsv(inText.toByteArray(inCharset))							// relevé lu
		assertTrue("relevé refusé : $vRead", vRead is BankRead.Valid)
		return (vRead as BankRead.Valid).lines
	}

	/*
		Colonnes Débit et Crédit, lignes d'informations avant le tableau,
		Windows-1252, montants avec espace des milliers et virgule.
	*/
	@Test
	fun debitCreditWithPreamble()
	{
		val vLines = lines(
			"Téléchargement du 12/10/2026;\nCompte de dépôt n° 000;\nSolde au 12/10/2026 : 1 234,56 €;\n\n" +
			"Date;Libellé;Débit euros;Crédit euros;\n" +
			"10/10/2026;\"CB CARREFOUR MARKET 09/10\";54,90;;\n" +
			"05/10/2026;\"VIR SALAIRE ENTREPRISE\";;2 150,00;\n" +
			"03/10/2026;\"PRLV SEPA EDF\";-80,00;;\n",
			Charset.forName("windows-1252")
		)																				// lignes lues
		assertEquals(listOf(-5_490L, 215_000L, -8_000L), vLines.map { it.amountCents })
		assertEquals(d("2026-10-10"), vLines.first().date)
		assertEquals("CB CARREFOUR MARKET 09/10", vLines.first().label)
	}

	/*
		Une colonne de montant signé, libellé court et libellé détaillé,
		séparateur « ; », date de valeur ignorée.
	*/
	@Test
	fun signedAmountAndValueDate()
	{
		val vLines = lines(
			"Date operation;Date valeur;Libelle court;Type operation;Libelle operation;Montant operation\n" +
			"11/10/2026;12/10/2026;FACTURE CARTE;CARTE;DU 101026 BOULANGERIE PAUL;-4,20\n" +
			"02/10/2026;02/10/2026;REMBOURSEMENT;VIREMENT;CPAM PARIS;17,50\n"
		)																				// lignes lues
		assertEquals(listOf(-420L, 1_750L), vLines.map { it.amountCents })
		assertEquals(d("2026-10-11"), vLines.first().date)
		assertTrue(vLines.first().label.contains("BOULANGERIE PAUL"))
	}

	/*
		Export à virgules et dates ISO, montants à point décimal, guillemets
		avec virgule dans un libellé.
	*/
	@Test
	fun commaSeparatedIsoDates()
	{
		val vLines = lines(
			"dateOp,dateVal,label,category,amount\n" +
			"2026-10-08,2026-10-09,\"AMAZON, PAYMENTS\",Achats,-23.99\n" +
			"2026-10-01,2026-10-01,VIR LIVRET A,Epargne,-500.00\n"
		)																				// lignes lues
		assertEquals(listOf(-2_399L, -50_000L), vLines.map { it.amountCents })
		assertEquals("AMAZON, PAYMENTS", vLines.first().label)
	}

	/*
		Fichiers refusés : sans tableau reconnaissable, ou sans opération.
	*/
	@Test
	fun refusesUnknownFiles()
	{
		assertTrue(readBankCsv("bonjour\nceci n'est pas un relevé".toByteArray()) is BankRead.Invalid)
		assertTrue(readBankCsv("Date;Libellé;Montant\n".toByteArray()) is BankRead.Invalid)
		assertTrue(readBankCsv(ByteArray(0)) is BankRead.Invalid)
	}

	/*
		Rapprochement : montant égal et date proche (cinq jours) ; chaque
		opération ne sert qu'une fois, la plus proche en date d'abord ; lignes
		manquantes et opérations en trop ; soldes initiaux ignorés.
	*/
	@Test
	fun matchesByAmountAndCloseDate()
	{
		val vShop1 = standard("2026-10-03", kChecking, eur(-54.90), kGroceries).copy(label = "Courses Carrefour")	// saisie le jour de l'achat
		val vShop2 = standard("2026-10-09", kChecking, eur(-54.90), kGroceries).copy(label = "Courses Carrefour")	// même montant, autre semaine
		val vExtra = standard("2026-10-07", kChecking, eur(-12.00), kLeisure).copy(label = "Cinéma")				// pas chez la banque
		val vOther = transfer("2026-10-04", kChecking, kLivret, eur(100.0))									// virement saisi
		val vData  = DataSet(listOf(kChecking, kLivret), listOf(kGroceries, kLeisure, kSalary), emptyList(),
			listOf(opening("2026-10-01", kChecking, eur(1_000.0)), vShop1, vShop2, vExtra, vOther), emptyList(), emptyList())	// données
		val vBank  = listOf(
			BankLine(d("2026-10-05"), "CB CARREFOUR 03/10", eur(-54.90)),
			BankLine(d("2026-10-10"), "CB CARREFOUR 09/10", eur(-54.90)),
			BankLine(d("2026-10-06"), "VIR VERS LIVRET A", eur(-100.0)),
			BankLine(d("2026-10-08"), "PRLV SEPA FREE MOBILE", eur(-19.99)),
			BankLine(d("2026-10-02"), "VIR SALAIRE ACME", eur(2_000.0))
		)																				// relevé
		val vResult = reconcile(vBank, kChecking.id, vData)							// rapprochement
		assertEquals(3, vResult.matched.size)
		assertEquals(vShop1.id, vResult.matched.single { it.line.date == d("2026-10-05") }.transaction.id)
		assertEquals(vShop2.id, vResult.matched.single { it.line.date == d("2026-10-10") }.transaction.id)
		assertEquals(listOf("PRLV SEPA FREE MOBILE", "VIR SALAIRE ACME").sorted(), vResult.missing.map { it.line.label }.sorted())
		assertEquals(listOf(vExtra.id), vResult.extra.map { it.id })
		assertEquals(d("2026-10-02"), vResult.from)
		assertEquals(vBank.sumOf { it.amountCents }, vResult.bankTotal)
		assertEquals(eur(-54.90 * 2 - 100.0 - 12.0), vResult.appTotal)			// retrouvées et en trop
		assertEquals(vResult.missing.sumOf { it.line.amountCents } - eur(-12.0), vResult.bankTotal - vResult.appTotal)	// écart = à créer − en trop
	}

	/*
		Saisie proposée : libellé et catégorie d'une saisie passée du même
		marchand ; sinon libellé nettoyé ; une catégorie de revenu n'est pas
		proposée pour un débit. Le formulaire construit passe les contrôles de
		saisie, remboursement compris.
	*/
	@Test
	fun suggestionsAndEntries()
	{
		val vPast  = standard("2026-09-02", kChecking, eur(-61.30), kGroceries).copy(label = "Courses Carrefour")	// saisie passée
		val vPay   = standard("2026-09-28", kChecking, eur(2_000.0), kSalary).copy(label = "Salaire")				// revenu passé
		val vData  = DataSet(listOf(kChecking), listOf(kGroceries, kSalary), emptyList(), listOf(vPast, vPay), emptyList(), emptyList())	// données
		val vBank  = listOf(
			BankLine(d("2026-10-05"), "CB CARREFOUR MARKET 03/10", eur(-41.00)),
			BankLine(d("2026-10-06"), "PRLV SEPA FREE MOBILE 123456", eur(-19.99)),
			BankLine(d("2026-10-07"), "REMB CARREFOUR", eur(8.50))
		)																				// relevé
		val vMissing = reconcile(vBank, kChecking.id, vData).missing					// lignes à créer
		val vShop    = vMissing.single { it.line.amountCents == eur(-41.00) }			// achat reconnu
		assertEquals("Courses Carrefour", vShop.label)
		assertEquals(kGroceries.id, vShop.categoryId)
		val vFree    = vMissing.single { it.line.amountCents == eur(-19.99) }			// marchand inconnu
		assertEquals("Free Mobile", vFree.label)
		assertEquals(null, vFree.categoryId)
		assertEquals("Carrefour Market", cleanLabel("CB CARREFOUR MARKET 03/10"))
		val vEntry   = EntryData(vData.accounts, vData.categories, emptyList(), emptyList(), vData.transactions)	// données de saisie
		val vRefund  = vMissing.single { it.line.amountCents == eur(8.50) }			// remboursement du marchand
		val vForm    = bankEntryForm(vRefund.line, vRefund.label, vRefund.categoryId, kChecking.id, vData.categories)	// formulaire
		assertEquals(EntryMode.EXPENSE, vForm.mode)
		assertTrue(vForm.refund)
		assertTrue(checkEntry(vForm, vEntry).isEmpty)
		assertEquals(eur(8.50), buildEntry(vForm, vEntry).postings.single().amountCents)
		val vShopForm = bankEntryForm(vShop.line, vShop.label, vShop.categoryId, kChecking.id, vData.categories)	// achat
		assertEquals(eur(-41.00), buildEntry(vShopForm, vEntry).postings.single().amountCents)
	}
}
