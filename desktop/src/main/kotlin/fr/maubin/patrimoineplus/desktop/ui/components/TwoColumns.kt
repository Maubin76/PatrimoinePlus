package fr.maubin.patrimoineplus.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val kTwoColumnsMinWidth = 860.dp													// largeur sous laquelle les colonnes s'empilent

/*
	Deux colonnes côte à côte quand la place le permet ; empilées sinon
	(panneau de saisie ouvert, fenêtre étroite).
	inRatio : largeur relative de la colonne de droite
	inLeft  : colonne de gauche
	inRight : colonne de droite
*/
@Composable
fun TwoColumns(
	inRatio : Float = 1.2f,
	inLeft  : @Composable ColumnScope.() -> Unit,
	inRight : @Composable ColumnScope.() -> Unit
)
{
	BoxWithConstraints {
		if (maxWidth >= kTwoColumnsMinWidth)
		{
			Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
				Column(Modifier.weight(1f), content = inLeft)
				Column(Modifier.weight(inRatio), content = inRight)
			}
		}
		else
		{
			Column {
				inLeft()
				Spacer(Modifier.height(36.dp))
				inRight()
			}
		}
	}
}
