package fr.maubin.patrimoineplus.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/*
	Accès aux comptes.
*/
@Dao
interface AccountDao
{
	// Tous les comptes, dans l'ordre d'affichage, mis à jour en continu.
	@Query("SELECT * FROM account ORDER BY sortOrder, name")
	fun observeAll(): Flow<List<AccountEntity>>

	/*
		Un compte par identifiant.
		inId : identifiant
	*/
	@Query("SELECT * FROM account WHERE id = :inId")
	suspend fun getById(inId: String): AccountEntity?

	/*
		Ajoute un compte.
		inAccount : compte
	*/
	@Insert
	suspend fun insert(inAccount: AccountEntity)

	/*
		Met à jour un compte.
		inAccount : compte
	*/
	@Update
	suspend fun update(inAccount: AccountEntity)

	/*
		Supprime un compte (refusé par la base s'il a des jambes).
		inId : identifiant
	*/
	@Query("DELETE FROM account WHERE id = :inId")
	suspend fun deleteById(inId: String)

	// Plus grand ordre d'affichage utilisé ; -1 sans compte.
	@Query("SELECT COALESCE(MAX(sortOrder), -1) FROM account")
	suspend fun maxSortOrder(): Int

	/*
		Opérations qui ont au moins une jambe sur un compte.
		inId : compte
	*/
	@Query("SELECT DISTINCT transactionId FROM posting WHERE accountId = :inId")
	suspend fun transactionIdsOf(inId: String): List<String>

	/*
		Opérations de solde initial d'un compte.
		inId : compte
	*/
	@Query("SELECT DISTINCT t.id FROM txn t JOIN posting p ON p.transactionId = t.id WHERE t.kind = 'OPENING' AND p.accountId = :inId")
	suspend fun openingIdsOf(inId: String): List<String>

	/*
		Soldes initiaux d'un compte qui ne portent que des espèces (sans
		position initiale).
		inId : compte
	*/
	@Query("SELECT DISTINCT t.id FROM txn t JOIN posting p ON p.transactionId = t.id WHERE t.kind = 'OPENING' AND p.accountId = :inId AND NOT EXISTS (SELECT 1 FROM trade r WHERE r.transactionId = t.id)")
	suspend fun cashOpeningIdsOf(inId: String): List<String>

	/*
		Change l'ordre d'affichage d'un compte.
		inId    : compte
		inOrder : nouvel ordre
		inNow   : horodatage de modification
	*/
	@Query("UPDATE account SET sortOrder = :inOrder, updatedAt = :inNow WHERE id = :inId")
	suspend fun setSortOrder(inId: String, inOrder: Int, inNow: Long)
}

/*
	Accès aux catégories.
*/
@Dao
interface CategoryDao
{
	// Toutes les catégories, dans l'ordre d'affichage.
	@Query("SELECT * FROM category ORDER BY kind, sortOrder, name")
	fun observeAll(): Flow<List<CategoryEntity>>

	/*
		Une catégorie par identifiant.
		inId : identifiant
	*/
	@Query("SELECT * FROM category WHERE id = :inId")
	suspend fun getById(inId: String): CategoryEntity?

	/*
		Ajoute des catégories.
		inCategories : catégories
	*/
	@Insert
	suspend fun insertAll(inCategories: List<CategoryEntity>)

	/*
		Met à jour une catégorie.
		inCategory : catégorie
	*/
	@Update
	suspend fun update(inCategory: CategoryEntity)

	/*
		Déplace toutes les opérations d'une catégorie vers une autre.
		inFrom : catégorie quittée
		inTo   : catégorie de destination
		inNow  : horodatage de modification
	*/
	@Query("UPDATE txn SET categoryId = :inTo, updatedAt = :inNow WHERE categoryId = :inFrom")
	suspend fun reassign(inFrom: String, inTo: String, inNow: Long)

	/*
		Nombre d'opérations qui utilisent une catégorie.
		inId : catégorie
	*/
	@Query("SELECT COUNT(*) FROM txn WHERE categoryId = :inId")
	suspend fun usage(inId: String): Int

