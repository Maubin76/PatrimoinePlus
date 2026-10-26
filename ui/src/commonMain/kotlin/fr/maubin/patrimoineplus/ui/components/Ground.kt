package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/*
	Fond de l'application (téléphone et PC), sous tout le contenu : dégradé
	pastel (nuit indigo en sombre), ou formes floues avec l'effet verre.
	inDark  : mode sombre
	inGlass : effet verre
*/
@Composable
fun AppGround(
	inDark  : Boolean,
	inGlass : Boolean
)
{
	if (inGlass) GlassBackdrop(inDark) else Box(Modifier.fillMaxSize().deskGround(inDark))
}

/*
	Fond de l'effet verre : formes floues que les panneaux translucides
	laissent deviner. Clair : dégradé pastel et grands halos (lavande, rose,
	bleu ciel, pêche). Sombre : noir profond, facettes fumées et lueurs
	violette et indigo.
	inDark : mode sombre
*/
@Composable
fun GlassBackdrop(inDark: Boolean)
{
	Box(Modifier.fillMaxSize().background(if (inDark) Color(0xFF07070B) else Color(0xFFE9E4FA))) {
		Canvas(Modifier.fillMaxSize().blur(if (inDark) 40.dp else 90.dp, BlurredEdgeTreatment.Rectangle)) {
			val vW = size.width															// largeur
			val vH = size.height															// hauteur
			val vM = maxOf(vW, vH)															// plus grande dimension
			if (inDark)
			{
				val vFacet = { inColor: Color, inPoints: List<Pair<Float, Float>> ->
					drawPath(Path().apply {
						moveTo(inPoints[0].first * vW, inPoints[0].second * vH)
						inPoints.drop(1).forEach { (inX, inY) -> lineTo(inX * vW, inY * vH) }
						close()
					}, inColor)
				}																		// facette, en fractions de l'écran
				vFacet(Color(0xFF101017), listOf(0f to 0f, 0.42f to 0f, 0.18f to 0.55f, 0f to 0.4f))
				vFacet(Color(0xFF15151D), listOf(0.42f to 0f, 0.78f to 0f, 0.5f to 0.38f, 0.18f to 0.55f))
				vFacet(Color(0xFF0D0D13), listOf(0.78f to 0f, 1f to 0f, 1f to 0.48f, 0.5f to 0.38f))
				vFacet(Color(0xFF13131A), listOf(0f to 0.4f, 0.18f to 0.55f, 0.36f to 1f, 0f to 1f))
				vFacet(Color(0xFF0B0B10), listOf(0.18f to 0.55f, 0.5f to 0.38f, 0.7f to 1f, 0.36f to 1f))
				vFacet(Color(0xFF17171F), listOf(0.5f to 0.38f, 1f to 0.48f, 1f to 1f, 0.7f to 1f))
				drawCircle(Brush.radialGradient(listOf(Color(0xFF5B4BE0).copy(alpha = 0.30f), Color.Transparent), center = Offset(0.82f * vW, 0.12f * vH), radius = 0.45f * vM), radius = 0.45f * vM, center = Offset(0.82f * vW, 0.12f * vH))
				drawCircle(Brush.radialGradient(listOf(Color(0xFF2A3A8C).copy(alpha = 0.20f), Color.Transparent), center = Offset(0.12f * vW, 0.92f * vH), radius = 0.4f * vM), radius = 0.4f * vM, center = Offset(0.12f * vW, 0.92f * vH))
			}
			else
			{
				drawRect(Brush.linearGradient(listOf(Color(0xFFE6E0FA), Color(0xFFDDE6F8), Color(0xFFE5E0F7)), start = Offset.Zero, end = Offset(vW, vH)))
				drawCircle(Color(0xFFC3B2FF).copy(alpha = 0.50f), radius = 0.26f * vM, center = Offset(0.2f * vW, 0.18f * vH))
				drawCircle(Color(0xFFFFBFD8).copy(alpha = 0.45f), radius = 0.28f * vM, center = Offset(0.06f * vW, 0.86f * vH))
				drawCircle(Color(0xFF9FCBFF).copy(alpha = 0.42f), radius = 0.27f * vM, center = Offset(0.86f * vW, 0.34f * vH))
				drawCircle(Color(0xFFFFD9B8).copy(alpha = 0.32f), radius = 0.2f * vM, center = Offset(0.6f * vW, 0.98f * vH))
			}
		}
	}
}

/*
	Fond de l'appli : dégradé pastel lavande et bleu brume, rose poudré en bas
	à gauche, halo bleu en haut à droite (sombre : nuit indigo et prune).
	inDark : mode sombre
*/
fun Modifier.deskGround(inDark: Boolean): Modifier =
	drawBehind {
		val vBase = if (inDark) listOf(Color(0xFF16162B), Color(0xFF121729), Color(0xFF181427)) else listOf(Color(0xFFE6E0FA), Color(0xFFDDE6F8), Color(0xFFE5E0F7))	// dégradé de fond
		val vLow  = if (inDark) Color(0xFF3A2142).copy(alpha = 0.55f) else Color(0xFFF7DCE9)	// halo bas gauche
		val vHigh = if (inDark) Color(0xFF223259).copy(alpha = 0.45f) else Color(0xFFD3E4FB)	// halo haut droit
		val vSpan = maxOf(size.width, size.height)										// plus grande dimension
		drawRect(Brush.linearGradient(vBase, start = Offset.Zero, end = Offset(size.width, size.height)))
		drawRect(Brush.radialGradient(listOf(vLow, vLow.copy(alpha = 0f)), center = Offset(0f, size.height), radius = vSpan * 0.62f))
		drawRect(Brush.radialGradient(listOf(vHigh, vHigh.copy(alpha = 0f)), center = Offset(size.width, 0f), radius = vSpan * 0.5f))
	}

/*
	Couleur de la barre de titre de la fenêtre (PC) : celle du haut du fond
	de l'appli, pour que la fenêtre forme un seul bloc ; le titre à l'encre
	du thème.
	inDark  : mode sombre
	inGlass : effet verre
	Retour : fond de la barre et couleur du titre
*/
fun titleBarColors(
	inDark  : Boolean,
	inGlass : Boolean
): Pair<Color, Color> =
	when
	{
		inDark && inGlass -> Color(0xFF07070B) to Color(0xFFF3F3F7)
		inDark            -> Color(0xFF16162B) to Color(0xFFF1EFF8)
		inGlass           -> Color(0xFFE9E4FA) to Color(0xFF16151F)
		else              -> Color(0xFFE6E0FA) to Color(0xFF16151F)
	}

