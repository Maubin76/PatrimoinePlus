package fr.maubin.patrimoineplus.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.CashFlow
import fr.maubin.patrimoineplus.ui.budget.CashFlowDiagram
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import kotlin.math.max

private val kNodeWidth  = 14.dp														// largeur d'un nœud
private val kNodeGap    = 12.dp														// espace entre deux nœuds d'une colonne
private val kSideLabels = 210.dp														// place des étiquettes de chaque côté

/*
	Diagramme de cashflow (Sankey) : les entrées, à gauche, se rejoignent dans
	le budget du mois, au centre, qui se répartit à droite en dépenses,
	investissements et reste. L'épaisseur de chaque flux est proportionnelle à
	son montant. Monochrome : entrées et investissements foncés, dépenses
	claires, reste en filigrane ; un déficit apparaît comme une entrée rouge.
	inDiagram     : diagramme calculé
	inCenterTitle : titre du nœud central (« Budget du mois »)
	inModifier    : modificateur appliqué
*/
@Composable
fun SankeyDiagram(
	inDiagram     : CashFlowDiagram,
	inCenterTitle : String,
	inModifier    : Modifier = Modifier
)
{
	val vColors   = MaterialTheme.colorScheme											// palette courante
	val vMeasurer = rememberTextMeasurer()												// mesure des étiquettes
	val vHidden   = LocalAmountsHidden.current											// mode discret
	val vName     = MaterialTheme.typography.bodyMedium.copy(color = vColors.onSurface)	// nom d'un flux
	val vAmount   = MaterialTheme.typography.bodySmall.copy(color = vColors.onSurfaceVariant)	// montant d'un flux
	val vCount    = max(inDiagram.sources.size, inDiagram.outputs.size)				// nœuds de la colonne la plus chargée
	val vHeight   = max(380, 64 * vCount).dp											// hauteur du dessin
	Canvas(
		inModifier.fillMaxWidth().height(vHeight).semantics {
			contentDescription = "Cashflow : " + inDiagram.sources.joinToString { it.name } + " vers " + inDiagram.outputs.joinToString { it.name }
		}
	) {
		if (inDiagram.total <= 0L) return@Canvas
		val vNode   = kNodeWidth.toPx()													// largeur d'un nœud
		val vGap    = kNodeGap.toPx()													// espace entre nœuds
		val vSide   = kSideLabels.toPx()												// place des étiquettes
		val vLeftX  = vSide																// nœuds des entrées
		val vRightX = size.width - vSide - vNode										// nœuds des sorties
		val vMidX   = vLeftX + (vRightX - vLeftX) * 0.4f								// nœud central, plus près des entrées : la répartition a de la place
		val vTop    = 28.dp.toPx()														// place du titre central
		val vUsable = size.height - vTop - 8.dp.toPx()									// hauteur utile
		val vScale  = (vUsable - vGap * (vCount - 1).coerceAtLeast(0)) / inDiagram.total	// pixels par centime
		val vMidH   = inDiagram.total * vScale											// hauteur du nœud central
		val vMidY   = vTop + (vUsable - vMidH) / 2f										// haut du nœud central

		/*
			Couleur d'un flux selon sa rubrique.
			inFlow : flux
			inLeft : flux entrant
		*/
		fun tint(inFlow: CashFlow, inLeft: Boolean): Color =
			when
			{
				inFlow.kind == null && inLeft          -> vColors.error						// déficit
				inFlow.kind == null                    -> vColors.outline					// reste
				inFlow.kind == BudgetSection.EXPENSE   -> vColors.secondary
				else                                   -> vColors.onSurface
			}

		/*
			Colonne de nœuds et leurs rubans vers (ou depuis) le nœud central.
			inFlows : flux de la colonne
			inLeft  : colonne de gauche (entrées)
		*/
		fun column(inFlows: List<CashFlow>, inLeft: Boolean)
		{
			val vStack = inFlows.sumOf { it.amount } * vScale + vGap * (inFlows.size - 1).coerceAtLeast(0)	// hauteur de la colonne
			var vY     = vTop + (vUsable - vStack) / 2f										// haut du nœud courant
			var vMidAt = vMidY																	// arrivée sur le nœud central
			val vX     = if (inLeft) vLeftX else vRightX										// abscisse des nœuds
			val vWant  = mutableListOf<Float>()													// milieu de chaque nœud (place voulue des étiquettes)
			inFlows.forEach { inFlow ->
				val vH     = inFlow.amount * vScale												// épaisseur du flux
				val vColor = tint(inFlow, inLeft)												// couleur
				val vFrom  = if (inLeft) vX + vNode else vMidX + vNode							// départ du ruban
				val vTo    = if (inLeft) vMidX else vX											// arrivée du ruban
				val vYFrom = if (inLeft) vY else vMidAt											// haut au départ
				val vYTo   = if (inLeft) vMidAt else vY											// haut à l'arrivée
				val vBend  = (vTo - vFrom) / 2f													// courbure
				val vPath  = Path().apply {
					moveTo(vFrom, vYFrom)
					cubicTo(vFrom + vBend, vYFrom, vTo - vBend, vYTo, vTo, vYTo)
					lineTo(vTo, vYTo + vH)
					cubicTo(vTo - vBend, vYTo + vH, vFrom + vBend, vYFrom + vH, vFrom, vYFrom + vH)
					close()
				}																				// ruban
				drawPath(vPath, vColor.copy(alpha = if (inFlow.kind == null && !inLeft) 0.12f else 0.22f))
				drawRect(vColor, Offset(vX, vY), Size(vNode, max(vH, 1.5f)))
				vWant  += vY + vH / 2f
				vY     += vH + vGap
				vMidAt += vH
			}
			val vLabelH = 34.dp.toPx()															// hauteur d'une étiquette (nom et montant)
			val vPlaced = spread(vWant, vLabelH, vTop, size.height)							// places sans chevauchement
			val vEdge   = if (inLeft) vX - 4.dp.toPx() else vX + vNode + 4.dp.toPx()			// bord du nœud côté étiquette
			val vTextX  = if (inLeft) vX - 14.dp.toPx() else vX + vNode + 14.dp.toPx()			// bord de l'étiquette
			inFlows.forEachIndexed { inIndex, inFlow ->
				if (kotlin.math.abs(vPlaced[inIndex] - vWant[inIndex]) > 2f)
				{
					drawLine(vColors.outline, Offset(vEdge, vWant[inIndex]), Offset(vTextX + (if (inLeft) 4.dp.toPx() else -4.dp.toPx()), vPlaced[inIndex]), 1.dp.toPx())	// renvoi vers l'étiquette déplacée
				}
				label(vMeasurer, inFlow, vHidden, inDiagram.total, vTextX, vPlaced[inIndex], inLeft, vSide - 22.dp.toPx(), vName, vAmount)
			}
		}

		column(inDiagram.sources, inLeft = true)
		column(inDiagram.outputs, inLeft = false)
		drawRect(vColors.onSurface, Offset(vMidX, vMidY), Size(vNode, vMidH))
		val vTitle = vMeasurer.measure("$inCenterTitle · ${if (vHidden) "•••" else formatEuros(inDiagram.total)}", vName.copy(fontWeight = FontWeight.SemiBold))	// titre central
		drawText(vTitle, topLeft = Offset(vMidX + vNode / 2f - vTitle.size.width / 2f, vMidY - vTitle.size.height - 6.dp.toPx()))
	}
}

