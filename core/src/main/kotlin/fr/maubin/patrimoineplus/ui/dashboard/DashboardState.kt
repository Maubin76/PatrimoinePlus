package fr.maubin.patrimoineplus.ui.dashboard

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.calc.CategoryLine
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint
import fr.maubin.patrimoineplus.domain.calc.accountValueSeries
import fr.maubin.patrimoineplus.domain.calc.amountOn
import fr.maubin.patrimoineplus.domain.calc.balanceSeries
import fr.maubin.patrimoineplus.domain.calc.monthChange
import fr.maubin.patrimoineplus.domain.calc.propertyItems
import fr.maubin.patrimoineplus.domain.calc.statementGap
import fr.maubin.patrimoineplus.domain.calc.valuePortfolio
import fr.maubin.patrimoineplus.domain.calc.wholePercents
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.ui.account.StatementItem
import fr.maubin.patrimoineplus.ui.accounts.AccountWeight
import fr.maubin.patrimoineplus.ui.accounts.buildAccountsState
import fr.maubin.patrimoineplus.ui.home.HomeUiState
import fr.maubin.patrimoineplus.ui.home.buildHomeState
import java.time.LocalDate
import java.time.YearMonth
import fr.maubin.patrimoineplus.ui.property.propertyNature
import fr.maubin.patrimoineplus.ui.property.propertyIcon
import fr.maubin.patrimoineplus.domain.calc.Installment
import fr.maubin.patrimoineplus.domain.calc.amortization
import fr.maubin.patrimoineplus.domain.calc.financedAccounts

const val kTileSeriesMonths   = 12												// mois de la petite courbe d'une tuile
const val kTileTopItems       = 3												// catégories ou positions montrées dans une tuile
const val kRecentOperations   = 8												// opérations récentes du tableau de bord

/*
	Aperçu propre au type d'un compte, montré dans sa tuile.
*/
sealed interface TilePreview
{
	/*
		Compte Courant : le mois en cours.
		income     : entrées du mois (hors remboursements)
		expenses   : dépenses nettes du mois (négatives)
		categories : plus grosses dépenses du mois, par catégorie
	*/
	data class Checking(
		val income     : Long,
		val expenses   : Long,
		val categories : List<CategoryLine>
	) : TilePreview

	/*
		Compte Épargne : intérêts de l'année et dernier relevé.
		year       : année en cours
		yearIncome : entrées de l'année (intérêts, revenus), hors virements
		statement  : dernier relevé, avec son écart au solde calculé ; null sans relevé
	*/
	data class Savings(
		val year       : Int,
		val yearIncome : Long,
		val statement  : StatementItem?
	) : TilePreview

	/*
		Compte Bourse : espèces, plus-value latente et principales positions.
		cashCents       : espèces
		unrealizedCents : plus-value latente
		costCents       : prix de revient des positions (taux de plus-value)
		positions       : plus grosses positions (nom, part en %), les autres regroupées
		others          : part des autres positions, en % ; 0 s'il n'y en a pas
	*/
	data class Brokerage(
		val cashCents       : Long,
		val unrealizedCents : Long,
		val costCents       : Long,
		val positions       : List<Pair<String, Int>>,
		val others          : Int
	) : TilePreview
	{
		// Plus-value latente en pour mille du prix de revient ; null sans position.
		val ratePermille: Long? get() = if (costCents > 0) unrealizedCents * 1000 / costCents else null
	}

	/*
		Compte Immobilier ou Collection : biens détenus.
		count  : nombre de biens détenus
		items  : plus gros biens (nom, prix d'achat), du plus cher au moins cher
		others : prix d'achat des autres biens ; 0 s'il n'y en a pas
		gains  : plus- et moins-values des biens vendus, cumulées
	*/
	data class Property(
		val count  : Int,
		val items  : List<Pair<String, Long>>,
		val others : Long,
		val gains  : Long
	) : TilePreview

