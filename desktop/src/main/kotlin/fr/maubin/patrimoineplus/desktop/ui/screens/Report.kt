package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.LocalSavePicker
import fr.maubin.patrimoineplus.desktop.ui.components.Cell
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.desktop.ui.components.TileRow
import fr.maubin.patrimoineplus.domain.format.formatPermille
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.components.TileFigure
import fr.maubin.patrimoineplus.ui.report.CategoryYear
import fr.maubin.patrimoineplus.ui.report.buildAnnualReport
import fr.maubin.patrimoineplus.ui.report.reportYears
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import fr.maubin.patrimoineplus.desktop.ReportTab
import fr.maubin.patrimoineplus.desktop.ui.panels.Choice
import fr.maubin.patrimoineplus.ui.components.flowAccent
import fr.maubin.patrimoineplus.ui.components.FlowAccent
import fr.maubin.patrimoineplus.ui.components.accountAccent
import fr.maubin.patrimoineplus.ui.components.isColorful
import fr.maubin.patrimoineplus.domain.model.AccountType
import androidx.compose.ui.graphics.Color

private val kLongDay = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)	// date en toutes lettres

/*
	Écran Bilan : onglets « Mois par mois » (dépenses et revenus des douze
	derniers mois) et « Bilan annuel » (chiffres de l'année en tuiles :
	patrimoine, revenus et dépenses, placements, comptes, catégories ; export
	PDF imprimable).
	inSpace      : données
	inController : contrôleur
*/
@Composable
fun ReportScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vTab by inController.reportTab.collectAsState()								// onglet affiché
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Bilan", style = MaterialTheme.typography.headlineMedium)
				Text("Le passé en chiffres : dépenses et revenus mois par mois, ou l’année entière avec son export PDF.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			Spacer(Modifier.width(24.dp))
			Box(Modifier.width(360.dp)) { Choice(ReportTab.entries.map { it to it.label }, vTab, inController::showReportTab) }
		}
		when (vTab)
		{
			ReportTab.MONTHLY -> AnalysisSection(inSpace, inController)
			ReportTab.ANNUAL  -> AnnualReportSection(inSpace, inController)
		}
	}
}

