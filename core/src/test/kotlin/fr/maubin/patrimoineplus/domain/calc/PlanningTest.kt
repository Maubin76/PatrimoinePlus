package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.backup.validateBackup
import fr.maubin.patrimoineplus.data.backupFileOf
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
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetLine
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.budget.compareBudget
import fr.maubin.patrimoineplus.ui.planning.buildRecurrence
import fr.maubin.patrimoineplus.ui.planning.editRecurrenceForm
import fr.maubin.patrimoineplus.ui.planning.nextOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Opérations récurrentes et crédits : tableau d'amortissement, dates
	d'échéance, création des opérations échues (une seule fois, même sur deux
	appareils), crédit créé avec son solde initial, budget, fichier.
*/
class PlanningTest
{
	private val kCar   = Account("auto", "Crédit auto", AccountType.LOAN)						// compte Crédit
	private val kToday = d("2026-10-12")															// date du jour
	private val kRent  = Recurrence("loyer", "Loyer", kChecking.id, null, kGroceries.id, eur(-950.0), Frequency.MONTHLY, d("2026-08-05"))	// dépense mensuelle
	private val kSave  = Recurrence("epargne", "Épargne", kChecking.id, kLivret.id, null, eur(300.0), Frequency.QUARTERLY, d("2026-01-31"))	// virement trimestriel
	private val kLoan  = Loan("pret", kCar.id, eur(10_000.0), 300, 12, d("2026-08-10"), eur(5.0), kChecking.id, kGroceries.id)	// 10 000 € à 3 % sur 12 mois

	/*
		Tableau d'amortissement : mensualité constante (846,94 € pour
		10 000 € à 3 % sur 12 mois), intérêts du premier mois (25 €), capital
		remboursé en entier, dernière mensualité qui solde ; taux nul : parts
		égales.
	*/
	@Test
	fun amortizationSchedule()
	{
		val vRows = amortization(kLoan)																// tableau
		assertEquals(12, vRows.size)
		assertEquals(eur(846.94), vRows[0].payment)
		assertEquals(eur(25.0), vRows[0].interest)
		assertEquals(eur(821.94), vRows[0].capital)
		assertTrue(vRows.dropLast(1).all { it.payment == eur(846.94) })
		assertEquals(kLoan.principal, vRows.sumOf { it.capital })
		assertEquals(0L, vRows.last().remaining)
		assertEquals(d("2027-07-10"), vRows.last().date)
		assertEquals(eur(851.94), vRows[0].total)													// avec l'assurance
		val vFree = amortization(kLoan.copy(rateBp = 0, principal = eur(1_000.0), months = 3))	// sans intérêts
		assertEquals(listOf(33_334L, 33_334L, 33_332L), vFree.map { it.capital })
		assertEquals(kLoan.principal - vRows[1].let { vRows[0].capital + it.capital }, remainingAt(kLoan, d("2026-09-10")))
		listOf(0, 137, 370, 999).forEach { inRate ->
			listOf(7, 13, 240).forEach { inMonths ->
				val vPlan = amortization(kLoan.copy(principal = 1_234_567, rateBp = inRate, months = inMonths))	// autre crédit
				assertEquals("$inRate pb, $inMonths mois", 1_234_567L, vPlan.sumOf { it.capital })	// capital remboursé en entier
				assertEquals(0L, vPlan.last().remaining)
				assertTrue(vPlan.all { it.capital >= 0 && it.interest >= 0 })
			}
		}
	}

