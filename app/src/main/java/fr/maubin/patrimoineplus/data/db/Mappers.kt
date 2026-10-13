package fr.maubin.patrimoineplus.data.db

import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountStatement
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind

// Conversions entre les tables Room et les modèles du domaine.

/*
	Compte de la base vers le domaine.
*/
fun AccountEntity.toDomain(): Account =
	Account(id, name, AccountType.valueOf(type), institution, sharePercent, sortOrder, archived)

/*
	Compte du domaine vers la base.
	inCreatedAt : horodatage de création
	inUpdatedAt : horodatage de modification
*/
fun Account.toEntity(
	inCreatedAt : Long,
	inUpdatedAt : Long
): AccountEntity =
	AccountEntity(id, name, type.name, institution, sharePercent, sortOrder, archived, inCreatedAt, inUpdatedAt)

/*
	Catégorie de la base vers le domaine.
*/
fun CategoryEntity.toDomain(): Category =
	Category(id, name, icon, CategoryKind.valueOf(kind), sortOrder, archived)

/*
	Catégorie du domaine vers la base.
	inCreatedAt : horodatage de création
	inUpdatedAt : horodatage de modification
*/
fun Category.toEntity(
	inCreatedAt : Long,
	inUpdatedAt : Long
): CategoryEntity =
	CategoryEntity(id, name, icon, kind.name, sortOrder, archived, inCreatedAt, inUpdatedAt)

/*
	Actif de la base vers le domaine.
*/
fun AssetEntity.toDomain(): Asset = Asset(id, name, isin, archived, ticker)

/*
	Actif du domaine vers la base.
	inCreatedAt : horodatage de création
	inUpdatedAt : horodatage de modification
*/
fun Asset.toEntity(
	inCreatedAt : Long,
	inUpdatedAt : Long
): AssetEntity =
	AssetEntity(id, name, isin, archived, inCreatedAt, inUpdatedAt, ticker)

/*
	Cours de la base vers le domaine.
*/
fun AssetPriceEntity.toDomain(): AssetPrice = AssetPrice(id, assetId, date, unitPrice)

/*
	Cours du domaine vers la base.
*/
fun AssetPrice.toEntity(): AssetPriceEntity = AssetPriceEntity(id, assetId, date, unitPrice)

/*
	Relevé de la base vers le domaine.
*/
fun AccountStatementEntity.toDomain(): AccountStatement = AccountStatement(id, accountId, date, balanceCents)

/*
	Relevé du domaine vers la base.
*/
fun AccountStatement.toEntity(): AccountStatementEntity = AccountStatementEntity(id, accountId, date, balanceCents)

/*
	Opération complète de la base vers le domaine.
*/
fun TransactionWithDetails.toDomain(): Transaction =
	Transaction(
		id         = transaction.id,
		date       = transaction.date,
		kind       = TransactionKind.valueOf(transaction.kind),
		label      = transaction.label,
		categoryId = transaction.categoryId,
		assetId    = transaction.assetId,
		notes      = transaction.notes,
		postings   = postings.map { Posting(it.id, it.accountId, it.amountCents) },
		trades     = trades.map { Trade(it.id, it.assetId, TradeSide.valueOf(it.side), it.quantity, it.unitPrice, it.feesCents) },
		linkedId   = transaction.linkedId
	)

/*
	En-tête d'une opération du domaine vers la base.
	inCreatedAt : horodatage de création
	inUpdatedAt : horodatage de modification
*/
fun Transaction.toHeaderEntity(
	inCreatedAt : Long,
	inUpdatedAt : Long
): TransactionEntity =
	TransactionEntity(id, date, kind.name, label, categoryId, assetId, notes, inCreatedAt, inUpdatedAt, linkedId)

/*
	Jambes d'une opération du domaine vers la base.
*/
fun Transaction.toPostingEntities(): List<PostingEntity> =
	postings.map { PostingEntity(it.id, id, it.accountId, it.amountCents) }

/*
	Ordres d'une opération du domaine vers la base.
*/
fun Transaction.toTradeEntities(): List<TradeEntity> =
	trades.map { TradeEntity(it.id, id, it.assetId, it.side.name, it.quantity, it.unitPrice, it.feesCents) }
