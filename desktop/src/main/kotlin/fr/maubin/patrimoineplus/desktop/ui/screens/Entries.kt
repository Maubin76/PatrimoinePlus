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
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
		verticalArrangement = Arrangement.spacedBy(32.dp)
	) {
		Column {
			Text("Opérations", style = MaterialTheme.typography.displaySmall)
		}
		BatchSection(inSpace, inController)
		RecurringSection(inSpace, inController)
	}
}
