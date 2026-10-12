package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.data.backup.AccountDto
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.demoBackupFile
import fr.maubin.patrimoineplus.data.edit.DataEditor
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.domain.model.AccountType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
	Tests de la synchronisation côté PC : modifications du PC écrites dans le
	fichier, modifications du fichier relues, conflit et arbitrage, fichier
	corrompu jamais écrasé, dossier vide, démonstration jamais écrite.
*/
class WorkspaceSyncTest
{
	@get:Rule
	val fTemp = TemporaryFolder()													// dossier synchronisé simulé

	private val kClock  = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private val kEditor = DataEditor(kClock)										// moteur de modification

	/*
		Dossier synchronisé simulé.
	*/
	private fun folder(): Path = fTemp.root.toPath()

	/*
		Fichier d'échange du dossier.
	*/
	private fun exchange(): Path = folder().resolve(kSyncFileName)

	/*
		Écrit le fichier d'échange comme le ferait le téléphone, avec une date.
		inFile : contenu
		inTime : date de modification (ms)
	*/
	private fun writeExchange(
		inFile : BackupFile,
		inTime : Long
	)
	{
		Files.newOutputStream(exchange()).use { writeBackupXlsx(inFile, it) }
		Files.setLastModifiedTime(exchange(), FileTime.fromMillis(inTime))
	}

	/*
		Contenu du fichier d'échange, relu et vérifié.
	*/
	private fun onDisk(): BackupFile = (Files.newInputStream(exchange()).use { readBackupXlsx(it) } as BackupRead.Valid).file

	/*
		Espace de travail ouvert sur le dossier (sans surveillance : synchronisation à la main).
	*/
	private fun openFolder(): Workspace = Workspace(kClock).apply { open(Source.Folder(folder())) }

	/*
		Ajoute un compte d'épargne.
		inSpace : espace de travail
		inName  : nom du compte
	*/
	private fun addAccount(
		inSpace : Workspace,
		inName  : String
	)
	{
		inSpace.edit { kEditor.createAccount(it, inName, AccountType.SAVINGS, null, 100, null) }
	}

	/*
		Modification faite sur le PC : visible tout de suite, en attente, puis
		écrite dans le fichier (sans fichier temporaire qui traîne) ; la
		synchronisation suivante ne fait rien de plus.
	*/
	@Test
	fun pcEditIsWritten()
	{
		writeExchange(demoBackupFile(), 1_000)
		val vSpace = openFolder()													// espace de travail
		addAccount(vSpace, "Ouvert sur le PC")
		assertEquals(LoadStatus.Pending, vSpace.state.value.status)
		assertTrue(vSpace.state.value.data.accounts.any { it.name == "Ouvert sur le PC" })
		vSpace.flush()
		assertTrue(vSpace.state.value.status is LoadStatus.Synced)
		assertTrue(onDisk().accounts.any { it.name == "Ouvert sur le PC" })
		assertFalse(Files.exists(folder().resolve(".$kSyncFileName.tmp")))
		val vBytes = Files.readAllBytes(exchange())									// fichier écrit
		vSpace.sync()
		assertArrayEquals(vBytes, Files.readAllBytes(exchange()))
	}

	/*
		Modification du fichier (téléphone) sans modification du PC : relue,
		le fichier n'est pas réécrit.
	*/
	@Test
	fun phoneEditIsRead()
	{
		writeExchange(demoBackupFile(), 1_000)
		val vSpace = openFolder()													// espace de travail
		val vMore  = demoBackupFile().let { it.copy(accounts = it.accounts + AccountDto("tel", "Ouvert sur le téléphone", "SAVINGS", null, 100, 99, false, 1, 2)) }	// contenu modifié
		writeExchange(vMore, 2_000)
		vSpace.sync()
		assertTrue(vSpace.state.value.data.accounts.any { it.name == "Ouvert sur le téléphone" })
		assertEquals(2_000, Files.getLastModifiedTime(exchange()).toMillis())
	}

