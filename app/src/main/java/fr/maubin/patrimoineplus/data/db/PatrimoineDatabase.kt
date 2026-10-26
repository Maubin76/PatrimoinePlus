package fr.maubin.patrimoineplus.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

/*
	Conversions de types pour Room : une date est stockée en texte ISO
	(« 2026-10-07 »), ce qui garde le tri et les requêtes par intervalle.
*/
class Converters
{
	/*
		Date vers texte ISO.
		inDate : date
	*/
	@TypeConverter
	fun fromDate(inDate: LocalDate?): String? = inDate?.toString()

	/*
		Texte ISO vers date.
		inText : texte ISO
	*/
	@TypeConverter
	fun toDate(inText: String?): LocalDate? = inText?.let(LocalDate::parse)
}

/*
	Base locale de l'application, seule source de données. Le schéma de chaque
	version est exporté dans app/schemas ; toute évolution passe par une
	migration, pour ne jamais perdre de données.
	Version 2 : symbole de cotation des actifs (colonne ajoutée, migration automatique).
	Version 3 : opération liée (vente d'un bien → son achat ; colonne ajoutée, migration automatique).
	Version 4 : opérations récurrentes et crédits (tables ajoutées, migration automatique).
	Version 5 : bien financé par un crédit (colonne ajoutée, migration automatique).
	Version 6 : profils et propriétaires des comptes (table et colonne ajoutées,
	migration automatique ; base d'avant : un profil « Moi » à la lecture).
	Version 7 : thèmes de couleurs personnalisés (table ajoutée, migration automatique).
*/
@Database(
	entities     = [
		AccountEntity::class,
		CategoryEntity::class,
		AssetEntity::class,
		TransactionEntity::class,
		PostingEntity::class,
		TradeEntity::class,
		AccountStatementEntity::class,
		AssetPriceEntity::class,
		RecurrenceEntity::class,
		LoanEntity::class,
		ProfileEntity::class,
		ThemeEntity::class
	],
	version       = 7,
	exportSchema  = true,
	autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7)]
)
@TypeConverters(Converters::class)
abstract class PatrimoineDatabase : RoomDatabase()
{
	// Accès aux comptes.
	abstract fun accountDao(): AccountDao

	// Accès aux catégories.
	abstract fun categoryDao(): CategoryDao

	// Accès aux opérations.
	abstract fun transactionDao(): TransactionDao

	// Accès aux actifs et aux cours.
	abstract fun assetDao(): AssetDao

	// Accès aux relevés.
	abstract fun statementDao(): StatementDao

	// Accès aux opérations récurrentes et aux crédits.
	abstract fun planningDao(): PlanningDao

	// Accès en bloc pour l'export et l'import.
	abstract fun backupDao(): BackupDao

	companion object
	{
		const val kFileName = "patrimoine.db"									// fichier de la base
	}
}
