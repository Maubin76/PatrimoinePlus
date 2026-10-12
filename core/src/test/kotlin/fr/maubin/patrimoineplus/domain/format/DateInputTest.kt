package fr.maubin.patrimoineplus.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/*
	Dates saisies à la main (appli PC) : jour et mois sur un ou deux
	chiffres ; dates impossibles et textes illisibles refusés (avant :
	« 5/3/2026 » refusé).
*/
class DateInputTest
{
	/*
		Formes acceptées et refusées.
	*/
	@Test
	fun parsesFrenchDates()
	{
		assertEquals(LocalDate.of(2026, 3, 5), parseFrenchDate("5/3/2026"))
		assertEquals(LocalDate.of(2026, 3, 5), parseFrenchDate(" 05/03/2026 "))
		assertEquals(LocalDate.of(2024, 2, 29), parseFrenchDate("29/02/2024"))
		assertNull(parseFrenchDate("29/02/2026"))
		assertNull(parseFrenchDate("31/04/2026"))
		assertNull(parseFrenchDate("2026-03-05"))
		assertNull(parseFrenchDate("demain"))
		assertEquals("05/03/2026", formatFrenchDate(LocalDate.of(2026, 3, 5)))
	}
}
