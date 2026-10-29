package fr.maubin.patrimoineplus.desktop

import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.demoBackupFile
import fr.maubin.patrimoineplus.data.link.DeviceInfo
import fr.maubin.patrimoineplus.data.link.LinkClient
import fr.maubin.patrimoineplus.data.link.PeerReply
import fr.maubin.patrimoineplus.data.link.PeerRequest
import fr.maubin.patrimoineplus.data.link.PhoneStep
import fr.maubin.patrimoineplus.data.link.kOpAck
import fr.maubin.patrimoineplus.data.link.kOpPair
import fr.maubin.patrimoineplus.data.link.kOpSync
import fr.maubin.patrimoineplus.data.link.phoneStep
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.domain.model.AccountType
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Clock

/*
	Synchronisation Wi-Fi du PC, par le contrôleur (boucle locale, téléphone
	simulé) : diagnostic qui se sert du serveur de la synchronisation ;
	fermeture retenue tant que le téléphone n'a pas les dernières données,
	« Transmis » après son passage, « Fermer quand même » ; options de fond
	(zone de notification, démarrage avec Windows) mémorisées, échec signalé.
*/
class DesktopWifiTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// réglages et dossier jetables

	private val kJson  = Json { ignoreUnknownKeys = true; encodeDefaults = true }		// messages
	private val kClock = Clock.systemDefaultZone()										// horloge réelle (partagée avec le téléphone simulé)
	private val kPhone = DeviceInfo("tel-1", "Pixel", "android", "1.5", 9)			// téléphone simulé
	private var fApp   : DesktopController? = null										// contrôleur ouvert
	private var fStartup = mutableListOf<Boolean>()										// démarrages avec Windows demandés

	/*
		Arrête l'écoute.
	*/
	@After
	fun stop()
	{
		fApp?.peers?.stop()
	}

	/*
		Contrôleur sur un dossier de données, synchronisation Wi-Fi activée,
		écoute sur la boucle locale seulement.
		inStartup : réponse du démarrage avec Windows
	*/
	private fun controller(inStartup: (Boolean) -> String? = { fStartup += it; null }): DesktopController
	{
		val vFolder = fTemp.newFolder("donnees").toPath()										// dossier de données
		Files.newOutputStream(vFolder.resolve(kSyncFileName)).use { writeBackupXlsx(demoBackupFile(), it) }
		return DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock, inLocalOnly = true, inStartup = inStartup)
			.apply { openFolder(vFolder); setWifiSync(true) }.also { fApp = it }
	}

	/*
		Échange du téléphone simulé avec le PC : synchronisation, adoption,
		accusé de réception.
		inApp  : contrôleur
		inKey  : clé de la paire
		inData : données du téléphone (JSON)
	*/
	private fun phoneSync(
		inApp  : DesktopController,
		inKey  : ByteArray,
		inData : String
	): PhoneStep
	{
		val vClient = LinkClient(kPhone, kClock::millis)											// téléphone
		val vCall   = { inRequest: PeerRequest -> kJson.decodeFromString(PeerReply.serializer(), vClient.call("127.0.0.1", inApp.peers.port, inApp.peers.state.value.let { _ -> fr.maubin.patrimoineplus.desktop.data.pcInfo(inApp.settings.value, null).id }, inKey, kJson.encodeToString(PeerRequest.serializer(), inRequest))) }	// demande au PC
		val vStep   = phoneStep(vCall(PeerRequest(kOpSync, file = inData)))								// réponse
		if (vStep is PhoneStep.Adopt) vCall(PeerRequest(kOpAck, fingerprint = vStep.fingerprint))
		return vStep
	}

	/*
		Diagnostic : la synchronisation écoute déjà, il se sert de son serveur.
	*/
	@Test
	fun diagnosticSharesTheServer()
	{
		val vApp = controller()																		// contrôleur
		vApp.peers.startPairing()
		vApp.diagnostic.start()
		val vState = vApp.diagnostic.state.value														// diagnostic
		assertTrue(vState.running)
		assertTrue(vState.shared)
		assertEquals(vApp.peers.port, vState.port)
		LinkClient(kPhone, kClock::millis).hello("127.0.0.1", vApp.peers.port)
		assertEquals(1, vApp.diagnostic.state.value.visits.size)									// présentation notée au journal
	}

	/*
		Fermeture : immédiate sans téléphone en retard ; retenue après une
		modification du PC, puis « Transmis » quand le téléphone est passé ;
		« Fermer quand même ».
	*/
	@Test
	fun exitWaitsForThePhone()
	{
		val vApp   = controller()																	// contrôleur
		vApp.peers.startPairing()
		val vKey   = vApp.peers.state.value.offer!!.key												// clé de la paire
		val vPcId  = fr.maubin.patrimoineplus.desktop.data.pcInfo(vApp.settings.value, null).id	// ce PC
		LinkClient(kPhone, kClock::millis).call("127.0.0.1", vApp.peers.port, vPcId, vKey, kJson.encodeToString(PeerRequest.serializer(), PeerRequest(kOpPair, device = kPhone)))
		var vPhoneData = encodeBackup(vApp.currentFile())											// données du téléphone : celles du PC
		assertEquals(PhoneStep.Done, phoneSync(vApp, vKey, vPhoneData))
		var vExited = 0																				// fermetures
		vApp.requestExit { vExited++ }
		assertEquals(1, vExited)																	// téléphone à jour : fermeture immédiate
		vApp.peers.startPairing()																	// réouverture (la fermeture a arrêté l'écoute)
		vApp.edit { vApp.editor.createAccount(it, "Ouvert sur le PC", AccountType.SAVINGS, null, 100, null) }
		vApp.requestExit { vExited++ }
		assertEquals(1, vExited)
		assertEquals(listOf("Pixel"), vApp.exitWait.value!!.phones)									// retenue
		val vStep  = phoneSync(vApp, vKey, vPhoneData) as PhoneStep.Adopt							// le téléphone passe et adopte
		vPhoneData = encodeBackup(vStep.file)
		vApp.checkExitWait()
		assertTrue(vApp.exitWait.value!!.done)														// « Transmis »
		vApp.closeNow()
		assertEquals(2, vExited)
		assertNull(vApp.exitWait.value)
		assertTrue((decodeBackup(vPhoneData) as BackupRead.Valid).file.accounts.any { it.name == "Ouvert sur le PC" })
		vApp.peers.startPairing()																	// réouverture
		vApp.edit { vApp.editor.createAccount(it, "Encore un", AccountType.SAVINGS, null, 100, null) }
		vApp.requestExit { vExited++ }
		assertNotNull(vApp.exitWait.value)
		vApp.closeNow()																				// « Fermer quand même »
		assertEquals(3, vExited)
	}

	/*
		Options de fond : mémorisées ; démarrage avec Windows impossible :
		message, réglage inchangé.
	*/
	@Test
	fun backgroundOptions()
	{
		val vApp = controller()																		// contrôleur
		vApp.setTray(true)
		vApp.setAutostart(true)
		assertEquals(listOf(true), fStartup)
		val vNext = DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load()		// réglages relus
		assertTrue(vNext.tray && vNext.autostart && vNext.wifiSync)
		vApp.peers.stop()
		val vFail = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("autre.properties")), Workspace(kClock), null, kClock, inStartup = { "chemin inconnu" })	// lanceur absent
		vFail.setAutostart(true)
		assertFalse(vFail.settings.value.autostart)
		assertEquals("Démarrage avec Windows impossible : chemin inconnu.", vFail.notice.value)
	}
}
