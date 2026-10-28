package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.Cell
import fr.maubin.patrimoineplus.desktop.ui.components.CellField
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.desktop.ui.components.TileGrid
import fr.maubin.patrimoineplus.desktop.ui.components.TileRow
import fr.maubin.patrimoineplus.desktop.ui.panels.Choice
import fr.maubin.patrimoineplus.desktop.ui.panels.FormError
import fr.maubin.patrimoineplus.desktop.ui.panels.FormField
import fr.maubin.patrimoineplus.desktop.ui.panels.Picker
import fr.maubin.patrimoineplus.desktop.ui.panels.SidePanel
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.formatFrenchDate
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.format.parseFrenchDate
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.components.TileFigure
import fr.maubin.patrimoineplus.ui.components.monthTitle
import fr.maubin.patrimoineplus.ui.goals.Goal
import fr.maubin.patrimoineplus.ui.goals.GoalKind
import fr.maubin.patrimoineplus.ui.goals.GoalStatus
import fr.maubin.patrimoineplus.ui.goals.GoalView
import fr.maubin.patrimoineplus.ui.goals.Goals
import fr.maubin.patrimoineplus.ui.goals.evaluateGoals
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong
import fr.maubin.patrimoineplus.ui.components.flowAccent
import fr.maubin.patrimoineplus.ui.components.FlowAccent
import fr.maubin.patrimoineplus.ui.components.accountAccent
import androidx.compose.ui.graphics.Color
import fr.maubin.patrimoineplus.ui.goals.MonthGoalKind
import fr.maubin.patrimoineplus.ui.goals.MonthGoalStatus
import fr.maubin.patrimoineplus.ui.goals.MonthGoalView
import fr.maubin.patrimoineplus.ui.goals.evaluateMonth
import fr.maubin.patrimoineplus.ui.analysis.buildExpenseAnalysis
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.components.MonthSwitcher
import fr.maubin.patrimoineplus.desktop.Screen
import java.time.YearMonth
import androidx.compose.foundation.layout.heightIn
import fr.maubin.patrimoineplus.ui.components.glassRaised
import fr.maubin.patrimoineplus.ui.components.glassWell
import fr.maubin.patrimoineplus.desktop.ui.tour.tourAnchor
import fr.maubin.patrimoineplus.desktop.ui.AssistantHint
import fr.maubin.patrimoineplus.desktop.ui.LocalAssistantRoom
import fr.maubin.patrimoineplus.ui.components.accountLabel
import androidx.compose.runtime.LaunchedEffect

private const val kBarMillis = 420														// remplissage de la barre d'avancement

/*
	Objectif en cours de saisie : textes tels que tapés.
	id          : identifiant de l'objectif modifié ; null pour un nouveau
	kind        : nature
	title       : intitulé
	target      : cible tapée (euros, ou % pour un taux)
	accountId   : compte suivi
	accountType : type de placement suivi
	current     : montant déjà atteint tapé (montant à la main)
	deadline    : échéance tapée (JJ/MM/AAAA), vide sans échéance
	note        : note
	done        : fait (à cocher)
*/
private data class Draft(
	val id          : String?,
	val kind        : GoalKind,
	val title       : String      = "",
	val target      : String      = "",
	val accountId   : String?     = null,
	val accountType : AccountType = AccountType.SAVINGS,
	val current     : String      = "",
	val deadline    : String      = "",
	val note        : String      = "",
	val done        : Boolean     = false
)

