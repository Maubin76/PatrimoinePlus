package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.LocalBankPicker
import fr.maubin.patrimoineplus.desktop.ui.components.Cell
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.desktop.ui.components.OperationLine
import fr.maubin.patrimoineplus.desktop.ui.components.TileRow
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint
import fr.maubin.patrimoineplus.domain.calc.allocation
import fr.maubin.patrimoineplus.domain.format.formatPermille
import fr.maubin.patrimoineplus.domain.format.formatPrice
import fr.maubin.patrimoineplus.domain.format.formatScaled
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.account.AccountDetailState
import fr.maubin.patrimoineplus.ui.account.AccountExtras
import fr.maubin.patrimoineplus.ui.account.OperationItem
import fr.maubin.patrimoineplus.ui.account.PortfolioView
import fr.maubin.patrimoineplus.ui.account.buildAccountDetail
import fr.maubin.patrimoineplus.ui.account.buildAccountExtras
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.BalanceChart
import fr.maubin.patrimoineplus.ui.components.BarSegment
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.MonthSwitcher
import fr.maubin.patrimoineplus.ui.components.ProportionBar
import fr.maubin.patrimoineplus.ui.components.SeriesDot
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.components.TileFigure
import fr.maubin.patrimoineplus.ui.components.allocationColor
import fr.maubin.patrimoineplus.ui.components.kAllocationSlots
import fr.maubin.patrimoineplus.ui.components.monthTitle
import fr.maubin.patrimoineplus.ui.home.accountDetail
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.Button
import fr.maubin.patrimoineplus.domain.calc.propertyItems
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.ui.property.editPurchaseForm
import fr.maubin.patrimoineplus.ui.property.editSaleForm
import fr.maubin.patrimoineplus.ui.property.newPurchaseForm
import fr.maubin.patrimoineplus.ui.property.newSaleForm
import fr.maubin.patrimoineplus.ui.property.propertyNoun
import fr.maubin.patrimoineplus.domain.calc.amortization
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import fr.maubin.patrimoineplus.domain.calc.propertyNets
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.desktop.ui.panels.Picker
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import fr.maubin.patrimoineplus.ui.components.ProportionChart
import fr.maubin.patrimoineplus.ui.components.ProportionToggle
import fr.maubin.patrimoineplus.ui.components.LocalAccent
import fr.maubin.patrimoineplus.ui.components.accountAccent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.maubin.patrimoineplus.desktop.ui.tour.tourAnchor

private val kDayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)	// en-tête d'un jour
private val kShortDay = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)		// date d'une opération
private val kFullDay  = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)	// date d'un relevé

/*
	Vue d'un compte en tuiles, adaptée à son type : mois par mois pour un
	compte Courant, globale pour un compte Épargne ou Bourse. Mêmes calculs
	que l'appli Android (buildAccountDetail, buildAccountExtras).
	inSpace      : données
	inController : contrôleur (mois, retour, saisies)
	inAccountId  : compte
	inMonth      : mois affiché (compte Courant) ; null : mois courant
*/
@Composable
fun AccountScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inAccountId  : String,
	inMonth      : YearMonth?
)
{
	val vData   = inSpace.data														// données
	val vState  = remember(vData, inAccountId, inMonth, inSpace.today) {
		buildAccountDetail(inAccountId, vData.accounts, vData.categories, vData.transactions, inMonth ?: YearMonth.from(inSpace.today), inSpace.today)
	}																				// état de la vue
	val vExtras = remember(vData, inAccountId, inSpace.today) {
		buildAccountExtras(inAccountId, vData.accounts, vData.transactions, vData.statements, vData.prices, vData.assets, inSpace.today, vData.loans)
	}																				// relevés ou portefeuille
	val vAccount = vState.account
	if (vAccount == null)
	{
		LaunchedEffect(inAccountId) { inController.back() }							// compte disparu : retour au tableau de bord
		return
	}
	val vOpen: (OperationItem) -> Unit = { inItem ->
		inController.openTransaction(inItem.transaction)
	}																				// ouvre une opération
	val vAccent = accountAccent(vAccount.type)										// couleur du type (thème Coloré)
	CompositionLocalProvider(LocalAccent provides vAccent) {
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = inController::back) { Icon(LineIcons.Back, contentDescription = "Retour au tableau de bord") }
			Spacer(Modifier.width(8.dp))
			if (vAccent != Color.Unspecified)
			{
				Box(Modifier.width(6.dp).height(44.dp).clip(RoundedCornerShape(3.dp)).background(vAccent))	// marque du type
				Spacer(Modifier.width(14.dp))
			}
			Column(Modifier.weight(1f).tourAnchor("account.header")) {
				Text(vAccount.name, style = MaterialTheme.typography.displaySmall)
				Text(accountDetail(vAccount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			OutlinedButton(onClick = { inController.openPanel(Panel.AccountSheet(vAccount.id)) }) { Text("Modifier le compte") }
		}
		when (vAccount.type)
		{
			AccountType.CHECKING  -> CheckingView(vState, vExtras, inController, vOpen)
			AccountType.SAVINGS   -> SavingsView(vState, vExtras, inController, vOpen)
			AccountType.BROKERAGE -> BrokerageView(vState, vExtras, inController, vOpen)
			AccountType.REAL_ESTATE, AccountType.COLLECTIBLE -> PropertyView(vState, inSpace, inController, vAccount)
			AccountType.LOAN      -> LoanView(vState, inSpace, inController, vAccount, vOpen)
		}
	}
	}
}

