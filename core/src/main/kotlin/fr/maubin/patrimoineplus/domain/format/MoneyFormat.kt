package fr.maubin.patrimoineplus.domain.format

import kotlin.math.abs

const val kThousandsSeparator = '\u202F'							// espace fine insécable entre les milliers
const val kCurrencySeparator  = '\u00A0'							// espace insécable avant le symbole €
const val kCentsPerEuro       = 100L								// nombre de centimes dans un euro
const val kMaskedAmount       = "\u0416\u202F\u042F\u0424\u0429,\u0414\u042E\u00A0€"	// montant masqué : lettres cyrilliques à la forme d'un montant

/*
	Formate un montant en centimes au format français : « 1 234,56 € ».
	Calcul entièrement sur des entiers, sans virgule flottante.
	inCents        : montant signé, en centimes
	inShowPlusSign : préfixe « + » devant les montants strictement positifs
*/
fun formatEuros(
	inCents        : Long,
	inShowPlusSign : Boolean = false
): String
{
	val vEuros    = abs(inCents / kCentsPerEuro)							// partie entière, sans signe
	val vCents    = abs(inCents % kCentsPerEuro)							// partie décimale, sans signe
	val vSign     = when												// signe affiché
	{
		inCents < 0                    -> "-"
		inCents > 0 && inShowPlusSign -> "+"
		else                           -> ""
	}
	val vGrouped  = groupThousands(vEuros.toString())						// partie entière groupée
	val vDecimals = vCents.toString().padStart(2, '0')						// toujours deux décimales
	return "$vSign$vGrouped,$vDecimals$kCurrencySeparator€"
}

/*
	Insère le séparateur des milliers dans une suite de chiffres.
	inDigits : chiffres décimaux, sans signe
*/
private fun groupThousands(inDigits: String): String
{
	val vResult = StringBuilder()											// chiffres groupés
	inDigits.forEachIndexed { inIndex, inDigit ->
		if (inIndex > 0 && (inDigits.length - inIndex) % 3 == 0)
		{
			vResult.append(kThousandsSeparator)
		}
		vResult.append(inDigit)
	}
	return vResult.toString()
}
