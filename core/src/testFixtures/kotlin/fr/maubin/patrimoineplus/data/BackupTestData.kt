package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.AccountDto
import fr.maubin.patrimoineplus.data.backup.AssetDto
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.CategoryDto
import fr.maubin.patrimoineplus.data.backup.PostingDto
import fr.maubin.patrimoineplus.data.backup.PriceDto
import fr.maubin.patrimoineplus.data.backup.StatementDto
import fr.maubin.patrimoineplus.data.backup.TradeDto
import fr.maubin.patrimoineplus.data.backup.TransactionDto
import fr.maubin.patrimoineplus.data.backup.kBackupFormat
import fr.maubin.patrimoineplus.data.backup.kBackupVersion
import fr.maubin.patrimoineplus.data.demo.buildDemoData
import java.time.LocalDate

/*
	Fichier d'export construit à partir des données de démonstration.
*/
fun demoBackupFile(): BackupFile = backupFileOf(buildDemoData(LocalDate.parse("2026-10-08")))

/*
	Fichier d'export construit à partir de données du domaine.
	inData : données
*/
fun backupFileOf(inData: DataSet): BackupFile
{
	val vDemo = inData															// données exportées
	return BackupFile(
		format       = kBackupFormat,
		version      = kBackupVersion,
		exportedAt   = "2026-10-08T10:00:00Z",
		accounts     = vDemo.accounts.map { AccountDto(it.id, it.name, it.type.name, it.institution, it.sharePercent, it.sortOrder, it.archived, 1, 2) },
		categories   = vDemo.categories.map { CategoryDto(it.id, it.name, it.icon, it.kind.name, it.sortOrder, it.archived, 1, 2) },
		assets       = vDemo.assets.map { AssetDto(it.id, it.name, it.isin, it.archived, 1, 2, it.ticker) },
		transactions = vDemo.transactions.map { inTx ->
			TransactionDto(inTx.id, inTx.date.toString(), inTx.kind.name, inTx.label, inTx.categoryId, inTx.assetId, inTx.notes, 1, 2,
				inTx.postings.map { PostingDto(it.id, it.accountId, it.amountCents) },
				inTx.trades.map { TradeDto(it.id, it.assetId, it.side.name, it.quantity, it.unitPrice, it.feesCents) }, inTx.linkedId)
		},
		statements   = vDemo.statements.map { StatementDto(it.id, it.accountId, it.date.toString(), it.balanceCents) },
		prices       = vDemo.prices.map { PriceDto(it.id, it.assetId, it.date.toString(), it.unitPrice) }
	)
}
