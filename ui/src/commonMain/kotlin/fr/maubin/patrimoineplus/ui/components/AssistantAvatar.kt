package fr.maubin.patrimoineplus.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import fr.maubin.patrimoineplus.ui.assistant.Mood
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val kBodyTop    = Color(0xFF9A8DFF)											// carte, haut (comme le logo)
private val kBodyMiddle = Color(0xFF6A5CF5)											// carte, milieu
private val kBodyBottom = Color(0xFF3B31B0)											// carte, bas
private val kEyeWhite   = Color(0xFFF7F5FE)											// blanc des yeux
private val kPupil      = Color(0xFF1E1A4D)											// pupilles, bouche, paupières (indigo profond)
private val kCheek      = Color(0xFFFF8FA8).copy(alpha = 0.55f)						// joues roses (content)

// Position du pointeur dans la fenêtre (racine de l'appli), lue au dessin ; null hors de la fenêtre.
val LocalPointer = staticCompositionLocalOf<() -> Offset?> { { null } }

/*
	Réaction ponctuelle de l'assistant à un événement.
*/
enum class AvatarReaction
{
	NOD,																			// hochement (synchronisation réussie)
	STARTLE,																		// sursaut, yeux grands ouverts (erreur, conflit, à enregistrer)
	JOY,																			// petit saut, yeux plissés (objectif atteint, budget tenu)
	LOOK_UP																			// regarde en haut (nouvelle version)
}

