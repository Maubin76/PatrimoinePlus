package fr.maubin.patrimoineplus.data.edit

import fr.maubin.patrimoineplus.data.backup.AccountDto
import fr.maubin.patrimoineplus.data.backup.AssetDto
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.CategoryDto
import fr.maubin.patrimoineplus.data.backup.PostingDto
import fr.maubin.patrimoineplus.data.backup.PriceDto
import fr.maubin.patrimoineplus.data.backup.StatementDto
import fr.maubin.patrimoineplus.data.backup.TradeDto
import fr.maubin.patrimoineplus.data.backup.TransactionDto
import fr.maubin.patrimoineplus.data.toDataSet
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.OversoldSale
import fr.maubin.patrimoineplus.domain.calc.findOversold
import fr.maubin.patrimoineplus.domain.calc.orphanedSale
import fr.maubin.patrimoineplus.domain.calc.tradedPairs
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.domain.model.kOpeningLabel
import fr.maubin.patrimoineplus.domain.rules.SetupError
import fr.maubin.patrimoineplus.domain.rules.TransactionValidator
import fr.maubin.patrimoineplus.domain.rules.ValidationError
import fr.maubin.patrimoineplus.domain.rules.validateAccount
import fr.maubin.patrimoineplus.domain.rules.validateReassignment
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.data.backup.recurrenceError
import fr.maubin.patrimoineplus.data.backup.loanError
import fr.maubin.patrimoineplus.data.backup.toDto
import fr.maubin.patrimoineplus.domain.calc.amortization
import fr.maubin.patrimoineplus.domain.calc.remainingAt
import fr.maubin.patrimoineplus.domain.calc.dueOperations
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.budget.themeOfCategory
import fr.maubin.patrimoineplus.domain.calc.accountRemoval

private const val kPositiveValues = "Quantité et cours doivent être positifs."		// refus d'une valeur nulle ou négative

/*
	Résultat d'une modification.
*/
sealed interface EditResult
{
	/*
		Modification appliquée.
		file : nouveau contenu
		id   : élément créé ou modifié, s'il y en a un
	*/
	data class Done(
		val file : BackupFile,
		val id   : String? = null
	) : EditResult

	/*
		Modification refusée ; le contenu n'a pas changé.
		messages : règles violées, pour l'utilisateur
	*/
	data class Refused(val messages: List<String>) : EditResult
}

/*
	Solde initial d'un compte.
	cents : espèces au départ
	date  : date du solde initial
*/
data class OpeningBalance(
	val cents : Long,
	val date  : LocalDate
)

