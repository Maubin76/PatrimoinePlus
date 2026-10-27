package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.LoadStatus
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import fr.maubin.patrimoineplus.ui.assistant.AssistantMood
import fr.maubin.patrimoineplus.ui.assistant.AssistantTarget
import fr.maubin.patrimoineplus.ui.assistant.Mood
import fr.maubin.patrimoineplus.ui.assistant.Remark
import fr.maubin.patrimoineplus.ui.assistant.assistantMood
import fr.maubin.patrimoineplus.ui.budget.compareBudget
import fr.maubin.patrimoineplus.ui.components.AssistantAvatar
import fr.maubin.patrimoineplus.ui.components.AvatarReaction
import fr.maubin.patrimoineplus.ui.components.FloatingSurface
import fr.maubin.patrimoineplus.ui.goals.GoalStatus
import fr.maubin.patrimoineplus.ui.goals.evaluateGoals
import fr.maubin.patrimoineplus.ui.home.buildHomeState
import kotlinx.coroutines.delay
import java.time.YearMonth
import androidx.compose.runtime.staticCompositionLocalOf
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalDensity
import fr.maubin.patrimoineplus.domain.calc.scoped
import fr.maubin.patrimoineplus.domain.calc.scopeOf

const val kAssistantName = "Assistant"											// nom de l'assistant (provisoire)
private val kAvatarSize  = 168.dp												// taille de l'assistant flottant
private const val kHoverMillis   = 450L											// survol avant d'ouvrir la bulle
private const val kLeaveMillis   = 350L											// sortie avant de la refermer
private const val kNoticeMillis  = 6_000L											// durée d'un message d'événement

/*
	Message ponctuel de l'assistant, après un événement.
	text   : phrase
	urgent : vrai si la bulle s'ouvre d'elle-même (problème à régler)
	token  : numéro, différent à chaque message
*/
private data class AssistantNotice(
	val text   : String,
	val urgent : Boolean,
	val token  : Long
)

