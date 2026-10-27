package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.LoadStatus
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.domain.calc.scopeOf
import fr.maubin.patrimoineplus.domain.calc.scoped
import fr.maubin.patrimoineplus.ui.assistant.AssistantMood
import fr.maubin.patrimoineplus.ui.assistant.AssistantTarget
import fr.maubin.patrimoineplus.ui.assistant.Mood
import fr.maubin.patrimoineplus.ui.assistant.assistantMood
import fr.maubin.patrimoineplus.ui.budget.compareBudget
import fr.maubin.patrimoineplus.ui.components.AssistantAvatar
import fr.maubin.patrimoineplus.ui.components.AvatarReaction
import fr.maubin.patrimoineplus.ui.components.LocalPointer
import fr.maubin.patrimoineplus.ui.goals.GoalStatus
import fr.maubin.patrimoineplus.ui.goals.evaluateGoals
import fr.maubin.patrimoineplus.ui.home.buildHomeState
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

const val kAssistantName = "Assistant"											// nom de l'assistant (provisoire)
private val kAvatarSize   = 168.dp												// taille de l'assistant flottant
private val kBubbleInk    = Color(0xFF16151F)									// encre de la bulle (trait et texte)
private val kBubbleTail   = 18.dp												// hauteur de la pointe de la bulle
private const val kHoverMillis  = 450L											// survol avant d'ouvrir la bulle
private const val kLeaveMillis  = 350L											// sortie avant de la refermer
private const val kNoticeMillis = 7_000L										// durée d'un message d'événement
private const val kLookMillis   = 2_500L										// durée d'un regard vers une fenêtre

/*
	Où l'assistant regarde après un événement.
*/
internal enum class LookTarget
{
	CENTER																			// une fenêtre de choix au centre (conflit, modifications à enregistrer, mise à jour)
}

/*
	Ce que fait l'assistant après un événement.
	reaction : réaction
	text     : ce qu'il dit ; null : il ne dit rien
	urgent   : sa bulle s'ouvre d'elle-même (problème à régler)
	look     : où il regarde un instant ; null : il suit le pointeur
*/
internal data class AssistantEvent(
	val reaction : AvatarReaction,
	val text     : String?     = null,
	val urgent   : Boolean     = false,
	val look     : LookTarget? = null
)

/*
	État observé par l'assistant, pour réagir à ce qui change.
	status  : état de la lecture du fichier d'échange
	pending : modifications à enregistrer avant une action
	update  : nouvelle version proposée
	mood    : humeur
	reached : titres des objectifs atteints
*/
internal data class Snapshot(
	val status  : LoadStatus,
	val pending : Boolean,
	val update  : Boolean,
	val mood    : Mood,
	val reached : Set<String>
)

/*
	Réaction de l'assistant à un changement d'état, la plus importante
	d'abord : conflit, fichier illisible, écriture impossible (sursaut, bulle
	ouverte) ; modifications à enregistrer (sursaut, regard vers la fenêtre) ;
	nouvelle version (regard vers le haut) ; objectif atteint, retour au vert
	(joie) ; enregistrement réussi (hochement). Phrases naturelles, sans
	détail technique. Fonction pure.
	inBefore : état précédent
	inAfter  : état courant
	Retour   : réaction ; null s'il n'y a pas de quoi réagir
*/
internal fun assistantEvent(
	inBefore : Snapshot,
	inAfter  : Snapshot
): AssistantEvent?
{
	val vNewGoal = (inAfter.reached - inBefore.reached).firstOrNull()									// objectif tout juste atteint
	return when
	{
		inAfter.status is LoadStatus.Conflict && inBefore.status !is LoadStatus.Conflict       -> AssistantEvent(AvatarReaction.STARTLE, "Oh ! Le téléphone et le PC ont modifié les mêmes données en même temps. Dites-moi quelle version garder.", true, LookTarget.CENTER)
		inAfter.status is LoadStatus.Invalid && inBefore.status !is LoadStatus.Invalid         -> AssistantEvent(AvatarReaction.STARTLE, "Je n’arrive pas à lire le fichier partagé avec le téléphone. Il est peut-être encore en train d’arriver ; je réessaie tout seul.", true)
		inAfter.status is LoadStatus.WriteFailed && inBefore.status !is LoadStatus.WriteFailed -> AssistantEvent(AvatarReaction.STARTLE, "Je n’arrive pas à enregistrer dans le dossier partagé. Il est peut-être protégé, ou le disque est plein.", true)
		inAfter.pending && !inBefore.pending                                                    -> AssistantEvent(AvatarReaction.STARTLE, "Attendez ! Certaines modifications ne sont pas encore enregistrées.", look = LookTarget.CENTER)
		inAfter.update && !inBefore.update                                                      -> AssistantEvent(AvatarReaction.LOOK_UP, "Psst… une nouvelle version de PatrimoinePlus est arrivée !", look = LookTarget.CENTER)
		vNewGoal != null                                                                        -> AssistantEvent(AvatarReaction.JOY, "Youpi, l’objectif « $vNewGoal » est atteint !")
		inAfter.mood == Mood.HAPPY && inBefore.mood == Mood.WORRIED                             -> AssistantEvent(AvatarReaction.JOY)
		inAfter.status is LoadStatus.Synced && inBefore.status == LoadStatus.Pending            -> AssistantEvent(AvatarReaction.NOD)
		else                                                                                    -> null
	}
}

