package fr.maubin.patrimoineplus.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.ConfirmDialog
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.components.ReorderableColumn
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin

/*
	Feuille ouverte sur l'écran des catégories.
	editing : catégorie modifiée ; null pour une création
	form    : saisie de départ
*/
private data class CategorySheetState(
	val editing : CategoryItem?,
	val form    : CategoryForm
)

/*
	Onglet des catégories : dépenses et revenus en listes réordonnables,
	création et modification en feuille du bas, catégories archivées à part ;
	une catégorie utilisée se supprime après réaffectation de ses opérations.
	inViewModel : ViewModel fourni par Hilt
*/
@Composable
fun CategoriesScreen(inViewModel: CategoriesViewModel = hiltViewModel())
{
	val vState    by inViewModel.uiState.collectAsStateWithLifecycle()		// état à afficher
	var vSheet    by remember { mutableStateOf<CategorySheetState?>(null) }	// feuille ouverte
	var vDeleting by remember { mutableStateOf<CategoryItem?>(null) }		// catégorie à supprimer

	CategoriesContent(
		inState     = vState,
		inOnAdd     = { inKind -> vSheet = CategorySheetState(null, CategoryForm(kind = inKind)) },
		inOnOpen    = { inItem -> vSheet = CategorySheetState(inItem, CategoryForm(inItem.category.name, inItem.category.kind, inItem.category.icon)) },
		inOnReorder = inViewModel::reorder
	)

	vSheet?.let { inSheet ->
		CategorySheet(
			inInitial   = inSheet.form,
			inEditing   = inSheet.editing?.category,
			inOnSave    = { inForm, inOnError -> inViewModel.save(inForm, inSheet.editing?.category) { if (it == null) vSheet = null else inOnError(it) } },
			inOnArchive = {
				inSheet.editing?.let { inViewModel.setArchived(it.category, !it.category.archived) }
				vSheet = null
			},
			inOnDelete  = {
				vDeleting = inSheet.editing
				vSheet    = null
			},
			inOnDismiss = { vSheet = null }
		)
	}

	vDeleting?.let { inItem ->
		if (inItem.usage == 0)
		{
			ConfirmDialog(
				inTitle     = "Supprimer « ${inItem.category.name} » ?",
				inText      = "Aucune opération n’utilise cette catégorie.",
				inAction    = "Supprimer",
				inOnConfirm = {
					inViewModel.delete(inItem.category, null) {}
					vDeleting = null
				},
				inOnDismiss = { vDeleting = null }
			)
		}
		else
		{
			ReassignDialog(
				inItem      = inItem,
				inChoices   = vState.replacementsFor(inItem.category.kind, inItem.category.id),
				inOnConfirm = { inTarget ->
					inViewModel.delete(inItem.category, inTarget) {}
					vDeleting = null
				},
				inOnDismiss = { vDeleting = null }
			)
		}
	}
}

/*
	Contenu de l'écran des catégories, sans état propre (prévisualisable).
	inState     : état à afficher
	inOnAdd     : ouvre la création d'une catégorie de la nature donnée
	inOnOpen    : ouvre la modification d'une catégorie
	inOnReorder : enregistre un nouvel ordre dans une nature
*/
@Composable
fun CategoriesContent(
	inState     : CategoriesUiState,
	inOnAdd     : (CategoryKind) -> Unit,
	inOnOpen    : (CategoryItem) -> Unit,
	inOnReorder : (List<CategoryItem>) -> Unit
)
{
	ScreenScaffold(inTitle = "Catégories") { inPadding ->
		Column(
			Modifier
				.padding(top = inPadding.calculateTopPadding())
				.verticalScroll(rememberScrollState())
				.padding(start = kScreenMargin, end = kScreenMargin, top = 8.dp, bottom = inPadding.calculateBottomPadding() + 16.dp)
		) {
			KindSection("Dépenses", CategoryKind.EXPENSE, inState.expenses, inState.loaded, inOnAdd, inOnOpen, inOnReorder)
			Spacer(Modifier.height(28.dp))
			KindSection("Revenus", CategoryKind.INCOME, inState.incomes, inState.loaded, inOnAdd, inOnOpen, inOnReorder)
			if (inState.archived.isNotEmpty())
			{
				Spacer(Modifier.height(28.dp))
				SectionHeader(inTitle = "Archivées")
				Spacer(Modifier.height(8.dp))
				BorderedCard {
					inState.archived.forEachIndexed { inIndex, inItem ->
						if (inIndex > 0)
						{
							HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
						}
						CategoryRow(inItem, inOnOpen)
					}
				}
			}
		}
	}
}

