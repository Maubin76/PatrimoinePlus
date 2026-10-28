package fr.maubin.patrimoineplus.data.backup

import fr.maubin.patrimoineplus.data.backup.xlsx.Cell
import fr.maubin.patrimoineplus.data.backup.xlsx.CellStyle
import fr.maubin.patrimoineplus.data.backup.xlsx.Column
import fr.maubin.patrimoineplus.data.backup.xlsx.ReadSheet
import fr.maubin.patrimoineplus.data.backup.xlsx.Sheet
import fr.maubin.patrimoineplus.data.backup.xlsx.fromExcelSerial
import fr.maubin.patrimoineplus.data.backup.xlsx.readXlsx
import fr.maubin.patrimoineplus.data.backup.xlsx.writeXlsx
import fr.maubin.patrimoineplus.domain.calc.kPriceScale
import fr.maubin.patrimoineplus.domain.calc.kQuantityScale
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.domain.calc.kThemeRoles
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.goals.GoalKind
import fr.maubin.patrimoineplus.ui.goals.MonthGoalKind
import fr.maubin.patrimoineplus.domain.calc.kFamilyView

// Intitulés des colonnes : ils servent aussi de clés à la lecture (l'ordre des colonnes est libre).
private const val kDate        = "Date"										// date
private const val kAccount     = "Compte"										// nom du compte
private const val kLabel       = "Libellé"										// libellé
private const val kNature      = "Nature"										// nature de l'opération
private const val kCategory    = "Catégorie"									// nom de la catégorie
private const val kAmount      = "Montant"										// montant en euros
private const val kAsset       = "Actif"										// nom de l'actif
private const val kSide        = "Sens"											// achat ou vente
private const val kQuantity    = "Quantité"										// quantité
private const val kPrice       = "Cours"										// cours unitaire
private const val kFees        = "Frais"										// frais de l'ordre
private const val kNotes       = "Notes"										// notes
private const val kName        = "Nom"											// nom
private const val kType        = "Type"											// type de compte
private const val kInstitution = "Établissement"								// établissement
private const val kShare       = "Part détenue (%)"							// part détenue (total des propriétaires)
private const val kOwners      = "Propriétaires"								// propriétaires et leur part, en clair (format 6)
private const val kShares      = "Parts"										// parts par identifiant de profil, « id=50;id=50 » (format 6)
private const val kProfileId   = "ID profil"									// identifiant du profil (format 6)
private const val kArchived    = "Archivé"										// archivé (Oui / Non)
private const val kKind        = "Nature"										// nature d'une catégorie
private const val kIcon        = "Icône"										// icône d'une catégorie
private const val kIsin        = "ISIN"											// code ISIN
private const val kTicker      = "Ticker"										// symbole de cotation
private const val kBalance     = "Solde"										// solde d'un relevé
private const val kKey         = "Clé"											// clé de la page Fichier
private const val kValue       = "Valeur"										// valeur de la page Fichier
private const val kTxId        = "ID opération"								// identifiant de l'opération
private const val kPostingId   = "ID ligne"										// identifiant de la jambe
private const val kTradeId     = "ID ordre"										// identifiant de l'ordre
private const val kAccountId   = "ID compte"									// identifiant du compte
private const val kCategoryId  = "ID catégorie"								// identifiant de la catégorie
private const val kAssetId     = "ID actif"										// identifiant de l'actif
private const val kLinkedAsset = "ID actif rattaché"							// actif rattaché à l'opération
private const val kLinkedTx    = "ID opération liée"							// achat d'un bien vendu
private const val kStatementId = "ID relevé"									// identifiant du relevé
private const val kPriceId     = "ID cours"										// identifiant du cours
private const val kSortOrder   = "Ordre"										// rang d'affichage
private const val kCreated     = "Créé (ms)"									// création, en millisecondes
private const val kUpdated     = "Modifié (ms)"								// modification, en millisecondes

private const val kSheetAccounts   = "Comptes"									// onglet des comptes
private const val kSheetCategories = "Catégories"								// onglet des catégories
private const val kSheetAssets     = "Actifs"									// onglet des actifs
private const val kSheetPrices     = "Cours"										// onglet des cours
private const val kSheetStatements = "Relevés"									// onglet des relevés
private const val kSheetFile       = "Fichier"									// onglet de signature
private const val kSheetRecurring  = "Récurrences"								// onglet des opérations récurrentes (format 4)
private const val kSheetLoans      = "Crédits"									// onglet des crédits (format 4)
private const val kSheetProfiles   = "Profils"									// onglet des profils (format 6)
private const val kSheetThemes     = "Thèmes"										// onglet des thèmes de couleurs (format 7)
private const val kThemeId         = "ID thème"									// identifiant du thème (format 7)
private const val kSheetBudget     = "Budget"									// onglet du budget de la famille (format 8)
private const val kSheetGoals      = "Objectifs"									// onglet des objectifs globaux (format 8)
private const val kSheetMonthGoals = "Objectifs du mois"							// onglet des objectifs fixés mois par mois (format 8)
private const val kSection         = "Rubrique"									// rubrique du budget
private const val kTheme           = "Thème"										// thème d'une dépense
private const val kMember          = "Membre"										// membre de la famille d'un investissement
private const val kView            = "Vue"										// vue d'un objectif (profil ou Famille)
private const val kTitle           = "Intitulé"									// intitulé d'un objectif
private const val kTarget          = "Cible"										// cible (euros, ou % pour un taux d'épargne)
private const val kCurrent         = "Avancement"								// avancement saisi à la main
private const val kDeadline        = "Échéance"									// échéance d'un objectif
private const val kDone            = "Fait"										// objectif à cocher fait (Oui / Non)
private const val kMonth           = "Mois"										// mois d'un objectif du mois (AAAA-MM)
private const val kConfirmed       = "Fixé le"									// date où le mois a été fixé
private const val kBudgetId        = "ID budget"									// identifiant de la ligne du budget
private const val kGoalId          = "ID objectif"								// identifiant de l'objectif
private const val kScopeId         = "ID vue"										// vue de l'objectif : identifiant du profil, « * » pour la famille
private const val kFamilyLabel     = "Famille"									// vue de la famille, en clair
private const val kSavingLabel     = "Épargne"									// objectif d'épargne du mois, en clair
private const val kSpendingLabel   = "Dépenses"									// objectif de dépenses du mois, en clair
private const val kProperty        = "Bien financé"								// bien financé par un crédit (format 5)
private const val kPropertyId      = "ID bien"									// identifiant de l'achat du bien financé (format 5)
private const val kTo           = "Vers"											// compte d'arrivée d'un virement
private const val kFrequency    = "Fréquence"									// fréquence d'une récurrence
private const val kStart        = "Début"										// première échéance
private const val kEnd          = "Fin"											// dernière échéance possible
private const val kLast         = "Dernière échéance créée"						// dernière échéance créée
private const val kPrincipal    = "Montant emprunté"								// capital emprunté
private const val kRate         = "Taux (%)"										// taux annuel
private const val kMonths       = "Durée (mois)"									// nombre de mensualités
private const val kFirstDue     = "Première mensualité"							// date de la première mensualité
private const val kInsurance    = "Assurance mensuelle"							// assurance par mois
private const val kPayFrom      = "Payé depuis"									// compte payeur
private const val kRecurrenceId = "ID récurrence"								// identifiant de la récurrence
private const val kLoanId       = "ID crédit"									// identifiant du crédit
private const val kToId         = "ID compte d'arrivée"							// compte d'arrivée
private const val kPayFromId    = "ID compte payeur"								// compte payeur

