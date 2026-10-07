package fr.maubin.patrimoineplus.ui.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin

/*
	Onglet des catégories. La gestion arrive à l'étape 3 ; l'écran montre
	son état vide réel.
*/
@Composable
fun CategoriesScreen()
{
	ScreenScaffold(inTitle = "Catégories") { inPadding ->
		Column(Modifier.padding(inPadding).padding(horizontal = kScreenMargin, vertical = 16.dp)) {
			SectionHeader(inTitle = "Dépenses et revenus")
			Spacer(Modifier.height(8.dp))
			BorderedCard {
				EmptyState(
					inTitle = "Aucune catégorie",
					inBody  = "Les catégories de dépenses et de revenus se géreront ici (étape 3). " +
						"Une liste de départ sera proposée au premier lancement."
				)
			}
		}
	}
}