/*
	Compte Courant, mois par mois : sélecteur de mois et chiffres du mois en
	tuiles, puis courbe et dépenses par catégorie, puis opérations et relevés.
	inState      : état du mois
	inExtras     : relevés
	inController : contrôleur
	inOnOpen     : ouvre une opération
*/
@Composable
private fun CheckingView(
	inState      : AccountDetailState,
	inExtras     : AccountExtras,
	inController : DesktopController,
	inOnOpen     : (OperationItem) -> Unit
)
{
	Row(Modifier.width(420.dp)) {
		Box(Modifier.tourAnchor("account.month")) { MonthSwitcher(inState.month, inState.firstMonth, inState.lastMonth, inController::showMonth) }
	}
	inState.summary?.let { inSummary ->
		val vCells = buildList {
			add(Cell(1f) { inMod -> Figure("Ouverture", inSummary.opening, inMod, inSigned = false) })
			if (inSummary.openings != 0L) add(Cell(1f) { inMod -> Figure("Solde initial", inSummary.openings, inMod) })
			add(Cell(1f) { inMod -> Figure("Entrées", inSummary.income, inMod) })
			add(Cell(1f) { inMod -> Figure("Dépenses", -inSummary.expenses, inMod, inSigned = false) })
			if (inSummary.savings != 0L) add(Cell(1f) { inMod -> Figure("Épargne", inSummary.savings, inMod) })
			if (inSummary.transfers != 0L) add(Cell(1f) { inMod -> Figure("Virements internes", inSummary.transfers, inMod) })
			add(Cell(1.2f) { inMod -> Figure("Clôture", inSummary.closing, inMod, inSigned = false, inStrong = true) })
		}																			// chiffres du mois
		TileRow(*vCells.toTypedArray(), inMinWidth = 900.dp)
	}
	TileRow(
		Cell(1.6f) { inMod -> ChartTile("Évolution du solde", inState.series, inState.month, inMod) },
		Cell(1f) { inMod ->
			Tile(inModifier = inMod, inTitle = "Dépenses par catégorie") {
				val vLines = inState.expenses.filter { it.netCents > 0 }			// catégories dépensières
				if (vLines.isEmpty()) Hint("Aucune dépense ce mois-ci.")
				vLines.forEach { inLine -> CategoryBar(inLine, vLines.first().netCents) }
			}
		}
	)
	TileRow(
		Cell(1.6f) { inMod ->
			Tile(inModifier = inMod.tourAnchor("account.operations"), inTitle = "Opérations de ${monthTitle(inState.month).lowercase()}") {
				if (inState.upcoming.isEmpty() && inState.days.isEmpty()) Hint("Aucune opération ce mois-ci.")
				if (inState.upcoming.isNotEmpty()) OperationsGroup("À venir", inState.upcoming, true, inOnOpen)
				inState.days.forEach { inDay -> OperationsGroup(inDay.date.format(kDayTitle).replaceFirstChar { it.uppercase() }, inDay.items, false, inOnOpen) }
			}
		},
		Cell(1f) { inMod -> StatementsTile(inState, inExtras, inController, inMod) }
	)
}

