package fr.maubin.patrimoineplus.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
	Tests de la lecture des montants saisis au clavier.
*/
class AmountInputTest
{
	/*
		Formes acceptées : entier, virgule ou point, une ou deux décimales,
		espaces de milliers, symbole €, signe moins.
	*/
	@Test
	fun acceptedForms()
	{
		assertEquals(123_400L, parseEuros("1234"))
		assertEquals(123_450L, parseEuros("1 234,5"))
		assertEquals(123_456L, parseEuros("1234.56"))
		assertEquals(123_456L, parseEuros("1 234,56 €"))
		assertEquals(123_456L, parseEuros(formatEuros(123_456)))
		assertEquals(-1_230L, parseEuros("-12,30"))
		assertEquals(-1_230L, parseEuros("−12,3"))
		assertEquals(50L, parseEuros(",5"))
		assertEquals(1_200L, parseEuros("12,"))
		assertEquals(0L, parseEuros("0"))
	}

	/*
		Formes refusées : vide, trois décimales, deux séparateurs, lettres,
		montant démesuré.
	*/
	@Test
	fun rejectedForms()
	{
		assertNull(parseEuros(""))
		assertNull(parseEuros("-"))
		assertNull(parseEuros(","))
		assertNull(parseEuros("12,345"))
		assertNull(parseEuros("1,2,3"))
		assertNull(parseEuros("12a"))
		assertNull(parseEuros("1234567890123"))
	}

	/*
		La forme éditable se relit à l'identique.
	*/
	@Test
	fun editableRoundTrip()
	{
		listOf(0L, 5L, 1_230L, -1_230L, 123_456_789L).forEach {
			assertEquals(it, parseEuros(editableEuros(it)))
		}
		assertEquals("-0,05", editableEuros(-5))
		assertEquals("1234,50", editableEuros(123_450))
	}
}
