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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.CategoryKind
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

private const val kUntracked = "__aucun__"											// choix « Non suivie »

/*
	Budget prévisionnel mensuel : lignes libres d'entrées, de dépenses et
	d'investissements (chacune peut suivre une catégorie ou un compte pour la
	comparer au réel), chiffres clés, et diagramme de cashflow du prévu ou du
	réel d'un mois. Le budget est enregistré sur le PC à chaque modification.
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
	var vMonth   by remember { mutableStateOf(YearMonth.from(inSpace.today)) }		// mois comparé
	var vActual  by remember { mutableStateOf(false) }									// schéma du réel
	val vView    = remember(vBudget, inSpace.data, vMonth) { compareBudget(vBudget, inSpace.data, vMonth) }	// budget comparé
	val vDiagram = remember(vView, vActual) { cashFlow(vView, vActual) }				// diagramme
	val vFirst   = inSpace.data.transactions.minOfOrNull { YearMonth.from(it.date) } ?: vMonth	// premier mois avec des données
	val vUpdate  = { inLines: List<BudgetLine> -> inController.setBudget(Budget(inLines)) }	// enregistre les lignes
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		Column {
			Text("Budget prévisionnel", style = MaterialTheme.typography.headlineMedium)
			Text("Un mois type : vos entrées, vos dépenses par type et vos investissements. Une ligne peut suivre une catégorie (ou un compte, pour un investissement) : son réel du mois s’affiche à côté. Enregistré sur ce PC.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Figures(vView)
		Tile(
			inTitle  = "Cashflow ${if (vActual) "réel de ${monthTitle(vMonth).lowercase()}" else "prévu"}",
			inAction = {
				FilterChip(selected = !vActual, onClick = { vActual = false }, label = { Text("Prévu") }, modifier = Modifier.padding(end = 8.dp))
				FilterChip(selected = vActual, onClick = { vActual = true }, label = { Text("Réel du mois") }, modifier = Modifier.padding(end = 16.dp))
				Box(Modifier.width(300.dp)) { MonthSwitcher(vMonth, vFirst.coerceAtMost(vMonth), YearMonth.from(inSpace.today).plusMonths(1), { vMonth = it }) }
			}
		) {
			if (vDiagram.total <= 0L)
			{
				Hint(if (vActual) "Aucun réel ce mois-ci sur les lignes suivies." else "Ajoutez des entrées et des dépenses ci-dessous pour voir le schéma.")
			}
			else
			{
				SankeyDiagram(vDiagram, "Budget")
				if (vDiagram.deficit) Hint("Les sorties dépassent les entrées : le déficit apparaît comme une entrée, à gauche.")
				if (vActual) Hint("Réel des lignes suivies seulement ; les lignes non suivies n’y figurent pas.")
			}
		}
		BudgetSection.entries.forEach { inSection -> SectionTile(inSection, vView, inSpace, vBudget, vUpdate) }
	}
}

/*
	Chiffres clés : prévu de chaque rubrique et reste, avec le réel du mois.
	inView : budget comparé
*/
@Composable
private fun Figures(inView: BudgetView)
{
	val vPlan   = { inSection: BudgetSection -> inView.budget.total(inSection) }		// prévu d'une rubrique
	val vActual = { inSection: BudgetSection -> inView.actualTotal(inSection) }			// réel d'une rubrique
	TileRow(
		*BudgetSection.entries.map { inSection ->
			Cell(1f) { inMod ->
				Tile(inModifier = inMod) {
					TileFigure(if (inSection == BudgetSection.INVESTMENT) "Investissements prévus" else "${inSection.label} prévues") { AmountText(inCents = vPlan(inSection), inStyle = MaterialTheme.typography.headlineSmall) }
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text("Réel ${monthTitle(inView.month).lowercase()} : ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
						AmountText(inCents = vActual(inSection), inStyle = MaterialTheme.typography.bodyMedium)
					}
				}
			}
		}.toTypedArray(),
		Cell(1f) { inMod ->
			Tile(inModifier = inMod) {
				TileFigure("Reste prévu") { AmountText(inCents = inView.budget.remaining, inStyle = MaterialTheme.typography.headlineSmall, inShowPlusSign = true, inTrend = true) }
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text("Réel : ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					AmountText(inCents = inView.actualRemaining, inStyle = MaterialTheme.typography.bodyMedium, inShowPlusSign = true)
				}
			}
		},
		inMinWidth = 1000.dp
	)
}