/*
	Compte Épargne, vue globale : solde et mouvements depuis l'ouverture en
	tuiles, puis courbe et relevés, puis opérations par mois.
	inState      : état global
	inExtras     : relevés
	inController : contrôleur
	inOnOpen     : ouvre une opération
*/
@Composable
private fun SavingsView(
	inState      : AccountDetailState,
	inExtras     : AccountExtras,
	inController : DesktopController,
	inOnOpen     : (OperationItem) -> Unit
)
{
	inState.totals?.let { inTotals ->
		TileRow(
			Cell(1.3f) { inMod -> Figure("Solde au jour", inTotals.balanceCents, inMod, inSigned = false, inStrong = true) },
			Cell(1f) { inMod -> Figure("Solde initial", inTotals.opening, inMod, inSigned = false) },
			Cell(1f) { inMod -> Figure("Versements nets", inTotals.transfers, inMod) },
			Cell(1f) { inMod -> Figure("Intérêts et revenus", inTotals.income, inMod, inTrend = true) },
			inMinWidth = 900.dp
		)
	}
	TileRow(
		Cell(1.6f) { inMod -> ChartTile("Évolution du solde", inState.series, inState.series.lastOrNull()?.month, inMod) },
		Cell(1f) { inMod -> StatementsTile(inState, inExtras, inController, inMod) }
	)
	OperationsTile(inState, inOnOpen)
}

/*
	Compte Bourse, vue globale : valeur et plus-values en tuiles, puis courbe
	de la valeur et répartition des titres, puis positions et opérations.
	inState      : état global
	inExtras     : portefeuille
	inController : contrôleur
	inOnOpen     : ouvre une opération
*/
@Composable
private fun BrokerageView(
	inState      : AccountDetailState,
	inExtras     : AccountExtras,
	inController : DesktopController,
	inOnOpen     : (OperationItem) -> Unit
)
{
	val vPortfolio = inExtras.portfolio ?: return									// portefeuille
	val vRate      = if (vPortfolio.costCents > 0) vPortfolio.valuation.unrealizedCents * 1000 / vPortfolio.costCents else null	// rendement, en pour mille
	TileRow(
		Cell(1.3f) { inMod -> Figure("Valeur du compte", vPortfolio.valuation.valueCents, inMod, inSigned = false, inStrong = true) },
		Cell(1f) { inMod -> Figure("Espèces", vPortfolio.valuation.cashCents, inMod, inSigned = false) },
		Cell(1f) { inMod -> Figure("Plus-value latente", vPortfolio.valuation.unrealizedCents, inMod, inTrend = true, inNote = vRate?.let(::formatPermille)) },
		Cell(1f) { inMod -> Figure("Plus-values réalisées", vPortfolio.realizedCents, inMod, inTrend = true) },
		inMinWidth = 900.dp
	)
	TileRow(
		Cell(1.6f) { inMod -> ChartTile("Évolution de la valeur", inExtras.valueSeries, inExtras.valueSeries.lastOrNull()?.month, inMod) },
		Cell(1f) { inMod -> AllocationTile(vPortfolio, inController, inMod, "titres:${inState.account?.id}") }
	)
	inState.account?.let { inAccount -> PositionsTile(vPortfolio, inExtras, inAccount.id, inController) }
	OperationsTile(inState, inOnOpen)
}

