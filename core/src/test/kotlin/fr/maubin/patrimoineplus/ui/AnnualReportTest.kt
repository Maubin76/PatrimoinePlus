package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.data.demo.buildDemoData
import fr.maubin.patrimoineplus.data.report.PdfDocument
import fr.maubin.patrimoineplus.data.report.textWidth
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.wealthAt
import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kEtf
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kPea
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.trade
import fr.maubin.patrimoineplus.ui.report.annualReportPdf
import fr.maubin.patrimoineplus.ui.report.buildAnnualReport
import fr.maubin.patrimoineplus.ui.report.reportYears
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import fr.maubin.patrimoineplus.domain.calc.amortization
import fr.maubin.patrimoineplus.domain.calc.remainingAt
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind

/*
	Bilan annuel (appli PC) : chiffres de l'année et document PDF.
*/
class AnnualReportTest
{
	private val kInterest = Category("interets", "Intérêts", "percent", CategoryKind.INCOME)	// intérêts
	private val kDividend = Category("dividendes", "Dividendes", "chart", CategoryKind.INCOME)	// dividendes
	private val kHouse    = Account("maison", "Maison", AccountType.REAL_ESTATE)					// compte Immobilier

	// Deux années : achat puis vente avec plus-value, intérêts, dividende, salaire, dépenses.
	private val kData = DataSet(
		listOf(kChecking, kLivret, kPea), listOf(kGroceries, kSalary, kInterest, kDividend), listOf(kEtf),
		listOf(
			opening("2025-01-01", kChecking, eur(1_000.0)),
			opening("2025-01-01", kLivret, eur(5_000.0)),
			opening("2025-01-01", kPea, eur(2_000.0)),
			trade("2025-06-01", kPea, TradeSide.BUY, kEtf, 10.0, 100.0),
			standard("2026-01-31", kChecking, eur(2_000.0), kSalary),
			standard("2026-02-10", kChecking, eur(-300.0), kGroceries),
			standard("2026-02-20", kChecking, eur(40.0), kGroceries),
			standard("2026-12-31", kLivret, eur(120.0), kInterest),
			standard("2026-03-15", kPea, eur(15.0), kDividend),
			trade("2026-04-01", kPea, TradeSide.SELL, kEtf, 4.0, 130.0)
		),
		emptyList(), emptyList()
	)																					// données

	/*
		Année close : patrimoine au début et à la fin (variation = mouvements
		de l'année, valeurs au prix de revient faute de cours), dépenses nettes
		et revenus par catégorie, intérêts des livrets, dividendes, plus-value
		réalisée de la vente ; années proposées.
	*/
	@Test
	fun closedYearFigures()
	{
		val vReport = buildAnnualReport(kData, 2026, d("2027-02-01"))					// bilan 2026
		val vLedger = Ledger(kData.transactions)										// grand livre
		assertEquals(wealthAt(kData.accounts, vLedger, d("2025-12-31"), emptyList()).totalCents, vReport.wealthStart)
		assertEquals(wealthAt(kData.accounts, vLedger, d("2026-12-31"), emptyList()).totalCents, vReport.wealthEnd)
		assertEquals(eur(260.0), vReport.totalExpenses)
		assertEquals(eur(2_000.0 + 120.0 + 15.0), vReport.totalIncomes)
		assertEquals(eur(120.0), vReport.interest)
		assertEquals(eur(15.0), vReport.dividends)
		assertEquals(eur(120.0), vReport.totalRealized)								// 4 × (130 − 100)
		assertEquals(12, vReport.months.size)
		assertEquals(3, vReport.accounts.size)
		assertEquals(listOf(2027, 2026, 2025), reportYears(kData, d("2027-02-01")))
		assertTrue(!vReport.partial)
	}

	/*
		Année en cours : bilan arrêté à aujourd'hui (mois jusqu'au mois en
		cours, opérations futures exclues).
	*/
	@Test
	fun currentYearStopsToday()
	{
		val vReport = buildAnnualReport(kData, 2026, d("2026-03-01"))					// bilan au 1er mars
		assertTrue(vReport.partial)
		assertEquals(d("2026-03-01"), vReport.to)
		assertEquals(3, vReport.months.size)
		assertEquals(0L, vReport.interest)
		assertEquals(0L, vReport.totalRealized)
	}