private const val kKeyFormat   = "Format"										// clé : signature
private const val kKeyVersion  = "Version"										// clé : version
private const val kKeyExported = "Exporté le"									// clé : date d'export

private val kSlashDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")			// date saisie à la française

/*
	Fichier de sauvegarde refusé à la lecture du classeur.
	inMessage : raison, pour l'utilisateur
*/
class WorkbookException(inMessage: String) : Exception(inMessage)

/*
	Écrit une sauvegarde sous forme de classeur .xlsx.
	inFile : contenu
	inOut  : flux de sortie (non fermé)
*/
fun writeBackupXlsx(
	inFile : BackupFile,
	inOut  : OutputStream
)
{
	writeXlsx(backupSheets(inFile), inOut)
}

/*
	Lit et vérifie une sauvegarde au format classeur (voir validateBackup).
	inStream : contenu du fichier
*/
fun readBackupXlsx(inStream: InputStream): BackupRead
{
	val vSheets = try
	{
		readXlsx(inStream)
	}
	catch (inError: Exception)
	{
		return BackupRead.Invalid("Fichier illisible : ce n’est pas un classeur Excel valide.")
	}
	val vFile = try
	{
		backupFromSheets(vSheets)
	}
	catch (inError: WorkbookException)
	{
		return BackupRead.Invalid(inError.message ?: "Classeur incomplet.")
	}
	catch (inError: Exception)
	{
		return BackupRead.Invalid("Valeur illisible dans le classeur : ${inError.message}")
	}
	return validateBackup(vFile)
}