/*
	Objectifs sur le patrimoine : objectifs chiffrés suivis sur les données
	(patrimoine, compte, type de placement, épargne mensuelle, taux d'épargne)
	ou mis à jour à la main, et objectifs à cocher ; avancement, rythme
	actuel, date estimée et effort mensuel pour tenir l'échéance. Saisie dans
	un panneau à droite. Enregistrés sur ce PC à chaque modification.
	inSpace      : données
	inController : contrôleur
*/
@Composable
fun GoalsScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vGoals  by inController.goals.collectAsState()									// objectifs
	val vViews  = remember(vGoals, inSpace.data, inSpace.today) { evaluateGoals(vGoals, inSpace.data, inSpace.today) }	// objectifs évalués
	var vDraft  by remember { mutableStateOf<Draft?>(null) }							// objectif en saisie ; null sans panneau
	var vLast   by remember { mutableStateOf<Draft?>(null) }							// dernière saisie (fermeture animée)
	var vStart  by remember { mutableStateOf<Draft?>(null) }							// saisie proposée à l'ouverture
	val vOpen   = { inDraft: Draft -> inController.whenDiscarded { vDraft = inDraft; vLast = inDraft; vStart = inDraft } }	// ouvre le panneau (saisie en cours : abandon confirmé)
	val vNav    by inController.navigation.collectAsState()							// écran affiché
	LaunchedEffect(vNav) { if (vNav.screen != Screen.GOALS || vNav.accountId != null) vDraft = null }	// autre écran : saisie abandonnée (déjà confirmée)
	val vUpdate = { inGoal: Goal -> inController.setGoals(vGoals.copy(goals = vGoals.goals.map { if (it.id == inGoal.id) inGoal else it })) }	// modifie un objectif
	Row(Modifier.fillMaxSize()) {
		Column(
			modifier            = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp).padding(bottom = LocalAssistantRoom.current),
			verticalArrangement = Arrangement.spacedBy(20.dp)
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text("Objectifs", style = MaterialTheme.typography.displaySmall)
				}
				Spacer(Modifier.width(24.dp))
				Button(onClick = { vOpen(Draft(null, GoalKind.WEALTH)) }, modifier = Modifier.tourAnchor("goals.new")) { Text("Nouvel objectif") }
			}
			SectionHeader(inTitle = "Objectifs globaux", inModifier = Modifier.tourAnchor("goals.global"))
			if (vViews.isEmpty())
			{
				Suggestions(inSpace, vOpen)
			}
			else
			{
				Figures(vViews)
				TileGrid(vViews, 400.dp) { inView, inMod -> GoalTile(inView, inSpace, inMod, { vOpen(draftOf(inView.goal)) }, vUpdate) }
			}
			Spacer(Modifier.height(12.dp))
			MonthSection(vGoals, inSpace, inController)
		}
		AnimatedVisibility(
			visible = vDraft != null,
			enter   = expandHorizontally(tween(220)) + fadeIn(tween(220)),
			exit    = shrinkHorizontally(tween(180)) + fadeOut(tween(180))
		) {
			vLast?.let { inDraft ->
				Editor(
					inDraft  = inDraft,
					inDirty  = vDraft != null && vDraft != vStart,
					inSpace  = inSpace,
					inChange = { vDraft = it; vLast = it },
					inClose  = { vDraft = null },
					inSave   = save@{ inGoal ->
						if (vDraft == null) return@save								// panneau déjà fermé (double clic pendant sa disparition)
						inController.setGoals(vGoals.copy(goals = if (vGoals.goals.any { it.id == inGoal.id }) vGoals.goals.map { if (it.id == inGoal.id) inGoal else it } else vGoals.goals + inGoal))
						vDraft = null
					},
					inDelete = { inId -> inController.setGoals(vGoals.copy(goals = vGoals.goals.filter { it.id != inId })); vDraft = null }
				)
			}
		}
	}
}

/*
	Chiffres clés : objectifs en cours, en bonne voie, en retard, atteints.
	inViews : objectifs évalués
*/
@Composable
private fun Figures(inViews: List<GoalView>)
{
	val vCount = { inStates: Set<GoalStatus> -> inViews.count { it.status in inStates } }	// objectifs dans ces états
	TileRow(
		*listOf(
			"En cours" to vCount(setOf(GoalStatus.ON_TRACK, GoalStatus.BEHIND, GoalStatus.OVERDUE, GoalStatus.OPEN, GoalStatus.TODO)),
			"En bonne voie" to vCount(setOf(GoalStatus.ON_TRACK)),
			"En retard" to vCount(setOf(GoalStatus.BEHIND, GoalStatus.OVERDUE)),
			"Atteints ou faits" to vCount(setOf(GoalStatus.REACHED, GoalStatus.DONE))
		).map { (inLabel, inCount) ->
			Cell(1f) { inMod -> Tile(inModifier = inMod) { TileFigure(inLabel) { Text(inCount.toString(), style = MaterialTheme.typography.headlineSmall) } } }
		}.toTypedArray(),
		inMinWidth = 900.dp
	)
}

