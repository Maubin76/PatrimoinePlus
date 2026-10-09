package fr.maubin.patrimoineplus.ui.lock

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

private const val kIntroMillis   = 1_250											// durée de l'animation du lancement
private const val kSplashLogoDp  = 192f												// diamètre du logo sur l'écran de démarrage système
private const val kFinalLogoDp   = 104f												// diamètre du logo sur l'écran de connexion
private val kInk                 = Color(0xFF0A0A0A)								// encre du logo (fixe, comme l'icône)
private val kPaper               = Color.White										// pastille du logo
private val kEase                = CubicBezierEasing(0.2f, 0f, 0f, 1f)			// courbe « accentuée » de Material

/*
	Écran de connexion. Au lancement, il s'ouvre sur l'animation du logo : les
	deux chevrons glissent dans la pastille et s'emboîtent, puis le logo
	remonte à sa place et le nom et le bouton apparaissent. Ensuite (retour
	après une minute), il s'affiche directement. L'authentification ne se lance
	qu'au toucher du bouton ; l'animation est sautée si les animations du
	téléphone sont désactivées.
	inAuth        : vrai si le verrou est activé (« Déverrouiller »), faux sinon (« Entrer »)
	inIntro       : vrai pour jouer l'animation du lancement
	inMessage     : dernier message d'échec ; null sans échec
	inOnUnlock    : bouton : authentification, ou entrée directe
	inOnIntroDone : animation terminée
	inOnReady     : logo mesuré : l'écran de démarrage système peut céder la place
*/
@Composable
fun LockScreen(
	inAuth        : Boolean,
	inIntro       : Boolean,
	inMessage     : String?,
	inOnUnlock    : () -> Unit,
	inOnIntroDone : () -> Unit,
	inOnReady     : () -> Unit = {}
)
{
	val vContext  = LocalContext.current										// pour lire le réglage des animations
	val vProgress = remember { Animatable(if (inIntro) 0f else 1f) }			// avancement de l'animation
	LaunchedEffect(Unit) {
		if (vProgress.value < 1f)
		{
			val vScale = Settings.Global.getFloat(vContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)	// réglage des animations
			if (vScale > 0f)
			{
				vProgress.animateTo(1f, tween(kIntroMillis, easing = LinearEasing))
			}
			else
			{
				vProgress.snapTo(1f)
			}
		}
		inOnIntroDone()
	}
	LockContent(vProgress.value, inAuth, inMessage, inOnUnlock, inOnReady)
}