/*
	Feuilles du classeur : une par type de compte (une ligne par mouvement sur
	un compte de ce type, les plus récents en tête), puis comptes, catégories,
	actifs, cours, relevés et la page de signature. Les identifiants sont dans
	des colonnes techniques repliées : ils permettent la restauration exacte.
	inFile : contenu
*/
internal fun backupSheets(inFile: BackupFile): List<Sheet>
{
	val vAccounts   = inFile.accounts.associateBy { it.id }						// comptes par identifiant
	val vCategories = inFile.categories.associateBy { it.id }					// catégories par identifiant
	val vAssets     = inFile.assets.associateBy { it.id }						// actifs par identifiant
	val vByType     = AccountType.entries.associateWith { mutableListOf<List<Cell>>() }	// lignes par type
	inFile.transactions.sortedWith(compareByDescending<TransactionDto> { it.date }.thenBy { it.id }).forEach { inTx ->
		val vTradeType = inTx.postings.firstOrNull()?.let { vAccounts[it.accountId]?.type }?.let { AccountType.valueOf(it) }	// onglet des ordres
		var vTrades    = inTx.trades															// ordres restant à écrire
		inTx.postings.forEach { inPosting ->
			val vAccount = vAccounts[inPosting.accountId]										// compte de la jambe
			val vType    = vAccount?.let { AccountType.valueOf(it.type) } ?: AccountType.CHECKING	// onglet de la jambe
			val vTrade   = if (vType == vTradeType) vTrades.firstOrNull() else null			// ordre fusionné à la ligne
			if (vTrade != null) vTrades = vTrades.drop(1)
			vByType.getValue(vType) += operationRow(inTx, inPosting, vTrade, vAccount?.name, vCategories, vAssets, vType)
		}
		vTrades.forEach { inTrade ->
			vByType.getValue(vTradeType ?: AccountType.BROKERAGE) += operationRow(inTx, null, inTrade, null, vCategories, vAssets, vTradeType ?: AccountType.BROKERAGE)
		}
	}
	return AccountType.entries.map { inType -> Sheet(inType.label, operationColumns(inType), vByType.getValue(inType)) } + listOf(
		Sheet(
			kSheetAccounts,
			listOf(Column(kName, 28.0), Column(kType, 12.0), Column(kInstitution, 22.0), Column(kShare, 16.0), Column(kOwners, 30.0), Column(kArchived, 10.0)) +
				tech(kAccountId, kSortOrder, kCreated, kUpdated, kShares),
			inFile.accounts.sortedBy { it.sortOrder }.map { inAccount ->
				listOf(
					text(inAccount.name), text(AccountType.valueOf(inAccount.type).label), text(inAccount.institution.orEmpty()),
					Cell.Number(BigDecimal(inAccount.sharePercent)),
					text(inAccount.owners.joinToString(", ") { inOwner -> "${inFile.profiles.firstOrNull { it.id == inOwner.profileId }?.name ?: "?"} ${inOwner.percent} %" }),
					text(yesNo(inAccount.archived)),
					techText(inAccount.id), techNumber(inAccount.sortOrder.toLong()), techNumber(inAccount.createdAt), techNumber(inAccount.updatedAt),
					techText(inAccount.owners.joinToString(";") { "${it.profileId}=${it.percent}" })
				)
			}
		),
		Sheet(
			kSheetCategories,
			listOf(Column(kName, 28.0), Column(kKind, 12.0), Column(kIcon, 12.0), Column(kArchived, 10.0)) +
				tech(kCategoryId, kSortOrder, kCreated, kUpdated),
			inFile.categories.sortedWith(compareBy({ it.kind }, { it.sortOrder })).map { inCategory ->
				listOf(
					text(inCategory.name), text(kindLabel(inCategory.kind)), text(inCategory.icon), text(yesNo(inCategory.archived)),
					techText(inCategory.id), techNumber(inCategory.sortOrder.toLong()), techNumber(inCategory.createdAt), techNumber(inCategory.updatedAt)
				)
			}
		),
		Sheet(
			kSheetAssets,
			listOf(Column(kName, 32.0), Column(kIsin, 16.0), Column(kTicker, 12.0), Column(kArchived, 10.0)) + tech(kAssetId, kCreated, kUpdated),
			inFile.assets.sortedBy { it.name }.map { inAsset ->
				listOf(
					text(inAsset.name), text(inAsset.isin.orEmpty()), text(inAsset.ticker.orEmpty()), text(yesNo(inAsset.archived)),
					techText(inAsset.id), techNumber(inAsset.createdAt), techNumber(inAsset.updatedAt)
				)
			}
		),
		Sheet(
			kSheetPrices,
			listOf(Column(kDate, 12.0), Column(kAsset, 32.0), Column(kPrice, 14.0)) + tech(kPriceId, kAssetId),
			inFile.prices.sortedWith(compareByDescending<PriceDto> { it.date }.thenBy { vAssets[it.assetId]?.name }).map { inPrice ->
				listOf(
					Cell.Date(LocalDate.parse(inPrice.date)), text(vAssets[inPrice.assetId]?.name.orEmpty()), scaled(inPrice.unitPrice, kPriceScale, CellStyle.PRICE),
					techText(inPrice.id), techText(inPrice.assetId)
				)
			}
		),
		Sheet(
			kSheetStatements,
			listOf(Column(kDate, 12.0), Column(kAccount, 28.0), Column(kBalance, 16.0)) + tech(kStatementId, kAccountId),
			inFile.statements.sortedWith(compareByDescending<StatementDto> { it.date }.thenBy { vAccounts[it.accountId]?.name }).map { inStatement ->
				listOf(
					Cell.Date(LocalDate.parse(inStatement.date)), text(vAccounts[inStatement.accountId]?.name.orEmpty()), euros(inStatement.balanceCents),
					techText(inStatement.id), techText(inStatement.accountId)
				)
			}
		),
		Sheet(
			kSheetRecurring,
			listOf(Column(kLabel, 30.0), Column(kAccount, 22.0), Column(kTo, 22.0), Column(kCategory, 20.0), Column(kAmount, 15.0), Column(kFrequency, 16.0),
				Column(kStart, 12.0), Column(kEnd, 12.0), Column(kLast, 14.0), Column(kNotes, 30.0)) + tech(kRecurrenceId, kAccountId, kToId, kCategoryId),
			inFile.recurrences.sortedBy { it.label }.map { inRec ->
				listOf(
					text(inRec.label), text(vAccounts[inRec.accountId]?.name.orEmpty()), text(inRec.toAccountId?.let { vAccounts[it]?.name }.orEmpty()),
					text(inRec.categoryId?.let { vCategories[it]?.name }.orEmpty()), euros(inRec.amountCents),
					text(Frequency.valueOf(inRec.frequency).label), Cell.Date(LocalDate.parse(inRec.start)),
					inRec.end?.let { Cell.Date(LocalDate.parse(it)) } ?: Cell.Empty, inRec.last?.let { Cell.Date(LocalDate.parse(it)) } ?: Cell.Empty,
					text(inRec.notes.orEmpty()),
					techText(inRec.id), techText(inRec.accountId), techText(inRec.toAccountId.orEmpty()), techText(inRec.categoryId.orEmpty())
				)
			}
		),
		Sheet(
			kSheetLoans,
			listOf(Column(kAccount, 26.0), Column(kPrincipal, 16.0), Column(kRate, 10.0), Column(kMonths, 12.0), Column(kFirstDue, 14.0), Column(kInsurance, 14.0),
				Column(kPayFrom, 22.0), Column(kCategory, 20.0), Column(kLast, 14.0), Column(kProperty, 26.0)) + tech(kLoanId, kAccountId, kPayFromId, kCategoryId, kPropertyId),
			inFile.loans.sortedBy { vAccounts[it.accountId]?.name }.map { inLoan ->
				listOf(
					text(vAccounts[inLoan.accountId]?.name.orEmpty()), euros(inLoan.principal), Cell.Number(BigDecimal.valueOf(inLoan.rateBp.toLong(), 2), CellStyle.NUMBER),
					Cell.Number(BigDecimal(inLoan.months), CellStyle.NUMBER), Cell.Date(LocalDate.parse(inLoan.firstDue)), euros(inLoan.insurance),
					text(vAccounts[inLoan.payFrom]?.name.orEmpty()), text(vCategories[inLoan.categoryId]?.name.orEmpty()),
					inLoan.last?.let { Cell.Date(LocalDate.parse(it)) } ?: Cell.Empty,
					text(inLoan.propertyId?.let { inId -> inFile.transactions.firstOrNull { it.id == inId }?.label }.orEmpty()),
					techText(inLoan.id), techText(inLoan.accountId), techText(inLoan.payFrom), techText(inLoan.categoryId), techText(inLoan.propertyId.orEmpty())
				)
			}
		),
		Sheet(
			kSheetProfiles,
			listOf(Column(kName, 28.0)) + tech(kProfileId, kSortOrder),
			inFile.profiles.sortedBy { it.sortOrder }.map { inProfile -> listOf(text(inProfile.name), techText(inProfile.id), techNumber(inProfile.sortOrder.toLong())) }
		),
		Sheet(
			kSheetThemes,
			listOf(Column(kName, 24.0)) + kThemeRoles.map { Column(it, 16.0) } + tech(kThemeId, kSortOrder),
			inFile.themes.sortedBy { it.sortOrder }.map { inTheme -> listOf(text(inTheme.name)) + inTheme.colors.map { text(it) } + listOf(techText(inTheme.id), techNumber(inTheme.sortOrder.toLong())) }
		),
		Sheet(
			kSheetBudget,
			listOf(Column(kSection, 16.0), Column(kName, 28.0), Column(kAmount, 15.0), Column(kTheme, 18.0), Column(kCategory, 20.0), Column(kAccount, 22.0), Column(kMember, 16.0)) +
				tech(kBudgetId, kCategoryId, kAccountId, kProfileId),
			inFile.budget.map { inLine ->
				listOf(
					text(BudgetSection.valueOf(inLine.section).label), text(inLine.name), euros(inLine.amount),
					text(inLine.theme?.let { ExpenseTheme.valueOf(it).label }.orEmpty()), text(inLine.categoryId?.let { vCategories[it]?.name }.orEmpty()),
					text(inLine.accountId?.let { vAccounts[it]?.name }.orEmpty()), text(inLine.profileId?.let { inId -> inFile.profiles.firstOrNull { it.id == inId }?.name }.orEmpty()),
					techText(inLine.id), techText(inLine.categoryId.orEmpty()), techText(inLine.accountId.orEmpty()), techText(inLine.profileId.orEmpty())
				)
			}
		),
		Sheet(
			kSheetGoals,
			listOf(Column(kView, 14.0), Column(kNature, 20.0), Column(kTitle, 30.0), Column(kTarget, 15.0), Column(kAccount, 22.0), Column(kType, 14.0),
				Column(kCurrent, 15.0), Column(kDeadline, 12.0), Column(kDone, 8.0), Column(kNotes, 30.0)) + tech(kGoalId, kScopeId, kAccountId),
			inFile.goals.map { inGoal ->
				val vKind = GoalKind.valueOf(inGoal.kind)												// nature
				listOf(
					text(scopeLabel(inGoal.scope, inFile)), text(vKind.label), text(inGoal.title),
					if (vKind == GoalKind.RATE) Cell.Number(BigDecimal.valueOf(inGoal.target, 1), CellStyle.NUMBER) else euros(inGoal.target),
					text(inGoal.accountId?.let { vAccounts[it]?.name }.orEmpty()), text(inGoal.accountType?.let { AccountType.valueOf(it).label }.orEmpty()),
					euros(inGoal.current), inGoal.deadline?.let { inDue -> runCatching { Cell.Date(LocalDate.parse(inDue)) }.getOrNull() } ?: Cell.Empty,
					text(yesNo(inGoal.done)), text(inGoal.note),
					techText(inGoal.id), techText(inGoal.scope), techText(inGoal.accountId.orEmpty())
				)
			}
		),
		Sheet(
			kSheetMonthGoals,
			listOf(Column(kView, 14.0), Column(kMonth, 10.0), Column(kConfirmed, 14.0), Column(kNature, 12.0), Column(kTheme, 18.0), Column(kTarget, 15.0)) + tech(kGoalId, kScopeId),
			inFile.monthGoals.map { inRow ->
				listOf(
					text(scopeLabel(inRow.scope, inFile)), text(inRow.month), text(inRow.confirmed),
					text(when (inRow.kind?.let { MonthGoalKind.valueOf(it) }) { MonthGoalKind.SAVING -> kSavingLabel; MonthGoalKind.SPENDING -> kSpendingLabel; null -> "" }),
					text(inRow.theme?.let { ExpenseTheme.valueOf(it).label }.orEmpty()), if (inRow.kind == null) Cell.Empty else euros(inRow.target),
					techText(inRow.id.orEmpty()), techText(inRow.scope)
				)
			}
		),
		Sheet(
			kSheetFile,
			listOf(Column(kKey, 16.0), Column(kValue, 70.0)),
			listOf(
				listOf(text(kKeyFormat), text(inFile.format)),
				listOf(text(kKeyVersion), Cell.Number(BigDecimal(kBackupVersion))),	// écrit au format courant (un fichier relu d'un format plus ancien est réécrit au format courant)
				listOf(text(kKeyExported), text(inFile.exportedAt)),
				listOf(text("Restauration"), text("Réglages › Données › Importer. Ne modifiez pas les colonnes repliées (identifiants).")),
				listOf(text("Résumé"), text(inFile.summary))
			)
		)
	)
}

