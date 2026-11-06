package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.tour.tourAnchor
import androidx.compose.foundation.layout.Box
import fr.maubin.patrimoineplus.desktop.ui.LocalAssistantRoom
import fr.maubin.patrimoineplus.desktop.ui.components.PageHeader
import fr.maubin.patrimoineplus.ui.components.LemonButton
import fr.maubin.patrimoineplus.ui.components.GhostPill
import fr.maubin.patrimoineplus.ui.icons.LineIcons

/*
	Écran Opérations : saisie rapide en grille, puis opérations récurrentes et
	mensualités des crédits, sur une même page.
	inSpace      : données
	inController : contrôleur
*/
@Composable
fun EntriesScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(top = 6.dp, bottom = 28.dp).padding(bottom = LocalAssistantRoom.current),
		verticalArrangement = Arrangement.spacedBy(32.dp)
	) {
		PageHeader(inTitle = "Opérations", inSubtitle = "Saisie rapide en grille, opérations récurrentes et mensualités", inPrimary = { LemonButton("Nouvelle opération", inController::newOperation, inIcon = LineIcons.Plus) })
		Box(Modifier.tourAnchor("ops.batch")) { BatchSection(inSpace, inController) }
		Box(Modifier.tourAnchor("ops.recurring")) { RecurringSection(inSpace, inController) }
	}
}
