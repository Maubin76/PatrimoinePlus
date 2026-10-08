package fr.maubin.patrimoineplus.domain.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Tests des règles de sécurité : verrouillage et rappel d'export.
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
		Rappel d'export : seulement s'il y a des données ; dès qu'aucun export
		n'a eu lieu ou que le dernier date de trente jours ou plus.
	*/
	@Test
	fun exportReminderAfterThirtyDays()
	{
		assertFalse(exportReminderDue(false, null, 0))
		assertTrue(exportReminderDue(true, null, 0))
		assertFalse(exportReminderDue(true, 0, kExportReminderMillis - 1))
		assertTrue(exportReminderDue(true, 0, kExportReminderMillis))
	}
}
