package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.validateBackup
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.backupFileOf
import fr.maubin.patrimoineplus.data.demo.buildDemoData
import fr.maubin.patrimoineplus.data.edit.DataEditor
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.data.toDataSet
import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.domain.rules.TransactionValidator
import fr.maubin.patrimoineplus.domain.rules.ValidationError
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.budget.compareBudget
import fr.maubin.patrimoineplus.ui.entry.EntryData
import fr.maubin.patrimoineplus.ui.entry.EntryMode
import fr.maubin.patrimoineplus.ui.entry.buildEntry
import fr.maubin.patrimoineplus.ui.entry.checkEntry
import fr.maubin.patrimoineplus.ui.entry.editEntryForm
import fr.maubin.patrimoineplus.ui.entry.entryModes
import fr.maubin.patrimoineplus.ui.entry.loanCharge
import fr.maubin.patrimoineplus.ui.entry.newEntryForm
import fr.maubin.patrimoineplus.ui.entry.switchEntryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/*
	Mensualités de crédit en une seule opération (format 5) : règles,
	comptes (capital en virement, intérêts en dépense), anciennes paires
	fusionnées, saisie en mode « Crédit », bien financé (valeur nette,
	patrimoine brut et net), lien retiré quand le bien disparaît.
*/
class LoanPaymentTest
{
	private val kCar    = Account("auto", "Crédit auto", AccountType.LOAN)									// compte Crédit
	private val kHome   = Account("maison", "Maison", AccountType.REAL_ESTATE)								// compte de biens
	private val kToday  = d("2026-10-12")																	// date du jour
	private val kLoan   = Loan("pret", kCar.id, eur(10_000.0), 300, 12, d("2026-08-10"), eur(5.0), kChecking.id, kGroceries.id)	// 10 000 € à 3 % sur 12 mois
	private val kEditor = DataEditor(Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris")))	// moteur

	/*
		Mensualité saisie : payeur débité du total, compte Crédit crédité du
		capital.
		inTotal   : total payé
		inCapital : capital remboursé
		inDate    : date
	*/
	private fun payment(inTotal: Long, inCapital: Long, inDate: String = "2026-09-10"): Transaction =
		loanPayment("m-$inDate-$inTotal", d(inDate), "Mensualité", kChecking.id, kCar.id, inCapital, inTotal - inCapital, kGroceries.id)

	/*
		Règles d'une mensualité.
		inTx : mensualité
	*/
	private fun errors(inTx: Transaction): List<ValidationError> =
		TransactionValidator(listOf(kChecking, kLivret, kCar, kHome).associateBy { it.id }, listOf(kGroceries, kSalary).associateBy { it.id }, emptyMap()).validate(inTx, Ledger(emptyList()))

	/*
		Règles : une mensualité valable passe ; capital supérieur au total,
		payeur Crédit ou compte de biens, compte d'arrivée qui n'est pas un
		crédit, catégorie manquante ou de revenu sont refusés ; un
		remboursement anticipé (tout en capital) n'a pas besoin de catégorie.
	*/
	@Test
	fun rules()
	{
		assertEquals(emptyList<ValidationError>(), errors(payment(eur(846.94), eur(821.94))))
		assertTrue(ValidationError.InvalidLoanPayment in errors(payment(eur(100.0), eur(120.0))))
		val vValid = payment(eur(846.94), eur(821.94))															// mensualité valable
		assertTrue(ValidationError.InvalidLoanPayment in errors(vValid.copy(postings = listOf(vValid.postings[0].copy(accountId = kCar.id), vValid.postings[1]))))
		assertTrue(ValidationError.InvalidLoanPayment in errors(vValid.copy(postings = listOf(vValid.postings[0], vValid.postings[1].copy(accountId = kLivret.id)))))
		assertTrue(ValidationError.InvalidLoanPayment in errors(vValid.copy(postings = vValid.postings.reversed())))
		assertTrue(ValidationError.PropertyOnly in errors(vValid.copy(postings = listOf(vValid.postings[0].copy(accountId = kHome.id), vValid.postings[1]))))
		assertTrue(ValidationError.MissingCategory in errors(vValid.copy(categoryId = null)))
		assertTrue(ValidationError.MissingCategory in errors(vValid.copy(categoryId = kSalary.id)))
		assertEquals(emptyList<ValidationError>(), errors(payment(eur(2_000.0), eur(2_000.0)).copy(categoryId = null)))	// remboursement anticipé
	}

	/*
		Comptes : vu du payeur, le capital est un virement et les intérêts une
		dépense ; vu du crédit, le capital est un virement ; la catégorie ne
		compte que les intérêts et l'assurance (synthèse du compte, analyse,
		budget) ; le solde du crédit baisse du capital seulement.
	*/
	@Test
	fun accounting()
	{
		val vPay    = payment(eur(846.94), eur(821.94))													// mensualité de septembre
		val vLedger = Ledger(listOf(opening("2026-08-01", kChecking, eur(5_000.0)), opening("2026-08-01", kCar, -eur(10_000.0)), vPay))	// grand livre
		val vAll    = listOf(kChecking, kCar).associateBy { it.id }										// comptes
		val vMonth  = vLedger.monthSummary(kChecking, YearMonth.of(2026, 9), vAll)							// synthèse du payeur
		assertEquals(-eur(25.0), vMonth.expenses)
		assertEquals(-eur(821.94), vMonth.transfers)
		assertEquals(eur(821.94), vLedger.monthSummary(kCar, YearMonth.of(2026, 9), vAll).transfers)
		assertEquals(-eur(10_000.0 - 821.94), vLedger.balanceAt(kCar.id, kToday))
		assertEquals(eur(25.0), vLedger.categorySummary(kChecking.id, d("2026-09-01"), d("2026-09-30"), listOf(kGroceries), CategoryKind.EXPENSE).single().netCents)
		assertTrue(vLedger.categorySummary(kCar.id, d("2026-09-01"), d("2026-09-30"), listOf(kGroceries), CategoryKind.EXPENSE).isEmpty())
		val vTotals = accountTotals(vLedger, kChecking.id, kToday)											// totaux du payeur
		assertEquals(-eur(25.0), vTotals.expenses)
		assertEquals(-eur(821.94), vTotals.transfers)
		val vData   = DataSet(listOf(kChecking, kCar), listOf(kGroceries), emptyList(), vLedger.transactions(), emptyList(), emptyList())	// données
		val vBudget = compareBudget(Budget(listOf(fr.maubin.patrimoineplus.ui.budget.BudgetLine("a", fr.maubin.patrimoineplus.ui.budget.BudgetSection.EXPENSE, "Courses", eur(400.0), theme = ExpenseTheme.DAILY), fr.maubin.patrimoineplus.ui.budget.BudgetLine("b", fr.maubin.patrimoineplus.ui.budget.BudgetSection.EXPENSE, "Prêt", eur(900.0), theme = ExpenseTheme.CREDIT))), vData, YearMonth.of(2026, 9))								// budget de septembre
		assertEquals(eur(25.0), vBudget.themes.single { it.theme == ExpenseTheme.DAILY }.actual)				// intérêts dans leur catégorie (Alimentation, ici)
		assertEquals(eur(821.94), vBudget.themes.single { it.theme == ExpenseTheme.CREDIT }.actual)			// capital dans le thème Crédit
	}

	/*
		Fusion des mensualités d'avant le format 5 (virement du capital et
		dépense des intérêts) : une seule opération, mêmes montants et même
		payeur, soldes inchangés ; identique sur deux appareils ; une paire
		modifiée en autre chose n'est pas touchée ; fichier valide après
		l'application, et rien à refaire ensuite.
	*/
	@Test
	fun mergesOldPairs()
	{
		val vKey     = "${kLoan.id}:1"																	// première mensualité
		val vPart    = amortization(kLoan).first()														// sa ligne du tableau
		val vCapital = Transaction(dueId("$vKey:capital"), vPart.date, TransactionKind.TRANSFER, "Mensualité Crédit auto (1/12)", notes = "prélevée",
			postings = listOf(Posting(dueId("$vKey:capital:out"), kChecking.id, -vPart.capital), Posting(dueId("$vKey:capital:in"), kCar.id, vPart.capital)))	// ancien virement
		val vFees    = Transaction(dueId("$vKey:frais"), vPart.date, TransactionKind.STANDARD, "Intérêts et assurance Crédit auto (1/12)", kGroceries.id,
			postings = listOf(Posting(dueId("$vKey:frais:p"), kChecking.id, -(vPart.interest + vPart.insurance))))	// ancienne dépense
		val vOld     = listOf(opening("2026-08-01", kChecking, eur(5_000.0)), opening("2026-08-01", kCar, -kLoan.principal), vCapital, vFees)	// opérations
		val vLoan    = kLoan.copy(last = vPart.date)															// crédit à jour
		val vDue     = dueOperations(listOf(kChecking, kCar), vOld, emptyList(), listOf(vLoan), vPart.date)	// fusion seule
		val vMerged  = vDue.transactions.single()														// mensualité fusionnée
		assertEquals(listOf(vCapital.id, vFees.id), vDue.replaced.getValue(vMerged.id))
		assertEquals(TransactionKind.LOAN_PAYMENT, vMerged.kind)
		assertEquals(listOf(-vPart.total, vPart.capital), vMerged.postings.map { it.amountCents })
		assertEquals(kGroceries.id, vMerged.categoryId)
		assertEquals("prélevée", vMerged.notes)
		assertEquals(vDue, dueOperations(listOf(kChecking, kCar), vOld, emptyList(), listOf(vLoan), vPart.date))	// autre appareil
		val vAfter   = Ledger(vOld.filterNot { it.id in vDue.replaced.getValue(vMerged.id) } + vMerged)	// grand livre fusionné
		listOf(kChecking, kCar).forEach { assertEquals(Ledger(vOld).balanceAt(it.id, kToday), vAfter.balanceAt(it.id, kToday)) }
		val vOnlyFees = dueOperations(listOf(kChecking, kCar), vOld - vCapital, emptyList(), listOf(vLoan), vPart.date).transactions.single()	// virement supprimé : reste les intérêts
		assertEquals(listOf(-(vPart.interest + vPart.insurance), 0L), vOnlyFees.postings.map { it.amountCents })
		val vChanged = vCapital.copy(postings = listOf(vCapital.postings[0], vCapital.postings[1].copy(accountId = kLivret.id)))	// virement détourné vers le livret
		assertTrue(dueOperations(listOf(kChecking, kCar, kLivret), vOld - vCapital + vChanged, emptyList(), listOf(vLoan), vPart.date).isEmpty)
		val vElsewhere = vFees.copy(postings = listOf(vFees.postings[0].copy(accountId = kLivret.id)))	// intérêts payés depuis un autre compte
		assertTrue(dueOperations(listOf(kChecking, kCar, kLivret), vOld - vFees + vElsewhere, emptyList(), listOf(vLoan), vPart.date).isEmpty)
		val vFile    = backupFileOf(DataSet(listOf(kChecking, kCar), listOf(kGroceries), emptyList(), vOld, emptyList(), emptyList(), loans = listOf(vLoan)))	// fichier v4
		val vApplied = kEditor.applyDue(vFile, kToday)!!												// fusion et mensualités suivantes
		assertTrue(vApplied.transactions.none { it.id == vCapital.id || it.id == vFees.id })
		assertEquals(3, vApplied.transactions.count { it.kind == TransactionKind.LOAN_PAYMENT.name })	// août fusionnée, septembre et octobre créées
		assertTrue(validateBackup(vApplied) is BackupRead.Valid)
		assertNull(kEditor.applyDue(vApplied, kToday))
		assertEquals(-remainingAt(kLoan, kToday), Ledger(vApplied.toDataSet().transactions).balanceAt(kCar.id, kToday))
	}

	/*
		Saisie en mode « Crédit » : proposé seulement s'il y a un compte
		Crédit ; ouvert sur un compte Crédit, il propose la mensualité du mois
		(total, capital, catégorie) et le payeur du crédit ; règles des
		champs ; intérêts = total − capital ; aller-retour exact d'une
		mensualité modifiée ; pas de récurrence.
	*/
	@Test
	fun entryMode()
	{
		val vData  = EntryData(listOf(kChecking, kLivret, kCar), listOf(kGroceries, kSalary), loans = listOf(kLoan))	// données de saisie
		assertTrue(EntryMode.LOAN in entryModes(vData, newEntryForm(vData, null, kToday)))
		assertFalse(EntryMode.LOAN in entryModes(vData.copy(accounts = listOf(kChecking)), newEntryForm(vData, null, kToday)))
		val vForm  = newEntryForm(vData, kCar.id, d("2026-09-10"))											// saisie depuis le compte Crédit
		val vPart  = amortization(kLoan).single { it.date == d("2026-09-10") }								// mensualité de septembre
		assertEquals(EntryMode.LOAN, vForm.mode)
		assertEquals(kChecking.id, vForm.accountId)
		assertEquals(kCar.id, vForm.toAccountId)
		assertEquals(kGroceries.id, vForm.categoryId)
		assertEquals(vPart.insurance + vPart.interest, loanCharge(vForm))
		assertTrue(checkEntry(vForm, vData).isEmpty)
		val vTx    = buildEntry(vForm, vData)																// mensualité construite
		assertEquals("Mensualité Crédit auto", vTx.label)
		assertEquals(listOf(-vPart.total, vPart.capital), vTx.postings.map { it.amountCents })
		assertEquals(vTx, buildEntry(editEntryForm(vTx, vData.categories), vData))
		assertNotNull(checkEntry(vForm.copy(capital = "9 999"), vData).capital)
		assertNotNull(checkEntry(vForm.copy(accountId = kCar.id), vData).account)
		assertNotNull(checkEntry(vForm.copy(toAccountId = kLivret.id), vData).target)
		assertNotNull(checkEntry(vForm.copy(categoryId = null), vData).category)
		val vEarly = vForm.copy(amount = "2 000", capital = "", categoryId = null)							// remboursement anticipé
		assertTrue(checkEntry(vEarly, vData).isEmpty)
		assertEquals(listOf(-eur(2_000.0), eur(2_000.0)), buildEntry(vEarly, vData).postings.map { it.amountCents })
		val vOther = switchEntryMode(newEntryForm(vData, kChecking.id, kToday), EntryMode.LOAN, vData)		// passage en mode Crédit depuis le courant
		assertEquals(kCar.id, vOther.toAccountId)
		assertEquals(kChecking.id, vOther.accountId)
		assertNull(fr.maubin.patrimoineplus.ui.planning.recurrenceFromEntry(vForm, "Mensualité", fr.maubin.patrimoineplus.domain.model.Frequency.MONTHLY))
	}

	/*
		Bien financé : valeur nette = prix d'achat − capital restant dû (solde
		du compte Crédit) ; bien sans crédit ou vendu : absent ; patrimoine
		brut (hors crédits) et net ; lien posé, refusé vers un bien inconnu,
		retiré quand l'achat est supprimé ; fichier v5 relu à l'identique
		(JSON et classeur) ; démonstration : prêt immobilier lié à
		l'appartement.
	*/
	@Test
	fun financedProperty()
	{
		val vFlat   = Transaction("achat", d("2020-06-01"), TransactionKind.ACQUISITION, "Appartement", postings = listOf(Posting("achat-p", kHome.id, eur(250_000.0))))	// bien déjà possédé
		val vLoan   = kLoan.copy(propertyId = vFlat.id)													// crédit lié
		val vLedger = Ledger(listOf(opening("2026-08-01", kChecking, eur(5_000.0)), opening("2026-08-01", kCar, -eur(9_000.0)), vFlat))	// soldes
		val vNets   = propertyNets(propertyItems(vLedger.transactions(), listOf(kHome)), listOf(vLoan), vLedger, kToday)	// valeurs nettes
		assertEquals(eur(9_000.0), vNets.getValue(vFlat.id).debt)
		assertEquals(eur(241_000.0), vNets.getValue(vFlat.id).net)
		assertTrue(propertyNets(propertyItems(vLedger.transactions(), listOf(kHome)), listOf(kLoan), vLedger, kToday).isEmpty())
		val vWealth = wealthAt(listOf(kChecking, kHome, kCar), vLedger, kToday, emptyList())					// patrimoine
		assertEquals(eur(255_000.0), vWealth.grossCents)
		assertEquals(-eur(9_000.0), vWealth.debtCents)
		assertEquals(eur(246_000.0), vWealth.totalCents)
		val vFile   = backupFileOf(DataSet(listOf(kChecking, kHome, kCar), listOf(kGroceries), emptyList(), vLedger.transactions(), emptyList(), emptyList(), loans = listOf(kLoan)))	// fichier
		val vLinked = (kEditor.setLoanProperty(vFile, kLoan.id, vFlat.id) as EditResult.Done).file			// crédit lié
		assertEquals(vFlat.id, vLinked.loans.single().propertyId)
		assertTrue(kEditor.setLoanProperty(vFile, kLoan.id, "inconnu") is EditResult.Refused)
		assertEquals(BackupRead.Valid(vLinked), decodeBackup(encodeBackup(vLinked)))
		val vBytes  = java.io.ByteArrayOutputStream().also { writeBackupXlsx(vLinked, it) }.toByteArray()	// classeur
		assertEquals(vFlat.id, (readBackupXlsx(java.io.ByteArrayInputStream(vBytes)) as BackupRead.Valid).file.loans.single().propertyId)
		assertTrue(validateBackup(vLinked.copy(loans = vLinked.loans.map { it.copy(propertyId = "inconnu") })) is BackupRead.Invalid)
		val vSold   = (kEditor.deleteTransactions(vLinked, listOf(vFlat.id)) as EditResult.Done).file		// achat supprimé
		assertNull(vSold.loans.single().propertyId)
		assertTrue(validateBackup(vSold) is BackupRead.Valid)
		val vDemo   = buildDemoData(kToday)																// démonstration
		val vHome   = vDemo.loans.single { it.propertyId != null }											// prêt immobilier
		assertEquals("Appartement (démo)", vDemo.transactions.single { it.id == vHome.propertyId }.label)
		assertEquals(vHome.propertyId, buildDemoData(kToday).loans.single { it.propertyId != null }.propertyId)	// identifiant de l'appartement stable
	}
}
