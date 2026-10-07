package fr.maubin.patrimoineplus.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.rules.SetupError
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
	Catégorie affichée dans la liste.
	category : catégorie
	usage    : nombre d'opérations qui l'utilisent
*/
data class CategoryItem(
	val category : Category,
	val usage    : Int
)

/*
	État de l'écran des catégories.
	loaded   : vrai une fois la base lue
	expenses : catégories de dépense actives, dans l'ordre d'affichage
	incomes  : catégories de revenu actives
	archived : catégories archivées
*/
data class CategoriesUiState(
	val loaded   : Boolean            = false,
	val expenses : List<CategoryItem> = emptyList(),
	val incomes  : List<CategoryItem> = emptyList(),
	val archived : List<CategoryItem> = emptyList()
)
{
	/*
		Catégories actives d'une nature, sauf une (remplacements possibles).
		inKind    : nature
		inExclude : catégorie exclue
	*/
	fun replacementsFor(
		inKind    : CategoryKind,
		inExclude : String
	): List<Category> =
		(if (inKind == CategoryKind.EXPENSE) expenses else incomes).map { it.category }.filter { it.id != inExclude }
}

/*
	Saisie du formulaire de catégorie.
	name : nom
	kind : dépense ou revenu
	icon : clé d'icône
*/
data class CategoryForm(
	val name : String       = "",
	val kind : CategoryKind = CategoryKind.EXPENSE,
	val icon : String       = "dots"
)

/*
	ViewModel de l'écran des catégories : listes par nature, création,
	modification, archivage, ordre et suppression avec réaffectation.
	inCategories   : dépôt des catégories
	inTransactions : dépôt des opérations
*/
@HiltViewModel
class CategoriesViewModel @Inject constructor(
	private val inCategories : CategoryRepository,
	inTransactions           : TransactionRepository
) : ViewModel()
{
	// État exposé à l'écran.
	val uiState: StateFlow<CategoriesUiState> =
		combine(inCategories.observeCategories(), inTransactions.observeTransactions()) { inList, inTxs ->
			val vUsage = inTxs.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()	// utilisations par catégorie
			val vItems = inList.sortedBy { it.sortOrder }.map { CategoryItem(it, vUsage[it.id] ?: 0) }	// catégories
			CategoriesUiState(
				loaded   = true,
				expenses = vItems.filter { !it.category.archived && it.category.kind == CategoryKind.EXPENSE },
				incomes  = vItems.filter { !it.category.archived && it.category.kind == CategoryKind.INCOME },
				archived = vItems.filter { it.category.archived }
			)
		}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

	/*
		Crée ou modifie une catégorie ; renvoie l'erreur éventuelle (null si enregistrée).
		inForm    : saisie
		inEditing : catégorie modifiée ; null pour une création
		inOnDone  : reçoit l'erreur
	*/
	fun save(
		inForm    : CategoryForm,
		inEditing : Category?,
		inOnDone  : (String?) -> Unit
	)
	{
		viewModelScope.launch {
			val vResult = if (inEditing == null) inCategories.create(inForm.name, inForm.icon, inForm.kind)
				else inCategories.update(inEditing.copy(name = inForm.name, icon = inForm.icon))	// résultat
			inOnDone((vResult as? SaveResult.Rejected<SetupError>)?.errors?.firstOrNull()?.message)
		}
	}

	/*
		Archive ou désarchive une catégorie.
		inCategory : catégorie
		inArchived : vrai pour archiver
	*/
	fun setArchived(
		inCategory : Category,
		inArchived : Boolean
	)
	{
		viewModelScope.launch { inCategories.update(inCategory.copy(archived = inArchived)) }
	}

	/*
		Supprime une catégorie, ses opérations réaffectées au remplacement.
		inCategory    : catégorie supprimée
		inReplacement : catégorie de remplacement ; null si elle n'est pas utilisée
		inOnDone      : reçoit l'erreur éventuelle
	*/
	fun delete(
		inCategory    : Category,
		inReplacement : Category?,
		inOnDone      : (String?) -> Unit
	)
	{
		viewModelScope.launch {
			val vResult = inCategories.delete(inCategory.id, inReplacement?.id)	// résultat
			inOnDone((vResult as? SaveResult.Rejected<SetupError>)?.errors?.firstOrNull()?.message)
		}
	}

	/*
		Enregistre un nouvel ordre pour les catégories d'une nature.
		inItems : catégories, dans l'ordre voulu
	*/
	fun reorder(inItems: List<CategoryItem>)
	{
		viewModelScope.launch { inCategories.reorder(inItems.map { it.category.id }) }
	}
}