	/*
		Compte Crédit : capital restant dû et prochaine mensualité.
		remaining : capital restant dû (positif), d'après le solde du compte
		next      : prochaine mensualité ; null sans tableau d'amortissement ou après la dernière
		end       : date de la dernière mensualité ; null sans tableau
		paid      : part du capital déjà remboursée, en % ; null sans tableau
	*/
	data class Loan(
		val remaining : Long,
		val next      : Installment?,
		val end       : LocalDate?,
		val paid      : Int?
	) : TilePreview
}

/*
	Tuile d'un compte dans le tableau de bord.
	account     : compte
	valueCents  : valeur entière aujourd'hui
	weight      : part dans le patrimoine ; null s'il n'y compte pas
	series      : valeur en fin de mois, douze derniers mois au plus (depuis l'ouverture)
	monthChange : variation depuis la fin du mois précédent ; null le premier mois
	preview     : aperçu propre au type
	note        : précision sous la valeur (crédit : somme due, compte déduit ;
	              compte de biens : net des crédits) ; null sans précision
*/
data class AccountTile(
	val account     : Account,
	val valueCents  : Long,
	val weight      : AccountWeight?,
	val series      : List<SeriesPoint>,
	val monthChange : Long?,
	val preview     : TilePreview,
	val note        : String? = null
)

/*
	Opération récente, tous comptes confondus.
	transaction : opération
	amount      : montant (vu du compte d'origine ; positif pour un virement)
	title       : libellé
	detail      : compte et catégorie, ou comptes d'un virement
	icon        : clé d'icône
*/
data class RecentOperation(
	val transaction : Transaction,
	val amount      : Long,
	val title       : String,
	val detail      : String,
	val icon        : String
)

/*
	État du tableau de bord (appli PC) : patrimoine et indicateurs du mois,
	tuiles des comptes, dépenses par catégorie et opérations récentes.
	home       : patrimoine, répartition par type, évolution
	yearChange : variation sur douze mois ; null avec moins d'un an d'historique
	income     : entrées du mois sur les comptes Courant (hors remboursements)
	expenses   : dépenses nettes du mois sur les comptes Courant (négatives)
	saved      : épargne du mois : virements nets des comptes Courant vers l'épargne et la bourse
	categories : dépenses nettes du mois par catégorie, tous comptes Courant
	tiles      : comptes actifs, dans l'ordre d'affichage
	archived   : comptes archivés
	recent     : opérations récentes (hors opérations à venir), de la plus récente
	upcoming   : nombre d'opérations à venir
*/
data class DashboardState(
	val home       : HomeUiState,
	val yearChange : Long?,
	val income     : Long,
	val expenses   : Long,
	val saved      : Long,
	val categories : List<CategoryLine>,
	val tiles      : List<AccountTile>,
	val archived   : List<AccountTile>,
	val recent     : List<RecentOperation>,
	val upcoming   : Int
)

