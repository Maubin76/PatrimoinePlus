package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.backupFileOf
import fr.maubin.patrimoineplus.data.demo.buildDemoData
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
	Sauvegardes automatiques du PC : une par jour dans « Sauvegardes PC » du
	dossier synchronisé, réécrite à la demande, sept gardées (les autres
	fichiers, dont les sauvegardes du téléphone, jamais touchés) ; rien sans
	compte ; relue à l'identique ; contrôleur : au lancement sur un dossier,
	jamais en démonstration, état et « Sauvegarder maintenant » pour les
	réglages.
*/
class PcBackupTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// dossier synchronisé jetable

	private val kToday = LocalDate.parse("2026-10-27")									// jour des sauvegardes
	private val kClock = Clock.fixed(Instant.parse("2026-10-27T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private val kFile  = backupFileOf(buildDemoData(kToday))								// données de démonstration

	/*
		Une par jour, réécrite à la demande, rotation, rien sans compte.
	*/
	@Test
	fun dailyAndRotation()
	{
		val vSynced = fTemp.root.toPath()															// dossier synchronisé
		val vBackup = PcBackup(kClock)																// sauvegardes
		val vFolder = vBackup.folder(vSynced)														// « Sauvegardes PC »
		Files.createDirectories(vFolder)
		(1..9).forEach { Files.writeString(vFolder.resolve(pcBackupFileName(kToday.minusDays(it.toLong()))), "ancienne") }
		Files.writeString(vFolder.resolve("notes.txt"), "à garder")
		Files.writeString(vSynced.resolve("patrimoineplus-2026-10-20.xlsx"), "téléphone")
		val vDone = vBackup.backup(vSynced, kFile) as PcBackupResult.Done						// sauvegarde du jour
		assertEquals("patrimoineplus-pc-2026-10-27.xlsx", vDone.file.fileName.toString())
		val vRead = Files.newInputStream(vDone.file).use { readBackupXlsx(it) } as BackupRead.Valid	// relue
		assertEquals(kFile.accounts.size, vRead.file.accounts.size)
		assertEquals(kFile.transactions.size, vRead.file.transactions.size)
		val vNames = Files.list(vFolder).use { inList -> inList.map { it.fileName.toString() }.sorted().toList() }	// fichiers restants
		assertEquals(kPcBackupKept, vNames.count { it.startsWith("patrimoineplus-pc-") })
		assertTrue("notes.txt" in vNames)
		assertFalse(vNames.any { it.endsWith(".tmp") })
		assertFalse("patrimoineplus-pc-2026-10-20.xlsx" in vNames)
		assertTrue(Files.exists(vSynced.resolve("patrimoineplus-2026-10-20.xlsx")))
		val vStamp = Files.getLastModifiedTime(vDone.file)											// date d'écriture
		Thread.sleep(20)
		vBackup.backup(vSynced, kFile)
		assertEquals(vStamp, Files.getLastModifiedTime(vDone.file))								// déjà faite aujourd'hui
		Thread.sleep(20)
		vBackup.backup(vSynced, kFile, inForce = true)
		assertTrue(Files.getLastModifiedTime(vDone.file) > vStamp)									// réécrite à la demande
		assertEquals(kToday, vBackup.last(vSynced))
		assertEquals(PcBackupResult.Empty, vBackup.backup(vSynced, kFile.copy(accounts = emptyList())))
		assertEquals(listOf("patrimoineplus-pc-2026-10-01.xlsx"), pcBackupsToDelete(listOf("patrimoineplus-2026-10-01.xlsx", "patrimoineplus-pc-2026-10-01.xlsx", "patrimoineplus-pc-2026-10-02.xlsx"), 1))
	}

	/*
		Contrôleur : sauvegarde au lancement sur un dossier, état pour les
		réglages, « Sauvegarder maintenant » ; rien en démonstration.
	*/
	@Test
	fun controller()
	{
		val vSynced = fTemp.newFolder("synchro").toPath()											// dossier synchronisé
		Files.newOutputStream(vSynced.resolve(kSyncFileName)).use { writeBackupXlsx(kFile, it) }
		val vConfig = DesktopConfig(fTemp.root.toPath().resolve("desktop.properties"))				// réglages
		vConfig.save(DesktopSettings(Source.Folder(vSynced)))
		val vApp    = DesktopController(vConfig, Workspace(kClock), null, kClock)					// lancement sur le dossier
		assertTrue(Files.exists(vSynced.resolve(kPcBackupFolder).resolve("patrimoineplus-pc-2026-10-27.xlsx")))
		assertTrue(vApp.backupInfo.value!!.contains("la dernière aujourd’hui"))
		vApp.backupIfDue(inForce = true)
		assertEquals("Sauvegarde enregistrée dans « Sauvegardes PC ».", vApp.notice.value)
		vApp.openDemo()
		assertNull(vApp.backupInfo.value)
	}
}
