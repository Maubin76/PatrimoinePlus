package fr.maubin.patrimoineplus.ui.chart

import kotlin.math.sqrt

/*
	Segment de courbe de Bézier cubique entre deux points consécutifs.
	c1x, c1y : premier point de contrôle
	c2x, c2y : second point de contrôle
	x, y     : point d'arrivée
*/
data class CurveSegment(
	val c1x : Float,
	val c1y : Float,
	val c2x : Float,
	val c2y : Float,
	val x   : Float,
	val y   : Float
)

/*
	Courbe lissée passant par des points (abscisses croissantes) :
	interpolation cubique monotone (Fritsch–Carlson), convertie en segments
	de Bézier. La courbe est souple et arrondie mais ne dépasse jamais les
	valeurs réelles : pas de creux sous un minimum ni de pic au-dessus d'un
	maximum, un palier reste plat. Vide avec moins de deux points.
	inX : abscisses, strictement croissantes
	inY : ordonnées
*/
fun smoothCurve(
	inX : List<Float>,
	inY : List<Float>
): List<CurveSegment>
{
	val vCount = inX.size																// nombre de points
	if (vCount < 2 || inY.size != vCount) return emptyList()
	val vSlope = FloatArray(vCount - 1) { (inY[it + 1] - inY[it]) / (inX[it + 1] - inX[it]) }	// pentes des segments
	val vTan   = FloatArray(vCount) { inIndex ->
		when
		{
			inIndex == 0                                         -> vSlope[0]
			inIndex == vCount - 1                                -> vSlope[vCount - 2]
			vSlope[inIndex - 1] * vSlope[inIndex] <= 0f          -> 0f				// extremum local ou palier : tangente horizontale
			else                                                 -> (vSlope[inIndex - 1] + vSlope[inIndex]) / 2f
		}
	}																					// tangentes aux points
	vSlope.indices.forEach { inSeg ->
		if (vSlope[inSeg] == 0f)
		{
			vTan[inSeg]     = 0f
			vTan[inSeg + 1] = 0f
		}
		else
		{
			val vA   = vTan[inSeg] / vSlope[inSeg]											// rapport de la tangente de départ
			val vB   = vTan[inSeg + 1] / vSlope[inSeg]										// rapport de la tangente d'arrivée
			val vSum = vA * vA + vB * vB														// condition de monotonie
			if (vSum > 9f)
			{
				val vT = 3f / sqrt(vSum)													// réduction des tangentes
				vTan[inSeg]     = vT * vA * vSlope[inSeg]
				vTan[inSeg + 1] = vT * vB * vSlope[inSeg]
			}
		}
	}
	return vSlope.indices.map { inSeg ->
		val vH = (inX[inSeg + 1] - inX[inSeg]) / 3f											// tiers de l'écart horizontal
		CurveSegment(
			c1x = inX[inSeg] + vH,
			c1y = inY[inSeg] + vTan[inSeg] * vH,
			c2x = inX[inSeg + 1] - vH,
			c2y = inY[inSeg + 1] - vTan[inSeg + 1] * vH,
			x   = inX[inSeg + 1],
			y   = inY[inSeg + 1]
		)
	}
}
