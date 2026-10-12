package fr.maubin.patrimoineplus.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.CashFlowDiagram
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import kotlin.math.max
import kotlin.math.min

private val kNodeWidth = 8.dp															// largeur d'un nœud (bouts arrondis)
private val kGap       = 8.dp															// espace entre deux nœuds d'un thème
private val kLaneGap   = 16.dp															// espace entre deux thèmes
private val kChipH     = 24.dp															// hauteur d'une pastille d'étiquette
private val kFlowSpan  = 380.dp															// hauteur donnée au total du budget

private val kSource    = Color(0xFFB7C1F7)												// entrées (pervenche)
private val kBudget    = Color(0xFFF7D0B0)												// budget du mois (pêche)
private val kInvest    = Color(0xFFF2B4C3)												// investissements (rose)
private val kRest      = Color(0xFFC8CDD7)												// reste (gris bleuté)
private val kDeficit   = Color(0xFFEE9A9A)												// déficit (rouge doux)
private val kThemes    = listOf(															// thèmes de dépenses, dans l'ordre
	Color(0xFFD6B6E6), Color(0xFFC4B6F5), Color(0xFFA7DCD5), Color(0xFFF5B6A6),
	Color(0xFFF2D8A2), Color(0xFFB7E0C2), Color(0xFFAECCEA), Color(0xFFE6C4AF)
)

/*
	Nœud placé : position verticale et teinte.
	top    : haut du nœud
	height : hauteur (épaisseur du flux)
	color  : teinte
*/
private class Placed(
	val top    : Float,
	val height : Float,
	val color  : Color
)
{
	// Milieu vertical.
	val center: Float get() = top + height / 2f
}

/*
	Mesures du dessin, en pixels.
	node    : largeur d'un nœud
	gap     : espace entre deux nœuds d'un thème
	laneGap : espace entre deux thèmes
	chip    : hauteur d'une pastille (place minimale d'un nœud)
	span    : hauteur donnée au total du budget
	pad     : marge haute et basse
*/
private class Metrics(
	val node    : Float,
	val gap     : Float,
	val laneGap : Float,
	val chip    : Float,
	val span    : Float,
	val pad     : Float
)

/*
	Placement vertical de tous les nœuds.
	sources : nœuds des entrées
	budget  : nœud du budget
	groups  : nœuds des thèmes
	items   : nœuds du détail, par thème
	height  : hauteur totale du dessin
*/
private class Layout(
	val sources : List<Placed>,
	val budget  : Placed,
	val groups  : List<Placed>,
	val items   : List<List<Placed>>,
	val height  : Float
)

