package fr.maubin.patrimoineplus.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.shell.EntryFab
import fr.maubin.patrimoineplus.ui.shell.PrivacyAction
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold

/*
	Onglet des comptes. La gestion (création, ordre, archivage) arrive à
	l'étape 3 ; l'écran montre son état vide réel.
	inOnToggleHidden : bascule le mode discret
*/
@Composable
fun AccountsScreen(inOnToggleHidden: () -> Unit)
{
	val vSnackbar = remember { SnackbarHostState() }						// messages éphémères
	ScreenScaffold(
		inTitle    = "Comptes",
		inSnackbar = vSnackbar,
		inActions  = { PrivacyAction(inOnToggleHidden) },
		inFab      = { EntryFab(vSnackbar) }
	) { inPadding ->
		Column(Modifier.padding(inPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
			EmptyState(
				inTitle = "Aucun compte",
				inBody  = "Comptes courants, livrets et compte-titres se créeront ici, " +
					"avec leur ordre d'affichage et leur archivage (étape 3)."
			)
		}
	}
}