/*
	Humeur de l'assistant pour la vue affichée et le mois en cours : variation
	du patrimoine, budget du mois (s'il y en a un), objectifs. Montants
	masqués en mode discret.
	inSpace      : données de la vue (profil ou famille)
	inController : contrôleur (budget et objectifs de la vue)
	inHidden     : mode discret
*/
@Composable
fun rememberAssistantMood(
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inHidden     : Boolean
): AssistantMood
{
	val vBudget by inController.budget.collectAsState()								// budget de la vue
	val vGoals  by inController.goals.collectAsState()								// objectifs de la vue
	return remember(inSpace.data, inSpace.today, vBudget, vGoals, inHidden) {
		val vData  = inSpace.data																// données
		val vChange = buildHomeState(vData.accounts, vData.transactions, vData.prices, inSpace.today, vData.loans).monthChange	// variation du mois
		val vView  = if (vBudget.lines.isEmpty()) null else compareBudget(vBudget, vData, YearMonth.from(inSpace.today))	// budget du mois
		assistantMood(vChange, vView, evaluateGoals(vGoals, vData, inSpace.today)) { inCents -> if (inHidden) kMaskedAmount else formatEuros(inCents) }
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
	Assistant : la carte vivante, son humeur du mois, ses
	réactions aux événements (enregistrement réussi : hochement ; conflit,
	fichier illisible, écriture impossible, modifications à enregistrer :
	sursaut et bulle ; nouvelle version : regard vers le haut ; objectif
	atteint, retour au vert : joie) et sa bulle (au survol ou au clic) :
	résumé de l'humeur, remarques qui mènent à leur page. Yeux fermés en mode
	discret. Mode calme : ni réactions ni bulle d'elle-même.
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
	val vMood     = rememberAssistantMood(inSpace, inController, inHidden)			// humeur et raisons
	val vPending  by inController.pending.collectAsState()							// modifications à enregistrer avant une action
	val vUpdate   by inController.update.collectAsState()							// nouvelle version
	var vReaction by remember { mutableStateOf<Pair<AvatarReaction, Long>?>(null) }	// dernière réaction
	var vNotice   by remember { mutableStateOf<AssistantNotice?>(null) }			// message d'événement
	var vOpen     by remember { mutableStateOf(false) }								// bulle ouverte
	var vPinned   by remember { mutableStateOf(false) }								// ouverte au clic (reste ouverte)
	val vHoverSrc = remember { MutableInteractionSource() }							// survol de l'assistant
	val vHovered  by vHoverSrc.collectIsHoveredAsState()								// pointeur sur l'assistant
	val vCount    = remember { mutableLongStateOf(0L) }								// numéros des réactions et messages
	val vPrevious = remember { mutableStateOf<Snapshot?>(null) }					// état précédent, pour voir ce qui change

	/*
		Déclenche une réaction et, au besoin, un message.
		inKind   : réaction
		inText   : message ; null sans
		inUrgent : la bulle s'ouvre d'elle-même
	*/
	fun react(inKind: AvatarReaction, inText: String? = null, inUrgent: Boolean = false)
	{
		vCount.longValue++
		vReaction = inKind to vCount.longValue
		if (inText != null) vNotice = AssistantNotice(inText, inUrgent, vCount.longValue)
		if (inUrgent && !inCalm) vOpen = true
	}

	val vGoals   by inController.goals.collectAsState()								// objectifs de la vue
	val vReached = remember(inSpace.data, vGoals, inSpace.today) {
		evaluateGoals(vGoals, inSpace.data, inSpace.today).filter { it.status == GoalStatus.REACHED }.map { it.goal.title }.toSet()
	}																					// objectifs atteints
	val vNow     = Snapshot(inSpace.status, vPending != null, vUpdate != null, vMood.mood, vReached)	// état courant
	LaunchedEffect(vNow) {
		val vBefore = vPrevious.value														// état précédent
		val vAfter  = vNow																	// état courant
		vPrevious.value = vAfter
		if (vBefore == null) return@LaunchedEffect												// premier affichage : pas de réaction
		when
		{
			vAfter.status is LoadStatus.Conflict && vBefore.status !is LoadStatus.Conflict       -> react(AvatarReaction.STARTLE, "Le PC et le téléphone ont changé les mêmes données : choisis la version à garder.", true)
			vAfter.status is LoadStatus.Invalid && vBefore.status !is LoadStatus.Invalid         -> react(AvatarReaction.STARTLE, "Le fichier d’échange est illisible : ${(vAfter.status as LoadStatus.Invalid).message}", true)
			vAfter.status is LoadStatus.WriteFailed && vBefore.status !is LoadStatus.WriteFailed -> react(AvatarReaction.STARTLE, "Impossible d’écrire dans le dossier synchronisé : ${(vAfter.status as LoadStatus.WriteFailed).message}", true)
			vAfter.pending && !vBefore.pending                                                    -> react(AvatarReaction.STARTLE, "Des modifications ne sont pas encore écrites dans le fichier d’échange.")
			vAfter.update && !vBefore.update                                                      -> react(AvatarReaction.LOOK_UP, "Une nouvelle version de PatrimoinePlus est disponible.")
			(vAfter.reached - vBefore.reached).isNotEmpty()                                       -> react(AvatarReaction.JOY, "Objectif « ${(vAfter.reached - vBefore.reached).first()} » atteint, bravo !")
			vAfter.mood == Mood.HAPPY && vBefore.mood == Mood.WORRIED                             -> react(AvatarReaction.JOY)
			vAfter.status is LoadStatus.Synced && vBefore.status == LoadStatus.Pending             -> react(AvatarReaction.NOD)
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
		if (vOpen)
		{
			val vAbove = with(LocalDensity.current) { (kAvatarSize * 0.75f).roundToPx() }	// la bulle s'ouvre au-dessus du corps
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
	État observé par l'assistant, pour réagir à ce qui change.
	status  : état de la lecture du fichier d'échange
	pending : modifications à enregistrer avant une action
	update  : nouvelle version proposée
	mood    : humeur
	reached : titres des objectifs atteints
*/
private data class Snapshot(
	val status  : LoadStatus,
	val pending : Boolean,
	val update  : Boolean,
	val mood    : Mood,
	val reached : Set<String>
)

/*
	Bulle de l'assistant : message d'événement éventuel, résumé de l'humeur,
	puis ses remarques (pastille de ton, phrase) qui mènent à leur page.
	inMood   : humeur et remarques
	inNotice : message d'événement ; null sans
	inOnGo   : ouvre la page d'une remarque
*/
@Composable
internal fun AssistantBubble(
	inMood   : AssistantMood,
	inNotice : String?,
	inOnGo   : (AssistantTarget) -> Unit
)
{
	val vColors = MaterialTheme.colorScheme													// palette
	FloatingSurface(RoundedCornerShape(20.dp), Modifier.widthIn(max = 340.dp), inSolid = true) {
		Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
			inNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = vColors.onSurface) }
			Text(inMood.headline, style = MaterialTheme.typography.titleSmall)
			inMood.remarks.forEach { inRemark -> RemarkRow(inRemark) { inOnGo(inRemark.target) } }
			if (inMood.remarks.isEmpty()) Text("Je surveille ton budget, tes objectifs et ton patrimoine du mois.", style = MaterialTheme.typography.bodySmall, color = vColors.onSurfaceVariant)
		}
	}
}

/*
	Ligne d'une remarque : pastille (verte si bonne nouvelle, rose si point
	d'attention), phrase, cliquable vers sa page.
	inRemark  : remarque
	inOnClick : ouvre sa page
*/
@Composable
private fun RemarkRow(
	inRemark  : Remark,
	inOnClick : () -> Unit
)
{
	val vDot = if (inRemark.tone == Mood.WORRIED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary	// pastille du ton
	Row(Modifier.clickable(onClick = inOnClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(8.dp).background(vDot, CircleShape))
		Spacer(Modifier.width(10.dp))
		Text(inRemark.text, style = MaterialTheme.typography.bodyMedium)
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

