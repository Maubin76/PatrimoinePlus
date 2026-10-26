package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.CellField
import fr.maubin.patrimoineplus.desktop.ui.components.Cell
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.desktop.ui.components.MenuCell
import fr.maubin.patrimoineplus.desktop.ui.components.SankeyDiagram
import fr.maubin.patrimoineplus.desktop.ui.components.TileRow
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetLine
import fr.maubin.patrimoineplus.ui.budget.BudgetLineView
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.BudgetView
import fr.maubin.patrimoineplus.ui.budget.cashFlow
import fr.maubin.patrimoineplus.ui.budget.compareBudget
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.MonthSwitcher
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.components.TileFigure
import fr.maubin.patrimoineplus.ui.components.monthTitle
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import java.time.YearMonth
import java.util.UUID
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.budget.ThemeView
import fr.maubin.patrimoineplus.ui.components.flowAccent
import fr.maubin.patrimoineplus.ui.components.FlowAccent
import fr.maubin.patrimoineplus.ui.components.isColorful
import androidx.compose.ui.graphics.Color
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.domain.format.formatFrenchDate
import java.time.LocalDate
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import fr.maubin.patrimoineplus.domain.format.kCurrencySeparator
import fr.maubin.patrimoineplus.ui.components.heroFilled
import fr.maubin.patrimoineplus.desktop.ui.tour.tourAnchor

private const val kNameWeight   = 2.4f											// largeur relative de la colonne du nom
private const val kAmountWeight = 1.2f											// largeur relative de la colonne du montant

/*
	Budget prévisionnel mensuel, page de planification : lignes libres
	d'entrées, de dépenses (rangées par thème) et d'investissements, chiffres
	clés et diagramme de cashflow ; le bandeau des objectifs fixe, pour le
	mois choisi, les objectifs du mois suivis dans la page Objectifs. Le
	budget est enregistré sur le PC à chaque modification.
	inSpace      : données
	inController : contrôleur
*/
@Composable
fun BudgetScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vBudget  by inController.budget.collectAsState()								// budget
	var vMonth   by remember { mutableStateOf(YearMonth.from(inSpace.today)) }		// mois dont on fixe les objectifs
	val vView    = remember(vBudget, inSpace.data) { compareBudget(vBudget, inSpace.data, YearMonth.from(inSpace.today)) }	// budget rangé par thème
	val vDiagram = remember(vView) { cashFlow(vView, false) }							// diagramme du budget
	val vUpdate  = { inLines: List<BudgetLine> -> inController.setBudget(Budget(inLines)) }	// enregistre les lignes
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		Column {
			Text("Budget prévisionnel", style = MaterialTheme.typography.displaySmall)
		}
		Box(Modifier.tourAnchor("budget.figures")) { Figures(vBudget) }
		Box(Modifier.tourAnchor("budget.goals")) { MonthGoalsBar(vMonth, { vMonth = it }, inSpace, inController, vBudget) }
		Tile(inTitle = "Cashflow", inModifier = Modifier.tourAnchor("budget.cashflow")) {
			if (vDiagram.total <= 0L)
			{
				Hint("Ajoutez des entrées et des dépenses ci-dessous pour voir le schéma.")
			}
			else
			{
				SankeyDiagram(vDiagram, "Budget")
				if (vDiagram.deficit) Hint("Les sorties dépassent les entrées : le déficit apparaît comme une entrée, à gauche.")
			}
		}
		Box(Modifier.tourAnchor("budget.lines")) {
			TileRow(
				Cell(1f) { inMod -> SectionTile(BudgetSection.INCOME, vView, vBudget, vUpdate, inMod) },
				Cell(1f) { inMod -> SectionTile(BudgetSection.INVESTMENT, vView, vBudget, vUpdate, inMod) },
				inMinWidth = 760.dp
			)																				// entrées et investissements côte à côte
		}
		SectionTile(BudgetSection.EXPENSE, vView, vBudget, vUpdate)
	}
}

/*
	Chiffres clés du budget : entrées, dépenses, investissements, reste.
	inBudget : budget
*/
@Composable
private fun Figures(inBudget: Budget)
{
	TileRow(
		*BudgetSection.entries.map { inSection ->
			Cell(1f) { inMod ->
				Tile(inModifier = inMod, inAccent = sectionAccent(inSection)) {
					TileFigure(inSection.label) { AmountText(inCents = inBudget.total(inSection), inStyle = MaterialTheme.typography.headlineSmall) }
				}
			}
		}.toTypedArray(),
		Cell(1f) { inMod ->
			Tile(inModifier = inMod, inFilled = heroFilled()) {
				TileFigure("Reste") { AmountText(inCents = inBudget.remaining, inStyle = MaterialTheme.typography.headlineSmall, inShowPlusSign = true, inTrend = true) }
			}
		},
		inMinWidth = 1000.dp
	)
}

