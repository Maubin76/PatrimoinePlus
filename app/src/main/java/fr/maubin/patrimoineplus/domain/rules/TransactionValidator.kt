package fr.maubin.patrimoineplus.domain.rules

import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.heldQuantity
import fr.maubin.patrimoineplus.domain.calc.tradeCashCents
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind

/*
	Violation d'une règle de saisie.
	message : explication pour l'utilisateur
*/
sealed class ValidationError(val message: String)
{
	// Le libellé est vide.
	data object BlankLabel : ValidationError("Le libellé est obligatoire.")

	/*
		Le nombre de jambes ne correspond pas à la nature de l'opération.
		expected : nombre attendu
		actual   : nombre trouvé
	*/
	data class PostingCount(val expected: Int, val actual: Int) :
		ValidationError("L'opération doit concerner $expected compte(s), pas $actual.")

	/*
		Une jambe vise un compte inconnu.
		accountId : compte introuvable
	*/
	data class UnknownAccount(val accountId: String) : ValidationError("Compte introuvable.")

	// Le montant est nul.
	data object ZeroAmount : ValidationError("Le montant ne peut pas être nul.")

	// Catégorie absente ou inconnue sur une dépense ou un revenu.
	data object MissingCategory : ValidationError("Choisissez une catégorie.")

	// Catégorie présente sur une opération qui n'en prend pas.
	data object UnexpectedCategory : ValidationError("Cette opération ne prend pas de catégorie.")

	// Un virement relie un compte à lui-même.
	data object SameAccount : ValidationError("Choisissez deux comptes différents.")

	// Les deux jambes d'un virement ne s'annulent pas.
	data object UnbalancedTransfer : ValidationError("Un virement doit sortir d'un compte le montant qui entre dans l'autre.")

	// Ordres présents sur une opération qui n'en prend pas.
	data object UnexpectedTrade : ValidationError("Cette opération ne prend pas d'ordre de bourse.")

	// Un ordre ou une position vise un compte qui n'est pas de type Bourse.
	data object NotBrokerage : ValidationError("Les ordres de bourse se passent sur un compte Bourse.")

	/*
		Nombre d'ordres incorrect.
		actual : nombre trouvé
	*/
	data class TradeCount(val actual: Int) : ValidationError("Un ordre de bourse comporte exactement un ordre, pas $actual.")

	// Actif inconnu.
	data object UnknownAsset : ValidationError("Actif introuvable.")

	// Quantité, cours ou frais hors limites.
	data object InvalidTradeValues : ValidationError("Quantité et cours doivent être positifs, les frais positifs ou nuls.")

	/*
		Le montant de la jambe ne correspond pas à cours × quantité ± frais.
		expected : montant attendu, en centimes
	*/
	data class TradeAmountMismatch(val expected: Long) : ValidationError("Le montant de l'ordre ne correspond pas au cours, à la quantité et aux frais.")

	/*
		Vente supérieure à la quantité détenue à la date.
		held : quantité détenue, en dix-millièmes
	*/
	data class OversoldPosition(val held: Long) : ValidationError("La vente dépasse la quantité détenue à cette date.")

	// Une position initiale doit être un achat.
	data object OpeningMustBuy : ValidationError("Une position initiale se saisit comme un achat.")
}

