package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountStatement
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import java.time.LocalDate
import java.time.YearMonth

/*
	Valeur d'un compte à une date, entière et à hauteur de la part détenue.
	account      : compte
	valueCents   : valeur entière (solde, ou espèces + positions pour Bourse)
	countedCents : valeur comptée dans le patrimoine (part détenue appliquée)
*/
data class AccountValue(
	val account      : Account,
	val valueCents   : Long,
	val countedCents : Long
)

/*
	Patrimoine à une date.
	date     : date de calcul
	accounts : valeur de chaque compte, dans l'ordre d'affichage
*/
data class Wealth(
	val date     : LocalDate,
	val accounts : List<AccountValue>
)
{
	// Patrimoine total (net) : somme des valeurs comptées.
	val totalCents: Long get() = accounts.sumOf { it.countedCents }

	// Patrimoine brut : valeurs comptées hors comptes Crédit.
	val grossCents: Long get() = accounts.filter { it.account.type != AccountType.LOAN }.sumOf { it.countedCents }

	// Dettes : capital restant dû compté des comptes Crédit (négatif ou nul).
	val debtCents: Long get() = accounts.filter { it.account.type == AccountType.LOAN }.sumOf { it.countedCents }

	// Répartition par type de compte (valeurs comptées), types vides exclus.
	val byType: Map<AccountType, Long>
		get() = AccountType.entries
			.associateWith { inType -> accounts.filter { it.account.type == inType }.sumOf { it.countedCents } }
			.filterValues { it != 0L }
}

/*
	Point d'une série mensuelle.
	month      : mois
	valueCents : valeur au dernier jour du mois
*/
data class SeriesPoint(
	val month      : YearMonth,
	val valueCents : Long
)

/*
	Valeur d'un compte à une date : solde pour Courant et Épargne ; espèces
	plus positions valorisées pour Bourse.
	inAccount : compte
	inLedger  : grand livre
	inDate    : date incluse
	inPrices  : cours relevés
*/
fun accountValue(
	inAccount : Account,
	inLedger  : Ledger,
	inDate    : LocalDate,
	inPrices  : List<AssetPrice>
): AccountValue
{
	val vValue = when (inAccount.type)										// valeur entière
	{
		AccountType.BROKERAGE -> valuePortfolio(inLedger, inAccount.id, inDate, inPrices).valueCents
		else                  -> inLedger.balanceAt(inAccount.id, inDate)
	}
	return AccountValue(inAccount, vValue, shareOf(vValue, inAccount.sharePercent))
}

/*
	Patrimoine à une date : tous les comptes, archivés compris, chacun pour sa
	part détenue.
	inAccounts : comptes
	inLedger   : grand livre
	inDate     : date incluse
	inPrices   : cours relevés
*/
fun wealthAt(
	inAccounts : List<Account>,
	inLedger   : Ledger,
	inDate     : LocalDate,
	inPrices   : List<AssetPrice>
): Wealth =
	Wealth(inDate, inAccounts.sortedBy { it.sortOrder }.map { accountValue(it, inLedger, inDate, inPrices) })

/*
	Série mensuelle du patrimoine : une valeur par fin de mois, du mois de la
	première opération jusqu'au mois de la date de fin. Le mois en cours est
	arrêté à la date de fin (les opérations à venir sont exclues).
	inAccounts : comptes
	inLedger   : grand livre
	inPrices   : cours relevés
	inUntil    : date de fin (en général aujourd'hui)
*/
fun wealthSeries(
	inAccounts : List<Account>,
	inLedger   : Ledger,
	inPrices   : List<AssetPrice>,
	inUntil    : LocalDate
): List<SeriesPoint>
{
	val vFirst = inLedger.transactions().firstOrNull()?.date ?: return emptyList()	// première opération
	if (vFirst.isAfter(inUntil))
	{
		return emptyList()
	}
	val vLast  = YearMonth.from(inUntil)										// dernier mois
	return generateSequence(YearMonth.from(vFirst)) { it.plusMonths(1) }
		.takeWhile { !it.isAfter(vLast) }
		.map { inMonth ->
			val vEnd = if (inMonth == vLast) inUntil else inMonth.atEndOfMonth()	// fin de période
			SeriesPoint(inMonth, wealthAt(inAccounts, inLedger, vEnd, inPrices).totalCents)
		}
		.toList()
}

/*
	Variation depuis la fin du mois précédent : dernier point de la série moins
	l'avant-dernier ; null si la série commence ce mois-ci (les soldes de
	départ ne sont pas une variation).
	inSeries : série mensuelle, le dernier point étant le mois en cours
*/
fun monthChange(inSeries: List<SeriesPoint>): Long? =
	if (inSeries.size < 2) null else inSeries.last().valueCents - inSeries[inSeries.size - 2].valueCents

/*
	Écart entre un relevé de la banque et le solde calculé à sa date :
	positif = la banque indique plus (intérêts non saisis, par exemple).
	inStatement : relevé
	inLedger    : grand livre
*/
fun statementGap(
	inStatement : AccountStatement,
	inLedger    : Ledger
): Long =
	inStatement.balanceCents - inLedger.balanceAt(inStatement.accountId, inStatement.date)
