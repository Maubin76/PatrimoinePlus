package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/*
	Couleurs d'une déclinaison du logo (verre sombre ou verre clair).
	fTileTop, fTileBottom : dégradé du fond de la tuile
	fHaloHigh, fHaloLow   : halos haut droit et bas gauche
	fTileEdge             : liseré de la tuile
	fBackTop, fBackBottom : dégradé de la carte de derrière
	fGlow                 : lueur sous la carte de devant
	fPlus                 : couleur du « + »
*/
private class LogoColors(
	val fTileTop    : Color,
	val fTileBottom : Color,
	val fHaloHigh   : Color,
	val fHaloLow    : Color,
	val fTileEdge   : Color,
	val fBackTop    : Color,
	val fBackBottom : Color,
	val fGlow       : Color,
	val fPlus       : Color
)

// Verre sombre : celui de l'icône de l'appli.
private val kDarkLogo  = LogoColors(
	fTileTop    = Color(0xFF15151D),
	fTileBottom = Color(0xFF07070B),
	fHaloHigh   = Color(0xFF5B4BE0).copy(alpha = 0.55f),
	fHaloLow    = Color(0xFF2A3A8C).copy(alpha = 0.45f),
	fTileEdge   = Color.White.copy(alpha = 0.12f),
	fBackTop    = Color(0xFF6F86E8).copy(alpha = 0.80f),
	fBackBottom = Color(0xFF2A3A8C).copy(alpha = 0.85f),
	fGlow       = Color(0xFF6A5CF5).copy(alpha = 0.50f),
	fPlus       = Color(0xFFF3F3F7)
)

// Verre clair : fond lavande, halos pastel, « + » violet profond.
private val kLightLogo = LogoColors(
	fTileTop    = Color(0xFFF7F5FE),
	fTileBottom = Color(0xFFE2DBFA),
	fHaloHigh   = Color(0xFFC3B2FF).copy(alpha = 0.65f),
	fHaloLow    = Color(0xFF9FCBFF).copy(alpha = 0.55f),
	fTileEdge   = Color(0xFF7B6CF6).copy(alpha = 0.22f),
	fBackTop    = Color(0xFFB4C2F7).copy(alpha = 0.90f),
	fBackBottom = Color(0xFF5A6FD0).copy(alpha = 0.85f),
	fGlow       = Color(0xFF7B6CF6).copy(alpha = 0.35f),
	fPlus       = Color(0xFF3B31B0)
)

private val kCardTop    = Color(0xFF9A8DFF).copy(alpha = 0.95f)					// carte de devant, haut
private val kCardMiddle = Color(0xFF6A5CF5).copy(alpha = 0.90f)					// carte de devant, milieu
private val kCardBottom = Color(0xFF3B31B0).copy(alpha = 0.92f)					// carte de devant, bas
private val kCardInk    = Color(0xFFF3F3F7)										// puce et courbe sur la carte

/*
	Logo dessiné : tuile de verre, deux cartes de comptes superposées (puce et
	courbe de hausse sur celle de devant) et un « + ». La carte de devant
	monte du bas de la tuile, qui la découpe, et le « + » apparaît, selon
	inArrival. Les proportions sont celles de l'icône (grille de 120).
	inSize    : côté de la tuile
	inArrival : arrivée de la carte de devant et du « + », de 0 (absents) à 1 (en place)
	inDark    : verre sombre (vrai) ou clair ; par défaut, celui du thème en cours
*/
@Composable
fun PatrimoineLogo(
	inSize    : Dp,
	inArrival : Float   = 1f,
	inDark    : Boolean = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
)
{
	val vColors = if (inDark) kDarkLogo else kLightLogo							// déclinaison
	Canvas(Modifier.size(inSize).semantics { contentDescription = "Logo PatrimoinePlus" }) {
		drawLogo(this, vColors, inArrival)
	}
}

