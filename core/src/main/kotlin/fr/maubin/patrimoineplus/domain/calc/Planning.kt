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
	replaced     : opérations remplacées par une opération ajoutée (identifiant
	               de l'ajoutée → identifiants retirés) : anciennes mensualités
	               en deux opérations, fusionnées
*/
data class DueOperations(
	val transactions : List<Transaction>,
	val recurrences  : List<Recurrence>,
	val loans        : List<Loan>,
	val replaced     : Map<String, List<String>> = emptyMap()
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
	échéance ; pour chaque crédit, une mensualité (payeur débité du total,
	compte Crédit crédité du capital, intérêts et assurance en dépense). Une
	récurrence ou un crédit dont un compte manque ou est archivé est laissé de
	côté. Une opération déjà présente (même identifiant) n'est pas recréée.
	Les mensualités d'avant le format 5, en deux opérations (virement du
	capital, dépense des intérêts), sont fusionnées en une seule, de mêmes
	montants. Fonction pure.
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
	val vById     = inTransactions.associateBy { it.id }									// opérations par identifiant
	val vReplaced = mutableMapOf<String, List<String>>()									// fusions
	inLoans.forEach { inLoan ->
		val vName = inAccounts.firstOrNull { it.id == inLoan.accountId }?.name ?: return@forEach	// nom du compte Crédit
		amortization(inLoan).forEach { inPart ->
			mergedPayment(inLoan, vName, inPart.index, vById)?.let { (inTx, inOld) ->
				vNew += inTx
				vReplaced[inTx.id] = inOld
			}
		}
	}
	val vLoans  = inLoans.mapNotNull { inLoan ->
		val vAccount = vActive[inLoan.accountId]											// compte Crédit
		if (vAccount == null || inLoan.payFrom !in vActive) return@mapNotNull null
		val vDue = amortization(inLoan).filter { (inLoan.last == null || it.date.isAfter(inLoan.last)) && !it.date.isAfter(inToday) }	// mensualités arrivées
		if (vDue.isEmpty()) return@mapNotNull null
		vDue.forEach { inPart ->
			val vKey = "${inLoan.id}:${inPart.index}"										// clé de la mensualité
			if (listOf("mensualite", "capital", "frais").any { dueId("$vKey:$it") in vKnown }) return@forEach
			vNew += loanPayment(dueId("$vKey:mensualite"), inPart.date, paymentLabel(vAccount.name, inPart.index, inLoan.months), inLoan.payFrom, inLoan.accountId,
				inPart.capital, inPart.interest + inPart.insurance, inLoan.categoryId)
		}
		inLoan.copy(last = vDue.last().date)
	}																						// crédits avancés
	return DueOperations(vNew, vRecs, vLoans, vReplaced)
}

/*
	Libellé d'une mensualité créée par le tableau d'amortissement.
	inName   : nom du compte Crédit
	inIndex  : rang de la mensualité
	inMonths : nombre de mensualités
*/
fun paymentLabel(
	inName   : String,
	inIndex  : Int,
	inMonths : Int
): String = "Mensualité $inName ($inIndex/$inMonths)"

/*
	Mensualité de crédit : le payeur est débité du total (capital, intérêts et
	assurance), le compte Crédit crédité du capital ; l'écart est la dépense
	de la catégorie. Jambes dans cet ordre : payeur, puis compte Crédit.
	inId        : identifiant
	inDate      : date
	inLabel     : libellé
	inPayer     : compte payeur
	inLoan      : compte Crédit
	inCapital   : capital remboursé (positif ou nul)
	inCharge    : intérêts et assurance (positifs ou nuls)
	inCategory  : catégorie des intérêts et de l'assurance
	inNotes     : notes
*/
fun loanPayment(
	inId       : String,
	inDate     : LocalDate,
	inLabel    : String,
	inPayer    : String,
	inLoan     : String,
	inCapital  : Long,
	inCharge   : Long,
	inCategory : String?,
	inNotes    : String? = null
): Transaction =
	Transaction(inId, inDate, TransactionKind.LOAN_PAYMENT, inLabel, inCategory, notes = inNotes,
		postings = listOf(Posting(dueId("$inId:out"), inPayer, -(inCapital + inCharge)), Posting(dueId("$inId:in"), inLoan, inCapital)))

/*
	Mensualité fusionnée à partir des deux opérations créées avant le format 5
	pour une échéance (virement du capital, dépense des intérêts et de
	l'assurance), avec ses montants tels qu'ils sont enregistrés ; null s'il
	n'y a rien à fusionner ou si les deux opérations ne vont pas ensemble
	(comptes différents, modifiées en autre chose). Même résultat sur chaque
	appareil : identifiant et contenu ne dépendent que des opérations lues.
	inLoan  : crédit
	inName  : nom du compte Crédit
	inIndex : rang de la mensualité
	inById  : opérations par identifiant
*/
private fun mergedPayment(
	inLoan  : Loan,
	inName  : String,
	inIndex : Int,
	inById  : Map<String, Transaction>
): Pair<Transaction, List<String>>?
{
	val vKey     = "${inLoan.id}:$inIndex"													// clé de la mensualité
	val vCapital = inById[dueId("$vKey:capital")]											// ancien virement du capital
	val vCharge  = inById[dueId("$vKey:frais")]												// ancienne dépense des intérêts
	if ((vCapital == null && vCharge == null) || dueId("$vKey:mensualite") in inById) return null
	val vOut     = vCapital?.postings?.firstOrNull { it.amountCents < 0 }					// sortie du capital
	val vIn      = vCapital?.postings?.firstOrNull { it.amountCents > 0 }					// entrée sur le crédit
	if (vCapital != null && (vCapital.kind != TransactionKind.TRANSFER || vOut == null || vIn?.accountId != inLoan.accountId)) return null
	val vFee     = vCharge?.postings?.singleOrNull()										// jambe des intérêts
	if (vCharge != null && (vCharge.kind != TransactionKind.STANDARD || vFee == null || vFee.amountCents > 0)) return null
	val vPayer   = vOut?.accountId ?: vFee!!.accountId										// compte payeur
	if (vFee != null && vFee.accountId != vPayer) return null
	val vNotes   = listOfNotNull(vCapital?.notes, vCharge?.notes).joinToString("\n").ifEmpty { null }	// notes gardées
	return loanPayment(dueId("$vKey:mensualite"), (vCapital ?: vCharge)!!.date, vCapital?.label?.takeIf { it.isNotBlank() } ?: paymentLabel(inName, inIndex, inLoan.months),
		vPayer, inLoan.accountId, vIn?.amountCents ?: 0L, -(vFee?.amountCents ?: 0L), vCharge?.categoryId ?: inLoan.categoryId, vNotes) to listOfNotNull(vCapital?.id, vCharge?.id)
}