/*
	Premier pas : quelques objectifs courants à reprendre d'un clic.
	inSpace : données (Livret A existant)
	inOpen  : ouvre le panneau sur un objectif prérempli
*/
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(
	inSpace : WorkspaceState,
	inOpen  : (Draft) -> Unit
)
{
	val vLivret = inSpace.data.accounts.firstOrNull { !it.archived && it.type == AccountType.SAVINGS && it.name.contains("livret a", ignoreCase = true) }	// Livret A existant
	val vPea    = inSpace.data.accounts.firstOrNull { !it.archived && it.type == AccountType.BROKERAGE && it.name.contains("pea", ignoreCase = true) }	// PEA existant
	val vMonths = remember(inSpace.data, inSpace.today) { buildExpenseAnalysis(inSpace.data, inSpace.today).averageExpense }	// dépenses moyennes d'un mois
	val vCushion = ((vMonths * 3 + 9_999) / 10_000 * 100).coerceAtLeast(300_000)		// trois mois de dépenses, arrondis à la centaine (au moins 3 000 €)
	val vIdeas  = listOfNotNull(
		Draft(null, GoalKind.TYPE, "Matelas de sécurité (3 mois de dépenses)", editableEuros(vCushion), accountType = AccountType.SAVINGS),
		vPea?.let { Draft(null, GoalKind.ACCOUNT, "Remplir le ${it.name}", "150000", accountId = it.id) },
		Draft(null, GoalKind.MANUAL, "Apport pour un achat immobilier", "40000"),
		Draft(null, GoalKind.WEALTH, "Atteindre 100 000 € de patrimoine", "100000"),
		vLivret?.let { Draft(null, GoalKind.ACCOUNT, "Remplir le ${it.name}", "22950", accountId = it.id) },
		Draft(null, GoalKind.MONTHLY, "Mettre 500 € de côté chaque mois", "500"),
		Draft(null, GoalKind.RATE, "Épargner 20 % de mes revenus", "20"),
		Draft(null, GoalKind.TASK, "Ouvrir une assurance vie")
	)																					// idées d'objectifs
	Tile(inTitle = "Aucun objectif pour l’instant") {
		AssistantHint("Choisissez une idée pour commencer, ou créez votre propre objectif. Tout reste modifiable.")
		Spacer(Modifier.height(12.dp))
		FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			vIdeas.forEach { inIdea -> OutlinedButton(onClick = { inOpen(inIdea) }) { Text(inIdea.title, maxLines = 1) } }
		}
	}
}

/*
	Tuile d'un objectif : nature et échéance, état, valeur et cible, barre
	d'avancement, rythme, date estimée et effort pour l'échéance ; case à
	cocher pour un objectif non chiffré, montant à mettre à jour pour un
	objectif suivi à la main.
	inView   : objectif évalué
	inSpace  : données (noms des comptes)
	inMod    : place de la tuile
	inEdit   : ouvre le panneau de modification
	inUpdate : modifie l'objectif (case cochée, montant atteint)
*/
@Composable
private fun GoalTile(
	inView   : GoalView,
	inSpace  : WorkspaceState,
	inMod    : Modifier,
	inEdit   : () -> Unit,
	inUpdate : (Goal) -> Unit
)
{
	val vGoal   = inView.goal																// objectif
	val vHidden = LocalAmountsHidden.current												// mode discret
	val vMoney  = { inCents: Long, inPlus: Boolean -> if (vHidden) kMaskedAmount else formatEuros(inCents, inPlus) }	// montant en texte
	val vSoft   = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	val vAccent = goalAccent(vGoal, inSpace)												// couleur de l'objectif (thème Coloré)
	Tile(
		inModifier = inMod,
		inAccent   = vAccent,
		inTitle    = vGoal.title.ifBlank { vGoal.kind.label },
		inAction   = {
			StatusPill(inView.status)
			IconButton(onClick = inEdit) { Icon(LineIcons.Edit, contentDescription = "Modifier l’objectif", modifier = Modifier.size(18.dp)) }
		}
	) {
		Text(subtitle(vGoal, inSpace), style = MaterialTheme.typography.bodyMedium, color = vSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
		Spacer(Modifier.height(14.dp))
		if (vGoal.kind == GoalKind.TASK)
		{
			Row(
				modifier          = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Checkbox) { inUpdate(vGoal.copy(done = !vGoal.done)) }.padding(end = 12.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Checkbox(checked = vGoal.done, onCheckedChange = null, modifier = Modifier.padding(12.dp))
				Text(if (vGoal.done) "Fait" else "Marquer comme fait", style = MaterialTheme.typography.bodyLarge)
			}
		}
		else
		{
			FlowRow(itemVerticalAlignment = Alignment.Bottom) {							// cible à côté du montant, ou en dessous si la place manque
				when (vGoal.kind)
				{
					GoalKind.RATE -> Text(rateText(inView.current), style = MaterialTheme.typography.headlineSmall)
					else          -> AmountText(inCents = inView.current, inStyle = MaterialTheme.typography.headlineSmall)
				}
				Text(
					text     = when (vGoal.kind) { GoalKind.MONTHLY -> " par mois en moyenne, sur ${vMoney(vGoal.target, false)}"; GoalKind.RATE -> " en moyenne, sur ${rateText(vGoal.target)}"; else -> " sur ${vMoney(vGoal.target, false)}" },
					style    = MaterialTheme.typography.bodyLarge,
					color    = vSoft,
					modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
				)
			}
			Spacer(Modifier.height(10.dp))
			ProgressBar(inView.progress, vAccent)
			Spacer(Modifier.height(10.dp))
			details(inView, vMoney).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = vSoft) }
			if (vGoal.kind == GoalKind.MANUAL)
			{
				var vText by remember(vGoal.id) { mutableStateOf(editableEuros(vGoal.current)) }	// montant atteint tapé
				Spacer(Modifier.height(8.dp))
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text("Montant atteint", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(140.dp))
					CellField(
						inValue       = vText,
						inOnChange    = { inText -> vText = inText; parseEuros(inText)?.takeIf { it >= 0 }?.let { inUpdate(vGoal.copy(current = it)) } },
						inModifier    = Modifier.width(160.dp),
						inPlaceholder = "0,00",
						inEnd         = true,
						inError       = parseEuros(vText)?.let { it >= 0 } != true
					)
				}
			}
		}
		if (vGoal.note.isNotBlank())
		{
			Spacer(Modifier.height(10.dp))
			Text(vGoal.note, style = MaterialTheme.typography.bodyMedium)
		}
	}
}

