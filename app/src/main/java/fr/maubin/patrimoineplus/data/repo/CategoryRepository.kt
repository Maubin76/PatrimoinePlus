package fr.maubin.patrimoineplus.data.repo

import androidx.room.withTransaction
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.db.toDomain
import fr.maubin.patrimoineplus.data.db.toEntity
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.rules.SetupError
import fr.maubin.patrimoineplus.domain.rules.validateReassignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/*
	Dépôt des catégories : création, modification, archivage, suppression
	avec réaffectation des opérations.
	inDb    : base locale
	inClock : horloge (horodatages)
*/
@Singleton
class CategoryRepository @Inject constructor(
	private val inDb    : PatrimoineDatabase,
	private val inClock : Clock
)
{
	private val fDao = inDb.categoryDao()										// accès aux catégories

	// Toutes les catégories.
	fun observeCategories(): Flow<List<Category>> =
		fDao.observeAll().map { inList -> inList.map { it.toDomain() } }

	/*
		Crée une catégorie.
		inName : nom
		inIcon : clé d'icône
		inKind : dépense ou revenu
	*/
	suspend fun create(
		inName : String,
		inIcon : String,
		inKind : CategoryKind
	): SaveResult<SetupError>
	{
		if (inName.isBlank())
		{
			return SaveResult.Rejected(listOf(SetupError.BlankName))
		}
		val vCategory = Category(UUID.randomUUID().toString(), inName.trim(), inIcon, inKind)	// nouvelle catégorie
		addAll(listOf(vCategory))
		return SaveResult.Saved(vCategory.id)
	}

	/*
		Ajoute des catégories déjà construites (catalogue de départ, démo).
		inCategories : catégories
	*/
	suspend fun addAll(inCategories: List<Category>)
	{
		val vNow = inClock.millis()												// horodatage
		fDao.insertAll(inCategories.map { it.toEntity(vNow, vNow) })
	}

	/*
		Modifie une catégorie (nom, icône, ordre, archivage).
		inCategory : catégorie modifiée
	*/
	suspend fun update(inCategory: Category): SaveResult<SetupError>
	{
		if (inCategory.name.isBlank())
		{
			return SaveResult.Rejected(listOf(SetupError.BlankName))
		}
		val vOld = fDao.getById(inCategory.id) ?: return SaveResult.Rejected(listOf(SetupError.NotFound))	// version enregistrée
		fDao.update(inCategory.copy(name = inCategory.name.trim(), sortOrder = vOld.sortOrder).toEntity(vOld.createdAt, inClock.millis()))	// rang actuel conservé
		return SaveResult.Saved(inCategory.id)
	}

	/*
		Supprime une catégorie. Si des opérations l'utilisent, elles sont
		d'abord réaffectées à la catégorie de remplacement (de même nature) ;
		sans remplacement, la suppression est refusée.
		inId            : catégorie supprimée
		inReplacementId : catégorie de remplacement, si la catégorie est utilisée
	*/
	suspend fun delete(
		inId            : String,
		inReplacementId : String?
	): SaveResult<SetupError> =
		inDb.withTransaction {
			val vFrom  = fDao.getById(inId)?.toDomain() ?: return@withTransaction SaveResult.Rejected(listOf(SetupError.NotFound))	// catégorie supprimée
			val vUsed  = usage(inId) > 0										// catégorie utilisée (opérations, récurrences, crédits)
			if (vUsed)
			{
				val vTo     = inReplacementId?.let { fDao.getById(it)?.toDomain() }	// remplacement
					?: return@withTransaction SaveResult.Rejected(listOf(SetupError.CategoryInUse))
				val vErrors = validateReassignment(vFrom, vTo)					// règles violées
				if (vErrors.isNotEmpty())
				{
					return@withTransaction SaveResult.Rejected(vErrors)
				}
				fDao.reassign(inId, vTo.id, inClock.millis())
				inDb.planningDao().reassignRecurrences(inId, vTo.id)
				inDb.planningDao().reassignLoans(inId, vTo.id)
			}
			fDao.deleteById(inId)
			SaveResult.Saved(inId)
		}

	/*
		Fixe l'ordre d'affichage des catégories (d'une même nature).
		inIds : identifiants, dans l'ordre voulu
	*/
	suspend fun reorder(inIds: List<String>)
	{
		val vNow = inClock.millis()												// horodatage
		inDb.withTransaction {
			inIds.forEachIndexed { inIndex, inId -> fDao.setSortOrder(inId, inIndex, vNow) }
		}
	}

	/*
		Nombre d'opérations, de récurrences et de crédits qui utilisent une
		catégorie.
		inId : catégorie
	*/
	suspend fun usage(inId: String): Int = fDao.usage(inId) + inDb.planningDao().categoryUsage(inId)
}
