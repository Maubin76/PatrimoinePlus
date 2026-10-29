package fr.maubin.patrimoineplus.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRepository
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.demo.DemoRepository
import fr.maubin.patrimoineplus.data.link.DeviceInfo
import fr.maubin.patrimoineplus.data.link.LinkServer
import fr.maubin.patrimoineplus.data.link.NetworkId
import fr.maubin.patrimoineplus.data.link.PairingOffer
import fr.maubin.patrimoineplus.data.link.PeerBase
import fr.maubin.patrimoineplus.data.link.PeerHost
import fr.maubin.patrimoineplus.data.link.PeerReply
import fr.maubin.patrimoineplus.data.link.PeerRequest
import fr.maubin.patrimoineplus.data.link.PeerSyncServer
import fr.maubin.patrimoineplus.data.link.PendingChoice
import fr.maubin.patrimoineplus.data.link.PhoneNetwork
import fr.maubin.patrimoineplus.data.link.WifiStatus
import fr.maubin.patrimoineplus.data.link.WifiSyncer
import fr.maubin.patrimoineplus.data.link.kActSame
import fr.maubin.patrimoineplus.data.link.kOpPair
import fr.maubin.patrimoineplus.data.link.newLinkKey
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.sync.HistoryAuthor
import fr.maubin.patrimoineplus.data.sync.SyncHooks
import fr.maubin.patrimoineplus.domain.model.AccountType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.net.InetAddress
import java.time.Clock

/*
	Synchronisation Wi-Fi du téléphone (Robolectric, base en mémoire, PC
	simulé sur la boucle locale) : appairage avec l'offre du QR code, rien de
	tenté hors du réseau familial ni sur un Wi-Fi facturé, saisie du
	téléphone adoptée par le PC, saisie du PC adoptée par le téléphone, PC
	retiré.
*/
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@ConscryptMode(ConscryptMode.Mode.OFF)																// chiffrement de la JVM (celui d'Android ne s'initialise pas sous Robolectric)
class WifiSyncerTest
{
	private val kJson    = Json { ignoreUnknownKeys = true; encodeDefaults = true }		// messages
	private val kClock   = Clock.systemDefaultZone()									// horloge réelle (même horloge pour le PC simulé)
	private val kHome    = NetworkId(NetworkId.kKindWifi, "Maison")					// Wi-Fi de la maison
	private val kPc      = DeviceInfo("pc-1", "PC du bureau", "windows", "1.5", 9)	// PC simulé
	private lateinit var fDb     : PatrimoineDatabase									// base en mémoire
	private lateinit var fSyncer : WifiSyncer											// service testé
	private var fPcData  : BackupFile? = null											// données du PC simulé
	private val fBases   = mutableMapOf<String, PeerBase>()								// versions communes du PC
	private val fKey     = newLinkKey()													// clé de l'offre
	private var fPaired  = false															// téléphone appairé par le PC
	private lateinit var fServer : LinkServer											// serveur du PC simulé
	private var fNetwork = PhoneNetwork(true, true, kHome, true)							// réseau du téléphone