/*
	Vue d'un objectif, en clair : nom du profil, ou « Famille ».
	inScope : identifiant du profil, ou « * »
	inFile  : contenu (profils)
*/
private fun scopeLabel(
	inScope : String,
	inFile  : BackupFile
): String = if (inScope == kFamilyView) kFamilyLabel else inFile.profiles.firstOrNull { it.id == inScope }?.name ?: inScope

/*
	Colonnes d'un onglet d'opérations : les colonnes d'ordre de bourse
	n'apparaissent que sur l'onglet Bourse.
	inType : type de compte de l'onglet
*/
private fun operationColumns(inType: AccountType): List<Column> =
	listOf(Column(kDate, 12.0), Column(kAccount, 22.0), Column(kLabel, 34.0), Column(kNature, 14.0), Column(kCategory, 20.0), Column(kAmount, 15.0)) +
	(if (inType == AccountType.BROKERAGE) listOf(Column(kAsset, 26.0), Column(kSide, 8.0), Column(kQuantity, 11.0), Column(kPrice, 12.0), Column(kFees, 10.0)) else emptyList()) +
	listOf(Column(kNotes, 30.0)) +
	tech(kTxId, kPostingId, kTradeId, kAccountId, kCategoryId, kAssetId, kLinkedAsset, kLinkedTx, kCreated, kUpdated)

