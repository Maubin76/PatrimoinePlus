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
import fr.maubin.patrimoineplus.domain.rules.validateAccount
import fr.maubin.patrimoineplus.domain.rules.validateReassignment
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

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
		if (inFile.accounts.none { it.id == inId }) return refused(SetupError.NotFound)
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
		return EditResult.Done(inFile.copy(transactions = vTxs), inId)
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
		return EditResult.Done(inFile.copy(transactions = vTxs), inId)
	}

	/*
		Nombre d'opérations d'un compte, hors son solde initial.
		inFile : contenu
		inId   : compte
	*/
	fun operationCount(
		inFile : BackupFile,
		inId   : String
	): Int = inFile.transactions.count { inTx -> inTx.kind != TransactionKind.OPENING.name && inTx.postings.any { it.accountId == inId } }

	/*
		Supprime un compte. Simple : seulement s'il n'a pas d'autre opération que
		son solde initial (supprimé avec lui). Définitive : toutes les opérations
		qui le touchent (un virement en entier) et ses relevés.
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
		return EditResult.Done(
			inFile.copy(
				accounts     = inFile.accounts.filterNot { it.id == inId },
				transactions = inFile.transactions.filterNot { inTx -> inTx.postings.any { it.accountId == inId } },
				statements   = inFile.statements.filterNot { it.accountId == inId }
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
		val vNow = now()															// horodatage
		return EditResult.Done(inFile.copy(accounts = inFile.accounts.map { inAccount ->
			val vIndex = inIds.indexOf(inAccount.id)								// nouveau rang
			if (vIndex >= 0 && vIndex != inAccount.sortOrder) inAccount.copy(sortOrder = vIndex, updatedAt = vNow) else inAccount
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
		Nombre d'opérations qui utilisent une catégorie.
		inFile : contenu
		inId   : catégorie
	*/
	fun categoryUsage(
		inFile : BackupFile,
		inId   : String
	): Int = inFile.transactions.count { it.categoryId == inId }

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
		val vNow = now()															// horodatage
		return EditResult.Done(inFile.copy(categories = inFile.categories.map { inCategory ->
			val vIndex = inIds.indexOf(inCategory.id)								// nouveau rang
			if (vIndex >= 0 && vIndex != inCategory.sortOrder) inCategory.copy(sortOrder = vIndex, updatedAt = vNow) else inCategory
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
		val vErrors = TransactionValidator(vData.accounts.associateBy { it.id }, vData.categories.associateBy { it.id }, vData.assets.associateBy { it.id })
			.validate(inTx, Ledger(vOthers))										// règles violées
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
			trades     = inTx.trades.map { TradeDto(it.id, it.assetId, it.side.name, it.quantity, it.unitPrice, it.feesCents) }
		)																			// opération enregistrée
		return EditResult.Done(inFile.copy(transactions = inFile.transactions.filterNot { it.id == inTx.id } + vDto), inTx.id)
	}

	/*
		Supprime des opérations (un virement en entier : il est une seule opération).
		inFile : contenu
		inIds  : identifiants
	*/
	fun deleteTransactions(
		inFile : BackupFile,
		inIds  : Collection<String>
	): EditResult = EditResult.Done(inFile.copy(transactions = inFile.transactions.filterNot { it.id in inIds }))

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

	// ---------- Outils ----------

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
