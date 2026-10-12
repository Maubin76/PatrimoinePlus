package fr.maubin.patrimoineplus.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

private val kInk   = Color(0xFF0A0A0A)											// encre du logo (fixe, comme l'icône)
private val kPaper = Color.White												// pastille du logo

/*
	Logo dessiné : disque noir, pastille blanche, deux chevrons emboîtés (mêmes
	proportions que l'icône de l'appli Android).
	inSize : diamètre du disque
*/
@Composable
fun PatrimoineLogo(inSize: Dp)
{
	Canvas(Modifier.size(inSize).semantics { contentDescription = "Logo PatrimoinePlus" }) {
		val vUnit   = size.minDimension / 72f										// une unité de la grille de l'icône
		val vCenter = Offset(size.width / 2f, size.height / 2f)						// centre du logo
		drawCircle(kInk, radius = 36f * vUnit, center = vCenter)
		drawCircle(kPaper, radius = 27f * vUnit, center = vCenter)
		clipPath(Path().apply { addOval(Rect(vCenter, 27f * vUnit)) }) {
			chevron(this, vUnit, listOf(39f to 37.5f, 55.5f to 51f, 39f to 64.5f))
			chevron(this, vUnit, listOf(69f to 43.5f, 52.5f to 57f, 69f to 70.5f))
		}
	}
}

/*
	Dessine un chevron donné dans la grille 108 × 108 de l'icône.
	inScope  : zone de dessin
	inUnit   : taille d'une unité
	inPoints : trois sommets
*/
private fun chevron(
	inScope  : DrawScope,
	inUnit   : Float,
	inPoints : List<Pair<Float, Float>>
)
{
	val vLeft = inScope.size.width / 2f - 54f * inUnit							// origine horizontale de la grille
	val vTop  = inScope.size.height / 2f - 54f * inUnit							// origine verticale de la grille
	val vPath = Path().apply {
		inPoints.forEachIndexed { inIndex, (inX, inY) ->
			if (inIndex == 0) moveTo(vLeft + inX * inUnit, vTop + inY * inUnit) else lineTo(vLeft + inX * inUnit, vTop + inY * inUnit)
		}
		close()
	}																			// triangle
	inScope.drawPath(vPath, kInk)
}