/*
	Ligne d'un onglet d'opérations : une jambe, un ordre, ou les deux fusionnés
	(ordre de bourse ordinaire).
	inTx         : opération
	inPosting    : jambe ; null pour une ligne d'ordre seule
	inTrade      : ordre ; null pour une ligne de jambe seule
	inAccount    : nom du compte de la jambe
	inCategories : catégories par identifiant
	inAssets     : actifs par identifiant
	inType       : type de compte de l'onglet
*/
private fun operationRow(
	inTx         : TransactionDto,
	inPosting    : PostingDto?,
	inTrade      : TradeDto?,
	inAccount    : String?,
	inCategories : Map<String, CategoryDto>,
	inAssets     : Map<String, AssetDto>,
	inType       : AccountType
): List<Cell>
{
	val vNature = when (TransactionKind.valueOf(inTx.kind))
	{
		TransactionKind.STANDARD -> if ((inPosting?.amountCents ?: 0L) < 0) "Dépense" else "Revenu"
		TransactionKind.TRANSFER -> "Virement"
		TransactionKind.TRADE    -> "Ordre"
		TransactionKind.OPENING  -> "Solde initial"
		TransactionKind.ACQUISITION -> "Achat de bien"
		TransactionKind.DISPOSAL    -> "Vente de bien"
		TransactionKind.LOAN_PAYMENT -> "Mensualité"
	}																			// nature lisible
	val vCells = mutableListOf<Cell>(
		Cell.Date(LocalDate.parse(inTx.date)),
		text(inAccount.orEmpty()),
		text(inTx.label),
		text(vNature),
		text(inTx.categoryId?.let { inCategories[it]?.name }.orEmpty()),
		inPosting?.let { euros(it.amountCents) } ?: Cell.Empty
	)																			// colonnes lisibles
	if (inType == AccountType.BROKERAGE)
	{
		vCells += if (inTrade == null) List(5) { Cell.Empty } else listOf(
			text(inAssets[inTrade.assetId]?.name.orEmpty()),
			text(if (TradeSide.valueOf(inTrade.side) == TradeSide.BUY) "Achat" else "Vente"),
			scaled(inTrade.quantity, kQuantityScale, CellStyle.NUMBER),
			scaled(inTrade.unitPrice, kPriceScale, CellStyle.PRICE),
			euros(inTrade.feesCents)
		)
	}
	vCells += text(inTx.notes.orEmpty())
	vCells += listOf(
		techText(inTx.id), techText(inPosting?.id.orEmpty()), techText(inTrade?.id.orEmpty()), techText(inPosting?.accountId.orEmpty()),
		techText(inTx.categoryId.orEmpty()), techText(inTrade?.assetId.orEmpty()), techText(inTx.assetId.orEmpty()), techText(inTx.linkedId.orEmpty()),
		techNumber(inTx.createdAt), techNumber(inTx.updatedAt)
	)
	return vCells
}