/*
	Calcule le tableau de bord. Fonction pure.
	inData  : données
	inToday : date du jour
*/
fun buildDashboard(
	inData  : DataSet,
	inToday : LocalDate
): DashboardState
{
	val vLedger    = Ledger(inData.transactions)								// grand livre
	val vMonth     = YearMonth.from(inToday)									// mois en cours
	val vById      = inData.accounts.associateBy { it.id }					// comptes par identifiant
	val vExpenseIds = inData.categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()	// catégories de dépense
	val vHome      = buildHomeState(inData.accounts, inData.transactions, inData.prices, inToday, inData.loans)	// patrimoine net
	val vAccounts  = buildAccountsState(inData.accounts, inData.transactions, inData.prices, inToday, inData.loans)	// valeurs nettes et parts
	val vLinks     = financedAccounts(inData.loans, inData.transactions, inData.accounts, inToday)	// compte de biens financé par chaque crédit
	val vChecking  = inData.accounts.filter { it.type == AccountType.CHECKING && !it.archived }	// comptes Courant actifs
	val vSummaries = vChecking.map { vLedger.monthSummary(it, vMonth, vById, vExpenseIds) }	// synthèses du mois
	val vTile      = { inItem: fr.maubin.patrimoineplus.ui.accounts.AccountItem ->
		val vSeries = netSeries(inItem.account, vLedger, inData, inToday, vLinks)	// petite courbe, nette
		val vNote   = when
		{
			inItem.account.type == AccountType.LOAN -> "Restant dû" + (inItem.deductedInto?.let { " · déduit de $it" } ?: "")
			inItem.account.id in vLinks.values      -> "Net des crédits restant dus"
			else                                    -> null
		}																		// précision sous la valeur
		AccountTile(inItem.account, inItem.valueCents, inItem.weight, vSeries, monthChange(vSeries), preview(inItem.account, vLedger, inData, inToday, vById, vExpenseIds), vNote)
	}
	return DashboardState(
		home       = vHome,
		yearChange = vHome.series.takeIf { it.size >= 13 }?.let { it.last().valueCents - it[it.size - 13].valueCents },
		income     = vSummaries.sumOf { it.income },
		expenses   = vSummaries.sumOf { it.expenses },
		saved      = -vSummaries.sumOf { it.savings },
		categories = mergeCategories(vChecking.map { inAccount ->
			vLedger.categorySummary(inAccount.id, vMonth.atDay(1), vMonth.atEndOfMonth(), inData.categories, CategoryKind.EXPENSE)
		}),
		tiles      = vAccounts.active.map(vTile),
		archived   = vAccounts.archived.map(vTile),
		recent     = inData.transactions
			.filter { !it.isUpcoming(inToday) && it.kind != TransactionKind.OPENING }
			.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
			.take(kRecentOperations)
			.map { recentOperation(it, vById, inData) },
		upcoming   = inData.transactions.count { it.isUpcoming(inToday) }
	)
}

/*
	Courbe nette d'une tuile : compte de biens moins le capital restant dû
	des crédits liés à ses biens, mois par mois ; crédit en positif (somme
	due) ; autres comptes inchangés.
	inAccount : compte
	inLedger  : grand livre
	inData    : données
	inToday   : date du jour
	inLinks   : compte de biens financé par chaque compte Crédit
*/
private fun netSeries(
	inAccount : Account,
	inLedger  : Ledger,
	inData    : DataSet,
	inToday   : LocalDate,
	inLinks   : Map<String, String>
): List<SeriesPoint>
{
	val vOwn = tileSeries(inAccount, inLedger, inData, inToday)						// valeur du compte seul
	if (inAccount.type == AccountType.LOAN) return vOwn.map { it.copy(valueCents = -it.valueCents) }
	val vLoans = inLinks.filterValues { it == inAccount.id }.keys				// crédits liés à ses biens
	if (vLoans.isEmpty()) return vOwn
	val vSince = inLedger.transactions().firstOrNull { inTx -> inTx.postings.any { it.accountId in vLoans } }?.date?.let(YearMonth::from)	// début du suivi des crédits
	return vOwn.filter { vSince == null || !it.month.isBefore(vSince) }.map { inPoint ->
		val vEnd = if (inPoint.month == YearMonth.from(inToday)) inToday else inPoint.month.atEndOfMonth()	// date du point
		inPoint.copy(valueCents = inPoint.valueCents + vLoans.sumOf { inLedger.balanceAt(it, vEnd) })
	}
}

