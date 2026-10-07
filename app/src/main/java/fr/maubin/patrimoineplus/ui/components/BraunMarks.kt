package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
	Touche « = » de la calculatrice : rond jaune portant deux traits. Elle
	précède un résultat calculé et n'est jamais cliquable.
	inSize     : diamètre de la touche
	inModifier : modificateur appliqué
*/
@Composable
fun EqualsKey(
	inSize     : Dp       = 32.dp,
	inModifier : Modifier = Modifier
)
{
	val vKey = MaterialTheme.colorScheme.primaryContainer					// jaune de la touche
	val vInk = MaterialTheme.colorScheme.onPrimaryContainer				// traits du signe
	Canvas(modifier = inModifier.size(inSize)) {
		drawCircle(color = vKey)
		val vHalf   = size.width * 0.2f									// demi-longueur d'un trait
		val vGap    = size.height * 0.09f									// demi-écart entre les traits
		val vStroke = size.width * 0.07f									// épaisseur d'un trait
		listOf(-vGap, vGap).forEach { inDy ->
			drawLine(
				color       = vInk,
				start       = Offset(center.x - vHalf, center.y + inDy),
				end         = Offset(center.x + vHalf, center.y + inDy),
				strokeWidth = vStroke,
				cap         = StrokeCap.Round
			)
		}
	}
}

/*
	Point de couleur identifiant une série (un type de compte).
	inColor    : couleur de la série
	inModifier : modificateur appliqué
*/
@Composable
fun SeriesDot(
	inColor    : Color,
	inModifier : Modifier = Modifier
)
{
	Box(modifier = inModifier.size(10.dp).background(inColor, CircleShape))
}

/*
	Segment d'une barre de répartition.
	value : poids du segment (montant positif, en centimes)
	color : couleur de la série
*/
data class BarSegment(
	val value : Long,
	val color : Color
)

/*
	Barre de répartition proportionnelle, sur une rampe fixe : la largeur
	totale vaut toujours 100 %. Sans segment, seul le rail est dessiné.
	inSegments    : segments, dans l'ordre d'affichage ; les poids négatifs sont ignorés
	inDescription : description lue par TalkBack
	inModifier    : modificateur appliqué
*/
@Composable
fun ProportionBar(
	inSegments    : List<BarSegment>,
	inDescription : String,
	inModifier    : Modifier = Modifier
)
{
	val vTrack = MaterialTheme.colorScheme.surfaceContainerHighest			// couleur du rail
	Canvas(
		modifier = inModifier
			.fillMaxWidth()
			.height(8.dp)
			.semantics { contentDescription = inDescription }
	) {
		val vRadius   = CornerRadius(size.height / 2f)						// arrondi des extrémités
		val vGap      = 2.dp.toPx()											// espace entre segments
		val vPositive = inSegments.filter { it.value > 0 }					// segments dessinables
		val vTotal    = vPositive.sumOf { it.value }.toFloat()				// somme des poids
		drawRoundRect(color = vTrack, cornerRadius = vRadius)
		if (vTotal <= 0f)
		{
			return@Canvas
		}
		val vUsable = size.width - vGap * (vPositive.size - 1)				// largeur hors espaces
		var vX      = 0f													// abscisse du segment courant
		vPositive.forEach { inSegment ->
			val vWidth = vUsable * inSegment.value / vTotal					// largeur proportionnelle
			drawRoundRect(
				color        = inSegment.color,
				topLeft      = Offset(vX, 0f),
				size         = Size(vWidth, size.height),
				cornerRadius = vRadius
			)
			vX += vWidth + vGap
		}
	}
}
