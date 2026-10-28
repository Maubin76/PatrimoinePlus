package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.data.backup.kBackupVersion
import fr.maubin.patrimoineplus.data.link.Beacon
import fr.maubin.patrimoineplus.data.link.DeviceInfo
import fr.maubin.patrimoineplus.data.link.DiscoveryResponder
import fr.maubin.patrimoineplus.data.link.LinkServer
import fr.maubin.patrimoineplus.data.link.NetworkId
import fr.maubin.patrimoineplus.data.link.kDiscoveryPort
import fr.maubin.patrimoineplus.data.link.kLinkPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalTime

/*
	État du diagnostic réseau.
	running   : écoute en cours
	networks  : réseaux auxquels le PC est relié (nom du Wi-Fi, box)
	addresses : adresses IPv4 du PC sur le réseau local
	port      : port du serveur ; 0 à l'arrêt
	visits    : appareils qui se sont présentés, du plus récent au plus ancien
	error     : échec du démarrage ; null sinon
*/
data class DiagnosticState(
	val running   : Boolean         = false,
	val networks  : List<NetworkId> = emptyList(),
	val addresses : List<String>    = emptyList(),
	val port      : Int             = 0,
	val visits    : List<String>    = emptyList(),
	val error     : String?         = null
)

/*
	Diagnostic réseau du PC (phase d'essai de la synchronisation) : lit les
	réseaux du PC, démarre le serveur et le répondeur de découverte, et note
	chaque appareil qui se présente (diagnostic du téléphone). Aucun échange
	de données : seule la présentation en clair est acceptée.
	inInfo     : présentation de ce PC
	inClock    : horloge (heure du journal)
	inNetworks : lecture des réseaux du PC
	inPort     : port du serveur (0 : port libre, tests)
	inBeacon   : port de découverte (0 : port libre, tests)
	inBind     : adresse d'écoute ; null pour toutes les interfaces (boucle locale en test : pas de fenêtre du pare-feu)
*/
class NetworkDiagnostic(
	private val inInfo     : () -> DeviceInfo,
	private val inClock    : Clock = Clock.systemDefaultZone(),
	private val inNetworks : () -> List<NetworkId> = ::currentNetworks,
	private val inPort     : Int = kLinkPort,
	private val inBeacon   : Int = kDiscoveryPort,
	private val inBind     : java.net.InetAddress? = null
)
{
	private val fState  = MutableStateFlow(DiagnosticState())							// état
	private var fServer : LinkServer? = null										// serveur démarré
	private var fFinder : DiscoveryResponder? = null								// répondeur démarré

	// État du diagnostic.
	val state: StateFlow<DiagnosticState> = fState

	// Port du répondeur de découverte ; 0 à l'arrêt.
	val beaconPort: Int get() = fFinder?.port ?: 0

	/*
		Démarre le diagnostic ; un port déjà pris est signalé.
	*/
	@Synchronized
	fun start()
	{
		if (fServer != null) return
		val vServer = LinkServer(
			inInfo    = inInfo,
			inKeyOf   = { null },
			inHandler = { error("appareil non appairé") },
			inOnHello = { inPeer, inAddress ->
				val vLine = "${LocalTime.now(inClock).withNano(0)} — ${inPeer.name} (${if (inPeer.platform == "android") "Android" else inPeer.platform}, version ${inPeer.appVersion}) depuis ${inAddress.hostAddress}"	// entrée du journal
				fState.update { it.copy(visits = (listOf(vLine) + it.visits).take(20)) }
			}
		)																				// serveur : présentations seulement
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
			fState.value = DiagnosticState(networks = inNetworks(), addresses = localAddresses(), error = "Démarrage impossible : ${inError.message ?: "port déjà utilisé"} (ports $inPort et $inBeacon). Une autre copie de PatrimoinePlus est peut-être ouverte.")
			return
		}
		fServer = vServer
		fFinder = vFinder
		fState.value = DiagnosticState(running = true, networks = inNetworks(), addresses = localAddresses(), port = vServer.port)
	}

	/*
		Arrête le diagnostic (le journal reste affiché).
	*/
	@Synchronized
	fun stop()
	{
		fServer?.stop()
		fFinder?.stop()
		fServer = null
		fFinder = null
		fState.update { it.copy(running = false, port = 0) }
	}

	/*
		Relit les réseaux du PC (sans démarrer l'écoute).
	*/
	fun refresh()
	{
		fState.update { it.copy(networks = inNetworks(), addresses = localAddresses()) }
	}
}

/*
	Présentation de ce PC.
	inSettings : réglages (identifiant et nom de l'appareil)
	inVersion  : version de l'appli
*/
fun pcInfo(
	inSettings : DesktopSettings,
	inVersion  : String?
): DeviceInfo = DeviceInfo(inSettings.deviceId, inSettings.deviceName.ifBlank { defaultPcName() }, "windows", inVersion ?: "?", kBackupVersion)

/*
	Nom proposé pour ce PC : son nom Windows, sinon « Ce PC ».
*/
fun defaultPcName(): String = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() } ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Ce PC"