/*
	Étiquette d'un nœud : nom, puis montant et part du total ; alignée sur le
	nœud, à gauche des entrées ou à droite des sorties.
	inMeasurer : mesure du texte
	inFlow     : flux
	inHidden   : mode discret
	inTotal    : total du budget
	inX        : bord de l'étiquette côté nœud
	inCenterY  : milieu vertical du nœud
	inLeft     : étiquette à gauche du nœud (alignée à droite)
	inWidth    : largeur disponible
	inName     : style du nom
	inAmount   : style du montant
*/
private fun DrawScope.label(
	inMeasurer : TextMeasurer,
	inFlow     : CashFlow,
	inHidden   : Boolean,
	inTotal    : Long,
	inX        : Float,
	inCenterY  : Float,
	inLeft     : Boolean,
	inWidth    : Float,
	inName     : TextStyle,
	inAmount   : TextStyle
)
{
	val vLimit  = Constraints(maxWidth = inWidth.toInt().coerceAtLeast(1))				// largeur maximale
	val vName   = inMeasurer.measure(inFlow.name, inName, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = vLimit)	// nom
	val vShare  = if (inTotal > 0) " · ${Math.round(inFlow.amount * 100.0 / inTotal)} %" else ""	// part du total
	val vAmount = inMeasurer.measure(if (inHidden) "•••" else formatEuros(inFlow.amount) + vShare, inAmount, maxLines = 1, constraints = vLimit)	// montant
	val vTop    = inCenterY - (vName.size.height + vAmount.size.height) / 2f			// haut du bloc
	drawText(vName, topLeft = Offset(if (inLeft) inX - vName.size.width else inX, vTop))
	drawText(vAmount, topLeft = Offset(if (inLeft) inX - vAmount.size.width else inX, vTop + vName.size.height))
}

/*
	Places verticales des étiquettes d'une colonne : au plus près du milieu de
	leur nœud, sans se chevaucher (écart minimal d'une hauteur d'étiquette),
	dans les limites du dessin.
	inWanted : milieux voulus, de haut en bas
	inHeight : hauteur d'une étiquette
	inTop    : haut de la zone
	inBottom : bas de la zone
*/
internal fun spread(
	inWanted : List<Float>,
	inHeight : Float,
	inTop    : Float,
	inBottom : Float
): List<Float>
{
	val vPlaced = inWanted.toMutableList()											// places retenues
	for (vIndex in 1 until vPlaced.size)
	{
		vPlaced[vIndex] = maxOf(vPlaced[vIndex], vPlaced[vIndex - 1] + inHeight)		// pousse vers le bas
	}
	val vOver = (vPlaced.lastOrNull() ?: 0f) + inHeight / 2f - inBottom					// dépassement en bas
	if (vOver > 0f)
	{
		vPlaced[vPlaced.lastIndex] -= vOver
		for (vIndex in vPlaced.lastIndex - 1 downTo 0)
		{
			vPlaced[vIndex] = minOf(vPlaced[vIndex], vPlaced[vIndex + 1] - inHeight)	// remonte au besoin
		}
	}
	return vPlaced.map { maxOf(it, inTop + inHeight / 2f) }
}