/*
	Valeur d'un compte en fin de mois pour sa tuile : douze mois au plus,
	depuis son ouverture ; le mois en cours est arrêté à aujourd'hui (comme
	la valeur affichée).
	inAccount : compte
	inLedger  : grand livre
	inData    : données (cours)
	inToday   : date du jour
*/
private fun tileSeries(
	inAccount : Account,
	inLedger  : Ledger,
	inData    : DataSet,
	inToday   : LocalDate
): List<SeriesPoint>
{
	if (inAccount.type == AccountType.BROKERAGE)
	{
		return accountValueSeries(inAccount, inLedger, inData.prices, inToday).takeLast(kTileSeriesMonths)
	}
	val vFirst = inLedger.transactions().firstOrNull { inTx -> inTx.postings.any { it.accountId == inAccount.id } }?.date	// première opération
		?: return emptyList()
	return balanceSeries(inLedger, inAccount.id, YearMonth.from(inToday), kTileSeriesMonths)
		.filter { !it.month.isBefore(YearMonth.from(vFirst)) }
		.let { inPoints -> if (inPoints.isEmpty()) inPoints else inPoints.dropLast(1) + inPoints.last().copy(valueCents = inLedger.balanceAt(inAccount.id, inToday)) }	// mois en cours arrêté à aujourd'hui
}

/*
	Aperçu d'un compte selon son type.
	inAccount    : compte
	inLedger     : grand livre
	inData       : données
	inToday      : date du jour
	inById       : comptes par identifiant
	inExpenseIds : catégories de dépense
*/
private fun preview(
	inAccount    : Account,
	inLedger     : Ledger,
	inData       : DataSet,
	inToday      : LocalDate,
	inById       : Map<String, Account>,
	inExpenseIds : Set<String>
): TilePreview
{
	val vMonth = YearMonth.from(inToday)											// mois en cours
	return when (inAccount.type)
	{
		AccountType.CHECKING  ->
		{
			val vSummary = inLedger.monthSummary(inAccount, vMonth, inById, inExpenseIds)	// synthèse du mois
			val vLines   = inLedger.categorySummary(inAccount.id, vMonth.atDay(1), vMonth.atEndOfMonth(), inData.categories, CategoryKind.EXPENSE)	// dépenses par catégorie
			TilePreview.Checking(vSummary.income, vSummary.expenses, vLines.filter { it.netCents > 0 }.take(kTileTopItems))
		}
		AccountType.SAVINGS   ->
		{
			val vYear     = inToday.year												// année en cours
			val vIncome   = inLedger.transactions()
				.filter { it.kind == TransactionKind.STANDARD && it.date.year == vYear && !it.date.isAfter(inToday) }
				.sumOf { amountOn(it, inAccount.id).coerceAtLeast(0L) }				// entrées de l'année
			val vLast     = inData.statements.filter { it.accountId == inAccount.id }.maxByOrNull { it.date }	// dernier relevé
			TilePreview.Savings(vYear, vIncome, vLast?.let { StatementItem(it, statementGap(it, inLedger)) })
		}
		AccountType.LOAN ->
		{
			val vLoan     = inData.loans.firstOrNull { it.accountId == inAccount.id }		// crédit du compte
			val vSchedule = vLoan?.let(::amortization).orEmpty()								// tableau d'amortissement
			val vLeft     = (-inLedger.balanceAt(inAccount.id, inToday)).coerceAtLeast(0L)	// capital restant dû
			TilePreview.Loan(
				remaining = vLeft,
				next      = vSchedule.firstOrNull { it.date.isAfter(inToday) },
				end       = vSchedule.lastOrNull()?.date,
				paid      = vLoan?.let { (((it.principal - vLeft) * 100) / it.principal).toInt().coerceIn(0, 100) }
			)
		}
		AccountType.REAL_ESTATE, AccountType.COLLECTIBLE ->
		{
			val vItems = propertyItems(inData.transactions, inData.accounts, inAccount.id)	// biens du compte
			val vHeld  = vItems.filter { it.heldAt(inToday) }.sortedByDescending { it.cost }	// biens détenus
			TilePreview.Property(
				count  = vHeld.size,
				items  = vHeld.take(kTileTopItems).map { it.name to it.cost },
				others = vHeld.drop(kTileTopItems).sumOf { it.cost },
				gains  = vItems.filter { it.disposal != null && !it.disposal.date.isAfter(inToday) }.sumOf { it.gain ?: 0L }
			)
		}
		AccountType.BROKERAGE ->
		{
			val vValue    = valuePortfolio(inLedger, inAccount.id, inToday, inData.prices)	// portefeuille
			val vNames    = inData.assets.associate { it.id to it.name }				// noms des actifs
			val vPercents = wholePercents(vValue.positions.map { it.valueCents.coerceAtLeast(0L) })	// parts des positions
			val vTop      = vValue.positions.take(kTileTopItems).mapIndexed { inIndex, inPos -> (vNames[inPos.assetId] ?: "Actif supprimé") to vPercents[inIndex] }	// plus grosses
			TilePreview.Brokerage(
				cashCents       = vValue.cashCents,
				unrealizedCents = vValue.unrealizedCents,
				costCents       = vValue.positions.sumOf { it.costCents },
				positions       = vTop,
				others          = vPercents.drop(kTileTopItems).sum()
			)
		}
	}
}