/*
	Modifications des données, sans base : chaque fonction prend le contenu
	complet (celui du fichier d'échange, horodatages compris) et rend le
	nouveau contenu, ou un refus motivé sans rien changer. Mêmes règles que les
	dépôts de l'appli Android : un compte ne change plus de type dès qu'il a
	des opérations, une catégorie utilisée est réaffectée avant suppression, un
	virement est supprimé en entier, chaque opération est validée comme à la
	saisie. Les dates de création sont conservées, celles de modification mises
	à jour.
	inClock : horloge (horodatages)
	inNewId : fabrique d'identifiants
*/
class DataEditor(
	private val inClock : Clock,
	private val inNewId : () -> String = { UUID.randomUUID().toString() }
)
{
	// ---------- Comptes ----------

	/*
		Crée un compte en fin de liste, avec son solde initial éventuel.
		inFile        : contenu
		inName        : nom
		inType        : type
		inInstitution : établissement, facultatif
		inShare       : part détenue, en %
		inOpening     : solde initial ; null ou nul pour aucun
	*/
	fun createAccount(
		inFile        : BackupFile,
		inName        : String,
		inType        : AccountType,
		inInstitution : String?,
		inShare       : Int,
		inOpening     : OpeningBalance?
	): EditResult
	{
		validateAccount(inName, inShare).refusal()?.let { return it }
		if (inType.isProperty && inOpening != null && inOpening.cents != 0L) return refused(SetupError.PropertyOpening)
		val vNow     = now()														// horodatage
		val vAccount = AccountDto(
			id           = inNewId(),
			name         = inName.trim(),
			type         = inType.name,
			institution  = inInstitution?.trim()?.ifEmpty { null },
			sharePercent = inShare,
			sortOrder    = (inFile.accounts.maxOfOrNull { it.sortOrder } ?: -1) + 1,
			archived     = false,
			createdAt    = vNow,
			updatedAt    = vNow
		)																			// nouveau compte
		var vFile = inFile.copy(accounts = inFile.accounts + vAccount)			// contenu avec le compte
		if (inOpening != null && inOpening.cents != 0L)
		{
			vFile = vFile.copy(transactions = vFile.transactions + newOpening(vAccount.id, inOpening, vNow))
		}
		return EditResult.Done(vFile, vAccount.id)
	}

	/*
		Modifie un compte (nom, établissement, part, archivage ; type tant qu'il
		n'a pas d'autre opération que son solde initial).
		inFile    : contenu
		inAccount : compte modifié
	*/
	fun updateAccount(
		inFile    : BackupFile,
		inAccount : Account
	): EditResult
	{
		validateAccount(inAccount.name, inAccount.sharePercent).refusal()?.let { return it }
		val vOld = inFile.accounts.firstOrNull { it.id == inAccount.id } ?: return refused(SetupError.NotFound)	// version actuelle
		if (vOld.type != inAccount.type.name && operationCount(inFile, inAccount.id) > 0)
		{
			return refused(SetupError.TypeLocked)
		}
		if (vOld.type != inAccount.type.name && (inFile.loans.any { it.accountId == inAccount.id || it.payFrom == inAccount.id } || inFile.recurrences.any { it.accountId == inAccount.id || it.toAccountId == inAccount.id }))
		{
			return refused(SetupError.TypeUsed)
		}
		if (inAccount.type.isProperty && openingOf(inFile.transactions, inAccount.id) != null)
		{
			return refused(SetupError.PropertyOpening)
		}
		val vNew = vOld.copy(
			name         = inAccount.name.trim(),
			type         = inAccount.type.name,
			institution  = inAccount.institution?.trim()?.ifEmpty { null },
			sharePercent = inAccount.sharePercent,
			archived     = inAccount.archived,
			updatedAt    = now()
		)																			// compte modifié
		return EditResult.Done(inFile.copy(accounts = inFile.accounts.map { if (it.id == vOld.id) vNew else it }), vOld.id)
	}

	/*
		Archive ou désarchive un compte.
		inFile     : contenu
		inId       : compte
		inArchived : vrai pour archiver
	*/
	fun setArchived(
		inFile     : BackupFile,
		inId       : String,
		inArchived : Boolean
	): EditResult
	{
		val vOld = inFile.accounts.firstOrNull { it.id == inId } ?: return refused(SetupError.NotFound)	// compte
		return EditResult.Done(inFile.copy(accounts = inFile.accounts.map { if (it.id == inId) vOld.copy(archived = inArchived, updatedAt = now()) else it }), inId)
	}

	/*
		Solde initial d'un compte ; null s'il n'en a pas.
		inFile : contenu
		inId   : compte
	*/
	fun openingOf(
		inFile : BackupFile,
		inId   : String
	): OpeningBalance? =
		openingOf(inFile.transactions, inId)?.let { OpeningBalance(it.postings.single().amountCents, LocalDate.parse(it.date)) }

	/*
		Crée, modifie ou supprime le solde initial d'un compte (un montant nul le
		supprime, sauf s'il porte des positions initiales).
		inFile    : contenu
		inId      : compte
		inOpening : nouveau solde initial
	*/
	fun setOpening(
		inFile    : BackupFile,
		inId      : String,
		inOpening : OpeningBalance
	): EditResult
	{
		val vAccount = inFile.accounts.firstOrNull { it.id == inId } ?: return refused(SetupError.NotFound)	// compte
		if (AccountType.valueOf(vAccount.type).isProperty && inOpening.cents != 0L) return refused(SetupError.PropertyOpening)
		val vOld = openingOf(inFile.transactions, inId)							// solde initial actuel
		val vNow = now()															// horodatage
		val vTxs = when
		{
			vOld == null && inOpening.cents != 0L          -> inFile.transactions + newOpening(inId, inOpening, vNow)
			vOld == null                                   -> inFile.transactions
			inOpening.cents == 0L && vOld.trades.isEmpty() -> inFile.transactions - vOld
			else                                           -> inFile.transactions.map {
				if (it.id == vOld.id) vOld.copy(date = inOpening.date.toString(), postings = listOf(vOld.postings.single().copy(amountCents = inOpening.cents)), updatedAt = vNow) else it
			}
		}																			// opérations modifiées
		val vFile = inFile.copy(transactions = vTxs)								// nouveau contenu
		vOld?.let { positionRefusal(vFile, inId, it.trades) }?.let { return it }
		return EditResult.Done(vFile, inId)
	}

	/*
		Ajoute une position initiale (achat au prix de revient, sans frais) au
		solde initial d'un compte Bourse, créé sans espèces s'il n'existe pas.
		inFile      : contenu
		inId        : compte Bourse
		inAssetId   : actif
		inQuantity  : quantité, en dix-millièmes
		inUnitPrice : prix de revient unitaire, en dix-millièmes d'euro
		inDate      : date du solde initial, s'il faut le créer
	*/
	fun addOpeningPosition(
		inFile      : BackupFile,
		inId        : String,
		inAssetId   : String,
		inQuantity  : Long,
		inUnitPrice : Long,
		inDate      : LocalDate
	): EditResult
	{
		val vAccount = inFile.accounts.firstOrNull { it.id == inId } ?: return refused(SetupError.NotFound)	// compte
		if (vAccount.type != AccountType.BROKERAGE.name) return refused(SetupError.KindMismatch)
		if (inFile.assets.none { it.id == inAssetId }) return refused(SetupError.NotFound)
		if (inQuantity <= 0L || inUnitPrice <= 0L) return EditResult.Refused(listOf(kPositiveValues))
		val vNow   = now()															// horodatage
		val vOld   = openingOf(inFile.transactions, inId)							// solde initial actuel
		val vTrade = TradeDto(inNewId(), inAssetId, TradeSide.BUY.name, inQuantity, inUnitPrice, 0)	// position
		val vTxs   = if (vOld == null) inFile.transactions + newOpening(inId, OpeningBalance(0, inDate), vNow).let { it.copy(trades = listOf(vTrade)) }
			else inFile.transactions.map { if (it.id == vOld.id) it.copy(trades = it.trades + vTrade, updatedAt = vNow) else it }	// opérations modifiées
		return EditResult.Done(inFile.copy(transactions = vTxs), vTrade.id)
	}

	/*
		Retire une position initiale ; le solde initial disparaît s'il ne porte
		plus ni espèces ni position.
		inFile    : contenu
		inId      : compte Bourse
		inTradeId : position
	*/
	fun removeOpeningPosition(
		inFile    : BackupFile,
		inId      : String,
		inTradeId : String
	): EditResult
	{
		val vOld = openingOf(inFile.transactions, inId) ?: return refused(SetupError.NotFound)	// solde initial
		val vNew = vOld.copy(trades = vOld.trades.filterNot { it.id == inTradeId }, updatedAt = now())	// sans la position
		val vTxs = if (vNew.trades.isEmpty() && vNew.postings.single().amountCents == 0L) inFile.transactions - vOld
			else inFile.transactions.map { if (it.id == vOld.id) vNew else it }	// opérations modifiées
		val vFile = inFile.copy(transactions = vTxs)								// nouveau contenu
		positionRefusal(vFile, inId, vOld.trades.filter { it.id == inTradeId })?.let { return it }
		return EditResult.Done(vFile, inId)
	}

	/*
		Nombre d'opérations d'un compte, hors son solde initial s'il ne porte
		que des espèces (des positions initiales comptent : elles verrouillent
		le type Bourse).
		inFile : contenu
		inId   : compte
	*/
	fun operationCount(
		inFile : BackupFile,
		inId   : String
	): Int = inFile.transactions.count { inTx ->
		(inTx.kind != TransactionKind.OPENING.name || inTx.trades.isNotEmpty()) && inTx.postings.any { it.accountId == inId }
	}

	/*
		Supprime un compte. Simple : seulement s'il n'a pas d'autre opération que
		son solde initial (supprimé avec lui). Définitive : toutes les opérations
		qui le touchent (un virement en entier) et ses relevés ; la vente d'un
		bien dont il avait reçu le prix est gardée sans versement, et la vente
		d'un bien dont l'achat disparaît est supprimée avec lui.
		inFile      : contenu
		inId        : compte
		inPermanent : suppression définitive
	*/
	fun deleteAccount(
		inFile      : BackupFile,
		inId        : String,
		inPermanent : Boolean
	): EditResult
	{
		if (inFile.accounts.none { it.id == inId }) return refused(SetupError.NotFound)
		if (!inPermanent && operationCount(inFile, inId) > 0)
		{
			return EditResult.Refused(listOf("Ce compte a des opérations : archivez-le ou supprimez-le définitivement."))
		}
		val vData    = inFile.toDataSet()												// modèles du domaine
		val vRemoval = accountRemoval(vData.transactions, vData.accounts, inId)		// opérations supprimées ou corrigées
		val vTrimmed = vRemoval.trimmed.associateBy { it.id }						// ventes gardées sans versement
		return EditResult.Done(
			inFile.copy(
				accounts     = inFile.accounts.filterNot { it.id == inId },
				transactions = inFile.transactions.filterNot { it.id in vRemoval.removed }.map { inTx ->
					vTrimmed[inTx.id]?.let { inKept -> inTx.copy(postings = inTx.postings.filter { inPosting -> inKept.postings.any { it.id == inPosting.id } }, updatedAt = now()) } ?: inTx
				},
				statements   = inFile.statements.filterNot { it.accountId == inId },
				recurrences  = inFile.recurrences.filterNot { it.accountId == inId || it.toAccountId == inId },
				loans        = inFile.loans.filterNot { it.accountId == inId || it.payFrom == inId }
			),
			inId
		)
	}

	/*
		Fixe l'ordre d'affichage des comptes.
		inFile : contenu
		inIds  : identifiants, dans l'ordre voulu
	*/
	fun reorderAccounts(
		inFile : BackupFile,
		inIds  : List<String>
	): EditResult
	{
		val vRanks = ranks(inIds, inFile.accounts.sortedBy { it.sortOrder }.map { it.id })	// nouveaux rangs
		val vNow   = now()															// horodatage
		return EditResult.Done(inFile.copy(accounts = inFile.accounts.map { inAccount ->
			val vIndex = vRanks.getValue(inAccount.id)								// nouveau rang
			if (vIndex != inAccount.sortOrder) inAccount.copy(sortOrder = vIndex, updatedAt = vNow) else inAccount
		}))
	}

	// ---------- Catégories ----------

	/*
		Crée une catégorie en fin de liste de sa nature.
		inFile : contenu
		inName : nom
		inIcon : clé d'icône
		inKind : dépense ou revenu
	*/
	fun createCategory(
		inFile : BackupFile,
		inName : String,
		inIcon : String,
		inKind : CategoryKind
	): EditResult
	{
		if (inName.isBlank()) return refused(SetupError.BlankName)
		val vNow = now()															// horodatage
		val vCategory = CategoryDto(inNewId(), inName.trim(), inIcon, inKind.name,
			(inFile.categories.filter { it.kind == inKind.name }.maxOfOrNull { it.sortOrder } ?: -1) + 1, false, vNow, vNow)	// nouvelle catégorie
		return EditResult.Done(inFile.copy(categories = inFile.categories + vCategory), vCategory.id)
	}

	/*
		Modifie une catégorie (nom, icône, archivage).
		inFile     : contenu
		inCategory : catégorie modifiée
	*/
	fun updateCategory(
		inFile     : BackupFile,
		inCategory : Category
	): EditResult
	{
		if (inCategory.name.isBlank()) return refused(SetupError.BlankName)
		val vOld = inFile.categories.firstOrNull { it.id == inCategory.id } ?: return refused(SetupError.NotFound)	// version actuelle
		val vNew = vOld.copy(name = inCategory.name.trim(), icon = inCategory.icon, archived = inCategory.archived, updatedAt = now())	// catégorie modifiée
		return EditResult.Done(inFile.copy(categories = inFile.categories.map { if (it.id == vOld.id) vNew else it }), vOld.id)
	}

	/*
		Nombre d'opérations, de récurrences et de crédits qui utilisent une
		catégorie.
		inFile : contenu
		inId   : catégorie
	*/
	fun categoryUsage(
		inFile : BackupFile,
		inId   : String
	): Int = inFile.transactions.count { it.categoryId == inId } + inFile.recurrences.count { it.categoryId == inId } + inFile.loans.count { it.categoryId == inId }

	/*
		Supprime une catégorie ; si elle est utilisée, ses opérations passent
		d'abord à la catégorie de remplacement, de même nature (refus sinon).
		inFile          : contenu
		inId            : catégorie supprimée
		inReplacementId : catégorie de remplacement
	*/
	fun deleteCategory(
		inFile          : BackupFile,
		inId            : String,
		inReplacementId : String?
	): EditResult
	{
		val vData = inFile.toDataSet()												// modèles du domaine
		val vFrom = vData.categories.firstOrNull { it.id == inId } ?: return refused(SetupError.NotFound)	// catégorie supprimée
		var vTxs  = inFile.transactions												// opérations
		if (categoryUsage(inFile, inId) > 0)
		{
			val vTo = inReplacementId?.let { inRepl -> vData.categories.firstOrNull { it.id == inRepl } } ?: return refused(SetupError.CategoryInUse)	// remplacement
			validateReassignment(vFrom, vTo).refusal()?.let { return it }
			val vNow = now()														// horodatage
			vTxs = vTxs.map { if (it.categoryId == inId) it.copy(categoryId = vTo.id, updatedAt = vNow) else it }
			return EditResult.Done(
				inFile.copy(
					categories   = inFile.categories.filterNot { it.id == inId },
					transactions = vTxs,
					recurrences  = inFile.recurrences.map { if (it.categoryId == inId) it.copy(categoryId = vTo.id) else it },
					loans        = inFile.loans.map { if (it.categoryId == inId) it.copy(categoryId = vTo.id) else it }
				),
				inId
			)
		}
		return EditResult.Done(inFile.copy(categories = inFile.categories.filterNot { it.id == inId }, transactions = vTxs), inId)
	}

	/*
		Fixe l'ordre d'affichage des catégories (d'une même nature).
		inFile : contenu
		inIds  : identifiants, dans l'ordre voulu
	*/
	fun reorderCategories(
		inFile : BackupFile,
		inIds  : List<String>
	): EditResult
	{
		val vKind  = inFile.categories.firstOrNull { it.id in inIds }?.kind		// nature réordonnée
		val vRanks = ranks(inIds, inFile.categories.filter { it.kind == vKind }.sortedBy { it.sortOrder }.map { it.id })	// nouveaux rangs
		val vNow   = now()															// horodatage
		return EditResult.Done(inFile.copy(categories = inFile.categories.map { inCategory ->
			val vIndex = vRanks[inCategory.id] ?: return@map inCategory				// nouveau rang (autre nature : inchangé)
			if (vIndex != inCategory.sortOrder) inCategory.copy(sortOrder = vIndex, updatedAt = vNow) else inCategory
		}))
	}

	// ---------- Opérations ----------

	/*
		Crée ou remplace une opération, après validation contre toutes les
		autres (mêmes règles qu'à la saisie sur le téléphone).
		inFile : contenu
		inTx   : opération
	*/
	fun saveTransaction(
		inFile : BackupFile,
		inTx   : Transaction
	): EditResult
	{
		val vData   = inFile.toDataSet()											// modèles du domaine
		val vOthers = vData.transactions.filter { it.id != inTx.id }				// autres opérations
		val vOld    = vData.transactions.firstOrNull { it.id == inTx.id }			// version remplacée
		val vErrors = TransactionValidator(vData.accounts.associateBy { it.id }, vData.categories.associateBy { it.id }, vData.assets.associateBy { it.id })
			.validate(inTx, Ledger(vOthers), vOld)									// règles violées
		if (vErrors.isNotEmpty())
		{
			return EditResult.Refused(vErrors.map { it.message })
		}
		val vNow     = now()														// horodatage
		val vCreated = inFile.transactions.firstOrNull { it.id == inTx.id }?.createdAt ?: vNow	// création conservée
		val vDto     = TransactionDto(
			id         = inTx.id,
			date       = inTx.date.toString(),
			kind       = inTx.kind.name,
			label      = inTx.label,
			categoryId = inTx.categoryId,
			assetId    = inTx.assetId,
			notes      = inTx.notes,
			createdAt  = vCreated,
			updatedAt  = vNow,
			postings   = inTx.postings.map { PostingDto(it.id, it.accountId, it.amountCents) },
			trades     = inTx.trades.map { TradeDto(it.id, it.assetId, it.side.name, it.quantity, it.unitPrice, it.feesCents) },
			linkedId   = inTx.linkedId
		)																			// opération enregistrée
		return EditResult.Done(inFile.copy(transactions = inFile.transactions.filterNot { it.id == inTx.id } + vDto), inTx.id)
	}

	/*
		Supprime des opérations (un virement en entier : il est une seule
		opération). Refusé si cela rend une vente de titres impossible, ou
		laisse la vente d'un bien sans son achat.
		inFile : contenu
		inIds  : identifiants
	*/
	fun deleteTransactions(
		inFile : BackupFile,
		inIds  : Collection<String>
	): EditResult
	{
		val vFile    = inFile.copy(transactions = inFile.transactions.filterNot { it.id in inIds })	// nouveau contenu
		val vRemoved = inFile.toDataSet().transactions.filter { it.id in inIds }	// opérations supprimées
		findOversold(Ledger(vFile.toDataSet().transactions), tradedPairs(vRemoved))?.let { inSale ->
			return EditResult.Refused(listOf(laterSaleMessage(inSale)))
		}
		orphanedSale(vFile.toDataSet().transactions)?.let { inSale ->
			return EditResult.Refused(listOf(ValidationError.SaleWithoutPurchase(inSale.label).message))
		}
		return EditResult.Done(vFile)
	}

	/*
		Change la catégorie de plusieurs opérations à la fois (modification
		groupée sur PC). Seules des dépenses ou des revenus de la même nature
		que la nouvelle catégorie peuvent changer : sinon tout est refusé, sans
		rien modifier (un remboursement reste un remboursement).
		inFile       : contenu
		inIds        : opérations
		inCategoryId : nouvelle catégorie
	*/
	fun recategorize(
		inFile       : BackupFile,
		inIds        : Collection<String>,
		inCategoryId : String
	): EditResult
	{
		val vTarget = inFile.categories.firstOrNull { it.id == inCategoryId } ?: return refused(SetupError.NotFound)	// nouvelle catégorie
		val vKinds  = inFile.categories.associate { it.id to it.kind }				// nature de chaque catégorie
		val vChosen = inFile.transactions.filter { it.id in inIds }				// opérations choisies
		val vWrong  = vChosen.count { it.kind != TransactionKind.STANDARD.name || vKinds[it.categoryId] != vTarget.kind }	// opérations incompatibles
		if (vWrong > 0)
		{
			val vNature = if (vTarget.kind == CategoryKind.EXPENSE.name) "dépenses" else "revenus"	// nature attendue
			return EditResult.Refused(listOf("$vWrong opération(s) choisie(s) ne sont pas des $vNature : « ${vTarget.name} » ne peut pas leur être attribuée."))
		}
		val vNow = now()																// horodatage
		return EditResult.Done(inFile.copy(transactions = inFile.transactions.map { if (it.id in inIds && it.categoryId != inCategoryId) it.copy(categoryId = inCategoryId, updatedAt = vNow) else it }))
	}

	// ---------- Actifs, cours, relevés ----------

	/*
		Crée un actif.
		inFile   : contenu
		inName   : nom
		inIsin   : ISIN, facultatif
		inTicker : symbole de cotation, facultatif
	*/
	fun createAsset(
		inFile   : BackupFile,
		inName   : String,
		inIsin   : String?,
		inTicker : String?
	): EditResult
	{
		if (inName.isBlank()) return refused(SetupError.BlankName)
		val vNow   = now()															// horodatage
		val vAsset = AssetDto(inNewId(), inName.trim(), clean(inIsin), false, vNow, vNow, clean(inTicker))	// nouvel actif
		return EditResult.Done(inFile.copy(assets = inFile.assets + vAsset), vAsset.id)
	}

	/*
		Modifie le nom, l'ISIN et le symbole de cotation d'un actif.
		inFile   : contenu
		inId     : actif
		inName   : nom
		inIsin   : ISIN, facultatif
		inTicker : symbole, facultatif
	*/
	fun updateAsset(
		inFile   : BackupFile,
		inId     : String,
		inName   : String,
		inIsin   : String?,
		inTicker : String?
	): EditResult
	{
		if (inName.isBlank()) return refused(SetupError.BlankName)
		val vOld = inFile.assets.firstOrNull { it.id == inId } ?: return refused(SetupError.NotFound)	// actif
		val vNew = vOld.copy(name = inName.trim(), isin = clean(inIsin), ticker = clean(inTicker), updatedAt = now())	// actif modifié
		return EditResult.Done(inFile.copy(assets = inFile.assets.map { if (it.id == inId) vNew else it }), inId)
	}

	/*
		Enregistre des cours à une même date (un par actif et par jour : remplace
		celui du même jour).
		inFile   : contenu
		inDate   : date des cours
		inPrices : cours par actif, en dix-millièmes d'euro
	*/
	fun putPrices(
		inFile   : BackupFile,
		inDate   : LocalDate,
		inPrices : Map<String, Long>
	): EditResult
	{
		if (inPrices.keys.any { inId -> inFile.assets.none { it.id == inId } }) return refused(SetupError.NotFound)
		if (inPrices.values.any { it <= 0L }) return EditResult.Refused(listOf(kPositiveValues))
		val vDay  = inDate.toString()												// jour, en texte
		val vKept = inFile.prices.filterNot { it.date == vDay && it.assetId in inPrices.keys }	// cours conservés
		return EditResult.Done(inFile.copy(prices = vKept + inPrices.map { (inAsset, inPrice) -> PriceDto(inNewId(), inAsset, vDay, inPrice) }))
	}

	/*
		Enregistre un relevé de la banque (remplace celui du même compte et du
		même jour).
		inFile    : contenu
		inAccount : compte
		inDate    : date du relevé
		inCents   : solde indiqué par la banque
	*/
	fun putStatement(
		inFile    : BackupFile,
		inAccount : String,
		inDate    : LocalDate,
		inCents   : Long
	): EditResult
	{
		if (inFile.accounts.none { it.id == inAccount }) return refused(SetupError.NotFound)
		val vDay = inDate.toString()												// jour, en texte
		val vNew = StatementDto(inNewId(), inAccount, vDay, inCents)				// relevé
		return EditResult.Done(inFile.copy(statements = inFile.statements.filterNot { it.accountId == inAccount && it.date == vDay } + vNew), vNew.id)
	}

	/*
		Supprime un relevé.
		inFile : contenu
		inId   : relevé
	*/
	fun deleteStatement(
		inFile : BackupFile,
		inId   : String
	): EditResult = EditResult.Done(inFile.copy(statements = inFile.statements.filterNot { it.id == inId }))

	// ---------- Opérations récurrentes et crédits ----------

	/*
		Crée ou remplace une opération récurrente, après vérification. Une
		récurrence modifiée garde sa dernière échéance créée (les échéances
		passées ne sont pas recréées).
		inFile : contenu
		inRec  : opération récurrente
	*/
	fun saveRecurrence(
		inFile : BackupFile,
		inRec  : Recurrence
	): EditResult
	{
		val vData = inFile.toDataSet()												// modèles du domaine
		recurrenceError(inRec, vData.accounts.associateBy { it.id }, vData.categories.associateBy { it.id })?.let { return EditResult.Refused(listOf(it.replaceFirstChar { c -> c.uppercase() })) }
		val vKept = vData.recurrences.firstOrNull { it.id == inRec.id }?.last		// dernière échéance déjà créée
		val vSaved = inRec.copy(label = inRec.label.trim(), notes = inRec.notes?.trim()?.ifEmpty { null }, last = vKept ?: inRec.last)	// récurrence retenue
		return EditResult.Done(inFile.copy(recurrences = inFile.recurrences.filterNot { it.id == inRec.id } + vSaved.toDto()), inRec.id)
	}

	/*
		Supprime une opération récurrente (les opérations déjà créées restent).
		inFile : contenu
		inId   : récurrence
	*/
	fun deleteRecurrence(
		inFile : BackupFile,
		inId   : String
	): EditResult = EditResult.Done(inFile.copy(recurrences = inFile.recurrences.filterNot { it.id == inId }))

	/*
		Crée un crédit : un compte de type Crédit en fin de liste, son solde
		initial du jour (moins le capital restant dû après les mensualités
		déjà passées), et son tableau d'amortissement. Les mensualités passées sont
		tenues pour payées avant le suivi dans l'appli ; les suivantes seront
		créées à leur date. Sans catégorie choisie, les intérêts vont dans la
		catégorie de dépense « Crédit », créée si besoin.
		inFile        : contenu
		inName        : nom du compte
		inInstitution : établissement, facultatif
		inShare       : part détenue, en %
		inLoan        : paramètres (identifiants de compte et de crédit ignorés ; catégorie vide : « Crédit »)
		inToday       : date du jour
	*/
	fun createLoan(
		inFile        : BackupFile,
		inName        : String,
		inInstitution : String?,
		inShare       : Int,
		inLoan        : Loan,
		inToday       : LocalDate
	): EditResult
	{
		var vFile = inFile																// contenu modifié
		val vCategory = inLoan.categoryId.takeIf { inId -> vFile.categories.any { it.id == inId } }
			?: vFile.categories.firstOrNull { it.kind == CategoryKind.EXPENSE.name && !it.archived && themeOfCategory(it.name) == ExpenseTheme.CREDIT }?.id
			?: when (val vCreated = createCategory(vFile, "Crédit", "bank", CategoryKind.EXPENSE))
			{
				is EditResult.Done    -> { vFile = vCreated.file; vCreated.id!! }
				is EditResult.Refused -> return vCreated
			}																			// catégorie des intérêts
		val vAccount = when (val vCreated = createAccount(vFile, inName, AccountType.LOAN, inInstitution, inShare, null))
		{
			is EditResult.Done    -> vCreated
			is EditResult.Refused -> return vCreated
		}																				// compte créé
		vFile = vAccount.file
		val vLoan  = inLoan.copy(id = inNewId(), accountId = vAccount.id!!, categoryId = vCategory, last = amortization(inLoan).lastOrNull { !it.date.isAfter(inToday) }?.date)	// crédit
		val vData  = vFile.toDataSet()													// modèles du domaine
		loanError(vLoan, vData.accounts.associateBy { it.id }, vData.categories.associateBy { it.id })?.let { return EditResult.Refused(listOf(it.replaceFirstChar { c -> c.uppercase() })) }
		val vOpen  = OpeningBalance(-remainingAt(vLoan, inToday), inToday)				// capital restant dû aujourd'hui
		val vNow   = now()																// horodatage
		return EditResult.Done(vFile.copy(transactions = vFile.transactions + newOpening(vLoan.accountId, vOpen, vNow), loans = vFile.loans + vLoan.toDto()), vLoan.accountId)
	}

	/*
		Supprime le tableau d'amortissement d'un crédit : plus aucune
		mensualité ne sera créée ; le compte et ses opérations restent.
		inFile : contenu
		inId   : crédit
	*/
	fun deleteLoan(
		inFile : BackupFile,
		inId   : String
	): EditResult = EditResult.Done(inFile.copy(loans = inFile.loans.filterNot { it.id == inId }))

	/*
		Crée les opérations des échéances arrivées (voir dueOperations) et
		avance les récurrences et les crédits. Une opération qui ne passe pas
		les règles de saisie (catégorie disparue…) n'est pas créée, mais son
		échéance est passée : elle ne sera pas reproposée.
		Les horodatages des opérations créées sont tirés de leur date : deux
		appareils qui créent la même échéance écrivent exactement la même
		opération. Null s'il n'y a rien à faire.
		inFile  : contenu
		inToday : date du jour
	*/
	fun applyDue(
		inFile  : BackupFile,
		inToday : LocalDate
	): BackupFile?
	{
		val vData = inFile.toDataSet()													// modèles du domaine
		val vDue  = dueOperations(vData.accounts, vData.transactions, vData.recurrences, vData.loans, inToday)	// échéances arrivées
		if (vDue.isEmpty) return null
		val vValidator = TransactionValidator(vData.accounts.associateBy { it.id }, vData.categories.associateBy { it.id }, vData.assets.associateBy { it.id })	// règles
		val vLedger    = Ledger(vData.transactions)										// opérations existantes
		val vAdded     = vDue.transactions.filter { vValidator.validate(it, vLedger).isEmpty() }.map { inTx ->
			val vStamp = inTx.date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()	// horodatage tiré de la date
			TransactionDto(inTx.id, inTx.date.toString(), inTx.kind.name, inTx.label, inTx.categoryId, inTx.assetId, inTx.notes, vStamp, vStamp,
				inTx.postings.map { PostingDto(it.id, it.accountId, it.amountCents) })
		}																				// opérations ajoutées
		val vRecs  = vDue.recurrences.associateBy { it.id }								// récurrences avancées
		val vLoans = vDue.loans.associateBy { it.id }									// crédits avancés
		return inFile.copy(
			transactions = inFile.transactions + vAdded,
			recurrences  = inFile.recurrences.map { inRec -> vRecs[inRec.id]?.toDto() ?: inRec },
			loans        = inFile.loans.map { inLoan -> vLoans[inLoan.id]?.toDto() ?: inLoan }
		)
	}

	// ---------- Outils ----------

	/*
		Refus si le contenu modifié rend une vente du compte impossible (titres
		plus détenus à sa date) ; null sinon.
		inFile    : contenu modifié
		inAccount : compte Bourse
		inTrades  : ordres retirés ou déplacés
	*/
	private fun positionRefusal(
		inFile    : BackupFile,
		inAccount : String,
		inTrades  : List<TradeDto>
	): EditResult?
	{
		if (inTrades.isEmpty()) return null
		val vPairs = inTrades.map { inAccount to it.assetId }.toSet()				// positions concernées
		val vSale  = findOversold(Ledger(inFile.toDataSet().transactions), vPairs) ?: return null	// vente devenue impossible
		return EditResult.Refused(listOf(laterSaleMessage(vSale)))
	}

	/*
		Message d'une vente rendue impossible.
		inSale : vente à découvert
	*/
	private fun laterSaleMessage(inSale: OversoldSale): String =
		ValidationError.BreaksLaterSale(inSale.transaction.label, inSale.transaction.date).message

	/*
		Rangs d'une liste réordonnée : les éléments donnés d'abord, dans
		l'ordre voulu, puis les autres (archivés…) dans leur ordre actuel.
		inIds     : identifiants, dans l'ordre voulu
		inCurrent : tous les identifiants, dans l'ordre actuel
	*/
	private fun ranks(
		inIds     : List<String>,
		inCurrent : List<String>
	): Map<String, Int> =
		(inIds.filter { it in inCurrent } + inCurrent.filterNot { it in inIds }).withIndex().associate { it.value to it.index }

	/*
		Instant présent (ms).
	*/
	private fun now(): Long = inClock.millis()

	/*
		Solde initial d'un compte parmi les opérations ; null s'il n'en a pas.
		inTxs : opérations
		inId  : compte
	*/
	private fun openingOf(
		inTxs : List<TransactionDto>,
		inId  : String
	): TransactionDto? = inTxs.firstOrNull { inTx -> inTx.kind == TransactionKind.OPENING.name && inTx.postings.any { it.accountId == inId } }

	/*
		Opération de solde initial d'un compte.
		inId      : compte
		inOpening : montant et date
		inNow     : horodatage
	*/
	private fun newOpening(
		inId      : String,
		inOpening : OpeningBalance,
		inNow     : Long
	): TransactionDto =
		TransactionDto(inNewId(), inOpening.date.toString(), TransactionKind.OPENING.name, kOpeningLabel, null, null, null, inNow, inNow,
			listOf(PostingDto(inNewId(), inId, inOpening.cents)))

	/*
		Texte nettoyé (espaces retirés, majuscules) ; null s'il est vide.
		inText : texte saisi
	*/
	private fun clean(inText: String?): String? = inText?.trim()?.uppercase()?.ifEmpty { null }

	/*
		Refus portant une règle.
		inError : règle violée
	*/
	private fun refused(inError: SetupError): EditResult = EditResult.Refused(listOf(inError.message))

	/*
		Refus si la liste de règles violées n'est pas vide ; null sinon.
	*/
	private fun List<SetupError>.refusal(): EditResult? = if (isEmpty()) null else EditResult.Refused(map { it.message })
}