	/*
		Change l'ordre d'affichage d'une catégorie.
		inId    : catégorie
		inOrder : nouvel ordre
		inNow   : horodatage de modification
	*/
	@Query("UPDATE category SET sortOrder = :inOrder, updatedAt = :inNow WHERE id = :inId")
	suspend fun setSortOrder(inId: String, inOrder: Int, inNow: Long)

	/*
		Supprime une catégorie (refusé par la base si elle est utilisée).
		inId : identifiant
	*/
	@Query("DELETE FROM category WHERE id = :inId")
	suspend fun deleteById(inId: String)
}

/*
	Accès aux opérations, avec leurs jambes et leurs ordres.
*/
@Dao
interface TransactionDao
{
	// Toutes les opérations complètes, mises à jour en continu.
	@Transaction
	@Query("SELECT * FROM txn ORDER BY date, createdAt")
	fun observeAll(): Flow<List<TransactionWithDetails>>

	// Toutes les opérations complètes, lues une fois.
	@Transaction
	@Query("SELECT * FROM txn ORDER BY date, createdAt")
	suspend fun getAll(): List<TransactionWithDetails>

	/*
		Une opération par identifiant.
		inId : identifiant
	*/
	@Query("SELECT * FROM txn WHERE id = :inId")
	suspend fun getById(inId: String): TransactionEntity?

	/*
		Une opération complète par identifiant.
		inId : identifiant
	*/
	@Transaction
	@Query("SELECT * FROM txn WHERE id = :inId")
	suspend fun getDetails(inId: String): TransactionWithDetails?

	/*
		Ajoute ou remplace l'en-tête d'une opération.
		inTx : opération
	*/
	@Upsert
	suspend fun upsertHeader(inTx: TransactionEntity)

	/*
		Ajoute des jambes.
		inPostings : jambes
	*/
	@Insert
	suspend fun insertPostings(inPostings: List<PostingEntity>)

	/*
		Ajoute des ordres.
		inTrades : ordres
	*/
	@Insert
	suspend fun insertTrades(inTrades: List<TradeEntity>)

	/*
		Supprime les jambes d'une opération.
		inId : opération
	*/
	@Query("DELETE FROM posting WHERE transactionId = :inId")
	suspend fun deletePostingsOf(inId: String)

	/*
		Supprime les ordres d'une opération.
		inId : opération
	*/
	@Query("DELETE FROM trade WHERE transactionId = :inId")
	suspend fun deleteTradesOf(inId: String)

	/*
		Supprime des opérations (jambes et ordres suivent en cascade).
		inIds : identifiants
	*/
	@Query("DELETE FROM txn WHERE id IN (:inIds)")
	suspend fun deleteByIds(inIds: List<String>)

	/*
		Enregistre une opération complète, en remplaçant ses jambes et ordres.
		inTx       : en-tête
		inPostings : jambes
		inTrades   : ordres
	*/
	@Transaction
	suspend fun save(
		inTx       : TransactionEntity,
		inPostings : List<PostingEntity>,
		inTrades   : List<TradeEntity>
	)
	{
		upsertHeader(inTx)
		deletePostingsOf(inTx.id)
		deleteTradesOf(inTx.id)
		insertPostings(inPostings)
		insertTrades(inTrades)
	}
}

/*
	Accès aux actifs et à leurs cours.
*/
@Dao
interface AssetDao
{
	// Tous les actifs, par nom.
	@Query("SELECT * FROM asset ORDER BY name")
	fun observeAll(): Flow<List<AssetEntity>>

	// Tous les cours relevés.
	@Query("SELECT * FROM asset_price ORDER BY assetId, date")
	fun observePrices(): Flow<List<AssetPriceEntity>>

	/*
		Ajoute des actifs.
		inAssets : actifs
	*/
	@Insert
	suspend fun insertAll(inAssets: List<AssetEntity>)

	/*
		Met à jour un actif.
		inAsset : actif
	*/
	@Update
	suspend fun update(inAsset: AssetEntity)

	/*
		Un actif ; null s'il n'existe pas.
		inId : identifiant
	*/
	@Query("SELECT * FROM asset WHERE id = :inId")
	suspend fun get(inId: String): AssetEntity?