	/*
		Base chargée avec la démonstration ; PC simulé qui accepte l'offre puis
		synchronise ; service relié au réseau simulé.
	*/
	@Before
	fun open() = runTest {
		fDb      = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PatrimoineDatabase::class.java).allowMainThreadQueries().build()
		val vBackup = BackupRepository(fDb, kClock)											// données du téléphone
		DemoRepository(fDb, kClock).loadDemo()
		fPcData  = vBackup.snapshot()
		val vSync = PeerSyncServer(object : PeerHost
		{
			override fun local(): BackupFile = fPcData!!
			override fun adopt(inFile: BackupFile, inNote: String?) { fPcData = inFile }
			override fun base(inPeerId: String): PeerBase? = fBases[inPeerId]
			override fun record(inPeerId: String, inBase: PeerBase) { fBases[inPeerId] = inBase }
			override fun ask(inChoice: PendingChoice?) {}
		}, { HistoryAuthor(kPc.id, kPc.name) })												// décisions du PC
		fServer  = LinkServer({ kPc }, { fKey }, { inCall ->
			val vRequest = kJson.decodeFromString(PeerRequest.serializer(), inCall.request)	// demande
			if (vRequest.op == kOpPair) { fPaired = true; kJson.encodeToString(PeerReply.serializer(), PeerReply(kActSame, pc = kPc, home = listOf(kHome))) }
			else kJson.encodeToString(PeerReply.serializer(), vSync.answer(inCall.peerId, "Pixel", vRequest))
		}).also { it.start(0, InetAddress.getLoopbackAddress()) }
		fSyncer  = WifiSyncer(ApplicationProvider.getApplicationContext(), vBackup, MemorySyncStore(null), object : SyncHooks
		{
			override suspend fun beforeImport(): Boolean = true
			override suspend fun afterImport() {}
		}, kClock).apply {
			network  = { fNetwork }
			discover = { emptyList() }
		}
	}

	/*
		Ferme la base et le PC simulé.
	*/
	@After
	fun close()
	{
		fServer.stop()
		fDb.close()
		java.io.File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "appareils.json").delete()
	}

	/*
		Offre du PC simulé, comme lue dans le QR code.
	*/
	private fun offer(): PairingOffer = PairingOffer(kPc.id, kPc.name, listOf("127.0.0.1"), fServer.port, fKey, kClock.millis() + 60_000)

	/*
		Appairage, réseau familial, échanges dans les deux sens.
	*/
	@Test
	fun pairThenSync() = runTest {
		assertEquals(WifiStatus.Disabled, fSyncer.sync())										// désactivée par défaut : rien
		fSyncer.setEnabled(true)
		assertEquals(WifiStatus.Unpaired, fSyncer.sync())
		assertNull(fSyncer.pair(offer()))
		assertTrue(fPaired)
		assertEquals(listOf("PC du bureau"), fSyncer.peers.value.pcs.map { it.name })
		assertEquals(listOf(kHome), fSyncer.peers.value.home)
		fNetwork = PhoneNetwork(true, true, NetworkId(NetworkId.kKindWifi, "Hôtel"), true)
		assertTrue(fSyncer.sync() is WifiStatus.Away)											// hors de la maison : rien
		fNetwork = PhoneNetwork(true, false, kHome, true)
		assertTrue(fSyncer.sync() is WifiStatus.Away)											// Wi-Fi facturé : rien
		fNetwork = PhoneNetwork(true, true, kHome, true)
		assertTrue(fSyncer.sync() is WifiStatus.Idle)											// première fois : mêmes données
		AccountRepository(fDb, kClock).create("Créé sur le téléphone", AccountType.CHECKING, null, 100)
		assertEquals(listOf("PC du bureau"), (fSyncer.sync() as WifiStatus.Idle).pcs)
		assertTrue(fPcData!!.accounts.any { it.name == "Créé sur le téléphone" })				// le PC l'a adopté
		fPcData = fPcData!!.copy(accounts = fPcData!!.accounts + fPcData!!.accounts.first().copy(id = "pc-compte", name = "Créé sur le PC", updatedAt = 5))
		assertTrue(fSyncer.sync() is WifiStatus.Idle)
		val vPhone = BackupRepository(fDb, kClock).snapshot()									// données du téléphone
		assertTrue(vPhone.accounts.any { it.name == "Créé sur le PC" })							// le téléphone l'a adopté
		assertTrue(fSyncer.peers.value.pcs.single().lastSync != null)
		assertEquals(listOf("pc-1"), vPhone.devices.map { it.pcId })								// couple inscrit sur le téléphone
		fSyncer.remove(kPc.id)
		assertEquals(WifiStatus.Unpaired, fSyncer.sync())
	}

	/*
		PC injoignable : échec signalé, rien de modifié.
	*/
	@Test
	fun unreachablePc() = runTest {
		fSyncer.setEnabled(true)
		assertNull(fSyncer.pair(offer()))
		fServer.stop()
		assertTrue((fSyncer.sync() as WifiStatus.Failed).message.contains("Aucun PC joignable"))
	}
}
