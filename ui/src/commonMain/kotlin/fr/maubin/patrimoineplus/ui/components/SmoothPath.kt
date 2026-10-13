package fr.maubin.patrimoineplus.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import fr.maubin.patrimoineplus.ui.chart.smoothCurve

/*
	Tracé lissé passant par des points (abscisses croissantes) : courbes
	douces et arrondies, sans dépasser les valeurs réelles (voir
	smoothCurve). Un seul point : tracé réduit à ce point.
	inPoints : points de la courbe, de gauche à droite
*/
fun smoothPath(inPoints: List<Offset>): Path =
	Path().apply {
		if (inPoints.isEmpty()) return@apply
		moveTo(inPoints[0].x, inPoints[0].y)
		smoothCurve(inPoints.map { it.x }, inPoints.map { it.y }).forEach { inSeg ->
			cubicTo(inSeg.c1x, inSeg.c1y, inSeg.c2x, inSeg.c2y, inSeg.x, inSeg.y)
		}
	}
