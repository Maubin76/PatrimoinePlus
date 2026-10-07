package fr.maubin.patrimoineplus.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test

/*
	Tests du formatage des montants en euros.
*/
class MoneyFormatTest
{
	/*
		Construit le texte attendu en rendant visibles les espaces spéciales.
		inText : texte où « _ » est l'espace fine et « ~ » l'espace insécable
	*/
	private fun expected(inText: String): String =
		inText.replace('_', '\u202F').replace('~', '\u00A0')

	/*
		Zéro s'affiche avec deux décimales et sans signe.
	*/
	@Test
	fun zero()
	{
		assertEquals(expected("0,00~€"), formatEuros(0))
	}

	/*
		Les centimes inférieurs à 10 sont complétés par un zéro.
	*/
	@Test
	fun smallAmounts()
	{
		assertEquals(expected("0,05~€"), formatEuros(5))
		assertEquals(expected("-0,05~€"), formatEuros(-5))
		assertEquals(expected("1,00~€"), formatEuros(100))
	}

	/*
		Les milliers sont séparés par une espace fine insécable.
	*/
	@Test
	fun thousandsGrouping()
	{
		assertEquals(expected("999,99~€"), formatEuros(99_999))
		assertEquals(expected("1_000,00~€"), formatEuros(100_000))
		assertEquals(expected("1_234_567,89~€"), formatEuros(123_456_789))
		assertEquals(expected("-1_234_567,89~€"), formatEuros(-123_456_789))
	}

	/*
		Le signe « + » n'apparaît que sur demande et jamais devant zéro.
	*/
	@Test
	fun plusSign()
	{
		assertEquals(expected("+12,30~€"), formatEuros(1_230, inShowPlusSign = true))
		assertEquals(expected("0,00~€"), formatEuros(0, inShowPlusSign = true))
		assertEquals(expected("-12,30~€"), formatEuros(-1_230, inShowPlusSign = true))
	}

	/*
		Les valeurs extrêmes d'un Long ne débordent pas.
	*/
	@Test
	fun extremeValues()
	{
		assertEquals(expected("92_233_720_368_547_758,07~€"), formatEuros(Long.MAX_VALUE))
		assertEquals(expected("-92_233_720_368_547_758,08~€"), formatEuros(Long.MIN_VALUE))
	}

	/*
		Le montant masqué ne révèle ni le signe ni l'ordre de grandeur.
	*/
	@Test
	fun maskedAmount()
	{
		assertEquals(expected("Ж_ЯФЩ,ДЮ~€"), kMaskedAmount)
	}
}
