package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToLong

/*
	Mensualité d'un crédit.
	index     : rang, à partir de 1
	date      : date de la mensualité
	payment   : mensualité hors assurance (capital + intérêts)
	capital   : capital remboursé
	interest  : intérêts
	insurance : assurance
	remaining : capital restant dû après la mensualité
*/
data class Installment(
	val index     : Int,
	val date      : LocalDate,
	val payment   : Long,
	val capital   : Long,
	val interest  : Long,
	val insurance : Long,
	val remaining : Long
)
{
	// Total payé : mensualité et assurance.
	val total: Long get() = payment + insurance
}

/*
	Tableau d'amortissement d'un crédit à mensualités constantes : mensualité
	M = C × t / (1 − (1 + t)^−n), t taux mensuel (taux annuel / 12) ; à
	chaque échéance, intérêts = capital restant × t (arrondis au centime),
	capital = M − intérêts ; la dernière mensualité solde le capital exact.
	Taux nul : capital divisé en parts égales.
	inLoan : crédit
*/
fun amortization(inLoan: Loan): List<Installment>
{
	if (inLoan.months <= 0 || inLoan.principal <= 0) return emptyList()
	val vRate    = inLoan.rateBp / 10_000.0 / 12.0											// taux mensuel
	val vPayment = if (vRate == 0.0) (inLoan.principal + inLoan.months - 1) / inLoan.months
		else (inLoan.principal * vRate / (1 - (1 + vRate).pow(-inLoan.months))).roundToLong()	// mensualité constante
	var vLeft    = inLoan.principal																// capital restant dû
	return (1..inLoan.months).map { inIndex ->
		val vInterest = (vLeft * vRate).roundToLong()											// intérêts du mois
		val vCapital  = if (inIndex == inLoan.months) vLeft else (vPayment - vInterest).coerceIn(0L, vLeft)	// capital remboursé
		vLeft -= vCapital
		Installment(inIndex, dueDate(inLoan.firstDue, inIndex - 1, 1), vCapital + vInterest, vCapital, vInterest, inLoan.insurance, vLeft)
	}
}

/*
	Capital restant dû d'un crédit après les mensualités échues à une date
	(toutes celles datées jusqu'à elle comprise).
	inLoan : crédit
	inDate : date
*/
fun remainingAt(
	inLoan : Loan,
	inDate : LocalDate
): Long = amortization(inLoan).lastOrNull { !it.date.isAfter(inDate) }?.remaining ?: inLoan.principal

/*
	Date d'une échéance : début décalé de inRank périodes, calculé depuis le
	début (un 31 janvier donne le 28 ou 29 février puis le 31 mars).
	inStart  : première échéance
	inRank   : rang de l'échéance, à partir de 0
	inMonths : mois entre deux échéances
*/
fun dueDate(
	inStart  : LocalDate,
	inRank   : Int,
	inMonths : Int
): LocalDate = inStart.plusMonths(inRank.toLong() * inMonths)

/*
	Échéances d'une opération récurrente entre deux dates : après inAfter
	(exclue ; null : depuis le début) et jusqu'à inUntil comprise, sans
	dépasser la fin de la récurrence.
	inRecurrence : opération récurrente
	inAfter      : dernière échéance déjà traitée ; null avant la première
	inUntil      : date limite
*/
fun occurrences(
	inRecurrence : Recurrence,
	inAfter      : LocalDate?,
	inUntil      : LocalDate
): List<LocalDate>
{
	val vLimit = listOfNotNull(inUntil, inRecurrence.end).min()							// dernière date possible
	return generateSequence(0) { it + 1 }
		.map { dueDate(inRecurrence.start, it, inRecurrence.frequency.months) }
		.takeWhile { !it.isAfter(vLimit) }
		.filter { inAfter == null || it.isAfter(inAfter) }
		.toList()
}

/*
	Opérations créées par les échéances arrivées, et récurrences et crédits
	mis à jour (dernière échéance créée).
	transactions : opérations à ajouter
	recurrences  : récurrences modifiées
	loans        : crédits modifiés
*/
data class DueOperations(
	val transactions : List<Transaction>,
	val recurrences  : List<Recurrence>,
	val loans        : List<Loan>
)
{
	// Vrai s'il n'y a rien à faire.
	val isEmpty: Boolean get() = transactions.isEmpty() && recurrences.isEmpty() && loans.isEmpty()
}