/*
	Assistant de l'appli : la carte de verre du logo, vivante. Yeux qui
	suivent le pointeur (LocalPointer), clignements, respiration et coups
	d'œil ailleurs au repos ; s'écrase puis rebondit au clic, étourdi après
	plusieurs clics rapides ; bouche et sourcils selon l'humeur ; yeux fermés
	en mode discret ; réactions ponctuelles (hochement, sursaut, joie, regard
	vers le haut). Tout est animé au dessin, sans recomposer à chaque image.
	inSize       : largeur de l'assistant (la carte fait 2/3 de haut)
	inMood       : humeur
	inEyesClosed : yeux fermés (montants masqués)
	inReaction   : dernière réaction demandée, avec un numéro qui change à chaque demande ; null sans
	inCalm       : sans réactions ni jeu au clic (yeux et clignements seulement)
	inLabel      : nom pour les lecteurs d'écran ; null : décoratif (le texte voisin dit tout)
	inOnClick    : clic (après le rebond) ; null : pas cliquable
	inModifier   : modificateur
*/
@Composable
fun AssistantAvatar(
	inSize       : Dp,
	inMood       : Mood,
	inEyesClosed : Boolean                     = false,
	inReaction   : Pair<AvatarReaction, Long>? = null,
	inCalm       : Boolean                     = false,
	inLabel      : String?                     = "Assistant",
	inOnClick    : (() -> Unit)?               = null,
	inModifier   : Modifier                    = Modifier
)
{
	val vPointer = LocalPointer.current												// pointeur de la fenêtre
	val vScope   = rememberCoroutineScope()											// animations du clic
	val vSquash  = remember { Animatable(0f) }										// écrasement (0 au repos, 1 aplati)
	val vHop     = remember { Animatable(0f) }										// saut, en part de la hauteur
	val vBlink   = remember { Animatable(0f) }										// paupières (0 ouvertes, 1 fermées)
	val vDizzy   = remember { Animatable(0f) }										// étourdissement (0 à 1)
	val vWide    = remember { Animatable(0f) }										// yeux écarquillés (sursaut)
	val vSquint  = remember { Animatable(0f) }										// yeux plissés de joie
	val vGlance  = remember { Animatable(Offset.Zero, Offset.VectorConverter) }		// regard ailleurs, au repos
	val vUp      = remember { Animatable(0f) }										// regard vers le haut
	var vCenter  by remember { mutableStateOf<Offset?>(null) }						// centre de l'assistant dans la fenêtre
	var vClicks  by remember { mutableStateOf(emptyList<Long>()) }					// instants des derniers clics
	val vBreath  by rememberInfiniteTransition(label = "souffle").animateFloat(0f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart), label = "souffle")	// respiration
	val vSpin    by rememberInfiniteTransition(label = "tournis").animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "tournis")	// pupilles qui tournent (étourdi)

	LaunchedEffect(Unit) {
		while (true)
		{
			delay(2_400L + Random.nextLong(3_600L))
			vBlink.animateTo(1f, tween(70)); vBlink.animateTo(0f, tween(110))
			if (Random.nextInt(4) == 0) { delay(160); vBlink.animateTo(1f, tween(60)); vBlink.animateTo(0f, tween(100)) }	// double clignement, parfois
		}
	}
	LaunchedEffect(Unit) {
		while (true)
		{
			delay(5_000L + Random.nextLong(6_000L))
			val vAngle = Random.nextFloat() * 2f * PI.toFloat()								// direction du coup d'œil
			vGlance.animateTo(Offset(cos(vAngle), sin(vAngle) * 0.6f), tween(260, easing = FastOutSlowInEasing))
			delay(900)
			vGlance.animateTo(Offset.Zero, tween(320, easing = FastOutSlowInEasing))
		}
	}
	LaunchedEffect(inReaction?.second) {
		val vKind = inReaction?.first ?: return@LaunchedEffect								// réaction demandée
		if (inCalm) return@LaunchedEffect
		when (vKind)
		{
			AvatarReaction.NOD     -> repeat(2) { vHop.animateTo(-0.06f, tween(120)); vHop.animateTo(0f, tween(140)) }
			AvatarReaction.STARTLE ->
			{
				launch { vWide.animateTo(1f, tween(90)); delay(900); vWide.animateTo(0f, tween(300)) }
				vHop.animateTo(0.18f, tween(110, easing = FastOutSlowInEasing))
				vHop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
			}
			AvatarReaction.JOY     ->
			{
				launch { vSquint.animateTo(1f, tween(120)); delay(1_200); vSquint.animateTo(0f, tween(300)) }
				repeat(2) { vHop.animateTo(0.14f, tween(150, easing = FastOutSlowInEasing)); vHop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
			}
			AvatarReaction.LOOK_UP -> { vUp.animateTo(1f, tween(260)); delay(1_400); vUp.animateTo(0f, tween(320)) }
		}
	}

	val vClick = inOnClick?.let { inAction ->
		{
			val vNow = System.currentTimeMillis()											// instant du clic
			vClicks = (vClicks + vNow).filter { vNow - it < 1_500L }
			if (!inCalm)
			{
				vScope.launch {
					vSquash.animateTo(1f, tween(80))
					vSquash.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium))
				}
				if (vClicks.size >= 4)
				{
					vClicks = emptyList()
					vScope.launch { vDizzy.animateTo(1f, tween(200)); delay(2_200); vDizzy.animateTo(0f, tween(400)) }
				}
			}
			inAction()
		}
	}

	Canvas(
		inModifier
			.size(inSize)
			.onGloballyPositioned { inLayout -> vCenter = inLayout.boundsInRoot().center }
			.then(if (inLabel != null) Modifier.semantics { contentDescription = inLabel } else Modifier)
			.then(if (vClick != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = vClick) else Modifier)
	) {
		val vLook = lookDirection(vPointer(), vCenter, size.width) + vGlance.value * 0.8f + Offset(0f, -vUp.value)	// regard : pointeur, coup d'œil, haut
		drawAssistant(
			inScope  = this,
			inMood   = inMood,
			inLook   = vLook,
			inBlink  = if (inEyesClosed) 1f else vBlink.value,
			inClosed = inEyesClosed,
			inBreath = if (inCalm) 0f else sin(vBreath * 2f * PI.toFloat()),
			inSquash = vSquash.value,
			inHop    = vHop.value,
			inDizzy  = vDizzy.value,
			inSpin   = vSpin,
			inWide   = vWide.value,
			inSquint = vSquint.value
		)
	}
}

/*
	Direction du regard vers le pointeur, de longueur au plus 1 (atteinte à
	une largeur d'assistant de distance) ; nulle sans pointeur.
	inPointer : pointeur dans la fenêtre ; null sans
	inCenter  : centre de l'assistant dans la fenêtre ; null avant la mise en page
	inWidth   : largeur de l'assistant, en pixels
*/
private fun lookDirection(
	inPointer : Offset?,
	inCenter  : Offset?,
	inWidth   : Float
): Offset
{
	if (inPointer == null || inCenter == null) return Offset.Zero
	val vDelta = inPointer - inCenter																// vers le pointeur
	val vLength = hypot(vDelta.x, vDelta.y)															// distance
	if (vLength < 1f) return Offset.Zero
	val vReach = min(1f, vLength / inWidth)															// plus loin : regard plus marqué
	return Offset(vDelta.x / vLength * vReach, vDelta.y / vLength * vReach)
}

