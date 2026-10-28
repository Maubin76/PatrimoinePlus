package fr.maubin.patrimoineplus.desktop

import fr.maubin.patrimoineplus.data.backup.AccountDto
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.demoBackupFile
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.LoadStatus
import fr.maubin.patrimoineplus.desktop.data.Source
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.entry.editEntryForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.data.backup.PriceDto

/*
	Horloge réglable (passage de minuit pendant que l'appli est ouverte).
	inNow : instant de départ
*/
private class MovableClock(var inNow: Instant) : Clock()
{
	override fun getZone(): ZoneId = ZoneOffset.UTC
	override fun withZone(inZone: ZoneId?): Clock = this
	override fun instant(): Instant = inNow
}

/*
	Tests des corrections de la revue du 2026-10-12 sur l'appli PC :
	conflit relu avant d'écraser, modifications non écrites retenues à la
	fermeture, panneau d'un élément supprimé ailleurs fermé, saisie neuve à
	chaque ouverture, date du jour qui suit minuit.
*/
class DesktopRobustnessTest
{
	@get:Rule
	val fTemp = TemporaryFolder()													// dossiers jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private val kDemo   = demoBackupFile()										// démonstration (identifiants des opérations tirés une seule fois)

	/*
		Dossier synchronisé simulé.
	*/
	private fun folder(): Path = fTemp.root.toPath().resolve("sync").also { Files.createDirectories(it) }

	/*
		Écrit le fichier d'échange comme le ferait le téléphone.
		inFile : contenu
		inTime : date de modification (ms)
	*/
	private fun writeExchange(
		inFile : BackupFile,
		inTime : Long
	)
	{
		val vPath = folder().resolve(kSyncFileName)									// fichier d'échange
		Files.newOutputStream(vPath).use { writeBackupXlsx(inFile, it) }
		Files.setLastModifiedTime(vPath, FileTime.fromMillis(inTime))
	}

	/*
		Contenu du fichier d'échange.
	*/
	private fun onDisk(): BackupFile = (Files.newInputStream(folder().resolve(kSyncFileName)).use { readBackupXlsx(it) } as BackupRead.Valid).file

	/*
		Démonstration avec un compte de plus et un cours du jour, écrite « par
		le téléphone ».
		inName : nom du compte
	*/
	private fun phoneVersion(inName: String): BackupFile =
		kDemo.let { it.copy(accounts = it.accounts + AccountDto("tel-$inName", inName, "SAVINGS", null, 100, 99, false, 1, 2), prices = it.prices + PriceDto("cours-$inName", it.assets.first().id, "2026-10-12", 2_000_000)) }