	/*
		Enregistre un cours ; remplace celui du même jour pour le même actif.
		inPrice : cours
	*/
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun putPrice(inPrice: AssetPriceEntity)

	/*
		Cours d'un actif à une date ; null s'il n'y en a pas.
		inAssetId : actif
		inDate    : date
	*/
	@Query("SELECT * FROM asset_price WHERE assetId = :inAssetId AND date = :inDate")
	suspend fun getPrice(
		inAssetId : String,
		inDate    : LocalDate
	): AssetPriceEntity?

	/*
		Supprime un cours.
		inId : identifiant
	*/
	@Query("DELETE FROM asset_price WHERE id = :inId")
	suspend fun deletePrice(inId: String)
}

/*
	Accès aux relevés de solde.
*/
@Dao
interface StatementDao
{
	// Tous les relevés.
	@Query("SELECT * FROM account_statement ORDER BY accountId, date")
	fun observeAll(): Flow<List<AccountStatementEntity>>

	/*
		Ajoute ou remplace un relevé.
		inStatement : relevé
	*/
	@Upsert
	suspend fun upsert(inStatement: AccountStatementEntity)

	/*
		Supprime un relevé.
		inId : identifiant
	*/
	@Query("DELETE FROM account_statement WHERE id = :inId")
	suspend fun deleteById(inId: String)
}

/*
	Accès en bloc pour l'export et l'import : lecture de toutes les tables,
	effacement complet avant un import.
*/
@Dao
interface BackupDao
{
	// Tous les comptes.
	@Query("SELECT * FROM account")
	suspend fun accounts(): List<AccountEntity>

	// Toutes les catégories.
	@Query("SELECT * FROM category")
	suspend fun categories(): List<CategoryEntity>

	// Tous les actifs.
	@Query("SELECT * FROM asset")
	suspend fun assets(): List<AssetEntity>

	// Tous les relevés.
	@Query("SELECT * FROM account_statement")
	suspend fun statements(): List<AccountStatementEntity>

	// Tous les cours.
	@Query("SELECT * FROM asset_price")
	suspend fun prices(): List<AssetPriceEntity>

	/*
		Efface toutes les données, dans l'ordre qu'imposent les clés étrangères.
	*/
	@Transaction
	suspend fun deleteAll()
	{
		deleteRecurrences()
		deleteLoans()
		deleteTrades()
		deletePostings()
		deleteTransactions()
		deletePrices()
		deleteStatements()
		deleteAssets()
		deleteCategories()
		deleteAccounts()
	}

	// Efface les opérations récurrentes.
	@Query("DELETE FROM recurrence")
	suspend fun deleteRecurrences()

	// Efface les crédits.
	@Query("DELETE FROM loan")
	suspend fun deleteLoans()

	// Efface les ordres.
	@Query("DELETE FROM trade")
	suspend fun deleteTrades()

	// Efface les jambes.
	@Query("DELETE FROM posting")
	suspend fun deletePostings()

	// Efface les opérations.
	@Query("DELETE FROM txn")
	suspend fun deleteTransactions()

	// Efface les cours.
	@Query("DELETE FROM asset_price")
	suspend fun deletePrices()

	// Efface les relevés.
	@Query("DELETE FROM account_statement")
	suspend fun deleteStatements()

	// Efface les actifs.
	@Query("DELETE FROM asset")
	suspend fun deleteAssets()

	// Efface les catégories.
	@Query("DELETE FROM category")
	suspend fun deleteCategories()

	// Efface les comptes.
	@Query("DELETE FROM account")
	suspend fun deleteAccounts()

	/*
		Ajoute des comptes.
		inAccounts : comptes
	*/
	@Insert
	suspend fun insertAccounts(inAccounts: List<AccountEntity>)

	/*
		Ajoute des relevés.
		inStatements : relevés
	*/
	@Insert
	suspend fun insertStatements(inStatements: List<AccountStatementEntity>)

	/*
		Ajoute des cours.
		inPrices : cours
	*/
	@Insert
	suspend fun insertPrices(inPrices: List<AssetPriceEntity>)