	/*
		Conflit : modification du PC pas encore écrite, et fichier modifié
		ailleurs ; rien n'est touché avant le choix. Garder le fichier remplace
		les données du PC.
	*/
	@Test
	fun conflictKeepFile()
	{
		writeExchange(demoBackupFile(), 1_000)
		val vSpace = openFolder()													// espace de travail
		addAccount(vSpace, "Ouvert sur le PC")
		writeExchange(demoBackupFile().let { it.copy(accounts = it.accounts + AccountDto("tel", "Ouvert sur le téléphone", "SAVINGS", null, 100, 99, false, 1, 2)) }, 2_000)
		vSpace.sync()
		assertTrue(vSpace.state.value.status is LoadStatus.Conflict)
		assertTrue(onDisk().accounts.none { it.name == "Ouvert sur le PC" })
		vSpace.resolve(inKeepLocal = false)
		assertTrue(vSpace.state.value.data.accounts.any { it.name == "Ouvert sur le téléphone" })
		assertTrue(vSpace.state.value.data.accounts.none { it.name == "Ouvert sur le PC" })
		assertTrue(vSpace.state.value.status is LoadStatus.Synced)
	}

	/*
		Conflit : garder le PC réécrit le fichier avec les données du PC.
	*/
	@Test
	fun conflictKeepPc()
	{
		writeExchange(demoBackupFile(), 1_000)
		val vSpace = openFolder()													// espace de travail
		addAccount(vSpace, "Ouvert sur le PC")
		writeExchange(demoBackupFile().let { it.copy(accounts = it.accounts + AccountDto("tel", "Ouvert sur le téléphone", "SAVINGS", null, 100, 99, false, 1, 2)) }, 2_000)
		vSpace.sync()
		vSpace.resolve(inKeepLocal = true)
		assertTrue(onDisk().accounts.any { it.name == "Ouvert sur le PC" })
		assertTrue(onDisk().accounts.none { it.name == "Ouvert sur le téléphone" })
	}

	/*
		Fichier corrompu (copie interrompue) : jamais écrasé, même avec des
		modifications du PC en attente ; état « refusé ».
	*/
	@Test
	fun corruptFileIsNeverOverwritten()
	{
		writeExchange(demoBackupFile(), 1_000)
		val vSpace  = openFolder()													// espace de travail
		val vBroken = Files.readAllBytes(exchange()).copyOf(300)					// fichier tronqué
		Files.write(exchange(), vBroken)
		Files.setLastModifiedTime(exchange(), FileTime.fromMillis(2_000))
		addAccount(vSpace, "Ouvert sur le PC")
		vSpace.flush()
		assertTrue(vSpace.state.value.status is LoadStatus.Invalid)
		assertArrayEquals(vBroken, Files.readAllBytes(exchange()))
	}

	/*
		Dossier vide : en attente ; une saisie sur le PC crée le fichier.
	*/
	@Test
	fun emptyFolderPcCreatesFile()
	{
		val vSpace = openFolder()													// espace de travail
		assertEquals(LoadStatus.Waiting, vSpace.state.value.status)
		assertTrue(vSpace.state.value.data.categories.isNotEmpty())
		addAccount(vSpace, "Premier compte")
		vSpace.flush()
		assertEquals(listOf("Premier compte"), onDisk().accounts.map { it.name })
	}

	/*
		Démonstration : modifiable, mais rien n'est jamais écrit sur le disque.
	*/
	@Test
	fun demoIsNeverWritten()
	{
		val vSpace = Workspace(kClock).apply { open(Source.Demo) }					// démonstration
		addAccount(vSpace, "Essai")
		vSpace.flush()
		assertTrue(vSpace.state.value.data.accounts.any { it.name == "Essai" })
		assertEquals(LoadStatus.Demo, vSpace.state.value.status)
		assertEquals(0, Files.list(folder()).use { it.count() })
	}
}
