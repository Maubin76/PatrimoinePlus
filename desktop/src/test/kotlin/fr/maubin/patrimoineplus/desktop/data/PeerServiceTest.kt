package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.demoBackupFile
import fr.maubin.patrimoineplus.data.link.DeviceInfo
import fr.maubin.patrimoineplus.data.link.LinkClient
import fr.maubin.patrimoineplus.data.link.LinkException
import fr.maubin.patrimoineplus.data.link.NetworkId
import fr.maubin.patrimoineplus.data.link.PeerReply
import fr.maubin.patrimoineplus.data.link.PeerRequest
import fr.maubin.patrimoineplus.data.link.PhoneStep
import fr.maubin.patrimoineplus.data.link.kOpAck
import fr.maubin.patrimoineplus.data.link.kOpPair
import fr.maubin.patrimoineplus.data.link.kOpSync
import fr.maubin.patrimoineplus.data.link.newLinkKey
import fr.maubin.patrimoineplus.data.link.phoneStep
import fr.maubin.patrimoineplus.data.sync.HistoryAuthor
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Synchronisation Wi-Fi du PC sur la boucle locale (sans réseau réel ni
	fenêtre du pare-feu) : écoute seulement le temps d'un appairage ou sur le
	réseau familial ; appairage avec la clé de l'offre (QR code), réseaux
	familiaux réunis ; synchronisation d'un téléphone appairé ; appareil
	inconnu ou retiré refusé ; arrêt hors du réseau familial ; appareils
	retrouvés au lancement suivant.
*/
class PeerServiceTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// dossier des réglages jetable

	private val kJson   = Json { ignoreUnknownKeys = true; encodeDefaults = true }		// messages
	private val kClock  = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private val kHome   = NetworkId(NetworkId.kKindWifi, "Maison")						// Wi-Fi de la maison
	private val kPc     = DeviceInfo("pc-1", "PC du bureau", "windows", "1.5", 9)		// PC
	private val kPhone  = DeviceInfo("tel-1", "Pixel", "android", "1.5", 9)			// téléphone
	private val kLocal  = InetAddress.getLoopbackAddress()								// boucle locale
	private var fWhere  = listOf(kHome)													// réseau courant du PC
	private var fData   : BackupFile = demoBackupFile()									// données du PC
	private var fService: PeerService? = null											// service démarré

	/*
		Arrête le service.
	*/
	@After
	fun stop()
	{
		fService?.stop()
	}

	/*
		Service du PC sur la boucle locale, données en mémoire.
	*/
	private fun service(): PeerService =
		PeerService(
			inInfo     = { kPc },
			inStore    = PeerStore(fTemp.root.toPath()),
			inData     = object : PeerData
			{
				override fun read(): BackupFile = fData
				override fun adopt(inFile: BackupFile, inNote: String?) { fData = inFile }
			},
			inAuthor   = { HistoryAuthor(kPc.id, kPc.name) },
			inClock    = kClock,
			inNetworks = { fWhere },
			inPort     = 0,
			inBeacon   = 0,
			inBind     = kLocal
		).also { fService = it }

	/*
		Demande du téléphone au PC.
		inService : PC
		inKey     : clé de la paire
		inRequest : demande
	*/
	private fun call(
		inService : PeerService,
		inKey     : ByteArray,
		inRequest : PeerRequest
	): PeerReply =
		kJson.decodeFromString(PeerReply.serializer(), LinkClient(kPhone, kClock::millis).call(kLocal.hostAddress, inService.port, kPc.id, inKey, kJson.encodeToString(PeerRequest.serializer(), inRequest)))

	/*
		Appairage, synchronisation, refus, arrêt hors de la maison.
	*/
	@Test
	fun pairSyncAndRefuse()
	{
		val vService = service()																// PC
		vService.refresh()
		assertFalse(vService.state.value.running)												// aucun téléphone : pas d'écoute
		vService.startPairing()
		val vOffer   = vService.state.value.offer!!												// offre du QR code
		assertTrue(vService.state.value.running)
		val vPair    = call(vService, vOffer.key, PeerRequest(kOpPair, device = kPhone, home = listOf(kHome)))	// appairage
		assertEquals(kPc, vPair.pc)
		assertEquals(listOf(kHome), vPair.home)
		assertEquals(listOf("Pixel"), vService.state.value.phones.map { it.name })
		assertNull(vService.state.value.offer)
		assertEquals("Pixel", vService.state.value.paired)
		assertEquals(listOf("pc-1|tel-1"), fData.devices.map { it.key })					// couple inscrit dans les données (fichier d'échange)
		assertTrue(vService.state.value.running)												// sur le réseau familial, téléphone appairé
		val vPhoneData = fData.copy(accounts = fData.accounts + fData.accounts.first().copy(id = "tel-compte", name = "Créé sur le téléphone"))	// données du téléphone
		assertEquals(PhoneStep.Done, phoneStep(call(vService, vOffer.key, PeerRequest(kOpSync, file = encodeBackup(fData)))))	// première fois : mêmes données
		assertEquals(PhoneStep.Done, phoneStep(call(vService, vOffer.key, PeerRequest(kOpSync, file = encodeBackup(vPhoneData)))))	// le PC adopte
		assertTrue(fData.accounts.any { it.name == "Créé sur le téléphone" })
		assertTrue(vService.state.value.phones.single().lastSync != null)
		try
		{
			LinkClient(kPhone, kClock::millis).call(kLocal.hostAddress, vService.port, kPc.id, newLinkKey(), "{}")
			fail("clé inconnue acceptée")
		}
		catch (inError: LinkException) { assertTrue(inError.message!!.contains("clé")) }
		fWhere = listOf(NetworkId(NetworkId.kKindWifi, "Hôtel"))
		vService.refresh()
		assertFalse(vService.state.value.running)												// hors de la maison : rien n'écoute
		assertEquals(listOf("Pixel"), service().state.value.phones.map { it.name })			// retrouvé au lancement suivant
	}

	/*
		Téléphone retiré : plus accepté ; offre expirée : appairage refusé.
	*/
	@Test
	fun removedPhoneIsRefused()
	{
		val vService = service()																// PC
		vService.startPairing()
		val vKey     = vService.state.value.offer!!.key											// clé de la paire
		call(vService, vKey, PeerRequest(kOpPair, device = kPhone, home = listOf(kHome)))
		vService.removePhone(kPhone.id)
		assertTrue(vService.state.value.phones.isEmpty())
		assertTrue(fData.devices.single().removed)													// couple marqué retiré
		assertFalse(vService.state.value.running)
		vService.startPairing()
		val vAfter   = call(vService, vService.state.value.offer!!.key, PeerRequest(kOpSync, file = encodeBackup(fData)))	// téléphone retiré, même avec la clé de l'offre
		assertTrue(vAfter.message!!.contains("non appairé"))
		assertEquals(PhoneStep.Refused(vAfter.message!!, null), phoneStep(vAfter))
		assertTrue(phoneStep(call(vService, vService.state.value.offer!!.key, PeerRequest(kOpAck, fingerprint = "x"))) is PhoneStep.Refused)
	}
}