/*
	Ligne discrète sous le titre : ce que suit l'objectif et son échéance.
	inGoal  : objectif
	inSpace : données (noms des comptes)
*/
private fun subtitle(
	inGoal  : Goal,
	inSpace : WorkspaceState
): String
{
	val vWhat = when (inGoal.kind)
	{
		GoalKind.ACCOUNT -> "Compte · " + (inSpace.data.accounts.firstOrNull { it.id == inGoal.accountId }?.name ?: "compte introuvable")
		GoalKind.TYPE    -> "Type de placement · " + (inGoal.accountType?.label ?: "")
		else             -> inGoal.kind.label
	}																					// suivi
	return vWhat + (inGoal.due?.let { " · échéance le ${formatFrenchDate(it)}" } ?: "")
}

/*
	Phrases d'explication d'un objectif chiffré : reste, rythme, date
	estimée, effort pour l'échéance ; mois complets pour un objectif mensuel.
	inView  : objectif évalué
	inMoney : montant en texte (masqué en mode discret)
*/
private fun details(
	inView  : GoalView,
	inMoney : (Long, Boolean) -> String
): List<String>
{
	val vGoal  = inView.goal																// objectif
	val vPace  = inView.pace																// rythme mensuel
	val vValue = { inValue: Long -> if (vGoal.kind == GoalKind.RATE) rateText(inValue) else inMoney(inValue, false) }	// valeur mensuelle en texte
	return when (vGoal.kind)
	{
		GoalKind.MONTHLY, GoalKind.RATE ->
			if (inView.months == 0) listOf("Pas encore de mois complet à comparer.")
			else listOfNotNull(
				inView.last?.let { "Mois dernier : ${vValue(it)}." },
				"Cible tenue ${inView.hits} mois sur ${inView.months}.",
				if (inView.status == GoalStatus.OVERDUE) "Échéance dépassée." else null
			)
		else -> listOfNotNull(
			if (inView.status == GoalStatus.REACHED) "Objectif atteint." else "Reste ${inMoney(inView.remaining, false)}.",
			when
			{
				inView.status == GoalStatus.REACHED -> null
				vGoal.kind == GoalKind.MANUAL       -> null
				vPace == null                       -> "Rythme pas encore connu : moins d’un mois de recul."
				vPace <= 0                          -> "Rythme actuel : ${inMoney(vPace, true)} par mois ; l’objectif n’avance pas."
				else                                -> "Rythme actuel : ${inMoney(vPace, true)} par mois" + (inView.eta?.let { ", atteint vers ${monthTitle(it).lowercase()}." } ?: ".")
			},
			inView.needed?.let { "Pour tenir l’échéance : ${inMoney(it, false)} par mois." },
			if (inView.status == GoalStatus.OVERDUE) "Échéance dépassée." else null
		)
	}
}

