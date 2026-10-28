package fr.maubin.patrimoineplus.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.maubin.patrimoineplus.data.backup.AccountDto
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.BackupRepository
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.demo.DemoRepository
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.sync.SyncFolder
import fr.maubin.patrimoineplus.data.sync.SyncHooks
import fr.maubin.patrimoineplus.data.sync.SyncStatus
import fr.maubin.patrimoineplus.data.sync.SyncStore
import fr.maubin.patrimoineplus.data.sync.Syncer
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import fr.maubin.patrimoineplus.domain.model.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import fr.maubin.patrimoineplus.data.backup.xlsx.Sheet
import fr.maubin.patrimoineplus.data.backup.xlsx.Column
import fr.maubin.patrimoineplus.data.backup.xlsx.Cell
import fr.maubin.patrimoineplus.data.backup.xlsx.writeXlsx
import fr.maubin.patrimoineplus.data.sync.MergeSide
import fr.maubin.patrimoineplus.data.backup.PriceDto
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import kotlinx.coroutines.flow.first
import fr.maubin.patrimoineplus.data.backup.kBackupVersion
import fr.maubin.patrimoineplus.data.sync.HistoryAuthor

/*
	Dossier synchronisé en mémoire.
*/
class MemorySyncFolder : SyncFolder
{
	var bytes  : ByteArray? = null													// fichier d'échange ; null s'il n'existe pas
	var writes = 0																	// écritures effectuées

	/*
		Contenu du fichier.
	*/
	override suspend fun read(): ByteArray? = bytes

	/*
		Écrit le fichier.
		inBytes : contenu
	*/
	override suspend fun write(inBytes: ByteArray)
	{
		bytes = inBytes
		writes++
	}
}

/*
	Mémoire de synchronisation en mémoire.
*/
class MemorySyncStore(inFolder: String?) : SyncStore
{
	private var fFingerprint : String? = null										// empreinte mémorisée
	private var fBase        : BackupFile? = null									// contenu commun mémorisé
	override val folder     = MutableStateFlow(inFolder)
	override val lastSyncAt = MutableStateFlow<Long?>(null)

	override suspend fun fingerprint(): String? = fFingerprint

	override suspend fun base(): BackupFile? = fBase

	override suspend fun deviceId(): String = "tel-test"

	/*
		Choisit le dossier et oublie l'empreinte.
		inUri : dossier
	*/
	override suspend fun setFolder(inUri: String?)
	{
		folder.value     = inUri
		fFingerprint     = null
		fBase            = null
		lastSyncAt.value = null
	}

	/*
		Mémorise une synchronisation.
		inFingerprint : empreinte
		inAt          : instant
		inBase        : contenu commun
	*/
	override suspend fun recordSync(inFingerprint: String, inAt: Long, inBase: BackupFile)
	{
		fFingerprint     = inFingerprint
		fBase            = inBase
		lastSyncAt.value = inAt
	}
}

