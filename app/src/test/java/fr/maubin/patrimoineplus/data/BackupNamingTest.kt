package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.backupFileName
import fr.maubin.patrimoineplus.data.backup.backupsToDelete
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/*
	Tests du nommage et de la rotation des sauvegardes.
*/
class BackupNamingTest
{
	/*
		Rotation : les trois plus récentes sont gardées, les autres supprimées ;
		les fichiers étrangers au modèle ne sont jamais touchés.
	*/
	@Test
	fun keepsThreeMostRecent()
	{
		val vNames = listOf(
			backupFileName(LocalDate.of(2026, 10, 7)),
			backupFileName(LocalDate.of(2026, 10, 9)),
			"notes.txt",
			backupFileName(LocalDate.of(2026, 9, 30)),
			backupFileName(LocalDate.of(2026, 10, 8)),
			"patrimoineplus-ancien.json"
		)																		// contenu du dossier
		assertEquals("patrimoineplus-2026-10-09.xlsx", backupFileName(LocalDate.of(2026, 10, 9)))
		assertEquals(listOf("patrimoineplus-2026-09-30.xlsx"), backupsToDelete(vNames))
		assertEquals(emptyList<String>(), backupsToDelete(vNames.take(2)))
	}
}
