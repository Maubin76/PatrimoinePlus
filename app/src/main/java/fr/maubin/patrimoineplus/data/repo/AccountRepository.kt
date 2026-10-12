package fr.maubin.patrimoineplus.data.repo

import androidx.room.withTransaction
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.db.toDomain
import fr.maubin.patrimoineplus.data.db.toEntity
import fr.maubin.patrimoineplus.data.db.toHeaderEntity
import fr.maubin.patrimoineplus.data.db.toPostingEntities
import fr.maubin.patrimoineplus.data.db.toTradeEntities
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.kOpeningLabel
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.domain.rules.SetupError
import fr.maubin.patrimoineplus.domain.rules.validateAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
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
	Solde initial d'un compte.
	cents : montant des espèces au départ
	date  : date du solde initial
*/
data class Opening(
	val cents : Long,
	val date  : LocalDate
)

/*
	Dépôt des comptes : création (avec solde initial), modification,
	archivage, ordre et suppression. Les comptes ne sont jamais déclarés dans
	le code.
	inDb    : base locale
	inClock : horloge (horodatages)
*/
@Singleton
class AccountRepository @Inject constructor(
	private val inDb    : PatrimoineDatabase,
	private val inClock : Clock
)
{
	private val fDao   = inDb.accountDao()										// accès aux comptes
	private val fTxDao = inDb.transactionDao()									// accès aux opérations

	// Tous les comptes, dans l'ordre d'affichage.
	fun observeAccounts(): Flow<List<Account>> =
		fDao.observeAll().map { inList -> inList.map { it.toDomain() } }

	/*
		Crée un compte, placé en fin de liste, avec son solde initial éventuel.
		inName        : nom
		inType        : Courant, Épargne ou Bourse
		inInstitution : établissement, facultatif
		inShare       : part détenue, en %
		inOpening     : solde initial ; null ou montant nul pour aucun
	*/
	suspend fun create(
		inName        : String,
		inType        : AccountType,
		inInstitution : String?  = null,
		inShare       : Int      = 100,
		inOpening     : Opening? = null
	): SaveResult<SetupError>
	{
		val vErrors = validateAccount(inName, inShare)							// règles violées
		if (vErrors.isNotEmpty())
		{
			return SaveResult.Rejected(vErrors)
		}
		val vNow = inClock.millis()												// horodatage
		return inDb.withTransaction {
			val vAccount = Account(												// nouveau compte
				id           = UUID.randomUUID().toString(),
				name         = inName.trim(),
				type         = inType,
				institution  = inInstitution?.trim()?.ifEmpty { null },
				sharePercent = inShare,
				sortOrder    = fDao.maxSortOrder() + 1
			)
			fDao.insert(vAccount.toEntity(vNow, vNow))
			if (inOpening != null && inOpening.cents != 0L)
			{
				saveOpening(newOpening(vAccount.id, inOpening), vNow, vNow)
			}
			SaveResult.Saved(vAccount.id)
		}
	}

	/*
		Modifie un compte (nom, établissement, part, archivage ; type tant que le
		compte n'a pas d'autre opération que son solde initial).
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
		if (vOld.type != inAccount.type.name && operationCount(inAccount.id) > 0)
		{
			return SaveResult.Rejected(listOf(SetupError.TypeLocked))
		}
		val vClean = inAccount.copy(name = inAccount.name.trim(), institution = inAccount.institution?.trim()?.ifEmpty { null })	// champs nettoyés
		fDao.update(vClean.toEntity(vOld.createdAt, inClock.millis()))
		return SaveResult.Saved(inAccount.id)
	}

	/*
		Solde initial d'un compte ; null s'il n'en a pas.
		inId : compte
	*/
	suspend fun openingOf(inId: String): Opening?
	{
		val vTx = openingTransaction(inId) ?: return null						// opération de solde initial
		return Opening(vTx.postings.single().amountCents, vTx.date)
	}

	/*
		Crée, modifie ou supprime le solde initial d'un compte. Un montant nul
		supprime l'opération, sauf si elle porte des positions initiales.
		inId      : compte
		inOpening : nouveau solde initial
	*/
	suspend fun setOpening(
		inId      : String,
		inOpening : Opening
	)
	{
		val vNow = inClock.millis()												// horodatage
		inDb.withTransaction {
			val vOld = openingTransaction(inId)									// solde initial existant
			when
			{
				vOld == null && inOpening.cents != 0L                  -> saveOpening(newOpening(inId, inOpening), vNow, vNow)
				vOld == null                                           -> Unit
				inOpening.cents == 0L && vOld.trades.isEmpty()         -> fTxDao.deleteByIds(listOf(vOld.id))
				else                                                   ->
				{
					val vCreated = fTxDao.getById(vOld.id)?.createdAt ?: vNow	// création conservée
					val vNew     = vOld.copy(date = inOpening.date, postings = listOf(vOld.postings.single().copy(amountCents = inOpening.cents)))	// solde modifié
					saveOpening(vNew, vCreated, vNow)
				}
			}
		}
	}

	/*
		Ajoute une position initiale (achat au prix de revient, sans frais) au
		solde initial d'un compte Bourse ; crée ce solde initial, sans espèces,
		s'il n'existe pas encore.
		inId        : compte Bourse
		inAssetId   : actif
		inQuantity  : quantité, en dix-millièmes
		inUnitPrice : prix de revient unitaire, en dix-millièmes d'euro
		inDate      : date du solde initial, s'il faut le créer
	*/
	suspend fun addOpeningPosition(
		inId        : String,
		inAssetId   : String,
		inQuantity  : Long,
		inUnitPrice : Long,
		inDate      : LocalDate
	)
	{
		val vNow = inClock.millis()												// horodatage
		inDb.withTransaction {
			val vOld   = openingTransaction(inId) ?: newOpening(inId, Opening(0, inDate))	// solde initial
			val vTrade = Trade(UUID.randomUUID().toString(), inAssetId, TradeSide.BUY, inQuantity, inUnitPrice, 0)	// position
			val vCreated = fTxDao.getById(vOld.id)?.createdAt ?: vNow			// création conservée
			saveOpening(vOld.copy(trades = vOld.trades + vTrade), vCreated, vNow)
		}
	}

	/*
		Retire une position initiale du solde initial d'un compte Bourse.
		inId      : compte Bourse
		inTradeId : position retirée
	*/
	suspend fun removeOpeningPosition(
		inId      : String,
		inTradeId : String
	)
	{
		val vNow = inClock.millis()												// horodatage
		inDb.withTransaction {
			val vOld = openingTransaction(inId) ?: return@withTransaction		// solde initial
			val vNew = vOld.copy(trades = vOld.trades.filterNot { it.id == inTradeId })	// sans la position
			if (vNew.trades.isEmpty() && vNew.postings.single().amountCents == 0L)
			{
				fTxDao.deleteByIds(listOf(vOld.id))
			}
			else
			{
				saveOpening(vNew, fTxDao.getById(vOld.id)?.createdAt ?: vNow, vNow)
			}
		}
	}

	/*
		Positions initiales d'un compte Bourse.
		inId : compte
	*/
	suspend fun openingPositions(inId: String): List<Trade> = openingTransaction(inId)?.trades.orEmpty()

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
		Nombre d'opérations qui touchent un compte, hors son solde initial (pour
		prévenir avant suppression).
		inId : compte
	*/
	suspend fun operationCount(inId: String): Int =
		(fDao.transactionIdsOf(inId) - fDao.openingIdsOf(inId).toSet()).size

	/*
		Supprime un compte qui n'a pas d'autre opération que son solde initial
		(supprimé avec lui). Refuse (faux) sinon : il faut alors l'archiver ou le
		supprimer définitivement.
		inId : compte
	*/
	suspend fun deleteIfUnused(inId: String): Boolean =
		inDb.withTransaction {
			if (operationCount(inId) > 0)
			{
				false
			}
			else
			{
				fTxDao.deleteByIds(fDao.openingIdsOf(inId))
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
				fTxDao.deleteByIds(vTxIds)
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

	/*
		Opération de solde initial d'un compte ; null s'il n'en a pas.
		inId : compte
	*/
	private suspend fun openingTransaction(inId: String): Transaction? =
		fDao.openingIdsOf(inId).firstOrNull()?.let { fTxDao.getDetails(it) }?.toDomain()

	/*
		Construit l'opération de solde initial d'un compte.
		inId      : compte
		inOpening : montant et date
	*/
	private fun newOpening(
		inId      : String,
		inOpening : Opening
	): Transaction =
		Transaction(UUID.randomUUID().toString(), inOpening.date, TransactionKind.OPENING, kOpeningLabel,
			postings = listOf(Posting(UUID.randomUUID().toString(), inId, inOpening.cents)))

	/*
		Enregistre une opération de solde initial (toujours valide par construction).
		inTx        : opération
		inCreatedAt : horodatage de création
		inUpdatedAt : horodatage de modification
	*/
	private suspend fun saveOpening(
		inTx        : Transaction,
		inCreatedAt : Long,
		inUpdatedAt : Long
	)
	{
		fTxDao.save(inTx.toHeaderEntity(inCreatedAt, inUpdatedAt), inTx.toPostingEntities(), inTx.toTradeEntities())
	}
}
