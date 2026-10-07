package fr.maubin.patrimoineplus.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.navigation.TopLevelDestination
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val kPillClearance  = 96.dp										// pilule (64) + marges (12 + 20)
private val kEntryClearance = 72.dp										// bouton de saisie (56) + écart (16)
private val kTabCollapsed   = 48.dp										// largeur d'un onglet au repos
private val kPadCollapsed   = 12.dp										// marge de l'icône au repos
private val kPadExpanded    = 16.dp										// marge de l'onglet actif
private val kTabSpring      = spring<Float>(							// ressort du changement d'onglet, sans rebond
	dampingRatio = Spring.DampingRatioNoBouncy,
	stiffness    = Spring.StiffnessMediumLow
)

/*
	Cadre de l'application : le contenu occupe tout l'écran ; par-dessus, en
	bas, la pilule de navigation flottante, le bouton de saisie au-dessus à
	droite (onglets qui le prévoient) et les messages éphémères.
	inSelected : onglet courant ; null pendant une transition
	inOnSelect : ouvre un onglet
	inSnackbar : hôte des messages éphémères
	inContent  : écran courant
*/
@Composable
fun AppFrame(
	inSelected : TopLevelDestination?,
	inOnSelect : (TopLevelDestination) -> Unit,
	inSnackbar : SnackbarHostState,
	inContent  : @Composable () -> Unit
)
{
	val vScope     = rememberCoroutineScope()									// portée des messages
	val vShowEntry = inSelected?.hasEntry == true							// bouton de saisie visible
	val vClearance = kPillClearance + if (vShowEntry) kEntryClearance else 0.dp	// place à laisser au contenu
	Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
		CompositionLocalProvider(LocalBottomClearance provides vClearance) {
			inContent()
		}
		Column(
			modifier            = Modifier
				.align(Alignment.BottomCenter)
				.fillMaxWidth()
				.navigationBarsPadding()
				.padding(bottom = 12.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			SnackbarHost(inSnackbar)
			AnimatedVisibility(
				visible  = vShowEntry,
				enter    = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.8f),
				exit     = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.8f),
				modifier = Modifier.fillMaxWidth()
			) {
				Box(Modifier.fillMaxWidth().padding(end = kScreenMargin, bottom = 16.dp), contentAlignment = Alignment.CenterEnd) {
					EntryFab { vScope.launch { inSnackbar.showSnackbar("La saisie des opérations arrive à l’étape 4.") } }
				}
			}
			FloatingNavBar(inSelected, inOnSelect)
		}
	}
}

/*
	Pilule de navigation flottante : capsule à ombre douce (liseré en mode
	sombre, où l'ombre ne se voit pas), icônes au trait fin. L'onglet actif
	devient une capsule pleine à l'encre qui s'élargit pour afficher son nom ;
	les autres restent des icônes grises. Chaque onglet est annoncé par son
	nom à TalkBack.
	inSelected : onglet courant
	inOnSelect : ouvre un onglet
*/
@Composable
fun FloatingNavBar(
	inSelected : TopLevelDestination?,
	inOnSelect : (TopLevelDestination) -> Unit
)
{
	val vColors = MaterialTheme.colorScheme								// palette courante
	val vDark   = vColors.surface.luminance() < 0.5f						// mode sombre : l'ombre ne se voit pas
	Surface(
		shape           = CircleShape,
		color           = vColors.surfaceBright,
		shadowElevation = 10.dp,
		border          = if (vDark) BorderStroke(1.dp, vColors.outlineVariant) else null
	) {
		Row(
			modifier              = Modifier.padding(6.dp).selectableGroup(),
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			TopLevelDestination.entries.forEach { inDestination ->
				NavTab(inDestination, inDestination == inSelected) { inOnSelect(inDestination) }
			}
		}
	}
}

