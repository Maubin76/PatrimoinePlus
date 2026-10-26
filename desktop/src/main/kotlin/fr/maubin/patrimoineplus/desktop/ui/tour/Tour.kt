package fr.maubin.patrimoineplus.desktop.ui.tour

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.FloatingSurface

/*
	Étape d'une visite guidée : élément présenté et texte de la bulle.
	anchor : ancre de l'élément (voir tourAnchor) ; absente à l'écran : bulle au centre
	title  : titre de la bulle
	text   : explication, en une ou deux phrases
*/
data class TourStep(
	val anchor : String,
	val title  : String,
	val text   : String
)

/*
	Ancres des éléments présentés : position dans la fenêtre et demande de
	défilement pour les amener à l'écran.
*/
class TourAnchors
{
	val bounds     = mutableStateMapOf<String, Rect>()								// position de chaque ancre, dans la fenêtre
	val requesters = mutableMapOf<String, BringIntoViewRequester>()				// défilement vers chaque ancre
}

// Ancres de la fenêtre, fournies par l'appli.
val LocalTourAnchors = staticCompositionLocalOf { TourAnchors() }

/*
	Marque un élément présentable par la visite guidée : sa position est
	suivie, et la visite peut le faire défiler jusqu'à l'écran.
	inKey : ancre
*/
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tourAnchor(inKey: String): Modifier =
	composed {
		val vAnchors   = LocalTourAnchors.current										// ancres de la fenêtre
		val vRequester = remember { BringIntoViewRequester() }						// défilement vers l'élément
		vAnchors.requesters[inKey] = vRequester
		this
			.bringIntoViewRequester(vRequester)
			.onGloballyPositioned { inLayout ->
				val vTop = inLayout.positionInRoot()										// coin haut gauche
				vAnchors.bounds[inKey] = Rect(vTop, Size(inLayout.size.width.toFloat(), inLayout.size.height.toFloat()))
			}
	}

/*
	Visite guidée d'une page : la fenêtre s'assombrit sauf l'élément de
	l'étape (découpe arrondie), une bulle flottante l'explique à côté (titre,
	texte, « 2 / 5 », Précédent, Suivant ou Terminer, Passer) ; « Ne plus
	afficher les tutos » coupe toutes les visites. Les clics hors de la bulle
	sont ignorés ; Échap passe la visite de la page, les flèches changent
	d'étape.
	inSteps    : étapes
	inStep     : étape affichée (rang)
	inOnStep   : change d'étape
	inOnFinish : visite terminée ou passée (vue)
	inOnStopAll : ne plus afficher aucune visite
*/
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TourOverlay(
	inSteps     : List<TourStep>,
	inStep      : Int,
	inOnStep    : (Int) -> Unit,
	inOnFinish  : () -> Unit,
	inOnStopAll : () -> Unit
)
{
	val vStep    = inSteps.getOrNull(inStep) ?: return									// étape affichée
	val vAnchors = LocalTourAnchors.current												// ancres de la fenêtre
	val vTarget  = vAnchors.bounds[vStep.anchor]										// élément présenté ; null s'il n'est pas à l'écran
	val vFocus   = remember { FocusRequester() }										// clavier pour la visite
	val vReveal  by animateFloatAsState(1f, tween(220), label = "visite")				// apparition
	LaunchedEffect(vStep.anchor) {
		vAnchors.requesters[vStep.anchor]?.bringIntoView()
		vFocus.requestFocus()
	}
	val vLast    = inStep == inSteps.size - 1											// dernière étape
	BoxWithConstraints(
		Modifier
			.fillMaxSize()
			.pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }		// clics hors de la bulle ignorés
			.focusRequester(vFocus)
			.focusable()
			.onPreviewKeyEvent { inEvent ->
				when
				{
					inEvent.type != KeyEventType.KeyDown -> false
					inEvent.key == Key.Escape            -> { inOnFinish(); true }
					inEvent.key == Key.DirectionRight    -> { if (vLast) inOnFinish() else inOnStep(inStep + 1); true }
					inEvent.key == Key.DirectionLeft     -> { if (inStep > 0) inOnStep(inStep - 1); true }
					else                                 -> false
				}
			}
	) {
		val vDensity = LocalDensity.current												// conversion dp ↔ px
		val vPad     = with(vDensity) { 10.dp.toPx() }									// marge autour de l'élément
		Canvas(Modifier.fillMaxSize()) {
			val vPath = Path().apply {
				fillType = PathFillType.EvenOdd
				addRect(Rect(Offset.Zero, size))
				vTarget?.let { addRoundRect(RoundRect(it.inflate(vPad), CornerRadius(vPad * 2.4f))) }
			}																			// voile percé autour de l'élément
			drawPath(vPath, Color.Black.copy(alpha = 0.55f * vReveal))
		}
		val vWidth   = 380.dp																// largeur de la bulle
		val vBubbleW = with(vDensity) { vWidth.toPx() }									// en px
		val vMaxW    = with(vDensity) { maxWidth.toPx() }								// fenêtre, en px
		val vMaxH    = with(vDensity) { maxHeight.toPx() }
		val vGap     = with(vDensity) { 18.dp.toPx() }									// écart entre l'élément et la bulle
		val vPlace   = when
		{
			vTarget == null                            -> IntOffset(((vMaxW - vBubbleW) / 2).toInt(), (vMaxH * 0.35f).toInt())
			vTarget.bottom + vGap + 220f < vMaxH       -> IntOffset((vTarget.left).coerceIn(16f, vMaxW - vBubbleW - 16f).toInt(), (vTarget.bottom + vPad + vGap).toInt())
			vTarget.right + vGap + vBubbleW < vMaxW    -> IntOffset((vTarget.right + vPad + vGap).toInt(), vTarget.top.coerceIn(16f, vMaxH - 260f).toInt())
			else                                       -> IntOffset((vTarget.left).coerceIn(16f, vMaxW - vBubbleW - 16f).toInt(), (vTarget.top - vPad - vGap - 230f).coerceAtLeast(16f).toInt())
		}																				// sous l'élément, à sa droite, ou au-dessus
		Box(Modifier.offset { vPlace }.width(vWidth).semantics { contentDescription = "Tutoriel : ${vStep.title}" }) {
			FloatingSurface(RoundedCornerShape(24.dp)) {
				Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Text(vStep.title, style = MaterialTheme.typography.titleLarge)
					Text(vStep.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
						Text("${inStep + 1} / ${inSteps.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
						Spacer(Modifier.weight(1f))
						TextButton(onClick = inOnFinish) { Text("Passer") }
						if (inStep > 0) TextButton(onClick = { inOnStep(inStep - 1) }) { Text("Précédent") }
						Button(onClick = { if (vLast) inOnFinish() else inOnStep(inStep + 1) }) { Text(if (vLast) "Terminer" else "Suivant") }
					}
					TextButton(onClick = inOnStopAll, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
						Text("Ne plus afficher les tutoriels", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			}
		}
	}
}