/*
	Compte Immobilier ou Collection : valeur au prix d'achat, nombre de biens
	et plus-values des ventes en tuiles, puis courbe de la valeur, puis biens
	détenus (ajout, modification, vente) et biens vendus.
	inState      : état global (courbe)
	inSpace      : données
	inController : contrôleur
	inAccount    : compte de biens
*/
@Composable
private fun PropertyView(
	inState      : AccountDetailState,
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inAccount    : Account
)
{
	val vItems = remember(inSpace.data, inAccount.id) { propertyItems(inSpace.data.transactions, inSpace.data.accounts, inAccount.id) }	// biens du compte
	val vToday = inSpace.today																// date du jour
	val vSold  = vItems.filter { inItem -> inItem.disposal?.date?.isAfter(vToday) == false }	// biens vendus
	val vHeld  = vItems - vSold.toSet()														// biens détenus (ou achetés plus tard)
	val vNoun  = propertyNoun(inAccount.type)												// « bien » ou « objet »
	val vNames = inSpace.data.accounts.associate { it.id to it.name }						// noms des comptes
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	val vValue = vHeld.filter { it.heldAt(vToday) }.sumOf { it.cost }						// valeur au prix d'achat
	val vNets  = remember(inSpace.data, inAccount.id, vToday) { propertyNets(vItems, inSpace.data.loans, Ledger(inSpace.data.transactions), vToday) }	// biens financés par un crédit
	TileRow(
		Cell(1.3f) { inMod -> Figure("Valeur au prix d’achat", vValue, inMod, inSigned = false, inStrong = true, inNote = if (inAccount.sharePercent < 100) "Comptée pour ${inAccount.sharePercent} % dans le patrimoine" else null) },
		*(if (vNets.isEmpty()) emptyArray() else arrayOf(Cell(1f) { inMod -> Figure("Valeur nette", vValue - vNets.values.sumOf { it.debt }, inMod, inSigned = false, inNote = "Crédits restant dus déduits") })),
		Cell(1f) { inMod -> Tile(inModifier = inMod) { TileFigure(if (vNoun == "bien") "Biens détenus" else "Objets détenus") { Text(vHeld.size.toString(), style = MaterialTheme.typography.titleLarge) } } },
		Cell(1f) { inMod -> Figure("Plus-values des ventes", vSold.sumOf { it.gain ?: 0L }, inMod, inTrend = true) },
		inMinWidth = 900.dp
	)
	ChartTile("Évolution de la valeur", inState.series, inState.series.lastOrNull()?.month, Modifier.fillMaxWidth())
	Tile(
		inTitle  = if (vNoun == "bien") "Biens détenus" else "Objets détenus",
		inAction = { Button(onClick = { inController.openPanel(Panel.Purchase(newPurchaseForm(inAccount.id, vToday))) }) { Text("Ajouter un $vNoun") } }
	) {
		if (vHeld.isEmpty()) Hint("Aucun $vNoun : ajoutez-en un avec son prix d’achat. Payé depuis un compte, il en débite le prix ; déjà possédé, il n’en débite aucun.")
		vHeld.forEach { inItem ->
			Row(
				modifier          = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = "Modifier") { inController.openPanel(Panel.Purchase(editPurchaseForm(inItem))) }.padding(horizontal = 8.dp, vertical = 6.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(CategoryIcons.of(if (inAccount.type == AccountType.REAL_ESTATE) "home" else "gem"), contentDescription = null, modifier = Modifier.size(20.dp))
				Spacer(Modifier.width(14.dp))
				val vNet = vNets[inItem.acquisition.id]												// crédit du bien
				Column(Modifier.weight(1f)) {
					Text(inItem.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
					Text("Acheté le ${inItem.acquisition.date.format(kFullDay)} · " + (inItem.paidFrom?.let { "payé depuis ${vNames[it] ?: "un compte supprimé"}" } ?: "déjà possédé"), style = MaterialTheme.typography.bodyMedium, color = vMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
					if (vNet != null)
					{
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text("${vNet.loans.joinToString { vNames[it.accountId] ?: "Crédit" }} : reste dû ", style = MaterialTheme.typography.bodyMedium, color = vMuted)
							AmountText(inCents = vNet.debt, inStyle = MaterialTheme.typography.bodyMedium, inColor = vMuted)
							Text(" · valeur nette ", style = MaterialTheme.typography.bodyMedium, color = vMuted)
							AmountText(inCents = vNet.net, inStyle = MaterialTheme.typography.bodyMedium, inColor = vMuted)
						}
					}
				}
				AmountText(inCents = inItem.cost, inStyle = MaterialTheme.typography.bodyLarge)
				Spacer(Modifier.width(16.dp))
				OutlinedButton(onClick = { inController.openPanel(Panel.Sale(inItem.disposal?.let { editSaleForm(inItem) } ?: newSaleForm(inItem, vToday))) }) { Text(if (inItem.disposal != null) "Vente prévue" else "Vendre") }
			}
		}
	}
	if (vSold.isNotEmpty())
	{
		Tile(inTitle = if (vNoun == "bien") "Biens vendus" else "Objets vendus") {
			Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp)) {
				listOf("Nom" to 2.4f, "Achat" to 1f, "Vente" to 1f, "Plus-value" to 1f).forEach { (inHead, inWeight) ->
					Text(inHead, style = MaterialTheme.typography.labelLarge, color = vMuted, modifier = Modifier.weight(inWeight))
				}
			}
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			vSold.forEach { inItem ->
				Row(
					modifier          = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = "Modifier la vente") { inController.openPanel(Panel.Sale(editSaleForm(inItem))) }.padding(horizontal = 8.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Column(Modifier.weight(2.4f)) {
						Text(inItem.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
						Text("Vendu le ${inItem.disposal!!.date.format(kFullDay)}" + (inItem.soldTo?.let { " · versé sur ${vNames[it] ?: "un compte supprimé"}" } ?: " · sans versement"), style = MaterialTheme.typography.bodyMedium, color = vMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
					}
					AmountText(inCents = inItem.cost, inStyle = MaterialTheme.typography.bodyLarge, inModifier = Modifier.weight(1f))
					AmountText(inCents = inItem.salePrice ?: 0L, inStyle = MaterialTheme.typography.bodyLarge, inModifier = Modifier.weight(1f))
					AmountText(inCents = inItem.gain ?: 0L, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = true, inTrend = true, inModifier = Modifier.weight(1f))
				}
			}
		}
	}
}

/*
	Compte Crédit : capital restant dû, mensualité, intérêts restants et fin
	en tuiles, courbe du solde, puis tableau d'amortissement (mensualités
	passées en retrait, prochaine en gras) et opérations.
	inState      : état global (courbe, opérations)
	inSpace      : données (crédit)
	inController : contrôleur
	inAccount    : compte Crédit
	inOnOpen     : ouvre une opération
*/
@Composable
private fun LoanView(
	inState      : AccountDetailState,
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inAccount    : Account,
	inOnOpen     : (OperationItem) -> Unit
)
{
	val vLoan  = inSpace.data.loans.firstOrNull { it.accountId == inAccount.id }			// crédit du compte
	val vRows  = remember(vLoan) { vLoan?.let(::amortization).orEmpty() }				// tableau d'amortissement
	val vToday = inSpace.today																// date du jour
	val vNext  = vRows.firstOrNull { it.date.isAfter(vToday) }							// prochaine mensualité
	val vLeft  = -(inState.totals?.balanceCents ?: 0L)										// capital restant dû
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	TileRow(
		Cell(1.3f) { inMod -> Figure("Capital restant dû", vLeft, inMod, inSigned = false, inStrong = true, inNote = if (inAccount.sharePercent < 100) "Compté pour ${inAccount.sharePercent} % dans le patrimoine" else null) },
		Cell(1f) { inMod -> Figure("Mensualité", vNext?.total ?: vRows.lastOrNull()?.total ?: 0L, inMod, inSigned = false, inNote = vNext?.let { "Prochaine le ${it.date.format(kFullDay)}" }) },
		Cell(1f) { inMod -> Figure("Intérêts restants", vRows.filter { it.date.isAfter(vToday) }.sumOf { it.interest }, inMod, inSigned = false, inNote = vRows.lastOrNull()?.let { "Dernière mensualité le ${it.date.format(kFullDay)}" }) },
		inMinWidth = 900.dp
	)
	ChartTile("Évolution du capital restant dû", inState.series.map { it.copy(valueCents = -it.valueCents) }, inState.series.lastOrNull()?.month, Modifier.fillMaxWidth())
	if (vLoan != null)
	{
		FinancedTile(inSpace, inController, vLoan)
	}
	if (vLoan == null)
	{
		Tile(inTitle = "Tableau d’amortissement") { Hint("Ce compte n’a pas de tableau d’amortissement : ses remboursements se saisissent comme des virements.") }
	}
	else
	{
		Tile(
			inTitle  = "Tableau d’amortissement",
			inAction = { Text("${if (LocalAmountsHidden.current) kMaskedAmount else formatEuros(vLoan.principal)} à ${rateText(vLoan.rateBp)} sur ${vLoan.months} mois", style = MaterialTheme.typography.bodyMedium, color = vMuted) }
		) {
			Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp)) {
				listOf("N°" to 0.5f, "Date" to 1.2f, "Mensualité" to 1f, "Capital" to 1f, "Intérêts" to 1f, "Assurance" to 1f, "Restant dû" to 1.2f).forEach { (inHead, inWeight) ->
					Text(inHead, style = MaterialTheme.typography.labelLarge, color = vMuted, modifier = Modifier.weight(inWeight))
				}
			}
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			vRows.forEach { inRow ->
				val vPast  = !inRow.date.isAfter(vToday)												// mensualité passée
				val vColor = if (vPast) vMuted else MaterialTheme.colorScheme.onSurface				// teinte de la ligne
				val vStyle = if (inRow == vNext) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium	// prochaine en gras
				Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
					Text(inRow.index.toString(), style = vStyle, color = vColor, modifier = Modifier.weight(0.5f))
					Text(inRow.date.format(kFullDay), style = vStyle, color = vColor, modifier = Modifier.weight(1.2f))
					AmountText(inCents = inRow.total, inStyle = vStyle, inColor = vColor, inModifier = Modifier.weight(1f))
					AmountText(inCents = inRow.capital, inStyle = vStyle, inColor = vColor, inModifier = Modifier.weight(1f))
					AmountText(inCents = inRow.interest, inStyle = vStyle, inColor = vColor, inModifier = Modifier.weight(1f))
					AmountText(inCents = inRow.insurance, inStyle = vStyle, inColor = vColor, inModifier = Modifier.weight(1f))
					AmountText(inCents = inRow.remaining, inStyle = vStyle, inColor = vColor, inModifier = Modifier.weight(1.2f))
				}
			}
			Spacer(Modifier.height(8.dp))
			Hint("Chaque mensualité crée à sa date une seule opération : ${inSpace.data.accounts.firstOrNull { it.id == vLoan.payFrom }?.name ?: "le compte payeur"} débité du total, le capital remboursé ici (il fait baisser le capital dû), les intérêts et l’assurance comptés en dépense.")
		}
	}
	OperationsTile(inState, inOnOpen)
}

