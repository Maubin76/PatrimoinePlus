package fr.maubin.patrimoineplus.data.repo

import androidx.room.withTransaction
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.db.toDomain
import fr.maubin.patrimoineplus.data.db.toEntity
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.rules.SetupError
import fr.maubin.patrimoineplus.domain.rules.validateAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/*
	Résultat d'un enregistrement : accepté (avec l'identifiant) ou refusé (avec
	les règles violées).
*/
sealed interface SaveResult<out E>
{
	/*
		Enregistrement accepté.
		id : identifiant de l'élément enregistré
	*/
	data class Saved(val id: String) : SaveResult<Nothing>

	/*
		Enregistrement refusé.
		errors : règles violées
	*/
	data class Rejected<E>(val errors: List<E>) : SaveResult<E>
}

/*
	Dépôt des comptes : création, modification, archivage, ordre et
	suppression. Les comptes ne sont jamais déclarés dans le code.
	inDb    : base locale
	inClock : horloge (horodatages)
*/
@Singleton
class AccountRepository @Inject constructor(
	private val inDb    : PatrimoineDatabase,
	private val inClock : Clock
)
{
	private val fDao = inDb.accountDao()										// accès aux comptes

	// Tous les comptes, dans l'ordre d'affichage.
	fun observeAccounts(): Flow<List<Account>> =
		fDao.observeAll().map { inList -> inList.map { it.toDomain() } }

	/*
		Crée un compte, placé en fin de liste.
		inName        : nom
		inType        : Courant, Épargne ou Bourse
		inInstitution : établissement, facultatif
		inShare       : part détenue, en %
	*/
	suspend fun create(
		inName        : String,
		inType        : AccountType,
		inInstitution : String? = null,
		inShare       : Int     = 100
	): SaveResult<SetupError>
	{
		val vErrors = validateAccount(inName, inShare)							// règles violées
		if (vErrors.isNotEmpty())
		{
			return SaveResult.Rejected(vErrors)
		}
		val vNow     = inClock.millis()										// horodatage
		val vAccount = Account(													// nouveau compte
			id           = UUID.randomUUID().toString(),
			name         = inName.trim(),
			type         = inType,
			institution  = inInstitution?.trim()?.ifEmpty { null },
			sharePercent = inShare,
			sortOrder    = fDao.maxSortOrder() + 1
		)
		fDao.insert(vAccount.toEntity(vNow, vNow))
		return SaveResult.Saved(vAccount.id)
	}

	/*
		Modifie un compte (nom, type, établissement, part, archivage).
		inAccount : compte modifié
	*/
	suspend fun update(inAccount: Account): SaveResult<SetupError>
	{
		val vErrors = validateAccount(inAccount.name, inAccount.sharePercent)	// règles violées
		if (vErrors.isNotEmpty())
		{
			return SaveResult.Rejected(vErrors)
		}
		val vOld = fDao.getById(inAccount.id) ?: return SaveResult.Rejected(listOf(SetupError.NotFound))	// version enregistrée
		val vClean = inAccount.copy(name = inAccount.name.trim(), institution = inAccount.institution?.trim()?.ifEmpty { null })	// champs nettoyés
		fDao.update(vClean.toEntity(vOld.createdAt, inClock.millis()))
		return SaveResult.Saved(inAccount.id)
	}

	/*
		Archive ou désarchive un compte.
		inId       : compte
		inArchived : vrai pour archiver
	*/
	suspend fun setArchived(
		inId       : String,
		inArchived : Boolean
	)
	{
		val vOld = fDao.getById(inId) ?: return									// version enregistrée
		fDao.update(vOld.copy(archived = inArchived, updatedAt = inClock.millis()))
	}

	/*
		Nombre d'opérations qui touchent un compte (pour prévenir avant suppression).
		inId : compte
	*/
	suspend fun usage(inId: String): Int = fDao.transactionIdsOf(inId).size

	/*
		Supprime un compte sans opération. Refuse (faux) s'il en a : il faut
		alors l'archiver ou le supprimer définitivement.
		inId : compte
	*/
	suspend fun deleteIfUnused(inId: String): Boolean =
		inDb.withTransaction {
			if (fDao.transactionIdsOf(inId).isNotEmpty())
			{
				false
			}
			else
			{
				fDao.deleteById(inId)
				true
			}
		}

	/*
		Supprime définitivement un compte et toutes les opérations qui le
		touchent ; un virement est supprimé en entier, ce qui modifie aussi
		l'autre compte. À n'appeler qu'après double confirmation.
		inId : compte
		Retour : nombre d'opérations supprimées
	*/
	suspend fun deletePermanently(inId: String): Int =
		inDb.withTransaction {
			val vTxIds = fDao.transactionIdsOf(inId)							// opérations concernées
			if (vTxIds.isNotEmpty())
			{
				inDb.transactionDao().deleteByIds(vTxIds)
			}
			fDao.deleteById(inId)
			vTxIds.size
		}

	/*
		Fixe l'ordre d'affichage des comptes.
		inIds : identifiants, dans l'ordre voulu
	*/
	suspend fun reorder(inIds: List<String>)
	{
		val vNow = inClock.millis()												// horodatage
		inDb.withTransaction {
			inIds.forEachIndexed { inIndex, inId -> fDao.setSortOrder(inId, inIndex, vNow) }
		}
	}
}
