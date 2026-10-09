package fr.maubin.patrimoineplus.data.backup

import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.domain.rules.TransactionValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

const val kBackupFormat  = "patrimoineplus"										// signature du fichier
const val kBackupVersion = 2													// version du format (2 : symbole de cotation des actifs)

private val kJson = Json { prettyPrint = true; ignoreUnknownKeys = false }		// lecteur et écrivain JSON

/*
	Compte exporté.
*/
@Serializable
data class AccountDto(
	val id           : String,
	val name         : String,
	val type         : String,
	val institution  : String? = null,
	val sharePercent : Int,
	val sortOrder    : Int,
	val archived     : Boolean,
	val createdAt    : Long,
	val updatedAt    : Long
)

/*
	Catégorie exportée.
*/
@Serializable
data class CategoryDto(
	val id        : String,
	val name      : String,
	val icon      : String,
	val kind      : String,
	val sortOrder : Int,
	val archived  : Boolean,
	val createdAt : Long,
	val updatedAt : Long
)

/*
	Actif exporté.
*/
@Serializable
data class AssetDto(
	val id        : String,
	val name      : String,
	val isin      : String? = null,
	val archived  : Boolean,
	val createdAt : Long,
	val updatedAt : Long,
	val ticker    : String? = null
)

/*
	Jambe exportée.
*/
@Serializable
data class PostingDto(
	val id          : String,
	val accountId   : String,
	val amountCents : Long
)

/*
	Ordre exporté.
*/
@Serializable
data class TradeDto(
	val id        : String,
	val assetId   : String,
	val side      : String,
	val quantity  : Long,
	val unitPrice : Long,
	val feesCents : Long
)

/*
	Opération exportée, avec ses jambes et ses ordres.
*/
@Serializable
data class TransactionDto(
	val id         : String,
	val date       : String,
	val kind       : String,
	val label      : String,
	val categoryId : String?          = null,
	val assetId    : String?          = null,
	val notes      : String?          = null,
	val createdAt  : Long,
	val updatedAt  : Long,
	val postings   : List<PostingDto> = emptyList(),
	val trades     : List<TradeDto>   = emptyList()
)

/*
	Relevé exporté.
*/
@Serializable
data class StatementDto(
	val id           : String,
	val accountId    : String,
	val date         : String,
	val balanceCents : Long
)

/*
	Cours exporté.
*/
@Serializable
data class PriceDto(
	val id        : String,
	val assetId   : String,
	val date      : String,
	val unitPrice : Long
)

/*
	Fichier d'export complet : toute la base, versionnée.
*/
@Serializable
data class BackupFile(
	val format       : String,
	val version      : Int,
	val exportedAt   : String,
	val accounts     : List<AccountDto>,
	val categories   : List<CategoryDto>,
	val assets       : List<AssetDto>,
	val transactions : List<TransactionDto>,
	val statements   : List<StatementDto>,
	val prices       : List<PriceDto>
)
{
	// Résumé lisible du contenu, pour la confirmation d'import.
	val summary: String
		get() = "${accounts.size} compte(s), ${categories.size} catégorie(s), ${transactions.size} opération(s), " +
			"${assets.size} actif(s), ${prices.size} cours, ${statements.size} relevé(s) ; exporté le ${exportedAt.take(10)}"
}

/*
	Résultat de la lecture d'un fichier d'export.
*/
sealed interface BackupRead
{
	/*
		Fichier valide, prêt à importer.
		file : contenu
	*/
	data class Valid(val file: BackupFile) : BackupRead

	/*
		Fichier refusé ; rien ne sera importé.
		message : raison, pour l'utilisateur
	*/
	data class Invalid(val message: String) : BackupRead
}

/*
	Écrit un fichier d'export en JSON lisible.
	inFile : contenu
*/
fun encodeBackup(inFile: BackupFile): String = kJson.encodeToString(BackupFile.serializer(), inFile)

/*
	Lit et vérifie un ancien export JSON (voir validateBackup).
	inText : contenu du fichier
*/
fun decodeBackup(inText: String): BackupRead
{
	val vFile = try
	{
		kJson.decodeFromString(BackupFile.serializer(), inText)
	}
	catch (inError: Exception)
	{
		return BackupRead.Invalid("Fichier illisible : ce n’est pas un export PatrimoinePlus valide.")
	}
	return validateBackup(vFile)
}

