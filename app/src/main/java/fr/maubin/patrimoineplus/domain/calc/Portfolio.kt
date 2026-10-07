package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.model.AssetPrice
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/*
	Position détenue sur un actif dans un compte Bourse.
	assetId        : actif
	quantity       : quantité détenue, en dix-millièmes
	costCents      : prix de revient total restant (frais d'achat inclus)
	priceDate      : date du cours utilisé ; null si aucun cours relevé
	unitPrice      : cours utilisé (dernier relevé, ou PRU à défaut), dix-millièmes d'euro
	valueCents     : valeur = quantité × cours
	unrealizedCents : plus-value latente = valeur − prix de revient
*/
data class Position(
	val assetId         : String,
	val quantity        : Long,
	val costCents       : Long,
	val priceDate       : LocalDate?,
	val unitPrice       : Long,
	val valueCents      : Long,
	val unrealizedCents : Long
)
{
	// Vrai si aucun cours n'a été relevé : la valeur est alors le prix de revient.
	val isUnpriced: Boolean get() = priceDate == null
}

/*
	Plus-value réalisée par une vente.
	transactionId : opération de vente
	date          : date de la vente
	assetId       : actif vendu
	gainCents     : produit net − prix de revient de la quantité vendue
*/
data class RealizedGain(
	val transactionId : String,
	val date          : LocalDate,
	val assetId       : String,
	val gainCents     : Long
)

/*
	Valorisation d'un compte Bourse à une date.
	cashCents  : espèces = solde du compte
	positions  : positions non nulles
	realized   : plus-values réalisées jusqu'à la date
*/
data class PortfolioValuation(
	val cashCents : Long,
	val positions : List<Position>,
	val realized  : List<RealizedGain>
)
{
	// Valeur totale : espèces + positions.
	val valueCents: Long get() = cashCents + positions.sumOf { it.valueCents }

	// Plus-value latente totale.
	val unrealizedCents: Long get() = positions.sumOf { it.unrealizedCents }
}

/*
	Calcule les positions d'un compte Bourse au PRU moyen pondéré, frais
	inclus : un achat ajoute son coût total, une vente retire une part du coût
	proportionnelle à la quantité vendue (le PRU est donc inchangé par une vente).
	inLedger    : grand livre
	inAccountId : compte Bourse
	inDate      : date de valorisation, incluse
	inPrices    : cours relevés (tous actifs)
*/
fun valuePortfolio(
	inLedger    : Ledger,
	inAccountId : String,
	inDate      : LocalDate,
	inPrices    : List<AssetPrice>
): PortfolioValuation
{
	val vQuantity = mutableMapOf<String, Long>()								// quantité par actif
	val vCost     = mutableMapOf<String, Long>()								// prix de revient par actif
	val vRealized = mutableListOf<RealizedGain>()								// plus-values réalisées
	inLedger.transactions()
		.filter { !it.date.isAfter(inDate) && isOnAccount(it, inAccountId) }
		.forEach { inTx ->
			inTx.trades.forEach { inTrade ->
				val vHeld = vQuantity[inTrade.assetId] ?: 0L					// quantité avant l'ordre
				val vPaid = vCost[inTrade.assetId] ?: 0L						// coût avant l'ordre
				when (inTrade.side)
				{
					TradeSide.BUY  ->
					{
						vQuantity[inTrade.assetId] = vHeld + inTrade.quantity
						vCost[inTrade.assetId]     = vPaid + grossCents(inTrade.quantity, inTrade.unitPrice) + inTrade.feesCents
					}
					TradeSide.SELL ->
					{
						val vRemoved = if (vHeld == 0L) 0L else roundDiv(vPaid * inTrade.quantity, vHeld)	// coût sorti
						val vNet     = tradeCashCents(TradeSide.SELL, inTrade.quantity, inTrade.unitPrice, inTrade.feesCents)	// produit net
						vQuantity[inTrade.assetId] = vHeld - inTrade.quantity
						vCost[inTrade.assetId]     = vPaid - vRemoved
						vRealized += RealizedGain(inTx.id, inTx.date, inTrade.assetId, vNet - vRemoved)
					}
				}
			}
		}
	val vPositions = vQuantity
		.filterValues { it != 0L }
		.map { (inAssetId, inQty) ->
			val vCostCents = vCost.getValue(inAssetId)							// prix de revient restant
			val vLast      = inPrices
				.filter { it.assetId == inAssetId && !it.date.isAfter(inDate) }
				.maxByOrNull { it.date }										// dernier cours connu
			val vPrice     = vLast?.unitPrice ?: averagePrice(vCostCents, inQty)	// cours retenu
			val vValue     = if (vLast == null) vCostCents else grossCents(inQty, vPrice)	// valeur
			Position(inAssetId, inQty, vCostCents, vLast?.date, vPrice, vValue, vValue - vCostCents)
		}
		.sortedByDescending { it.valueCents }
	return PortfolioValuation(inLedger.balanceAt(inAccountId, inDate), vPositions, vRealized)
}

/*
	Quantité détenue d'un actif sur un compte à une date (pour refuser une
	vente à découvert).
	inLedger    : grand livre
	inAccountId : compte Bourse
	inAssetId   : actif
	inDate      : date incluse
*/
fun heldQuantity(
	inLedger    : Ledger,
	inAccountId : String,
	inAssetId   : String,
	inDate      : LocalDate
): Long =
	inLedger.transactions()
		.filter { !it.date.isAfter(inDate) && isOnAccount(it, inAccountId) }
		.flatMap { it.trades }
		.filter { it.assetId == inAssetId }
		.sumOf { if (it.side == TradeSide.BUY) it.quantity else -it.quantity }

/*
	PRU, en dix-millièmes d'euro : prix de revient total / quantité.
	inCostCents : prix de revient total, en centimes
	inQuantity  : quantité, en dix-millièmes
*/
fun averagePrice(
	inCostCents : Long,
	inQuantity  : Long
): Long =
	if (inQuantity == 0L) 0L
	else BigDecimal(inCostCents)
		.multiply(BigDecimal(kQuantityScale * kPriceScale / 100))
		.divide(BigDecimal(inQuantity), 0, RoundingMode.HALF_UP)
		.longValueExact()

/*
	Vrai si une opération porte des ordres d'un compte : elle a des ordres et
	sa jambe est sur ce compte (un solde initial fait de positions seules porte
	une jambe à 0 € pour désigner son compte).
	inTx        : opération
	inAccountId : compte
*/
private fun isOnAccount(
	inTx        : Transaction,
	inAccountId : String
): Boolean =
	inTx.trades.isNotEmpty() && inTx.postings.any { it.accountId == inAccountId }
