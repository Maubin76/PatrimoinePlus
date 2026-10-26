package fr.maubin.patrimoineplus.ui.shell

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.ui.graphics.Color

// Hauteur laissée libre en bas du contenu pour la pilule de navigation (et le bouton de saisie).
val LocalBottomClearance = staticCompositionLocalOf { 0.dp }

// Marge horizontale standard des contenus d'écran.
val kScreenMargin: Dp = 20.dp

/*
	Gabarit commun des écrans, sur le fond de l'appli : grand titre à gauche
	qui se replie en barre quand le contenu défile (la barre repliée prend
	le fond, avec un filet). Les marges transmises au contenu incluent la
	place de la pilule flottante.
	inTitle   : titre de l'écran
	inActions : actions de la barre du haut
	inContent : contenu, qui reçoit les marges à respecter
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
	inTitle   : String,
	inActions : @Composable RowScope.() -> Unit = {},
	inContent : @Composable (PaddingValues) -> Unit
)
{
	val vScroll    = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())	// grand titre replié au défilement
	val vScrolled  = vScroll.state.collapsedFraction > 0.99f				// titre replié : le contenu passe sous la barre
	val vBar       = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)	// barre repliée, sur le fond
	val vClearance = LocalBottomClearance.current							// place de la pilule
	val vDirection = LocalLayoutDirection.current							// sens de lecture
	Scaffold(
		modifier       = Modifier.nestedScroll(vScroll.nestedScrollConnection),
		containerColor = Color.Transparent,
		topBar         = {
			Column {
				LargeTopAppBar(
					title          = { Text(inTitle) },
					actions        = inActions,
					scrollBehavior = vScroll,
					colors         = TopAppBarDefaults.topAppBarColors(
						containerColor         = Color.Transparent,
						scrolledContainerColor = vBar
					)
				)
				if (vScrolled)
				{
					HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
				}
			}
		}
	) { inPadding ->
		inContent(
			PaddingValues(
				start  = inPadding.calculateStartPadding(vDirection),
				top    = inPadding.calculateTopPadding(),
				end    = inPadding.calculateEndPadding(vDirection),
				bottom = inPadding.calculateBottomPadding() + vClearance
			)
		)
	}
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
