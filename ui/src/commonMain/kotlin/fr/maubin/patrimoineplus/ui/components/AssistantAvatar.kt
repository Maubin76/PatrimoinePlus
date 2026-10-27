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
import androidx.compose.ui.graphics.drawscope.clipPath
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

private val kBodyTop    = Color(0xFFFFFFFF)											// corps, haut : blanc
private val kBodyBottom = Color(0xFFE3DFF3)											// corps, bas : reflet lavande
private val kEye        = Color(0xFF16151F)											// yeux, sourcils (encre)
private val kCheek      = Color(0xFFFF8FA8).copy(alpha = 0.45f)						// joues roses (content)
private val kBadge      = Color(0xFF6A5CF5)											// pastille : remarques (violet de l'appli)
private val kAlert      = Color(0xFFE0577E)											// pastille : point à régler (corail)

/*
	Pastille de l'assistant, en haut à gauche.
*/
enum class AvatarBadge
{
	NONE,																			// sans pastille
	DOTS,																			// « … » : il a des remarques
	ALERT																			// « ! » : un point demande attention
}

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
	Assistant de l'appli : petite carte blanche aux angles très doux, deux
	petits yeux d'encre et une pastille violette, vivante. Yeux qui
	suivent le pointeur (LocalPointer), clignements, respiration et coups
	d'œil ailleurs au repos ; s'écrase puis rebondit au clic, étourdi après
	plusieurs clics rapides ; bouche et sourcils selon l'humeur ; yeux fermés
	en mode discret ; réactions ponctuelles (hochement, sursaut, joie, regard
	vers le haut). Tout est animé au dessin, sans recomposer à chaque image.
	inSize       : côté de la zone de l'assistant (le corps en occupe la largeur, les 3/5 de la hauteur)
	inMood       : humeur
	inEyesClosed : yeux fermés (montants masqués)
	inReaction   : dernière réaction demandée, avec un numéro qui change à chaque demande ; null sans
	inCalm       : sans réactions ni jeu au clic (yeux et clignements seulement)
	inBadge      : pastille en haut à gauche
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
	inBadge      : AvatarBadge                 = AvatarBadge.NONE,
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
			inSquint = vSquint.value,
			inBadge  = inBadge
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
	Dessine l'assistant dans sa zone (grille de 120 de large) : ombre douce,
	corps blanc aux angles très arrondis, deux petits yeux ovales d'encre qui
	regardent (sourcils inquiets, joues contentes), pastille en haut à gauche.
	inScope  : zone de dessin
	inMood   : humeur
	inLook   : direction du regard (longueur ≤ 1 environ)
	inBlink  : paupières, de 0 (ouverts) à 1 (fermés)
	inClosed : yeux fermés pour de bon (mode discret) : traits paisibles
	inBreath : respiration, de −1 à 1
	inSquash : écrasement du clic, de 0 à 1
	inHop    : saut (positif vers le haut), en part de la hauteur
	inDizzy  : étourdissement, de 0 à 1
	inSpin   : tour des yeux étourdis, de 0 à 1
	inWide   : yeux écarquillés, de 0 à 1
	inSquint : yeux plissés de joie, de 0 à 1
	inBadge  : pastille
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
	inSquint : Float,
	inBadge  : AvatarBadge
) = with(inScope)
{
	val vU    = size.width / 120f															// une unité de la grille
	fun p(inX: Float, inY: Float) = Offset(inX * vU, inY * vU)							// point de la grille
	val vSx   = 1f + 0.14f * inSquash - 0.010f * inBreath									// largeur : s'étale écrasé, respire
	val vSy   = 1f - 0.20f * inSquash + 0.022f * inBreath									// hauteur
	val vBase = p(60f, 104f)																// pied du corps (point fixe de l'écrasement)
	drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.18f * (1f - inHop * 2f).coerceIn(0.3f, 1f)), Color.Black.copy(alpha = 0f)), p(60f, 106f), 46f * vU), p(14f, 100f), Size(92f * vU, 12f * vU))	// ombre au sol
	translate(top = -inHop * size.height) {
		scale(vSx, vSy, vBase) {
			val vBody = Rect(p(10f, 34f), p(110f, 104f))												// corps, 100 × 70
			val vRound = CornerRadius(30f * vU)															// angles très doux
			drawRoundRect(Brush.verticalGradient(listOf(kBodyTop, kBodyBottom), vBody.top, vBody.bottom), vBody.topLeft, vBody.size, vRound)
			clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(vBody, vRound)) }) {
				drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)), vBody.top, vBody.top + 22f * vU), vBody.topLeft, Size(vBody.width, 22f * vU))	// reflet du haut, dans la forme du corps
			}
			drawRoundRect(Color(0xFF9E97C8).copy(alpha = 0.35f), vBody.topLeft, vBody.size, vRound, style = Stroke(1.2f * vU))	// contour léger (fonds clairs)
			if (inMood == Mood.HAPPY || inSquint > 0f)
			{
				drawOval(kCheek, p(34f, 78f), Size(13f * vU, 7f * vU))
				drawOval(kCheek, p(84f, 78f), Size(13f * vU, 7f * vU))
			}
			val vEyes = p(70f, 66f) + Offset(inLook.x * 12f * vU, inLook.y * 7f * vU)					// milieu des yeux, qui suit le regard
			listOf(-11f, 11f).forEach { inDx -> eye(this, vEyes + Offset(inDx * vU, 0f), vU, inBlink, inClosed, inDizzy, inSpin, inWide, inSquint, inDx < 0f) }
			if (inMood == Mood.WORRIED && !inClosed && inSquint == 0f)
			{
				drawLine(kEye, vEyes + Offset(-16f * vU, -14f * vU), vEyes + Offset(-6f * vU, -16.5f * vU), 2.2f * vU, StrokeCap.Round)
				drawLine(kEye, vEyes + Offset(6f * vU, -16.5f * vU), vEyes + Offset(16f * vU, -14f * vU), 2.2f * vU, StrokeCap.Round)
			}
			if (inWide > 0.4f) drawOval(kEye, vEyes + Offset(-3f * vU, 12f * vU), Size(6f * vU, 5f * vU))	// petite bouche ronde du sursaut
			if (inBadge != AvatarBadge.NONE) badge(this, p(22f, 36f), 14f * vU, inBadge)
		}
	}
}