/*
	Identifiant stable d'une opération créée par une échéance : le même sur
	chaque appareil, si bien qu'une échéance créée sur le téléphone et sur le
	PC n'en fait qu'une après synchronisation.
	inKey : clé de l'échéance
*/
fun dueId(inKey: String): String = UUID.nameUUIDFromBytes("patrimoineplus-echeance:$inKey".toByteArray(Charsets.UTF_8)).toString()

/*
	Crée les opérations des échéances arrivées jusqu'à aujourd'hui compris :
	pour chaque récurrence, une dépense, un revenu ou un virement par
	échéance ; pour chaque crédit, par mensualité, le virement du capital
	(compte payeur → compte Crédit) et la dépense des intérêts et de
	l'assurance. Une récurrence ou un crédit dont un compte manque ou est
	archivé est laissé de côté. Une opération déjà présente (même
	identifiant) n'est pas recréée. Fonction pure.
	inAccounts     : comptes
	inTransactions : opérations existantes
	inRecurrences  : opérations récurrentes
	inLoans        : crédits
	inToday        : date du jour
*/
fun dueOperations(
	inAccounts     : List<Account>,
	inTransactions : List<Transaction>,
	inRecurrences  : List<Recurrence>,
	inLoans        : List<Loan>,
	inToday        : LocalDate
): DueOperations
{
	val vActive = inAccounts.filterNot { it.archived }.associateBy { it.id }				// comptes actifs
	val vKnown  = inTransactions.map { it.id }.toSet()										// opérations existantes
	val vNew    = mutableListOf<Transaction>()												// opérations créées
	val vRecs   = inRecurrences.mapNotNull { inRec ->
		if (inRec.accountId !in vActive || (inRec.toAccountId != null && inRec.toAccountId !in vActive)) return@mapNotNull null
		val vDates = occurrences(inRec, inRec.last, inToday)							// échéances arrivées
		if (vDates.isEmpty()) return@mapNotNull null
		vDates.forEach { inDate ->
			val vId = dueId("${inRec.id}:$inDate")											// identifiant de l'échéance
			if (vId in vKnown) return@forEach
			vNew += if (inRec.toAccountId != null)
				Transaction(vId, inDate, TransactionKind.TRANSFER, inRec.label, notes = inRec.notes,
					postings = listOf(Posting(dueId("$vId:out"), inRec.accountId, -inRec.amountCents), Posting(dueId("$vId:in"), inRec.toAccountId, inRec.amountCents)))
			else
				Transaction(vId, inDate, TransactionKind.STANDARD, inRec.label, inRec.categoryId, notes = inRec.notes,
					postings = listOf(Posting(dueId("$vId:p"), inRec.accountId, inRec.amountCents)))
		}
		inRec.copy(last = vDates.last())
	}																						// récurrences avancées
	val vLoans  = inLoans.mapNotNull { inLoan ->
		val vAccount = vActive[inLoan.accountId]											// compte Crédit
		if (vAccount == null || inLoan.payFrom !in vActive) return@mapNotNull null
		val vDue = amortization(inLoan).filter { (inLoan.last == null || it.date.isAfter(inLoan.last)) && !it.date.isAfter(inToday) }	// mensualités arrivées
		if (vDue.isEmpty()) return@mapNotNull null
		vDue.forEach { inPart ->
			val vKey = "${inLoan.id}:${inPart.index}"										// clé de la mensualité
			if (inPart.capital > 0 && dueId("$vKey:capital") !in vKnown)
			{
				vNew += Transaction(dueId("$vKey:capital"), inPart.date, TransactionKind.TRANSFER, "Mensualité ${vAccount.name} (${inPart.index}/${inLoan.months})",
					postings = listOf(Posting(dueId("$vKey:capital:out"), inLoan.payFrom, -inPart.capital), Posting(dueId("$vKey:capital:in"), inLoan.accountId, inPart.capital)))
			}
			if (inPart.interest + inPart.insurance > 0 && dueId("$vKey:frais") !in vKnown)
			{
				vNew += Transaction(dueId("$vKey:frais"), inPart.date, TransactionKind.STANDARD, "Intérêts et assurance ${vAccount.name} (${inPart.index}/${inLoan.months})", inLoan.categoryId,
					postings = listOf(Posting(dueId("$vKey:frais:p"), inLoan.payFrom, -(inPart.interest + inPart.insurance))))
			}
		}
		inLoan.copy(last = vDue.last().date)
	}																						// crédits avancés
	return DueOperations(vNew, vRecs, vLoans)
}
