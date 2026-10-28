package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.sync.MergeConflict
import fr.maubin.patrimoineplus.data.sync.MergeSide

/*
	Fenêtre des éléments en conflit après une synchronisation : tout le reste
	est déjà fusionné ; pour chaque élément modifié des deux côtés (ou
	supprimé d'un côté et modifié de l'autre), les deux versions sont
	montrées et l'utilisateur choisit celle à garder. « Appliquer » n'est
	possible qu'une fois chaque élément tranché ; deux raccourcis gardent tout
	d'un côté. Sans fermeture possible : rien n'est écrit tant que le choix
	n'est pas fait.
	inConflicts   : éléments en conflit
	inMineLabel   : cet appareil, en clair (« Ce PC », « Ce téléphone »)
	inTheirsLabel : l'autre côté, en clair (« Le téléphone », « Le fichier synchronisé »)
	inOnResolve   : choix faits, par clé d'élément
*/
@Composable
fun MergeConflictsDialog(
	inConflicts   : List<MergeConflict>,
	inMineLabel   : String,
	inTheirsLabel : String,
	inOnResolve   : (Map<String, MergeSide>) -> Unit
)
{
	val vChoices = remember(inConflicts) { mutableStateMapOf<String, MergeSide>() }	// choix faits
	AlertDialog(
		onDismissRequest = {},
		title            = { Text(if (inConflicts.size == 1) "Un élément modifié des deux côtés" else "${inConflicts.size} éléments modifiés des deux côtés") },
		text             = {
			Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
				Text("Les autres changements ont été fusionnés. Choisissez la version à garder pour chacun de ces éléments ; l’autre reste dans les sauvegardes.", style = MaterialTheme.typography.bodyMedium)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(onClick = { inConflicts.forEach { vChoices[it.key] = MergeSide.MINE } }) { Text("Tout garder : $inMineLabel") }
					TextButton(onClick = { inConflicts.forEach { vChoices[it.key] = MergeSide.THEIRS } }) { Text("Tout garder : $inTheirsLabel") }
				}
				inConflicts.forEach { inConflict ->
					Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
						Text("${inConflict.kind} « ${inConflict.title} »", style = MaterialTheme.typography.titleSmall)
						Version(inMineLabel, inConflict.mine, vChoices[inConflict.key] == MergeSide.MINE) { vChoices[inConflict.key] = MergeSide.MINE }
						Version(inTheirsLabel, inConflict.theirs, vChoices[inConflict.key] == MergeSide.THEIRS) { vChoices[inConflict.key] = MergeSide.THEIRS }
					}
				}
			}
		},
		confirmButton    = {
			Button(onClick = { inOnResolve(vChoices.toMap()) }, enabled = inConflicts.all { it.key in vChoices }) { Text("Appliquer") }
		}
	)
}

/*
	Une version d'un élément en conflit, à choisir.
	inSide     : côté, en clair
	inText     : version, en clair ; null si l'élément y a été supprimé
	inSelected : version choisie
	inOnClick  : choix
*/
@Composable
private fun Version(
	inSide     : String,
	inText     : String?,
	inSelected : Boolean,
	inOnClick  : () -> Unit
)
{
	val vShape = RoundedCornerShape(14.dp)												// cadre
	Row(
		modifier          = Modifier
			.fillMaxWidth()
			.clip(vShape)
			.border(1.dp, if (inSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, vShape)
			.selectable(selected = inSelected, onClick = inOnClick, role = Role.RadioButton)
			.padding(horizontal = 8.dp, vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		RadioButton(selected = inSelected, onClick = null)
		Column(Modifier.padding(start = 6.dp)) {
			Text(inSide, style = MaterialTheme.typography.labelLarge)
			Text(inText ?: "Supprimé", style = MaterialTheme.typography.bodyMedium, color = if (inText == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}
}