/*
	Reconstitue le contenu d'une sauvegarde à partir des onglets lus. Les
	opérations sont regroupées par identifiant, sur tous les onglets de
	comptes : un virement entre deux types de comptes n'est compté qu'une fois.
	Lève WorkbookException si un onglet ou une colonne manque.
	inSheets : onglets lus
*/
internal fun backupFromSheets(inSheets: List<ReadSheet>): BackupFile
{
	val vByName = inSheets.associateBy { it.name }								// onglets par nom
	/*
		Onglet obligatoire.
		inName : nom de l'onglet
	*/
	fun sheet(inName: String): ReadSheet = vByName[inName] ?: throw WorkbookException("Onglet manquant dans le classeur : « $inName ».")
	val vInfo = sheet(kSheetFile).rows.associate { (it[kKey] ?: "") to (it[kValue] ?: "") }	// page de signature
	val vHeaders = linkedMapOf<String, TransactionDto>()						// en-têtes d'opérations, dans l'ordre lu
	val vPostings = mutableMapOf<String, MutableList<PostingDto>>()			// jambes par opération
	val vTrades   = mutableMapOf<String, MutableList<TradeDto>>()				// ordres par opération
	AccountType.entries.forEach { inType ->
		if ((inType.isProperty || inType == AccountType.LOAN) && inType.label !in vByName) return@forEach	// classeur antérieur à ce type de compte
		val vSheet = sheet(inType.label)										// onglet du type
		requireColumns(vSheet, kTxId, kDate, kLabel, kNature, kAmount, kPostingId, kAccountId)
		vSheet.rows.forEach { inRow ->
			val vTxId = inRow.req(kTxId)										// opération de la ligne
			vHeaders.getOrPut(vTxId) {
				TransactionDto(
					id         = vTxId,
					date       = date(inRow.req(kDate)).toString(),
					kind       = natureKind(inRow.req(kNature)),
					label      = inRow[kLabel].orEmpty(),
					categoryId = inRow.opt(kCategoryId),
					assetId    = inRow.opt(kLinkedAsset),
					notes      = inRow.opt(kNotes),
					linkedId   = inRow.opt(kLinkedTx),
					createdAt  = inRow.opt(kCreated)?.let { decimal(it).toLong() } ?: 0L,
					updatedAt  = inRow.opt(kUpdated)?.let { decimal(it).toLong() } ?: 0L
				)
			}
			inRow.opt(kPostingId)?.let { inId ->
				vPostings.getOrPut(vTxId) { mutableListOf() } += PostingDto(inId, inRow.req(kAccountId), cents(inRow.req(kAmount)))
			}
			inRow.opt(kTradeId)?.let { inId ->
				vTrades.getOrPut(vTxId) { mutableListOf() } += TradeDto(
					id        = inId,
					assetId   = inRow.req(kAssetId),
					side      = sideName(inRow.req(kSide)),
					quantity  = scaledLong(inRow.req(kQuantity), kQuantityScale),
					unitPrice = scaledLong(inRow.req(kPrice), kPriceScale),
					feesCents = inRow.opt(kFees)?.let { cents(it) } ?: 0L
				)
			}
		}
	}
	val vAccounts   = sheet(kSheetAccounts).also { requireColumns(it, kName, kType, kAccountId) }		// onglet des comptes
	val vCategories = sheet(kSheetCategories).also { requireColumns(it, kName, kKind, kCategoryId) }	// onglet des catégories
	val vAssets     = sheet(kSheetAssets).also { requireColumns(it, kName, kAssetId) }				// onglet des actifs
	val vPrices     = sheet(kSheetPrices).also { requireColumns(it, kDate, kPrice, kPriceId, kAssetId) }	// onglet des cours
	val vStatements = sheet(kSheetStatements).also { requireColumns(it, kDate, kBalance, kStatementId, kAccountId) }	// onglet des relevés
	return BackupFile(
		format       = vInfo[kKeyFormat].orEmpty(),
		version      = vInfo[kKeyVersion]?.let { decimal(it).toInt() } ?: 0,
		exportedAt   = vInfo[kKeyExported].orEmpty(),
		accounts     = vAccounts.rows.map { inRow ->
			AccountDto(
				id           = inRow.req(kAccountId),
				name         = inRow.req(kName),
				type         = typeName(inRow.req(kType)),
				institution  = inRow.opt(kInstitution),
				sharePercent = inRow.opt(kShare)?.let { decimal(it).intValueExact() } ?: 100,
				sortOrder    = inRow.opt(kSortOrder)?.let { decimal(it).toInt() } ?: 0,
				archived     = yes(inRow[kArchived]),
				createdAt    = inRow.opt(kCreated)?.let { decimal(it).toLong() } ?: 0L,
				updatedAt    = inRow.opt(kUpdated)?.let { decimal(it).toLong() } ?: 0L,
				owners       = inRow.opt(kShares)?.split(";")?.filter { it.isNotBlank() }?.map { inPart ->
					val vPair = inPart.split("=")												// identifiant et part
					if (vPair.size != 2) throw WorkbookException("Parts illisibles pour le compte « ${inRow[kName]} » : $inPart")
					OwnerDto(vPair[0].trim(), decimal(vPair[1]).intValueExact())
				}.orEmpty()
			)
		},
		categories   = vCategories.rows.map { inRow ->
			CategoryDto(
				id        = inRow.req(kCategoryId),
				name      = inRow.req(kName),
				icon      = inRow.opt(kIcon) ?: "dots",
				kind      = kindName(inRow.req(kKind)),
				sortOrder = inRow.opt(kSortOrder)?.let { decimal(it).toInt() } ?: 0,
				archived  = yes(inRow[kArchived]),
				createdAt = inRow.opt(kCreated)?.let { decimal(it).toLong() } ?: 0L,
				updatedAt = inRow.opt(kUpdated)?.let { decimal(it).toLong() } ?: 0L
			)
		},
		assets       = vAssets.rows.map { inRow ->
			AssetDto(
				id        = inRow.req(kAssetId),
				name      = inRow.req(kName),
				isin      = inRow.opt(kIsin),
				ticker    = inRow.opt(kTicker),
				archived  = yes(inRow[kArchived]),
				createdAt = inRow.opt(kCreated)?.let { decimal(it).toLong() } ?: 0L,
				updatedAt = inRow.opt(kUpdated)?.let { decimal(it).toLong() } ?: 0L
			)
		},
		transactions = vHeaders.values.map { inTx ->
			inTx.copy(postings = vPostings[inTx.id].orEmpty(), trades = vTrades[inTx.id].orEmpty())
		},
		statements   = vStatements.rows.map { inRow ->
			StatementDto(inRow.req(kStatementId), inRow.req(kAccountId), date(inRow.req(kDate)).toString(), cents(inRow.req(kBalance)))
		},
		prices       = vPrices.rows.map { inRow ->
			PriceDto(inRow.req(kPriceId), inRow.req(kAssetId), date(inRow.req(kDate)).toString(), scaledLong(inRow.req(kPrice), kPriceScale))
		},
		recurrences  = vByName[kSheetRecurring]?.also { requireColumns(it, kLabel, kAmount, kFrequency, kStart, kRecurrenceId, kAccountId) }?.rows.orEmpty().map { inRow ->
			RecurrenceDto(
				id          = inRow.req(kRecurrenceId),
				label       = inRow.req(kLabel),
				accountId   = inRow.req(kAccountId),
				toAccountId = inRow.opt(kToId),
				categoryId  = inRow.opt(kCategoryId),
				amountCents = cents(inRow.req(kAmount)),
				frequency   = frequencyName(inRow.req(kFrequency)),
				start       = date(inRow.req(kStart)).toString(),
				end         = inRow.opt(kEnd)?.let { date(it).toString() },
				last        = inRow.opt(kLast)?.let { date(it).toString() },
				notes       = inRow.opt(kNotes)
			)
		},
		loans        = vByName[kSheetLoans]?.also { requireColumns(it, kPrincipal, kRate, kMonths, kFirstDue, kLoanId, kAccountId, kPayFromId, kCategoryId) }?.rows.orEmpty().map { inRow ->
			LoanDto(
				id         = inRow.req(kLoanId),
				accountId  = inRow.req(kAccountId),
				principal  = cents(inRow.req(kPrincipal)),
				rateBp     = decimal(inRow.req(kRate)).setScale(2, RoundingMode.HALF_UP).movePointRight(2).intValueExact(),
				months     = decimal(inRow.req(kMonths)).intValueExact(),
				firstDue   = date(inRow.req(kFirstDue)).toString(),
				insurance  = inRow.opt(kInsurance)?.let { cents(it) } ?: 0L,
				payFrom    = inRow.req(kPayFromId),
				categoryId = inRow.req(kCategoryId),
				last       = inRow.opt(kLast)?.let { date(it).toString() },
				propertyId = inRow.opt(kPropertyId)
			)
		},
		profiles     = vByName[kSheetProfiles]?.also { requireColumns(it, kName, kProfileId) }?.rows.orEmpty().map { inRow ->
			ProfileDto(inRow.req(kProfileId), inRow.req(kName), inRow.opt(kSortOrder)?.let { decimal(it).toInt() } ?: 0)
		},
		themes       = vByName[kSheetThemes]?.also { requireColumns(it, kName, kThemeId) }?.rows.orEmpty().map { inRow ->
			ThemeDto(inRow.req(kThemeId), inRow.req(kName), kThemeRoles.map { inRow[it].orEmpty() }, inRow.opt(kSortOrder)?.let { decimal(it).toInt() } ?: 0)
		},
		budget       = vByName[kSheetBudget]?.also { requireColumns(it, kSection, kAmount, kBudgetId) }?.rows.orEmpty().map { inRow ->
			BudgetLineDto(
				id         = inRow.req(kBudgetId),
				section    = enumName(inRow.req(kSection), BudgetSection.entries.map { it.name to it.label }),
				name       = inRow[kName].orEmpty(),
				amount     = cents(inRow.req(kAmount)),
				categoryId = inRow.opt(kCategoryId),
				accountId  = inRow.opt(kAccountId),
				theme      = inRow.opt(kTheme)?.let { enumName(it, ExpenseTheme.entries.map { inTheme -> inTheme.name to inTheme.label }) },
				profileId  = inRow.opt(kProfileId)
			)
		},
		goals        = vByName[kSheetGoals]?.also { requireColumns(it, kNature, kGoalId, kScopeId) }?.rows.orEmpty().map { inRow ->
			val vKind = GoalKind.valueOf(enumName(inRow.req(kNature), GoalKind.entries.map { it.name to it.label }))	// nature
			GoalDto(
				scope       = inRow.req(kScopeId),
				id          = inRow.req(kGoalId),
				kind        = vKind.name,
				title       = inRow[kTitle].orEmpty(),
				target      = inRow.opt(kTarget)?.let { if (vKind == GoalKind.RATE) decimal(it).setScale(1, RoundingMode.HALF_UP).movePointRight(1).longValueExact() else cents(it) } ?: 0L,
				accountId   = inRow.opt(kAccountId),
				accountType = inRow.opt(kType)?.let { typeName(it) },
				current     = inRow.opt(kCurrent)?.let { cents(it) } ?: 0L,
				deadline    = inRow.opt(kDeadline)?.let { date(it).toString() },
				done        = yes(inRow[kDone]),
				note        = inRow[kNotes].orEmpty()
			)
		},
		monthGoals   = vByName[kSheetMonthGoals]?.also { requireColumns(it, kMonth, kConfirmed, kScopeId) }?.rows.orEmpty().map { inRow ->
			val vKind = inRow.opt(kNature)?.let { inText ->
				when (inText.lowercase())
				{
					kSavingLabel.lowercase(), "saving"     -> MonthGoalKind.SAVING.name
					kSpendingLabel.lowercase(), "spending" -> MonthGoalKind.SPENDING.name
					else                                   -> throw WorkbookException("Objectif du mois inconnu : « $inText ».")
				}
			}																// nature ; null : mois fixé sans objectif
			MonthGoalDto(
				scope     = inRow.req(kScopeId),
				month     = inRow.req(kMonth),
				confirmed = inRow.req(kConfirmed),
				id        = inRow.opt(kGoalId),
				kind      = vKind,
				target    = inRow.opt(kTarget)?.let { cents(it) } ?: 0L,
				theme     = inRow.opt(kTheme)?.let { enumName(it, ExpenseTheme.entries.map { inTheme -> inTheme.name to inTheme.label }) }
			)
		}
	)
}

