package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.clearAndSetSemantics

/*
	Mémoire de la pile : écran affiché, écran précédent, écrans en vie.
	fCurrent  : écran affiché
	fPrevious : écran quitté en dernier ; null au départ
	fAlive    : écrans composés, dans l'ordre d'arrivée
*/
private class StackMemory<T>(
	var fCurrent  : T,
	var fPrevious : T? = null,
	val fAlive    : MutableList<T> = mutableListOf(fCurrent)
)

/*
	Pile d'écrans : les écrans gardés restent composés une fois quittés
	(leurs calculs et leur état aussi), mais ne sont ni mesurés ni dessinés
	tant qu'ils sont cachés, ni lus par les lecteurs d'écran ; y revenir ne
	coûte qu'une mise en page, au lieu
	de tout recomposer (saccade au changement d'écran). Les autres écrans
	(compte, relevé) sont oubliés dès qu'ils ne sont plus ni affichés ni en
	train de partir. Transition : l'écran qui part s'efface vite en glissant
	un peu, celui qui arrive apparaît en glissant ; animation lue à la mise
	en page, sans recomposer les écrans à chaque image.
	inKey     : écran affiché
	inKeep    : vrai si un écran doit rester en vie une fois quitté
	inShift   : sens du glissement d'un écran vers un autre (1 : vers la droite, -1 : vers la gauche, 0 : fondu seul)
	inMillis  : durée de la transition
	inEasing  : courbe de la transition
	inContent : contenu d'un écran
*/
@Composable
fun <T> ScreenStack(
	inKey     : T,
	inKeep    : (T) -> Boolean,
	inShift   : (T, T) -> Int,
	inMillis  : Int,
	inEasing  : Easing,
	inContent : @Composable (T) -> Unit
)
{
	val vMemory = remember { StackMemory(inKey) }										// pile
	if (vMemory.fCurrent != inKey)
	{
		vMemory.fPrevious = vMemory.fCurrent
		vMemory.fCurrent  = inKey
		vMemory.fAlive.removeAll { it != inKey && it != vMemory.fPrevious && !inKeep(it) }
		if (inKey !in vMemory.fAlive) vMemory.fAlive.add(inKey)
	}
	val vShift = vMemory.fPrevious?.let { inShift(it, inKey) } ?: 0					// sens de la dernière transition
	Box(Modifier.fillMaxSize()) {
		vMemory.fAlive.toList().forEach { inScreen ->
			key(inScreen) {
				val vShown = inScreen == inKey														// écran affiché
				val vAlpha = remember { Animatable(if (vShown) 1f else 0f) }						// opacité
				val vSlide = remember { Animatable(0f) }											// décalage, en part de la largeur
				val vGone  by remember(vShown) { derivedStateOf { !vShown && vAlpha.value == 0f } }	// caché, transition finie (bascule deux fois par transition, pas à chaque image)
				LaunchedEffect(vShown) {
					if (vShown)
					{
						if (vAlpha.value < 1f) vSlide.snapTo(vShift / 12f)
						launch { vSlide.animateTo(0f, tween(inMillis, easing = inEasing)) }
						vAlpha.animateTo(1f, tween(inMillis, delayMillis = if (vAlpha.value > 0f) 0 else inMillis / 4, easing = inEasing))
					}
					else
					{
						launch { vSlide.animateTo(-vShift / 24f, tween(inMillis, easing = inEasing)) }
						vAlpha.animateTo(0f, tween(inMillis / 2))
					}
				}
				Box(
					Modifier
						.zIndex(if (vShown) 1f else 0f)
						.then(if (vGone) Modifier.clearAndSetSemantics {} else Modifier)				// caché : absent pour les lecteurs d'écran
						.layout { inMeasurable, inConstraints ->
							if (!vShown && vAlpha.value == 0f)
							{
								layout(0, 0) {}																// caché : ni mesuré ni dessiné
							}
							else
							{
								val vPlaced = inMeasurable.measure(inConstraints)						// écran mesuré
								layout(vPlaced.width, vPlaced.height) {
									vPlaced.placeWithLayer(0, 0) {
										alpha        = vAlpha.value
										translationX = vSlide.value * vPlaced.width
									}
								}
							}
						}
				) {
					inContent(inScreen)
				}
			}
		}
	}
}
