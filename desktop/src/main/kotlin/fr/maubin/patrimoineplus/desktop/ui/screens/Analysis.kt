package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.Cell
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.desktop.ui.components.TileRow
import fr.maubin.patrimoineplus.domain.format.formatPermille
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.ui.analysis.AnalysisRow
import fr.maubin.patrimoineplus.ui.analysis.ExpenseAnalysis
import fr.maubin.patrimoineplus.ui.analysis.buildExpenseAnalysis
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.SeriesDot
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.components.TileFigure
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.ui.search.SearchQuery
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val kMonthShort  = DateTimeFormatter.ofPattern("MMM", Locale.FRANCE)		// mois en tête de colonne
/*
	Largeurs des colonnes du tableau, calculées pour remplir la tuile (avec un
	minimum lisible ; en dessous, le tableau défile horizontalement).
	name    : colonne des catégories
	month   : colonne d'un mois
	summary : colonnes Moyenne et Écart
*/
private data class Columns(
	val name    : Dp = 200.dp,
	val month   : Dp = 64.dp,
	val summary : Dp = 84.dp
)

private val LocalColumns = staticCompositionLocalOf { Columns() }					// largeurs du tableau affiché

/*
	Analyse des dépenses et des revenus des douze derniers mois, tous comptes :
	indicateurs moyens, barres mois par mois, puis le tableau catégories ×
	mois ; un clic sur une case montre ses opérations (écran Recherche
	filtré sur la catégorie et le mois). Onglet « Mois par mois » du Bilan.
	inSpace      : données
	inController : contrôleur
*/
@Composable
fun AnalysisSection(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vTable = remember(inSpace.data, inSpace.today) { buildExpenseAnalysis(inSpace.data, inSpace.today) }	// tableau d'analyse
	Column(
		modifier            = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		if (vTable.expenses.isEmpty() && vTable.incomes.isEmpty())
		{
			Tile { Hint("Aucune dépense ni revenu sur les douze derniers mois.") }
			return@Column
		}
		Figures(vTable)
		Tile(inTitle = "Mois par mois") {
			MonthBars(vTable)
		}
		Tile(inTitle = "Par catégorie") {
			Hint("Un clic sur un montant montre ses opérations. Écart : dernier mois complet comparé à la moyenne des mois précédents.")
			Spacer(Modifier.height(8.dp))
			Table(vTable) { inCategory, inMonth ->
				inController.openSearch(SearchQuery(categoryId = inCategory.id, from = inMonth.atDay(1), to = inMonth.atEndOfMonth()))
			}
		}
	}
}

/*
	Indicateurs moyens : dépenses, revenus, taux d'épargne, plus fortes
	hausses du dernier mois complet.
	inTable : tableau d'analyse
*/
@Composable
private fun Figures(inTable: ExpenseAnalysis)
{
	TileRow(
		Cell(1f) { inMod -> Tile(inModifier = inMod) { TileFigure("Dépenses moyennes par mois") { AmountText(inCents = inTable.averageExpense, inStyle = MaterialTheme.typography.headlineSmall) } } },
		Cell(1f) { inMod -> Tile(inModifier = inMod) { TileFigure("Revenus moyens par mois") { AmountText(inCents = inTable.averageIncome, inStyle = MaterialTheme.typography.headlineSmall) } } },
		Cell(1f) { inMod ->
			Tile(inModifier = inMod) {
				TileFigure("Taux d’épargne moyen") {
					Text(inTable.averageRate?.takeIf { !LocalAmountsHidden.current }?.let { formatPermille(it).removePrefix("+") } ?: "—", style = MaterialTheme.typography.headlineSmall)
				}
				Hint("Part des revenus qui n’est pas dépensée.")
			}
		},
		Cell(1.4f) { inMod ->
			Tile(inModifier = inMod, inTitle = "Plus fortes hausses") {
				if (inTable.rises.isEmpty()) Hint("Aucune catégorie au-dessus de sa moyenne le mois dernier.")
				inTable.rises.forEach { inRow ->
					Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
						Icon(CategoryIcons.of(inRow.category.icon), contentDescription = null, modifier = Modifier.size(16.dp))
						Spacer(Modifier.width(10.dp))
						Text(inRow.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
						AmountText(inCents = inRow.lastGap ?: 0L, inStyle = MaterialTheme.typography.bodyMedium, inShowPlusSign = true)
						Text(" vs moyenne", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			}
		},
		inMinWidth = 1000.dp
	)
}

/*
	Barres des dépenses (pleines) et des revenus (claires) de chaque mois, avec
	le taux d'épargne sous chaque mois.
	inTable : tableau d'analyse
*/
@Composable
private fun MonthBars(inTable: ExpenseAnalysis)
{
	val vInk    = MaterialTheme.colorScheme.onSurface									// dépenses
	val vLight  = MaterialTheme.colorScheme.outline										// revenus
	val vMax    = (inTable.totalExpense + inTable.totalIncome).maxOrNull()?.coerceAtLeast(1L) ?: 1L	// plus grande valeur
	val vHidden = LocalAmountsHidden.current											// mode discret
	Row(verticalAlignment = Alignment.CenterVertically) {
		SeriesDot(vInk); Spacer(Modifier.width(8.dp)); Text("Dépenses", style = MaterialTheme.typography.bodyMedium)
		Spacer(Modifier.width(20.dp))
		SeriesDot(vLight); Spacer(Modifier.width(8.dp)); Text("Revenus", style = MaterialTheme.typography.bodyMedium)
	}
	Spacer(Modifier.height(12.dp))
	Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "Dépenses et revenus des douze derniers mois" }) {
		val vSlot = size.width / inTable.months.size										// largeur d'un mois
		val vBar  = (vSlot * 0.28f).coerceAtMost(26.dp.toPx())							// largeur d'une barre
		val vGap  = 3.dp.toPx()															// espace entre les deux barres
		inTable.months.indices.forEach { inIndex ->
			val vMid = vSlot * inIndex + vSlot / 2f										// milieu du mois
			listOf(inTable.totalExpense[inIndex] to vInk, inTable.totalIncome[inIndex] to vLight).forEachIndexed { inSide, (inValue, inColor) ->
				val vHeight = if (vHidden) 0f else size.height * inValue.coerceAtLeast(0L) / vMax	// hauteur
				val vLeft   = if (inSide == 0) vMid - vGap / 2f - vBar else vMid + vGap / 2f	// bord gauche
				if (vHeight > 0f) drawRoundRect(inColor, Offset(vLeft, size.height - vHeight), Size(vBar, vHeight), CornerRadius(4.dp.toPx()))
			}
		}
	}
	Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
		inTable.months.forEachIndexed { inIndex, inMonth ->
			Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
				Text(monthLabel(inMonth, inIndex == inTable.currentIndex), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				Text(inTable.savingsRate[inIndex]?.takeIf { !vHidden }?.let { "${it / 10} %" } ?: "—", style = MaterialTheme.typography.labelMedium)
			}
		}
	}
	Hint("Sous chaque mois : taux d’épargne (part des revenus non dépensée).")
}