/*
	Onglet de la pilule. Une seule valeur animée (0 au repos, 1 actif, sur un
	ressort sans rebond) pilote à la fois la largeur, la marge, la couleur et
	l'opacité du nom. Elle n'est lue qu'aux phases de mise en page et de
	dessin : l'animation ne recompose pas l'onglet à chaque image. Au repos,
	icône grise seule, cible de 48 × 52 dp.
	inDestination : onglet représenté
	inSelected    : vrai si l'onglet est actif
	inOnClick     : ouvre l'onglet
*/
@Composable
private fun NavTab(
	inDestination : TopLevelDestination,
	inSelected    : Boolean,
	inOnClick     : () -> Unit
)
{
	val vColors   = MaterialTheme.colorScheme								// palette courante
	val vRest     = vColors.surfaceBright									// fond au repos
	val vActive   = vColors.onSurface										// fond actif
	val vProgress = animateFloatAsState(									// 0 au repos, 1 actif
		targetValue   = if (inSelected) 1f else 0f,
		animationSpec = kTabSpring,
		label         = "onglet"
	)
	Row(
		modifier          = Modifier
			.height(52.dp)
			.clip(CircleShape)
			.drawBehind { drawRect(lerp(vRest, vActive, vProgress.value)) }
			.selectable(selected = inSelected, role = Role.Tab, onClick = inOnClick)
			.semantics { contentDescription = inDestination.label }
			.tabWidth(vProgress),
		verticalAlignment = Alignment.CenterVertically
	) {
		TabIcon(inDestination, vProgress)
		Text(
			text     = inDestination.label,
			style    = MaterialTheme.typography.labelLarge,
			color    = vColors.surface,
			maxLines = 1,
			softWrap = false,
			modifier = Modifier
				.padding(start = 8.dp)
				.graphicsLayer { alpha = vProgress.value }
				.clearAndSetSemantics {}
		)
	}
}

/*
	Icône d'onglet : deux exemplaires superposés (gris au repos, couleur du
	fond quand actif) dont l'opacité se croise, pour animer la teinte sans
	recomposer.
	inDestination : onglet représenté
	inProgress    : avancement de l'animation (0 au repos, 1 actif)
*/
@Composable
private fun TabIcon(
	inDestination : TopLevelDestination,
	inProgress    : State<Float>
)
{
	Box {
		Icon(
			imageVector        = inDestination.icon,
			contentDescription = null,
			tint               = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier           = Modifier.graphicsLayer { alpha = 1f - inProgress.value }
		)
		Icon(
			imageVector        = inDestination.icon,
			contentDescription = null,
			tint               = MaterialTheme.colorScheme.surface,
			modifier           = Modifier.graphicsLayer { alpha = inProgress.value }
		)
	}
}

/*
	Largeur d'un onglet interpolée entre le repos (icône seule, 48 dp) et
	l'état actif (icône et nom) ; le contenu est mesuré une fois à sa largeur
	naturelle puis découpé par la capsule.
	inProgress : avancement de l'animation (0 au repos, 1 actif)
*/
private fun Modifier.tabWidth(inProgress: State<Float>): Modifier =
	layout { inMeasurable, inConstraints ->
		val vPlaceable = inMeasurable.measure(inConstraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))	// contenu entier
		val vP         = inProgress.value										// avancement
		val vPadRest   = kPadCollapsed.toPx()									// marge au repos
		val vPadActive = kPadExpanded.toPx()									// marge active
		val vCollapsed = kTabCollapsed.toPx()									// largeur au repos
		val vExpanded  = vPlaceable.width + 2 * vPadActive						// largeur active
		val vWidth     = (vCollapsed + (vExpanded - vCollapsed) * vP).roundToInt()	// largeur courante
		val vStart     = (vPadRest + (vPadActive - vPadRest) * vP).roundToInt()	// décalage du contenu
		layout(vWidth, vPlaceable.height) { vPlaceable.placeRelative(vStart, 0) }
	}

/*
	Bouton de saisie : rond noir (blanc en mode sombre), sans ombre.
	inOnClick : action du bouton
*/
@Composable
fun EntryFab(inOnClick: () -> Unit)
{
	FloatingActionButton(
		onClick        = inOnClick,
		shape          = CircleShape,
		containerColor = MaterialTheme.colorScheme.primaryContainer,
		contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
		elevation      = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
	) {
		Icon(Icons.Rounded.Add, contentDescription = "Nouvelle opération")
	}
}
