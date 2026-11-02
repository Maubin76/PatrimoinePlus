package fr.maubin.patrimoineplus.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/*
	Une seule appli PC à la fois (boucle locale, port libre) : la seconde
	instance réveille la première et ne se lance pas ; au démarrage avec
	Windows, elle se ferme sans rien afficher ; un port pris par un autre
	programme n'empêche pas le lancement.
*/
class SingleInstanceTest
{
	/*
		Attend qu'une condition soit vraie (fil du verrou), 2 s au plus.
		inCheck : condition
	*/
	private fun waitFor(inCheck: () -> Boolean): Boolean
	{
		val vEnd = System.currentTimeMillis() + 2_000											// échéance
		while (!inCheck() && System.currentTimeMillis() < vEnd) Thread.sleep(10)
		return inCheck()
	}

	/*
		Seconde instance : la première passe au premier plan (une demande par
		lancement) ; démarrage avec Windows : aucune demande.
	*/
	@Test
	fun secondLaunchShowsTheFirst()
	{
		val vFirst = SingleInstance(0)																// première instance
		assertTrue(vFirst.claim())
		try
		{
			assertFalse(SingleInstance(vFirst.port).claim())
			assertTrue(waitFor { vFirst.requests.value == 1 })
			assertFalse(SingleInstance(vFirst.port).claim(inNotify = false))
			assertFalse(SingleInstance(vFirst.port).claim())
			assertTrue(waitFor { vFirst.requests.value == 2 })
		}
		finally
		{
			vFirst.release()
		}
	}

	/*
		Port tenu par un autre programme (qui ne répond pas) : l'appli se lance.
	*/
	@Test
	fun foreignPortDoesNotBlock()
	{
		val vOther = ServerSocket().apply { bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0)) }	// autre programme
		vOther.use {
			Thread { runCatching { vOther.accept().use { Thread.sleep(100) } } }.apply { isDaemon = true }.start()
			assertTrue(SingleInstance(vOther.localPort).claim())
		}
		assertEquals(0, SingleInstance(0).let { it.port })
	}
}