/*
	Contenu de l'écran de connexion à un instant de l'animation (0 : logo
	centré en grand, sans chevrons, comme l'écran de démarrage système ; 1 :
	écran posé). Sans état : sert aussi aux captures.
	inProgress : avancement, de 0 à 1
	inAuth     : vrai si le verrou est activé
	inMessage  : dernier message d'échec ; null sans échec
	inOnUnlock : bouton
	inOnReady  : appelé une fois le logo mesuré (sa place de départ est alors connue)
*/
@Composable
fun LockContent(
	inProgress : Float,
	inAuth     : Boolean,
	inMessage  : String?,
	inOnUnlock : () -> Unit,
	inOnReady  : () -> Unit = {}
)
{
	val vChevrons = kEase.transform(phase(inProgress, 0.05f, 0.5f))				// glissement des chevrons
	val vMove     = kEase.transform(phase(inProgress, 0.5f, 0.85f))				// remontée du logo
	val vReveal   = kEase.transform(phase(inProgress, 0.68f, 1f))				// apparition du texte et du bouton
	var vRoot     by remember { mutableStateOf<Offset?>(null) }				// centre de l'écran
	var vLogo     by remember { mutableStateOf<Offset?>(null) }				// centre du logo posé
	Box(
		modifier         = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surface)
			.onGloballyPositioned { inRoot -> vRoot = Offset(inRoot.size.width / 2f, inRoot.size.height / 2f) },
		contentAlignment = Alignment.Center
	) {
		Column(
			modifier            = Modifier.padding(32.dp),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Box(
				Modifier
					.onGloballyPositioned { inLogo -> vLogo = inLogo.boundsInRoot().center }
					.graphicsLayer {
						val vStart  = kSplashLogoDp / kFinalLogoDp						// échelle de départ
						val vOffset = if (vRoot != null && vLogo != null) vRoot!! - vLogo!! else Offset.Zero	// du logo posé au centre de l'écran
						alpha        = if (vLogo != null || vMove >= 1f) 1f else 0f
						scaleX       = lerp(vStart, 1f, vMove)
						scaleY       = lerp(vStart, 1f, vMove)
						translationX = lerp(vOffset.x, 0f, vMove)
						translationY = lerp(vOffset.y, 0f, vMove)
					}
			) {
				PatrimoineLogo(kFinalLogoDp.dp, vChevrons)
			}
			LaunchedEffect(vRoot != null && vLogo != null) {
				if (vRoot != null && vLogo != null) inOnReady()
			}
			Spacer(Modifier.height(28.dp))
			Column(
				modifier            = Modifier.graphicsLayer {
					alpha        = vReveal
					translationY = (1f - vReveal) * 24.dp.toPx()
				},
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Text("PatrimoinePlus", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
				Spacer(Modifier.height(8.dp))
				Text(
					text  = if (inAuth) "Empreinte ou code du téléphone" else "Votre patrimoine, en un coup d’œil",
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				if (inMessage != null)
				{
					Spacer(Modifier.height(16.dp))
					Text(inMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
				}
				Spacer(Modifier.height(36.dp))
				Button(
					onClick        = inOnUnlock,
					enabled        = inProgress >= 1f,
					contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp)
				) {
					Text(if (inAuth) "Déverrouiller" else "Entrer")
				}
			}
		}
	}
}

/*
	Logo dessiné : disque noir, pastille blanche, deux chevrons qui s'emboîtent
	(mêmes proportions que l'icône de l'appli). Les chevrons arrivent des
	bords de la pastille, qui les découpe, selon inChevrons.
	inSize     : diamètre du disque
	inChevrons : avancement des chevrons, de 0 (hors de la pastille) à 1 (en place)
*/
@Composable
fun PatrimoineLogo(
	inSize     : Dp,
	inChevrons : Float = 1f
)
{
	Canvas(Modifier.size(inSize).semantics { contentDescription = "Logo PatrimoinePlus" }) {
		val vUnit   = size.minDimension / 72f										// une unité de la grille de l'icône (72 visibles sur 108)
		val vCenter = Offset(size.width / 2f, size.height / 2f)						// centre du logo
		drawCircle(kInk, radius = 36f * vUnit, center = vCenter)
		drawCircle(kPaper, radius = 27f * vUnit, center = vCenter)
		val vPastille = Path().apply { addOval(androidx.compose.ui.geometry.Rect(vCenter, 27f * vUnit)) }	// découpe des chevrons
		val vShift    = (1f - inChevrons) * 30f * vUnit									// décalage restant
		clipPath(vPastille) {
			translate(left = -vShift) { chevron(this, vUnit, listOf(39f to 37.5f, 55.5f to 51f, 39f to 64.5f)) }
			translate(left = vShift) { chevron(this, vUnit, listOf(69f to 43.5f, 52.5f to 57f, 69f to 70.5f)) }
		}
	}
}

/*
	Dessine un chevron (triangle) donné dans la grille 108 × 108 de l'icône.
	inScope  : zone de dessin
	inUnit   : taille d'une unité de la grille
	inPoints : trois sommets, en unités de la grille
*/
private fun chevron(
	inScope  : DrawScope,
	inUnit   : Float,
	inPoints : List<Pair<Float, Float>>
)
{
	val vOrigin = inScope.size.width / 2f - 54f * inUnit						// position de l'origine de la grille
	val vTop    = inScope.size.height / 2f - 54f * inUnit						// position du haut de la grille
	val vPath   = Path().apply {
		inPoints.forEachIndexed { inIndex, (inX, inY) ->
			if (inIndex == 0) moveTo(vOrigin + inX * inUnit, vTop + inY * inUnit) else lineTo(vOrigin + inX * inUnit, vTop + inY * inUnit)
		}
		close()
	}																			// triangle
	inScope.drawPath(vPath, kInk)
}

/*
	Avancement d'une phase de l'animation : 0 avant son début, 1 après sa fin.
	inProgress : avancement global
	inStart    : début de la phase
	inEnd      : fin de la phase
*/
private fun phase(
	inProgress : Float,
	inStart    : Float,
	inEnd      : Float
): Float = ((inProgress - inStart) / (inEnd - inStart)).coerceIn(0f, 1f)
