package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.desktop.ui.components.TwoColumns
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import fr.maubin.patrimoineplus.domain.model.CategoryKind

/*
	Catégories de dépense et de revenu, côte à côte : ajout, modification
	(clic), ordre (flèches), nombre d'opérations de chacune.
	inSpace      : données
	inController : contrôleur
*/
@Composable
fun CategoriesScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp)) {
		Text("Catégories", style = MaterialTheme.typography.headlineMedium)
		Spacer(Modifier.height(32.dp))
		TwoColumns(
			inRatio = 1f,
			inLeft  = { KindColumn(inSpace, inController, CategoryKind.EXPENSE) },
			inRight = { KindColumn(inSpace, inController, CategoryKind.INCOME) }
		)
	}
}

/*
	Colonne d'une nature : titre, liste, bouton d'ajout.
	inSpace      : données
	inController : contrôleur
	inKind       : nature
*/
@Composable
private fun KindColumn(
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inKind       : CategoryKind
)
{
	SectionHeader(inTitle = if (inKind == CategoryKind.EXPENSE) "Dépenses" else "Revenus")
	Spacer(Modifier.height(12.dp))
	CategoryList(inSpace, inController, inKind)
	Spacer(Modifier.height(12.dp))
	OutlinedButton(onClick = { inController.openPanel(Panel.CategorySheet(null, inKind)) }) {
		Text(if (inKind == CategoryKind.EXPENSE) "Ajouter une catégorie de dépense" else "Ajouter une catégorie de revenu")
	}
}

/*
	Liste des catégories d'une nature, dans leur ordre.
	inSpace      : données
	inController : contrôleur
	inKind       : nature
*/
@Composable
private fun CategoryList(
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inKind       : CategoryKind
)
{
	val vList  = inSpace.data.categories.filter { it.kind == inKind }.sortedBy { it.sortOrder }	// catégories de cette nature
	val vUsage = inSpace.data.transactions.groupingBy { it.categoryId }.eachCount()				// opérations par catégorie
	BorderedCard {
		vList.forEachIndexed { inIndex, inCategory ->
			if (inIndex > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
			Row(
				modifier          = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { inController.openPanel(Panel.CategorySheet(inCategory.id)) }.padding(start = 24.dp, end = 8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(CategoryIcons.of(inCategory.icon), contentDescription = null, modifier = Modifier.size(20.dp))
				Spacer(Modifier.width(14.dp))
				Column(Modifier.weight(1f)) {
					Text(inCategory.name + if (inCategory.archived) " (archivée)" else "", style = MaterialTheme.typography.bodyLarge)
					Text("${vUsage[inCategory.id] ?: 0} opération(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				IconButton(onClick = { move(inController, vList.map { it.id }, inIndex, -1) }, enabled = inIndex > 0) {
					Icon(LineIcons.ChevronUp, contentDescription = "Monter ${inCategory.name}", modifier = Modifier.size(18.dp))
				}
				IconButton(onClick = { move(inController, vList.map { it.id }, inIndex, 1) }, enabled = inIndex < vList.lastIndex) {
					Icon(LineIcons.ChevronDown, contentDescription = "Descendre ${inCategory.name}", modifier = Modifier.size(18.dp))
				}
			}
		}
	}
}

/*
	Déplace une catégorie d'un rang dans l'ordre de sa nature.
	inController : contrôleur
	inIds        : ordre actuel
	inIndex      : rang de la catégorie
	inStep       : -1 pour monter, +1 pour descendre
*/
private fun move(
	inController : DesktopController,
	inIds        : List<String>,
	inIndex      : Int,
	inStep       : Int
)
{
	val vIds = inIds.toMutableList()											// nouvel ordre
	java.util.Collections.swap(vIds, inIndex, inIndex + inStep)
	inController.edit { inController.editor.reorderCategories(it, vIds) }
}
