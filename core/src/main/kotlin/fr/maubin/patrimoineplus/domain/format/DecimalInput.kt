package fr.maubin.patrimoineplus.domain.format

import fr.maubin.patrimoineplus.domain.calc.kPriceScale
import fr.maubin.patrimoineplus.domain.calc.kQuantityScale

private const val kScaleDigits = 4												// décimales des quantités et des cours
private const val kMaxWholeDigits = 12											// chiffres maximum de la partie entière

/*
	Lit un nombre décimal positif saisi au clavier (quantité, cours) et le
	convertit en dix-millièmes, sans virgule flottante. Accepte « 12 », « 12,5 »,
	« 1 234.5678 », « 144,91 € ». Refuse (null) : vide, négatif, plus de quatre
	décimales, plusieurs séparateurs, caractères étrangers.
	inText : texte saisi
*/
fun parseScaled(inText: String): Long?
{
	val vClean = inText
		.replace(" ", "")
		.replace(kThousandsSeparator.toString(), "")
		.replace(kCurrencySeparator.toString(), "")
		.removeSuffix("€")
		.replace('.', ',')														// texte normalisé
	val vParts = vClean.split(',')												// partie entière, décimales
	if (vClean.isEmpty() || vParts.size > 2)
	{
		return null
	}
	val vWhole    = vParts[0]													// partie entière
	val vDecimals = vParts.getOrElse(1) { "" }									// décimales
	if ((vWhole.isEmpty() && vDecimals.isEmpty()) || !vWhole.all { it.isDigit() } || !vDecimals.all { it.isDigit() })
	{
		return null
	}
	if (vDecimals.length > kScaleDigits || vWhole.length > kMaxWholeDigits)
	{
		return null
	}
	return vWhole.ifEmpty { "0" }.toLong() * kQuantityScale + vDecimals.padEnd(kScaleDigits, '0').toLong()
}

/*
	Écrit un nombre en dix-millièmes au format français, sans zéros inutiles :
	« 48 », « 12,5 », « 0,0125 » ; milliers séparés.
	inScaled : valeur en dix-millièmes
*/
fun formatScaled(inScaled: Long): String
{
	val vWhole    = inScaled / kQuantityScale										// partie entière
	val vDecimals = (inScaled % kQuantityScale).toString().padStart(kScaleDigits, '0').trimEnd('0')	// décimales utiles
	val vGrouped  = vWhole.toString().reversed().chunked(3).joinToString(kThousandsSeparator.toString()).reversed()	// milliers
	return if (vDecimals.isEmpty()) vGrouped else "$vGrouped,$vDecimals"
}

/*
	Écrit un cours en dix-millièmes d'euro, avec au moins deux décimales :
	« 144,91 € », « 85,1234 € ».
	inPrice : cours en dix-millièmes d'euro
*/
fun formatPrice(inPrice: Long): String
{
	val vText = formatScaled(inPrice)											// cours sans zéros inutiles
	val vComma = vText.indexOf(',')												// position de la virgule
	val vFull = when																// au moins deux décimales
	{
		vComma < 0                    -> "$vText,00"
		vText.length - vComma - 1 < 2 -> vText.padEnd(vComma + 3, '0')
		else                          -> vText
	}
	return "$vFull${kCurrencySeparator}€"
}

/*
	Forme éditable d'une valeur en dix-millièmes (« 12,5 »), pour pré-remplir
	un champ.
	inScaled : valeur en dix-millièmes
*/
fun editableScaled(inScaled: Long): String = formatScaled(inScaled).replace(kThousandsSeparator.toString(), "")

/*
	Garde-fou : quantités et cours partagent la même échelle.
*/
internal val kSameScale: Boolean = kQuantityScale == kPriceScale
