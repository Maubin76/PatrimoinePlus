package fr.maubin.patrimoineplus.data.backup

import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.kChronological
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
import fr.maubin.patrimoineplus.domain.rules.validateAccount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.domain.calc.kMaxTradeCents
import fr.maubin.patrimoineplus.domain.model.Ownership
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileId
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileName
import fr.maubin.patrimoineplus.domain.rules.ownersError
import fr.maubin.patrimoineplus.domain.calc.themeError

const val kBackupFormat  = "patrimoineplus"										// signature du fichier
const val kBackupVersion = 7													// version du format (2 : symbole de cotation des actifs ; 3 : comptes de biens ; 4 : opérations récurrentes et crédits ; 5 : mensualités en une opération, bien financé ; 6 : profils et propriétaires des comptes ; 7 : thèmes de couleurs)

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
	val updatedAt    : Long,
	val owners       : List<OwnerDto> = emptyList()
)

/*
	Part d'un profil dans un compte exporté.
*/
@Serializable
data class OwnerDto(
	val profileId : String,
	val percent   : Int
)

/*
	Thème de couleurs créé par l'utilisateur, exporté.
*/
@Serializable
data class ThemeDto(
	val id        : String,
	val name      : String,
	val colors    : List<String>,
	val sortOrder : Int
)

