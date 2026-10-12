package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.AccountDto
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.demoBackupFile
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Tests de l'espace de travail de l'appli PC : démonstration, dossier
	synchronisé (fichier absent, valide, corrompu, modifié).
*/
class WorkspaceTest
{
	@get:Rule
	val fTemp = TemporaryFolder()													// dossier synchronisé simulé

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Écrit le fichier d'échange dans un dossier, avec une date de modification.
		inFolder : dossier
		inFile   : contenu
		inTime   : date de modification (ms)
	*/
	private fun writeExchange(
		inFolder : Path,
		inFile   : BackupFile,
		inTime   : Long
	)
	{
		val vPath = inFolder.resolve(kSyncFileName)								// fichier d'échange
		Files.newOutputStream(vPath).use { writeBackupXlsx(inFile, it) }
		Files.setLastModifiedTime(vPath, FileTime.fromMillis(inTime))
	}

	/*
		Démonstration : données présentes sans aucun fichier ; rien de choisi :
		aucune donnée.
	*/
	@Test
	fun demoAndNone()
	{
		val vSpace = Workspace(kClock)											// espace de travail
		vSpace.open(Source.Demo)
		assertEquals(LoadStatus.Demo, vSpace.state.value.status)
		assertTrue(vSpace.state.value.data.accounts.isNotEmpty())
		vSpace.open(Source.None)
		assertEquals(LoadStatus.Idle, vSpace.state.value.status)
		assertTrue(vSpace.state.value.data.accounts.isEmpty())
	}

	/*
		Dossier sans fichier : en attente ; le fichier arrive (Syncthing) : il
		est lu au rechargement suivant.
	*/
	@Test
	fun waitsThenLoadsExchangeFile()
	{
		val vFolder = fTemp.root.toPath()											// dossier synchronisé
		val vSpace  = Workspace(kClock)											// espace de travail
		vSpace.open(Source.Folder(vFolder))
		assertEquals(LoadStatus.Waiting, vSpace.state.value.status)
		writeExchange(vFolder, demoBackupFile(), 1_000)
		vSpace.reload()
		assertTrue(vSpace.state.value.status is LoadStatus.Synced)
		assertEquals(demoBackupFile().accounts.size, vSpace.state.value.data.accounts.size)
		assertEquals(demoBackupFile().transactions.size, vSpace.state.value.data.transactions.size)
	}

	/*
		Fichier modifié (date ou taille) : relu ; inchangé : pas relu ; corrompu
		(copie interrompue) : refusé, les dernières données valides restent.
	*/
	@Test
	fun reloadsOnChangeAndKeepsDataWhenCorrupt()
	{
		val vFolder = fTemp.root.toPath()											// dossier synchronisé
		writeExchange(vFolder, demoBackupFile(), 1_000)
		val vSpace = Workspace(kClock)											// espace de travail
		vSpace.open(Source.Folder(vFolder))
		val vBefore = vSpace.state.value											// premier chargement
		vSpace.reload()
		assertTrue(vSpace.state.value === vBefore)
		val vMore = demoBackupFile().let { it.copy(accounts = it.accounts + AccountDto("pc", "Ajouté ailleurs", "SAVINGS", null, 100, 99, false, 1, 2)) }	// contenu modifié
		writeExchange(vFolder, vMore, 2_000)
		vSpace.reload()
		assertTrue(vSpace.state.value.data.accounts.any { it.name == "Ajouté ailleurs" })
		val vPath = vFolder.resolve(kSyncFileName)									// fichier d'échange
		Files.write(vPath, Files.readAllBytes(vPath).copyOf(300))
		Files.setLastModifiedTime(vPath, FileTime.fromMillis(3_000))
		vSpace.reload()
		assertTrue(vSpace.state.value.status is LoadStatus.Invalid)
		assertTrue(vSpace.state.value.data.accounts.any { it.name == "Ajouté ailleurs" })
	}
}
