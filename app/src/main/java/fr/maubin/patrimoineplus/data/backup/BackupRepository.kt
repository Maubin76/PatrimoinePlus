package fr.maubin.patrimoineplus.data.backup

import androidx.room.withTransaction
import fr.maubin.patrimoineplus.data.db.AccountEntity
import fr.maubin.patrimoineplus.data.db.AccountStatementEntity
import fr.maubin.patrimoineplus.data.db.AssetEntity
import fr.maubin.patrimoineplus.data.db.AssetPriceEntity
import fr.maubin.patrimoineplus.data.db.CategoryEntity
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.db.PostingEntity
import fr.maubin.patrimoineplus.data.db.TradeEntity
import fr.maubin.patrimoineplus.data.db.TransactionEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import fr.maubin.patrimoineplus.data.backup.toDto
import fr.maubin.patrimoineplus.data.backup.toDomain
import fr.maubin.patrimoineplus.data.db.toDomain
import fr.maubin.patrimoineplus.data.db.toEntity
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import fr.maubin.patrimoineplus.domain.calc.decodeShares
import fr.maubin.patrimoineplus.domain.calc.encodeShares
import fr.maubin.patrimoineplus.data.backup.OwnerDto
import fr.maubin.patrimoineplus.data.backup.ProfileDto
import fr.maubin.patrimoineplus.data.backup.withProfiles
import fr.maubin.patrimoineplus.data.db.ProfileEntity
import fr.maubin.patrimoineplus.domain.model.Ownership
import fr.maubin.patrimoineplus.data.backup.ThemeDto
import fr.maubin.patrimoineplus.data.db.ThemeEntity
import fr.maubin.patrimoineplus.data.backup.decodePlans
import fr.maubin.patrimoineplus.data.backup.encodePlans
import fr.maubin.patrimoineplus.data.backup.PlansDto
import fr.maubin.patrimoineplus.data.db.PlanEntity
import fr.maubin.patrimoineplus.data.backup.HistoryDto
import fr.maubin.patrimoineplus.data.backup.DeviceLinkDto
import fr.maubin.patrimoineplus.data.sync.joinDevices

