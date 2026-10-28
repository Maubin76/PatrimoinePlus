package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.link.Beacon
import fr.maubin.patrimoineplus.data.link.DeviceInfo
import fr.maubin.patrimoineplus.data.link.DiscoveryResponder
import fr.maubin.patrimoineplus.data.link.LinkCall
import fr.maubin.patrimoineplus.data.link.LinkServer
import fr.maubin.patrimoineplus.data.link.NetworkId
import fr.maubin.patrimoineplus.data.link.PairingOffer
import fr.maubin.patrimoineplus.data.link.PeerBase
import fr.maubin.patrimoineplus.data.link.PeerHost
import fr.maubin.patrimoineplus.data.link.PeerReply
import fr.maubin.patrimoineplus.data.link.PeerRequest
import fr.maubin.patrimoineplus.data.link.PeerSyncServer
import fr.maubin.patrimoineplus.data.link.PendingChoice
import fr.maubin.patrimoineplus.data.link.fromBase64
import fr.maubin.patrimoineplus.data.link.isHomeNetwork
import fr.maubin.patrimoineplus.data.link.kActRefused
import fr.maubin.patrimoineplus.data.link.kActSame
import fr.maubin.patrimoineplus.data.link.kActStored
import fr.maubin.patrimoineplus.data.link.kActAdopt
import fr.maubin.patrimoineplus.data.link.kDiscoveryPort
import fr.maubin.patrimoineplus.data.link.kLinkPort
import fr.maubin.patrimoineplus.data.link.kOpPair
import fr.maubin.patrimoineplus.data.link.kPairValidityMs
import fr.maubin.patrimoineplus.data.link.newLinkKey
import fr.maubin.patrimoineplus.data.link.toBase64
import fr.maubin.patrimoineplus.data.sync.HistoryAuthor
import fr.maubin.patrimoineplus.data.sync.MergeSide
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val kPeersJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }	// fichier des appareils
private val kPeerJson  = Json { ignoreUnknownKeys = true; encodeDefaults = true }					// messages du protocole

/*
	Téléphone appairé avec ce PC.
	id          : identifiant du téléphone
	name        : son nom
	key         : clé secrète de la paire (base64)
	pairedAt    : instant de l'appairage (ms)
	lastSync    : dernière synchronisation réussie (ms) ; null avant la première
	lastAddress : dernière adresse d'où il s'est connecté ; null avant
*/
@Serializable
data class PairedPhone(
	val id          : String,
	val name        : String,
	val key         : String,
	val pairedAt    : Long,
	val lastSync    : Long?   = null,
	val lastAddress : String? = null
)

/*
	Contenu du fichier des appareils du PC.
	phones : téléphones appairés
	home   : réseaux familiaux (nom du Wi-Fi, box)
*/
@Serializable
data class PeersFile(
	val phones : List<PairedPhone> = emptyList(),
	val home   : List<NetworkId>   = emptyList()
)

/*
	Appareils du PC, rangés à côté des réglages : téléphones appairés et
	réseaux familiaux (appareils.json), et dernière version commune avec
	chaque téléphone (sync-<téléphone>.json.gz). Écritures dans un fichier
	provisoire puis remplacement.
	inFolder : dossier des réglages
*/
class PeerStore(private val inFolder: Path)
{
	private val fFile = inFolder.resolve("appareils.json")							// téléphones et réseaux

	/*
		Appareils enregistrés ; aucun si le fichier est absent ou illisible.
	*/
	fun load(): PeersFile = runCatching { kPeersJson.decodeFromString(PeersFile.serializer(), Files.readString(fFile)) }.getOrDefault(PeersFile())

	/*
		Enregistre les appareils.
		inPeers : appareils
	*/
	fun save(inPeers: PeersFile) = write(fFile, kPeersJson.encodeToString(PeersFile.serializer(), inPeers).toByteArray(Charsets.UTF_8))

	/*
		Dernière version commune avec un téléphone ; null si elle est absente ou illisible.
		inPhone : téléphone
	*/
	fun base(inPhone: String): PeerBase? =
		runCatching { GZIPInputStream(Files.newInputStream(baseFile(inPhone))).use { String(it.readBytes(), Charsets.UTF_8) } }.getOrNull()
			?.let { (decodeBackup(it) as? BackupRead.Valid)?.file }?.let { PeerBase(syncFingerprint(it), it) }