/*
	Barre d'avancement fine, remplie en douceur.
	inPercent : avancement, de 0 à 100
	inColor   : couleur de la part remplie ; non précisée : encre du thème
	inModifier : place de la barre
	inLabel    : texte à droite de la barre ; null : le pourcentage rempli
*/
@Composable
private fun ProgressBar(
	inPercent  : Int,
	inColor    : Color    = Color.Unspecified,
	inModifier : Modifier = Modifier,
	inLabel    : String?  = null
)
{
	val vFill by animateFloatAsState(inPercent / 100f, tween(kBarMillis), label = "avancement")	// part remplie
	Row(inModifier, verticalAlignment = Alignment.CenterVertically) {
		Box(
			Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(glassWell(MaterialTheme.colorScheme.surfaceContainerHighest))
				.semantics { contentDescription = "Avancement $inPercent %" }
		) {
			Box(Modifier.fillMaxWidth(vFill).height(8.dp).clip(RoundedCornerShape(4.dp)).background(inColor.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface))
		}
		Text(inLabel ?: "$inPercent %", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(if (inLabel != null) 110.dp else 56.dp).padding(start = 12.dp))
	}
}

/*
	Pastille d'état : pleine pour un objectif atteint ou fait, bordée sinon.
	inStatus : état
*/
@Composable
private fun StatusPill(inStatus: GoalStatus)
{
	val vColors = MaterialTheme.colorScheme											// palette courante
	val vFull   = inStatus == GoalStatus.REACHED || inStatus == GoalStatus.DONE		// pastille pleine
	val vText   = when (inStatus)
	{
		GoalStatus.REACHED  -> "Atteint"
		GoalStatus.ON_TRACK -> "En bonne voie"
		GoalStatus.BEHIND   -> "En retard"
		GoalStatus.OVERDUE  -> "Échéance dépassée"
		GoalStatus.OPEN     -> "En cours"
		GoalStatus.DONE     -> "Fait"
		GoalStatus.TODO     -> "À faire"
	}																					// libellé
	Text(
		text     = vText,
		style    = MaterialTheme.typography.labelMedium,
		color    = if (vFull) vColors.onPrimary else if (inStatus == GoalStatus.BEHIND || inStatus == GoalStatus.OVERDUE) vColors.onSurface else vColors.onSurfaceVariant,
		modifier = Modifier
			.clip(RoundedCornerShape(50))
			.background(if (vFull) vColors.primary else glassRaised(vColors.surface))
			.border(1.dp, if (vFull) vColors.primary else if (inStatus == GoalStatus.BEHIND || inStatus == GoalStatus.OVERDUE) vColors.onSurface else vColors.outlineVariant, RoundedCornerShape(50))
			.padding(horizontal = 10.dp, vertical = 3.dp)
	)
}

/*
	Panneau de saisie d'un objectif : nature, intitulé, cible, compte ou type
	suivi, montant atteint, échéance, note ; suppression d'un objectif
	existant.
	inDraft  : saisie en cours
	inDirty  : saisie modifiée depuis l'ouverture
	inSpace  : données (comptes)
	inChange : saisie modifiée
	inClose  : fermeture sans enregistrer
	inSave   : objectif valide à enregistrer
	inDelete : suppression de l'objectif (identifiant)
*/
@Composable
private fun Editor(
	inDraft  : Draft,
	inDirty  : Boolean,
	inSpace  : WorkspaceState,
	inChange : (Draft) -> Unit,
	inClose  : () -> Unit,
	inSave   : (Goal) -> Unit,
	inDelete : (String) -> Unit
)
{
	var vError  by remember(inDraft.id) { mutableStateOf<String?>(null) }				// refus de l'enregistrement
	val vSubmit = { goalOf(inDraft, inSpace).fold({ inSave(it) }, { vError = it.message }) }	// enregistre si la saisie est valide
	val vRate   = inDraft.kind == GoalKind.RATE											// cible en %
	SidePanel(
		inTitle   = if (inDraft.id == null) "Nouvel objectif" else "Modifier l’objectif",
		inOnClose = inClose,
		inDirty   = inDirty,
		inOnEnter = { vSubmit() },
		inActions = {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
				Button(onClick = { vSubmit() }) { Text("Enregistrer") }
				TextButton(onClick = inClose) { Text("Annuler") }
				Spacer(Modifier.weight(1f))
				inDraft.id?.let { inId -> TextButton(onClick = { inDelete(inId) }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } }
			}
		}
	) {
		FormError(vError)
		Picker("Nature", GoalKind.entries.map { it to it.label }, inDraft.kind, { inChange(inDraft.copy(kind = it)) })
		FormField(inDraft.title, { inChange(inDraft.copy(title = it)) }, "Intitulé", inSupport = "Par exemple « Apport pour un achat »")
		when (inDraft.kind)
		{
			GoalKind.ACCOUNT -> Picker("Compte suivi", inSpace.data.accounts.filter { !it.archived || it.id == inDraft.accountId }.map { it.id to accountLabel(it) }, inDraft.accountId, { inChange(inDraft.copy(accountId = it)) })
			GoalKind.TYPE    -> Choice(AccountType.entries.map { it to it.label }, inDraft.accountType, { inChange(inDraft.copy(accountType = it)) })
			else             -> Unit
		}
		if (inDraft.kind != GoalKind.TASK)
		{
			FormField(
				inValue    = inDraft.target,
				inOnChange = { inChange(inDraft.copy(target = it)) },
				inLabel    = when (inDraft.kind) { GoalKind.MONTHLY -> "Cible par mois (€)"; GoalKind.RATE -> "Taux d’épargne visé (%)"; else -> "Cible (€)" },
				inSupport  = when (inDraft.kind)
				{
					GoalKind.MONTHLY -> "Comparée à la moyenne des virements vers l’épargne et la bourse"
					GoalKind.RATE    -> "(revenus − dépenses) / revenus, moyenne des mois complets"
					GoalKind.ACCOUNT -> "Valeur entière du compte (plafond d’un livret)"
					GoalKind.WEALTH  -> "Patrimoine compté, part détenue appliquée"
					else             -> null
				},
				inNumeric  = true
			)
		}
		if (inDraft.kind == GoalKind.MANUAL)
		{
			FormField(inDraft.current, { inChange(inDraft.copy(current = it)) }, "Déjà atteint (€)", inSupport = "À mettre à jour de temps en temps", inNumeric = true)
		}
		FormField(inDraft.deadline, { inChange(inDraft.copy(deadline = it)) }, "Échéance (facultative)", inSupport = "JJ/MM/AAAA" + if (vRate || inDraft.kind == GoalKind.MONTHLY) "" else " ; donne l’effort mensuel nécessaire")
		FormField(inDraft.note, { inChange(inDraft.copy(note = it)) }, "Note (facultative)")
	}
}

