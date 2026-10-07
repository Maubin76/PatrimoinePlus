package fr.maubin.patrimoineplus.ui.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.calc.LabelSuggestion
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.categories.kindLabel
import fr.maubin.patrimoineplus.ui.components.AmountInput
import fr.maubin.patrimoineplus.ui.components.ConfirmDialog
import fr.maubin.patrimoineplus.ui.components.DateInput
import fr.maubin.patrimoineplus.ui.components.FormSheet
import fr.maubin.patrimoineplus.ui.components.SegmentedChoice
import fr.maubin.patrimoineplus.ui.components.TextInput
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons

/*
	Feuille de saisie d'une dépense ou d'un revenu : nature, montant (sans
	signe), compte, libellé avec suggestions, catégorie, date, notes ; en
	modification, suppression après confirmation.
	inInitial      : saisie de départ
	inAccounts     : comptes actifs
	inCategories   : catégories actives
	inSuggestions  : suggestions de libellés pour un début de saisie
	inOnSave       : enregistre la saisie et renvoie les erreurs par rappel
	inOnDelete     : supprime l'opération modifiée
	inOnDismiss    : fermeture
*/
@Composable
fun TransactionSheet(
	inInitial     : EntryForm,
	inAccounts    : List<Account>,
	inCategories  : List<Category>,
	inSuggestions : (String) -> List<LabelSuggestion>,
	inOnSave      : (EntryForm, (EntryErrors) -> Unit) -> Unit,
	inOnDelete    : () -> Unit,
	inOnDismiss   : () -> Unit
)
{
	var vForm    by remember { mutableStateOf(inInitial) }						// saisie en cours
	var vErrors  by remember { mutableStateOf(EntryErrors()) }				// erreurs affichées
	var vConfirm by remember { mutableStateOf(false) }						// suppression à confirmer
	val vByKind  = inCategories.filter { it.kind == vForm.kind }				// catégories de la nature choisie
	val vHints   = if (vForm.id == null) inSuggestions(vForm.label) else emptyList()	// suggestions affichées

	FormSheet(inTitle = if (vForm.id == null) "Nouvelle opération" else "Modifier l’opération", inOnDismiss = inOnDismiss) {
		SegmentedChoice(
			inOptions  = CategoryKind.entries,
			inSelected = vForm.kind,
			inLabel    = ::kindLabel,
			inOnSelect = { inKind ->
				val vKeep = inCategories.firstOrNull { it.id == vForm.categoryId }?.kind == inKind	// catégorie encore valable
				vForm = vForm.copy(kind = inKind, categoryId = if (vKeep) vForm.categoryId else null)
			}
		)
		AmountInput(vForm.amount, { vForm = vForm.copy(amount = it.replace("-", "")) }, "Montant", vErrors.amount)
		AccountPicker(inAccounts, vForm.accountId, vErrors.account) { vForm = vForm.copy(accountId = it) }
		TextInput(vForm.label, { vForm = vForm.copy(label = it) }, "Libellé", vErrors.label)
		if (vHints.isNotEmpty())
		{
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				vHints.forEach { inHint ->
					Pill(inText = inHint.label, inSelected = false) {
						val vCategory = inCategories.firstOrNull { it.id == inHint.categoryId }	// catégorie de la suggestion
						vForm = vForm.copy(
							label      = inHint.label,
							categoryId = vCategory?.id ?: vForm.categoryId,
							kind       = vCategory?.kind ?: vForm.kind
						)
					}
				}
			}
		}
		Text("Catégorie", style = MaterialTheme.typography.titleSmall)
		if (vByKind.isEmpty())
		{
			Text(
				text  = "Aucune catégorie de ${kindLabel(vForm.kind).lowercase()} : créez-en une dans l’onglet Catégories.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			vByKind.forEach { inCategory ->
				Pill(inText = inCategory.name, inSelected = inCategory.id == vForm.categoryId, inIcon = inCategory.icon) {
					vForm = vForm.copy(categoryId = inCategory.id)
				}
			}
		}
		vErrors.category?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
		DateInput(vForm.date, { vForm = vForm.copy(date = it) }, "Date")
		TextInput(vForm.notes, { vForm = vForm.copy(notes = it) }, "Notes (facultatif)")
		vErrors.general?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		Button(
			onClick        = { inOnSave(vForm) { vErrors = it } },
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) {
			Text("Enregistrer")
		}
		if (vForm.id != null)
		{
			TextButton(onClick = { vConfirm = true }, modifier = Modifier.fillMaxWidth()) {
				Text("Supprimer l’opération", color = MaterialTheme.colorScheme.error)
			}
		}
	}

	if (vConfirm)
	{
		ConfirmDialog(
			inTitle     = "Supprimer l’opération ?",
			inText      = "« ${vForm.label} » sera supprimée ; le solde du compte sera recalculé.",
			inAction    = "Supprimer",
			inOnConfirm = {
				vConfirm = false
				inOnDelete()
			},
			inOnDismiss = { vConfirm = false }
		)
	}
}

/*
	Choix du compte dans une liste déroulante.
	inAccounts : comptes proposés
	inSelected : compte choisi
	inError    : message d'erreur ; null sans erreur
	inOnSelect : choix d'un compte
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPicker(
	inAccounts : List<Account>,
	inSelected : String?,
	inError    : String?,
	inOnSelect : (String) -> Unit
)
{
	var vOpen by remember { mutableStateOf(false) }							// liste ouverte
	ExposedDropdownMenuBox(expanded = vOpen, onExpandedChange = { vOpen = it }) {
		OutlinedTextField(
			value          = inAccounts.firstOrNull { it.id == inSelected }?.name.orEmpty(),
			onValueChange  = {},
			readOnly       = true,
			label          = { Text("Compte") },
			isError        = inError != null,
			supportingText = inError?.let { { Text(it) } },
			trailingIcon   = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vOpen) },
			singleLine     = true,
			modifier       = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
		)
		ExposedDropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, containerColor = MaterialTheme.colorScheme.surface) {
			inAccounts.forEach { inAccount ->
				DropdownMenuItem(
					text    = { Text("${inAccount.name}  ·  ${inAccount.type.label}") },
					onClick = {
						inOnSelect(inAccount.id)
						vOpen = false
					}
				)
			}
		}
	}
}

/*
	Pastille de choix (catégorie, suggestion) : bordée au repos, à l'encre
	quand elle est choisie ; icône facultative.
	inText     : texte
	inSelected : choisie
	inIcon     : clé d'icône de catégorie ; null sans icône
	inOnClick  : choix
*/
@Composable
private fun Pill(
	inText     : String,
	inSelected : Boolean,
	inIcon     : String? = null,
	inOnClick  : () -> Unit
)
{
	val vColors  = MaterialTheme.colorScheme									// palette courante
	val vContent = if (inSelected) vColors.surface else vColors.onSurface		// couleur du contenu
	Surface(
		shape    = CircleShape,
		color    = if (inSelected) vColors.onSurface else vColors.surface,
		border   = BorderStroke(1.dp, if (inSelected) vColors.onSurface else vColors.outlineVariant),
		modifier = Modifier.semantics { selected = inSelected }
	) {
		Row(
			modifier          = Modifier
				.heightIn(min = 40.dp)
				.clickable(role = Role.Button, onClick = inOnClick)
				.padding(horizontal = 14.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			if (inIcon != null)
			{
				Icon(CategoryIcons.of(inIcon), contentDescription = null, tint = vContent, modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(8.dp))
			}
			Text(inText, style = MaterialTheme.typography.labelLarge, color = vContent)
		}
	}
}
