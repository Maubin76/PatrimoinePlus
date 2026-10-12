package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.AmountText
import fr.maubin.patrimoineplus.desktop.ui.components.BalanceChart
import fr.maubin.patrimoineplus.desktop.ui.components.BarSegment
import fr.maubin.patrimoineplus.desktop.ui.components.BorderedCard
import fr.maubin.patrimoineplus.desktop.ui.components.MonthSwitcher
import fr.maubin.patrimoineplus.desktop.ui.components.ProportionBar
import fr.maubin.patrimoineplus.desktop.ui.components.SectionHeader
import fr.maubin.patrimoineplus.desktop.ui.components.SeriesDot
import fr.maubin.patrimoineplus.desktop.ui.components.allocationColor
import fr.maubin.patrimoineplus.desktop.ui.components.kAllocationSlots
import fr.maubin.patrimoineplus.desktop.ui.components.monthTitle
import fr.maubin.patrimoineplus.desktop.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.desktop.ui.icons.LineIcons
import fr.maubin.patrimoineplus.domain.calc.allocation
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.account.AccountDetailState
import fr.maubin.patrimoineplus.ui.account.AccountExtras
import fr.maubin.patrimoineplus.ui.account.OperationItem
import fr.maubin.patrimoineplus.ui.account.PortfolioView
import fr.maubin.patrimoineplus.ui.account.buildAccountDetail
import fr.maubin.patrimoineplus.ui.account.buildAccountExtras
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val kDayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)	// en-tête d'un jour
private val kShortDay = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)		// date d'une opération

