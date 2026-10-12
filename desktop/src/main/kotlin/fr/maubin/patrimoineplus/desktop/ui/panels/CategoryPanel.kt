package fr.maubin.patrimoineplus.desktop.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.domain.model.CategoryKind

/*
	Création ou modification d'une catégorie : nom, nature (à la création),
	icône ; suppression, avec réaffectation des opérations à une catégorie de
	même nature si elle est utilisée.
	inController : contrôleur
	inId         : catégorie modifiée ; null pour en créer une
	inKind       : nature d'une nouvelle catégorie
*/
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPanel(
	inController : DesktopController,
	inId         : String?,
	inKind       : CategoryKind
)
{
	val vSpace    by inController.workspace.collectAsState()				// données
	val vCategory = inId?.let { inCatId -> vSpace.data.categories.firstOrNull { it.id == inCatId } }	// catégorie modifiée
	var vName     by remember(inId) { mutableStateOf(vCategory?.name.orEmpty()) }	// nom saisi
	var vKind     by remember(inId) { mutableStateOf(vCategory?.kind ?: inKind) }	// nature
	var vIcon     by remember(inId) { mutableStateOf(vCategory?.icon ?: "dots") }	// icône
	var vError    by remember(inId) { mutableStateOf<String?>(null) }			// erreur affichée
	var vDelete   by remember(inId) { mutableStateOf(false) }					// suppression demandée
	var vTarget   by remember(inId) { mutableStateOf<String?>(null) }			// catégorie de remplacement
	val vUsage    = inId?.let { inController.editor.categoryUsage(inController.currentFile(), it) } ?: 0	// opérations qui l'utilisent

	/*
		Enregistre la catégorie.
	*/
	fun save()
	{
		val vResult = inController.edit { inFile ->
			if (vCategory == null) inController.editor.createCategory(inFile, vName, vIcon, vKind)
			else inController.editor.updateCategory(inFile, vCategory.copy(name = vName, icon = vIcon))
		}																			// résultat
		when (vResult)
		{
			is EditResult.Done    -> inController.closePanel()
			is EditResult.Refused -> vError = vResult.messages.joinToString(" ")
		}
	}

	SidePanel(
		inTitle   = if (vCategory == null) "Nouvelle catégorie" else "Modifier la catégorie",
		inOnClose = inController::closePanel,
		inOnEnter = ::save,
		inActions = {
			if (vCategory != null) OutlinedButton(onClick = { vDelete = true }) { Text("Supprimer") }
			Button(onClick = ::save) { Text("Enregistrer") }
		}
	) {
		FormError(vError)
		FormField(vName, { vName = it }, "Nom")
		Choice(listOf(CategoryKind.EXPENSE to "Dépense", CategoryKind.INCOME to "Revenu"), vKind, { vKind = it }, inEnabled = vCategory == null)
		Text("Icône", style = MaterialTheme.typography.titleSmall)
		FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			CategoryIcons.keys.forEach { inKey ->
				val vOn = inKey == vIcon												// icône choisie
				Box(
					modifier         = Modifier
						.size(40.dp)
						.clip(CircleShape)
						.background(if (vOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
						.clickable { vIcon = inKey }
						.semantics { selected = vOn; contentDescription = inKey },
					contentAlignment = Alignment.Center
				) {
					Icon(CategoryIcons.of(inKey), contentDescription = null, tint = if (vOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
				}
			}
		}
	}

	if (vDelete && vCategory != null)
	{
		val vOthers = vSpace.data.categories.filter { it.kind == vCategory.kind && it.id != vCategory.id }	// remplacements possibles
		AlertDialog(
			onDismissRequest = { vDelete = false },
			title            = { Text("Supprimer « ${vCategory.name} » ?") },
			text             = {
				if (vUsage == 0) Text("Aucune opération ne l’utilise.")
				else
				{
					androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
						Text("$vUsage opération(s) l’utilisent : choisissez la catégorie qui la remplace.")
						Picker("Remplacer par", vOthers.map { it.id to it.name }, vTarget, { vTarget = it })
					}
				}
			},
			confirmButton    = {
				TextButton(
					onClick = {
						when (val vResult = inController.edit { inController.editor.deleteCategory(it, vCategory.id, vTarget) })
						{
							is EditResult.Done    -> { vDelete = false; inController.closePanel() }
							is EditResult.Refused -> { vDelete = false; vError = vResult.messages.joinToString(" ") }
						}
					},
					enabled = vUsage == 0 || vTarget != null
				) { Text("Supprimer") }
			},
			dismissButton    = { TextButton(onClick = { vDelete = false }) { Text("Annuler") } }
		)
	}
}
