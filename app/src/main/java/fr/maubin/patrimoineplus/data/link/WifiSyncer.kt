package fr.maubin.patrimoineplus.data.link

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.maubin.patrimoineplus.data.backup.BackupRepository
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.sync.SyncHooks
import fr.maubin.patrimoineplus.data.sync.SyncStore
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import fr.maubin.patrimoineplus.data.backup.DeviceLinkDto

private val kJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }		// fichier des appareils et messages

/*
	PC appairé avec ce téléphone.
	id          : identifiant du PC
	name        : son nom
	key         : clé secrète de la paire (base64)
	port        : port de son serveur
	addresses   : adresses connues (dernière réussie d'abord)
	lastSync    : dernière synchronisation réussie (ms) ; null avant la première
*/
@Serializable
data class PairedPc(
	val id        : String,
	val name      : String,
	val key       : String,
	val port      : Int,
	val addresses : List<String> = emptyList(),
	val lastSync  : Long?        = null
)

/*
	Appareils du téléphone.
	pcs     : PC appairés
	home    : réseaux familiaux
	enabled : synchronisation Wi-Fi activée (désactivée par défaut)
*/
@Serializable
data class PhonePeers(
	val pcs     : List<PairedPc>   = emptyList(),
	val home    : List<NetworkId>  = emptyList(),
	val enabled : Boolean          = false
)

/*
	État de la synchronisation Wi-Fi du téléphone.
*/
sealed interface WifiStatus
{
	// Synchronisation Wi-Fi désactivée (réglage).
	data object Disabled : WifiStatus

	// Aucun PC appairé.
	data object Unpaired : WifiStatus

	/*
		Hors du réseau familial (ou nom du Wi-Fi illisible) : rien n'est tenté.
		reason : explication
	*/
	data class Away(val reason: String) : WifiStatus

	// Synchronisation en cours.
	data object Working : WifiStatus

	/*
		À jour.
		at  : instant (ms)
		pcs : PC joints, en clair
	*/
	data class Idle(
		val at  : Long,
		val pcs : List<String>
	) : WifiStatus

	/*
		Un choix attend l'utilisateur sur un PC.
		message : explication
	*/
	data class Waiting(val message: String) : WifiStatus

	/*
		Échec (aucun PC joignable, refus…).
		message     : raison
		newerFormat : format plus récent en cause (mise à jour nécessaire) ; null sinon
	*/
	data class Failed(
		val message     : String,
		val newerFormat : Int? = null
	) : WifiStatus
}