/*
	Diagramme de cashflow à la façon d'un Sankey : entrées → budget du mois →
	thèmes des sorties (investissements en haut, puis dépenses, puis reste) →
	détail de chaque thème. Rubans en courbes douces et teintes pastel par
	thème, nœuds aux bouts arrondis, étiquettes « Nom : montant » dans des
	pastilles posées le long des flux. L'épaisseur de chaque flux est
	proportionnelle à son montant ; un petit flux garde une place lisible.
	inDiagram     : diagramme calculé
	inCenterTitle : nom du nœud du budget (« Budget »)
	inModifier    : modificateur appliqué
*/
@Composable
fun SankeyDiagram(
	inDiagram     : CashFlowDiagram,
	inCenterTitle : String,
	inModifier    : Modifier = Modifier
)
{
	val vDark     = MaterialTheme.colorScheme.surface.luminance() < 0.5f				// fond sombre
	val vMeasurer = rememberTextMeasurer()												// mesure des étiquettes
	val vHidden   = LocalAmountsHidden.current											// mode discret
	val vText     = MaterialTheme.typography.labelLarge.copy(color = Color(0xFF1D1D24))	// texte des pastilles (toujours sur fond clair)
	val vPx       = with(LocalDensity.current) { Metrics(kNodeWidth.toPx(), kGap.toPx(), kLaneGap.toPx(), kChipH.toPx(), kFlowSpan.toPx(), 12.dp.toPx()) }	// mesures en pixels
	val vLayout   = layoutDiagram(inDiagram, vPx)											// placement vertical
	val vHeight   = with(LocalDensity.current) { vLayout.height.toDp() }				// hauteur du dessin
	Canvas(
		inModifier.fillMaxWidth().height(vHeight).semantics {
			contentDescription = "Cashflow : " + inDiagram.sources.joinToString { it.name } + " vers " + inDiagram.groups.joinToString { it.name }
		}
	) {
		if (inDiagram.total <= 0L) return@Canvas
		val vNode   = vPx.node																// largeur d'un nœud
		val vX0     = 2.dp.toPx()															// colonne des entrées
		val vX1     = size.width * 0.27f													// colonne du budget
		val vX2     = size.width * 0.56f													// colonne des thèmes
		val vX3     = size.width - vNode - 2.dp.toPx()										// colonne du détail
		val vAlpha  = if (vDark) 0.74f else 0.6f												// opacité des rubans
		val vEdge   = { inColor: Color -> if (vDark) inColor else lerp(inColor, Color.Black, 0.18f) }	// teinte d'un nœud
		val vLabel  = { inName: String, inCents: Long -> "$inName : ${if (vHidden) "•••" else formatEuros(inCents)}" }	// texte d'une pastille

		// Entrées → budget.
		var vAt = vLayout.budget.top																// arrivée sur le budget
		vLayout.sources.forEach { inNode ->
			ribbon(vX0 + vNode, inNode.top, vX1, vAt, inNode.height, inNode.color.copy(alpha = vAlpha))
			vAt += inNode.height
		}
		// Budget → thèmes, puis thèmes → détail.
		vAt = vLayout.budget.top
		inDiagram.groups.forEachIndexed { inIndex, inGroup ->
			val vGroupP = vLayout.groups[inIndex]													// nœud du thème
			ribbon(vX1 + vNode, vAt, vX2, vGroupP.top, vGroupP.height, kBudget.copy(alpha = vAlpha))
			vAt += vGroupP.height
			var vFrom = vGroupP.top																	// départ depuis le thème
			inGroup.items.indices.forEach { inItem ->
				val vItemP = vLayout.items[inIndex][inItem]											// nœud du détail
				ribbon(vX2 + vNode, vFrom, vX3, vItemP.top, vItemP.height, vGroupP.color.copy(alpha = vAlpha))
				vFrom += vItemP.height
			}
		}
		// Nœuds.
		vLayout.sources.forEach { node(vX0, it, vNode, vEdge(it.color)) }
		node(vX1, vLayout.budget, vNode, vEdge(kBudget))
		vLayout.groups.forEachIndexed { inIndex, inPlaced ->
			node(vX2, inPlaced, vNode, vEdge(inPlaced.color))
			vLayout.items[inIndex].forEach { node(vX3, it, vNode, vEdge(inPlaced.color)) }
		}
		// Pastilles.
		val vGapX = 8.dp.toPx()																	// écart entre nœud et pastille
		inDiagram.sources.forEachIndexed { inIndex, inFlow ->
			chip(vMeasurer, vLabel(inFlow.name, inFlow.amount), vText, vX0 + vNode + vGapX, vLayout.sources[inIndex].center, inLeftOf = false, inMaxWidth = vX1 - vX0 - vNode - 2 * vGapX)
		}
		chip(vMeasurer, vLabel(inCenterTitle, inDiagram.total), vText, vX1 + vNode / 2f, vLayout.budget.top - vPx.chip / 2f - 4.dp.toPx(), inLeftOf = null, inMaxWidth = vX2 - vX0)	// au-dessus du nœud : ne se mêle pas aux entrées
		inDiagram.groups.forEachIndexed { inIndex, inGroup ->
			chip(vMeasurer, vLabel(inGroup.name, inGroup.amount), vText, vX2 - vGapX, vLayout.groups[inIndex].center, inLeftOf = true, inMaxWidth = vX2 - vX1 - vNode - 2 * vGapX)
			inGroup.items.forEachIndexed { inItem, inFlow ->
				chip(vMeasurer, vLabel(inFlow.name, inFlow.amount), vText, vX3 - vGapX, vLayout.items[inIndex][inItem].center, inLeftOf = true, inMaxWidth = vX3 - vX2 - vNode - 2 * vGapX)
			}
		}
	}
}

/*
	Place les nœuds : chaque thème occupe un couloir assez haut pour les
	pastilles de son détail ; son nœud est centré sur le couloir. Le budget
	est centré sur l'ensemble des thèmes, les entrées sur le budget.
	inDiagram : diagramme
	inPx      : mesures
*/
private fun layoutDiagram(
	inDiagram : CashFlowDiagram,
	inPx      : Metrics
): Layout
{
	val vScale  = if (inDiagram.total > 0) inPx.span / inDiagram.total else 0f		// pixels par centime
	val vSlot   = { inCents: Long -> max(inCents * vScale, inPx.chip) }				// place d'un nœud
	val vTop    = inPx.pad + inPx.chip + inPx.gap											// haut des thèmes (place de la pastille du budget)
	var vY      = vTop																	// haut du couloir courant
	var vTheme  = 0																		// rang des teintes de dépenses
	val vGroups = mutableListOf<Placed>()												// nœuds des thèmes
	val vItems  = mutableListOf<List<Placed>>()										// nœuds du détail
	inDiagram.groups.forEach { inGroup ->
		val vColor = when (inGroup.kind)
		{
			BudgetSection.INVESTMENT -> kInvest
			null                     -> kRest
			else                     -> kThemes[vTheme++ % kThemes.size]
		}																				// teinte du thème
		val vLane  = if (inGroup.items.isEmpty()) vSlot(inGroup.amount)
			else inGroup.items.sumOf { vSlot(it.amount).toDouble() }.toFloat() + inPx.gap * (inGroup.items.size - 1)	// hauteur du couloir
		val vH     = inGroup.amount * vScale											// épaisseur du thème
		vGroups += Placed(vY + vLane / 2f - vH / 2f, max(vH, 3f), vColor)
		var vItemY = vY																	// haut de la place du détail
		vItems += inGroup.items.map { inFlow ->
			val vSlotH = vSlot(inFlow.amount)											// place du détail
			val vItemH = inFlow.amount * vScale											// épaisseur du détail
			Placed(vItemY + vSlotH / 2f - vItemH / 2f, max(vItemH, 3f), vColor).also { vItemY += vSlotH + inPx.gap }
		}
		vY += vLane + inPx.laneGap
	}
	val vGroupsBottom = vY - inPx.laneGap												// bas du dernier thème
	val vBudgetH = inDiagram.total * vScale											// épaisseur du budget
	val vMid     = (vTop + vGroupsBottom) / 2f											// milieu des thèmes
	val vBudget  = Placed(vMid - vBudgetH / 2f, vBudgetH, kBudget)						// nœud du budget
	val vStack   = inDiagram.sources.sumOf { vSlot(it.amount).toDouble() }.toFloat() + inPx.gap * (inDiagram.sources.size - 1)	// hauteur des entrées
	var vSrcY    = max(vTop, vMid - vStack / 2f)									// haut de la première entrée
	val vSources = inDiagram.sources.map { inFlow ->
		val vSlotH = vSlot(inFlow.amount)												// place de l'entrée
		val vH     = inFlow.amount * vScale												// épaisseur de l'entrée
		Placed(vSrcY + vSlotH / 2f - vH / 2f, max(vH, 3f), if (inFlow.kind == null) kDeficit else kSource).also { vSrcY += vSlotH + inPx.gap }
	}
	val vBottom = max(vGroupsBottom, max(vSrcY - inPx.gap, vBudget.top + vBudget.height)) + inPx.pad	// bas du dessin
	return Layout(vSources, vBudget, vGroups, vItems, vBottom)
}

