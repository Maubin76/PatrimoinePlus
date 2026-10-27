package fr.maubin.patrimoineplus.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import fr.maubin.patrimoineplus.ui.assistant.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val kBodyTop    = Color(0xFFFFFFFF)											// corps, haut : blanc
private val kBodyBottom = Color(0xFFF2F0F8)											// corps, bas : blanc à peine teinté (mat)
private val kShade      = Color(0xFF8E86C4)											// ombrage doux des bords (volume)
private val kEye        = Color(0xFF16151F)											// yeux, sourcils, bouche (encre)
private val kCheek      = Color(0xFFFF8FA8)											// joues roses
private val kBadge      = Color(0xFF6A5CF5)											// pastille : remarques (violet de l'appli)
private val kAlert      = Color(0xFFE0577E)											// pastille : point à régler (corail)
private const val kSleepMillis = 40_000L											// sans mouvement du pointeur : il somnole

// Position du pointeur dans la fenêtre (racine de l'appli), lue au dessin ; null hors de la fenêtre.
val LocalPointer = staticCompositionLocalOf<() -> Offset?> { { null } }

/*
	Pastille de l'assistant, en haut à gauche.
*/
enum class AvatarBadge
{
	NONE,																			// sans pastille
	DOTS,																			// « … » : il a des remarques
	ALERT																			// « ! » : un point demande attention
}

/*
	Réaction ponctuelle de l'assistant à un événement.
*/
enum class AvatarReaction
{
	NOD,																			// hochement (synchronisation réussie)
	STARTLE,																		// sursaut, yeux grands ouverts (erreur, conflit, à enregistrer)
	JOY,																			// grands sauts, yeux plissés (objectif atteint, budget tenu)
	LOOK_UP																			// regarde en haut (nouvelle version)
}

/*
	Gestes de l'assistant au repos, tirés au hasard de temps en temps.
*/
private enum class IdleMove
{
	TILT,																			// penche la tête
	WIGGLE,																			// se dandine
	STRETCH,																		// s'étire vers le haut
	HOP,																			// petit saut
	LOOK_AROUND																		// regarde à gauche puis à droite
}

/*
	État animé de l'assistant (toutes les valeurs sont lues au dessin).
	squash : écrasement (> 0 aplati, < 0 étiré vers le haut)
	jelly  : tremblement de gelée (> 0 plus large, < 0 plus haut)
	hop    : saut, en part de la hauteur (positif vers le haut)
	tilt   : inclinaison, en degrés
	blink  : paupières (0 ouvertes, 1 fermées)
	dizzy  : étourdissement (0 à 1)
	wide   : yeux écarquillés (0 à 1)
	squint : yeux plissés de joie (0 à 1)
	glance : coup d'œil ailleurs
	up     : regard vers le haut (0 à 1)
	perk   : redressé au survol (0 à 1)
	sleepy : somnolence (0 à 1)
*/
private class AvatarMotion
{
	val squash = Animatable(0f)
	val jelly  = Animatable(0f)
	val hop    = Animatable(0f)
	val tilt   = Animatable(0f)
	val blink  = Animatable(0f)
	val dizzy  = Animatable(0f)
	val wide   = Animatable(0f)
	val squint = Animatable(0f)
	val glance = Animatable(Offset.Zero, Offset.VectorConverter)
	val up     = Animatable(0f)
	val perk   = Animatable(0f)
	val sleepy = Animatable(0f)

	/*
		Tremblement de gelée : élan donné, qui s'amortit en oscillant.
		inPush : élan (positif : s'élargit d'abord)
	*/
	suspend fun wobble(inPush: Float) = jelly.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 420f), initialVelocity = inPush)

	/*
		Saut élastique : prise d'élan (écrasé), envol étiré, chute, atterrissage
		écrasé puis tremblement de gelée.
		inScope  : portée des animations parallèles
		inHeight : hauteur, en part de la hauteur de la zone
	*/
	suspend fun jump(
		inScope  : CoroutineScope,
		inHeight : Float
	)
	{
		squash.animateTo(0.5f, tween(110, easing = FastOutSlowInEasing))							// prise d'élan
		inScope.launch { squash.animateTo(-0.45f, tween(130)); squash.animateTo(0f, tween(220)) }	// envol étiré
		hop.animateTo(inHeight, tween(280, easing = LinearOutSlowInEasing))
		hop.animateTo(0f, tween(240, easing = FastOutLinearInEasing))								// chute
		squash.snapTo(0.55f)																		// atterrissage
		inScope.launch { wobble(-4f) }
		squash.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow))
	}
}