	/*
		Ajoute des en-têtes d'opérations.
		inTxs : opérations
	*/
	@Insert
	suspend fun insertTransactions(inTxs: List<TransactionEntity>)
}

/*
	Accès aux opérations récurrentes et aux crédits.
*/
@Dao
interface PlanningDao
{
	// Opérations récurrentes, observées.
	@Query("SELECT * FROM recurrence ORDER BY label")
	fun observeRecurrences(): Flow<List<RecurrenceEntity>>

	// Crédits, observés.
	@Query("SELECT * FROM loan")
	fun observeLoans(): Flow<List<LoanEntity>>

	// Opérations récurrentes.
	@Query("SELECT * FROM recurrence")
	suspend fun recurrences(): List<RecurrenceEntity>

	// Crédits.
	@Query("SELECT * FROM loan")
	suspend fun loans(): List<LoanEntity>

	/*
		Crée ou remplace des opérations récurrentes.
		inItems : récurrences
	*/
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertRecurrences(inItems: List<RecurrenceEntity>)

	/*
		Crée ou remplace des crédits.
		inItems : crédits
	*/
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertLoans(inItems: List<LoanEntity>)

	/*
		Supprime une opération récurrente.
		inId : récurrence
	*/
	@Query("DELETE FROM recurrence WHERE id = :inId")
	suspend fun deleteRecurrence(inId: String)

	/*
		Supprime un crédit (tableau d'amortissement).
		inId : crédit
	*/
	@Query("DELETE FROM loan WHERE id = :inId")
	suspend fun deleteLoan(inId: String)

	/*
		Supprime les récurrences et crédits liés à un compte supprimé.
		inId : compte
	*/
	@Query("DELETE FROM recurrence WHERE accountId = :inId OR toAccountId = :inId")
	suspend fun deleteRecurrencesOf(inId: String)

	/*
		Supprime les crédits liés à un compte supprimé.
		inId : compte
	*/
	@Query("DELETE FROM loan WHERE accountId = :inId OR payFrom = :inId")
	suspend fun deleteLoansOf(inId: String)

	/*
		Délie les crédits dont le bien financé n'existe plus (achat supprimé ou
		changé en autre chose) ; le crédit reste.
	*/
	@Query("UPDATE loan SET propertyId = NULL WHERE propertyId IS NOT NULL AND propertyId NOT IN (SELECT id FROM txn WHERE kind = 'ACQUISITION')")
	suspend fun unlinkLostProperties()

	/*
		Lie un crédit à un bien, ou le délie.
		inId         : crédit
		inPropertyId : achat du bien ; null pour délier
	*/
	@Query("UPDATE loan SET propertyId = :inPropertyId WHERE id = :inId")
	suspend fun setLoanProperty(inId: String, inPropertyId: String?)

	/*
		Réaffecte la catégorie des récurrences.
		inFrom : catégorie supprimée
		inTo   : catégorie de remplacement
	*/
	@Query("UPDATE recurrence SET categoryId = :inTo WHERE categoryId = :inFrom")
	suspend fun reassignRecurrences(inFrom: String, inTo: String)

	/*
		Réaffecte la catégorie des intérêts des crédits.
		inFrom : catégorie supprimée
		inTo   : catégorie de remplacement
	*/
	@Query("UPDATE loan SET categoryId = :inTo WHERE categoryId = :inFrom")
	suspend fun reassignLoans(inFrom: String, inTo: String)

	/*
		Nombre de récurrences et de crédits qui utilisent une catégorie.
		inId : catégorie
	*/
	@Query("SELECT (SELECT COUNT(*) FROM recurrence WHERE categoryId = :inId) + (SELECT COUNT(*) FROM loan WHERE categoryId = :inId)")
	suspend fun categoryUsage(inId: String): Int

	// Efface toutes les récurrences.
	@Query("DELETE FROM recurrence")
	suspend fun deleteAllRecurrences()

	// Efface tous les crédits.
	@Query("DELETE FROM loan")
	suspend fun deleteAllLoans()
}