	/*
		Biens et crédits de l'année : achats et ventes de biens (plus-value),
		crédit (capital restant dû à la fin, capital remboursé et intérêts
		d'après le tableau d'amortissement), avoirs = patrimoine + crédits ;
		un bien déjà possédé acheté il y a longtemps n'ouvre pas de bilans
		vides.
	*/
	@Test
	fun assetsAndLoans()
	{
		val vToday  = d("2026-10-09")														// date du jour
		val vDemo   = buildDemoData(vToday)												// démonstration
		val vReport = buildAnnualReport(vDemo, 2026, vToday)								// bilan de l'année en cours
		assertTrue(vReport.bought.any { it.name == "Montre de collection" && it.cost == 420_000L && it.paid })
		assertTrue(vReport.bought.single { it.name == "Lithographie" }.paid.not())				// déjà possédée
		val vSold   = vReport.sold.single()													// vinyles vendus
		assertEquals("Vinyles des années 70", vSold.name)
		assertEquals(85_000L, vSold.salePrice)
		assertEquals(25_000L, vReport.propertyGains)
		val vLoan   = vDemo.loans.single()													// crédit auto
		val vYear   = amortization(vLoan).filter { it.date.year == 2026 && !it.date.isAfter(vToday) }	// mensualités de 2026
		val vCar    = vReport.loans.single()													// crédit du bilan
		assertEquals(remainingAt(vLoan, vToday), vCar.end)
		assertEquals(vYear.sumOf { it.capital }, vCar.repaid)
		assertEquals(vYear.sumOf { it.interest + it.insurance }, vCar.interest)
		assertEquals(vCar.end, vReport.debtEnd)
		assertEquals(vReport.wealthEnd + vReport.debtEnd, vReport.grossEnd)
		val vOld    = kData.copy(accounts = kData.accounts + kHouse, transactions = kData.transactions + Transaction("maison", d("2012-06-01"), TransactionKind.ACQUISITION, "Maison",
			postings = listOf(Posting("maison-p", kHouse.id, eur(200_000.0)))))			// bien déjà possédé, acheté en 2012
		assertEquals(listOf(2026, 2025), reportYears(vOld, vToday))							// pas de bilans vides depuis 2012
		assertEquals(eur(200_000.0), buildAnnualReport(vOld, 2025, vToday).accounts.single { it.account.id == kHouse.id }.end)
	}

	/*
		Document PDF bien formé (en-tête, table des objets, fin), titre et
		texte accentué encodés, biens et crédits de la démonstration ; le
		fichier est aussi déposé dans build/ pour être ouvert à la main.
	*/
	@Test
	fun pdfIsWellFormed()
	{
		val vToday = d("2026-10-09")														// date du jour
		val vDemo  = buildDemoData(vToday)												// démonstration
		val vBytes = annualReportPdf(buildAnnualReport(vDemo, 2026, vToday), vToday)	// bilan
		File("build/outputs").mkdirs()
		File("build/outputs/bilan-demo.pdf").writeBytes(vBytes)
		val vText  = String(vBytes, Charsets.ISO_8859_1)								// contenu brut
		assertTrue(vText.startsWith("%PDF-1.4"))
		assertTrue(vText.trimEnd().endsWith("%%EOF"))
		val vXref  = vText.substringAfterLast("startxref\n").trim().lines().first().toInt()	// position de la table
		val vRows  = vText.substring(vXref).lines().drop(3).takeWhile { it.endsWith(" n ") }	// entrées des objets
		vRows.forEachIndexed { inIndex, inRow -> assertTrue(vText.startsWith("${inIndex + 1} 0 obj", inRow.take(10).toInt())) }
		assertTrue(vText.contains("(Bilan 2026) Tj"))
		assertTrue(vText.contains("D\\351penses"))										// « Dépenses » en WinAnsi
		assertTrue(vText.contains("(Biens) Tj"))											// achats et ventes de la démonstration
		assertTrue(vText.contains("(Cr\\351dits) Tj"))										// crédit auto de la démonstration
		val vDoc = PdfDocument()															// document de plusieurs pages
		repeat(3) { vDoc.newPage().text(56f, 100f, "Page ${it + 1} €") }
		assertTrue(String(vDoc.toBytes("Essai"), Charsets.ISO_8859_1).contains("/Count 3"))
		assertTrue(textWidth("MMM", 10f) > textWidth("iii", 10f))
	}
}