/*
	Vérifie un contenu de sauvegarde, quelle que soit sa forme d'origine
	(classeur ou JSON) : signature, version, valeurs connues, identifiants
	uniques, références existantes, et règles de saisie de chaque opération
	(dans l'ordre chronologique). Au premier problème, tout est refusé.
	inFile : contenu lu
*/
fun validateBackup(inFile: BackupFile): BackupRead
{
	val vFile = inFile															// contenu vérifié
	if (vFile.format != kBackupFormat)
	{
		return BackupRead.Invalid("Ce fichier n’est pas un export PatrimoinePlus.")
	}
	if (vFile.version > kBackupVersion)
	{
		return BackupRead.Invalid("Export produit par une version plus récente de l’appli (format ${vFile.version}).")
	}
	val vDomain = try
	{
		toDomain(vFile)
	}
	catch (inError: IllegalArgumentException)
	{
		return BackupRead.Invalid("Valeur inconnue dans le fichier : ${inError.message}")
	}
	catch (inError: java.time.format.DateTimeParseException)
	{
		return BackupRead.Invalid("Date illisible dans le fichier : ${inError.parsedString}")
	}
	val vIds = vFile.accounts.map { it.id } + vFile.categories.map { it.id } + vFile.assets.map { it.id } +
		vFile.transactions.map { it.id } + vFile.transactions.flatMap { inTx -> inTx.postings.map { it.id } + inTx.trades.map { it.id } } +
		vFile.statements.map { it.id } + vFile.prices.map { it.id }			// tous les identifiants
	if (vIds.size != vIds.toSet().size)
	{
		return BackupRead.Invalid("Identifiants en double dans le fichier.")
	}
	val vAccounts   = vDomain.accounts.associateBy { it.id }					// comptes connus
	val vCategories = vDomain.categories.associateBy { it.id }				// catégories connues
	val vAssets     = vDomain.assets.associateBy { it.id }						// actifs connus
	vFile.transactions.forEach { inTx ->
		if (inTx.categoryId != null && inTx.categoryId !in vCategories) return BackupRead.Invalid("Catégorie inconnue dans « ${inTx.label} ».")
		if (inTx.assetId != null && inTx.assetId !in vAssets) return BackupRead.Invalid("Actif inconnu dans « ${inTx.label} ».")
		if (inTx.postings.any { it.accountId !in vAccounts }) return BackupRead.Invalid("Compte inconnu dans « ${inTx.label} ».")
		if (inTx.trades.any { it.assetId !in vAssets }) return BackupRead.Invalid("Actif inconnu dans « ${inTx.label} ».")
	}
	if (vFile.statements.any { it.accountId !in vAccounts }) return BackupRead.Invalid("Relevé d’un compte inconnu.")
	if (vFile.prices.any { it.assetId !in vAssets }) return BackupRead.Invalid("Cours d’un actif inconnu.")
	if (vFile.prices.groupBy { it.assetId to it.date }.any { it.value.size > 1 }) return BackupRead.Invalid("Deux cours pour un même actif et un même jour.")
	val vValidator = TransactionValidator(vAccounts, vCategories, vAssets)	// règles de saisie
	val vDone      = mutableListOf<Transaction>()								// opérations déjà vérifiées
	vDomain.transactions.sortedBy { it.date }.forEach { inTx ->
		val vErrors = vValidator.validate(inTx, Ledger(vDone))					// règles violées
		if (vErrors.isNotEmpty())
		{
			return BackupRead.Invalid("Opération invalide « ${inTx.label} » du ${inTx.date} : ${vErrors.first().message}")
		}
		vDone += inTx
	}
	return BackupRead.Valid(vFile)
}

/*
	Modèles du domaine extraits d'un fichier (pour les vérifications).
	accounts     : comptes
	categories   : catégories
	assets       : actifs
	transactions : opérations
*/
private data class DomainContent(
	val accounts     : List<Account>,
	val categories   : List<Category>,
	val assets       : List<Asset>,
	val transactions : List<Transaction>
)

/*
	Convertit le fichier en modèles du domaine ; lève une exception sur une
	valeur d'énumération ou une date inconnue.
	inFile : contenu
*/
private fun toDomain(inFile: BackupFile): DomainContent =
	DomainContent(
		accounts     = inFile.accounts.map { Account(it.id, it.name, AccountType.valueOf(it.type), it.institution, it.sharePercent, it.sortOrder, it.archived) },
		categories   = inFile.categories.map { Category(it.id, it.name, it.icon, CategoryKind.valueOf(it.kind), it.sortOrder, it.archived) },
		assets       = inFile.assets.map { Asset(it.id, it.name, it.isin, it.archived, it.ticker) },
		transactions = inFile.transactions.map { inTx ->
			Transaction(
				id         = inTx.id,
				date       = LocalDate.parse(inTx.date),
				kind       = TransactionKind.valueOf(inTx.kind),
				label      = inTx.label,
				categoryId = inTx.categoryId,
				assetId    = inTx.assetId,
				notes      = inTx.notes,
				postings   = inTx.postings.map { Posting(it.id, it.accountId, it.amountCents) },
				trades     = inTx.trades.map { Trade(it.id, it.assetId, TradeSide.valueOf(it.side), it.quantity, it.unitPrice, it.feesCents) }
			)
		}
	).also {
		inFile.statements.forEach { inStatement -> LocalDate.parse(inStatement.date) }
		inFile.prices.forEach { inPrice -> LocalDate.parse(inPrice.date) }
	}