/*
	Dessine le logo sur toute la zone.
	inScope   : zone de dessin (carrée)
	inColors  : déclinaison
	inArrival : arrivée de la carte de devant et du « + », de 0 à 1
*/
private fun drawLogo(
	inScope   : DrawScope,
	inColors  : LogoColors,
	inArrival : Float
) = with(inScope)
{
	val vU    = size.minDimension / 120f												// une unité de la grille
	fun p(inX: Float, inY: Float) = Offset(inX * vU, inY * vU)					// point de la grille
	fun s(inW: Float, inH: Float) = Size(inW * vU, inH * vU)						// taille dans la grille
	val vTile = Path().apply { addRoundRect(RoundRect(0f, 0f, 120f * vU, 120f * vU, CornerRadius(27f * vU))) }	// contour de la tuile
	clipPath(vTile) {
		drawRect(Brush.linearGradient(listOf(inColors.fTileTop, inColors.fTileBottom), p(0f, 0f), p(120f, 120f)))
		drawRect(Brush.radialGradient(listOf(inColors.fHaloHigh, inColors.fHaloHigh.copy(alpha = 0f)), p(98f, 17f), 90f * vU))
		drawRect(Brush.radialGradient(listOf(inColors.fHaloLow, inColors.fHaloLow.copy(alpha = 0f)), p(12f, 114f), 84f * vU))
		rotate(-12f, p(61f, 52f)) {
			drawRoundRect(Brush.verticalGradient(listOf(inColors.fBackTop, inColors.fBackBottom), 34f * vU, 70f * vU), p(34f, 34f), s(54f, 36f), CornerRadius(8f * vU))
			glassEdge(this, p(34f, 34f), s(54f, 36f), 8f * vU, 1f * vU)
		}
		translate(top = (1f - inArrival) * 96f * vU) {
			drawCircle(Brush.radialGradient(listOf(inColors.fGlow, inColors.fGlow.copy(alpha = 0f)), p(58f, 72f), 46f * vU), 46f * vU, p(58f, 72f))
			drawRoundRect(Brush.linearGradient(0f to kCardTop, 0.55f to kCardMiddle, 1f to kCardBottom, start = p(28f, 50f), end = p(44f, 90f)), p(28f, 50f), s(60f, 40f), CornerRadius(9f * vU))
			drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0f)), 50f * vU, 64f * vU), p(28f, 50f), s(60f, 14f), CornerRadius(9f * vU))
			glassEdge(this, p(28f, 50f), s(60f, 40f), 9f * vU, 1.2f * vU)
			drawRoundRect(kCardInk.copy(alpha = 0.85f), p(35f, 58f), s(11f, 8f), CornerRadius(2f * vU))
			val vCurve = Path().apply {
				moveTo(36f * vU, 82f * vU); lineTo(48f * vU, 76f * vU); lineTo(56f * vU, 79f * vU); lineTo(70f * vU, 70f * vU)
			}																		// courbe de hausse
			drawPath(vCurve, kCardInk, style = Stroke(2.6f * vU, cap = StrokeCap.Round, join = StrokeJoin.Round))
		}
		if (inArrival > 0f) scale(inArrival, p(90f, 34f)) {
			drawLine(inColors.fPlus, p(90f, 28f), p(90f, 40f), 3.6f * vU, StrokeCap.Round)
			drawLine(inColors.fPlus, p(84f, 34f), p(96f, 34f), 3.6f * vU, StrokeCap.Round)
		}
		drawRoundRect(inColors.fTileEdge, p(0.6f, 0.6f), s(118.8f, 118.8f), CornerRadius(26.4f * vU), style = Stroke(1.2f * vU))
	}
}

/*
	Liseré de verre d'un rectangle arrondi : clair en haut à gauche, presque
	effacé au milieu, de nouveau visible en bas à droite.
	inScope   : zone de dessin
	inTopLeft : coin haut gauche
	inSize    : taille
	inRadius  : rayon des coins
	inWidth   : épaisseur du trait
*/
private fun glassEdge(
	inScope   : DrawScope,
	inTopLeft : Offset,
	inSize    : Size,
	inRadius  : Float,
	inWidth   : Float
)
{
	val vEnd = Offset(inTopLeft.x + inSize.width, inTopLeft.y + inSize.height)	// coin bas droit
	inScope.drawRoundRect(
		brush        = Brush.linearGradient(0f to Color.White.copy(alpha = 0.70f), 0.5f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.30f), start = inTopLeft, end = vEnd),
		topLeft      = inTopLeft,
		size         = inSize,
		cornerRadius = CornerRadius(inRadius),
		style        = Stroke(inWidth)
	)
}