/*
	Onglet « Bilan annuel » : choix de l'année, chiffres de l'année en
	tuiles et export PDF.
	inSpace      : données
	inController : contrôleur
*/
@Composable
private fun AnnualReportSection(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vYears  = remember(inSpace.data, inSpace.today) { reportYears(inSpace.data, inSpace.today) }	// années possibles
	var vYear   by remember { mutableStateOf(vYears.first()) }							// année affichée
	val vReport = remember(inSpace.data, inSpace.today, vYear) { buildAnnualReport(inSpace.data, vYear, inSpace.today) }	// bilan
	val vPick   = LocalSavePicker.current													// choix du fichier PDF
	var vError  by remember { mutableStateOf<String?>(null) }							// échec de l'export
	Column(
		modifier            = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Spacer(Modifier.weight(1f))														// années et export à droite
			vYears.forEach { inYear ->
				FilterChip(selected = inYear == vYear, onClick = { vYear = inYear }, label = { Text("$inYear") }, modifier = Modifier.padding(end = 8.dp))
			}
			Spacer(Modifier.width(8.dp))
			Button(onClick = { vPick("bilan-patrimoineplus-$vYear.pdf")?.let { vError = inController.exportReport(vYear, it) } }) { Text("Exporter en PDF…") }
		}
		vError?.let { Text("Export impossible : $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		TileRow(
			Cell(1f) { inMod -> Tile(inModifier = inMod) { TileFigure("Patrimoine au 1er janvier") { AmountText(inCents = vReport.wealthStart, inStyle = MaterialTheme.typography.headlineSmall) } } },
			Cell(1f) { inMod ->
				Tile(inModifier = inMod) {
					TileFigure("Patrimoine au ${vReport.to.format(kLongDay)}") { AmountText(inCents = vReport.wealthEnd, inStyle = MaterialTheme.typography.headlineSmall) }
					if (vReport.debtEnd > 0)
					{
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text("Avoirs ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
							AmountText(inCents = vReport.grossEnd, inStyle = MaterialTheme.typography.bodyMedium, inColor = MaterialTheme.colorScheme.onSurfaceVariant)
						}
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text("Crédits ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
							AmountText(inCents = -vReport.debtEnd, inStyle = MaterialTheme.typography.bodyMedium, inColor = MaterialTheme.colorScheme.onSurfaceVariant)
						}
					}
				}
			},
			Cell(1f) { inMod -> Tile(inModifier = inMod, inFilled = isColorful()) { TileFigure("Variation") { AmountText(inCents = vReport.wealthChange, inStyle = MaterialTheme.typography.headlineSmall, inShowPlusSign = true, inTrend = true) } } },
			Cell(1f) { inMod ->
				Tile(inModifier = inMod) {
					TileFigure("Taux d’épargne") { Text(vReport.savingsRate?.takeIf { !LocalAmountsHidden.current }?.let { formatPermille(it).removePrefix("+") } ?: "—", style = MaterialTheme.typography.headlineSmall) }
				}
			},
			inMinWidth = 1000.dp
		)
		TileRow(
			Cell(1f) { inMod ->
				Tile(inModifier = inMod, inTitle = "Revenus et dépenses", inAccent = flowAccent(FlowAccent.INFLOW)) {
					Line("Revenus", vReport.totalIncomes)
					Line("Dépenses", vReport.totalExpenses)
					HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
					Line("Épargne", vReport.totalIncomes - vReport.totalExpenses, inSigned = true)
				}
			},
			Cell(1f) { inMod ->
				Tile(inModifier = inMod, inTitle = "Placements", inAccent = flowAccent(FlowAccent.INVEST)) {
					Line("Intérêts (comptes d’épargne)", vReport.interest)
					Line("Dividendes (comptes Bourse)", vReport.dividends)
					Line("Plus-values réalisées", vReport.totalRealized, inSigned = true)
					vReport.realized.forEach { Line("   ${it.assetName}", it.cents, inSigned = true, inMuted = true) }
				}
			}
		)
		Tile(inTitle = "Comptes") {
			Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
				Text("Compte", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2f))
				listOf("1er janvier", "Fin", "Variation").forEach { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) }
			}
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			vReport.accounts.forEach { inAcc ->
				Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
					Text("${inAcc.account.name} · ${inAcc.account.type.label}${if (inAcc.share != 100) " · ${inAcc.share} %" else ""}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f))
					AmountText(inCents = inAcc.start, inStyle = MaterialTheme.typography.bodyMedium, inModifier = Modifier.weight(1f))
					AmountText(inCents = inAcc.end, inStyle = MaterialTheme.typography.bodyMedium, inModifier = Modifier.weight(1f))
					AmountText(inCents = inAcc.end - inAcc.start, inStyle = MaterialTheme.typography.bodyMedium, inShowPlusSign = true, inModifier = Modifier.weight(1f))
				}
			}
		}
		if (vReport.bought.isNotEmpty() || vReport.sold.isNotEmpty() || vReport.loans.isNotEmpty())
		{
			TileRow(
				Cell(1f) { inMod ->
					Tile(inModifier = inMod, inTitle = "Biens", inAccent = accountAccent(AccountType.REAL_ESTATE)) {
						if (vReport.bought.isEmpty() && vReport.sold.isEmpty()) Hint("Aucun achat ni vente de bien sur la période.")
						vReport.bought.forEach { Line(if (it.paid) "Achat · ${it.name}" else "Ajouté (déjà possédé) · ${it.name}", it.cost) }
						vReport.sold.forEach { Line("Vente · ${it.name} (achat ${if (LocalAmountsHidden.current) kMaskedAmount else formatEuros(it.cost)})", it.salePrice ?: 0L) }
						if (vReport.sold.isNotEmpty())
						{
							HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
							Line("Plus-values des ventes", vReport.propertyGains, inSigned = true)
						}
					}
				},
				Cell(1f) { inMod ->
					Tile(inModifier = inMod, inTitle = "Crédits", inAccent = accountAccent(AccountType.LOAN)) {
						if (vReport.loans.isEmpty()) Hint("Aucun crédit sur la période.")
						vReport.loans.forEach { inLoan ->
							Text(inLoan.account.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
							if (inLoan.start > 0) Line("Capital restant dû au 1er janvier", inLoan.start, inMuted = true)
							Line("Capital restant dû à la fin", inLoan.end, inMuted = true)
							Line("Capital remboursé", inLoan.repaid)
							inLoan.interest?.let { Line("Intérêts et assurance", it) }
						}
					}
				}
			)
		}
		TileRow(
			Cell(1f) { inMod -> CategoriesTile("Dépenses par catégorie", vReport.expenses, vReport.totalExpenses, inMod, flowAccent(FlowAccent.OUTFLOW)) },
			Cell(1f) { inMod -> CategoriesTile("Revenus par catégorie", vReport.incomes, vReport.totalIncomes, inMod, flowAccent(FlowAccent.INFLOW)) }
		)
		Hint("Le PDF reprend ces chiffres (biens et crédits compris) et le patrimoine de fin de chaque mois, sur des pages A4 prêtes à imprimer.")
	}
}

/*
	Ligne libellé / montant d'une tuile du bilan.
	inLabel  : libellé
	inCents  : montant
	inSigned : signe « + » devant un montant positif
	inMuted  : ligne de détail, en gris
*/
@Composable
private fun Line(
	inLabel  : String,
	inCents  : Long,
	inSigned : Boolean = false,
	inMuted  : Boolean = false
)
{
	Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
		Text(inLabel, style = MaterialTheme.typography.bodyLarge, color = if (inMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
		AmountText(inCents = inCents, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = inSigned)
	}
}

/*
	Tuile des montants par catégorie, avec leur part du total.
	inTitle    : titre
	inLines    : montants par catégorie
	inTotal    : total
	inModifier : place
	inAccent   : couleur de la tuile (thème Coloré)
*/
@Composable
private fun CategoriesTile(
	inTitle    : String,
	inLines    : List<CategoryYear>,
	inTotal    : Long,
	inModifier : Modifier,
	inAccent   : Color = Color.Unspecified
)
{
	Tile(inModifier = inModifier, inTitle = inTitle, inAccent = inAccent) {
		if (inLines.isEmpty()) Hint("Rien sur la période.")
		inLines.forEach { inLine ->
			Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
				Text(inLine.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
				if (inTotal > 0) Text("${Math.round(inLine.cents * 100.0 / inTotal)} %", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
				AmountText(inCents = inLine.cents, inStyle = MaterialTheme.typography.bodyMedium)
			}
		}
	}
}