/*
	Assistant de l'appli : petit être blanc et mat aux angles très doux, avec
	une légère lueur blanche, deux grands yeux d'encre brillants et des joues
	roses, très vivant. Ses yeux suivent le pointeur (LocalPointer) ; au
	repos il respire, cligne, et de temps en temps penche la tête, se
	dandine, s'étire, fait un petit saut ou regarde autour de lui ; sans
	mouvement du pointeur il somnole, et se réveille en sursaut. Survolé, il
	se redresse ; cliqué, il s'écrase et tremble comme de la gelée (étourdi
	après plusieurs clics rapides). Réactions ponctuelles amples (hochement,
	sursaut, joie, regard vers le haut). Yeux fermés en mode discret. Tout
	est animé au dessin, sans recomposer à chaque image.
	inSize       : côté de la zone de l'assistant (le corps en occupe la largeur, les 3/5 de la hauteur)
	inMood       : humeur
	inEyesClosed : yeux fermés (montants masqués)
	inReaction   : dernière réaction demandée, avec un numéro qui change à chaque demande ; null sans
	inCalm       : sans gestes, réactions ni jeu au clic (yeux, clignements et respiration seulement)
	inHovered    : pointeur sur l'assistant (il se redresse)
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
	inHovered    : Boolean                     = false,
	inBadge      : AvatarBadge                 = AvatarBadge.NONE,
	inLabel      : String?                     = "Assistant",
	inOnClick    : (() -> Unit)?               = null,
	inModifier   : Modifier                    = Modifier
)
{
	val vPointer = LocalPointer.current												// pointeur de la fenêtre
	val vScope   = rememberCoroutineScope()											// animations du clic
	val vMotion  = remember { AvatarMotion() }										// état animé
	var vCenter  by remember { mutableStateOf<Offset?>(null) }						// centre de l'assistant dans la fenêtre
	var vClicks  by remember { mutableStateOf(emptyList<Long>()) }					// instants des derniers clics
	val vMoved   = remember { mutableLongStateOf(System.currentTimeMillis()) }		// dernier mouvement du pointeur
	val vBreath  by rememberInfiniteTransition(label = "souffle").animateFloat(0f, 1f, infiniteRepeatable(tween(3_400, easing = LinearEasing), RepeatMode.Restart), label = "souffle")	// respiration
	val vSpin    by rememberInfiniteTransition(label = "tournis").animateFloat(0f, 1f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Restart), label = "tournis")	// yeux qui tournent (étourdi)

	LaunchedEffect(Unit) {
		while (true)
		{
			delay(2_200L + Random.nextLong(3_400L))
			if (vMotion.sleepy.value > 0.5f) continue
			vMotion.blink.animateTo(1f, tween(70)); vMotion.blink.animateTo(0f, tween(120))
			if (Random.nextInt(4) == 0) { delay(150); vMotion.blink.animateTo(1f, tween(60)); vMotion.blink.animateTo(0f, tween(110)) }	// double clignement, parfois
		}
	}
	LaunchedEffect(Unit) {
		snapshotFlow { vPointer() }.collect { vMoved.longValue = System.currentTimeMillis() }	// le pointeur bouge : il est réveillé
	}
	LaunchedEffect(inCalm) {
		if (inCalm) return@LaunchedEffect
		while (true)
		{
			delay(1_000)
			val vIdle = System.currentTimeMillis() - vMoved.longValue						// temps sans mouvement
			if (vIdle > kSleepMillis && vMotion.sleepy.value == 0f)
			{
				vMotion.sleepy.animateTo(1f, tween(1_600, easing = FastOutSlowInEasing))		// s'endort
			}
			else if (vIdle < 1_000 && vMotion.sleepy.value > 0f)
			{
				vMotion.sleepy.snapTo(0f)													// se réveille en sursaut
				launch { vMotion.wide.animateTo(1f, tween(80)); delay(500); vMotion.wide.animateTo(0f, tween(300)) }
				vMotion.jump(this, 0.10f)
			}
		}
	}
	LaunchedEffect(inCalm, inMood) {
		if (inCalm) return@LaunchedEffect
		while (true)
		{
			delay(5_500L + Random.nextLong(6_500L))
			if (vMotion.sleepy.value > 0f) continue
			val vMove = if (inMood == Mood.HAPPY && Random.nextInt(3) == 0) IdleMove.WIGGLE else IdleMove.entries.random()	// geste tiré au hasard
			when (vMove)
			{
				IdleMove.TILT        ->
				{
					val vSide = if (Random.nextBoolean()) 1f else -1f							// côté
					vMotion.tilt.animateTo(9f * vSide, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
					delay(1_100)
					vMotion.tilt.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
				}
				IdleMove.WIGGLE      ->
				{
					repeat(3) { vMotion.tilt.animateTo(7f, tween(150)); vMotion.tilt.animateTo(-7f, tween(150)) }
					vMotion.tilt.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow))
				}
				IdleMove.STRETCH     ->
				{
					launch { vMotion.squint.animateTo(1f, tween(400)) }
					vMotion.squash.animateTo(-0.38f, tween(650, easing = FastOutSlowInEasing))
					delay(350)
					vMotion.squint.animateTo(0f, tween(200))
					launch { vMotion.wobble(3f) }
					vMotion.squash.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = Spring.StiffnessMediumLow))
				}
				IdleMove.HOP         -> vMotion.jump(this, 0.09f)
				IdleMove.LOOK_AROUND ->
				{
					vMotion.glance.animateTo(Offset(-1f, 0f), tween(260, easing = FastOutSlowInEasing)); delay(500)
					vMotion.glance.animateTo(Offset(1f, 0f), tween(360, easing = FastOutSlowInEasing)); delay(500)
					vMotion.glance.animateTo(Offset.Zero, tween(300, easing = FastOutSlowInEasing))
				}
			}
		}
	}
	LaunchedEffect(inHovered, inCalm) {
		if (inCalm) return@LaunchedEffect
		if (inHovered)
		{
			launch { vMotion.wobble(-2.5f) }
			vMotion.perk.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
		}
		else vMotion.perk.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
	}
	LaunchedEffect(inReaction?.second) {
		val vKind = inReaction?.first ?: return@LaunchedEffect								// réaction demandée
		if (inCalm) return@LaunchedEffect
		vMotion.sleepy.snapTo(0f)
		when (vKind)
		{
			AvatarReaction.NOD     -> repeat(2) {
				vMotion.squash.animateTo(0.28f, tween(120))
				vMotion.squash.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
			}
			AvatarReaction.STARTLE ->
			{
				launch { vMotion.wide.animateTo(1f, tween(80)); delay(1_000); vMotion.wide.animateTo(0f, tween(320)) }
				vMotion.jump(this, 0.2f)
			}
			AvatarReaction.JOY     ->
			{
				launch { vMotion.squint.animateTo(1f, tween(120)); delay(1_600); vMotion.squint.animateTo(0f, tween(300)) }
				vMotion.jump(this, 0.22f)
				vMotion.jump(this, 0.14f)
				repeat(2) { vMotion.tilt.animateTo(8f, tween(130)); vMotion.tilt.animateTo(-8f, tween(130)) }
				vMotion.tilt.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow))
			}
			AvatarReaction.LOOK_UP ->
			{
				vMotion.up.animateTo(1f, tween(260))
				vMotion.squash.animateTo(-0.2f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
				delay(1_400)
				vMotion.up.animateTo(0f, tween(320))
				vMotion.squash.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
			}
		}
	}

	val vClick = inOnClick?.let { inAction ->
		{
			val vNow = System.currentTimeMillis()											// instant du clic
			vClicks = (vClicks + vNow).filter { vNow - it < 1_500L }
			if (!inCalm)
			{
				vScope.launch {
					vMotion.sleepy.snapTo(0f)
					vMotion.squash.animateTo(0.6f, tween(70))
					launch { vMotion.wobble(5f) }
					vMotion.squash.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessMedium))
				}
				if (vClicks.size >= 4)
				{
					vClicks = emptyList()
					vScope.launch { vMotion.dizzy.animateTo(1f, tween(200)); delay(2_400); vMotion.dizzy.animateTo(0f, tween(400)) }
					vScope.launch { repeat(4) { vMotion.tilt.animateTo(6f, tween(160)); vMotion.tilt.animateTo(-6f, tween(160)) }; vMotion.tilt.animateTo(0f, tween(200)) }
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
		val vLook = lookDirection(vPointer(), vCenter, size.width) * (1f - vMotion.sleepy.value) + vMotion.glance.value * 0.9f + Offset(0f, -vMotion.up.value)	// regard : pointeur, coup d'œil, haut
		drawAssistant(
			inScope  = this,
			inMood   = inMood,
			inMotion = vMotion,
			inLook   = vLook,
			inClosed = inEyesClosed,
			inBreath = sin(vBreath * 2f * PI.toFloat()),
			inSpin   = vSpin,
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
	val vDelta  = inPointer - inCenter																// vers le pointeur
	val vLength = hypot(vDelta.x, vDelta.y)															// distance
	if (vLength < 1f) return Offset.Zero
	val vReach = min(1f, vLength / inWidth)															// plus loin : regard plus marqué
	return Offset(vDelta.x / vLength * vReach, vDelta.y / vLength * vReach)
}

/*
	Dessine l'assistant dans sa zone (grille de 120 de large) : ombre au sol,
	lueur blanche, corps blanc mat (ombrage doux des bords pour le volume),
	joues, grands yeux brillants, sourcils inquiets, petite bouche, pastille.
	inScope  : zone de dessin
	inMood   : humeur
	inMotion : état animé
	inLook   : direction du regard (longueur ≤ 1 environ)
	inClosed : yeux fermés pour de bon (mode discret)
	inBreath : respiration, de −1 à 1
	inSpin   : tour des yeux étourdis, de 0 à 1
	inBadge  : pastille
*/
private fun drawAssistant(
	inScope  : DrawScope,
	inMood   : Mood,
	inMotion : AvatarMotion,
	inLook   : Offset,
	inClosed : Boolean,
	inBreath : Float,
	inSpin   : Float,
	inBadge  : AvatarBadge
) = with(inScope)
{
	val vU       = size.width / 120f															// une unité de la grille
	fun p(inX: Float, inY: Float) = Offset(inX * vU, inY * vU)							// point de la grille
	val vSquash  = inMotion.squash.value - 0.16f * inMotion.perk.value + 0.06f * inMotion.sleepy.value	// écrasement (redressé au survol, tassé endormi)
	val vJelly   = inMotion.jelly.value * 0.05f													// tremblement de gelée
	val vBreathe = inBreath * (0.03f - 0.015f * inMotion.sleepy.value)							// respiration (plus lente endormi)
	val vSx      = 1f + 0.20f * vSquash + vJelly - 0.6f * vBreathe								// largeur
	val vSy      = 1f - 0.26f * vSquash - vJelly + vBreathe										// hauteur
	val vHop     = inMotion.hop.value															// saut
	val vBase    = p(60f, 104f)																	// pied du corps (point fixe des déformations)
	val vShadow  = (1f - vHop * 2.4f).coerceIn(0.35f, 1f)											// ombre : plus petite et pâle en l'air
	drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.16f * vShadow), Color.Black.copy(alpha = 0f)), p(60f, 106f), 46f * vU * vShadow), Offset((60f - 46f * vShadow) * vU, 100f * vU), Size(92f * vU * vShadow, 12f * vU))	// ombre au sol
	translate(top = -vHop * size.height) {
		rotate(inMotion.tilt.value, vBase) {
			scale(vSx, vSy, vBase) {
				val vBody  = Rect(p(10f, 34f), p(110f, 104f))											// corps, 100 × 70
				val vRound = CornerRadius(32f * vU)														// angles très doux
				repeat(12) { inStep ->
					val vGrow = (inStep + 1) * 1.3f * vU													// lueur, de plus en plus large et pâle (couches fines : sans marches)
					val vFade = 1f - inStep / 12f															// atténuation
					drawRoundRect(Color.White.copy(alpha = 0.06f * vFade * vFade), vBody.topLeft - Offset(vGrow, vGrow), Size(vBody.width + 2 * vGrow, vBody.height + 2 * vGrow), CornerRadius(vRound.x + vGrow))
				}
				drawRoundRect(Brush.verticalGradient(listOf(kBodyTop, kBodyBottom), vBody.top, vBody.bottom), vBody.topLeft, vBody.size, vRound)
				drawRoundRect(Brush.radialGradient(listOf(kShade.copy(alpha = 0f), kShade.copy(alpha = 0.10f)), p(60f, 56f), 62f * vU), vBody.topLeft, vBody.size, vRound)	// ombrage doux des bords (volume mat)
				drawRoundRect(Color(0xFFB9B3DA).copy(alpha = 0.22f), vBody.topLeft, vBody.size, vRound, style = Stroke(1f * vU))	// contour très léger (fonds clairs)
				val vEyes  = p(60f, 64f) + Offset(inLook.x * 11f * vU, inLook.y * 6f * vU)					// milieu des yeux, qui suit le regard
				val vBlush = if (inMood == Mood.HAPPY || inMotion.squint.value > 0f) 0.55f else 0.30f		// joues
				drawOval(kCheek.copy(alpha = vBlush), vEyes + Offset(-30f * vU, 8f * vU), Size(14f * vU, 7.5f * vU))
				drawOval(kCheek.copy(alpha = vBlush), vEyes + Offset(16f * vU, 8f * vU), Size(14f * vU, 7.5f * vU))
				val vBlink = if (inClosed) 1f else maxOf(inMotion.blink.value, 0.55f * inMotion.sleepy.value)	// paupières
				listOf(-13f, 13f).forEach { inDx -> eye(this, vEyes + Offset(inDx * vU, 0f), vU, inLook, vBlink, inClosed, inMotion.dizzy.value, inSpin, inMotion.wide.value, inMotion.squint.value, inDx < 0f) }
				if (inMood == Mood.WORRIED && !inClosed && inMotion.squint.value == 0f)
				{
					drawLine(kEye, vEyes + Offset(-20f * vU, -15f * vU), vEyes + Offset(-8f * vU, -18f * vU), 2.2f * vU, StrokeCap.Round)
					drawLine(kEye, vEyes + Offset(8f * vU, -18f * vU), vEyes + Offset(20f * vU, -15f * vU), 2.2f * vU, StrokeCap.Round)
				}
				mouth(this, vEyes + Offset(0f, 13f * vU), vU, inMood, inMotion.wide.value, inMotion.squint.value, inMotion.sleepy.value)
				if (inBadge != AvatarBadge.NONE) badge(this, p(22f, 36f), 14f * vU, inBadge)
			}
		}
	}
}

