package fr.maubin.patrimoineplus.ui.shell

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import kotlinx.coroutines.launch

/*
	Gabarit commun des écrans principaux : barre du haut sur le fond de l'écran,
	un filet apparaît sous elle dès que le contenu défile (pas d'ombre ni de teinte).
	inTitle    : titre de l'écran
	inSnackbar : hôte des messages éphémères
	inActions  : actions de la barre du haut
	inFab      : bouton flottant ; vide si l'écran n'en a pas
	inContent  : contenu, qui reçoit les marges à respecter
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
	inTitle    : String,
	inSnackbar : SnackbarHostState,
	inActions  : @Composable RowScope.() -> Unit = {},
	inFab      : @Composable () -> Unit           = {},
	inContent  : @Composable (PaddingValues) -> Unit
)
{
	val vScroll   = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())	// suivi du défilement
	val vScrolled = vScroll.state.overlappedFraction > 0f					// le contenu passe sous la barre
	val vSurface  = MaterialTheme.colorScheme.surface						// fond de l'écran
	Scaffold(
		modifier             = Modifier.nestedScroll(vScroll.nestedScrollConnection),
		containerColor       = vSurface,
		topBar               = {
			Column {
				TopAppBar(
					title          = { Text(inTitle, style = MaterialTheme.typography.titleLarge) },
					actions        = inActions,
					scrollBehavior = vScroll,
					colors         = TopAppBarDefaults.topAppBarColors(
						containerColor         = vSurface,
						scrolledContainerColor = vSurface
					)
				)
				if (vScrolled)
				{
					HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
				}
			}
		},
		floatingActionButton = inFab,
		snackbarHost         = { SnackbarHost(inSnackbar) },
		content              = inContent
	)
}

/*
	Action « œil » de la barre du haut : masque ou affiche les montants.
	inOnToggle : bascule le mode discret
*/
@Composable
fun PrivacyAction(inOnToggle: () -> Unit)
{
	val vHidden = LocalAmountsHidden.current								// mode discret actif
	IconButton(onClick = inOnToggle) {
		Crossfade(targetState = vHidden, label = "œil") { inHidden ->
			Icon(
				imageVector        = if (inHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
				contentDescription = if (inHidden) "Afficher les montants" else "Masquer les montants"
			)
		}
	}
}

/*
	Bouton de saisie : la seule touche jaune de l'écran, ronde comme celles
	de la calculatrice, sans ombre. La saisie arrive à l'étape 4 ; d'ici là, il le dit.
	inSnackbar : hôte où afficher le message provisoire
*/
@Composable
fun EntryFab(inSnackbar: SnackbarHostState)
{
	val vScope = rememberCoroutineScope()									// portée du message
	FloatingActionButton(
		onClick        = { vScope.launch { inSnackbar.showSnackbar("La saisie des opérations arrive à l'étape 4.") } },
		shape          = CircleShape,
		containerColor = MaterialTheme.colorScheme.primaryContainer,
		contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
		elevation      = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
	) {
		Icon(Icons.Rounded.Add, contentDescription = "Nouvelle opération")
	}
}