/*
	Tableau catégories × mois : dépenses puis revenus, totaux, épargne et taux
	d'épargne ; défile horizontalement si la fenêtre est étroite.
	inTable   : tableau d'analyse
	inOnCell  : clic sur une case (catégorie, mois)
*/
@Composable
private fun Table(
	inTable  : ExpenseAnalysis,
	inOnCell : (Category, YearMonth) -> Unit
)
{
	val vStrong = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)	// lignes de total
	BoxWithConstraints(Modifier.fillMaxWidth()) {
	val vMonth  = ((maxWidth - 200.dp - 168.dp) / inTable.months.size).coerceAtLeast(58.dp)	// largeur d'un mois
	CompositionLocalProvider(LocalColumns provides Columns(month = vMonth)) {
	Column(Modifier.horizontalScroll(rememberScrollState())) {
		Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
			Spacer(Modifier.width(LocalColumns.current.name))
			inTable.months.forEachIndexed { inIndex, inMonth -> HeadCell(monthLabel(inMonth, inIndex == inTable.currentIndex), LocalColumns.current.month) }
			HeadCell("Moyenne", LocalColumns.current.summary)
			HeadCell("Écart", LocalColumns.current.summary)
		}
		HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
		Section("Dépenses")
		inTable.expenses.forEach { inRow -> CategoryRow(inTable, inRow, inOnCell) }
		TotalRow(inTable, "Total des dépenses", inTable.totalExpense, vStrong)
		Section("Revenus")
		inTable.incomes.forEach { inRow -> CategoryRow(inTable, inRow, inOnCell) }
		TotalRow(inTable, "Total des revenus", inTable.totalIncome, vStrong)
		HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
		TotalRow(inTable, "Épargne (revenus − dépenses)", inTable.months.indices.map { inTable.totalIncome[it] - inTable.totalExpense[it] }, vStrong, inSigned = true)
		Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
			NameCell("Taux d’épargne", vStrong)
			inTable.savingsRate.forEach { inRate -> TextCell(inRate?.takeIf { !LocalAmountsHidden.current }?.let { "${it / 10} %" } ?: "—", LocalColumns.current.month) }
			TextCell(inTable.averageRate?.takeIf { !LocalAmountsHidden.current }?.let { "${it / 10} %" } ?: "—", LocalColumns.current.summary)
		}
	}
	}
	}
}

/*
	Ligne d'une catégorie : montant de chaque mois (cliquable), moyenne, écart.
	inTable  : tableau d'analyse
	inRow    : ligne
	inOnCell : clic sur une case
*/
@Composable
private fun CategoryRow(
	inTable  : ExpenseAnalysis,
	inRow    : AnalysisRow,
	inOnCell : (Category, YearMonth) -> Unit
)
{
	Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
		Row(Modifier.width(LocalColumns.current.name).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
			Icon(CategoryIcons.of(inRow.category.icon), contentDescription = null, modifier = Modifier.size(16.dp))
			Spacer(Modifier.width(10.dp))
			Text(inRow.category.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
		}
		inRow.values.forEachIndexed { inIndex, inValue ->
			AmountCell(inValue, LocalColumns.current.month, inMuted = inIndex == inTable.currentIndex) { inOnCell(inRow.category, inTable.months[inIndex]) }
		}
		AmountCell(inRow.average, LocalColumns.current.summary)
		AmountCell(inRow.lastGap ?: 0L, LocalColumns.current.summary, inSigned = true, inEmpty = inRow.lastGap == null)
	}
}

