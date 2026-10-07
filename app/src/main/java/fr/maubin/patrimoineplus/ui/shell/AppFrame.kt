package fr.maubin.patrimoineplus.ui.shell

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.abs
import kotlin.math.roundToInt

private val kPillClearance  = 96.dp										// pilule (64) + marges (12 + 20)
private val kEntryClearance = 72.dp										// bouton de saisie (56) + écart (16)
private val kTabCollapsed   = 48.dp										// largeur d'un onglet au repos
private val kPadCollapsed   = 12.dp										// marge de l'icône au repos
private val kPadExpanded    = 16.dp										// marge de l'onglet actif
private val kTabGap         = 8.dp										// écart entre onglets
private val kIconSize       = 24.dp										// taille des icônes d'onglet

/*
	Cadre de l'application : le contenu occupe tout l'écran ; par-dessus, en
	bas, la pilule de navigation flottante, le bouton de saisie au-dessus à
	droite (onglets qui le prévoient) et les messages éphémères.
	inPosition : position continue du pager (page + fraction), lue sans recomposer
	inSelected : onglet courant
	inOnSelect : ouvre un onglet
	inSnackbar : hôte des messages éphémères
	inContent  : écran courant
*/
@Composable
fun AppFrame(
	inPosition : () -> Float,
	inSelected : TopLevelDestination,
	inOnSelect : (TopLevelDestination) -> Unit,
	inSnackbar : SnackbarHostState,
	inContent  : @Composable () -> Unit
)
{
	val vScope     = rememberCoroutineScope()									// portée des messages
	val vShowEntry = inSelected.hasEntry									// bouton de saisie visible
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
			FloatingNavBar(inPosition, inSelected, inOnSelect)
		}
	}
}

/*
	Pilule de navigation flottante : capsule à ombre douce (liseré en mode
	sombre, où l'ombre ne se voit pas), icônes au trait fin. Un indicateur
	unique, capsule pleine à l'encre, glisse sous les onglets en épousant la
	largeur de l'onglet actif, qui s'élargit pour afficher son nom. Tout
	découle de la position du pager, lue aux phases de mise en page et de
	dessin : la capsule suit le doigt, sans recomposition par image. Chaque
	onglet est annoncé par son nom à TalkBack.
	inPosition : position continue du pager (page + fraction)
	inSelected : onglet courant
	inOnSelect : ouvre un onglet
*/
@Composable
fun FloatingNavBar(
	inPosition : () -> Float,
	inSelected : TopLevelDestination,
	inOnSelect : (TopLevelDestination) -> Unit
)
{
	val vColors    = MaterialTheme.colorScheme							// palette courante
	val vDark      = vColors.surface.luminance() < 0.5f					// mode sombre : l'ombre ne se voit pas
	val vIndicator = vColors.onSurface									// couleur de l'indicateur
	val vLeft      = remember { mutableFloatStateOf(0f) }					// bord gauche de l'indicateur
	val vRight     = remember { mutableFloatStateOf(0f) }					// bord droit de l'indicateur
	val vCoverage  = remember { List(TopLevelDestination.entries.size) { mutableFloatStateOf(0f) } }	// part de chaque icône sous l'indicateur
	Surface(
		shape           = CircleShape,
		color           = vColors.surfaceBright,
		shadowElevation = 10.dp,
		border          = if (vDark) BorderStroke(1.dp, vColors.outlineVariant) else null
	) {
		Layout(
			modifier = Modifier
				.padding(6.dp)
				.selectableGroup()
				.drawBehind {
					drawRoundRect(
						color        = vIndicator,
						topLeft      = Offset(vLeft.floatValue, 0f),
						size         = Size(vRight.floatValue - vLeft.floatValue, size.height),
						cornerRadius = CornerRadius(size.height / 2f)
					)
				},
			content  = {
				TopLevelDestination.entries.forEach { inDestination ->
					val vProgress = {											// 1 sur l'onglet, 0 à une page ou plus
						(1f - abs(inPosition() - inDestination.ordinal)).coerceIn(0f, 1f)
					}
					NavTab(
						inDestination = inDestination,
						inSelected    = inDestination == inSelected,
						inProgress    = vProgress,
						inCoverage    = { vCoverage[inDestination.ordinal].floatValue },
						inOnClick     = { inOnSelect(inDestination) }
					)
				}
			}
		) { inMeasurables, inConstraints ->
			val vGap        = kTabGap.roundToPx()								// écart entre onglets
			val vPlaceables = inMeasurables.map { it.measure(inConstraints.copy(minWidth = 0)) }	// onglets mesurés
			val vStarts     = vPlaceables.runningFold(0) { inX, inTab -> inX + inTab.width + vGap }	// abscisses
			val vWidth      = vStarts[vPlaceables.size] - vGap					// largeur totale
			val vHeight     = vPlaceables.maxOf { it.height }					// hauteur commune
			val vPos        = inPosition().coerceIn(0f, (vPlaceables.size - 1).toFloat())	// position du pager
			val vFrom       = vPos.toInt()										// onglet de départ
			val vTo         = minOf(vFrom + 1, vPlaceables.size - 1)			// onglet d'arrivée
			val vFraction   = vPos - vFrom										// avancement entre les deux
			val vFromRight  = (vStarts[vFrom] + vPlaceables[vFrom].width).toFloat()	// bord droit de départ
			val vToRight    = (vStarts[vTo] + vPlaceables[vTo].width).toFloat()		// bord droit d'arrivée
			vLeft.floatValue  = vStarts[vFrom] + (vStarts[vTo] - vStarts[vFrom]) * vFraction
			vRight.floatValue = vFromRight + (vToRight - vFromRight) * vFraction
			val vIcon = kIconSize.toPx()											// largeur d'une icône
			vPlaceables.indices.forEach { inIndex ->
				val vP        = (1f - abs(vPos - inIndex)).coerceIn(0f, 1f)		// avancement de l'onglet
				val vIconLeft = vStarts[inIndex] + kPadCollapsed.toPx() + (kPadExpanded.toPx() - kPadCollapsed.toPx()) * vP	// bord gauche de l'icône
				val vOverlap  = minOf(vRight.floatValue, vIconLeft + vIcon) - maxOf(vLeft.floatValue, vIconLeft)	// largeur recouverte
				vCoverage[inIndex].floatValue = (vOverlap / vIcon).coerceIn(0f, 1f)
			}
			layout(vWidth, vHeight) {
				vPlaceables.forEachIndexed { inIndex, inTab -> inTab.placeRelative(vStarts[inIndex], 0) }
			}
		}
	}
}