/*
	Profil (membre de la famille) exporté.
*/
@Serializable
data class ProfileDto(
	val id        : String,
	val name      : String,
	val sortOrder : Int
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
	val trades     : List<TradeDto>   = emptyList(),
	val linkedId   : String?          = null
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
	Opération récurrente exportée.
*/
@Serializable
data class RecurrenceDto(
	val id          : String,
	val label       : String,
	val accountId   : String,
	val toAccountId : String? = null,
	val categoryId  : String? = null,
	val amountCents : Long,
	val frequency   : String,
	val start       : String,
	val end         : String? = null,
	val last        : String? = null,
	val notes       : String? = null
)

/*
	Crédit exporté.
*/
@Serializable
data class LoanDto(
	val id         : String,
	val accountId  : String,
	val principal  : Long,
	val rateBp     : Int,
	val months     : Int,
	val firstDue   : String,
	val insurance  : Long,
	val payFrom    : String,
	val categoryId : String,
	val last       : String? = null,
	val propertyId : String? = null
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
	val prices       : List<PriceDto>,
	val recurrences  : List<RecurrenceDto> = emptyList(),
	val loans        : List<LoanDto>       = emptyList(),
	val profiles     : List<ProfileDto>    = emptyList(),
	val themes       : List<ThemeDto>      = emptyList()
)
{
	// Contenu en nombres d'éléments (fenêtre de conflit : la date d'export n'y a pas de sens).
	val contents: String
		get() = "${accounts.size} compte(s), ${categories.size} catégorie(s), ${transactions.size} opération(s), " +
			"${assets.size} actif(s), ${prices.size} cours, ${statements.size} relevé(s)" +
			(if (recurrences.isNotEmpty()) ", ${recurrences.size} récurrence(s)" else "") +
			(if (loans.isNotEmpty()) ", ${loans.size} crédit(s)" else "")

	// Résumé lisible du contenu et de sa date d'export, pour la confirmation d'import.
	val summary: String
		get() = contents + (runCatching { " ; exporté le " + java.time.LocalDate.parse(exportedAt.take(10)).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(""))
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
	(dans l'ordre canonique : date, puis soldes initiaux, achats, autres, ventes). Au premier problème, tout est refusé.
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
	vDomain.accounts.firstOrNull { validateAccount(it.name, it.sharePercent).isNotEmpty() }?.let {
		return BackupRead.Invalid("Compte « ${it.name} » invalide : nom vide ou part détenue hors de 1 à 100 %.")
	}
	if (vFile.profiles.any { it.name.isBlank() }) return BackupRead.Invalid("Profil sans nom dans le fichier.")
	vFile.themes.firstOrNull { inTheme -> themeError(inTheme.name, inTheme.colors, vFile.themes.filter { it.id != inTheme.id }.map { it.name }) != null }?.let {
		return BackupRead.Invalid("Thème « ${it.name} » invalide : ${themeError(it.name, it.colors, vFile.themes.filter { inOther -> inOther.id != it.id }.map { inOther -> inOther.name })}")
	}
	if (vFile.themes.map { it.id }.let { it.size != it.toSet().size }) return BackupRead.Invalid("Identifiants en double dans le fichier.")
	if (vFile.profiles.map { it.id }.let { it.size != it.toSet().size }) return BackupRead.Invalid("Identifiants en double dans le fichier.")
	val vProfileIds = vFile.profiles.map { it.id }.toSet()						// profils connus
	vFile.accounts.firstOrNull { inAccount -> inAccount.owners.isNotEmpty() && ownersError(inAccount.owners.map { Ownership(it.profileId, it.percent) }, vProfileIds) != null }?.let {
		return BackupRead.Invalid("Compte « ${it.name} » : ${ownersError(it.owners.map { inOwner -> Ownership(inOwner.profileId, inOwner.percent) }, vProfileIds)}")
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
	vDomain.recurrences.firstOrNull { recurrenceError(it, vAccounts, vCategories) != null }?.let {
		return BackupRead.Invalid("Opération récurrente « ${it.label} » invalide : ${recurrenceError(it, vAccounts, vCategories)}")
	}
	val vPurchases  = vDomain.transactions.filter { it.kind == TransactionKind.ACQUISITION }.map { it.id }.toSet()	// achats de biens
	vDomain.loans.firstOrNull { loanError(it, vAccounts, vCategories, vPurchases) != null }?.let {
		return BackupRead.Invalid("Crédit invalide : ${loanError(it, vAccounts, vCategories, vPurchases)}")
	}
	if ((vFile.recurrences.map { it.id } + vFile.loans.map { it.id }).let { it.size != it.toSet().size }) return BackupRead.Invalid("Identifiants en double dans le fichier.")
	if (vFile.prices.any { it.assetId !in vAssets }) return BackupRead.Invalid("Cours d’un actif inconnu.")
	if (vFile.prices.groupBy { it.assetId to it.date }.any { it.value.size > 1 }) return BackupRead.Invalid("Deux cours pour un même actif et un même jour.")
	val vValidator = TransactionValidator(vAccounts, vCategories, vAssets)	// règles de saisie
	val vDone      = mutableListOf<Transaction>()								// opérations déjà vérifiées
	vDomain.transactions.sortedWith(kChronological).forEach { inTx ->
		val vErrors = vValidator.validate(inTx, Ledger(vDone))					// règles violées
		if (vErrors.isNotEmpty())
		{
			return BackupRead.Invalid("Opération invalide « ${inTx.label} » du ${inTx.date} : ${vErrors.first().message}")
		}
		vDone += inTx
	}
	return BackupRead.Valid(vFile.withProfiles())
}

/*
	Fichier complété pour les profils : au moins un profil (« Moi » pour un
	fichier d'avant les profils), chaque compte avec ses propriétaires (le
	premier profil, pour la part détenue) et une part égale à leur total.
*/
fun BackupFile.withProfiles(): BackupFile
{
	val vProfiles = profiles.ifEmpty { listOf(ProfileDto(kDefaultProfileId, kDefaultProfileName, 0)) }	// profils
	val vFirst    = vProfiles.minBy { it.sortOrder }.id								// premier profil
	return copy(
		profiles = vProfiles,
		accounts = accounts.map { inAccount ->
			val vOwners = inAccount.owners.ifEmpty { listOf(OwnerDto(vFirst, inAccount.sharePercent)) }	// propriétaires
			inAccount.copy(owners = vOwners, sharePercent = vOwners.sumOf { it.percent }.coerceIn(1, 100))
		}
	)
}

/*
	Modèles du domaine extraits d'un fichier (pour les vérifications).
	accounts     : comptes
	categories   : catégories
	assets       : actifs
	transactions : opérations
	recurrences  : opérations récurrentes
	loans        : crédits
*/
private data class DomainContent(
	val accounts     : List<Account>,
	val categories   : List<Category>,
	val assets       : List<Asset>,
	val transactions : List<Transaction>,
	val recurrences  : List<Recurrence>,
	val loans        : List<Loan>
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
				trades     = inTx.trades.map { Trade(it.id, it.assetId, TradeSide.valueOf(it.side), it.quantity, it.unitPrice, it.feesCents) },
				linkedId   = inTx.linkedId
			)
		},
		recurrences  = inFile.recurrences.map { it.toDomain() },
		loans        = inFile.loans.map { it.toDomain() }
	).also {
		inFile.statements.forEach { inStatement -> LocalDate.parse(inStatement.date) }
		inFile.prices.forEach { inPrice -> LocalDate.parse(inPrice.date) }
	}

/*
	Opération récurrente du domaine ; lève une exception sur une valeur inconnue.
*/
fun RecurrenceDto.toDomain(): Recurrence =
	Recurrence(id, label, accountId, toAccountId, categoryId, amountCents, Frequency.valueOf(frequency), LocalDate.parse(start), end?.let(LocalDate::parse), last?.let(LocalDate::parse), notes)

/*
	Opération récurrente exportée.
*/
fun Recurrence.toDto(): RecurrenceDto =
	RecurrenceDto(id, label, accountId, toAccountId, categoryId, amountCents, frequency.name, start.toString(), end?.toString(), last?.toString(), notes)

/*
	Crédit du domaine ; lève une exception sur une date illisible.
*/
fun LoanDto.toDomain(): Loan =
	Loan(id, accountId, principal, rateBp, months, LocalDate.parse(firstDue), insurance, payFrom, categoryId, last?.let(LocalDate::parse), propertyId)

/*
	Crédit exporté.
*/
fun Loan.toDto(): LoanDto =
	LoanDto(id, accountId, principal, rateBp, months, firstDue.toString(), insurance, payFrom, categoryId, last?.toString(), propertyId)

/*
	Défaut d'une opération récurrente ; null si elle est valable : libellé,
	comptes connus et différents, catégorie connue pour une dépense ou un
	revenu, montant non nul (positif pour un virement), fin après le début.
	inRec        : opération récurrente
	inAccounts   : comptes par identifiant
	inCategories : catégories par identifiant
*/
fun recurrenceError(
	inRec        : Recurrence,
	inAccounts   : Map<String, Account>,
	inCategories : Map<String, Category>
): String? =
	when
	{
		inRec.label.isBlank()                                              -> "libellé vide."
		inRec.accountId !in inAccounts                                     -> "compte inconnu."
		inRec.toAccountId != null && inRec.toAccountId !in inAccounts      -> "compte d’arrivée inconnu."
		inRec.toAccountId == inRec.accountId                               -> "deux comptes identiques."
		inRec.toAccountId == null && inRec.categoryId !in inCategories     -> "catégorie inconnue."
		inRec.toAccountId != null && inRec.categoryId != null              -> "un virement ne prend pas de catégorie."
		inRec.amountCents == 0L || (inRec.toAccountId != null && inRec.amountCents < 0) -> "montant nul ou négatif."
		inRec.end != null && inRec.end.isBefore(inRec.start)               -> "fin avant le début."
		listOfNotNull(inRec.accountId, inRec.toAccountId).any { inAccounts[it]?.type?.isProperty == true } -> "un compte Immobilier ou Collection ne reçoit pas d’opération récurrente."
		else                                                               -> null
	}

/*
	Défaut d'un crédit ; null s'il est valable : compte de type Crédit,
	compte payeur ordinaire, catégorie de dépense, montant, taux et durée
	plausibles, bien financé connu.
	inLoan       : crédit
	inAccounts   : comptes par identifiant
	inCategories : catégories par identifiant
	inPurchases  : achats de biens connus (identifiants) ; null : bien non vérifié
*/
fun loanError(
	inLoan       : Loan,
	inAccounts   : Map<String, Account>,
	inCategories : Map<String, Category>,
	inPurchases  : Set<String>?          = null
): String? =
	when
	{
		inAccounts[inLoan.accountId]?.type != AccountType.LOAN                       -> "le compte du crédit doit être de type Crédit."
		inAccounts[inLoan.payFrom].let { it == null || it.type == AccountType.LOAN || it.type.isProperty } -> "compte payeur invalide."
		inCategories[inLoan.categoryId]?.kind != CategoryKind.EXPENSE                -> "catégorie des intérêts : choisissez une catégorie de dépense."
		inLoan.principal <= 0 || inLoan.principal >= kMaxTradeCents                  -> "montant emprunté invalide."
		inLoan.rateBp !in 0..3_000                                                   -> "taux hors de 0 à 30 %."
		inLoan.months !in 1..600                                                     -> "durée hors de 1 à 600 mois."
		inLoan.insurance < 0                                                         -> "assurance négative."
		inLoan.propertyId != null && inPurchases != null && inLoan.propertyId !in inPurchases -> "bien financé introuvable."
		else                                                                         -> null
	}