/*
	Section d'une nature : titre, lien « Ajouter », liste réordonnable.
	inTitle     : titre
	inKind      : nature
	inItems     : catégories de la nature
	inLoaded    : vrai une fois la base lue
	inOnAdd     : ouvre la création
	inOnOpen    : ouvre la modification
	inOnReorder : enregistre un nouvel ordre
*/
@Composable
private fun KindSection(
	inTitle     : String,
	inKind      : CategoryKind,
	inItems     : List<CategoryItem>,
	inLoaded    : Boolean,
	inOnAdd     : (CategoryKind) -> Unit,
	inOnOpen    : (CategoryItem) -> Unit,
	inOnReorder : (List<CategoryItem>) -> Unit
)
{
	SectionHeader(inTitle = inTitle, inLinkLabel = "Ajouter", inOnLink = { inOnAdd(inKind) })
	Spacer(Modifier.height(8.dp))
	BorderedCard {
		if (inItems.isEmpty() && inLoaded)
		{
			EmptyState(
				inTitle = if (inKind == CategoryKind.EXPENSE) "Aucune catégorie de dépense" else "Aucune catégorie de revenu",
				inBody  = "Ajoutez-en une avec le lien ci-dessus."
			)
		}
		else
		{
			ReorderableColumn(inItems, { it.category.id }, inOnReorder) { inItem -> CategoryRow(inItem, inOnOpen) }
		}
	}
}

/*
	Ligne d'une catégorie : icône, nom, nombre d'opérations.
	inItem   : catégorie affichée
	inOnOpen : ouvre la modification
*/
@Composable
private fun CategoryRow(
	inItem   : CategoryItem,
	inOnOpen : (CategoryItem) -> Unit
)
{
	Row(
		modifier          = Modifier
			.fillMaxWidth()
			.heightIn(min = 56.dp)
			.clickable { inOnOpen(inItem) }
			.padding(horizontal = 20.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(CategoryIcons.of(inItem.category.icon), contentDescription = null)
		Spacer(Modifier.width(16.dp))
		Text(inItem.category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
		if (inItem.usage > 0)
		{
			Text(
				text  = "${inItem.usage} op.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

/*
	Choix de la catégorie qui reprend les opérations d'une catégorie supprimée.
	inItem      : catégorie supprimée
	inChoices   : remplacements possibles (même nature)
	inOnConfirm : réaffectation et suppression
	inOnDismiss : annulation
*/
@Composable
private fun ReassignDialog(
	inItem      : CategoryItem,
	inChoices   : List<Category>,
	inOnConfirm : (Category) -> Unit,
	inOnDismiss : () -> Unit
)
{
	var vTarget by remember { mutableStateOf(inChoices.firstOrNull()) }		// remplacement choisi
	AlertDialog(
		onDismissRequest = inOnDismiss,
		title            = { Text("Supprimer « ${inItem.category.name} » ?") },
		text             = {
			Column(Modifier.selectableGroup()) {
				Text(
					if (inChoices.isEmpty()) "${inItem.usage} opération(s) l’utilisent. Créez d’abord une autre catégorie de même nature pour les y déplacer, ou archivez celle-ci."
					else "${inItem.usage} opération(s) l’utilisent. Elles seront déplacées vers :"
				)
				Spacer(Modifier.height(8.dp))
				inChoices.forEach { inChoice ->
					Row(
						modifier          = Modifier
							.fillMaxWidth()
							.heightIn(min = 48.dp)
							.selectable(selected = inChoice == vTarget, role = Role.RadioButton) { vTarget = inChoice },
						verticalAlignment = Alignment.CenterVertically
					) {
						RadioButton(
							selected = inChoice == vTarget,
							onClick  = null,
							colors   = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onSurface)
						)
						Spacer(Modifier.width(12.dp))
						Text(inChoice.name)
					}
				}
			}
		},
		confirmButton    = {
			TextButton(onClick = { vTarget?.let(inOnConfirm) }, enabled = vTarget != null) { Text("Déplacer et supprimer") }
		},
		dismissButton    = { TextButton(onClick = inOnDismiss) { Text("Annuler") } }
	)
}