/*
	Dépenses par catégorie de plusieurs comptes, additionnées et triées.
	inLines : lignes de chaque compte
*/
private fun mergeCategories(inLines: List<List<CategoryLine>>): List<CategoryLine>
{
	val vTotals = inLines.flatten().groupBy { it.category.id }
		.map { (_, inSame) -> inSame.first().category to inSame.sumOf { it.netCents } }
		.filter { it.second != 0L }													// sommes nettes par catégorie
	val vPercents = wholePercents(vTotals.map { it.second.coerceAtLeast(0L) })		// parts entières
	return vTotals.mapIndexed { inIndex, (inCategory, inNet) -> CategoryLine(inCategory, inNet, vPercents[inIndex]) }
		.sortedByDescending { it.netCents }
}

/*
	Opération récente : montant et détail lisibles hors de la vue d'un compte.
	inTx   : opération
	inById : comptes par identifiant
	inData : données (catégories)
*/
private fun recentOperation(
	inTx   : Transaction,
	inById : Map<String, Account>,
	inData : DataSet
): RecentOperation
{
	val vName     = { inId: String -> inById[inId]?.name ?: "Compte supprimé" }	// nom d'un compte
	val vCategory = inTx.categoryId?.let { inId -> inData.categories.firstOrNull { it.id == inId } }	// catégorie
	val vFirst    = inTx.postings.first()											// jambe principale
	return when (inTx.kind)
	{
		TransactionKind.TRANSFER ->
		{
			val vFrom = inTx.postings.firstOrNull { it.amountCents < 0 } ?: vFirst	// compte débité
			val vTo   = inTx.postings.firstOrNull { it.amountCents > 0 } ?: vFirst	// compte crédité
			RecentOperation(inTx, vTo.amountCents, inTx.label, "${vName(vFrom.accountId)} → ${vName(vTo.accountId)}", "repeat")
		}
		TransactionKind.TRADE    -> RecentOperation(inTx, vFirst.amountCents, inTx.label, "${vName(vFirst.accountId)} · Ordre de bourse", "chart")
		TransactionKind.LOAN_PAYMENT -> RecentOperation(inTx, vFirst.amountCents, inTx.label, "${vName(vFirst.accountId)} → ${inTx.postings.getOrNull(1)?.let { vName(it.accountId) } ?: "Crédit"} · Mensualité", vCategory?.icon ?: "bank")
		TransactionKind.ACQUISITION, TransactionKind.DISPOSAL -> RecentOperation(inTx, vFirst.amountCents, inTx.label, "${vName(vFirst.accountId)} · ${propertyNature(inTx)}", propertyIcon(inTx, inById))
		else                     -> RecentOperation(inTx, vFirst.amountCents, inTx.label, "${vName(vFirst.accountId)} · ${vCategory?.name ?: "Sans catégorie"}", vCategory?.icon ?: "dots")
	}
}