/*
	Saisie reprise d'un objectif existant.
	inGoal : objectif
*/
private fun draftOf(inGoal: Goal): Draft =
	Draft(
		id          = inGoal.id,
		kind        = inGoal.kind,
		title       = inGoal.title,
		target      = when { inGoal.kind == GoalKind.RATE -> rateInput(inGoal.target); inGoal.target > 0 -> editableEuros(inGoal.target); else -> "" },
		accountId   = inGoal.accountId,
		accountType = inGoal.accountType ?: AccountType.SAVINGS,
		current     = if (inGoal.current > 0) editableEuros(inGoal.current) else "",
		deadline    = inGoal.due?.let { formatFrenchDate(it) } ?: "",
		note        = inGoal.note,
		done        = inGoal.done
	)

/*
	Objectif tiré d'une saisie, ou l'erreur qui empêche de l'enregistrer.
	inDraft : saisie
	inSpace : données (comptes)
*/
private fun goalOf(
	inDraft : Draft,
	inSpace : WorkspaceState
): Result<Goal>
{
	val vFail   = { inMessage: String -> Result.failure<Goal>(IllegalArgumentException(inMessage)) }	// refus
	val vTitle  = inDraft.title.trim()														// intitulé
	if (vTitle.isEmpty()) return vFail("Donnez un intitulé à l’objectif.")
	val vTarget = when (inDraft.kind)
	{
		GoalKind.TASK -> 0L
		GoalKind.RATE -> inDraft.target.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 100 }?.let { (it * 10).roundToLong() } ?: return vFail("Taux visé illisible : un pourcentage entre 0 et 100.")
		else          -> parseEuros(inDraft.target)?.takeIf { it > 0 } ?: return vFail("Cible illisible : un montant positif en euros.")
	}																						// cible
	if (inDraft.kind == GoalKind.ACCOUNT && inSpace.data.accounts.none { it.id == inDraft.accountId }) return vFail("Choisissez le compte suivi.")
	val vCurrent = if (inDraft.kind == GoalKind.MANUAL && inDraft.current.isNotBlank()) parseEuros(inDraft.current)?.takeIf { it >= 0 } ?: return vFail("Montant déjà atteint illisible.") else 0L	// montant atteint
	val vDue     = if (inDraft.deadline.isBlank()) null else parseFrenchDate(inDraft.deadline) ?: return vFail("Échéance illisible : JJ/MM/AAAA.")	// échéance
	return Result.success(Goal(
		id          = inDraft.id ?: UUID.randomUUID().toString(),
		kind        = inDraft.kind,
		title       = vTitle,
		target      = vTarget,
		accountId   = inDraft.accountId.takeIf { inDraft.kind == GoalKind.ACCOUNT },
		accountType = inDraft.accountType.takeIf { inDraft.kind == GoalKind.TYPE },
		current     = vCurrent,
		deadline    = vDue?.toString(),
		done        = inDraft.done && inDraft.kind == GoalKind.TASK,
		note        = inDraft.note.trim()
	))
}