/*
	Ruban d'un flux entre deux nœuds : courbe douce (Bézier, poignées à
	mi-chemin), de même épaisseur aux deux bouts.
	inX1     : bord droit du nœud de départ
	inY1     : haut du flux au départ
	inX2     : bord gauche du nœud d'arrivée
	inY2     : haut du flux à l'arrivée
	inHeight : épaisseur du flux
	inColor  : teinte
*/
private fun DrawScope.ribbon(
	inX1     : Float,
	inY1     : Float,
	inX2     : Float,
	inY2     : Float,
	inHeight : Float,
	inColor  : Color
)
{
	val vMidX = (inX1 + inX2) / 2f															// poignées des courbes
	val vPath = Path().apply {
		moveTo(inX1, inY1)
		cubicTo(vMidX, inY1, vMidX, inY2, inX2, inY2)
		lineTo(inX2, inY2 + inHeight)
		cubicTo(vMidX, inY2 + inHeight, vMidX, inY1 + inHeight, inX1, inY1 + inHeight)
		close()
	}																						// ruban
	drawPath(vPath, inColor)
}

/*
	Nœud : barre verticale aux bouts arrondis.
	inX     : gauche
	inNode  : place verticale
	inWidth : largeur
	inColor : teinte
*/
private fun DrawScope.node(
	inX     : Float,
	inNode  : Placed,
	inWidth : Float,
	inColor : Color
)
{
	drawRoundRect(inColor, Offset(inX, inNode.top), Size(inWidth, inNode.height), CornerRadius(inWidth / 2f))
}

/*
	Pastille d'étiquette : texte sur fond clair arrondi, centrée sur une
	hauteur, à gauche, à droite ou au milieu d'une abscisse.
	inMeasurer : mesure du texte
	inText     : texte
	inStyle    : style
	inX        : bord de la pastille côté nœud
	inCenterY  : milieu vertical
	inLeftOf   : pastille à gauche de inX ; null : centrée sur inX
	inMaxWidth : largeur maximale
*/
private fun DrawScope.chip(
	inMeasurer : TextMeasurer,
	inText     : String,
	inStyle    : TextStyle,
	inX        : Float,
	inCenterY  : Float,
	inLeftOf   : Boolean?,
	inMaxWidth : Float
)
{
	val vPadX   = 8.dp.toPx()																// marge intérieure
	val vLayout = inMeasurer.measure(inText, inStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = (inMaxWidth - 2 * vPadX).toInt().coerceAtLeast(1)))	// texte mesuré
	val vW      = vLayout.size.width + 2 * vPadX												// largeur de la pastille
	val vH      = min(kChipH.toPx(), vLayout.size.height + 6.dp.toPx())						// hauteur de la pastille
	val vLeft   = when (inLeftOf) { true -> inX - vW; false -> inX; null -> inX - vW / 2f }												// gauche de la pastille
	val vTop    = inCenterY - vH / 2f															// haut de la pastille
	drawRoundRect(Color(0xF2F3F4F8), Offset(vLeft, vTop), Size(vW, vH), CornerRadius(6.dp.toPx()))
	drawRoundRect(Color(0x1A000000), Offset(vLeft, vTop), Size(vW, vH), CornerRadius(6.dp.toPx()), style = Stroke(1f))
	drawText(vLayout, topLeft = Offset(vLeft + vPadX, inCenterY - vLayout.size.height / 2f))
}
