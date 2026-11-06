package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/*
	Fond de l'application (téléphone et PC), sous tout le contenu : un neutre
	gris-bleu, à peine plus clair en haut (ardoise en sombre).
	inDark  : mode sombre
	inGlass : sans effet (un seul style ; gardé pour les appels existants)
*/
@Composable
fun AppGround(
	inDark  : Boolean,
	inGlass : Boolean = true
)
{
	GlassBackdrop(inDark)
}

/*
	Fond de l'effet verre : formes floues que les panneaux translucides
	laissent deviner. Clair : dégradé pastel et grands halos (lavande, rose,
	bleu ciel, pêche). Sombre : noir profond, facettes fumées et lueurs
	violette et indigo. Le flou est obtenu une fois pour toutes : formes
	dessinées dans une petite image, agrandie par étapes avec lissage, puis
	gardée tant que la taille et le mode ne changent pas (un flou recalculé à
	chaque image ralentissait les animations ; il manquait sous Android 11).
	inDark : mode sombre
*/
@Composable
fun GlassBackdrop(inDark: Boolean)
{
	Box(
		Modifier
			.fillMaxSize()
			.background(if (inDark) Color(0xFF0F1318) else Color(0xFFE0E6EB))
			.drawWithCache {
				val vImage = softImage(this, (if (inDark) 70.dp else 80.dp).toPx()) { glassShapes(inDark) }	// formes floues, calculées une fois
				onDrawBehind { drawImage(vImage) }
			}
	)
}

/*
	Image floue d'un dessin, à la taille de la zone : le dessin est fait dans
	une image réduite (un pixel par rayon de flou), puis
	agrandi trois fois par deux avec lissage, ce qui adoucit les bords comme
	un flou, et enfin à la taille de la zone (simple copie ensuite).
	inScope  : zone à couvrir
	inRadius : rayon du flou, en pixels
	inDraw   : dessin, en proportions de la zone (size est celle de l'image réduite)
*/
private fun softImage(
	inScope  : CacheDrawScope,
	inRadius : Float,
	inDraw   : DrawScope.() -> Unit
): ImageBitmap
{
	val vStep  = maxOf(2f, inRadius)												// réduction : pixels de la zone par pixel de l'image
	var vW     = maxOf(2, (inScope.size.width / vStep).roundToInt())				// largeur réduite
	var vH     = maxOf(2, (inScope.size.height / vStep).roundToInt())				// hauteur réduite
	var vImage = ImageBitmap(vW, vH)													// image réduite
	CanvasDrawScope().draw(inScope, inScope.layoutDirection, Canvas(vImage), Size(vW.toFloat(), vH.toFloat())) {
		inDraw()
	}
	repeat(3) {
		val vNext = ImageBitmap(vW * 2, vH * 2)											// image agrandie
		CanvasDrawScope().draw(inScope, inScope.layoutDirection, Canvas(vNext), Size(vW * 2f, vH * 2f)) {
			drawImage(vImage, dstSize = IntSize(vW * 2, vH * 2), filterQuality = FilterQuality.High)
		}
		vImage = vNext; vW *= 2; vH *= 2
	}
	val vFullW = maxOf(1, inScope.size.width.roundToInt())								// largeur de la zone
	val vFullH = maxOf(1, inScope.size.height.roundToInt())								// hauteur de la zone
	val vFull  = ImageBitmap(vFullW, vFullH)											// image finale, à la taille de la zone
	CanvasDrawScope().draw(inScope, inScope.layoutDirection, Canvas(vFull), Size(vFullW.toFloat(), vFullH.toFloat())) {
		drawImage(vImage, dstSize = IntSize(vFullW, vFullH), filterQuality = FilterQuality.High)
	}
	return vFull
}

/*
	Fond de l'appli, avant adoucissement : un neutre gris-bleu, à peine plus
	clair en haut à gauche (simple variation de ton, aucune tache de couleur) ;
	en sombre, ardoise à peine plus claire en haut.
	inDark : mode sombre
*/
private fun DrawScope.glassShapes(inDark: Boolean)
{
	val vTones = if (inDark) listOf(Color(0xFF12161C), Color(0xFF0D1015)) else listOf(Color(0xFFE6EBF0), Color(0xFFDCE2E8))	// deux tons du même neutre
	drawRect(Brush.linearGradient(vTones, start = Offset.Zero, end = Offset(size.width, size.height)))
}

/*
	Fond de l'appli : dégradé pastel lavande et bleu brume, rose poudré en bas
	à gauche, halo bleu en haut à droite (sombre : nuit indigo et prune).
	Dessiné une fois dans une image gardée tant que la taille et le mode ne
	changent pas : trois dégradés plein écran recalculés à chaque image
	coûtaient pendant les animations.
	inDark : mode sombre
*/
fun Modifier.deskGround(inDark: Boolean): Modifier =
	drawWithCache {
		val vW     = maxOf(1, size.width.roundToInt())									// largeur
		val vH     = maxOf(1, size.height.roundToInt())									// hauteur
		val vImage = ImageBitmap(vW, vH)													// fond dessiné une fois
		CanvasDrawScope().draw(this, layoutDirection, Canvas(vImage), Size(vW.toFloat(), vH.toFloat())) { deskGradients(inDark) }
		onDrawBehind { drawImage(vImage) }
	}

/*
	Dégradés du fond de l'appli, sur toute la zone.
	inDark : mode sombre
*/
private fun DrawScope.deskGradients(inDark: Boolean)
{
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
	if (inDark) Color(0xFF12161C) to Color(0xFFEDF0F4) else Color(0xFFE6EBF0) to Color(0xFF14171C)

