package fr.maubin.patrimoineplus.ui.account

import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.PortfolioValuation
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint
import fr.maubin.patrimoineplus.domain.calc.accountValueSeries
import fr.maubin.patrimoineplus.domain.calc.statementGap
import fr.maubin.patrimoineplus.domain.calc.valuePortfolio
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountStatement
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate
import fr.maubin.patrimoineplus.domain.calc.PropertyItem
import fr.maubin.patrimoineplus.domain.calc.propertyItems
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.calc.Installment
import fr.maubin.patrimoineplus.domain.calc.amortization

/*
	Relevé de la banque, avec l'écart au solde calculé.
	statement : relevé
	gapCents  : relevé − solde calculé à sa date (positif = intérêts non saisis…)
*/
data class StatementItem(
	val statement : AccountStatement,
	val gapCents  : Long
)

/*
	Position initiale d'un compte Bourse (dans son solde initial).
	trade     : position (achat au prix de revient)
	assetName : nom de l'actif
*/
data class OpeningPositionItem(
	val trade     : Trade,
	val assetName : String
)

/*
	Portefeuille d'un compte Bourse, valorisé aujourd'hui.
	valuation : espèces, positions, plus-values réalisées
	names     : nom des actifs par identifiant
	assets    : actifs par identifiant (ISIN, symbole de cotation)
*/
data class PortfolioView(
	val valuation : PortfolioValuation,
	val names     : Map<String, String>,
	val assets    : Map<String, Asset> = emptyMap()
)
{
	// Prix de revient total des positions.
	val costCents: Long get() = valuation.positions.sumOf { it.costCents }

	// Plus-values réalisées cumulées.
	val realizedCents: Long get() = valuation.realized.sumOf { it.gainCents }
}

/*
	Compléments de la vue d'un compte, indépendants du mois affiché : relevés
	(Courant, Épargne), portefeuille et positions initiales (Bourse), ou biens
	(Immobilier, Collection).
	statements       : relevés, du plus récent au plus ancien
	portfolio        : portefeuille ; null hors compte Bourse
	openingPositions : positions initiales du compte Bourse
	valueSeries      : valeur du compte Bourse en fin de mois, depuis son ouverture
	properties       : biens d'un compte Immobilier ou Collection, du plus récent au plus ancien
	accountNames     : noms des comptes (payeurs et bénéficiaires des biens)
	asOf             : date de calcul (biens détenus ou vendus, mensualités passées) ; null hors compte de biens ou crédit
	loan             : crédit du compte Crédit ; null sinon (ou sans tableau d'amortissement)
	schedule         : tableau d'amortissement du crédit
*/
data class AccountExtras(
	val statements       : List<StatementItem>       = emptyList(),
	val portfolio        : PortfolioView?            = null,
	val openingPositions : List<OpeningPositionItem> = emptyList(),
	val valueSeries      : List<SeriesPoint>         = emptyList(),
	val properties       : List<PropertyItem>        = emptyList(),
	val accountNames     : Map<String, String>       = emptyMap(),
	val asOf             : LocalDate?                = null,
	val loan             : Loan?                     = null,
	val schedule         : List<Installment>         = emptyList()
)

/*
	Calcule les compléments de la vue d'un compte. Fonction pure.
	inAccountId    : compte affiché
	inAccounts     : tous les comptes
	inTransactions : toutes les opérations
	inStatements   : tous les relevés
	inPrices       : tous les cours
	inAssets       : tous les actifs
	inToday        : date de valorisation
	inLoans        : crédits (compte Crédit)
*/
fun buildAccountExtras(
	inAccountId    : String,
	inAccounts     : List<Account>,
	inTransactions : List<Transaction>,
	inStatements   : List<AccountStatement>,
	inPrices       : List<AssetPrice>,
	inAssets       : List<Asset>,
	inToday        : LocalDate,
	inLoans        : List<Loan> = emptyList()
): AccountExtras
{
	val vAccount = inAccounts.firstOrNull { it.id == inAccountId } ?: return AccountExtras()	// compte affiché
	val vLedger  = Ledger(inTransactions)										// grand livre
	val vNames   = inAssets.associate { it.id to it.name }						// noms des actifs
	if (vAccount.type == AccountType.LOAN)
	{
		val vLoan = inLoans.firstOrNull { it.accountId == inAccountId }						// crédit du compte
		return AccountExtras(loan = vLoan, schedule = vLoan?.let(::amortization).orEmpty(), asOf = inToday, accountNames = inAccounts.associate { it.id to it.name })
	}
	if (vAccount.type.isProperty)
	{
		return AccountExtras(properties = propertyItems(inTransactions, inAccounts, inAccountId), accountNames = inAccounts.associate { it.id to it.name }, asOf = inToday)
	}
	if (vAccount.type != AccountType.BROKERAGE)
	{
		return AccountExtras(
			statements = inStatements
				.filter { it.accountId == inAccountId }
				.sortedByDescending { it.date }
				.map { StatementItem(it, statementGap(it, vLedger)) }
		)
	}
	val vOpening = inTransactions.firstOrNull { inTx -> inTx.kind == TransactionKind.OPENING && inTx.postings.any { it.accountId == inAccountId } }	// solde initial
	return AccountExtras(
		portfolio        = PortfolioView(valuePortfolio(vLedger, inAccountId, inToday, inPrices), vNames, inAssets.associateBy { it.id }),
		openingPositions = vOpening?.trades.orEmpty().map { OpeningPositionItem(it, vNames[it.assetId] ?: "Actif supprimé") },
		valueSeries      = accountValueSeries(vAccount, vLedger, inPrices, inToday)
	)
}