/*
	Vue d'un compte, en deux colonnes, adaptée à son type comme sur le
	téléphone : mois par mois pour un compte Courant, globale pour un compte
	Épargne ou Bourse. Mêmes calculs que l'appli Android (buildAccountDetail,
	buildAccountExtras).
	inSpace     : données
	inAccountId : compte
	inMonth     : mois affiché (compte Courant) ; null : mois courant
	inOnMonth   : change de mois
	inOnBack    : retour à la liste
*/
@Composable
fun AccountScreen(
	inSpace     : WorkspaceState,
	inAccountId : String,
	inMonth     : YearMonth?,
	inOnMonth   : (YearMonth) -> Unit,
	inOnBack    : () -> Unit
)
{
	val vData   = inSpace.data													// données
	val vState  = remember(vData, inAccountId, inMonth, inSpace.today) {
		buildAccountDetail(inAccountId, vData.accounts, vData.categories, vData.transactions, inMonth ?: YearMonth.from(inSpace.today), inSpace.today)
	}																			// état de la vue
	val vExtras = remember(vData, inAccountId, inSpace.today) {
		buildAccountExtras(inAccountId, vData.accounts, vData.transactions, vData.statements, vData.prices, vData.assets, inSpace.today)
	}																			// relevés ou portefeuille
	val vAccount = vState.account ?: run { inOnBack(); return }				// compte (disparu : retour)
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 28.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = inOnBack) { Icon(LineIcons.Back, contentDescription = "Retour aux comptes") }
			Spacer(Modifier.width(8.dp))
			Column {
				Text(vAccount.name, style = MaterialTheme.typography.headlineMedium)
				Text(fr.maubin.patrimoineplus.ui.home.accountDetail(vAccount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
		Spacer(Modifier.height(28.dp))
		when (vAccount.type)
		{
			AccountType.CHECKING  -> CheckingView(vState, inOnMonth)
			AccountType.SAVINGS   -> SavingsView(vState, vExtras)
			AccountType.BROKERAGE -> BrokerageView(vState, vExtras)
		}
	}
}

/*
	Compte Courant : mois par mois. À gauche, sélecteur de mois, synthèse et
	dépenses par catégorie ; à droite, courbe du solde et opérations par jour.
	inState   : état du mois
	inOnMonth : change de mois
*/
@Composable
private fun CheckingView(
	inState   : AccountDetailState,
	inOnMonth : (YearMonth) -> Unit
)
{
	Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
		Column(Modifier.weight(1f)) {
			MonthSwitcher(inState.month, inState.firstMonth, inState.lastMonth, inOnMonth)
			Spacer(Modifier.height(12.dp))
			inState.summary?.let { inSummary ->
				BorderedCard {
					Column(Modifier.padding(vertical = 8.dp)) {
						StatRow("Ouverture", inSummary.opening, inMuted = true)
						if (inSummary.openings != 0L) StatRow("Solde initial", inSummary.openings, inSigned = true)
						StatRow("Entrées", inSummary.income, inSigned = true)
						StatRow("Dépenses", inSummary.expenses, inSigned = true)
						if (inSummary.savings != 0L) StatRow("Épargne", inSummary.savings, inSigned = true)
						if (inSummary.transfers != 0L) StatRow("Virements internes", inSummary.transfers, inSigned = true)
						HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
						StatRow("Clôture", inSummary.closing, inStrong = true)
					}
				}
			}
			Spacer(Modifier.height(32.dp))
			SectionHeader(inTitle = "Dépenses par catégorie")
			Spacer(Modifier.height(12.dp))
			BorderedCard {
				if (inState.expenses.isEmpty())
				{
					Text("Aucune dépense ce mois-ci.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
				}
				inState.expenses.forEach { inLine ->
					Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
						Icon(CategoryIcons.of(inLine.category.icon), contentDescription = null, modifier = Modifier.size(20.dp))
						Spacer(Modifier.width(14.dp))
						Text(inLine.category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
						Text("${inLine.percent} %", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
						Spacer(Modifier.width(20.dp))
						AmountText(inCents = inLine.netCents, inStyle = MaterialTheme.typography.bodyLarge)
					}
				}
			}
		}
		Column(Modifier.weight(1.2f)) {
			ChartSection("Évolution du solde", inState.series, inState.month)
			Spacer(Modifier.height(32.dp))
			SectionHeader(inTitle = "Opérations")
			if (inState.upcoming.isEmpty() && inState.days.isEmpty())
			{
				Text("Aucune opération ce mois-ci.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
			}
			if (inState.upcoming.isNotEmpty()) OperationsGroup("À venir", inState.upcoming, inWithDate = true)
			inState.days.forEach { inDay -> OperationsGroup(inDay.date.format(kDayTitle).replaceFirstChar { it.uppercase() }, inDay.items, inWithDate = false) }
		}
	}
}

/*
	Compte Épargne : vue globale. À gauche, solde et totaux depuis
	l'ouverture, puis relevés ; à droite, courbe du solde et opérations par mois.
	inState  : état global
	inExtras : relevés
*/
@Composable
private fun SavingsView(
	inState  : AccountDetailState,
	inExtras : AccountExtras
)
{
	Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
		Column(Modifier.weight(1f)) {
			inState.totals?.let { inTotals ->
				AmountText(inCents = inTotals.balanceCents, inStyle = MaterialTheme.typography.displaySmall)
				Text("Solde au jour", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				Spacer(Modifier.height(16.dp))
				BorderedCard {
					Column(Modifier.padding(vertical = 8.dp)) {
						StatRow("Solde initial", inTotals.opening, inMuted = true)
						StatRow("Versements nets", inTotals.transfers, inSigned = true)
						StatRow("Intérêts et revenus", inTotals.income, inSigned = true)
						if (inTotals.expenses != 0L) StatRow("Frais et retraits", inTotals.expenses, inSigned = true)
					}
				}
			}
			Spacer(Modifier.height(32.dp))
			SectionHeader(inTitle = "Relevés")
			Spacer(Modifier.height(12.dp))
			BorderedCard {
				if (inExtras.statements.isEmpty())
				{
					Text("Aucun relevé.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
				}
				inExtras.statements.forEach { inItem ->
					Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
						Column(Modifier.weight(1f)) {
							Text(inItem.statement.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)), style = MaterialTheme.typography.bodyLarge)
							if (inItem.gapCents != 0L)
							{
								Row {
									Text("Écart ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
									AmountText(inCents = inItem.gapCents, inStyle = MaterialTheme.typography.bodyMedium, inColor = MaterialTheme.colorScheme.onSurfaceVariant, inShowPlusSign = true)
								}
							}
						}
						AmountText(inCents = inItem.statement.balanceCents, inStyle = MaterialTheme.typography.bodyLarge)
					}
				}
			}
		}
		Column(Modifier.weight(1.2f)) {
			ChartSection("Évolution du solde", inState.series, inState.series.lastOrNull()?.month)
			Spacer(Modifier.height(32.dp))
			GlobalOperations(inState)
		}
	}
}

/*
	Compte Bourse : vue globale. À gauche, valeur, plus-values et positions
	avec leur répartition ; à droite, courbe de la valeur et opérations par mois.
	inState  : état global
	inExtras : portefeuille
*/
@Composable
private fun BrokerageView(
	inState  : AccountDetailState,
	inExtras : AccountExtras
)
{
	val vPortfolio = inExtras.portfolio ?: return								// portefeuille
	Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
		Column(Modifier.weight(1f)) {
			AmountText(inCents = vPortfolio.valuation.valueCents, inStyle = MaterialTheme.typography.displaySmall)
			Text("Valeur du compte, espèces comprises", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Spacer(Modifier.height(16.dp))
			BorderedCard {
				Column(Modifier.padding(vertical = 8.dp)) {
					StatRow("Espèces", vPortfolio.valuation.cashCents)
					StatRow("Plus-value latente", vPortfolio.valuation.unrealizedCents, inSigned = true)
					StatRow("Plus-values réalisées", vPortfolio.realizedCents, inSigned = true)
				}
			}
			Spacer(Modifier.height(32.dp))
			Positions(vPortfolio)
		}
		Column(Modifier.weight(1.2f)) {
			ChartSection("Évolution de la valeur", inExtras.valueSeries, inExtras.valueSeries.lastOrNull()?.month)
			Spacer(Modifier.height(32.dp))
			GlobalOperations(inState)
		}
	}
}

/*
	Positions d'un compte Bourse : barre de répartition des titres, puis une
	ligne par titre (pastille, nom, part, valeur), comme sur le téléphone.
	inPortfolio : portefeuille valorisé
*/
@Composable
private fun Positions(inPortfolio: PortfolioView)
{
	val vLines = allocation(inPortfolio.valuation.positions)					// répartition des titres
	SectionHeader(inTitle = "Positions")
	Spacer(Modifier.height(12.dp))
	if (vLines.size > 1)
	{
		ProportionBar(
			inSegments    = vLines.withIndex().groupBy { it.index.coerceAtMost(kAllocationSlots - 1) }.toSortedMap()
				.map { (inRank, inGroup) -> BarSegment(inGroup.sumOf { it.value.position.valueCents }, allocationColor(inRank)) },
			inDescription = "Répartition des titres"
		)
		Spacer(Modifier.height(16.dp))
	}
	BorderedCard {
		if (vLines.isEmpty())
		{
			Text("Aucune position.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
		}
		vLines.forEachIndexed { inIndex, inLine ->
			if (inIndex > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
			Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
				if (vLines.size > 1) { SeriesDot(allocationColor(inIndex)); Spacer(Modifier.width(14.dp)) }
				Text(inPortfolio.names[inLine.position.assetId] ?: "Actif supprimé", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
				Text("${inLine.percent} %", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				Spacer(Modifier.width(20.dp))
				AmountText(inCents = inLine.position.valueCents, inStyle = MaterialTheme.typography.titleMedium)
			}
		}
	}
}

/*
	Opérations d'une vue globale : « À venir », puis par mois.
	inState : état global
*/
@Composable
private fun GlobalOperations(inState: AccountDetailState)
{
	SectionHeader(inTitle = "Opérations")
	if (inState.upcoming.isEmpty() && inState.months.isEmpty())
	{
		Text("Aucune opération.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
	}
	if (inState.upcoming.isNotEmpty()) OperationsGroup("À venir", inState.upcoming, inWithDate = true)
	inState.months.forEach { inGroup -> OperationsGroup(monthTitle(inGroup.month), inGroup.items, inWithDate = true) }
}

/*
	Section de courbe : titre et courbe, mois mis en avant ; message si la
	série est trop courte.
	inTitle     : titre
	inSeries    : valeurs de fin de mois
	inHighlight : mois mis en avant
*/
@Composable
private fun ChartSection(
	inTitle     : String,
	inSeries    : List<SeriesPoint>,
	inHighlight : YearMonth?
)
{
	SectionHeader(inTitle = inTitle)
	Spacer(Modifier.height(12.dp))
	if (inSeries.size > 1 && inHighlight != null)
	{
		BalanceChart(inSeries, inHighlight)
	}
	else
	{
		Text("La courbe apparaîtra après un premier mois d’historique.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

/*
	Groupe d'opérations sous un titre (jour, mois, « À venir »).
	inTitle    : titre du groupe
	inItems    : opérations
	inWithDate : affiche la date de chaque opération
*/
@Composable
private fun OperationsGroup(
	inTitle    : String,
	inItems    : List<OperationItem>,
	inWithDate : Boolean
)
{
	Text(inTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
	BorderedCard {
		inItems.forEachIndexed { inIndex, inItem ->
			if (inIndex > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
			Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
					Icon(CategoryIcons.of(inItem.icon), contentDescription = null, modifier = Modifier.size(18.dp))
				}
				Spacer(Modifier.width(14.dp))
				Column(Modifier.weight(1f)) {
					Text(inItem.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
					Text(
						text     = if (inWithDate) "${inItem.transaction.date.format(kShortDay)} · ${inItem.subtitle}" else inItem.subtitle,
						style    = MaterialTheme.typography.bodyMedium,
						color    = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
				Spacer(Modifier.width(16.dp))
				AmountText(inCents = inItem.amount, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = true)
			}
		}
	}
}

/*
	Ligne de statistique : libellé et montant.
	inLabel  : libellé
	inCents  : montant
	inSigned : affiche le signe « + »
	inMuted  : ligne atténuée (ouverture)
	inStrong : ligne mise en avant (clôture)
*/
@Composable
private fun StatRow(
	inLabel  : String,
	inCents  : Long,
	inSigned : Boolean = false,
	inMuted  : Boolean = false,
	inStrong : Boolean = false
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	val vStyle  = if (inStrong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge	// style de la ligne
	Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
		Text(inLabel, style = vStyle, color = if (inMuted) vColors.onSurfaceVariant else vColors.onSurface, modifier = Modifier.weight(1f))
		AmountText(
			inCents        = inCents,
			inStyle        = vStyle,
			inColor        = when { inStrong && inCents < 0 -> vColors.error; inMuted -> vColors.onSurfaceVariant; else -> Color.Unspecified },
			inShowPlusSign = inSigned
		)
	}
}
