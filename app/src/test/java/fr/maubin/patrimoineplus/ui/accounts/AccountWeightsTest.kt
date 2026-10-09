package fr.maubin.patrimoineplus.ui.accounts

import fr.maubin.patrimoineplus.domain.calc.wholePercents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
	Tests de la part de chaque compte dans le patrimoine.
*/
class AccountWeightsTest
{
	/*
		Parts : valeurs comptées positives seulement (un compte à découvert ou
		vide n'y figure pas), rangées de la plus grosse à la plus petite, parts
		entières qui font 100 ; les parts entières génériques gardent l'ordre
		reçu.
	*/
	@Test
	fun weightsOfPositiveCountedValues()
	{
		val vWeights = accountWeights(mapOf("courant" to 2_000_00L, "joint" to 500_00L, "livret" to 10_000_00L, "pea" to 0L, "decouvert" to -150_00L))	// valeurs comptées
		assertEquals(setOf("livret", "courant", "joint"), vWeights.keys)
		assertEquals(listOf(0, 1, 2), listOf("livret", "courant", "joint").map { vWeights.getValue(it).rank })
		assertEquals(listOf(80, 16, 4), listOf("livret", "courant", "joint").map { vWeights.getValue(it).percent })
		assertEquals(500_00L, vWeights.getValue("joint").counted)
		assertNull(vWeights["decouvert"])
		assertEquals(listOf(17, 50, 17, 16), wholePercents(listOf(1L, 3L, 1L, 1L)))
		assertEquals(listOf(0, 0), wholePercents(listOf(0L, 0L)))
	}
}