/*
	Onglet de la pilule, sans fond propre : l'indicateur glisse dessous. Son
	avancement (0 au repos, 1 actif) pilote sa largeur, sa marge, la teinte
	de l'icône et l'opacité du nom ; le nom n'apparaît qu'au-delà de la
	moitié, pour ne jamais être vu tronqué. Au repos, icône grise seule,
	cible de 48 × 52 dp.
	inDestination : onglet représenté
	inSelected    : vrai si l'onglet est la page courante
	inProgress    : avancement de l'onglet, lu à la demande
	inCoverage    : part de l'icône recouverte par l'indicateur, lue à la demande
	inOnClick     : ouvre l'onglet
*/
@Composable
private fun NavTab(
	inDestination : TopLevelDestination,
	inSelected    : Boolean,
	inProgress    : () -> Float,
	inCoverage    : () -> Float,
	inOnClick     : () -> Unit
)
{
	Row(
		modifier          = Modifier
			.height(52.dp)
			.clip(CircleShape)
			.selectable(selected = inSelected, role = Role.Tab, onClick = inOnClick)
			.semantics { contentDescription = inDestination.label }
			.tabWidth(inProgress),
		verticalAlignment = Alignment.CenterVertically
	) {
		TabIcon(inDestination, inCoverage)
		Text(
			text     = inDestination.label,
			style    = MaterialTheme.typography.labelLarge,
			color    = MaterialTheme.colorScheme.surface,
			maxLines = 1,
			softWrap = false,
			modifier = Modifier
				.padding(start = 8.dp)
				.graphicsLayer { alpha = ((inProgress() - 0.5f) * 2f).coerceIn(0f, 1f) }
				.clearAndSetSemantics {}
		)
	}
}

/*
	Icône d'onglet : deux exemplaires superposés (gris hors de l'indicateur,
	couleur du fond dessous) dont l'opacité suit la part recouverte par
	l'indicateur, pour garder le contraste sans recomposer.
	inDestination : onglet représenté
	inCoverage    : part de l'icône sous l'indicateur (0 à 1), lue à la demande
*/
@Composable
private fun TabIcon(
	inDestination : TopLevelDestination,
	inCoverage    : () -> Float
)
{
	Box {
		Icon(
			imageVector        = inDestination.icon,
			contentDescription = null,
			tint               = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier           = Modifier.graphicsLayer { alpha = 1f - inCoverage() }
		)
		Icon(
			imageVector        = inDestination.icon,
			contentDescription = null,
			tint               = MaterialTheme.colorScheme.surface,
			modifier           = Modifier.graphicsLayer { alpha = inCoverage() }
		)
	}
}

/*
	Largeur d'un onglet interpolée entre le repos (icône seule, 48 dp) et
	l'état actif (icône et nom) ; le contenu est mesuré une fois à sa largeur
	naturelle puis découpé par la capsule.
	inProgress : avancement de l'onglet (0 au repos, 1 actif), lu à la demande
*/
private fun Modifier.tabWidth(inProgress: () -> Float): Modifier =
	layout { inMeasurable, inConstraints ->
		val vPlaceable = inMeasurable.measure(inConstraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))	// contenu entier
		val vP         = inProgress()										// avancement
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