/*
	Dessine l'assistant dans sa zone (grille de 120 de large).
	inScope  : zone de dessin
	inMood   : humeur
	inLook   : direction du regard (longueur ≤ 1 environ)
	inBlink  : paupières, de 0 (ouvertes) à 1 (fermées)
	inClosed : yeux fermés pour de bon (mode discret) : traits paisibles
	inBreath : respiration, de −1 à 1
	inSquash : écrasement du clic, de 0 à 1
	inHop    : saut (positif vers le haut), en part de la hauteur
	inDizzy  : étourdissement, de 0 à 1
	inSpin   : tour des pupilles étourdies, de 0 à 1
	inWide   : yeux écarquillés, de 0 à 1
	inSquint : yeux plissés de joie, de 0 à 1
*/
private fun drawAssistant(
	inScope  : DrawScope,
	inMood   : Mood,
	inLook   : Offset,
	inBlink  : Float,
	inClosed : Boolean,
	inBreath : Float,
	inSquash : Float,
	inHop    : Float,
	inDizzy  : Float,
	inSpin   : Float,
	inWide   : Float,
	inSquint : Float
) = with(inScope)
{
	val vU      = size.width / 120f														// une unité de la grille
	fun p(inX: Float, inY: Float) = Offset(inX * vU, inY * vU)						// point de la grille
	val vSx     = 1f + 0.16f * inSquash - 0.012f * inBreath								// largeur : s'étale écrasé, respire
	val vSy     = 1f - 0.22f * inSquash + 0.025f * inBreath								// hauteur
	val vBase   = p(60f, 100f)																// pied de la carte (point fixe de l'écrasement)
	translate(top = -inHop * size.height) {
		scale(vSx, vSy, vBase) {
			// ombre douce au sol
			drawOval(Color.Black.copy(alpha = 0.10f), p(22f, 98f), Size(76f * vU, 8f * vU))
			// carte
			val vBody = Rect(p(10f, 22f), p(110f, 98f))												// carte, 100 × 76
			drawRoundRect(Brush.linearGradient(0f to kBodyTop, 0.55f to kBodyMiddle, 1f to kBodyBottom, start = vBody.topLeft, end = vBody.bottomRight.copy(x = vBody.left + vBody.width * 0.3f)), vBody.topLeft, vBody.size, CornerRadius(18f * vU))
			drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0f)), vBody.top, vBody.top + 26f * vU), vBody.topLeft, Size(vBody.width, 26f * vU), CornerRadius(18f * vU))
			drawRoundRect(Brush.linearGradient(0f to Color.White.copy(alpha = 0.7f), 0.5f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.3f), start = vBody.topLeft, end = vBody.bottomRight), vBody.topLeft, vBody.size, CornerRadius(18f * vU), style = Stroke(1.6f * vU))
			// joues
			if (inMood == Mood.HAPPY || inSquint > 0f)
			{
				drawOval(kCheek, p(20f, 66f), Size(16f * vU, 9f * vU))
				drawOval(kCheek, p(84f, 66f), Size(16f * vU, 9f * vU))
			}
			// yeux
			listOf(40f, 80f).forEach { inX -> eye(this, p(inX, 52f), vU, inLook, inBlink, inClosed, inDizzy, inSpin, inWide, inSquint, inX < 60f) }
			// sourcils (inquiet)
			if (inMood == Mood.WORRIED && !inClosed)
			{
				drawLine(kPupil, p(31f, 37f), p(45f, 34f), 2.6f * vU, StrokeCap.Round)
				drawLine(kPupil, p(75f, 34f), p(89f, 37f), 2.6f * vU, StrokeCap.Round)
			}
			// bouche
			mouth(this, vU, if (inSquint > 0.5f) Mood.HAPPY else inMood, inWide, inDizzy)
		}
	}
}

