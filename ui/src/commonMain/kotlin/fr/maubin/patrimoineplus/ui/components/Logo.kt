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
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
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

private val kIntroEase  = CubicBezierEasing(0.2f, 0f, 0f, 1f)						// courbe des étapes de la construction
private val kTraceColor = Color(0xFF7B6CF6).copy(alpha = 0.9f)					// trait qui trace le cadre

/*
	Logo dessiné : tuile de verre, deux cartes de comptes superposées (puce et
	courbe de hausse sur celle de devant) et un « + ». Les proportions sont
	celles de l'icône (grille de 120). Construction selon inIntro (écran de
	lancement du téléphone) : d'abord les deux cartes l'une sur l'autre, seules
	(on n'en voit qu'une) ; celle de derrière glisse en haut à droite en
	s'inclinant ; la puce, la courbe et le « + » apparaissent ; le cadre se
	trace dans le sens des aiguilles d'une montre, puis la tuile se remplit.
	inSize  : côté de la tuile
	inIntro : avancement de la construction, de 0 (une carte seule) à 1 (logo complet)
	inDark  : verre sombre (vrai) ou clair ; par défaut, celui du thème en cours
*/
@Composable
fun PatrimoineLogo(
	inSize  : Dp,
	inIntro : Float   = 1f,
	inDark  : Boolean = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
)
{
	val vColors = if (inDark) kDarkLogo else kLightLogo							// déclinaison
	Canvas(Modifier.size(inSize).semantics { contentDescription = "Logo PatrimoinePlus" }) {
		drawLogo(this, vColors, inIntro)
	}
}

/*
	Avancement d'une étape de la construction, de 0 à 1, adouci.
	inIntro : avancement de la construction
	inFrom  : début de l'étape
	inTo    : fin de l'étape
*/
private fun step(
	inIntro : Float,
	inFrom  : Float,
	inTo    : Float
): Float = kIntroEase.transform(((inIntro - inFrom) / (inTo - inFrom)).coerceIn(0f, 1f))

/*
	Contour de la tuile, du milieu du haut, dans le sens des aiguilles d'une
	montre (sert à le tracer petit à petit).
	inU : taille d'une unité de la grille
*/
private fun outlinePath(inU: Float): Path
{
	val vIn = 0.6f * inU																// retrait du trait
	val vR  = 26.4f * inU																// rayon des coins
	val vE  = 120f * inU - vIn															// bord bas droit
	return Path().apply {
		moveTo(60f * inU, vIn)
		lineTo(vE - vR, vIn)
		arcTo(Rect(vE - 2 * vR, vIn, vE, vIn + 2 * vR), -90f, 90f, false)
		lineTo(vE, vE - vR)
		arcTo(Rect(vE - 2 * vR, vE - 2 * vR, vE, vE), 0f, 90f, false)
		lineTo(vIn + vR, vE)
		arcTo(Rect(vIn, vE - 2 * vR, vIn + 2 * vR, vE), 90f, 90f, false)
		lineTo(vIn, vIn + vR)
		arcTo(Rect(vIn, vIn, vIn + 2 * vR, vIn + 2 * vR), 180f, 90f, false)
		close()
	}
}