/*
	Message ponctuel de l'assistant, après un événement.
	text   : ce qu'il dit
	urgent : vrai si la bulle s'est ouverte d'elle-même
	token  : numéro, différent à chaque message
*/
private data class AssistantNotice(
	val text   : String,
	val urgent : Boolean,
	val token  : Long
)

/*
	Humeur de l'assistant pour la vue affichée et le mois en cours : variation
	du patrimoine, budget du mois (s'il y en a un), objectifs.
	inSpace      : données de la vue (profil ou famille)
	inController : contrôleur (budget et objectifs de la vue)
*/
@Composable
fun rememberAssistantMood(
	inSpace      : WorkspaceState,
	inController : DesktopController
): AssistantMood
{
	val vBudget by inController.budget.collectAsState()								// budget de la vue
	val vGoals  by inController.goals.collectAsState()								// objectifs de la vue
	return remember(inSpace.data, inSpace.today, vBudget, vGoals) {
		val vData   = inSpace.data															// données
		val vChange = buildHomeState(vData.accounts, vData.transactions, vData.prices, inSpace.today, vData.loans).monthChange	// variation du mois
		val vView   = if (vBudget.lines.isEmpty()) null else compareBudget(vBudget, vData, YearMonth.from(inSpace.today))	// budget du mois
		assistantMood(vChange, vView, evaluateGoals(vGoals, vData, inSpace.today))
	}
}

/*
	Assistant flottant en bas à droite de la fenêtre (au-dessus du contenu,
	par-dessus le cadre) ; il s'efface quand le panneau de saisie est ouvert,
	pour n'en cacher aucun bouton. Lancement : il arrive en dernier.
	inController : contrôleur
	inSpace      : données et état de la lecture (toutes vues)
	inHidden     : mode discret
	inCalm       : mode calme
*/
@Composable
fun FloatingAssistant(
	inController : DesktopController,
	inSpace      : WorkspaceState,
	inHidden     : Boolean,
	inCalm       : Boolean
)
{
	val vSet   by inController.settings.collectAsState()							// réglages (vue affichée)
	val vPanel by inController.panel.collectAsState()								// panneau de saisie ouvert
	val vView  = remember(inSpace, vSet.view) { inSpace.copy(data = inSpace.data.scoped(scopeOf(vSet.view, inSpace.data.profiles))) }	// données de la vue
	Box(Modifier.fillMaxSize().padding(end = 44.dp, bottom = 34.dp), contentAlignment = Alignment.BottomEnd) {
		AnimatedVisibility(
			visible  = vPanel == null,
			enter    = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
			exit     = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 3 },
			modifier = Modifier.launchReveal(0.45f, inDy = 24.dp)
		) {
			AssistantCorner(inController, vView, inHidden, inCalm)
		}
	}
}