/*
	Règles de saisie d'une opération, vérifiées avant tout enregistrement.
	Pure : ne lit que ce qu'on lui donne.
	inAccounts   : comptes connus, par identifiant
	inCategories : catégories connues, par identifiant
	inAssets     : actifs connus, par identifiant
*/
class TransactionValidator(
	private val inAccounts   : Map<String, Account>,
	private val inCategories : Map<String, Category>,
	private val inAssets     : Map<String, Asset>
)
{
	/*
		Vérifie une opération ; liste vide = opération valide.
		inTx          : opération à vérifier
		inOtherLedger : grand livre des autres opérations (sans celle-ci, en cas
		                de modification), pour contrôler les ventes
	*/
	fun validate(
		inTx          : Transaction,
		inOtherLedger : Ledger
	): List<ValidationError>
	{
		val vErrors = mutableListOf<ValidationError>()							// violations trouvées
		if (inTx.label.isBlank())
		{
			vErrors += ValidationError.BlankLabel
		}
		inTx.postings.map { it.accountId }.filter { it !in inAccounts }.forEach {
			vErrors += ValidationError.UnknownAccount(it)
		}
		when (inTx.kind)
		{
			TransactionKind.STANDARD -> checkStandard(inTx, vErrors)
			TransactionKind.TRANSFER -> checkTransfer(inTx, vErrors)
			TransactionKind.TRADE    -> checkTrade(inTx, inOtherLedger, vErrors)
			TransactionKind.OPENING  -> checkOpening(inTx, vErrors)
		}
		return vErrors
	}

	/*
		Dépense ou revenu : une jambe non nulle, une catégorie connue, pas d'ordre.
		inTx     : opération
		inErrors : violations, complétées
	*/
	private fun checkStandard(
		inTx     : Transaction,
		inErrors : MutableList<ValidationError>
	)
	{
		checkCount(inTx, 1, inErrors)
		if (inTx.postings.any { it.amountCents == 0L })
		{
			inErrors += ValidationError.ZeroAmount
		}
		if (inTx.categoryId == null || inTx.categoryId !in inCategories)
		{
			inErrors += ValidationError.MissingCategory
		}
		if (inTx.trades.isNotEmpty())
		{
			inErrors += ValidationError.UnexpectedTrade
		}
	}

	/*
		Virement : deux jambes sur deux comptes distincts, de somme nulle et non
		nulles, sans catégorie ni ordre.
		inTx     : opération
		inErrors : violations, complétées
	*/
	private fun checkTransfer(
		inTx     : Transaction,
		inErrors : MutableList<ValidationError>
	)
	{
		checkCount(inTx, 2, inErrors)
		checkNoCategory(inTx, inErrors)
		if (inTx.trades.isNotEmpty())
		{
			inErrors += ValidationError.UnexpectedTrade
		}
		if (inTx.postings.size != 2)
		{
			return
		}
		if (inTx.postings[0].accountId == inTx.postings[1].accountId)
		{
			inErrors += ValidationError.SameAccount
		}
		if (inTx.postings.any { it.amountCents == 0L })
		{
			inErrors += ValidationError.ZeroAmount
		}
		if (inTx.postings.sumOf { it.amountCents } != 0L)
		{
			inErrors += ValidationError.UnbalancedTransfer
		}
	}

	/*
		Ordre de bourse : une jambe sur un compte Bourse, un ordre valide dont la
		jambe vaut cours × quantité ± frais ; une vente ne dépasse pas la
		quantité détenue à la date.
		inTx          : opération
		inOtherLedger : autres opérations
		inErrors      : violations, complétées
	*/
	private fun checkTrade(
		inTx          : Transaction,
		inOtherLedger : Ledger,
		inErrors      : MutableList<ValidationError>
	)
	{
		checkCount(inTx, 1, inErrors)
		checkNoCategory(inTx, inErrors)
		checkBrokerage(inTx, inErrors)
		if (inTx.trades.size != 1)
		{
			inErrors += ValidationError.TradeCount(inTx.trades.size)
			return
		}
		val vTrade = inTx.trades.single()										// l'ordre
		if (!checkTradeValues(vTrade.assetId, vTrade.quantity, vTrade.unitPrice, vTrade.feesCents, inErrors))
		{
			return
		}
		val vExpected = tradeCashCents(vTrade.side, vTrade.quantity, vTrade.unitPrice, vTrade.feesCents)	// jambe attendue
		val vPosting  = inTx.postings.singleOrNull() ?: return				// jambe saisie
		if (vPosting.amountCents != vExpected)
		{
			inErrors += ValidationError.TradeAmountMismatch(vExpected)
		}
		if (vTrade.side == TradeSide.SELL)
		{
			val vHeld = heldQuantity(inOtherLedger, vPosting.accountId, vTrade.assetId, inTx.date)	// quantité détenue
			if (vTrade.quantity > vHeld)
			{
				inErrors += ValidationError.OversoldPosition(vHeld)
			}
		}
	}

	/*
		Solde initial : une jambe (espèces, éventuellement 0 €), sans catégorie ;
		des positions initiales (achats) seulement sur un compte Bourse.
		inTx     : opération
		inErrors : violations, complétées
	*/
	private fun checkOpening(
		inTx     : Transaction,
		inErrors : MutableList<ValidationError>
	)
	{
		checkCount(inTx, 1, inErrors)
		checkNoCategory(inTx, inErrors)
		if (inTx.trades.isEmpty())
		{
			return
		}
		checkBrokerage(inTx, inErrors)
		inTx.trades.forEach { inTrade ->
			if (inTrade.side != TradeSide.BUY)
			{
				inErrors += ValidationError.OpeningMustBuy
			}
			checkTradeValues(inTrade.assetId, inTrade.quantity, inTrade.unitPrice, inTrade.feesCents, inErrors)
		}
	}

	/*
		Vérifie le nombre de jambes.
		inTx       : opération
		inExpected : nombre attendu
		inErrors   : violations, complétées
	*/
	private fun checkCount(
		inTx       : Transaction,
		inExpected : Int,
		inErrors   : MutableList<ValidationError>
	)
	{
		if (inTx.postings.size != inExpected)
		{
			inErrors += ValidationError.PostingCount(inExpected, inTx.postings.size)
		}
	}

	/*
		Refuse une catégorie sur une opération qui n'en prend pas.
		inTx     : opération
		inErrors : violations, complétées
	*/
	private fun checkNoCategory(
		inTx     : Transaction,
		inErrors : MutableList<ValidationError>
	)
	{
		if (inTx.categoryId != null)
		{
			inErrors += ValidationError.UnexpectedCategory
		}
	}

	/*
		Exige que les jambes soient sur des comptes Bourse.
		inTx     : opération
		inErrors : violations, complétées
	*/
	private fun checkBrokerage(
		inTx     : Transaction,
		inErrors : MutableList<ValidationError>
	)
	{
		if (inTx.postings.any { inAccounts[it.accountId]?.let { inAccount -> inAccount.type != AccountType.BROKERAGE } == true })
		{
			inErrors += ValidationError.NotBrokerage
		}
	}

	/*
		Vérifie l'actif et les valeurs d'un ordre ; vrai si tout est valide.
		inAssetId   : actif
		inQuantity  : quantité
		inUnitPrice : cours
		inFeesCents : frais
		inErrors    : violations, complétées
	*/
	private fun checkTradeValues(
		inAssetId   : String,
		inQuantity  : Long,
		inUnitPrice : Long,
		inFeesCents : Long,
		inErrors    : MutableList<ValidationError>
	): Boolean
	{
		var vValid = true														// aucune violation
		if (inAssetId !in inAssets)
		{
			inErrors += ValidationError.UnknownAsset
			vValid = false
		}
		if (inQuantity <= 0 || inUnitPrice <= 0 || inFeesCents < 0)
		{
			inErrors += ValidationError.InvalidTradeValues
			vValid = false
		}
		return vValid
	}
}
