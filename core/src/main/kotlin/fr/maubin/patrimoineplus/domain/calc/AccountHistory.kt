package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate
import java.time.YearMonth

/*
	Mouvements d'un compte depuis son ouverture, ventilés par nature : la vue
	globale des comptes Épargne. Leur somme est le solde à la date de calcul.
	opening   : soldes initiaux
	transfers : virements nets (versements moins retraits vers d'autres comptes)
	income    : opérations positives (intérêts, revenus, remboursements)
	expenses  : opérations négatives (frais, dépenses), montant négatif
	trades    : mouvements d'espèces des ordres de bourse
*/
data class AccountTotals(
	val opening   : Long,
	val transfers : Long,
	val income    : Long,
	val expenses  : Long,
	val trades    : Long
)
{
	// Solde : somme de tous les mouvements.
	val balanceCents: Long get() = opening + transfers + income + expenses + trades
}

/*
	Totaux d'un compte depuis son ouverture jusqu'à une date incluse (les
	opérations à venir sont exclues).
	inLedger    : grand livre
	inAccountId : compte
	inDate      : date de calcul
*/
fun accountTotals(
	inLedger    : Ledger,
	inAccountId : String,
	inDate      : LocalDate
): AccountTotals
{
	var vOpening   = 0L															// soldes initiaux
	var vTransfers = 0L															// virements nets
	var vIncome    = 0L															// opérations positives
	var vExpenses  = 0L															// opérations négatives
	var vTrades    = 0L															// ordres de bourse
	inLedger.transactions()
		.filter { !it.date.isAfter(inDate) }
		.forEach { inTx ->
			val vAmount = amountOn(inTx, inAccountId)							// montant vu du compte
			when (inTx.kind)
			{
				TransactionKind.OPENING  -> vOpening   += vAmount
				TransactionKind.TRANSFER, TransactionKind.ACQUISITION, TransactionKind.DISPOSAL -> vTransfers += vAmount
				TransactionKind.TRADE    -> vTrades    += vAmount
				TransactionKind.STANDARD -> if (vAmount >= 0) vIncome += vAmount else vExpenses += vAmount
				TransactionKind.LOAN_PAYMENT ->
				{
					val vCharge = if (vAmount < 0) inTx.categoryAmount() else 0L		// intérêts et assurance, portés par le payeur
					vExpenses  += vCharge
					vTransfers += vAmount - vCharge
				}
			}
		}
	return AccountTotals(vOpening, vTransfers, vIncome, vExpenses, vTrades)
}

/*
	Valeur d'un compte en fin de mois, du mois de sa première opération jusqu'au
	mois de la date de fin, arrêté à cette date (opérations à venir exclues).
	Solde pour Courant et Épargne ; espèces plus positions au dernier cours
	connu pour Bourse. Valeur entière, sans la part détenue.
	inAccount : compte
	inLedger  : grand livre
	inPrices  : cours relevés
	inUntil   : date de fin (en général aujourd'hui)
*/
fun accountValueSeries(
	inAccount : Account,
	inLedger  : Ledger,
	inPrices  : List<AssetPrice>,
	inUntil   : LocalDate
): List<SeriesPoint>
{
	val vFirst = inLedger.transactions()
		.firstOrNull { inTx -> inTx.postings.any { it.accountId == inAccount.id } }
		?.date
		?: return emptyList()													// première opération du compte
	if (vFirst.isAfter(inUntil))
	{
		return emptyList()
	}
	val vLast  = YearMonth.from(inUntil)										// dernier mois
	return generateSequence(YearMonth.from(vFirst)) { it.plusMonths(1) }
		.takeWhile { !it.isAfter(vLast) }
		.map { inMonth ->
			val vEnd = if (inMonth == vLast) inUntil else inMonth.atEndOfMonth()	// fin de période
			SeriesPoint(inMonth, accountValue(inAccount, inLedger, vEnd, inPrices).valueCents)
		}
		.toList()
}