/*
	Synchronisation Wi-Fi du téléphone, relais entre les PC : **seulement sur
	le réseau familial** (nom du Wi-Fi reconnu, réseau non facturé à l'usage) ;
	pour chaque PC appairé joignable (dernière adresse, puis recherche sur le
	réseau local), envoie les données du téléphone, adopte celles que le PC
	renvoie (si le téléphone n'a pas changé entre-temps, après la sauvegarde
	de sécurité), puis accuse réception. Le PC décide (voir PeerSyncServer).
	inContext : contexte (réseau, fichier des appareils)
	inBackup  : données du téléphone
	inStore   : mémoire de la synchronisation (identifiant du téléphone)
	inHooks   : actions autour d'un import (sauvegarde de sécurité)
	inClock   : horloge
*/
@Singleton
class WifiSyncer @Inject constructor(
	@ApplicationContext private val inContext : Context,
	private val inBackup : BackupRepository,
	private val inStore  : SyncStore,
	private val inHooks  : SyncHooks,
	private val inClock  : Clock
) : DefaultLifecycleObserver
{
	private val fScope  = CoroutineScope(SupervisorJob() + Dispatchers.Default)		// portée des synchronisations automatiques
	private val fFile   = File(inContext.filesDir, "appareils.json")				// PC appairés et réseaux familiaux
	private val fLock   = Mutex()														// une synchronisation à la fois
	private val fStatus = MutableStateFlow<WifiStatus>(if (!load().enabled) WifiStatus.Disabled else if (load().pcs.isEmpty()) WifiStatus.Unpaired else WifiStatus.Away("En attente de la première synchronisation (au premier plan, sur le Wi-Fi de la maison)."))	// état
	private val fPeers  = MutableStateFlow(load())										// appareils

	// État de la synchronisation Wi-Fi.
	val status: StateFlow<WifiStatus> = fStatus

	// PC appairés et réseaux familiaux.
	val peers: StateFlow<PhonePeers> = fPeers

	// Lecture du réseau du téléphone (remplaçable en test).
	var network: () -> PhoneNetwork = { currentPhoneNetwork(inContext) }

	// Recherche des PC sur le réseau local (remplaçable en test).
	var discover: () -> List<FoundPc> = { discoverPcs() }

	/*
		Démarre la synchronisation automatique : quelques secondes après chaque
		modification des données (le retour au premier plan passe par onStart).
		inChanges : signal de modification des données
	*/
	@OptIn(FlowPreview::class)
	fun start(inChanges: Flow<Any>)
	{
		fScope.launch { inChanges.drop(1).debounce(4_000).collect { runCatching { sync() } } }
	}

	/*
		Retour au premier plan : synchronisation (seulement à la maison).
		inOwner : processus
	*/
	override fun onStart(inOwner: LifecycleOwner)
	{
		fScope.launch { runCatching { sync() } }
	}

	/*
		Appairage avec le PC d'une offre scannée : se présente avec la clé de
		l'offre (adresses de l'offre essayées l'une après l'autre), puis
		enregistre le PC et les réseaux familiaux. Rend null si l'appairage a
		réussi, sinon la raison.
		inOffer : offre lue dans le QR code
	*/
	suspend fun pair(inOffer: PairingOffer): String? =
		withContext(Dispatchers.IO) {
			val vNet    = network()																// réseau du téléphone
			val vWifi   = vNet.wifi ?: return@withContext if (!vNet.permission) "Autorisez la localisation : elle sert à reconnaître le Wi-Fi de la maison." else "Reliez le téléphone au Wi-Fi de la maison."
			val vClient = LinkClient(phoneInfo(inContext, inStore.deviceId()), inClock::millis)	// client
			val vRequest = kJson.encodeToString(PeerRequest.serializer(), PeerRequest(kOpPair, device = phoneInfo(inContext, inStore.deviceId()), home = listOf(vWifi)))	// demande
			var vError  = "PC injoignable"															// dernière erreur
			for (vHost in inOffer.addresses)
			{
				val vReply = runCatching { kJson.decodeFromString(PeerReply.serializer(), vClient.call(vHost, inOffer.port, inOffer.pcId, inOffer.key, vRequest)) }	// réponse
				vReply.onFailure { vError = it.message ?: "PC injoignable" }
				val vOk    = vReply.getOrNull() ?: continue										// réponse lue
				if (vOk.action == kActRefused) return@withContext vOk.message ?: "appairage refusé"
				val vPc    = PairedPc(inOffer.pcId, vOk.pc?.name ?: inOffer.pcName, toBase64(inOffer.key), inOffer.port, listOf(vHost) + inOffer.addresses.filter { it != vHost })	// PC appairé
				save(load().let { inPeers -> inPeers.copy(pcs = inPeers.pcs.filter { it.id != vPc.id } + vPc, home = (inPeers.home + listOf(vWifi) + vOk.home).distinct()) })	// interrupteur gardé
				link(vPc)
				return@withContext null
			}
			"Appairage impossible : $vError. Vérifiez que les deux appareils sont sur le même Wi-Fi et que le QR code est encore valable."
		}

	/*
		Retire un PC appairé (il n'est plus joint).
		inId : PC
	*/
	suspend fun remove(inId: String)
	{
		val vGone = load().pcs.firstOrNull { it.id == inId }										// PC retiré
		save(load().let { it.copy(pcs = it.pcs.filter { inPc -> inPc.id != inId }) })
		vGone?.let { link(it, inRemoved = true) }
	}

	/*
		Inscrit dans les données du téléphone le couple avec un PC (schéma de
		l'écosystème, réuni par les synchronisations).
		inPc      : PC
		inRemoved : vrai si le couple est retiré
	*/
	private suspend fun link(
		inPc      : PairedPc,
		inRemoved : Boolean = false
	)
	{
		val vMe = phoneInfo(inContext, inStore.deviceId())											// ce téléphone
		inBackup.addDevices(listOf(DeviceLinkDto(inPc.id, inPc.name, vMe.id, vMe.name, inClock.millis(), inPc.lastSync, inRemoved, inClock.millis())))
	}

	/*
		Active ou désactive la synchronisation Wi-Fi (désactivée par défaut) ;
		désactivée, rien n'est tenté.
		inOn : vrai pour l'activer
	*/
	fun setEnabled(inOn: Boolean)
	{
		save(load().copy(enabled = inOn))
		fStatus.value = if (!inOn) WifiStatus.Disabled else if (load().pcs.isEmpty()) WifiStatus.Unpaired else WifiStatus.Away("En attente de la première synchronisation.")
	}

	/*
		Retire un réseau familial.
		inNetwork : réseau
	*/
	fun removeHome(inNetwork: NetworkId)
	{
		save(load().let { it.copy(home = it.home - inNetwork) })
	}

	/*
		Synchronise avec chaque PC appairé joignable, seulement sur le réseau
		familial.
	*/
	suspend fun sync(): WifiStatus =
		fLock.withLock {
			withContext(Dispatchers.IO) {
				val vPeers = load()																		// appareils
				val vNet   = network()																	// réseau du téléphone
				val vNext  = when
				{
					!vPeers.enabled                                   -> WifiStatus.Disabled
					vPeers.pcs.isEmpty()                              -> WifiStatus.Unpaired
					!vNet.onWifi                                      -> WifiStatus.Away("Pas de Wi-Fi : synchronisation au retour à la maison.")
					!vNet.unmetered                                   -> WifiStatus.Away("Wi-Fi facturé à l’usage : pas de synchronisation.")
					vNet.wifi == null                                 -> WifiStatus.Away(if (!vNet.permission) "Autorisez la localisation pour reconnaître le Wi-Fi de la maison." else "Nom du Wi-Fi illisible : activez la localisation du téléphone.")
					!isHomeNetwork(listOf(vNet.wifi), vPeers.home)    -> WifiStatus.Away("Hors du réseau familial (Wi-Fi « ${vNet.wifi.value} ») : synchronisation au retour à la maison.")
					else                                              -> { fStatus.value = WifiStatus.Working; syncAll(vPeers) }
				}
				fStatus.value = vNext
				vNext
			}
		}

	/*
		Synchronise avec chaque PC joignable.
		inPeers : appareils
	*/
	private suspend fun syncAll(inPeers: PhonePeers): WifiStatus
	{
		val vFound   by lazy { runCatching { discover() }.getOrDefault(emptyList()) }			// PC trouvés sur le réseau local (au besoin)
		val vReached = mutableListOf<String>()														// PC joints
		var vWait    : String? = null																// choix attendu sur un PC
		var vError   : WifiStatus.Failed? = null													// dernier échec
		for (vPc in inPeers.pcs)
		{
			val vHosts = (vPc.addresses + vFound.filter { it.beacon.id == vPc.id }.map { it.address }).distinct()	// adresses à essayer
			val vPort  = vFound.firstOrNull { it.beacon.id == vPc.id }?.beacon?.port ?: vPc.port					// port
			val vKey   = fromBase64(vPc.key) ?: continue
			val vHost  = vHosts.firstOrNull { inHost -> runCatching { exchange(inHost, vPort, vPc, vKey) }.onSuccess { inStep ->
				when (inStep)
				{
					is PhoneStep.Wait    -> vWait = inStep.message
					is PhoneStep.Refused -> vError = WifiStatus.Failed("${vPc.name} : ${inStep.message}", inStep.newerFormat)
					else                 -> Unit
				}
			}.isSuccess } ?: continue																	// adresse qui a répondu
			vReached += vPc.name
			save(load().let { inNow -> inNow.copy(pcs = inNow.pcs.map { if (it.id == vPc.id) it.copy(lastSync = inClock.millis(), addresses = (listOf(vHost) + it.addresses).distinct().take(4), port = vPort) else it }) })
			load().pcs.firstOrNull { it.id == vPc.id }?.let { link(it) }
		}
		return when
		{
			vError != null     -> vError!!
			vWait != null      -> WifiStatus.Waiting(vWait!!)
			vReached.isEmpty() -> WifiStatus.Failed("Aucun PC joignable : ouvrez PatrimoinePlus sur un PC de la maison.")
			else               -> WifiStatus.Idle(inClock.millis(), vReached)
		}
	}

	/*
		Un échange avec un PC : envoi des données, adoption de la réponse,
		accusé de réception.
		inHost : adresse
		inPort : port
		inPc   : PC
		inKey  : clé de la paire
	*/
	private suspend fun exchange(
		inHost : String,
		inPort : Int,
		inPc   : PairedPc,
		inKey  : ByteArray
	): PhoneStep
	{
		val vClient = LinkClient(phoneInfo(inContext, inStore.deviceId()), inClock::millis)		// client
		val vLocal  = inBackup.snapshot()																// données du téléphone
		val vLocalFp = syncFingerprint(vLocal)															// leur empreinte
		val vReply  = kJson.decodeFromString(PeerReply.serializer(), vClient.call(inHost, inPort, inPc.id, inKey, kJson.encodeToString(PeerRequest.serializer(), PeerRequest(kOpSync, file = encodeBackup(vLocal)))))	// réponse
		val vStep   = phoneStep(vReply)																	// suite à donner
		if (vStep is PhoneStep.Adopt && inHooks.beforeImport() && inBackup.replaceAll(vStep.file, vLocalFp))
		{
			inHooks.afterImport()
			vClient.call(inHost, inPort, inPc.id, inKey, kJson.encodeToString(PeerRequest.serializer(), PeerRequest(kOpAck, fingerprint = vStep.fingerprint)))
		}
		return vStep
	}

	/*
		Appareils enregistrés ; aucun si le fichier est absent ou illisible.
	*/
	private fun load(): PhonePeers = runCatching { kJson.decodeFromString(PhonePeers.serializer(), fFile.readText()) }.getOrDefault(PhonePeers())

	/*
		Enregistre les appareils (fichier provisoire puis remplacement).
		inPeers : appareils
	*/
	private fun save(inPeers: PhonePeers)
	{
		val vTemp = File(inContext.filesDir, "appareils.json.tmp")							// écriture provisoire
		vTemp.writeText(kJson.encodeToString(PhonePeers.serializer(), inPeers))
		if (!vTemp.renameTo(fFile)) { fFile.delete(); vTemp.renameTo(fFile) }
		fPeers.value = inPeers
		if (inPeers.enabled && inPeers.pcs.isEmpty()) fStatus.value = WifiStatus.Unpaired
	}
}
