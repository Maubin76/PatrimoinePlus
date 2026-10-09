package fr.maubin.patrimoineplus.domain.format

import kotlin.math.abs

private const val kMaxEuroDigits = 12										// chiffres maximum de la partie entière

/*
	Lit un montant saisi au clavier et le convertit en centimes, sans virgule
	flottante. Accepte : « 1234 », « 1 234,5 », « 1234.56 », « -12,30 »,
	« 1 234,56 € ». Refuse (null) : texte vide, plus de deux décimales,
	plusieurs séparateurs décimaux, caractères étrangers, montant démesuré.
	inText : texte saisi
*/
fun parseEuros(inText: String): Long?
{
	val vClean = inText
		.replace(" ", "")
		.replace(kThousandsSeparator.toString(), "")
		.replace(kCurrencySeparator.toString(), "")
		.removeSuffix("€")
		.replace('.', ',')														// texte normalisé
	val vNegative = vClean.startsWith("-") || vClean.startsWith("−")		// signe moins (ASCII ou typographique)
	val vBody     = if (vNegative) vClean.substring(1) else vClean			// sans le signe
	val vParts    = vBody.split(',')										// partie entière, décimales
	if (vBody.isEmpty() || vParts.size > 2)
	{
		return null
	}
	val vWhole    = vParts[0]												// partie entière
	val vDecimals = vParts.getOrElse(1) { "" }								// décimales
	if (vWhole.isEmpty() && vDecimals.isEmpty())
	{
		return null
	}
	if (!vWhole.all { it.isDigit() } || !vDecimals.all { it.isDigit() } || vDecimals.length > 2 || vWhole.length > kMaxEuroDigits)
	{
		return null
	}
	val vCents = (vWhole.ifEmpty { "0" }.toLong() * kCentsPerEuro) + vDecimals.padEnd(2, '0').toLong()	// montant absolu
	return if (vNegative) -vCents else vCents
}

/*
	Écrit un montant en centimes sous la forme éditable « 1234,56 » (sans
	séparateur de milliers ni symbole), pour pré-remplir un champ.
	inCents : montant signé
*/
fun editableEuros(inCents: Long): String
{
	val vSign = if (inCents < 0) "-" else ""									// signe
	return "$vSign${abs(inCents / kCentsPerEuro)},${abs(inCents % kCentsPerEuro).toString().padStart(2, '0')}"
}
