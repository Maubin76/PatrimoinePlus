package fr.maubin.patrimoineplus.data.repo

import androidx.room.withTransaction
import fr.maubin.patrimoineplus.data.backup.loanError
import fr.maubin.patrimoineplus.data.backup.recurrenceError
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.db.toDomain
import fr.maubin.patrimoineplus.data.db.toEntity
import fr.maubin.patrimoineplus.data.db.toHeaderEntity
import fr.maubin.patrimoineplus.data.db.toPostingEntities
import fr.maubin.patrimoineplus.data.db.toTradeEntities
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.amortization
import fr.maubin.patrimoineplus.domain.calc.dueOperations
import fr.maubin.patrimoineplus.domain.calc.remainingAt
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.domain.rules.TransactionValidator
import fr.maubin.patrimoineplus.domain.rules.validateAccount
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.budget.themeOfCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/*
	Dépôt des opérations récurrentes et des crédits : mêmes règles que sur le
	PC (DataEditor). Après chaque modification, les échéances arrivées sont
	créées (applyDue), comme au lancement de l'appli et après un import.
	inDb    : base locale
	inClock : horloge
*/
@Singleton
class PlanningRepository @Inject constructor(
	private val inDb    : PatrimoineDatabase,
	private val inClock : Clock
)
{
	private val fDao = inDb.planningDao()											// accès aux récurrences et crédits

	// Opérations récurrentes.
	fun observeRecurrences(): Flow<List<Recurrence>> = fDao.observeRecurrences().map { inList -> inList.map { it.toDomain() } }

	// Crédits.
	fun observeLoans(): Flow<List<Loan>> = fDao.observeLoans().map { inList -> inList.map { it.toDomain() } }

	/*
		Crée ou remplace une opération récurrente, après vérification ; une
		récurrence modifiée garde sa dernière échéance créée. Les échéances
		déjà passées sont créées aussitôt.
		inRec : opération récurrente
		Retour : null si enregistrée ; sinon le motif du refus
	*/
	suspend fun saveRecurrence(inRec: Recurrence): String?
	{
		val vRefusal = inDb.withTransaction {
			recurrenceError(inRec, accounts().associateBy { it.id }, categories().associateBy { it.id })?.let { return@withTransaction it.replaceFirstChar { c -> c.uppercase() } }
			val vKept = fDao.recurrences().firstOrNull { it.id == inRec.id }?.last		// dernière échéance déjà créée
			fDao.upsertRecurrences(listOf(inRec.copy(label = inRec.label.trim(), notes = inRec.notes?.trim()?.ifEmpty { null }, last = vKept ?: inRec.last).toEntity()))
			null
		}
		if (vRefusal == null) applyDue()
		return vRefusal
	}

	/*
		Supprime une opération récurrente (les opérations créées restent).
		inId : récurrence
	*/
	suspend fun deleteRecurrence(inId: String) = fDao.deleteRecurrence(inId)

	/*
		Crée un crédit : compte de type Crédit en fin de liste, solde initial du
		jour (moins le capital restant dû après les mensualités passées), et
		tableau d'amortissement ; catégorie des intérêts « Crédit » créée si
		besoin. Les mensualités suivantes seront créées à leur date.
		inName        : nom du compte
		inInstitution : établissement, facultatif
		inShare       : part détenue, en %
		inLoan        : paramètres (catégorie vide : « Crédit »)
		Retour : null si créé ; sinon le motif du refus
	*/
	suspend fun createLoan(
		inName        : String,
		inInstitution : String?,
		inShare       : Int,
		inLoan        : Loan
	): String?
	{
		validateAccount(inName, inShare).firstOrNull()?.let { return it.message }
		val vToday = LocalDate.now(inClock)													// date du jour
		val vNow   = inClock.millis()															// horodatage
		return runCatching { inDb.withTransaction { createLoanNow(inName, inInstitution, inShare, inLoan, vToday, vNow) } }
			.getOrElse { inError -> if (inError is IllegalArgumentException) inError.message else throw inError }
	}

	/*
		Création d'un crédit, dans une transaction ; lève IllegalArgumentException
		si le crédit est refusé (rien n'est alors écrit).
		inName        : nom du compte
		inInstitution : établissement
		inShare       : part détenue
		inLoan        : paramètres
		inToday       : date du jour
		inNow         : horodatage
	*/
	private suspend fun createLoanNow(
		inName        : String,
		inInstitution : String?,
		inShare       : Int,
		inLoan        : Loan,
		inToday       : LocalDate,
		inNow         : Long
	): String?
	{
		val vToday = inToday																		// date du jour
		val vNow   = inNow																			// horodatage
		run {
			val vCats     = categories()														// catégories
			val vCategory = inLoan.categoryId.takeIf { inId -> vCats.any { it.id == inId } }
				?: vCats.firstOrNull { it.kind == CategoryKind.EXPENSE && themeOfCategory(it.name) == ExpenseTheme.CREDIT }?.id
				?: Category(UUID.randomUUID().toString(), "Crédit", "bank", CategoryKind.EXPENSE, (vCats.filter { it.kind == CategoryKind.EXPENSE }.maxOfOrNull { it.sortOrder } ?: -1) + 1)
					.also { inDb.categoryDao().insertAll(listOf(it.toEntity(vNow, vNow))) }.id	// catégorie des intérêts
			val vAccount  = Account(UUID.randomUUID().toString(), inName.trim(), AccountType.LOAN, inInstitution?.trim()?.ifEmpty { null }, inShare, inDb.accountDao().maxSortOrder() + 1)	// compte Crédit
			inDb.accountDao().insert(vAccount.toEntity(vNow, vNow))
			val vLoan     = inLoan.copy(id = UUID.randomUUID().toString(), accountId = vAccount.id, categoryId = vCategory, last = amortization(inLoan).lastOrNull { !it.date.isAfter(vToday) }?.date)	// crédit
			loanError(vLoan, accounts().associateBy { it.id }, categories().associateBy { it.id })?.let { inError ->
				throw IllegalArgumentException(inError.replaceFirstChar { it.uppercase() })
			}
			val vOpening  = Transaction(UUID.randomUUID().toString(), vToday, TransactionKind.OPENING, "Solde initial",
				postings = listOf(Posting(UUID.randomUUID().toString(), vAccount.id, -remainingAt(vLoan, vToday))))	// capital restant dû aujourd'hui
			inDb.transactionDao().save(vOpening.toHeaderEntity(vNow, vNow), vOpening.toPostingEntities(), vOpening.toTradeEntities())
			fDao.upsertLoans(listOf(vLoan.toEntity()))
		}
		return null
	}

	/*
		Supprime le tableau d'amortissement d'un crédit (le compte reste).
		inId : crédit
	*/
	suspend fun deleteLoan(inId: String) = fDao.deleteLoan(inId)

	/*
		Crée les opérations des échéances arrivées jusqu'à aujourd'hui
		(récurrences, mensualités de crédit) et avance récurrences et crédits ;
		une opération refusée par les règles n'est pas créée, mais son
		échéance est passée. Horodatages tirés de la date de l'échéance : le
		téléphone et le PC écrivent exactement la même opération.
		Retour : nombre d'opérations créées
	*/
	suspend fun applyDue(): Int =
		inDb.withTransaction {
			val vAccounts = accounts()																	// comptes
			val vAll      = inDb.transactionDao().getAll().map { it.toDomain() }							// opérations
			val vDue      = dueOperations(vAccounts, vAll, fDao.recurrences().map { it.toDomain() }, fDao.loans().map { it.toDomain() }, LocalDate.now(inClock))	// échéances arrivées
			if (vDue.isEmpty) return@withTransaction 0
			val vRules    = TransactionValidator(vAccounts.associateBy { it.id }, categories().associateBy { it.id }, inDb.assetDao().observeAll().first().associate { it.id to it.toDomain() })	// règles
			val vLedger   = Ledger(vAll)																	// opérations existantes
			val vAdded    = vDue.transactions.filter { vRules.validate(it, vLedger).isEmpty() }			// opérations valables
			vAdded.forEach { inTx ->
				val vStamp = inTx.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()			// horodatage tiré de la date
				inDb.transactionDao().save(inTx.toHeaderEntity(vStamp, vStamp), inTx.toPostingEntities(), inTx.toTradeEntities())
			}
			fDao.upsertRecurrences(vDue.recurrences.map { it.toEntity() })
			fDao.upsertLoans(vDue.loans.map { it.toEntity() })
			vAdded.size
		}

	/*
		Comptes enregistrés.
	*/
	private suspend fun accounts(): List<Account> = inDb.accountDao().observeAll().first().map { it.toDomain() }

	/*
		Catégories enregistrées.
	*/
	private suspend fun categories(): List<Category> = inDb.categoryDao().observeAll().first().map { it.toDomain() }
}
