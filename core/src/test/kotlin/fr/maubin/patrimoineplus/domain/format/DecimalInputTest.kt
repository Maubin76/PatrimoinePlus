package fr.maubin.patrimoineplus.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
	Tests de la lecture et de l'écriture des quantités et des cours.
*/
class DecimalInputTest
{
	private val kNarrow = '\u202F'														// espace fine insécable
	private val kNbsp   = '\u00A0'														// espace insécable

	/*
		Formes acceptées et refusées.
	*/
	@Test
	fun parsing()
	{
		assertEquals(480_000L, parseScaled("48"))
		assertEquals(125_000L, parseScaled("12,5"))
		assertEquals(12_345_678L, parseScaled("1 234.5678"))
		assertEquals(1_449_100L, parseScaled("144,91 €"))
		assertEquals(125L, parseScaled("0,0125"))
		assertNull(parseScaled(""))
		assertNull(parseScaled("-3"))
		assertNull(parseScaled("1,23456"))
		assertNull(parseScaled("1,2,3"))
		assertNull(parseScaled("abc"))
	}

	/*
		Écriture sans zéros inutiles, milliers séparés ; cours avec au moins deux
		décimales ; forme éditable relue à l'identique.
	*/
	@Test
	fun formatting()
	{
		assertEquals("48", formatScaled(480_000))
		assertEquals("12,5", formatScaled(125_000))
		assertEquals("0,0125", formatScaled(125))
		assertEquals("1${kNarrow}234,5", formatScaled(12_345_000))
		assertEquals("144,90${kNbsp}€", formatPrice(1_449_000))
		assertEquals("85,1234${kNbsp}€", formatPrice(851_234))
		assertEquals("100,00${kNbsp}€", formatPrice(1_000_000))
		listOf(0L, 125L, 480_000L, 12_345_678L).forEach { assertEquals(it, parseScaled(editableScaled(it))) }
		assertEquals(true, kSameScale)
	}
}