/*
	Dessine un grand œil : ovale d'encre, deux reflets (un grand, un petit)
	qui le rendent brillant, paupière qui descend ; fermé pour de bon : trait
	arrondi vers le bas ; plissé de joie : arc vers le haut ; étourdi :
	tourne sur lui-même.
	inScope  : zone de dessin
	inCenter : centre de l'œil
	inU      : unité de la grille
	inLook   : direction du regard (les reflets glissent un peu à l'opposé)
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
	val vRx = (5.6f + 1.6f * inWide) * inU															// demi-largeur
	val vRy = (8.2f + 2.0f * inWide) * inU															// demi-hauteur
	if (inClosed || inSquint > 0.5f)
	{
		val vDy  = if (inClosed) 2.8f * inU else -3.6f * inU											// fermé : vers le bas ; joie : vers le haut
		val vArc = Path().apply { moveTo(inCenter.x - 6.4f * inU, inCenter.y); quadraticTo(inCenter.x, inCenter.y + vDy * 2f, inCenter.x + 6.4f * inU, inCenter.y) }	// trait de l'œil
		drawPath(vArc, kEye, style = Stroke(2.6f * inU, cap = StrokeCap.Round))
		return@with
	}
	val vAngle  = (if (inLeft) inSpin else -inSpin) * 2f * PI.toFloat()								// tour étourdi
	val vCenter = inCenter + Offset(cos(vAngle), sin(vAngle)) * (3f * inU * inDizzy)					// œil qui tourne
	val vOpen   = (1f - inBlink).coerceIn(0.1f, 1f)													// part ouverte
	drawOval(kEye, Offset(vCenter.x - vRx, vCenter.y - vRy * vOpen), Size(2 * vRx, 2 * vRy * vOpen))
	if (vOpen > 0.45f)
	{
		val vShift = Offset(-inLook.x * 0.8f * inU, -inLook.y * 0.6f * inU)							// reflets : glissent un peu à l'opposé du regard
		drawCircle(Color.White.copy(alpha = 0.95f), 2.4f * inU, vCenter + Offset(-1.8f * inU, -3.2f * inU * vOpen) + vShift)	// grand reflet
		drawCircle(Color.White.copy(alpha = 0.75f), 1.1f * inU, vCenter + Offset(2.0f * inU, 3.0f * inU * vOpen) + vShift)	// petit reflet
	}
}

/*
	Dessine la petite bouche, sous les yeux : sourire content, petite moue
	inquiète, minuscule « o » au sursaut, petit trait endormi ; rien sinon
	(visage plus doux).
	inScope  : zone de dessin
	inCenter : centre de la bouche
	inU      : unité de la grille
	inMood   : humeur
	inWide   : sursaut, de 0 à 1
	inSquint : joie, de 0 à 1
	inSleepy : somnolence, de 0 à 1
*/
private fun mouth(
	inScope  : DrawScope,
	inCenter : Offset,
	inU      : Float,
	inMood   : Mood,
	inWide   : Float,
	inSquint : Float,
	inSleepy : Float
) = with(inScope)
{
	val vStroke = Stroke(2.2f * inU, cap = StrokeCap.Round)											// trait
	when
	{
		inWide > 0.4f                           -> drawOval(kEye, inCenter + Offset(-2.6f * inU, -1.6f * inU), Size(5.2f * inU, 5.6f * inU))
		inSleepy > 0.6f                         -> drawLine(kEye, inCenter + Offset(-2.4f * inU, 0f), inCenter + Offset(2.4f * inU, 0f), 2f * inU, StrokeCap.Round)
		inMood == Mood.HAPPY || inSquint > 0.5f -> drawPath(Path().apply { moveTo(inCenter.x - 4f * inU, inCenter.y - 1f * inU); quadraticTo(inCenter.x, inCenter.y + 3.6f * inU, inCenter.x + 4f * inU, inCenter.y - 1f * inU) }, kEye, style = vStroke)
		inMood == Mood.WORRIED                  -> drawPath(Path().apply { moveTo(inCenter.x - 3.4f * inU, inCenter.y + 1.6f * inU); quadraticTo(inCenter.x, inCenter.y - 1.4f * inU, inCenter.x + 3.4f * inU, inCenter.y + 1.6f * inU) }, kEye, style = vStroke)
		else                                    -> Unit
	}
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
	val vColor = if (inBadge == AvatarBadge.ALERT) kAlert else kBadge									// teinte
	drawCircle(Color.White, inRadius * 1.16f, inCenter)
	drawCircle(Brush.verticalGradient(listOf(lerp(vColor, Color.White, 0.25f), vColor), inCenter.y - inRadius, inCenter.y + inRadius), inRadius, inCenter)
	val vDot = inRadius * 0.17f																		// point
	if (inBadge == AvatarBadge.DOTS)
	{
		listOf(-0.42f, 0f, 0.42f).forEach { inDx -> drawCircle(Color.White, vDot, inCenter + Offset(inDx * inRadius, 0f)) }
	}
	else
	{
		drawLine(Color.White, inCenter + Offset(0f, -inRadius * 0.45f), inCenter + Offset(0f, inRadius * 0.12f), vDot * 1.9f, StrokeCap.Round)
		drawCircle(Color.White, vDot, inCenter + Offset(0f, inRadius * 0.45f))
	}
}
