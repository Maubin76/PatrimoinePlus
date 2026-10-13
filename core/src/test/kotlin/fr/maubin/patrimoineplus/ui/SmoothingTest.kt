package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.ui.chart.CurveSegment
import fr.maubin.patrimoineplus.ui.chart.smoothCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Lissage des courbes : la courbe passe par chaque point, reste entre les
	valeurs de chaque segment (aucun dépassement), garde les paliers plats et
	des tangentes horizontales aux extremums.
*/
class SmoothingTest
{
	/*
		Point d'un segment de Bézier à un paramètre.
		inX0, inY0 : départ
		inSeg      : segment
		inT        : paramètre, de 0 à 1
	*/
	private fun pointAt(inX0: Float, inY0: Float, inSeg: CurveSegment, inT: Float): Pair<Float, Float>
	{
		val vU = 1f - inT																// complément
		val vX = vU * vU * vU * inX0 + 3 * vU * vU * inT * inSeg.c1x + 3 * vU * inT * inT * inSeg.c2x + inT * inT * inT * inSeg.x	// abscisse
		val vY = vU * vU * vU * inY0 + 3 * vU * vU * inT * inSeg.c1y + 3 * vU * inT * inT * inSeg.c2y + inT * inT * inT * inSeg.y	// ordonnée
		return vX to vY
	}

	/*
		Une série irrégulière (hausse, chute, palier, remontée brusque) : la
		courbe passe par les points et ne sort jamais de l'intervalle des
		deux valeurs de chaque segment.
	*/
	@Test
	fun staysWithinValues()
	{
		val vX   = listOf(0f, 10f, 20f, 30f, 40f, 50f, 60f)							// abscisses
		val vY   = listOf(100f, 140f, 20f, 20f, 25f, 200f, 190f)						// ordonnées
		val vSeg = smoothCurve(vX, vY)													// segments
		assertEquals(vX.size - 1, vSeg.size)
		vSeg.forEachIndexed { inIndex, inSeg ->
			assertEquals(vX[inIndex + 1], inSeg.x)
			assertEquals(vY[inIndex + 1], inSeg.y)
			val vLow  = minOf(vY[inIndex], vY[inIndex + 1]) - 0.001f						// bas permis
			val vHigh = maxOf(vY[inIndex], vY[inIndex + 1]) + 0.001f						// haut permis
			(0..20).forEach { inStep ->
				val vY0 = pointAt(vX[inIndex], vY[inIndex], inSeg, inStep / 20f).second	// ordonnée du point
				assertTrue("segment $inIndex, t=${inStep / 20f} : $vY0", vY0 in vLow..vHigh)
			}
		}
		assertEquals(20f, vSeg[2].c1y)													// palier plat
		assertEquals(20f, vSeg[2].c2y)
		assertEquals(140f, vSeg[0].c2y)												// maximum : tangente horizontale
		assertEquals(140f, vSeg[1].c1y)
	}

	/*
		Une hausse régulière reste une droite ; moins de deux points : rien.
	*/
	@Test
	fun straightAndEmpty()
	{
		val vSeg = smoothCurve(listOf(0f, 1f, 2f), listOf(0f, 2f, 4f))					// droite
		listOf(1f / 3f, 2f / 3f, 2f / 3f, 4f / 3f, 1f, 2f).zip(vSeg[0].let { listOf(it.c1x, it.c1y, it.c2x, it.c2y, it.x, it.y) }).forEach { (inWanted, inGot) ->
			assertEquals(inWanted, inGot, 0.0001f)										// points de contrôle sur la droite
		}
		assertTrue(smoothCurve(listOf(0f), listOf(1f)).isEmpty())
	}
}
