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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.navigation.TopLevelDestination
import kotlinx.coroutines.launch

private val kPillClearance  = 96.dp										// pilule (64) + marges (12 + 20)
private val kEntryClearance = 72.dp										// bouton de saisie (56) + écart (16)

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
	sombre, où l'ombre ne se voit pas), icônes seules ;
	l'onglet actif est posé dans une pastille grise et passe en icône pleine.
	Chaque onglet est annoncé par son nom à TalkBack.
	inSelected : onglet courant
	inOnSelect : ouvre un onglet
*/
@Composable
fun FloatingNavBar(
	inSelected : TopLevelDestination?,
	inOnSelect : (TopLevelDestination) -> Unit
)
{
	val vDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f			// mode sombre : l'ombre ne se voit pas
	Surface(
		shape           = CircleShape,
		color           = MaterialTheme.colorScheme.surfaceBright,
		shadowElevation = 10.dp,
		border          = if (vDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
	) {
		Row(
			modifier              = Modifier.padding(8.dp).selectableGroup(),
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			TopLevelDestination.entries.forEach { inDestination ->
				val vSelected = inDestination == inSelected					// onglet actif
				Box(
					modifier         = Modifier
						.size(width = 68.dp, height = 48.dp)
						.clip(CircleShape)
						.background(if (vSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceBright)
						.selectable(selected = vSelected, role = Role.Tab, onClick = { inOnSelect(inDestination) })
						.semantics { contentDescription = inDestination.label },
					contentAlignment = Alignment.Center
				) {
					Icon(
						imageVector        = if (vSelected) inDestination.selectedIcon else inDestination.icon,
						contentDescription = null,
						tint               = MaterialTheme.colorScheme.onSurface
					)
				}
			}
		}
	}
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
