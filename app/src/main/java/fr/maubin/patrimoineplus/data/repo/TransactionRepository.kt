package fr.maubin.patrimoineplus.data.repo

import androidx.room.withTransaction
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.db.toDomain
import fr.maubin.patrimoineplus.data.db.toHeaderEntity
import fr.maubin.patrimoineplus.data.db.toPostingEntities
import fr.maubin.patrimoineplus.data.db.toTradeEntities
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.rules.TransactionValidator
import fr.maubin.patrimoineplus.domain.rules.ValidationError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/*
	Dépôt des opérations. Tout enregistrement passe par les règles de saisie
	du domaine ; une opération invalide n'atteint jamais la base.
	inDb    : base locale
	inClock : horloge (horodatages)
*/
@Singleton
class TransactionRepository @Inject constructor(
	private val inDb    : PatrimoineDatabase,
	private val inClock : Clock
)
{
	private val fDao = inDb.transactionDao()									// accès aux opérations

	// Toutes les opérations, avec jambes et ordres.
	fun observeTransactions(): Flow<List<Transaction>> =
		fDao.observeAll().map { inList -> inList.map { it.toDomain() } }

	/*
		Crée ou modifie une opération après l'avoir validée ; une modification
		remplace ses jambes et ses ordres.
		inTx : opération
	*/
	suspend fun save(inTx: Transaction): SaveResult<ValidationError> =
		inDb.withTransaction {
			val vAccounts   = inDb.accountDao().observeAll().first().associate { it.id to it.toDomain() }	// comptes
			val vCategories = inDb.categoryDao().observeAll().first().associate { it.id to it.toDomain() }	// catégories
			val vAssets     = inDb.assetDao().observeAll().first().associate { it.id to it.toDomain() }		// actifs
			val vOthers     = fDao.getAll().filter { it.transaction.id != inTx.id }.map { it.toDomain() }	// autres opérations
			val vErrors     = TransactionValidator(vAccounts, vCategories, vAssets).validate(inTx, Ledger(vOthers))	// règles violées
			if (vErrors.isNotEmpty())
			{
				return@withTransaction SaveResult.Rejected(vErrors)
			}
			val vNow     = inClock.millis()										// horodatage
			val vCreated = fDao.getById(inTx.id)?.createdAt ?: vNow				// création conservée
			fDao.save(inTx.toHeaderEntity(vCreated, vNow), inTx.toPostingEntities(), inTx.toTradeEntities())
			SaveResult.Saved(inTx.id)
		}

	/*
		Supprime des opérations (unitaire ou multiple).
		inIds : identifiants
	*/
	suspend fun delete(inIds: List<String>)
	{
		fDao.deleteByIds(inIds)
	}
}
