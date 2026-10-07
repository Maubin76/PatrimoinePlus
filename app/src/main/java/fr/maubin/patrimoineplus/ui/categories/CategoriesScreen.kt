package fr.maubin.patrimoineplus.ui.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold

/*
	Onglet des catégories. La gestion arrive à l'étape 3 ; l'écran montre
	son état vide réel.
*/
@Composable
fun CategoriesScreen()
{
	val vSnackbar = remember { SnackbarHostState() }						// messages éphémères
	ScreenScaffold(inTitle = "Catégories", inSnackbar = vSnackbar) { inPadding ->
		Column(Modifier.padding(inPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
			EmptyState(
				inTitle = "Aucune catégorie",
				inBody  = "Les catégories de dépenses et de revenus se géreront ici (étape 3). " +
					"Une liste de départ sera proposée au premier lancement."
			)
		}
	}
}