/*
	Tuile d'une rubrique : ses lignes (nom et montant) ; « + » ajoute une
	ligne. Entrées et investissements : « + » sur la ligne du titre. Dépenses :
	les six thèmes toujours affichés, sur deux colonnes de trois, chacun avec
	son total et son « + », qui range la nouvelle ligne dans ce thème.
	inSection  : rubrique
	inView     : budget rangé par thème
	inBudget   : budget
	inUpdate   : enregistre les lignes
	inModifier : place de la tuile
*/
@Composable
private fun SectionTile(
	inSection  : BudgetSection,
	inView     : BudgetView,
	inBudget   : Budget,
	inUpdate   : (List<BudgetLine>) -> Unit,
	inModifier : Modifier = Modifier
)
{
	val vChange = { inLineView: BudgetLineView -> { inNew: BudgetLine? -> inUpdate(inBudget.lines.mapNotNull { if (it.id == inLineView.line.id) inNew else it }) } }	// modification d'une ligne
	val vAdd    = { inTheme: ExpenseTheme? -> inUpdate(inBudget.lines + BudgetLine(UUID.randomUUID().toString(), inSection, "", 0, theme = inTheme)) }	// ajout d'une ligne
	Tile(
		inModifier   = inModifier,
		inTitle      = inSection.label,
		inTitleStyle = MaterialTheme.typography.headlineSmall,							// titre de rubrique, bien visible
		inAccent     = sectionAccent(inSection),
		inAction     = {
			Text("Total ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			AmountText(inCents = inBudget.total(inSection), inStyle = MaterialTheme.typography.titleMedium)
			if (inSection != BudgetSection.EXPENSE) AddButton("Ajouter une ligne aux ${inSection.label.lowercase()}") { vAdd(null) }
		}
	) {
		if (inSection == BudgetSection.EXPENSE)
		{
			Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Top) {
				ExpenseTheme.entries.chunked(3).forEach { inColumn ->
					Column(Modifier.weight(1f)) {
						inColumn.forEach { inTheme ->
							val vTheme = inView.themes.firstOrNull { it.theme == inTheme }		// lignes du thème
							ThemeRow(inTheme, vTheme?.planned ?: 0L) { vAdd(inTheme) }
							vTheme?.lines.orEmpty().forEach { inLineView -> LineRow(inLineView, vChange(inLineView)) }
						}
					}
				}
			}
		}
		else
		{
			val vLines = inView.section(inSection)											// lignes de la rubrique
			if (vLines.isEmpty()) Hint(if (inSection == BudgetSection.INCOME) "Salaire, primes, loyers perçus…" else "Versements vers un livret, un PEA, une assurance vie…")
			vLines.forEach { inLineView -> LineRow(inLineView, vChange(inLineView)) }
		}
	}
}

/*
	Bouton « + » d'ajout d'une ligne, aligné sur la colonne des « × ».
	inDescription : action lue par les lecteurs d'écran
	inOnClick     : ajout
*/
@Composable
private fun AddButton(
	inDescription : String,
	inOnClick     : () -> Unit
)
{
	Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
		IconButton(onClick = inOnClick, modifier = Modifier.semantics { contentDescription = inDescription }) {
			Icon(LineIcons.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
		}
	}
}

/*
	Ligne d'un thème de dépenses : nom du thème, total de ses lignes et « + »
	qui ajoute une ligne dans ce thème.
	inTheme   : thème
	inPlanned : total des lignes du thème
	inOnAdd   : ajout d'une ligne au thème
*/
@Composable
private fun ThemeRow(
	inTheme   : ExpenseTheme,
	inPlanned : Long,
	inOnAdd   : () -> Unit
)
{
	Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
		val vStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp)	// sous-titre : entre le texte des lignes et le titre de rubrique
		Text(inTheme.label, style = vStyle, modifier = Modifier.weight(kNameWeight).padding(end = 12.dp))
		Box(Modifier.weight(kAmountWeight).padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) { AmountText(inCents = inPlanned, inStyle = vStyle) }
		AddButton("Ajouter une ligne à ${inTheme.label}", inOnAdd)
	}
	HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))	// trait sous le thème, plus marqué qu'un filet
}