	/*
		Contrôleur sur le dossier simulé, sans surveillance.
	*/
	private fun controller(): DesktopController =
		DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openFolder(folder()) }

	/*
		Met le contrôleur en conflit : modifications incompatibles du PC et du
		téléphone (un cours du même actif, le même jour) : fusion impossible.
		inApp : contrôleur
	*/
	private fun makeConflict(inApp: DesktopController)
	{
		inApp.edit { inApp.editor.createAccount(it, "Ouvert sur le PC", AccountType.SAVINGS, null, 100, null) }
		inApp.edit { EditResult.Done(it.copy(prices = it.prices + PriceDto("cours-pc", it.assets.first().id, "2026-10-12", 1_000_000))) }
		writeExchange(phoneVersion("Téléphone 1"), 2_000)
		inApp.flush()
		assertTrue(inApp.workspace.value.status is LoadStatus.Conflict)
	}

	/*
		« Ce PC » alors que le téléphone a encore changé le fichier pendant
		le choix : le conflit est présenté à nouveau, rien n'est écrasé ; le
		second choix est appliqué.
	*/
	@Test
	fun conflictIsRereadBeforeOverwriting()
	{
		writeExchange(kDemo, 1_000)
		val vApp = controller()														// contrôleur
		makeConflict(vApp)
		writeExchange(phoneVersion("Téléphone 2"), 3_000)
		vApp.resolveConflict(inKeepLocal = true)
		val vStatus = vApp.workspace.value.status									// état après le premier choix
		assertTrue(vStatus is LoadStatus.Conflict)
		assertTrue(onDisk().accounts.any { it.name == "Téléphone 2" })
		vApp.resolveConflict(inKeepLocal = true)
		assertTrue(onDisk().accounts.any { it.name == "Ouvert sur le PC" })
		assertTrue(vApp.workspace.value.status is LoadStatus.Synced)
	}

	/*
		Fermeture ou changement de source avec des modifications non écrites
		(conflit en attente) : l'action est retenue, l'utilisateur peut
		enregistrer une copie, puis continuer ou annuler.
	*/
	@Test
	fun unsavedChangesHoldExit()
	{
		writeExchange(kDemo, 1_000)
		val vApp  = controller()														// contrôleur
		makeConflict(vApp)
		var vExit = false																// appli fermée
		vApp.requestExit { vExit = true }
		assertFalse(vExit)
		assertNotNull(vApp.pending.value)
		val vCopy = fTemp.root.toPath().resolve("copie.xlsx")						// copie de secours
		assertNull(vApp.saveCopy(vCopy))
		assertTrue((Files.newInputStream(vCopy).use { readBackupXlsx(it) } as BackupRead.Valid).file.accounts.any { it.name == "Ouvert sur le PC" })
		vApp.cancelPending()
		assertNull(vApp.pending.value)
		assertFalse(vExit)
		vApp.openDemo()
		assertTrue(vApp.workspace.value.source is Source.Folder)
		vApp.confirmPending()
		assertTrue(vApp.workspace.value.source is Source.Demo)
	}

	/*
		Sans modification en attente, la fermeture est immédiate.
	*/
	@Test
	fun cleanExitIsImmediate()
	{
		writeExchange(kDemo, 1_000)
		val vApp  = controller()														// contrôleur
		var vExit = false																// appli fermée
		vApp.edit { vApp.editor.createAccount(it, "Écrit tout de suite", AccountType.SAVINGS, null, 100, null) }
		vApp.requestExit { vExit = true }
		assertTrue(vExit)
		assertTrue(onDisk().accounts.any { it.name == "Écrit tout de suite" })
	}

	/*
		Panneau ouvert sur une opération que le téléphone supprime : fermé,
		avec un message (avant : « Enregistrer » la recréait).
	*/
	@Test
	fun stalePanelIsClosed()
	{
		val vApp = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openDemo() }	// démonstration
		val vTx  = vApp.workspace.value.data.transactions.first { it.kind == fr.maubin.patrimoineplus.domain.model.TransactionKind.STANDARD }	// opération ouverte
		vApp.openPanel(Panel.Operation(editEntryForm(vTx, vApp.workspace.value.data.categories)))
		vApp.dropStalePanel()
		assertNotNull(vApp.panel.value)
		vApp.edit { vApp.editor.deleteTransactions(it, listOf(vTx.id)) }
		vApp.dropStalePanel()
		assertNull(vApp.panel.value)
		assertNotNull(vApp.notice.value)
	}

	/*
		Chaque ouverture d'un panneau repart d'une saisie neuve (Ctrl+N deux
		fois : formulaire remis à zéro) ; changer d'écran ferme le panneau.
	*/
	@Test
	fun panelsStartFresh()
	{
		val vApp = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openDemo() }	// démonstration
		vApp.newOperation()
		val vFirst = vApp.panel.value!!.token											// première ouverture
		vApp.newOperation()
		assertNotEquals(vFirst, vApp.panel.value!!.token)
		vApp.show(Screen.SETTINGS)
		assertNull(vApp.panel.value)
	}

	/*
		Appli restée ouverte après minuit : la date de calcul suit.
	*/
	@Test
	fun dayFollowsMidnight()
	{
		val vClock = MovableClock(Instant.parse("2026-10-31T23:59:00Z"))			// horloge réglable
		val vSpace = Workspace(vClock).apply { open(Source.Demo) }					// démonstration
		assertEquals(LocalDate.of(2026, 10, 31), vSpace.state.value.today)
		vClock.inNow = Instant.parse("2026-11-01T00:01:00Z")
		vSpace.refreshDay()
		assertEquals(LocalDate.of(2026, 11, 1), vSpace.state.value.today)
	}
}