/*
	Tests du service de synchronisation par fichier, sur une base en mémoire et
	un dossier simulé : export, import, conflit et son arbitrage, refus d'un
	fichier corrompu.
*/
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SyncerTest
{
	private val kClock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private lateinit var fDb      : PatrimoineDatabase							// base en mémoire
	private lateinit var fBackup  : BackupRepository							// instantané et remplacement
	private lateinit var fFolder  : MemorySyncFolder							// dossier synchronisé
	private lateinit var fStore   : MemorySyncStore								// mémoire de synchronisation
	private lateinit var fSyncer  : Syncer										// service testé
	private var fImports = 0														// imports signalés (avant et après)
	private var fBackupOk = true													// issue de la sauvegarde de sécurité

	/*
		Base chargée avec la démonstration, dossier vide, synchronisation active.
	*/
	@Before
	fun open() = runTest {
		fDb     = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PatrimoineDatabase::class.java).allowMainThreadQueries().build()
		fBackup = BackupRepository(fDb, kClock)
		DemoRepository(fDb, kClock).loadDemo()
		fFolder = MemorySyncFolder()
		fStore  = MemorySyncStore("dossier")
		fSyncer = syncer(emptyFlow())
	}

	/*
		Ferme la base.
	*/
	@After
	fun close()
	{
		fDb.close()
	}

	/*
		Service relié au dossier et à la mémoire simulés.
		inChanges : signal de modification (inutilisé : synchronisation appelée à la main)
	*/
	private fun syncer(inChanges: Flow<Any>): Syncer =
		Syncer(fBackup, fStore, { fFolder }, object : SyncHooks
		{
			override suspend fun beforeImport(): Boolean { fImports++; return fBackupOk }
			override suspend fun afterImport() { fImports++ }
		}, inChanges, kClock)

	/*
		Contenu du fichier d'échange, relu et vérifié.
	*/
	private fun fileContent(): BackupFile = (readBackupXlsx(ByteArrayInputStream(fFolder.bytes!!)) as BackupRead.Valid).file

	/*
		Écrit un contenu dans le fichier d'échange, comme le ferait le PC.
		inFile : contenu
	*/
	private fun writeFile(inFile: BackupFile)
	{
		fFolder.bytes = ByteArrayOutputStream().also { writeBackupXlsx(inFile, it) }.toByteArray()
	}

	/*
		Contenu modifié « ailleurs » : un compte ajouté.
		inFile : contenu de départ
	*/
	private fun withExtraAccount(inFile: BackupFile): BackupFile =
		inFile.copy(accounts = inFile.accounts + AccountDto("pc", "Ouvert sur le PC", "SAVINGS", null, 100, 99, false, 1, 2))

	/*
		Première synchronisation : le fichier n'existe pas, il est créé avec
		les données du téléphone ; une seconde synchronisation ne fait rien.
	*/
	@Test
	fun firstSyncExports() = runTest {
		assertTrue(fSyncer.sync() is SyncStatus.Idle)
		assertEquals(syncFingerprint(fBackup.snapshot()), syncFingerprint(fileContent()))
		fSyncer.sync()
		assertEquals(1, fFolder.writes)
		assertEquals(0, fImports)
	}

	/*
		Modification sur le téléphone : le fichier est réécrit.
	*/
	@Test
	fun localChangeIsExported() = runTest {
		fSyncer.sync()
		AccountRepository(fDb, kClock).create("Nouveau livret", AccountType.SAVINGS, null, 100)
		fSyncer.sync()
		assertEquals(2, fFolder.writes)
		assertTrue(fileContent().accounts.any { it.name == "Nouveau livret" })
	}

	/*
		Modification dans le fichier (PC) : les données du téléphone sont
		remplacées, après la sauvegarde de sécurité ; le fichier n'est pas réécrit.
	*/
	@Test
	fun fileChangeIsImported() = runTest {
		fSyncer.sync()
		writeFile(withExtraAccount(fileContent()))
		assertTrue(fSyncer.sync() is SyncStatus.Idle)
		assertTrue(fBackup.snapshot().accounts.any { it.name == "Ouvert sur le PC" })
		assertEquals(2, fImports)
		assertEquals(1, fFolder.writes)
		assertEquals(syncFingerprint(fBackup.snapshot()), fStore.fingerprint())
	}

	/*
		Sauvegarde de sécurité impossible avant un import : les données du
		téléphone ne sont pas remplacées (avant : remplacées sans copie).
	*/
	@Test
	fun failedSafetyBackupBlocksImport() = runTest {
		fSyncer.sync()
		val vBefore = syncFingerprint(fBackup.snapshot())						// téléphone avant
		writeFile(withExtraAccount(fileContent()))
		fBackupOk = false
		assertTrue(fSyncer.sync() is SyncStatus.Failed)
		assertEquals(vBefore, syncFingerprint(fBackup.snapshot()))
		fBackupOk = true
		assertTrue(fSyncer.sync() is SyncStatus.Idle)
		assertTrue(fBackup.snapshot().accounts.any { it.name == "Ouvert sur le PC" })
	}

	/*
		Compte ajouté sur le PC et compte ajouté sur le téléphone : fusionnés sans
		question, des deux côtés (téléphone sauvegardé avant, fichier réécrit).
	*/
	@Test
	fun independentChangesAreMerged() = runTest {
		fSyncer.sync()
		writeFile(withExtraAccount(fileContent()))
		AccountRepository(fDb, kClock).create("Créé sur le téléphone", AccountType.CHECKING, null, 100)
		assertTrue(fSyncer.sync() is SyncStatus.Idle)
		assertTrue(fBackup.snapshot().accounts.map { it.name }.containsAll(listOf("Ouvert sur le PC", "Créé sur le téléphone")))
		assertTrue(fileContent().accounts.map { it.name }.containsAll(listOf("Ouvert sur le PC", "Créé sur le téléphone")))
		assertEquals(2, fImports)														// sauvegarde de sécurité avant, puis après
		assertEquals(syncFingerprint(fBackup.snapshot()), fStore.fingerprint())
	}

	/*
		Historique : chaque envoi du téléphone y laisse une ligne à son nom,
		gardée sur le téléphone (le prochain envoi la conserve).
	*/
	@Test
	fun historyIsRecorded() = runTest {
		val vSyncer = Syncer(fBackup, fStore, { fFolder }, object : SyncHooks
		{
			override suspend fun beforeImport(): Boolean = true
			override suspend fun afterImport() {}
		}, emptyFlow(), kClock) { HistoryAuthor("tel-1", "Pixel") }						// service avec un auteur
		vSyncer.sync()
		AccountRepository(fDb, kClock).create("Créé sur le téléphone", AccountType.CHECKING, null, 100)
		vSyncer.sync()
		assertEquals(listOf("première synchronisation"), fileContent().history.drop(1).map { it.summary })
		assertEquals("1 ajout", fileContent().history.first().summary)
		assertEquals(listOf("Pixel", "Pixel"), fileContent().history.map { it.device })
		assertEquals(fileContent().history, fBackup.snapshot().history)
	}

	/*
		Même compte renommé des deux côtés : choix par élément, rien n'est
		modifié avant ; « le fichier » adopte le nom du PC des deux côtés.
	*/
	@Test
	fun sameElementIsAsked() = runTest {
		fSyncer.sync()
		val vAccount = fBackup.snapshot().accounts.first()								// compte renommé des deux côtés
		writeFile(fileContent().let { it.copy(accounts = it.accounts.map { inAccount -> if (inAccount.id == vAccount.id) inAccount.copy(name = "Nom du PC", updatedAt = inAccount.updatedAt + 1) else inAccount }) })
		AccountRepository(fDb, kClock).let { inRepo -> inRepo.observeAccounts().first().single { it.id == vAccount.id }.let { inRepo.update(it.copy(name = "Nom du téléphone")) } }
		val vBefore  = syncFingerprint(fBackup.snapshot())								// téléphone avant le choix
		val vItems   = fSyncer.sync() as SyncStatus.Items									// choix demandé
		assertEquals(listOf("comptes/${vAccount.id}"), vItems.conflicts.map { it.key })
		assertEquals(vBefore, syncFingerprint(fBackup.snapshot()))
		assertEquals(1, fFolder.writes)
		assertTrue(fSyncer.resolveItems(mapOf("comptes/${vAccount.id}" to MergeSide.THEIRS)) is SyncStatus.Idle)
		assertEquals("Nom du PC", fBackup.snapshot().accounts.single { it.id == vAccount.id }.name)
		assertEquals("Nom du PC", fileContent().accounts.single { it.id == vAccount.id }.name)
	}

	/*
		Changements incompatibles (un cours du même actif, le même jour, des
		deux côtés) : fusion impossible, choix d'une version entière ; garder le
		téléphone réécrit le fichier, garder le fichier remplace le téléphone.
	*/
	@Test
	fun incompatibleChangesAskWholeVersion() = runTest {
		listOf(true, false).forEach { inKeepPhone ->
			fSyncer.sync()
			val vAsset = fBackup.snapshot().assets.first().id							// actif coté des deux côtés
			val vDay   = "2026-10-0${if (inKeepPhone) 7 else 8}"						// jour du cours
			writeFile(fileContent().let { it.copy(prices = it.prices.filter { inPrice -> inPrice.date != vDay } + PriceDto("cours-pc-$vDay", vAsset, vDay, 1_000_000)) })
			MarketRepository(fDb, kClock).putPriceSeries(vAsset, listOf(java.time.LocalDate.parse(vDay) to 2_000_000L))
			assertTrue(fSyncer.sync() is SyncStatus.Conflict)
			fSyncer.resolve(inKeepLocal = inKeepPhone)
			assertEquals(if (inKeepPhone) 2_000_000L else 1_000_000L, fileContent().prices.single { it.assetId == vAsset && it.date == vDay }.unitPrice)
			assertEquals(if (inKeepPhone) 2_000_000L else 1_000_000L, fBackup.snapshot().prices.single { it.assetId == vAsset && it.date == vDay }.unitPrice)
		}
	}

	/*
		Téléphone vide (nouvelle installation) face à un fichier existant : le
		fichier est importé sans conflit.
	*/
	@Test
	fun emptyPhoneImportsExistingFile() = runTest {
		fSyncer.sync()
		val vShared = fileContent()												// contenu partagé
		DemoRepository(fDb, kClock).clearAll()
		val vFresh = MemorySyncStore("dossier")									// nouvelle installation : aucune empreinte
		fStore = vFresh
		fSyncer = syncer(emptyFlow())
		assertTrue(fSyncer.sync() is SyncStatus.Idle)
		assertEquals(syncFingerprint(vShared), syncFingerprint(fBackup.snapshot()))
	}

	/*
		Fichier corrompu (copie interrompue) : refusé, rien n'est modifié ni réécrit.
	*/
	@Test
	fun corruptFileIsRefused() = runTest {
		fSyncer.sync()
		val vBefore = syncFingerprint(fBackup.snapshot())						// téléphone avant
		fFolder.bytes = fFolder.bytes!!.copyOf(200)
		assertTrue(fSyncer.sync() is SyncStatus.Failed)
		assertEquals(vBefore, syncFingerprint(fBackup.snapshot()))
		assertEquals(1, fFolder.writes)
		assertNull((fSyncer.sync() as SyncStatus.Failed).newerFormat)				// fichier abîmé : pas une question de version
	}

	/*
		Fichier écrit par une version plus récente (PC mis à jour, pas le
		téléphone) : refusé avec son format (fenêtre « Mise à jour
		nécessaire »), rien n'est modifié ni réécrit.
	*/
	@Test
	fun newerFileAsksForUpdate() = runTest {
		fSyncer.sync()
		val vBefore = syncFingerprint(fBackup.snapshot())						// téléphone avant
		val vSign   = Sheet("Fichier", listOf(Column("Clé", 16.0), Column("Valeur", 40.0)), listOf(
			listOf(Cell.Text("Format"), Cell.Text("patrimoineplus")),
			listOf(Cell.Text("Version"), Cell.Number(java.math.BigDecimal(kBackupVersion + 1)))
		))																		// page de signature d'un format futur
		fFolder.bytes = ByteArrayOutputStream().also { writeXlsx(listOf(vSign), it) }.toByteArray()
		val vStatus = fSyncer.sync() as SyncStatus.Failed						// refus
		assertEquals(kBackupVersion + 1, vStatus.newerFormat)
		assertEquals(vBefore, syncFingerprint(fBackup.snapshot()))
		assertEquals(1, fFolder.writes)
	}

	/*
		Sans dossier choisi : synchronisation arrêtée, aucun accès.
	*/
	@Test
	fun noFolderMeansOff() = runTest {
		fStore.setFolder(null)
		assertEquals(SyncStatus.Off, fSyncer.sync())
		assertNull(fFolder.bytes)
		assertNotNull(fBackup.snapshot())
	}
}
