package fr.maubin.patrimoineplus.domain.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Tests des règles de sécurité : verrouillage.
*/
class SafetyTest
{
	/*
		Verrou : jamais s'il est désactivé ; toujours au démarrage ; après une
		minute ou plus en arrière-plan, pas avant.
	*/
	@Test
	fun lockAfterOneMinuteInBackground()
	{
		assertFalse(shouldLock(false, null, 0))
		assertFalse(shouldLock(false, 0, 10 * kLockGraceMillis))
		assertTrue(shouldLock(true, null, 0))
		assertFalse(shouldLock(true, 1_000, 1_000 + kLockGraceMillis - 1))
		assertTrue(shouldLock(true, 1_000, 1_000 + kLockGraceMillis))
		assertTrue(shouldLock(true, 1_000, 1_000 + 5 * kLockGraceMillis))
	}

	/*
		Écran de connexion : toujours au lancement, verrou ou non ; ensuite
		seulement si le verrou est activé et l'authentification due.
	*/
	@Test
	fun gateAtLaunchThenOnlyWithLock()
	{
		assertTrue(gateShown(inColdStart = true, inEnabled = false, inNeedsAuth = false))
		assertTrue(gateShown(inColdStart = true, inEnabled = true, inNeedsAuth = true))
		assertFalse(gateShown(inColdStart = false, inEnabled = false, inNeedsAuth = true))
		assertFalse(gateShown(inColdStart = false, inEnabled = true, inNeedsAuth = false))
		assertTrue(gateShown(inColdStart = false, inEnabled = true, inNeedsAuth = true))
	}
}