/*
	Tuile d'une rubrique : ses lignes (nom, thème des sorties, suivi, montant
	prévu, réel, écart) et l'ajout d'une ligne.
	inSection : rubrique
	inView    : budget comparé
	inSpace   : données (catégories, comptes)
	inBudget  : budget
	inUpdate  : enregistre les lignes
*/
@Composable
private fun SectionTile(
	inSection : BudgetSection,
	inView    : BudgetView,
	inSpace   : WorkspaceState,
	inBudget  : Budget,
	inUpdate  : (List<BudgetLine>) -> Unit
)
{
	val vLines = inView.section(inSection)												// lignes de la rubrique
	Tile(
		inTitle  = inSection.label,
		inAction = {
			Text("Total ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			AmountText(inCents = inBudget.total(inSection), inStyle = MaterialTheme.typography.titleMedium)
		}
	) {
		if (vLines.isEmpty())
		{
			Hint(
				when (inSection)
				{
					BudgetSection.INCOME     -> "Salaire, primes, loyers perçus…"
					BudgetSection.EXPENSE    -> "Logement, alimentation, transport, loisirs…"
					BudgetSection.INVESTMENT -> "Versements vers un livret, un PEA, une assurance vie…"
				}
			)
		}
		else
		{
			Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
				val vHeads = listOfNotNull("Nom" to 2f, ("Thème" to 1.4f).takeIf { inSection != BudgetSection.INCOME }, "Suivi" to 1.6f, "Prévu" to 1f, "Réel du mois" to 1f, "Écart" to 1f)	// en-têtes (les entrées n'ont pas de thème)
			vHeads.forEach { (inHead, inWeight) ->
					Text(inHead, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(inWeight).padding(end = 12.dp))
				}
				Spacer(Modifier.width(40.dp))
			}
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
		}
		vLines.forEach { inLineView ->
			LineRow(inLineView, inSpace) { inNew ->
				inUpdate(inBudget.lines.mapNotNull { if (it.id == inLineView.line.id) inNew else it })
			}
		}
		Spacer(Modifier.padding(top = 6.dp))
		OutlinedButton(onClick = { inUpdate(inBudget.lines + BudgetLine(UUID.randomUUID().toString(), inSection, "", 0)) }) { Text("Ajouter une ligne") }
	}
}

/*
	Ligne de budget modifiable ; une sortie (dépense, investissement) porte
	un thème qui la regroupe avec d'autres dans le schéma.
	inView     : ligne et son réel
	inSpace    : données (catégories, comptes)
	inOnChange : nouvelle ligne ; null pour la retirer
*/
@Composable
private fun LineRow(
	inView     : BudgetLineView,
	inSpace    : WorkspaceState,
	inOnChange : (BudgetLine?) -> Unit
)
{
	val vLine   = inView.line																// ligne
	var vAmount by remember(vLine.id) { mutableStateOf(if (vLine.amount == 0L) "" else editableEuros(vLine.amount)) }	// montant tapé
	val vTracks = when (vLine.section)
	{
		BudgetSection.INCOME     -> inSpace.data.categories.filter { it.kind == CategoryKind.INCOME && !it.archived }.sortedBy { it.sortOrder }.map { it.id to it.name }
		BudgetSection.EXPENSE    -> inSpace.data.categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }.sortedBy { it.sortOrder }.map { it.id to it.name }
		BudgetSection.INVESTMENT -> inSpace.data.accounts.filter { it.type != AccountType.CHECKING && !it.archived }.map { it.id to it.name }
	}																						// suivis possibles
	val vTracked = vLine.categoryId ?: vLine.accountId										// suivi actuel
	Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
		CellField(vLine.name, { inOnChange(vLine.copy(name = it)) }, Modifier.weight(2f).padding(end = 12.dp), inPlaceholder = "Nom")
		if (vLine.section != BudgetSection.INCOME)
		{
			CellField(vLine.group, { inOnChange(vLine.copy(group = it)) }, Modifier.weight(1.4f).padding(end = 12.dp), inPlaceholder = if (vLine.section == BudgetSection.INVESTMENT) "Investissements" else "Thème (facultatif)")
		}
		MenuCell(
			inText     = vTracks.firstOrNull { it.first == vTracked }?.second ?: "Non suivie",
			inOptions  = listOf(kUntracked to "Non suivie") + vTracks,
			inOnSelect = { inId ->
				val vId = inId.takeIf { it != kUntracked }											// suivi choisi
				inOnChange(if (vLine.section == BudgetSection.INVESTMENT) vLine.copy(accountId = vId, categoryId = null) else vLine.copy(categoryId = vId, accountId = null))
			},
			inModifier = Modifier.weight(1.6f).padding(end = 12.dp),
			inMuted    = vTracked == null
		)
		CellField(
			inValue    = vAmount,
			inOnChange = { inText -> vAmount = inText; (if (inText.isBlank()) 0L else parseEuros(inText)?.takeIf { it >= 0 })?.let { inOnChange(vLine.copy(amount = it)) } },
			inModifier = Modifier.weight(1f).padding(end = 12.dp),
			inPlaceholder = "0,00",
			inEnd      = true,
			inError    = vAmount.isNotBlank() && parseEuros(vAmount)?.let { it >= 0 } != true
		)
		Box(Modifier.weight(1f).padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
			inView.actual?.let { AmountText(inCents = it, inStyle = MaterialTheme.typography.bodyLarge) } ?: Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Box(Modifier.weight(1f).padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
			inView.gap?.let { AmountText(inCents = it, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = true) } ?: Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Box(Modifier.width(40.dp)) {
			IconButton(onClick = { inOnChange(null) }) { Icon(LineIcons.Close, contentDescription = "Retirer la ligne", modifier = Modifier.size(16.dp)) }
		}
	}
}