/*
	Taux en pour mille écrit en % (« 20,5 % »).
	inPermille : taux, en pour mille
*/
private fun rateText(inPermille: Long): String = (if (inPermille < 0) "-" else "") + "${abs(inPermille) / 10},${abs(inPermille) % 10} %"

/*
	Taux en pour mille écrit pour un champ (« 20 », « 12,5 »).
	inPermille : taux, en pour mille
*/
private fun rateInput(inPermille: Long): String = if (inPermille % 10 == 0L) (inPermille / 10).toString() else "${inPermille / 10},${abs(inPermille) % 10}"

/*
	Couleur d'un objectif dans le thème Coloré : celle du compte ou du type
	visé, menthe pour l'épargne mensuelle, ambre pour le taux d'épargne,
	corail pour un montant saisi, couleur principale sinon.
	inGoal  : objectif
	inSpace : données (type du compte visé)
*/
@Composable
private fun goalAccent(
	inGoal  : Goal,
	inSpace : WorkspaceState
): Color =
	when (inGoal.kind)
	{
		GoalKind.ACCOUNT -> inSpace.data.accounts.firstOrNull { it.id == inGoal.accountId }?.let { accountAccent(it.type) } ?: flowAccent(FlowAccent.MAIN)
		GoalKind.TYPE    -> inGoal.accountType?.let { accountAccent(it) } ?: flowAccent(FlowAccent.MAIN)
		GoalKind.MONTHLY -> flowAccent(FlowAccent.INFLOW)
		GoalKind.RATE    -> flowAccent(FlowAccent.INVEST)
		GoalKind.MANUAL  -> flowAccent(FlowAccent.OUTFLOW)
		else             -> flowAccent(FlowAccent.MAIN)
	}

/*
	Section des objectifs du mois : mois choisi (mois en cours par défaut,
	navigation dans l'historique), ses objectifs fixés depuis le budget et
	leur suivi sur les opérations, puis l'historique des mois précédents
	(réussis sur fixés), un clic ouvrant un mois.
	inGoals      : objectifs (mois fixés)
	inSpace      : données
	inController : contrôleur
*/
@Composable
private fun MonthSection(
	inGoals      : Goals,
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vBudget by inController.budget.collectAsState()									// budget (comptes suivis en investissement)
	val vToday  = YearMonth.from(inSpace.today)											// mois en cours
	var vMonth  by remember { mutableStateOf(vToday) }									// mois affiché
	val vFirst  = inGoals.months.firstOrNull()?.yearMonth?.coerceAtMost(vToday) ?: vToday	// premier mois fixé
	val vLast   = listOfNotNull(vToday, inGoals.months.lastOrNull()?.yearMonth).max()		// dernier mois (le suivant peut être fixé d'avance)
	val vPlan   = inGoals.plan(vMonth)													// objectifs du mois affiché
	val vViews  = remember(vPlan, inSpace.data, vBudget, inSpace.today) { vPlan?.let { evaluateMonth(it, inSpace.data, vBudget, inSpace.today) }.orEmpty() }	// objectifs évalués
	Row(verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.weight(1f)) { SectionHeader(inTitle = "Objectifs du mois", inModifier = Modifier.tourAnchor("goals.month")) }
		Box(Modifier.width(300.dp)) { MonthSwitcher(vMonth, vFirst, vLast, { vMonth = it }) }
	}
	if (vPlan == null)
	{
		Tile {
			Hint(if (vMonth.isBefore(vToday)) "Aucun objectif n’avait été fixé pour ${monthTitle(vMonth).lowercase()}." else "Pas encore d’objectifs pour ${monthTitle(vMonth).lowercase()} : fixez-les depuis votre budget.")
			if (!vMonth.isBefore(vToday))
			{
				Spacer(Modifier.height(8.dp))
				Button(onClick = { inController.show(Screen.BUDGET) }) { Text("Aller au budget") }
			}
		}
	}
	else
	{
		Text("${vViews.count { it.success }} objectif(s) réussi(s) sur ${vViews.size}" + if (vMonth == vToday) " pour l’instant." else ".", style = MaterialTheme.typography.titleMedium)
		TileGrid(vViews, 320.dp) { inView, inMod -> MonthGoalTile(inView, inMod) }
	}
	val vPast = inGoals.months.filter { it.yearMonth.isBefore(vToday) }.reversed()		// mois écoulés, du plus récent
	if (vPast.isNotEmpty())
	{
		Tile(inTitle = "Historique") {
			vPast.forEach { inPast ->
				val vResult = remember(inPast, inSpace.data, vBudget, inSpace.today) { evaluateMonth(inPast, inSpace.data, vBudget, inSpace.today) }	// bilan du mois
				Row(
					modifier          = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Voir le mois") { vMonth = inPast.yearMonth }.padding(horizontal = 8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(monthTitle(inPast.yearMonth), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
					Text("${vResult.count { it.success }} / ${vResult.size} réussis", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					Spacer(Modifier.width(16.dp))
					ProgressBar(if (vResult.isEmpty()) 0 else vResult.count { it.success } * 100 / vResult.size, flowAccent(FlowAccent.INFLOW), Modifier.width(220.dp))
				}
			}
		}
	}
}

