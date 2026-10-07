package fr.maubin.patrimoineplus.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.shell.PrivacyAction
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin

/*
	Onglet des comptes. La gestion (création, ordre, archivage) arrive à
	l'étape 3 ; l'écran montre son état vide réel.
	inOnToggleHidden : bascule le mode discret
*/
@Composable
fun AccountsScreen(inOnToggleHidden: () -> Unit)
{
	ScreenScaffold(
		inTitle   = "Comptes",
		inActions = { PrivacyAction(inOnToggleHidden) }
	) { inPadding ->
		Column(Modifier.padding(inPadding).padding(horizontal = kScreenMargin, vertical = 16.dp)) {
			BorderedCard {
				EmptyState(
					inTitle = "Aucun compte",
					inBody  = "Comptes courants, livrets et compte-titres se créeront ici, " +
						"avec leur ordre d’affichage et leur archivage (étape 3)."
				)
			}
		}
	}
}
