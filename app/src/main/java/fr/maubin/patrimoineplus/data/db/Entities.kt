package fr.maubin.patrimoineplus.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate

// Tables Room. Aucune ne stocke de solde ni de total : tout se recalcule
// dans la couche domain. Énumérations stockées par leur nom (texte).

/*
	Table des comptes.
*/
@Entity(tableName = "account")
data class AccountEntity(
	@PrimaryKey val id   : String,											// UUID
	val name             : String,											// nom
	val type             : String,											// CHECKING, SAVINGS, BROKERAGE
	val institution      : String?,											// établissement
	val sharePercent     : Int,												// part détenue, 1 à 100
	val sortOrder        : Int,												// ordre d'affichage
	val archived         : Boolean,											// archivé
	val createdAt        : Long,											// création, ms epoch
	val updatedAt        : Long												// modification, ms epoch
)

/*
	Table des catégories.
*/
@Entity(tableName = "category")
data class CategoryEntity(
	@PrimaryKey val id   : String,											// UUID
	val name             : String,											// nom
	val icon             : String,											// clé d'icône
	val kind             : String,											// EXPENSE, INCOME
	val sortOrder        : Int,												// ordre d'affichage
	val archived         : Boolean,											// archivée
	val createdAt        : Long,											// création, ms epoch
	val updatedAt        : Long												// modification, ms epoch
)

/*
	Table des actifs (catalogue partagé entre comptes Bourse).
*/
@Entity(tableName = "asset")
data class AssetEntity(
	@PrimaryKey val id   : String,											// UUID
	val name             : String,											// nom
	val isin             : String?,											// code ISIN
	val archived         : Boolean,											// archivé
	val createdAt        : Long,											// création, ms epoch
	val updatedAt        : Long												// modification, ms epoch
)

/*
	Table des opérations. Une catégorie ou un actif utilisés ne peuvent pas
	être supprimés (RESTRICT) : il faut d'abord réaffecter.
*/
@Entity(
	tableName   = "txn",
	foreignKeys = [
		ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
		ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT)
	],
	indices     = [Index("date"), Index("categoryId"), Index("assetId")]
)
data class TransactionEntity(
	@PrimaryKey val id   : String,											// UUID
	val date             : LocalDate,										// date (texte ISO)
	val kind             : String,											// STANDARD, TRANSFER, TRADE, OPENING
	val label            : String,											// libellé
	val categoryId       : String?,											// catégorie
	val assetId          : String?,											// actif rattaché (dividende)
	val notes            : String?,											// notes
	val createdAt        : Long,											// création, ms epoch
	val updatedAt        : Long												// modification, ms epoch
)

/*
	Table des jambes. Supprimées avec leur opération ; un compte qui a des
	jambes ne peut pas être supprimé au niveau SQL (RESTRICT).
*/
@Entity(
	tableName   = "posting",
	foreignKeys = [
		ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
		ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)
	],
	indices     = [Index("transactionId"), Index("accountId")]
)
data class PostingEntity(
	@PrimaryKey val id   : String,											// UUID
	val transactionId    : String,											// opération
	val accountId        : String,											// compte
	val amountCents      : Long												// montant signé
)

/*
	Table des ordres de bourse, supprimés avec leur opération.
*/
@Entity(
	tableName   = "trade",
	foreignKeys = [
		ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
		ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT)
	],
	indices     = [Index("transactionId"), Index("assetId")]
)
data class TradeEntity(
	@PrimaryKey val id   : String,											// UUID
	val transactionId    : String,											// opération
	val assetId          : String,											// actif
	val side             : String,											// BUY, SELL
	val quantity         : Long,											// dix-millièmes d'unité
	val unitPrice        : Long,											// dix-millièmes d'euro
	val feesCents        : Long												// frais
)

/*
	Table des relevés de solde, supprimés avec leur compte.
*/
@Entity(
	tableName   = "account_statement",
	foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)],
	indices     = [Index("accountId")]
)
data class AccountStatementEntity(
	@PrimaryKey val id   : String,											// UUID
	val accountId        : String,											// compte
	val date             : LocalDate,										// date du relevé
	val balanceCents     : Long												// solde indiqué par la banque
)

/*
	Table des cours relevés, un par actif et par jour, supprimés avec l'actif.
*/
@Entity(
	tableName   = "asset_price",
	foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE)],
	indices     = [Index(value = ["assetId", "date"], unique = true)]
)
data class AssetPriceEntity(
	@PrimaryKey val id   : String,											// UUID
	val assetId          : String,											// actif
	val date             : LocalDate,										// date du cours
	val unitPrice        : Long												// dix-millièmes d'euro
)

/*
	Opération complète : l'opération, ses jambes et ses ordres.
*/
data class TransactionWithDetails(
	@Embedded val transaction : TransactionEntity,							// opération
	@Relation(parentColumn = "id", entityColumn = "transactionId")
	val postings              : List<PostingEntity>,						// jambes
	@Relation(parentColumn = "id", entityColumn = "transactionId")
	val trades                : List<TradeEntity>							// ordres
)
