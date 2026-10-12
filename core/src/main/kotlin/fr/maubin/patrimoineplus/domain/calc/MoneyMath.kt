package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.model.TradeSide
import java.math.BigDecimal
import java.math.RoundingMode

const val kQuantityScale = 10_000L											// quantités en dix-millièmes d'unité
const val kPriceScale    = 10_000L											// cours en dix-millièmes d'euro

private val kCentsPerMicroProduct = BigDecimal(kQuantityScale * kPriceScale / 100)	// produit quantité × cours → centimes

const val kMaxTradeCents = 100_000_000_000_000L								// montant d'ordre maximal (mille milliards d'euros)

/*
	Division entière arrondie au plus proche, les demis s'éloignant de zéro
	(arrondi commercial, symétrique pour les négatifs).
	inNumerator : dividende
	inDivisor   : diviseur, strictement positif
*/
fun roundDiv(
	inNumerator : Long,
	inDivisor   : Long
): Long =
	BigDecimal(inNumerator).divide(BigDecimal(inDivisor), 0, RoundingMode.HALF_UP).longValueExact()

/*
	Part détenue d'un montant, arrondie au centime.
	inCents   : montant entier, en centimes
	inPercent : part détenue, de 0 à 100
*/
fun shareOf(
	inCents   : Long,
	inPercent : Int
): Long =
	if (inPercent == 100) inCents else roundDiv(inCents * inPercent, 100)

/*
	Montant brut d'une quantité à un cours, arrondi au centime. Calcul en
	précision exacte (BigDecimal) : le produit brut peut dépasser un Long ; il
	est alors plafonné à kMaxTradeCents (valeur absurde, refusée par la
	validation) plutôt que de faire échouer l'affichage.
	inQuantity  : quantité, en dix-millièmes d'unité
	inUnitPrice : cours, en dix-millièmes d'euro
*/
fun grossCents(
	inQuantity  : Long,
	inUnitPrice : Long
): Long =
	BigDecimal(inQuantity)
		.multiply(BigDecimal(inUnitPrice))
		.divide(kCentsPerMicroProduct, 0, RoundingMode.HALF_UP)
		.min(BigDecimal(kMaxTradeCents))
		.longValueExact()

/*
	Montant de la jambe espèces d'un ordre de bourse, signé :
	achat = −(cours × quantité + frais), vente = +(cours × quantité − frais).
	inSide      : achat ou vente
	inQuantity  : quantité, en dix-millièmes d'unité
	inUnitPrice : cours, en dix-millièmes d'euro
	inFeesCents : frais, en centimes
*/
fun tradeCashCents(
	inSide      : TradeSide,
	inQuantity  : Long,
	inUnitPrice : Long,
	inFeesCents : Long
): Long
{
	val vGross = grossCents(inQuantity, inUnitPrice)						// montant hors frais
	return when (inSide)
	{
		TradeSide.BUY  -> -(vGross + inFeesCents)
		TradeSide.SELL -> vGross - inFeesCents
	}
}