/*
	Vérifie qu'un onglet porte les colonnes nécessaires.
	inSheet   : onglet lu
	inHeaders : intitulés obligatoires
*/
private fun requireColumns(
	inSheet          : ReadSheet,
	vararg inHeaders : String
)
{
	val vMissing = inHeaders.filter { it !in inSheet.headers }					// colonnes absentes
	if (vMissing.isNotEmpty() && inSheet.rows.isNotEmpty())
	{
		throw WorkbookException("Colonne manquante dans l’onglet « ${inSheet.name} » : ${vMissing.joinToString(", ")}.")
	}
}

/*
	Valeur obligatoire d'une ligne.
	inHeader : colonne
*/
private fun Map<String, String>.req(inHeader: String): String =
	this[inHeader]?.trim()?.takeIf { it.isNotEmpty() } ?: throw WorkbookException("Valeur manquante dans la colonne « $inHeader ».")

/*
	Valeur facultative d'une ligne ; null si vide.
	inHeader : colonne
*/
private fun Map<String, String>.opt(inHeader: String): String? = this[inHeader]?.trim()?.takeIf { it.isNotEmpty() }

/*
	Nombre décimal lu dans une cellule : nombre écrit par un tableur, ou texte
	saisi à la française (« 1 234,56 € »).
	inText : valeur brute
*/
internal fun decimal(inText: String): BigDecimal
{
	val vClean = inText.trim().replace("€", "").replace(" ", "").replace("\u00A0", "").replace("\u202F", "")	// sans espaces ni symbole
	val vComma = vClean.lastIndexOf(',')										// dernière virgule
	val vDot   = vClean.lastIndexOf('.')										// dernier point
	return BigDecimal(
		when
		{
			vComma < 0  -> vClean												// « 1234.56 » ou « 1234 »
			vDot < 0    -> vClean.replace(',', '.')							// « 1234,56 »
			vComma > vDot -> vClean.replace(".", "").replace(',', '.')			// « 1.234,56 » : point des milliers
			else        -> vClean.replace(",", "")								// « 1,234.56 » : virgule des milliers
		}
	)
}