/*
	Ligne de total : libellé, montant de chaque mois et moyenne des mois
	complets suivis.
	inTable  : tableau (mois suivis)
	inLabel  : libellé
	inValues : montant de chaque mois
	inStyle  : style du texte
	inSigned : montants signés
*/
@Composable
private fun TotalRow(
	inTable  : ExpenseAnalysis,
	inLabel  : String,
	inValues : List<Long>,
	inStyle  : TextStyle,
	inSigned : Boolean = false
)
{
	Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
		NameCell(inLabel, inStyle)
		inValues.forEach { AmountCell(it, LocalColumns.current.month, inSigned = inSigned, inStyle = inStyle) }
		val vTracked = inValues.subList(inTable.firstIndex, inTable.currentIndex)		// mois complets suivis
		AmountCell(if (vTracked.isEmpty()) 0L else vTracked.average().roundToLong(), LocalColumns.current.summary, inSigned = inSigned, inStyle = inStyle)
	}
}

/*
	Titre d'une partie du tableau.
	inTitle : titre
*/
@Composable
private fun Section(inTitle: String)
{
	Text(inTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

/*
	Case d'en-tête, alignée à droite.
	inText  : texte
	inWidth : largeur
*/
@Composable
private fun HeadCell(
	inText  : String,
	inWidth : Dp
)
{
	Text(inText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(inWidth).padding(end = 10.dp))
}

/*
	Case du nom d'une ligne de total.
	inText  : texte
	inStyle : style
*/
@Composable
private fun NameCell(
	inText  : String,
	inStyle : TextStyle
)
{
	Text(inText, style = inStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(LocalColumns.current.name).padding(end = 12.dp))
}

/*
	Case de texte alignée à droite.
	inText  : texte
	inWidth : largeur
*/
@Composable
private fun TextCell(
	inText  : String,
	inWidth : Dp
)
{
	Text(inText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.width(inWidth).padding(end = 10.dp))
}

/*
	Case d'un montant, en euros arrondis (« 1 980 »), masquée en mode discret ;
	un tiret pour zéro ; cliquable si une action est donnée.
	inCents   : montant
	inWidth   : largeur
	inSigned  : signe « + » devant un montant positif
	inMuted   : montant atténué (mois en cours, incomplet)
	inEmpty   : case sans valeur (tiret)
	inStyle   : style
	inOnClick : clic ; null pour une case inerte
*/
@Composable
private fun AmountCell(
	inCents   : Long,
	inWidth   : Dp,
	inSigned  : Boolean   = false,
	inMuted   : Boolean   = false,
	inEmpty   : Boolean   = false,
	inStyle   : TextStyle = MaterialTheme.typography.bodyMedium,
	inOnClick : (() -> Unit)? = null
)
{
	val vText = when
	{
		inEmpty || inCents == 0L    -> "—"
		LocalAmountsHidden.current  -> "•••"
		else                        -> compactEuros(inCents, inSigned)
	}																				// texte de la case
	Box(
		modifier         = Modifier
			.width(inWidth)
			.clip(RoundedCornerShape(6.dp))
			.then(if (inOnClick != null && inCents != 0L) Modifier.clickable(onClickLabel = "Voir les opérations", onClick = inOnClick) else Modifier)
			.padding(vertical = 6.dp, horizontal = 10.dp),
		contentAlignment = Alignment.CenterEnd
	) {
		Text(vText, style = inStyle, color = if (inMuted || inEmpty || inCents == 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, maxLines = 1)
	}
}

/*
	Montant en euros entiers, espaces fines des milliers (« 1 980 », « +12 »).
	inCents  : montant
	inSigned : signe « + » devant un montant positif
*/
private fun compactEuros(
	inCents  : Long,
	inSigned : Boolean
): String
{
	val vEuros  = (abs(inCents) / 100.0).roundToLong()								// euros arrondis
	val vDigits = vEuros.toString().reversed().chunked(3).joinToString("\u202F").reversed()	// milliers séparés
	val vSign   = when { inCents < 0 -> "−"; inSigned && inCents > 0 -> "+"; else -> "" }	// signe
	return vSign + vDigits
}

/*
	Mois en tête de colonne (« oct. »), marqué d'une étoile s'il est en cours.
	inMonth   : mois
	inCurrent : mois en cours
*/
private fun monthLabel(
	inMonth   : YearMonth,
	inCurrent : Boolean
): String = inMonth.format(kMonthShort) + if (inCurrent) "*" else ""