/*
	Assistant : la carte vivante, son humeur du mois, ses réactions aux
	événements (voir assistantEvent) et sa bulle de BD (au survol ou au clic,
	ou d'elle-même sur un problème, une fois par jour au plus pour un même
	message) : ce qu'il dit, en phrases naturelles, puis de petits liens vers
	les pages concernées. Après certains événements, il regarde un instant la
	fenêtre de choix ouverte au centre. Yeux fermés en mode discret. Mode
	calme : ni réactions ni bulle d'elle-même.
	inController : contrôleur
	inSpace      : données de la vue et état de la lecture
	inHidden     : mode discret
	inCalm       : mode calme
*/
@Composable
fun AssistantCorner(
	inController : DesktopController,
	inSpace      : WorkspaceState,
	inHidden     : Boolean,
	inCalm       : Boolean
)
{
	val vMood     = rememberAssistantMood(inSpace, inController)					// humeur et raisons
	val vPending  by inController.pending.collectAsState()							// modifications à enregistrer avant une action
	val vUpdate   by inController.update.collectAsState()							// nouvelle version
	val vGoals    by inController.goals.collectAsState()								// objectifs de la vue
	var vReaction by remember { mutableStateOf<Pair<AvatarReaction, Long>?>(null) }	// dernière réaction
	var vNotice   by remember { mutableStateOf<AssistantNotice?>(null) }			// message d'événement
	var vOpen     by remember { mutableStateOf(false) }								// bulle ouverte
	var vPinned   by remember { mutableStateOf(false) }								// ouverte au clic (reste ouverte)
	var vLookAt   by remember { mutableStateOf<Offset?>(null) }						// regard imposé un instant ; null : le pointeur
	val vHoverSrc = remember { MutableInteractionSource() }							// survol de l'assistant
	val vHovered  by vHoverSrc.collectIsHoveredAsState()								// pointeur sur l'assistant
	val vCount    = remember { mutableLongStateOf(0L) }								// numéros des réactions et messages
	val vPrevious = remember { mutableStateOf<Snapshot?>(null) }					// état précédent, pour voir ce qui change
	val vOpened   = remember { mutableMapOf<String, LocalDate>() }					// messages déjà ouverts d'eux-mêmes, et quand
	val vWindow   = LocalWindowInfo.current.containerSize							// taille de la fenêtre (regard vers son centre)
	val vPointer  = LocalPointer.current												// pointeur de la fenêtre

	val vReached = remember(inSpace.data, vGoals, inSpace.today) {
		evaluateGoals(vGoals, inSpace.data, inSpace.today).filter { it.status == GoalStatus.REACHED }.map { it.goal.title }.toSet()
	}																					// objectifs atteints
	val vNow     = Snapshot(inSpace.status, vPending != null, vUpdate != null, vMood.mood, vReached)	// état courant
	LaunchedEffect(vNow) {
		val vBefore = vPrevious.value														// état précédent
		vPrevious.value = vNow
		val vEvent  = vBefore?.let { assistantEvent(it, vNow) } ?: return@LaunchedEffect	// premier affichage, ou rien de nouveau
		vCount.longValue++
		vReaction = vEvent.reaction to vCount.longValue
		vEvent.text?.let { inText ->
			val vAgain = vOpened[inText] == LocalDate.now()								// déjà ouvert d'elle-même aujourd'hui
			vNotice = AssistantNotice(inText, vEvent.urgent && !vAgain, vCount.longValue)
			if (vEvent.urgent && !vAgain && !inCalm) { vOpened[inText] = LocalDate.now(); vOpen = true }
		}
		if (vEvent.look == LookTarget.CENTER && !inCalm)
		{
			vLookAt = Offset(vWindow.width / 2f, vWindow.height / 2f)
			delay(kLookMillis)
			vLookAt = null
		}
	}
	LaunchedEffect(vNotice?.token) {
		val vToken = vNotice?.token ?: return@LaunchedEffect								// message affiché
		delay(kNoticeMillis)
		if (vNotice?.token == vToken && !vPinned) { vNotice = null; if (!vHovered) vOpen = false }
	}
	LaunchedEffect(vHovered) {
		if (vHovered) { delay(kHoverMillis); vOpen = true }
		else if (!vPinned) { delay(kLeaveMillis); if (vNotice?.urgent != true) vOpen = false }
	}

	Box(Modifier.hoverable(vHoverSrc)) {
		CompositionLocalProvider(LocalPointer provides { vLookAt ?: vPointer() }) {
			AssistantAvatar(
				inSize       = kAvatarSize,
				inMood       = vMood.mood,
				inEyesClosed = inHidden,
				inReaction   = vReaction,
				inCalm       = inCalm,
				inHovered    = vHovered,
				inLabel      = "$kAssistantName : ${vMood.headline}",
				inOnClick    = { if (vOpen && vPinned) { vOpen = false; vPinned = false } else { vOpen = true; vPinned = true } }
			)
		}
		if (vOpen)
		{
			val vAbove = with(LocalDensity.current) { (kAvatarSize * 0.72f).roundToPx() }		// la bulle s'ouvre au-dessus du corps
			Popup(alignment = Alignment.BottomEnd, offset = IntOffset(0, -vAbove), onDismissRequest = { vOpen = false; vPinned = false }) {
				AssistantBubble(vMood, vNotice?.text) { inTarget ->
					vOpen = false; vPinned = false
					inController.show(when (inTarget) { AssistantTarget.DASHBOARD -> Screen.DASHBOARD; AssistantTarget.BUDGET -> Screen.BUDGET; AssistantTarget.GOALS -> Screen.GOALS })
				}
			}
		}
	}
}