/*
	Dessine le logo sur toute la zone.
	inScope  : zone de dessin (carrée)
	inColors : déclinaison
	inIntro  : avancement de la construction, de 0 à 1
*/
private fun drawLogo(
	inScope  : DrawScope,
	inColors : LogoColors,
	inIntro  : Float
) = with(inScope)
{
	val vU       = size.minDimension / 120f											// une unité de la grille
	fun p(inX: Float, inY: Float) = Offset(inX * vU, inY * vU)					// point de la grille
	fun s(inW: Float, inH: Float) = Size(inW * vU, inH * vU)						// taille dans la grille
	val vCards   = step(inIntro, 0.00f, 0.38f)										// glissement de la carte de derrière
	val vMarks   = step(inIntro, 0.30f, 0.55f)										// puce, courbe et « + »
	val vOutline = step(inIntro, 0.45f, 0.85f)										// tracé du cadre
	val vFill    = step(inIntro, 0.72f, 1.00f)										// remplissage de la tuile
	val vTile    = Path().apply { addRoundRect(RoundRect(0f, 0f, 120f * vU, 120f * vU, CornerRadius(27f * vU))) }	// contour de la tuile
	clipPath(vTile) {
		if (vFill > 0f)
		{
			drawRect(Brush.linearGradient(listOf(inColors.fTileTop, inColors.fTileBottom), p(0f, 0f), p(120f, 120f)), alpha = vFill)
			drawRect(Brush.radialGradient(listOf(inColors.fHaloHigh, inColors.fHaloHigh.copy(alpha = 0f)), p(98f, 17f), 90f * vU), alpha = vFill)
			drawRect(Brush.radialGradient(listOf(inColors.fHaloLow, inColors.fHaloLow.copy(alpha = 0f)), p(12f, 114f), 84f * vU), alpha = vFill)
			drawCircle(Brush.radialGradient(listOf(inColors.fGlow, inColors.fGlow.copy(alpha = 0f)), p(58f, 72f), 46f * vU), 46f * vU, p(58f, 72f), alpha = vFill)
		}
		val vX = lerp(28f, 34f, vCards)														// carte de derrière : de sous celle de devant à sa place
		val vY = lerp(50f, 34f, vCards)
		val vW = lerp(60f, 54f, vCards)
		val vH = lerp(40f, 36f, vCards)
		val vR = lerp(9f, 8f, vCards)
		val vBack = (vCards * 4f).coerceAtMost(1f)											// carte de derrière : invisible sous l'autre au départ, apparaît en glissant
		if (vBack > 0f) rotate(-12f * vCards, p(vX + vW / 2f, vY + vH / 2f)) {
			drawRoundRect(Brush.verticalGradient(listOf(inColors.fBackTop, inColors.fBackBottom), vY * vU, (vY + vH) * vU), p(vX, vY), s(vW, vH), CornerRadius(vR * vU), alpha = vBack)
			glassEdge(this, p(vX, vY), s(vW, vH), vR * vU, 1f * vU, vBack)
		}
		drawRoundRect(Brush.linearGradient(0f to kCardTop, 0.55f to kCardMiddle, 1f to kCardBottom, start = p(28f, 50f), end = p(44f, 90f)), p(28f, 50f), s(60f, 40f), CornerRadius(9f * vU))
		drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0f)), 50f * vU, 64f * vU), p(28f, 50f), s(60f, 14f), CornerRadius(9f * vU))
		glassEdge(this, p(28f, 50f), s(60f, 40f), 9f * vU, 1.2f * vU)
		if (vMarks > 0f)
		{
			drawRoundRect(kCardInk.copy(alpha = 0.85f), p(35f, 58f), s(11f, 8f), CornerRadius(2f * vU), alpha = vMarks)
			val vCurve = Path().apply {
				moveTo(36f * vU, 82f * vU); lineTo(48f * vU, 76f * vU); lineTo(56f * vU, 79f * vU); lineTo(70f * vU, 70f * vU)
			}																		// courbe de hausse
			drawPath(vCurve, kCardInk, alpha = vMarks, style = Stroke(2.6f * vU, cap = StrokeCap.Round, join = StrokeJoin.Round))
			scale(vMarks, p(90f, 34f)) {
				drawLine(inColors.fPlus, p(90f, 28f), p(90f, 40f), 3.6f * vU, StrokeCap.Round, alpha = vMarks)
				drawLine(inColors.fPlus, p(84f, 34f), p(96f, 34f), 3.6f * vU, StrokeCap.Round, alpha = vMarks)
			}
		}
		if (vOutline > 0f)
		{
			val vEdge = lerp(kTraceColor, inColors.fTileEdge, vFill)							// trait du tracé, qui devient le liseré de la tuile
			if (vOutline >= 1f)
			{
				drawRoundRect(vEdge, p(0.6f, 0.6f), s(118.8f, 118.8f), CornerRadius(26.4f * vU), style = Stroke(lerp(1.6f, 1.2f, vFill) * vU))
			}
			else
			{
				val vMeasure = PathMeasure().apply { setPath(outlinePath(vU), false) }		// longueur du contour
				val vPart    = Path()																// partie déjà tracée
				vMeasure.getSegment(0f, vMeasure.length * vOutline, vPart, true)
				drawPath(vPart, vEdge, style = Stroke(1.6f * vU, cap = StrokeCap.Round))
			}
		}
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
	inAlpha   : opacité
*/
private fun glassEdge(
	inScope   : DrawScope,
	inTopLeft : Offset,
	inSize    : Size,
	inRadius  : Float,
	inWidth   : Float,
	inAlpha   : Float = 1f
)
{
	val vEnd = Offset(inTopLeft.x + inSize.width, inTopLeft.y + inSize.height)	// coin bas droit
	inScope.drawRoundRect(
		brush        = Brush.linearGradient(0f to Color.White.copy(alpha = 0.70f), 0.5f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.30f), start = inTopLeft, end = vEnd),
		topLeft      = inTopLeft,
		size         = inSize,
		cornerRadius = CornerRadius(inRadius),
		alpha        = inAlpha,
		style        = Stroke(inWidth)
	)
}
