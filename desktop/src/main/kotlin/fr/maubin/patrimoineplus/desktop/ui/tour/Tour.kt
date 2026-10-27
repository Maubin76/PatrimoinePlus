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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.CompositionLocalProvider
import fr.maubin.patrimoineplus.ui.components.LocalPointer
import fr.maubin.patrimoineplus.ui.components.AssistantAvatar
import fr.maubin.patrimoineplus.ui.components.AvatarReaction
import fr.maubin.patrimoineplus.ui.assistant.Mood

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
	Visite guidée d'une page, animée pour rester fluide : la fenêtre
	s'assombrit sauf l'élément de l'étape ; la découpe glisse et se
	redimensionne d'un élément à l'autre (elle se referme quand l'étape n'a
	pas d'élément à l'écran), un halo respire autour. Une bulle flottante
	l'explique à côté : elle suit l'élément avec un léger ressort, son contenu
	glisse dans le sens de la navigation ; points de progression (l'étape en
	cours en pilule), Précédent, Suivant ou Terminer, Passer ; « Ne plus
	afficher les tutoriels » coupe toutes les visites. Les clics hors de la
	bulle sont ignorés ; Échap passe la visite de la page, les flèches
	changent d'étape. L'apparition et la disparition (fondu) sont faites par
	l'appelant.
	inSteps     : étapes
	inStep      : étape affichée (rang)
	inOnStep    : change d'étape
	inOnFinish  : visite terminée ou passée (vue)
	inOnStopAll : ne plus afficher aucune visite
	inGuide     : assistant en guide, à gauche du texte (regarde l'élément présenté, hoche la tête à chaque étape) ; vrai : en mode calme ; null : sans assistant
*/
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TourOverlay(
	inSteps     : List<TourStep>,
	inStep      : Int,
	inOnStep    : (Int) -> Unit,
	inOnFinish  : () -> Unit,
	inOnStopAll : () -> Unit,
	inGuide     : Boolean? = null
)
{
	val vStep    = inSteps.getOrNull(inStep) ?: return									// étape affichée
	val vAnchors = LocalTourAnchors.current												// ancres de la fenêtre
	val vTarget  = vAnchors.bounds[vStep.anchor]										// élément présenté ; null s'il n'est pas à l'écran
	val vFocus   = remember { FocusRequester() }										// clavier pour la visite
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
		val vMaxW    = with(vDensity) { maxWidth.toPx() }								// fenêtre, en px
		val vMaxH    = with(vDensity) { maxHeight.toPx() }
		val vClosed  = Rect(Offset(vMaxW / 2f, vMaxH * 0.4f), Size.Zero)				// découpe refermée (étape sans élément)
		val vGoal    = vTarget?.inflate(vPad) ?: vClosed									// découpe visée
		val vHoleA   = remember { Animatable(vGoal, Rect.VectorConverter) }				// découpe, en mouvement
		var vPlaced  by remember { mutableStateOf(vTarget != null) }						// découpe déjà posée sur un élément
		LaunchedEffect(vGoal) {
			if (!vPlaced && vTarget != null) { vHoleA.snapTo(vGoal); vPlaced = true }		// premier élément : posée d'emblée
			else vHoleA.animateTo(vGoal, tween(kMoveMillis, easing = kTourEase))			// ensuite : glisse d'un élément à l'autre
		}
		val vHole    = vHoleA.value															// découpe affichée
		val vPulse   = rememberInfiniteTransition(label = "halo")						// respiration du halo
		val vGlow    by vPulse.animateFloat(0.25f, 0.65f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "halo")
		val vRing    = with(vDensity) { 2.dp.toPx() }									// épaisseur du halo
		Canvas(Modifier.fillMaxSize()) {
			val vRadius = CornerRadius(minOf(vPad * 2.4f, vHole.minDimension / 2f))		// arrondi de la découpe
			val vPath = Path().apply {
				fillType = PathFillType.EvenOdd
				addRect(Rect(Offset.Zero, size))
				if (!vHole.isEmpty) addRoundRect(RoundRect(vHole, vRadius))
			}																			// voile percé autour de l'élément
			drawPath(vPath, Color.Black.copy(alpha = 0.55f))
			if (vHole.width > 4f) drawRoundRect(Color.White.copy(alpha = vGlow), vHole.topLeft, vHole.size, vRadius, style = Stroke(vRing))	// halo autour de l'élément
		}
		val vWidth   = 380.dp																// largeur de la bulle
		val vBubbleW = with(vDensity) { vWidth.toPx() }									// en px
		val vGap     = with(vDensity) { 18.dp.toPx() }									// écart entre l'élément et la bulle
		val vPlace   = when
		{
			vTarget == null                            -> IntOffset(((vMaxW - vBubbleW) / 2).toInt(), (vMaxH * 0.35f).toInt())
			vTarget.bottom + vGap + 220f < vMaxH       -> IntOffset((vTarget.left).coerceIn(16f, vMaxW - vBubbleW - 16f).toInt(), (vTarget.bottom + vPad + vGap).toInt())
			vTarget.right + vGap + vBubbleW < vMaxW    -> IntOffset((vTarget.right + vPad + vGap).toInt(), vTarget.top.coerceIn(16f, vMaxH - 260f).toInt())
			else                                       -> IntOffset((vTarget.left).coerceIn(16f, vMaxW - vBubbleW - 16f).toInt(), (vTarget.top - vPad - vGap - 230f).coerceAtLeast(16f).toInt())
		}																				// sous l'élément, à sa droite, ou au-dessus
		val vBubbleA = remember { Animatable(vPlace, IntOffset.VectorConverter) }			// bulle, en mouvement
		var vDocked  by remember { mutableStateOf(vTarget != null) }						// bulle déjà posée près d'un élément
		LaunchedEffect(vPlace) {
			if (!vDocked && vTarget != null) { vBubbleA.snapTo(vPlace); vDocked = true }	// premier élément : posée d'emblée
			else vBubbleA.animateTo(vPlace, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))	// ensuite : suit l'élément avec un ressort
		}
		val vOffset  = vBubbleA.value														// bulle affichée
		var vShown   by remember { mutableStateOf(false) }								// bulle apparue
		LaunchedEffect(Unit) { vShown = true }
		val vScale   by animateFloatAsState(if (vShown) 1f else 0.92f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "apparition")	// la bulle grandit à l'ouverture
		Box(
			Modifier
				.offset { vOffset }
				.width(vWidth)
				.graphicsLayer { scaleX = vScale; scaleY = vScale; transformOrigin = TransformOrigin(0f, 0f) }
				.semantics { contentDescription = "Tutoriel : ${vStep.title}" }
		) {
			FloatingSurface(RoundedCornerShape(24.dp)) {
				Column(Modifier.padding(22.dp).animateContentSize(tween(kMoveMillis, easing = kTourEase)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
						if (inGuide != null)
						{
							CompositionLocalProvider(LocalPointer provides { vTarget?.center }) {			// l'assistant regarde l'élément présenté
								AssistantAvatar(48.dp, Mood.HAPPY, inReaction = AvatarReaction.NOD to inStep.toLong(), inCalm = inGuide, inLabel = "Assistant, guide du tutoriel")
							}
						}
						AnimatedContent(
							targetState    = inStep,
							transitionSpec = {
								val vForward = targetState > initialState						// sens de la navigation
								(slideInHorizontally(tween(kMoveMillis, easing = kTourEase)) { if (vForward) it / 6 else -it / 6 } + fadeIn(tween(kMoveMillis / 2, delayMillis = kMoveMillis / 4))) togetherWith
									(slideOutHorizontally(tween(kMoveMillis, easing = kTourEase)) { if (vForward) -it / 6 else it / 6 } + fadeOut(tween(kMoveMillis / 3)))
							},
							label          = "étape"
						) { inShown ->
							val vText = inSteps[inShown]												// étape montrée
							Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
								Text(vText.title, style = MaterialTheme.typography.titleLarge)
								Text(vText.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
						}
					}
					Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
						StepDots(inSteps.size, inStep)
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

/*
	Points de progression : un point par étape, l'étape en cours étirée en
	pilule à l'encre de la coque (en mouvement d'une étape à l'autre) ; lu
	« Étape 2 sur 5 ».
	inCount : nombre d'étapes
	inStep  : étape en cours
*/
@Composable
private fun StepDots(
	inCount : Int,
	inStep  : Int
)
{
	val vColors = MaterialTheme.colorScheme												// palette courante
	Row(Modifier.semantics { contentDescription = "Étape ${inStep + 1} sur $inCount" }, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
		repeat(inCount) { inIndex ->
			val vActive = inIndex == inStep												// étape en cours
			val vWide   by animateDpAsState(if (vActive) 18.dp else 6.dp, tween(kMoveMillis, easing = kTourEase), label = "point")	// largeur du point
			val vTint   by animateColorAsState(if (vActive) vColors.primary else vColors.outline.copy(alpha = 0.5f), tween(kMoveMillis), label = "teinte")
			Box(Modifier.width(vWide).height(6.dp).clip(RoundedCornerShape(3.dp)).background(vTint))
		}
	}
}

private const val kMoveMillis = 380														// passage d'une étape à l'autre
private val kTourEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)							// courbe « accentuée » de Material
