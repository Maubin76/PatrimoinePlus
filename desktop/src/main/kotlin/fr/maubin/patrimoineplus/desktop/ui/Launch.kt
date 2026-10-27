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

private const val kLaunchDelay  = 150												// fond seul, avant les éléments
private const val kLaunchMillis = 750												// arrivée de tous les éléments
private const val kStepLength   = 0.55f												// part de l'arrivée occupée par un élément
private val kLaunchEase         = CubicBezierEasing(0.2f, 0f, 0f, 1f)				// courbe « accentuée » de Material

// Avancement de l'animation du lancement (0 : fond seul, 1 : tout est en place), lu au dessin.
val LocalLaunch = staticCompositionLocalOf<() -> Float> { { 1f } }

/*
	Avancement de l'animation du lancement, lancé une fois à la première
	composition : d'abord le fond seul, puis l'arrivée des éléments.
	Retour : lecture de l'avancement, à faire au dessin (pas de recomposition)
*/
@Composable
fun rememberLaunch(): () -> Float
{
	val vProgress = remember { Animatable(0f) }											// avancement
	LaunchedEffect(Unit) { vProgress.animateTo(1f, tween(kLaunchMillis, delayMillis = kLaunchDelay, easing = LinearEasing)) }
	return remember { { vProgress.value } }
}

/*
	Arrivée d'un élément au lancement : fondu, glissement et léger zoom, à son
	tour (les éléments arrivent l'un après l'autre).
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
		alpha        = vT
		translationX = (1f - vT) * inDx.toPx()
		translationY = (1f - vT) * inDy.toPx()
		scaleX       = inScale + (1f - inScale) * vT
		scaleY       = inScale + (1f - inScale) * vT
	}
}
