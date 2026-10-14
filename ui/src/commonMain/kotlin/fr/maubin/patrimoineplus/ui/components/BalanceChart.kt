package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint
import fr.maubin.patrimoineplus.domain.format.formatEuros
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.graphics.Color

private val kMonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)	// mois en toutes lettres

/*
	Courbe des soldes de clôture mois par mois, à l'encre, sur une échelle
	allant du plus bas au plus haut de la série ; ligne zéro en pointillés si
	elle est dans l'échelle. Le mois mis en avant porte un point plein ; toucher
	la courbe affiche la valeur du mois le plus proche au-dessus.
	inSeries    : points, du plus ancien au plus récent
	inHighlight : mois mis en avant
	inHeight    : hauteur du tracé (plus haute sur grand écran)
*/
@Composable
fun BalanceChart(
	inSeries    : List<SeriesPoint>,
	inHighlight : YearMonth,
	inHeight    : Dp = 140.dp
)
{
	if (inSeries.isEmpty())
	{
		return
	}
	val vColors  = MaterialTheme.colorScheme									// palette courante
	val vInk     = LocalAccent.current.takeIf { it != Color.Unspecified } ?: vColors.onSurface	// trait : couleur de la page (thème Coloré) ou encre
	val vHidden  = LocalAmountsHidden.current									// mode discret actif
	var vPicked  by remember(inSeries, inHighlight) { mutableStateOf(inSeries.indexOfFirst { it.month == inHighlight }.takeIf { it >= 0 } ?: inSeries.lastIndex) }	// point affiché
	val vPoint   = inSeries[vPicked]												// point affiché
	val vMin     = inSeries.minOf { it.valueCents }								// bas de l'échelle
	val vMax     = inSeries.maxOf { it.valueCents }							// haut de l'échelle
	val vSpan    = (vMax - vMin).coerceAtLeast(1L).toFloat()					// amplitude
	Column(Modifier.fillMaxWidth()) {
		Text(
			text  = "${vPoint.month.format(kMonthYear).replaceFirstChar { it.uppercase() }} : ${if (vHidden) "masqué" else formatEuros(vPoint.valueCents)}",
			style = MaterialTheme.typography.bodyMedium,
			color = vColors.onSurfaceVariant
		)
		Spacer(Modifier.height(12.dp))
		Canvas(
			modifier = Modifier
				.fillMaxWidth()
				.height(inHeight)
				.semantics {
					contentDescription = if (vHidden) "Évolution du solde sur ${inSeries.size} mois"
						else "Évolution du solde : de ${formatEuros(inSeries.first().valueCents)} à ${formatEuros(inSeries.last().valueCents)}"
				}
				.pointerInput(inSeries) {
					detectTapGestures { inTap ->
						val vStep = size.width / (inSeries.size - 1).coerceAtLeast(1)	// écart entre deux mois
						vPicked = Math.round(inTap.x / vStep).coerceIn(0, inSeries.lastIndex)
					}
				}
		) {
			val vInset = 6.dp.toPx()												// marge pour les points
			val vStep  = (size.width - 2 * vInset) / (inSeries.size - 1).coerceAtLeast(1)	// écart entre deux mois

			/*
				Position d'un point de la série.
				inIndex : rang du point
			*/
			fun at(inIndex: Int): Offset =
				Offset(vInset + inIndex * vStep, vInset + (size.height - 2 * vInset) * (1f - (inSeries[inIndex].valueCents - vMin) / vSpan))

			if (vMin < 0 && vMax > 0)
			{
				val vZero = vInset + (size.height - 2 * vInset) * (1f - (0 - vMin) / vSpan)	// ordonnée du zéro
				drawLine(vColors.outlineVariant, Offset(0f, vZero), Offset(size.width, vZero), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
			}
			val vPath = smoothPath(inSeries.indices.map(::at))					// tracé lissé de la courbe
			drawPath(vPath, vInk, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
			inSeries.indices.forEach { inIndex ->
				val vChosen = inIndex == vPicked								// point mis en avant
				drawCircle(if (vChosen) vInk else vColors.surface, if (vChosen) 5.dp.toPx() else 3.dp.toPx(), at(inIndex))
				if (!vChosen)
				{
					drawCircle(vInk, 3.dp.toPx(), at(inIndex), style = Stroke(1.5.dp.toPx()))
				}
			}
		}
		Spacer(Modifier.height(8.dp))
		Row(Modifier.fillMaxWidth()) {
			inSeries.forEachIndexed { inIndex, inPoint ->
				Text(
					text      = inPoint.month.month.getDisplayName(TextStyle.NARROW, Locale.FRANCE).uppercase(),
					style     = MaterialTheme.typography.labelSmall,
					color     = if (inIndex == vPicked) vColors.onSurface else vColors.onSurfaceVariant,
					textAlign = TextAlign.Center,
					modifier  = Modifier.weight(1f).padding(horizontal = 1.dp)
				)
			}
		}
	}
}
