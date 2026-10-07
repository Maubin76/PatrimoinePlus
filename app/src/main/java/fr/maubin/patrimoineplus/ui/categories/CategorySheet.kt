package fr.maubin.patrimoineplus.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.components.FormSheet
import fr.maubin.patrimoineplus.ui.components.SegmentedChoice
import fr.maubin.patrimoineplus.ui.components.TextInput
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons

/*
	Libellé d'une nature de catégorie.
	inKind : nature
*/
fun kindLabel(inKind: CategoryKind): String = if (inKind == CategoryKind.EXPENSE) "Dépense" else "Revenu"

/*
	Feuille de création ou de modification d'une catégorie : nom, nature (à
	la création seulement), icône ; en modification, archivage et suppression.
	inInitial   : saisie de départ
	inEditing   : catégorie modifiée ; null pour une création
	inOnSave    : enregistre la saisie et renvoie l'erreur par rappel
	inOnArchive : archive ou désarchive la catégorie modifiée
	inOnDelete  : demande la suppression de la catégorie modifiée
	inOnDismiss : fermeture
*/
@Composable
fun CategorySheet(
	inInitial   : CategoryForm,
	inEditing   : Category?,
	inOnSave    : (CategoryForm, (String?) -> Unit) -> Unit,
	inOnArchive : () -> Unit,
	inOnDelete  : () -> Unit,
	inOnDismiss : () -> Unit
)
{
	var vForm  by remember { mutableStateOf(inInitial) }						// saisie en cours
	var vError by remember { mutableStateOf<String?>(null) }					// erreur affichée
	FormSheet(inTitle = if (inEditing == null) "Nouvelle catégorie" else "Modifier la catégorie", inOnDismiss = inOnDismiss) {
		TextInput(vForm.name, { vForm = vForm.copy(name = it) }, "Nom", vError)
		SegmentedChoice(
			inOptions  = CategoryKind.entries,
			inSelected = vForm.kind,
			inLabel    = ::kindLabel,
			inOnSelect = { vForm = vForm.copy(kind = it) },
			inEnabled  = inEditing == null
		)
		Text("Icône", style = MaterialTheme.typography.titleSmall)
		IconGrid(vForm.icon) { vForm = vForm.copy(icon = it) }
		Button(
			onClick        = { inOnSave(vForm) { vError = it } },
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) {
			Text(if (inEditing == null) "Créer la catégorie" else "Enregistrer")
		}
		if (inEditing != null)
		{
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
				TextButton(onClick = inOnArchive) { Text(if (inEditing.archived) "Désarchiver" else "Archiver") }
				TextButton(onClick = inOnDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
			}
		}
	}
}

/*
	Grille de choix d'icône : cercles bordés, l'icône choisie sur fond d'encre.
	inSelected : clé choisie
	inOnSelect : choix d'une clé
*/
@Composable
private fun IconGrid(
	inSelected : String,
	inOnSelect : (String) -> Unit
)
{
	val vColors = MaterialTheme.colorScheme									// palette courante
	FlowRow(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement   = Arrangement.spacedBy(8.dp),
		modifier              = Modifier.fillMaxWidth()
	) {
		CategoryIcons.keys.forEach { inKey ->
			val vChosen = inKey == inSelected									// icône choisie
			Box(
				modifier         = Modifier
					.size(48.dp)
					.clip(CircleShape)
					.background(if (vChosen) vColors.onSurface else vColors.surface)
					.border(BorderStroke(1.dp, if (vChosen) vColors.onSurface else vColors.outlineVariant), CircleShape)
					.clickable(role = Role.RadioButton) { inOnSelect(inKey) }
					.semantics { contentDescription = "Icône ${CategoryIcons.label(inKey)}"; selected = vChosen },
				contentAlignment = Alignment.Center
			) {
				Icon(CategoryIcons.of(inKey), contentDescription = null, tint = if (vChosen) vColors.surface else vColors.onSurface)
			}
		}
	}
}