/*
	Tuile du bien financé par un crédit : choix du bien (ou aucun), puis prix
	d'achat, capital restant dû des crédits du bien et valeur nette.
	inSpace      : données
	inController : contrôleur
	inLoan       : crédit
*/
@Composable
private fun FinancedTile(
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inLoan       : Loan
)
{
	val vExtras = remember(inSpace.data, inLoan, inSpace.today) {
		buildAccountExtras(inLoan.accountId, inSpace.data.accounts, inSpace.data.transactions, inSpace.data.statements, inSpace.data.prices, inSpace.data.assets, inSpace.today, inSpace.data.loans)
	}																				// bien financé et biens possibles
	var vError by remember(inLoan) { mutableStateOf<String?>(null) }				// refus
	Tile(inTitle = "Bien financé") {
		Picker(
			"Bien",
			listOf<Pair<String?, String>>(null to "Aucun (auto, prêt personnel…)") + vExtras.candidates.map { it.acquisition.id to it.name },
			inLoan.propertyId,
			{ inId -> (inController.edit { inController.editor.setLoanProperty(it, inLoan.id, inId) } as? EditResult.Refused)?.let { vError = it.messages.joinToString(" ") } },
			vError
		)
		val vNet = vExtras.financedNet													// valeur nette du bien
		when
		{
			vNet != null                -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
				TileFigure("Prix d’achat") { AmountText(inCents = vNet.item.cost, inStyle = MaterialTheme.typography.titleLarge) }
				TileFigure(if (vNet.loans.size > 1) "Crédits restant dus" else "Restant dû") { AmountText(inCents = -vNet.debt, inStyle = MaterialTheme.typography.titleLarge) }
				TileFigure("Valeur nette") { AmountText(inCents = vNet.net, inStyle = MaterialTheme.typography.titleLarge) }
			}
			vExtras.financed != null    -> Hint("Bien vendu : il ne compte plus dans le patrimoine.")
			else                        -> Hint("Liez le crédit au bien qu’il finance pour voir sa valeur nette (prix d’achat moins capital restant dû).")
		}
	}
}

