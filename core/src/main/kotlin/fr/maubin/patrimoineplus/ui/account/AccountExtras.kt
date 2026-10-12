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
	(Courant, Épargne) ou portefeuille et positions initiales (Bourse).
	statements       : relevés, du plus récent au plus ancien
	portfolio        : portefeuille ; null hors compte Bourse
	openingPositions : positions initiales du compte Bourse
	valueSeries      : valeur du compte Bourse en fin de mois, depuis son ouverture
*/
data class AccountExtras(
	val statements       : List<StatementItem>       = emptyList(),
	val portfolio        : PortfolioView?            = null,
	val openingPositions : List<OpeningPositionItem> = emptyList(),
	val valueSeries      : List<SeriesPoint>         = emptyList()
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
*/
fun buildAccountExtras(
	inAccountId    : String,
	inAccounts     : List<Account>,
	inTransactions : List<Transaction>,
	inStatements   : List<AccountStatement>,
	inPrices       : List<AssetPrice>,
	inAssets       : List<Asset>,
	inToday        : LocalDate
): AccountExtras
{
	val vAccount = inAccounts.firstOrNull { it.id == inAccountId } ?: return AccountExtras()	// compte affiché
	val vLedger  = Ledger(inTransactions)										// grand livre
	val vNames   = inAssets.associate { it.id to it.name }						// noms des actifs
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
