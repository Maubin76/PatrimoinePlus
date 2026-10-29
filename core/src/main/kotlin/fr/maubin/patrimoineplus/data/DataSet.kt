package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
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
import java.time.LocalDate
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.data.backup.toDomain
import fr.maubin.patrimoineplus.domain.model.Ownership
import fr.maubin.patrimoineplus.domain.model.Profile
import fr.maubin.patrimoineplus.domain.calc.withProfiles
import fr.maubin.patrimoineplus.domain.model.CustomTheme
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.goals.Goals
import fr.maubin.patrimoineplus.data.backup.HistoryDto
import fr.maubin.patrimoineplus.data.backup.DeviceLinkDto

/*
	Toutes les données de l'appli, en modèles du domaine : ce que lisent les
	écrans, que les données viennent de la base (téléphone), d'un fichier
	d'échange (PC) ou de la démonstration.
	accounts     : comptes, dans l'ordre d'affichage
	categories   : catégories
	assets       : actifs
	transactions : opérations
	prices       : cours relevés
	statements   : relevés de solde
	recurrences  : opérations récurrentes
	loans        : crédits
	profiles     : membres de la famille (vide : données d'avant les profils, voir withProfiles)
	themes       : thèmes de couleurs créés par l'utilisateur
	budget       : budget de la famille (PC ; le téléphone le garde sans l'afficher)
	goals        : objectifs par vue (identifiant du profil, ou kFamilyView)
	history      : dernières modifications envoyées par les appareils, de la plus récente à la plus ancienne
	devices      : couples d'appareils appairés (PC ↔ téléphone)
*/
data class DataSet(
	val accounts     : List<Account>,
	val categories   : List<Category>,
	val assets       : List<Asset>,
	val transactions : List<Transaction>,
	val prices       : List<AssetPrice>,
	val statements   : List<AccountStatement>,
	val recurrences  : List<Recurrence> = emptyList(),
	val loans        : List<Loan>       = emptyList(),
	val profiles     : List<Profile>    = emptyList(),
	val themes       : List<CustomTheme> = emptyList(),
	val budget       : Budget             = Budget(),
	val goals        : Map<String, Goals> = emptyMap(),
	val history      : List<HistoryDto>   = emptyList(),
	val devices      : List<DeviceLinkDto> = emptyList()
)
{
	companion object
	{
		// Aucune donnée.
		val kEmpty = DataSet(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
	}
}

/*
	Données d'une sauvegarde déjà vérifiée (voir validateBackup), en modèles du
	domaine ; comptes et catégories dans leur ordre d'affichage.
	inFile : contenu vérifié
*/
fun BackupFile.toDataSet(): DataSet =
	DataSet(
		accounts     = accounts.sortedBy { it.sortOrder }.map { Account(it.id, it.name, AccountType.valueOf(it.type), it.institution, it.sharePercent, it.sortOrder, it.archived, it.owners.map { inOwner -> Ownership(inOwner.profileId, inOwner.percent) }) },
		profiles     = profiles.sortedBy { it.sortOrder }.map { Profile(it.id, it.name, it.sortOrder) },
		themes       = themes.sortedBy { it.sortOrder }.map { CustomTheme(it.id, it.name, it.colors) },
		categories   = categories.sortedBy { it.sortOrder }.map { Category(it.id, it.name, it.icon, CategoryKind.valueOf(it.kind), it.sortOrder, it.archived) },
		assets       = assets.map { Asset(it.id, it.name, it.isin, it.archived, it.ticker) },
		transactions = transactions.map { inTx ->
			Transaction(
				id         = inTx.id,
				date       = LocalDate.parse(inTx.date),
				kind       = TransactionKind.valueOf(inTx.kind),
				label      = inTx.label,
				categoryId = inTx.categoryId,
				assetId    = inTx.assetId,
				notes      = inTx.notes,
				postings   = inTx.postings.map { Posting(it.id, it.accountId, it.amountCents) },
				trades     = inTx.trades.map { Trade(it.id, it.assetId, TradeSide.valueOf(it.side), it.quantity, it.unitPrice, it.feesCents) },
				linkedId   = inTx.linkedId
			)
		},
		prices       = prices.map { AssetPrice(it.id, it.assetId, LocalDate.parse(it.date), it.unitPrice) },
		statements   = statements.map { AccountStatement(it.id, it.accountId, LocalDate.parse(it.date), it.balanceCents) },
		recurrences  = recurrences.map { it.toDomain() },
		loans        = loans.map { it.toDomain() },
		budget       = budgetOf(),
		goals        = goalsByScope(),
		history      = history,
		devices      = devices
	).withProfiles()