/*
	Taux en points de base écrit en % (« 3,45 % »).
	inBp : taux, en points de base
*/
private fun rateText(inBp: Int): String = "${inBp / 100},${(inBp % 100).toString().padStart(2, '0')} %"

/*
	Tuile d'un chiffre clé : libellé et montant.
	inLabel    : libellé
	inCents    : montant
	inModifier : place de la tuile
	inSigned   : signe « + » devant un montant positif
	inStrong   : chiffre mis en avant (plus grand)
	inTrend    : hausse ou baisse (couleur si le réglage est activé)
	inNote     : texte discret sous le montant ; null sans texte
*/
@Composable
private fun Figure(
	inLabel    : String,
	inCents    : Long,
	inModifier : Modifier,
	inSigned   : Boolean = true,
	inStrong   : Boolean = false,
	inTrend    : Boolean = false,
	inNote     : String? = null
)
{
	Tile(inModifier = inModifier, inAccent = if (inStrong) LocalAccent.current else Color.Unspecified) {	// chiffre principal : tuile à la couleur du compte
		TileFigure(inLabel) {
			AmountText(
				inCents        = inCents,
				inStyle        = if (inStrong) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
				inShowPlusSign = inSigned,
				inTrend        = inTrend
			)
		}
		if (inNote != null && !LocalAmountsHidden.current) Hint(inNote)
	}
}

/*
	Tuile d'une courbe ; message si la série est trop courte.
	inTitle     : titre
	inSeries    : valeurs de fin de mois
	inHighlight : mois mis en avant
	inModifier  : place de la tuile
*/
@Composable
private fun ChartTile(
	inTitle     : String,
	inSeries    : List<SeriesPoint>,
	inHighlight : YearMonth?,
	inModifier  : Modifier
)
{
	Tile(inModifier = inModifier, inTitle = inTitle) {
		if (inSeries.size > 1 && inHighlight != null) BalanceChart(inSeries, inHighlight, inHeight = 220.dp)
		else Hint("La courbe apparaîtra après un premier mois d’historique.")
	}
}