/*
	Tuile d'un objectif du mois : thème (ou épargne) et état. Dépenses : ce
	qu'il reste à dépenser dans le thème (ou le dépassement), barre
	d'utilisation qui se remplit au fil des dépenses avec la part restante
	écrite à côté, puis le montant dépensé.
	Épargne : mis de côté sur la cible, barre, puis ce qu'il reste à mettre
	de côté.
	inView : objectif évalué
	inMod  : place de la tuile
*/
@Composable
private fun MonthGoalTile(
	inView : MonthGoalView,
	inMod  : Modifier
)
{
	val vGoal   = inView.goal																// objectif
	val vSpend  = vGoal.kind == MonthGoalKind.SPENDING									// dépense maximale
	val vHidden = LocalAmountsHidden.current												// mode discret
	val vMoney  = { inCents: Long -> if (vHidden) kMaskedAmount else formatEuros(inCents) }	// montant en texte
	val vAccent = flowAccent(if (vSpend) FlowAccent.OUTFLOW else FlowAccent.INVEST)		// couleur (thème Coloré)
	Tile(inModifier = inMod, inTitle = vGoal.label, inAccent = vAccent, inAction = { MonthPill(inView.status) }) {
		val vLeft = vGoal.target - inView.actual												// reste à dépenser (négatif : dépassement)
		Row(verticalAlignment = Alignment.Bottom) {
			AmountText(inCents = if (vSpend) vLeft else inView.actual, inStyle = MaterialTheme.typography.headlineSmall, inColor = if (vSpend && vLeft < 0) MaterialTheme.colorScheme.error else Color.Unspecified)
			Text(
				text  = when
				{
					vSpend && vLeft >= 0 -> "  restants sur ${vMoney(vGoal.target)}"
					vSpend               -> "  au-delà des ${vMoney(vGoal.target)} prévus"
					else                 -> "  sur ${vMoney(vGoal.target)} au moins"
				},
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		Spacer(Modifier.height(10.dp))
		ProgressBar(
			inView.percent.coerceAtMost(100),
			if (inView.status == MonthGoalStatus.EXCEEDED || inView.status == MonthGoalStatus.MISSED) MaterialTheme.colorScheme.error else vAccent,
			inLabel = if (vSpend) "${(100 - inView.percent).coerceAtLeast(0)} % restant" else null
		)																					// dépenses : barre d'utilisation, part restante écrite ; épargne : part atteinte
		Spacer(Modifier.height(6.dp))
		Hint(
			when
			{
				vSpend                        -> "Dépensé : ${vMoney(inView.actual)}."
				inView.actual >= vGoal.target -> "Objectif atteint."
				else                          -> "Encore ${vMoney(vGoal.target - inView.actual)} à mettre de côté."
			}
		)
	}
}

/*
	Pastille d'état d'un objectif du mois : pleine pour un objectif réussi,
	couleur d'erreur pour un dépassement ou un manque, bordée sinon.
	inStatus : état
*/
@Composable
private fun MonthPill(inStatus: MonthGoalStatus)
{
	val vColors = MaterialTheme.colorScheme											// palette courante
	val vGood   = inStatus == MonthGoalStatus.KEPT || inStatus == MonthGoalStatus.REACHED	// réussi
	val vBad    = inStatus == MonthGoalStatus.EXCEEDED || inStatus == MonthGoalStatus.MISSED	// raté
	Text(
		text     = inStatus.label,
		style    = MaterialTheme.typography.labelMedium,
		color    = if (vGood) vColors.onPrimary else if (vBad) vColors.error else vColors.onSurfaceVariant,
		modifier = Modifier
			.clip(RoundedCornerShape(50))
			.background(if (vGood) vColors.primary else glassRaised(vColors.surface))
			.border(1.dp, if (vGood) vColors.primary else if (vBad) vColors.error else vColors.outlineVariant, RoundedCornerShape(50))
			.padding(horizontal = 10.dp, vertical = 3.dp)
	)
}