/*
	Montant en centimes d'une valeur en euros.
	inText : valeur brute
*/
private fun cents(inText: String): Long = decimal(inText).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()

/*
	Valeur en unités internes (dix-millièmes) d'une quantité ou d'un cours.
	inText  : valeur brute
	inScale : échelle interne
*/
private fun scaledLong(
	inText  : String,
	inScale : Long
): Long = decimal(inText).multiply(BigDecimal(inScale)).setScale(0, RoundingMode.HALF_UP).longValueExact()

/*
	Date lue : numéro de série de tableur, ou texte AAAA-MM-JJ ou JJ/MM/AAAA.
	inText : valeur brute
*/
internal fun date(inText: String): LocalDate
{
	val vText = inText.trim()													// valeur nettoyée
	return when
	{
		vText.contains('-') -> LocalDate.parse(vText.take(10))
		vText.contains('/') -> LocalDate.parse(vText, kSlashDate)
		else                -> fromExcelSerial(decimal(vText).toLong())
	}
}

/*
	Nature d'opération lue (libellé français ou nom interne).
	inText : valeur brute
*/
private fun natureKind(inText: String): String =
	when (inText.trim().lowercase())
	{
		"dépense", "revenu", "standard" -> TransactionKind.STANDARD.name
		"virement", "transfer"          -> TransactionKind.TRANSFER.name
		"ordre", "trade"                -> TransactionKind.TRADE.name
		"solde initial", "opening"      -> TransactionKind.OPENING.name
		"achat de bien", "acquisition"  -> TransactionKind.ACQUISITION.name
		"vente de bien", "disposal"     -> TransactionKind.DISPOSAL.name
		"mensualité", "loan_payment"    -> TransactionKind.LOAN_PAYMENT.name
		else                            -> throw WorkbookException("Nature d’opération inconnue : « $inText ».")
	}

/*
	Valeur d'une liste fermée lue (libellé français ou nom interne).
	inText    : valeur brute
	inChoices : noms internes et leur libellé
*/
private fun enumName(
	inText    : String,
	inChoices : List<Pair<String, String>>
): String =
	inChoices.firstOrNull { it.first.equals(inText.trim(), ignoreCase = true) || it.second.equals(inText.trim(), ignoreCase = true) }?.first
		?: throw WorkbookException("Valeur inconnue : « $inText ».")

/*
	Fréquence lue (libellé français ou nom interne).
	inText : valeur brute
*/
private fun frequencyName(inText: String): String =
	Frequency.entries.firstOrNull { it.label.equals(inText.trim(), ignoreCase = true) || it.name.equals(inText.trim(), ignoreCase = true) }?.name
		?: throw WorkbookException("Fréquence inconnue : « $inText ».")

/*
	Type de compte lu (libellé français ou nom interne).
	inText : valeur brute
*/
private fun typeName(inText: String): String =
	AccountType.entries.firstOrNull { it.label.equals(inText.trim(), ignoreCase = true) || it.name.equals(inText.trim(), ignoreCase = true) }?.name
		?: throw WorkbookException("Type de compte inconnu : « $inText ».")

/*
	Nature de catégorie lue.
	inText : valeur brute
*/
private fun kindName(inText: String): String =
	when (inText.trim().lowercase())
	{
		"dépense", "expense" -> CategoryKind.EXPENSE.name
		"revenu", "income"   -> CategoryKind.INCOME.name
		else                 -> throw WorkbookException("Nature de catégorie inconnue : « $inText ».")
	}

/*
	Sens d'un ordre lu.
	inText : valeur brute
*/
private fun sideName(inText: String): String =
	when (inText.trim().lowercase())
	{
		"achat", "buy"  -> TradeSide.BUY.name
		"vente", "sell" -> TradeSide.SELL.name
		else            -> throw WorkbookException("Sens d’ordre inconnu : « $inText ».")
	}

/*
	Libellé d'une nature de catégorie.
	inKind : nom interne
*/
private fun kindLabel(inKind: String): String = if (CategoryKind.valueOf(inKind) == CategoryKind.EXPENSE) "Dépense" else "Revenu"

/*
	« Oui » ou « Non ».
	inValue : booléen
*/
private fun yesNo(inValue: Boolean): String = if (inValue) "Oui" else "Non"

/*
	Booléen lu (« Oui », « 1 », « vrai »…) ; faux si vide.
	inText : valeur brute
*/
private fun yes(inText: String?): Boolean = inText?.trim()?.lowercase() in setOf("oui", "1", "vrai", "true", "yes")

/*
	Colonnes techniques (repliées).
	inHeaders : intitulés
*/
private fun tech(vararg inHeaders: String): List<Column> = inHeaders.map { Column(it, if (it.endsWith("(ms)")) 15.0 else 38.0, technical = true) }

/*
	Cellule de texte ordinaire.
	inText : texte
*/
private fun text(inText: String): Cell = Cell.Text(inText)

/*
	Cellule de texte technique.
	inText : texte
*/
private fun techText(inText: String): Cell = Cell.Text(inText, CellStyle.TECH)

/*
	Cellule de nombre entier technique.
	inValue : valeur
*/
private fun techNumber(inValue: Long): Cell = Cell.Number(BigDecimal(inValue), CellStyle.TECH)

/*
	Cellule de montant en euros.
	inCents : montant en centimes
*/
private fun euros(inCents: Long): Cell = Cell.Number(BigDecimal.valueOf(inCents, 2), CellStyle.EUROS)

/*
	Cellule d'une valeur interne en dix-millièmes, sans zéros inutiles.
	inValue : valeur interne
	inScale : échelle interne
	inStyle : présentation
*/
private fun scaled(
	inValue : Long,
	inScale : Long,
	inStyle : CellStyle
): Cell = Cell.Number(BigDecimal(inValue).divide(BigDecimal(inScale)).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }, inStyle)