	/*
		Dates d'échéance : un 31 devient la fin des mois plus courts, sans
		dériver ; fin respectée ; après la dernière échéance traitée.
	*/
	@Test
	fun occurrenceDates()
	{
		val vMonthly = kSave.copy(frequency = Frequency.MONTHLY)										// mensuel depuis le 31 janvier
		assertEquals(listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31")), occurrences(vMonthly, null, d("2026-04-15")).take(3))
		assertEquals(listOf(d("2026-01-31"), d("2026-04-30"), d("2026-07-31")), occurrences(kSave, null, kToday))
		assertEquals(listOf(d("2026-07-31")), occurrences(kSave, d("2026-04-30"), kToday))
		assertEquals(listOf(d("2026-01-31")), occurrences(kSave.copy(end = d("2026-03-01")), null, kToday))
	}

	/*
		Échéances arrivées : une dépense par mois, un virement par trimestre,
		deux opérations par mensualité de crédit ; dernière échéance retenue ;
		rien à refaire ensuite, même si une opération a été supprimée ; mêmes
		identifiants d'un calcul à l'autre (deux appareils) ; compte archivé :
		rien.
	*/
	@Test
	fun dueOperationsOnce()
	{
		val vAccounts = listOf(kChecking, kLivret, kCar)												// comptes
		val vFirst    = dueOperations(vAccounts, emptyList(), listOf(kRent, kSave), listOf(kLoan), kToday)	// premier passage
		assertEquals(3 + 3 + 3 * 2, vFirst.transactions.size)
		assertEquals(d("2026-10-05"), vFirst.recurrences.single { it.id == "loyer" }.last)
		assertEquals(d("2026-07-31"), vFirst.recurrences.single { it.id == "epargne" }.last)
		assertEquals(d("2026-10-10"), vFirst.loans.single().last)
		val vTransfer = vFirst.transactions.first { it.kind == TransactionKind.TRANSFER && it.label == "Épargne" }	// virement créé
		assertEquals(listOf(eur(-300.0), eur(300.0)), vTransfer.postings.map { it.amountCents })
		val vFees     = vFirst.transactions.first { it.label.startsWith("Intérêts") }				// intérêts de la première mensualité
		assertEquals(eur(-30.0), vFees.postings.single().amountCents)								// 25 € d'intérêts + 5 € d'assurance
		assertEquals(vFirst.transactions.map { it.id }, dueOperations(vAccounts, emptyList(), listOf(kRent, kSave), listOf(kLoan), kToday).transactions.map { it.id })	// autre appareil : mêmes identifiants
		val vKept     = vFirst.transactions.drop(1)													// une opération supprimée ensuite
		assertTrue(dueOperations(vAccounts, vKept, vFirst.recurrences, vFirst.loans, kToday).isEmpty)
		assertTrue(dueOperations(vAccounts, emptyList(), listOf(kRent), emptyList(), d("2026-08-04")).isEmpty)	// pas encore
		assertTrue(dueOperations(vAccounts.map { it.copy(archived = it.id == kChecking.id) }, emptyList(), listOf(kRent), listOf(kLoan), kToday).isEmpty)
		val vLedger   = Ledger(listOf(opening("2026-08-01", kCar, -kLoan.principal)) + vFirst.transactions)	// soldes
		assertEquals(-remainingAt(kLoan, kToday), vLedger.balanceAt(kCar.id, kToday))
	}

	/*
		Crédit créé sur le fichier : compte Crédit, catégorie « Crédit » créée,
		mensualités passées tenues pour payées (solde initial du jour = capital
		restant dû), puis échéances créées à leur date avec des horodatages
		tirés de la date (identiques d'un appareil à l'autre) ; un second
		passage ne fait rien ; fichier valide ; budget : le thème Crédit
		compte le capital remboursé et les intérêts.
	*/
	@Test
	fun loanOnFile()
	{
		val vEditor  = DataEditor(Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris")))	// moteur
		val vFile    = backupFileOf(DataSet(listOf(kChecking, kLivret), listOf(kGroceries, kSalary), emptyList(), listOf(opening("2026-01-01", kChecking, eur(20_000.0))), emptyList(), emptyList()))	// contenu de départ
		val vStarted = kLoan.copy(firstDue = d("2025-11-10"), months = 24, categoryId = "")			// crédit commencé il y a onze mois
		val vResult  = vEditor.createLoan(vFile, "Prêt travaux", null, 100, vStarted, kToday)		// création
		assertTrue("$vResult", vResult is EditResult.Done)
		val vWith    = (vResult as EditResult.Done).file											// contenu avec le crédit
		val vData    = vWith.toDataSet()															// modèles
		val vLoan    = vData.loans.single()															// crédit enregistré
		assertEquals(AccountType.LOAN, vData.accounts.single { it.id == vLoan.accountId }.type)
		assertEquals("Crédit", vData.categories.single { it.id == vLoan.categoryId }.name)
		assertEquals(d("2026-10-10"), vLoan.last)
		assertEquals(-remainingAt(vLoan, kToday), Ledger(vData.transactions).balanceAt(vLoan.accountId, kToday))
		assertTrue(validateBackup(vWith) is BackupRead.Valid)
		assertNull(vEditor.applyDue(vWith, kToday))													// rien d'échu depuis
		val vLater   = vEditor.applyDue(vWith, d("2026-11-12"))!!									// un mois plus tard
		val vAdded   = vLater.transactions.filter { inTx -> vWith.transactions.none { it.id == inTx.id } }	// opérations créées
		assertEquals(2, vAdded.size)
		assertTrue(vAdded.all { it.createdAt == Instant.parse("2026-11-10T00:00:00Z").toEpochMilli() })
		assertEquals(vLater, vEditor.applyDue(vWith, d("2026-11-12")))								// même résultat sur un autre appareil
		assertNull(vEditor.applyDue(vLater, d("2026-11-12")))
		assertTrue(validateBackup(vLater) is BackupRead.Valid)
		assertEquals(vLater, (decodeBackup(encodeBackup(vLater)) as BackupRead.Valid).file)
		val vBudget  = Budget(listOf(BudgetLine("c", BudgetSection.EXPENSE, "Prêt", eur(900.0), theme = ExpenseTheme.CREDIT)))	// budget
		val vView    = compareBudget(vBudget, vLater.toDataSet(), java.time.YearMonth.of(2026, 11))	// novembre
		val vPart    = amortization(vLoan).single { it.date == d("2026-11-10") }					// mensualité de novembre
		assertEquals(vPart.capital + vPart.interest + vPart.insurance, vView.themes.single().actual)
	}

	/*
		Récurrences sur le fichier : refusée si invalide (catégorie manquante,
		comptes identiques), modifiée sans perdre sa dernière échéance ;
		catégorie réaffectée à la suppression ; compte supprimé : ses
		récurrences et crédits disparaissent.
	*/
	@Test
	fun recurrencesOnFile()
	{
		val vEditor = DataEditor(Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris")))	// moteur
		val vFile   = backupFileOf(DataSet(listOf(kChecking, kLivret), listOf(kGroceries, fr.maubin.patrimoineplus.domain.kLeisure), emptyList(), emptyList(), emptyList(), emptyList()))	// contenu
		assertTrue(vEditor.saveRecurrence(vFile, kRent.copy(categoryId = null)) is EditResult.Refused)
		assertTrue(vEditor.saveRecurrence(vFile, kSave.copy(toAccountId = kChecking.id)) is EditResult.Refused)
		val vSaved  = (vEditor.saveRecurrence(vFile, kRent.copy(last = d("2026-09-05"))) as EditResult.Done).file	// récurrence enregistrée
		val vEdited = (vEditor.saveRecurrence(vSaved, kRent.copy(label = "Loyer appartement", last = null)) as EditResult.Done).file	// modifiée
		assertEquals("2026-09-05", vEdited.recurrences.single().last)
		val vMoved  = (vEditor.deleteCategory(vEdited, kGroceries.id, fr.maubin.patrimoineplus.domain.kLeisure.id) as EditResult.Done).file	// catégorie supprimée
		assertEquals(fr.maubin.patrimoineplus.domain.kLeisure.id, vMoved.recurrences.single().categoryId)
		assertTrue((vEditor.deleteAccount(vMoved, kChecking.id, true) as EditResult.Done).file.recurrences.isEmpty())
		assertTrue((vEditor.deleteRecurrence(vMoved, kRent.id) as EditResult.Done).file.recurrences.isEmpty())
		val vRefused = vEditor.updateAccount(vMoved, kChecking.copy(type = AccountType.SAVINGS))	// type d'un compte utilisé par une récurrence
		assertTrue(vRefused is EditResult.Refused && "récurrente" in (vRefused as EditResult.Refused).messages.single())
		assertTrue(vEditor.updateAccount(vMoved, kChecking.copy(name = "Courant")) is EditResult.Done)	// renommer reste possible
	}

	/*
		Saisie d'une récurrence : prochaine échéance après la dernière créée
		et à partir d'aujourd'hui ; un remboursement régulier garde son signe
		et sa dernière échéance à la modification.
	*/
	@Test
	fun recurrenceForms()
	{
		assertEquals(d("2026-11-05"), nextOccurrence(kRent.copy(last = d("2026-10-05")), kToday))
		assertEquals(d("2026-11-05"), nextOccurrence(kRent, kToday))								// échéances passées non créées : pas de rattrapage affiché
		assertEquals(d("2026-10-12"), nextOccurrence(kRent.copy(start = kToday), kToday))			// aujourd'hui compris
		assertEquals(d("2026-11-12"), nextOccurrence(kRent.copy(start = kToday, last = kToday), kToday))	// saisie du jour déjà créée
		assertEquals(d("2027-01-05"), nextOccurrence(kRent.copy(start = d("2027-01-05")), kToday))
		assertNull(nextOccurrence(kRent.copy(end = d("2026-10-31")), kToday))						// terminée
		val vRefund = kRent.copy(amountCents = eur(30.0), last = d("2026-10-05"))					// remboursement mensuel sur une catégorie de dépense
		val vForm   = editRecurrenceForm(vRefund, listOf(kGroceries))								// saisie
		assertTrue(vForm.refund)
		assertEquals(vRefund, buildRecurrence(vForm))
		assertEquals(kRent, buildRecurrence(editRecurrenceForm(kRent, listOf(kGroceries))))
	}
}
