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
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.data.sync.MergeSide
import fr.maubin.patrimoineplus.data.backup.PriceDto
import fr.maubin.patrimoineplus.data.sync.HistoryAuthor

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
	private val kDemo   = demoBackupFile()										// démonstration (identifiants des opérations tirés une seule fois)

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
		writeExchange(kDemo, 1_000)
		val vSpace = openFolder()													// espace de travail
		addAccount(vSpace, "Ouvert sur le PC")
		assertEquals(LoadStatus.Pending, vSpace.state.value.status)
		assertTrue(vSpace.state.value.data.accounts.any { it.name == "Ouvert sur le PC" })
		vSpace.flush()
		assertTrue(vSpace.state.value.status is LoadStatus.Synced)
		assertTrue(onDisk().accounts.any { it.name == "Ouvert sur le PC" })
		assertFalse(Files.exists(folder().resolve(".syncthing.$kSyncFileName.tmp")))
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
		writeExchange(kDemo, 1_000)
		val vSpace = openFolder()													// espace de travail
		val vMore  = kDemo.let { it.copy(accounts = it.accounts + AccountDto("tel", "Ouvert sur le téléphone", "SAVINGS", null, 100, 99, false, 1, 2)) }	// contenu modifié
		writeExchange(vMore, 2_000)
		vSpace.sync()
		assertTrue(vSpace.state.value.data.accounts.any { it.name == "Ouvert sur le téléphone" })
		assertEquals(2_000, Files.getLastModifiedTime(exchange()).toMillis())
	}

	/*
		Compte ajouté sur le PC et compte ajouté sur le téléphone avant la
		synchronisation : fusionnés sans question, des deux côtés (fichier
		réécrit), avec un message.
	*/
	@Test
	fun independentChangesAreMerged()
	{
		writeExchange(kDemo, 1_000)
		val vSpace = openFolder()													// espace de travail
		addAccount(vSpace, "Ouvert sur le PC")
		writeExchange(kDemo.let { it.copy(accounts = it.accounts + AccountDto("tel", "Ouvert sur le téléphone", "SAVINGS", null, 100, 99, false, 1, 2)) }, 2_000)
		vSpace.sync()
		assertTrue(vSpace.state.value.status is LoadStatus.Synced)
		assertTrue(vSpace.state.value.data.accounts.map { it.name }.containsAll(listOf("Ouvert sur le PC", "Ouvert sur le téléphone")))
		assertTrue(onDisk().accounts.map { it.name }.containsAll(listOf("Ouvert sur le PC", "Ouvert sur le téléphone")))
		assertEquals("Fusion avec le fichier synchronisé : 1 changement(s) repris.", vSpace.state.value.merge)
	}

	/*
		Historique : un envoi du PC y laisse une ligne à son nom (changements
		comptés) ; une synchronisation sans changement n'en ajoute pas.
	*/
	@Test
	fun historyIsRecorded()
	{
		writeExchange(kDemo, 1_000)
		val vSpace = openFolder().apply { author = HistoryAuthor("pc-1", "PC du bureau") }	// espace de travail
		addAccount(vSpace, "Ouvert sur le PC")
		vSpace.sync()
		val vEntry = onDisk().history.single()											// ligne de l'envoi
		assertEquals("PC du bureau", vEntry.device)
		assertEquals("1 ajout", vEntry.summary)
		assertEquals(listOf("PC du bureau"), vSpace.state.value.data.history.map { it.device })
		vSpace.sync()
		assertEquals(1, onDisk().history.size)
	}

	/*
		Même compte renommé différemment des deux côtés : choix par élément,
		rien n'est écrit avant ; « le fichier » adopte le nom du téléphone des
		deux côtés.
	*/
	@Test
	fun sameElementIsAsked()
	{
		writeExchange(kDemo, 1_000)
		val vSpace = openFolder()													// espace de travail
		val vId    = kDemo.accounts.first().id							// compte renommé des deux côtés
		vSpace.edit { EditResult.Done(it.copy(accounts = it.accounts.map { inAccount -> if (inAccount.id == vId) inAccount.copy(name = "Nom du PC", updatedAt = 5) else inAccount })) }
		writeExchange(kDemo.let { it.copy(accounts = it.accounts.map { inAccount -> if (inAccount.id == vId) inAccount.copy(name = "Nom du téléphone", updatedAt = 6) else inAccount }) }, 2_000)
		vSpace.sync()
		val vItems = vSpace.state.value.status as LoadStatus.Items					// choix demandé
		assertEquals(listOf("comptes/$vId"), vItems.conflicts.map { it.key })
		assertTrue(onDisk().accounts.none { it.name == "Nom du PC" })
		vSpace.resolveItems(mapOf("comptes/$vId" to MergeSide.THEIRS))
		assertTrue(vSpace.state.value.status is LoadStatus.Synced)
		assertEquals("Nom du téléphone", vSpace.state.value.data.accounts.single { it.id == vId }.name)
		assertEquals("Nom du téléphone", onDisk().accounts.single { it.id == vId }.name)
	}

	/*
		Changements incompatibles (un cours du même actif, le même jour, saisi
		des deux côtés) : fusion impossible, choix d'une version entière ;
		« le fichier » remplace les données du PC, « ce PC » réécrit le fichier.
	*/
	@Test
	fun incompatibleChangesAskWholeVersion()
	{
		val vAsset = kDemo.assets.first().id								// actif coté des deux côtés
		listOf(false, true).forEach { inKeepPc ->
			writeExchange(kDemo, 1_000)
			val vSpace = openFolder()												// espace de travail
			vSpace.edit { EditResult.Done(it.copy(prices = it.prices + PriceDto("cours-pc", vAsset, "2026-10-12", 1_000_000))) }
			writeExchange(kDemo.let { it.copy(prices = it.prices + PriceDto("cours-tel", vAsset, "2026-10-12", 2_000_000)) }, 2_000L + (if (inKeepPc) 1 else 0))
			vSpace.sync()
			assertTrue(vSpace.state.value.status is LoadStatus.Conflict)
			vSpace.resolve(inKeepLocal = inKeepPc)
			assertTrue(vSpace.state.value.status is LoadStatus.Synced)
			assertEquals(if (inKeepPc) "cours-pc" else "cours-tel", onDisk().prices.single { it.assetId == vAsset && it.date == "2026-10-12" }.id)
			assertEquals(if (inKeepPc) "cours-pc" else "cours-tel", vSpace.state.value.data.prices.single { it.assetId == vAsset && it.date.toString() == "2026-10-12" }.id)
		}
	}

	/*
		Fichier corrompu (copie interrompue) : jamais écrasé, même avec des
		modifications du PC en attente ; état « refusé ».
	*/
	@Test
	fun corruptFileIsNeverOverwritten()
	{
		writeExchange(kDemo, 1_000)
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