/*
	Contour d'une bulle de BD en pixels.
	inSize   : taille totale (pointe comprise)
	inRadius : rayon des coins
	inTail   : hauteur de la pointe
	inTailX  : distance du bord droit au bout de la pointe
*/
private fun speechPath(
	inSize   : Size,
	inRadius : Float,
	inTail   : Float,
	inTailX  : Float
): Path
{
	val vBody = Path().apply { addRoundRect(RoundRect(0f, 0f, inSize.width, inSize.height - inTail, CornerRadius(inRadius))) }	// corps
	val vTip  = Path().apply {
		val vX = inSize.width - inTailX																	// bout de la pointe
		moveTo(vX - inTail * 1.6f, inSize.height - inTail - 2f)
		quadraticTo(vX - inTail * 0.4f, inSize.height - inTail * 0.4f, vX + inTail * 0.25f, inSize.height)	// pointe courbée, comme dessinée
		quadraticTo(vX - inTail * 0.1f, inSize.height - inTail * 0.6f, vX + inTail * 0.2f, inSize.height - inTail - 2f)
		close()
	}																									// pointe
	return Path().apply { op(vBody, vTip, PathOperation.Union) }
}

/*
	Bulle de BD de l'assistant : fond blanc, trait d'encre épais, pointe vers
	lui ; elle apparaît avec un petit « pop ». Dedans : le message d'un
	événement éventuel, puis ce qu'il dit de son humeur, en phrases
	naturelles ; dessous, de petits liens vers les pages concernées.
	inMood   : humeur et remarques
	inNotice : message d'événement ; null sans
	inOnGo   : ouvre une page
*/
@Composable
internal fun AssistantBubble(
	inMood   : AssistantMood,
	inNotice : String?,
	inOnGo   : (AssistantTarget) -> Unit
)
{
	val vDensity = LocalDensity.current															// conversions
	val vRadius  = with(vDensity) { 26.dp.toPx() }													// coins
	val vTail    = with(vDensity) { kBubbleTail.toPx() }											// pointe
	val vTailX   = with(vDensity) { (kAvatarSize / 2 + 4.dp).toPx() }								// pointe vers le milieu de l'assistant
	val vStroke  = with(vDensity) { 2.6.dp.toPx() }												// trait
	val vShape   = remember(vRadius, vTail, vTailX) { GenericShape { inSize, _ -> addPath(speechPath(inSize, vRadius, vTail, vTailX)) } }	// forme
	val vPop     = remember { Animatable(0.6f) }													// apparition
	LaunchedEffect(Unit) { vPop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
	val vLinks   = inMood.remarks.map { it.target }.distinct()										// pages concernées
	Box(
		Modifier
			.widthIn(max = 330.dp)
			.graphicsLayer { scaleX = vPop.value; scaleY = vPop.value; alpha = ((vPop.value - 0.6f) / 0.25f).coerceIn(0f, 1f); transformOrigin = TransformOrigin(1f - vTailX / size.width.coerceAtLeast(1f), 1f) }
			.background(Color.White, vShape)
			.border(with(vDensity) { vStroke.toDp() }, kBubbleInk, vShape)
			.semantics { contentDescription = "$kAssistantName dit : ${listOfNotNull(inNotice, inMood.speech).joinToString(" ")}" }
			.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + kBubbleTail)
	) {
		Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
			inNotice?.let { Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = kBubbleInk) }
			Text(inMood.speech, style = MaterialTheme.typography.bodyLarge, color = kBubbleInk)
			if (vLinks.isNotEmpty())
			{
				FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
					vLinks.forEach { inTarget ->
						Text(
							text     = when (inTarget) { AssistantTarget.DASHBOARD -> "Voir le tableau de bord"; AssistantTarget.BUDGET -> "Voir le budget"; AssistantTarget.GOALS -> "Voir les objectifs" },
							style    = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline),
							color    = Color(0xFF4B3FCE),
							modifier = Modifier.clickable { inOnGo(inTarget) }
						)
					}
				}
			}
		}
	}
}

// Assistant affiché : null s'il est retiré (Réglages), sinon vrai en mode calme.
val LocalAssistant = staticCompositionLocalOf<Boolean?> { null }

/*
	Message d'un écran vide, dit par l'assistant (petit, à gauche) quand il
	est affiché : il propose l'action utile ; sinon le message seul.
	inText : message
*/
@Composable
fun AssistantHint(inText: String)
{
	val vCalm = LocalAssistant.current ?: return Hint(inText)								// assistant retiré : message seul
	Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
		AssistantAvatar(40.dp, Mood.NEUTRAL, inCalm = vCalm, inLabel = null)						// décoratif : le message dit tout
		Spacer(Modifier.width(12.dp))
		Text(inText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}

// Place laissée en bas des écrans défilants pour que leur contenu remonte au-dessus de l'assistant flottant ; 0 sans assistant.
val LocalAssistantRoom = staticCompositionLocalOf { 0.dp }

// Place à laisser quand l'assistant flottant est affiché (sa hauteur et sa marge).
val kAssistantRoom = 200.dp