	/*
		Mémorise la dernière version commune avec un téléphone.
		inPhone : téléphone
		inBase  : version commune
	*/
	fun record(
		inPhone : String,
		inBase  : PeerBase
	)
	{
		val vBytes = java.io.ByteArrayOutputStream().also { inOut -> GZIPOutputStream(inOut).use { it.write(encodeBackup(inBase.base).toByteArray(Charsets.UTF_8)) } }.toByteArray()	// contenu compressé
		write(baseFile(inPhone), vBytes)
	}

	/*
		Oublie la version commune avec un téléphone retiré.
		inPhone : téléphone
	*/
	fun forget(inPhone: String)
	{
		runCatching { Files.deleteIfExists(baseFile(inPhone)) }
	}

	/*
		Fichier de la version commune avec un téléphone (identifiant aux caractères sûrs).
		inPhone : téléphone
	*/
	private fun baseFile(inPhone: String): Path = inFolder.resolve("sync-" + inPhone.replace(Regex("[^A-Za-z0-9-]"), "_") + ".json.gz")

	/*
		Écrit un fichier (provisoire puis remplacement).
		inPath  : fichier
		inBytes : contenu
	*/
	private fun write(
		inPath  : Path,
		inBytes : ByteArray
	)
	{
		Files.createDirectories(inFolder)
		val vTemp = inPath.resolveSibling("${inPath.fileName}.tmp")						// écriture provisoire
		Files.write(vTemp, inBytes)
		Files.move(vTemp, inPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
	}
}

/*
	État de la synchronisation Wi-Fi du PC.
	running  : serveur à l'écoute
	onHome   : PC sur le réseau familial
	networks : réseaux auxquels le PC est relié
	home     : réseaux familiaux
	phones   : téléphones appairés
	offer    : offre d'appairage affichée (QR code) ; null sans
	paired   : téléphone appairé à l'instant (message) ; null sans
	choice   : choix attendu sur le PC ; null sans
	error    : échec du démarrage ; null sinon
*/
data class PeerState(
	val running  : Boolean            = false,
	val onHome   : Boolean            = false,
	val networks : List<NetworkId>    = emptyList(),
	val home     : List<NetworkId>    = emptyList(),
	val phones   : List<PairedPhone>  = emptyList(),
	val offer    : PairingOffer?      = null,
	val paired   : String?            = null,
	val choice   : PendingChoice?     = null,
	val error    : String?            = null
)

/*
	Synchronisation Wi-Fi du PC : serveur des échanges et répondeur de
	découverte, **seulement sur le réseau familial** (ou le temps d'un
	appairage) ; appairage par une offre affichée en QR code, valable cinq
	minutes ; synchronisation des téléphones appairés (le PC décide, voir
	PeerSyncServer). Tout ce qui touche aux données du PC passe par inData,
	sur le fil de l'interface.
	inInfo     : présentation de ce PC
	inStore    : appareils enregistrés
	inData     : données du PC (lecture, remplacement, choix présentés)
	inAuthor   : auteur des changements du PC (historique)
	inClock    : horloge
	inNetworks : lecture des réseaux du PC
	inPort     : port du serveur (0 : port libre, tests)
	inBeacon   : port de découverte (0 : port libre, tests)
	inBind     : adresse d'écoute ; null pour toutes les interfaces
*/
class PeerService(
	private val inInfo     : () -> DeviceInfo,
	private val inStore    : PeerStore,
	private val inData     : PeerData,
	private val inAuthor   : () -> HistoryAuthor?,
	private val inClock    : Clock = Clock.systemDefaultZone(),
	private val inNetworks : () -> List<NetworkId> = ::currentNetworks,
	private val inPort     : Int = kLinkPort,
	private val inBeacon   : Int = kDiscoveryPort,
	private val inBind     : InetAddress? = null
)
{
	private val fState  = MutableStateFlow(PeerState(phones = inStore.load().phones, home = inStore.load().home))	// état
	private var fServer : LinkServer? = null										// serveur démarré
	private var fFinder : DiscoveryResponder? = null								// répondeur démarré
	private val fSync   = PeerSyncServer(Host(), inAuthor, inClock::millis)			// décisions de synchronisation

	// État de la synchronisation Wi-Fi.
	val state: StateFlow<PeerState> = fState

	// Port du serveur ; 0 à l'arrêt.
	val port: Int get() = fServer?.port ?: 0

	// Port du répondeur de découverte ; 0 à l'arrêt.
	val beaconPort: Int get() = fFinder?.port ?: 0

	/*
		Relit le réseau du PC ; démarre ou arrête l'écoute selon qu'il est sur
		le réseau familial (ou qu'un appairage est en cours). Une offre expirée
		est retirée.
	*/
	@Synchronized
	fun refresh()
	{
		val vNetworks = inNetworks()														// réseaux du PC
		val vOffer    = fState.value.offer?.takeIf { it.expires > inClock.millis() }			// offre encore valable
		val vHome     = isHomeNetwork(vNetworks, fState.value.home)							// sur le réseau familial
		fState.update { it.copy(networks = vNetworks, onHome = vHome, offer = vOffer) }
		if (vHome && fState.value.phones.isNotEmpty() || vOffer != null) start() else stop()
	}

	/*
		Affiche une offre d'appairage (QR code) valable cinq minutes ;
		l'écoute démarre le temps de l'appairage.
	*/
	@Synchronized
	fun startPairing()
	{
		val vOffer = PairingOffer(inInfo().id, inInfo().name, localAddresses(), if (inPort == 0) 0 else inPort, newLinkKey(), inClock.millis() + kPairValidityMs)	// offre
		fState.update { it.copy(offer = vOffer, paired = null, networks = inNetworks()) }
		start()
		if (inPort == 0) fState.update { it.copy(offer = vOffer.copy(port = port)) }		// port libre (tests)
	}

	/*
		Retire l'offre d'appairage affichée.
	*/
	@Synchronized
	fun cancelPairing()
	{
		fState.update { it.copy(offer = null) }
		refresh()
	}

	/*
		Retire un téléphone : il n'est plus accepté ; sa version commune est
		oubliée.
		inId : téléphone
	*/
	@Synchronized
	fun removePhone(inId: String)
	{
		val vPeers = inStore.load().let { it.copy(phones = it.phones.filter { inPhone -> inPhone.id != inId }) }	// appareils restants
		inStore.save(vPeers)
		inStore.forget(inId)
		fState.update { it.copy(phones = vPeers.phones) }
		refresh()
	}

	/*
		Ajoute le réseau courant aux réseaux familiaux, ou en retire un.
		inNetwork : réseau
		inAdd     : vrai pour l'ajouter
	*/
	@Synchronized
	fun setHome(
		inNetwork : NetworkId,
		inAdd     : Boolean
	)
	{
		val vPeers = inStore.load().let { it.copy(home = if (inAdd) (it.home + inNetwork).distinct() else it.home - inNetwork) }	// réseaux familiaux
		inStore.save(vPeers)
		fState.update { it.copy(home = vPeers.home) }
		refresh()
	}

	/*
		Tranche le choix présenté.
		inChoices : version gardée pour chaque élément, par clé
		inKeepPc  : version entière : vrai pour garder le PC
	*/
	fun resolve(
		inChoices : Map<String, MergeSide> = emptyMap(),
		inKeepPc  : Boolean = true
	) = fSync.resolve(inChoices, inKeepPc)

	/*
		Arrête l'écoute (fermeture de l'appli).
	*/
	@Synchronized
	fun stop()
	{
		fServer?.stop()
		fFinder?.stop()
		fServer = null
		fFinder = null
		fState.update { it.copy(running = false) }
	}

	/*
		Démarre l'écoute si elle ne tourne pas.
	*/
	private fun start()
	{
		if (fServer != null) return
		val vServer = LinkServer(inInfo, ::keyOf, ::handle, inNow = inClock::millis)			// serveur
		val vFinder = DiscoveryResponder { Beacon(inInfo().id, inInfo().name, vServer.port) }	// répondeur de découverte
		try
		{
			vServer.start(inPort, inBind)
			vFinder.start(inBeacon, inBind)
		}
		catch (inError: Exception)
		{
			vServer.stop()
			vFinder.stop()
			fState.update { it.copy(running = false, error = "Écoute impossible : ${inError.message ?: "port déjà utilisé"}. Une autre copie de PatrimoinePlus (ou le diagnostic) est peut-être ouverte.") }
			return
		}
		fServer = vServer
		fFinder = vFinder
		fState.update { it.copy(running = true, error = null) }
	}

	/*
		Clé d'un appareil : celle d'un téléphone appairé, ou celle de l'offre
		d'appairage en cours (pour un appareil encore inconnu).
		inId : appareil
	*/
	private fun keyOf(inId: String): ByteArray? =
		fState.value.phones.firstOrNull { it.id == inId }?.key?.let(::fromBase64)
			?: fState.value.offer?.takeIf { it.expires > inClock.millis() }?.key

	/*
		Réponse à une demande d'un téléphone (message déjà ouvert et vérifié).
		inCall : demande
	*/
	private fun handle(inCall: LinkCall): String
	{
		val vRequest = runCatching { kPeerJson.decodeFromString(PeerRequest.serializer(), inCall.request) }.getOrNull()	// demande
			?: return reply(PeerReply(kActRefused, message = "demande illisible"))
		val vPhone   = fState.value.phones.firstOrNull { it.id == inCall.peerId }			// téléphone appairé ; null sinon
		if (vRequest.op == kOpPair) return reply(pair(inCall, vRequest))
		if (vPhone == null) return reply(PeerReply(kActRefused, message = "téléphone non appairé : appairez-le depuis la page Appareils du PC"))
		val vReply   = fSync.answer(vPhone.id, vPhone.name, vRequest)						// décision
		if (vReply.action == kActSame || vReply.action == kActStored || vReply.action == kActAdopt) seen(vPhone.id, inCall.address.hostAddress)
		return reply(vReply)
	}

	/*
		Appairage d'un téléphone qui a scanné l'offre (sa clé est celle de
		l'offre) : il est enregistré, les réseaux familiaux réunis, l'offre
		retirée.
		inCall    : demande
		inRequest : demande d'appairage
	*/
	@Synchronized
	private fun pair(
		inCall    : LinkCall,
		inRequest : PeerRequest
	): PeerReply
	{
		val vOffer  = fState.value.offer?.takeIf { it.expires > inClock.millis() } ?: return PeerReply(kActRefused, message = "offre d’appairage expirée : affichez un nouveau QR code sur le PC")	// offre en cours
		val vDevice = inRequest.device ?: return PeerReply(kActRefused, message = "présentation absente")	// téléphone
		val vPeers  = inStore.load()																// appareils enregistrés
		val vPhone  = PairedPhone(vDevice.id, vDevice.name, toBase64(vOffer.key), inClock.millis(), lastAddress = inCall.address.hostAddress)	// nouveau téléphone
		val vNext   = PeersFile(vPeers.phones.filter { it.id != vDevice.id } + vPhone, (vPeers.home + inRequest.home + fState.value.networks).distinct())	// appareils et réseaux familiaux
		inStore.save(vNext)
		inStore.forget(vDevice.id)
		fState.update { it.copy(phones = vNext.phones, home = vNext.home, offer = null, paired = vDevice.name) }
		refresh()
		return PeerReply(kActSame, pc = inInfo(), home = vNext.home)
	}

	/*
		Note une synchronisation réussie d'un téléphone.
		inId      : téléphone
		inAddress : son adresse
	*/
	@Synchronized
	private fun seen(
		inId      : String,
		inAddress : String
	)
	{
		val vPeers = inStore.load().let { it.copy(phones = it.phones.map { inPhone -> if (inPhone.id == inId) inPhone.copy(lastSync = inClock.millis(), lastAddress = inAddress) else inPhone }) }	// appareils
		inStore.save(vPeers)
		fState.update { it.copy(phones = vPeers.phones) }
	}

	/*
		Réponse en texte.
		inReply : réponse
	*/
	private fun reply(inReply: PeerReply): String = kPeerJson.encodeToString(PeerReply.serializer(), inReply)

	/*
		Le PC vu par le serveur de synchronisation : données par inData,
		versions communes par le magasin, choix dans l'état.
	*/
	private inner class Host : PeerHost
	{
		override fun local() = inData.read()
		override fun adopt(inFile: fr.maubin.patrimoineplus.data.backup.BackupFile, inNote: String?) = inData.adopt(inFile, inNote)
		override fun base(inPeerId: String): PeerBase? = inStore.base(inPeerId)
		override fun record(inPeerId: String, inBase: PeerBase) = inStore.record(inPeerId, inBase)
		override fun ask(inChoice: PendingChoice?) { fState.update { it.copy(choice = inChoice) } }
	}
}

/*
	Données du PC vues par la synchronisation Wi-Fi (sur le fil de
	l'interface).
*/
interface PeerData
{
	/*
		Données du PC.
	*/
	fun read(): fr.maubin.patrimoineplus.data.backup.BackupFile

	/*
		Remplace les données du PC.
		inFile : données vérifiées
		inNote : résultat de la fusion, en clair ; null pour un import
	*/
	fun adopt(
		inFile : fr.maubin.patrimoineplus.data.backup.BackupFile,
		inNote : String?
	)
}