/*
	Dessine un œil : petit ovale d'encre (le blanc du corps l'entoure) qui
	s'aplatit au clignement ; fermé pour de bon : trait arrondi vers le bas ;
	plissé de joie : arc vers le haut ; étourdi : tourne sur lui-même.
	inScope  : zone de dessin
	inCenter : centre de l'œil
	inU      : unité de la grille
	inBlink  : paupière, de 0 à 1
	inClosed : fermé pour de bon
	inDizzy  : étourdissement, de 0 à 1
	inSpin   : tour des yeux, de 0 à 1
	inWide   : écarquillé, de 0 à 1
	inSquint : plissé, de 0 à 1
	inLeft   : œil gauche (sens du tour étourdi)
*/
private fun eye(
	inScope  : DrawScope,
	inCenter : Offset,
	inU      : Float,
	inBlink  : Float,
	inClosed : Boolean,
	inDizzy  : Float,
	inSpin   : Float,
	inWide   : Float,
	inSquint : Float,
	inLeft   : Boolean
) = with(inScope)
{
	val vRx = (3.4f + 1.4f * inWide) * inU															// demi-largeur
	val vRy = (5.8f + 1.8f * inWide) * inU															// demi-hauteur
	if (inClosed || inSquint > 0.5f)
	{
		val vDy  = if (inClosed) 2.6f * inU else -3.2f * inU											// fermé : vers le bas ; joie : vers le haut
		val vArc = Path().apply { moveTo(inCenter.x - 4.6f * inU, inCenter.y); quadraticTo(inCenter.x, inCenter.y + vDy * 2f, inCenter.x + 4.6f * inU, inCenter.y) }	// trait de l'œil
		drawPath(vArc, kEye, style = Stroke(2.4f * inU, cap = StrokeCap.Round))
		return@with
	}
	val vAngle  = (if (inLeft) inSpin else -inSpin) * 2f * PI.toFloat()								// tour étourdi
	val vCenter = inCenter + Offset(cos(vAngle), sin(vAngle)) * (2.4f * inU * inDizzy)				// œil qui tourne
	val vOpen   = (1f - inBlink).coerceIn(0.12f, 1f)													// part ouverte
	drawOval(kEye, Offset(vCenter.x - vRx, vCenter.y - vRy * vOpen), Size(2 * vRx, 2 * vRy * vOpen))
	if (vOpen > 0.5f) drawCircle(Color.White.copy(alpha = 0.85f), 1.1f * inU, vCenter + Offset(-1.2f * inU, -2.4f * inU * vOpen))	// reflet
}

/*
	Dessine la pastille : disque violet avec « … » (remarques) ou corail
	avec « ! » (point à régler), cerclée de blanc.
	inScope  : zone de dessin
	inCenter : centre
	inRadius : rayon
	inBadge  : pastille
*/
private fun badge(
	inScope  : DrawScope,
	inCenter : Offset,
	inRadius : Float,
	inBadge  : AvatarBadge
) = with(inScope)
{
	drawCircle(Color.White, inRadius * 1.16f, inCenter)
	drawCircle(Brush.verticalGradient(listOf(lerpColor(if (inBadge == AvatarBadge.ALERT) kAlert else kBadge, 0.25f), if (inBadge == AvatarBadge.ALERT) kAlert else kBadge), inCenter.y - inRadius, inCenter.y + inRadius), inRadius, inCenter)
	val vDot = inRadius * 0.17f																		// point
	if (inBadge == AvatarBadge.DOTS)
	{
		listOf(-0.42f, 0f, 0.42f).forEach { inDx -> drawCircle(Color.White, vDot, inCenter + Offset(inDx * inRadius, 0f)) }
	}
	else
	{
		drawLine(Color.White, inCenter + Offset(0f, -inRadius * 0.45f), inCenter + Offset(0f, inRadius * 0.12f), vDot * 1.9f, StrokeCap.Round)
		drawCircle(Color.White, vDot * 1.0f, inCenter + Offset(0f, inRadius * 0.45f))
	}
}

/*
	Couleur éclaircie vers le blanc.
	inColor  : couleur
	inAmount : part de blanc, de 0 à 1
*/
private fun lerpColor(
	inColor  : Color,
	inAmount : Float
): Color = androidx.compose.ui.graphics.lerp(inColor, Color.White, inAmount)