/*
	Tuile des relevés de la banque : ajout, écart au solde calculé,
	suppression.
	inState      : état du compte
	inExtras     : relevés
	inController : contrôleur
	inModifier   : place de la tuile
*/
@Composable
private fun StatementsTile(
	inState      : AccountDetailState,
	inExtras     : AccountExtras,
	inController : DesktopController,
	inModifier   : Modifier
)
{
	Tile(
		inModifier = inModifier,
		inTitle    = "Relevés",
		inAction   = {
			val vPick = LocalBankPicker.current										// choix du relevé de la banque
			TextButton(onClick = { inState.account?.let { inAccount -> vPick()?.let { inController.openBankStatement(inAccount.id, it) } } }) { Text("Rapprocher un relevé…") }
			TextButton(onClick = { inState.account?.let { inController.openPanel(Panel.Statement(it.id)) } }) { Text("Ajouter") }
		}
	) {
		if (inExtras.statements.isEmpty()) Hint("Saisissez le solde indiqué par la banque : l’appli le compare au solde calculé.")
		inExtras.statements.forEachIndexed { inIndex, inItem ->
			if (inIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text(inItem.statement.date.format(kFullDay), style = MaterialTheme.typography.bodyLarge)
					if (inItem.gapCents != 0L)
					{
						Row {
							Text("Écart ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
							AmountText(inCents = inItem.gapCents, inStyle = MaterialTheme.typography.bodyMedium, inColor = MaterialTheme.colorScheme.onSurfaceVariant, inShowPlusSign = true)
						}
					}
					else
					{
						Text("Conforme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
				AmountText(inCents = inItem.statement.balanceCents, inStyle = MaterialTheme.typography.bodyLarge)
				IconButton(onClick = { inController.edit { inController.editor.deleteStatement(it, inItem.statement.id) } }) {
					Icon(LineIcons.Close, contentDescription = "Supprimer le relevé du ${inItem.statement.date.format(kFullDay)}", modifier = Modifier.size(16.dp))
				}
			}
		}
	}
}

/*
	Tuile de la répartition des titres d'un compte Bourse : barre, puis part de
	chaque titre ; un clic ouvre la fiche de l'actif.
	inPortfolio  : portefeuille
	inController : contrôleur
	inModifier   : place de la tuile
	inKey        : clé du diagramme (forme mémorisée pour ce compte)
*/
@Composable
private fun AllocationTile(
	inPortfolio  : PortfolioView,
	inController : DesktopController,
	inModifier   : Modifier,
	inKey        : String
)
{
	val vLines = allocation(inPortfolio.valuation.positions)						// répartition des titres
	Tile(inModifier = inModifier, inTitle = "Répartition des titres", inAction = { if (vLines.isNotEmpty()) ProportionToggle(inKey) }) {
		if (vLines.isEmpty())
		{
			Hint("Aucune position.")
			return@Tile
		}
		ProportionChart(
			inKey         = inKey,
			inSegments    = vLines.withIndex().groupBy { it.index.coerceAtMost(kAllocationSlots - 1) }.toSortedMap()
				.map { (inRank, inGroup) -> BarSegment(inGroup.sumOf { it.value.position.valueCents }, allocationColor(inRank)) },
			inDescription = "Répartition des titres"
		) {
			Column {
				vLines.forEachIndexed { inIndex, inLine ->
					Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
						SeriesDot(allocationColor(inIndex))
						Spacer(Modifier.width(12.dp))
						TextButton(onClick = { inController.openPanel(Panel.AssetSheet(inLine.position.assetId)) }, modifier = Modifier.weight(1f)) {
							Text(inPortfolio.names[inLine.position.assetId] ?: "Actif supprimé", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
						}
						Text("${inLine.percent} %", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			}
		}
	}
}

/*
	Tuile des positions d'un compte Bourse : tableau (quantité, PRU, cours,
	valeur, plus-value), actions (cours, positions initiales), positions
	initiales.
	inPortfolio  : portefeuille
	inExtras     : positions initiales
	inAccountId  : compte Bourse
	inController : contrôleur
*/
@Composable
private fun PositionsTile(
	inPortfolio  : PortfolioView,
	inExtras     : AccountExtras,
	inAccountId  : String,
	inController : DesktopController
)
{
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant							// texte secondaire
	Tile(
		inTitle  = "Positions",
		inAction = {
			TextButton(onClick = { inController.openPanel(Panel.Prices(inAccountId)) }, enabled = inPortfolio.valuation.positions.isNotEmpty()) { Text("Saisir des cours") }
			TextButton(onClick = { inController.openPanel(Panel.OpeningPosition(inAccountId)) }) { Text("Position initiale") }
		}
	) {
		if (inPortfolio.valuation.positions.isEmpty())
		{
			Hint("Aucune position : saisissez un ordre d’achat, ou une position initiale.")
		}
		else
		{
			Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
				listOf("Titre" to 2.2f, "Quantité" to 1f, "PRU" to 1f, "Cours" to 1f, "Valeur" to 1.2f, "Plus-value" to 1.2f).forEach { (inHead, inWeight) ->
					Text(inHead, style = MaterialTheme.typography.labelLarge, color = vMuted, modifier = Modifier.weight(inWeight))
				}
			}
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			inPortfolio.valuation.positions.forEach { inPos ->
				Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
					Text(
						text     = inPortfolio.names[inPos.assetId] ?: "Actif supprimé",
						style    = MaterialTheme.typography.bodyLarge,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(2.2f).clickable(onClickLabel = "Fiche de l’actif") { inController.openPanel(Panel.AssetSheet(inPos.assetId)) }.padding(vertical = 12.dp)
					)
					Text(formatScaled(inPos.quantity), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
					Text(if (LocalAmountsHidden.current) "•••" else formatPrice(fr.maubin.patrimoineplus.domain.calc.averagePrice(inPos.costCents, inPos.quantity)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
					Column(Modifier.weight(1f)) {
						Text(formatPrice(inPos.unitPrice), style = MaterialTheme.typography.bodyMedium)
						Text(inPos.priceDate?.let { "au ${it.format(kShortDay)}" } ?: "sans cours", style = MaterialTheme.typography.bodySmall, color = vMuted)
					}
					AmountText(inCents = inPos.valueCents, inStyle = MaterialTheme.typography.bodyLarge, inModifier = Modifier.weight(1.2f))
					AmountText(inCents = inPos.unrealizedCents, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = true, inTrend = true, inModifier = Modifier.weight(1.2f))
				}
			}
		}
		if (inExtras.openingPositions.isNotEmpty())
		{
			Spacer(Modifier.height(16.dp))
			Text("Positions initiales", style = MaterialTheme.typography.labelLarge, color = vMuted)
			inExtras.openingPositions.forEach { inItem ->
				Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
					Text("${inItem.assetName} : ${formatScaled(inItem.trade.quantity)} × ${formatPrice(inItem.trade.unitPrice)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
					IconButton(onClick = {
						when (val vResult = inController.edit { inController.editor.removeOpeningPosition(it, inAccountId, inItem.trade.id) })
						{
							is fr.maubin.patrimoineplus.data.edit.EditResult.Refused -> inController.showNotice(vResult.messages.joinToString(" "))
							else                                                     -> Unit
						}
					}) {
						Icon(LineIcons.Close, contentDescription = "Retirer la position initiale ${inItem.assetName}", modifier = Modifier.size(16.dp))
					}
				}
			}
		}
	}
}

/*
	Tuile des opérations d'une vue globale : « À venir », puis par mois.
	inState  : état global
	inOnOpen : ouvre une opération
*/
@Composable
private fun OperationsTile(
	inState  : AccountDetailState,
	inOnOpen : (OperationItem) -> Unit
)
{
	Tile(inTitle = "Opérations") {
		if (inState.upcoming.isEmpty() && inState.months.isEmpty()) Hint("Aucune opération.")
		if (inState.upcoming.isNotEmpty()) OperationsGroup("À venir", inState.upcoming, true, inOnOpen)
		inState.months.forEach { inGroup -> OperationsGroup(monthTitle(inGroup.month), inGroup.items, true, inOnOpen) }
	}
}

/*
	Groupe d'opérations sous un titre (jour, mois, « À venir »).
	inTitle    : titre du groupe
	inItems    : opérations
	inWithDate : affiche la date de chaque opération
	inOnOpen   : ouvre une opération (clic)
*/
@Composable
private fun OperationsGroup(
	inTitle    : String,
	inItems    : List<OperationItem>,
	inWithDate : Boolean,
	inOnOpen   : (OperationItem) -> Unit
)
{
	Text(inTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
	inItems.forEach { inItem ->
		OperationLine(
			inIcon    = inItem.icon,
			inTitle   = inItem.title,
			inDetail  = if (inWithDate) "${inItem.transaction.date.format(kShortDay)} · ${inItem.subtitle}" else inItem.subtitle,
			inCents   = inItem.amount,
			inOnClick = { inOnOpen(inItem) }
		)
	}
}

