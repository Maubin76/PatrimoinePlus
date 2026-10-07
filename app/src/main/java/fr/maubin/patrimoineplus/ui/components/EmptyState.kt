package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
	État vide d'une section : ce qui manque, pourquoi, et l'action pour y remédier.
	inTitle       : ce qui manque, en une phrase courte
	inBody        : explication
	inActionLabel : libellé de l'action ; null pour ne pas en afficher
	inOnAction    : action déclenchée par le bouton
	inModifier    : modificateur appliqué
*/
@Composable
fun EmptyState(
	inTitle       : String,
	inBody        : String,
	inActionLabel : String?     = null,
	inOnAction    : () -> Unit  = {},
	inModifier    : Modifier    = Modifier
)
{
	Column(
		modifier            = inModifier.padding(vertical = 8.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp)
	) {
		Text(inTitle, style = MaterialTheme.typography.titleMedium)
		Text(
			text  = inBody,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		if (inActionLabel != null)
		{
			Spacer(Modifier.height(10.dp))
			OutlinedButton(onClick = inOnAction) { Text(inActionLabel) }
		}
	}
}