/*
	Export et import de toute la base, sous forme de fichier JSON versionné.
	L'import remplace toutes les données en une seule transaction, et seulement
	après vérification complète du fichier (tout ou rien).
	inDb    : base locale
	inClock : horloge (date de l'export)
*/
@Singleton
class BackupRepository @Inject constructor(
	private val inDb    : PatrimoineDatabase,
	private val inClock : Clock
)
{
	private val fDao = inDb.backupDao()											// accès en bloc

	/*
		Instantané complet de la base, lu dans une seule transaction.
	*/
	suspend fun snapshot(): BackupFile =
		inDb.withTransaction {
			BackupFile(
				format       = kBackupFormat,
				version      = kBackupVersion,
				exportedAt   = Instant.now(inClock).toString(),
				accounts     = fDao.accounts().map { AccountDto(it.id, it.name, it.type, it.institution, it.sharePercent, it.sortOrder, it.archived, it.createdAt, it.updatedAt, decodeShares(it.owners).map { inOwner -> OwnerDto(inOwner.profileId, inOwner.percent) }) },
				profiles     = fDao.profiles().map { ProfileDto(it.id, it.name, it.sortOrder) },
				themes       = fDao.themes().map { ThemeDto(it.id, it.name, it.colors.split(";"), it.sortOrder) },
				categories   = fDao.categories().map { CategoryDto(it.id, it.name, it.icon, it.kind, it.sortOrder, it.archived, it.createdAt, it.updatedAt) },
				assets       = fDao.assets().map { AssetDto(it.id, it.name, it.isin, it.archived, it.createdAt, it.updatedAt, it.ticker) },
				transactions = inDb.transactionDao().getAll().map { inTx ->
					val vHead = inTx.transaction										// en-tête
					TransactionDto(
						vHead.id, vHead.date.toString(), vHead.kind, vHead.label, vHead.categoryId, vHead.assetId, vHead.notes, vHead.createdAt, vHead.updatedAt,
						inTx.postings.map { PostingDto(it.id, it.accountId, it.amountCents) },
						inTx.trades.map { TradeDto(it.id, it.assetId, it.side, it.quantity, it.unitPrice, it.feesCents) },
						vHead.linkedId
					)
				},
				statements   = fDao.statements().map { StatementDto(it.id, it.accountId, it.date.toString(), it.balanceCents) },
				prices       = fDao.prices().map { PriceDto(it.id, it.assetId, it.date.toString(), it.unitPrice) },
				recurrences  = inDb.planningDao().recurrences().map { it.toDomain().toDto() },
				loans        = inDb.planningDao().loans().map { it.toDomain().toDto() }
			).let { inFile ->
				val vPlans = fDao.plans()?.let(::decodePlans) ?: return@let inFile				// budget et objectifs du PC, gardés tels quels
				inFile.copy(budget = vPlans.budget, goals = vPlans.goals, monthGoals = vPlans.monthGoals, history = vPlans.history, devices = vPlans.devices)
			}.withProfiles()														// base d'avant les profils : profil « Moi »
		}

	/*
		Remplace l'historique des modifications gardé avec le budget et les
		objectifs (après un envoi de ce téléphone).
		inHistory : historique
	*/
	suspend fun setHistory(inHistory: List<HistoryDto>)
	{
		inDb.withTransaction {
			val vPlans = fDao.plans()?.let(::decodePlans) ?: PlansDto()						// budget, objectifs, historique
			fDao.insertPlan(PlanEntity("plans", encodePlans(vPlans.copy(history = inHistory))))
		}
	}

	/*
		Réunit des couples d'appareils à ceux gardés avec le budget (appairage,
		synchronisation ou retrait d'un PC).
		inLinks : couples
	*/
	suspend fun addDevices(inLinks: List<DeviceLinkDto>)
	{
		inDb.withTransaction {
			val vPlans = fDao.plans()?.let(::decodePlans) ?: PlansDto()						// budget, objectifs, historique, couples
			fDao.insertPlan(PlanEntity("plans", encodePlans(vPlans.copy(devices = joinDevices(vPlans.devices, inLinks)))))
		}
	}

	/*
		Remplace toutes les données par celles d'un fichier déjà vérifié.
		inFile : contenu valide (voir decodeBackup)
	*/
	suspend fun replaceAll(
		inFile     : BackupFile,
		inExpected : String? = null
	): Boolean =
		inDb.withTransaction {
			if (inExpected != null && syncFingerprint(snapshot()) != inExpected) return@withTransaction false	// données modifiées entre-temps : rien n'est remplacé
			fDao.deleteAll()
			fDao.insertProfiles(inFile.profiles.map { ProfileEntity(it.id, it.name, it.sortOrder) })
			fDao.insertThemes(inFile.themes.map { ThemeEntity(it.id, it.name, it.colors.joinToString(";"), it.sortOrder) })
			fDao.insertAccounts(inFile.accounts.map { AccountEntity(it.id, it.name, it.type, it.institution, it.sharePercent, it.sortOrder, it.archived, it.createdAt, it.updatedAt, encodeShares(it.owners.map { inOwner -> Ownership(inOwner.profileId, inOwner.percent) })) })
			inDb.categoryDao().insertAll(inFile.categories.map { CategoryEntity(it.id, it.name, it.icon, it.kind, it.sortOrder, it.archived, it.createdAt, it.updatedAt) })
			inDb.assetDao().insertAll(inFile.assets.map { AssetEntity(it.id, it.name, it.isin, it.archived, it.createdAt, it.updatedAt, it.ticker) })
			fDao.insertTransactions(inFile.transactions.map { TransactionEntity(it.id, LocalDate.parse(it.date), it.kind, it.label, it.categoryId, it.assetId, it.notes, it.createdAt, it.updatedAt, it.linkedId) })
			inDb.transactionDao().insertPostings(inFile.transactions.flatMap { inTx -> inTx.postings.map { PostingEntity(it.id, inTx.id, it.accountId, it.amountCents) } })
			inDb.transactionDao().insertTrades(inFile.transactions.flatMap { inTx -> inTx.trades.map { TradeEntity(it.id, inTx.id, it.assetId, it.side, it.quantity, it.unitPrice, it.feesCents) } })
			fDao.insertStatements(inFile.statements.map { AccountStatementEntity(it.id, it.accountId, LocalDate.parse(it.date), it.balanceCents) })
			fDao.insertPrices(inFile.prices.map { AssetPriceEntity(it.id, it.assetId, LocalDate.parse(it.date), it.unitPrice) })
			inDb.planningDao().upsertRecurrences(inFile.recurrences.map { it.toDomain().toEntity() })
			inDb.planningDao().upsertLoans(inFile.loans.map { it.toDomain().toEntity() })
			if (inFile.plans != PlansDto()) fDao.insertPlan(PlanEntity("plans", encodePlans(inFile.plans)))
			true
		}
}
