package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

private val kInk   = Color(0xFF0A0A0A)											// encre du logo (fixe, comme l'icône)
private val kPaper = Color.White												// pastille du logo

/*
	Logo dessiné : disque noir, pastille blanche, deux chevrons qui s'emboîtent
	(mêmes proportions que l'icône de l'appli). Les chevrons arrivent des
	bords de la pastille, qui les découpe, selon inChevrons.
	inSize     : diamètre du disque
	inChevrons : avancement des chevrons, de 0 (hors de la pastille) à 1 (en place)
*/
@Composable
fun PatrimoineLogo(
	inSize     : Dp,
	inChevrons : Float = 1f
)
{
	Canvas(Modifier.size(inSize).semantics { contentDescription = "Logo PatrimoinePlus" }) {
		val vUnit   = size.minDimension / 72f										// une unité de la grille de l'icône (72 visibles sur 108)
		val vCenter = Offset(size.width / 2f, size.height / 2f)						// centre du logo
		drawCircle(kInk, radius = 36f * vUnit, center = vCenter)
		drawCircle(kPaper, radius = 27f * vUnit, center = vCenter)
		val vPastille = Path().apply { addOval(androidx.compose.ui.geometry.Rect(vCenter, 27f * vUnit)) }	// découpe des chevrons
		val vShift    = (1f - inChevrons) * 30f * vUnit									// décalage restant
		clipPath(vPastille) {
			translate(left = -vShift) { chevron(this, vUnit, listOf(39f to 37.5f, 55.5f to 51f, 39f to 64.5f)) }
			translate(left = vShift) { chevron(this, vUnit, listOf(69f to 43.5f, 52.5f to 57f, 69f to 70.5f)) }
		}
	}
}

/*
	Dessine un chevron (triangle) donné dans la grille 108 × 108 de l'icône.
	inScope  : zone de dessin
	inUnit   : taille d'une unité de la grille
	inPoints : trois sommets, en unités de la grille
*/
private fun chevron(
	inScope  : DrawScope,
	inUnit   : Float,
	inPoints : List<Pair<Float, Float>>
)
{
	val vOrigin = inScope.size.width / 2f - 54f * inUnit						// position de l'origine de la grille
	val vTop    = inScope.size.height / 2f - 54f * inUnit						// position du haut de la grille
	val vPath   = Path().apply {
		inPoints.forEachIndexed { inIndex, (inX, inY) ->
			if (inIndex == 0) moveTo(vOrigin + inX * inUnit, vTop + inY * inUnit) else lineTo(vOrigin + inX * inUnit, vTop + inY * inUnit)
		}
		close()
	}																			// triangle
	inScope.drawPath(vPath, kInk)
}
