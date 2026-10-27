package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val kLaunchDelay  = 150												// fond seul, avant les éléments
private const val kLaunchMillis = 750												// arrivée de tous les éléments
private const val kStepLength   = 0.55f												// part de l'arrivée occupée par un élément
private val kLaunchEase         = CubicBezierEasing(0.2f, 0f, 0f, 1f)				// courbe « accentuée » de Material
private const val kSmoothFrames = 3													// images rapides (< 50 ms) d'affilée avant de lancer l'animation
private const val kWaitMillis   = 4_000L											// attente maximale de la fenêtre
private const val kWarmAlpha    = 0.003f											// opacité invisible des éléments pas encore arrivés

// Avancement de l'animation du lancement (0 : fond seul, 1 : tout est en place), lu au dessin.
val LocalLaunch = staticCompositionLocalOf<() -> Float> { { 1f } }

/*
	Avancement de l'animation du lancement, lancé une fois : d'abord le fond
	seul, puis l'arrivée des éléments. Dans la vraie fenêtre, l'animation
	attend que la fenêtre soit à l'écran et que les premières images (les plus
	lentes : démarrage, agrandissement, premier dessin) soient passées ; sans
	cela, elle était finie avant d'être vue.
	inShown : vrai quand la fenêtre est à l'écran ; null : départ immédiat (rendu sans fenêtre, tests)
	Retour  : lecture de l'avancement, à faire au dessin (pas de recomposition)
*/
@Composable
fun rememberLaunch(inShown: (() -> Boolean)? = null): () -> Float
{
	val vProgress = remember { Animatable(0f) }											// avancement
	LaunchedEffect(Unit) {
		if (inShown != null)
		{
			withTimeoutOrNull(kWaitMillis) {
				while (!inShown()) delay(16)
				var vLast = withFrameNanos { it }												// image précédente
				var vSmooth = 0																// images rapides d'affilée
				while (vSmooth < kSmoothFrames)
				{
					val vNow = withFrameNanos { it }											// image suivante
					vSmooth = if (vNow - vLast < 50_000_000L) vSmooth + 1 else 0
					vLast = vNow
				}
			}
		}
		vProgress.animateTo(1f, tween(kLaunchMillis, delayMillis = kLaunchDelay, easing = LinearEasing))
	}
	return remember { { vProgress.value } }
}

/*
	Arrivée d'un élément au lancement : fondu, glissement et léger zoom, à son
	tour (les éléments arrivent l'un après l'autre). Pour la fluidité :
	l'élément est déjà dessiné, à une opacité invisible, pendant la phase du
	fond seul : son premier dessin, le plus coûteux, n'arrive pas en pleine
	animation.
	inStart : début de son arrivée, en part de l'animation (0 à 1 − kStepLength)
	inDx    : décalage horizontal de départ
	inDy    : décalage vertical de départ
	inScale : échelle de départ
*/
@Composable
fun Modifier.launchReveal(
	inStart : Float,
	inDx    : Dp    = 0.dp,
	inDy    : Dp    = 0.dp,
	inScale : Float = 1f
): Modifier
{
	val vLaunch = LocalLaunch.current													// avancement du lancement
	return graphicsLayer {
		val vT = kLaunchEase.transform(((vLaunch() - inStart) / kStepLength).coerceIn(0f, 1f))	// avancement de cet élément
		alpha               = maxOf(vT, kWarmAlpha)													// jamais tout à fait 0 : dessiné (et mis en cache) dès la phase du fond seul
		translationX = (1f - vT) * inDx.toPx()
		translationY = (1f - vT) * inDy.toPx()
		scaleX       = inScale + (1f - inScale) * vT
		scaleY       = inScale + (1f - inScale) * vT
	}
}