/*
	Dessine un œil : blanc, pupille qui suit le regard, paupière qui descend ;
	fermé pour de bon : trait arrondi paisible ; étourdi : pupille qui tourne ;
	plissé de joie : arc vers le haut.
	inScope  : zone de dessin
	inCenter : centre de l'œil
	inU      : unité de la grille
	inLook   : direction du regard
	inBlink  : paupière, de 0 à 1
	inClosed : fermé pour de bon
	inDizzy  : étourdissement, de 0 à 1
	inSpin   : tour des pupilles, de 0 à 1
	inWide   : écarquillé, de 0 à 1
	inSquint : plissé, de 0 à 1
	inLeft   : œil gauche (sens du tour étourdi)
*/
private fun eye(
	inScope  : DrawScope,
	inCenter : Offset,
	inU      : Float,
	inLook   : Offset,
	inBlink  : Float,
	inClosed : Boolean,
	inDizzy  : Float,
	inSpin   : Float,
	inWide   : Float,
	inSquint : Float,
	inLeft   : Boolean
) = with(inScope)
{
	val vRx = (9f + 2.5f * inWide) * inU															// demi-largeur
	val vRy = (11f + 3f * inWide) * inU																// demi-hauteur
	if (inClosed || inSquint > 0.5f)
	{
		val vArc = Path().apply {
			val vDy = if (inClosed) 2f * inU else -3f * inU											// fermé : vers le bas ; joie : vers le haut
			moveTo(inCenter.x - vRx, inCenter.y)
			quadraticTo(inCenter.x, inCenter.y + vDy * 2.4f, inCenter.x + vRx, inCenter.y)
		}																						// trait de l'œil
		drawPath(vArc, kPupil, style = Stroke(2.8f * inU, cap = StrokeCap.Round))
		return@with
	}
	val vOpen = (1f - inBlink).coerceIn(0.06f, 1f)												// part ouverte
	drawOval(kEyeWhite, Offset(inCenter.x - vRx, inCenter.y - vRy * vOpen), Size(2 * vRx, 2 * vRy * vOpen))
	if (vOpen > 0.2f)
	{
		val vSpinAngle = (if (inLeft) inSpin else -inSpin) * 2f * PI.toFloat()					// tour étourdi
		val vLook      = inLook * (1f - inDizzy) + Offset(cos(vSpinAngle), sin(vSpinAngle)) * inDizzy	// regard, ou tournis
		val vPupil     = Offset(inCenter.x + vLook.x * vRx * 0.45f, inCenter.y + vLook.y * vRy * 0.4f * vOpen)	// centre de la pupille
		val vR         = (5.2f - 1.4f * inWide) * inU											// rayon de la pupille (rétrécit au sursaut)
		drawCircle(kPupil, vR, vPupil)
		drawCircle(Color.White.copy(alpha = 0.9f), vR * 0.32f, vPupil + Offset(-vR * 0.35f, -vR * 0.38f))	// reflet
	}
}

/*
	Dessine la bouche selon l'humeur : sourire, trait, petite moue ; ronde
	au sursaut ; ondulée quand il est étourdi.
	inScope : zone de dessin
	inU     : unité de la grille
	inMood  : humeur
	inWide  : sursaut, de 0 à 1
	inDizzy : étourdissement, de 0 à 1
*/
private fun mouth(
	inScope : DrawScope,
	inU     : Float,
	inMood  : Mood,
	inWide  : Float,
	inDizzy : Float
) = with(inScope)
{
	val vStroke = Stroke(2.8f * inU, cap = StrokeCap.Round)										// trait de la bouche
	when
	{
		inWide > 0.4f  -> drawOval(kPupil, Offset(55f * inU, 72f * inU), Size(10f * inU, 9f * inU))
		inDizzy > 0.4f -> drawPath(Path().apply {
			moveTo(50f * inU, 78f * inU)
			cubicTo(53f * inU, 74f * inU, 56f * inU, 82f * inU, 60f * inU, 78f * inU)
			cubicTo(64f * inU, 74f * inU, 67f * inU, 82f * inU, 70f * inU, 78f * inU)
		}, kPupil, style = vStroke)
		inMood == Mood.HAPPY   -> drawPath(Path().apply { moveTo(50f * inU, 74f * inU); quadraticTo(60f * inU, 84f * inU, 70f * inU, 74f * inU) }, kPupil, style = vStroke)
		inMood == Mood.WORRIED -> drawPath(Path().apply { moveTo(52f * inU, 80f * inU); quadraticTo(60f * inU, 73f * inU, 68f * inU, 80f * inU) }, kPupil, style = vStroke)
		else                   -> drawLine(kPupil, Offset(53f * inU, 77f * inU), Offset(67f * inU, 77f * inU), 2.8f * inU, StrokeCap.Round)
	}
}