/*
	Ligne de budget modifiable : nom et montant, sans filets ; « × » la
	retire. Une dépense reste dans le thème où elle a été ajoutée.
	inView     : ligne
	inOnChange : nouvelle ligne ; null pour la retirer
*/
@Composable
private fun LineRow(
	inView     : BudgetLineView,
	inOnChange : (BudgetLine?) -> Unit
)
{
	val vLine   = inView.line																// ligne
	var vAmount by remember(vLine.id) { mutableStateOf(if (vLine.amount == 0L) "" else editableEuros(vLine.amount)) }	// montant tapé
	Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
		CellField(vLine.name, { inOnChange(vLine.copy(name = it)) }, Modifier.weight(kNameWeight).padding(end = 12.dp), inPlaceholder = "Nom", inUnderline = false)
		Row(Modifier.weight(kAmountWeight).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
			CellField(
				inValue     = vAmount,
				inOnChange  = { inText -> vAmount = inText; (if (inText.isBlank()) 0L else parseEuros(inText)?.takeIf { it >= 0 })?.let { inOnChange(vLine.copy(amount = it)) } },
				inModifier  = Modifier.weight(1f),
				inPlaceholder = "0,00",
				inEnd       = true,
				inError     = vAmount.isNotBlank() && parseEuros(vAmount)?.let { it >= 0 } != true,
				inUnderline = false
			)
			Text("$kCurrencySeparator€", style = MaterialTheme.typography.bodyLarge)						// même fin que les totaux : chiffres alignés
		}
		Box(Modifier.width(40.dp)) {
			IconButton(onClick = { inOnChange(null) }) { Icon(LineIcons.Close, contentDescription = "Retirer la ligne", modifier = Modifier.size(16.dp)) }
		}
	}
}

/*
	Couleur d'une rubrique du budget dans le thème Coloré : entrées
	bleu-vert, dépenses orange, investissements ambre.
	inSection : rubrique
*/
@Composable
private fun sectionAccent(inSection: BudgetSection): Color =
	flowAccent(
		when (inSection)
		{
			BudgetSection.INCOME     -> FlowAccent.INFLOW
			BudgetSection.EXPENSE    -> FlowAccent.OUTFLOW
			BudgetSection.INVESTMENT -> FlowAccent.INVEST
		}
	)

/*
	Bandeau des objectifs du mois choisi : « Fixer les objectifs » transforme
	le cashflow prévu en objectifs du mois (dépenses par thème, épargne des
	investissements prévus), suivis dans la page Objectifs ; un mois déjà fixé
	peut être mis à jour ; un mois écoulé reste figé (historique).
	inMonth      : mois choisi
	inOnMonth    : change de mois (mois en cours ou suivant)
	inSpace      : données
	inController : contrôleur
	inBudget     : budget
*/
@Composable
private fun MonthGoalsBar(
	inMonth      : YearMonth,
	inOnMonth    : (YearMonth) -> Unit,
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inBudget     : Budget
)
{
	val vGoals  by inController.goals.collectAsState()									// objectifs
	val vPlan   = vGoals.plan(inMonth)													// objectifs déjà fixés
	val vPast   = inMonth.isBefore(YearMonth.from(inSpace.today))						// mois écoulé
	val vEmpty  = inBudget.total(BudgetSection.EXPENSE) <= 0L && inBudget.total(BudgetSection.INVESTMENT) <= 0L	// rien à transformer en objectifs
	Tile(inAccent = flowAccent(FlowAccent.MAIN)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				val vName = monthTitle(inMonth).lowercase()										// mois, en minuscules
				Text("Objectifs ${if (vName.first() in "aeiouyàâéèêîôû") "d’" else "de "}$vName", style = MaterialTheme.typography.titleMedium)
				val vState = when
				{
					vPlan != null -> "Fixés le ${formatFrenchDate(LocalDate.parse(vPlan.confirmed))} : ${vPlan.goals.size} objectif(s), suivis dans la page Objectifs." + if (vPast) "" else " Mettez-les à jour si le budget a changé."
					vPast         -> "Mois écoulé : aucun objectif n’avait été fixé."
					vEmpty        -> "Ajoutez des dépenses ou des investissements au budget pour en faire les objectifs du mois."
					else          -> null
				}																		// état du mois ; rien à dire avant de fixer les objectifs
				vState?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
			}
			Spacer(Modifier.width(16.dp))
			Box(Modifier.width(260.dp)) { MonthSwitcher(inMonth, YearMonth.from(inSpace.today), YearMonth.from(inSpace.today).plusMonths(1), inOnMonth) }
			Spacer(Modifier.width(8.dp))
			if (vPlan != null) TextButton(onClick = { inController.show(Screen.GOALS) }) { Text("Voir les objectifs") }
			if (!vPast) Button(onClick = { inController.confirmMonthGoals(inMonth) }, enabled = !vEmpty) { Text(if (vPlan == null) "Fixer les objectifs du mois" else "Mettre à jour") }
		}
	}
}
