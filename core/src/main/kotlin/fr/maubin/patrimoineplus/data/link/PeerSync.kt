package fr.maubin.patrimoineplus.data.link

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.sync.HistoryAuthor
import fr.maubin.patrimoineplus.data.sync.MergeConflict
import fr.maubin.patrimoineplus.data.sync.MergeSide
import fr.maubin.patrimoineplus.data.sync.SyncPlan
import fr.maubin.patrimoineplus.data.sync.planSync
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import kotlinx.serialization.Serializable
import fr.maubin.patrimoineplus.data.sync.withAuthor
import fr.maubin.patrimoineplus.data.sync.joinHistory

const val kOpPair = "pair"															// appairage du téléphone
const val kOpSync = "sync"															// synchronisation : le téléphone envoie ses données
const val kOpAck  = "ack"															// le téléphone a adopté les données envoyées par le PC

const val kActSame    = "same"														// rien à faire : mêmes données
const val kActStored  = "stored"													// le PC a adopté les données du téléphone
const val kActAdopt   = "adopt"														// le téléphone doit adopter les données jointes
const val kActWait    = "wait"														// un choix attend l'utilisateur sur le PC
const val kActRefused = "refused"													// refus (format, données illisibles)

/*
	Demande du téléphone au PC (contenu d'un message scellé).
	op          : nature (kOpPair, kOpSync, kOpAck)
	device      : présentation du téléphone (appairage)
	home        : réseaux familiaux connus du téléphone (appairage)
	file        : données du téléphone, en JSON (synchronisation)
	fingerprint : empreinte des données adoptées (accusé de réception)
*/
@Serializable
data class PeerRequest(
	val op          : String,
	val device      : DeviceInfo?     = null,
	val home        : List<NetworkId> = emptyList(),
	val file        : String?         = null,
	val fingerprint : String?         = null
)

/*
	Réponse du PC (contenu d'un message scellé).
	action      : suite à donner (kActSame, kActStored, kActAdopt, kActWait, kActRefused)
	file        : données à adopter, en JSON (kActAdopt)
	fingerprint : empreinte des données jointes
	message     : explication, en clair
	newerFormat : format des données refusées s'il est plus récent que celui du PC
	pc          : présentation du PC (appairage)
	home        : réseaux familiaux connus du PC (appairage)
*/
@Serializable
data class PeerReply(
	val action      : String,
	val file        : String?         = null,
	val fingerprint : String?         = null,
	val message     : String?         = null,
	val newerFormat : Int?            = null,
	val pc          : DeviceInfo?     = null,
	val home        : List<NetworkId> = emptyList()
)

/*
	Dernière version commune avec un pair.
	fingerprint : son empreinte
	base        : son contenu
*/
data class PeerBase(
	val fingerprint : String,
	val base        : BackupFile
)

/*
	Choix attendu sur le PC pour les données d'un téléphone.
	peerId    : téléphone
	peerName  : son nom
	remote    : ses données, telles que reçues
	conflicts : éléments modifiés des deux côtés ; vide : fusion impossible, choix d'une version entière
	reason    : raison du choix d'une version entière
*/
data class PendingChoice(
	val peerId    : String,
	val peerName  : String,
	val remote    : BackupFile,
	val conflicts : List<MergeConflict>,
	val reason    : String = ""
)

/*
	Ce que le PC confie au serveur de synchronisation : ses données, leur
	remplacement, la mémoire par téléphone, la présentation des choix.
*/
interface PeerHost
{
	/*
		Données du PC.
	*/
	fun local(): BackupFile

	/*
		Remplace les données du PC (import ou fusion).
		inFile : données vérifiées
		inNote : résultat de la fusion, en clair ; null pour un import
	*/
	fun adopt(
		inFile : BackupFile,
		inNote : String?
	)

	/*
		Dernière version commune avec un téléphone ; null avant la première.
		inPeerId : téléphone
	*/
	fun base(inPeerId: String): PeerBase?

	/*
		Mémorise la dernière version commune avec un téléphone.
		inPeerId : téléphone
		inBase   : version commune
	*/
	fun record(
		inPeerId : String,
		inBase   : PeerBase
	)

	/*
		Présente un choix à l'utilisateur (ou l'efface : null).
		inChoice : choix attendu
	*/
	fun ask(inChoice: PendingChoice?)
}

/*
	Synchronisation côté PC avec les téléphones appairés : le PC décide (voir
	planSync), à partir de la dernière version commune avec ce téléphone ; les
	choix (éléments modifiés des deux côtés, version entière) sont présentés
	sur le PC, le téléphone attend. Une version commune n'est retenue qu'une
	fois acquise des deux côtés : quand le PC envoie des données à adopter, il
	attend l'accusé de réception du téléphone (s'il ne vient pas, la fusion
	suivante repart de l'ancienne version commune, rien n'est perdu).
	inHost   : PC
	inAuthor : auteur des changements du PC (historique)
	inNow    : horloge (ms)
*/
class PeerSyncServer(
	private val inHost   : PeerHost,
	private val inAuthor : () -> HistoryAuthor?,
	private val inNow    : () -> Long = System::currentTimeMillis
)
{
	private val fSent   = mutableMapOf<String, PeerBase>()						// données envoyées à adopter, par téléphone (en attente d'accusé)
	private var fChoice : PendingChoice? = null									// choix présenté ; null sans

	// Choix présenté sur le PC ; null sans.
	val choice: PendingChoice? get() = fChoice

	/*
		Répond à une demande de synchronisation d'un téléphone appairé.
		inPeerId   : téléphone
		inPeerName : son nom
		inRequest  : demande
	*/
	@Synchronized
	fun answer(
		inPeerId   : String,
		inPeerName : String,
		inRequest  : PeerRequest
	): PeerReply =
		when (inRequest.op)
		{
			kOpSync -> sync(inPeerId, inPeerName, inRequest)
			kOpAck  ->
			{
				val vSent = fSent.remove(inPeerId)												// données envoyées
				if (vSent != null && vSent.fingerprint == inRequest.fingerprint) inHost.record(inPeerId, vSent)
				PeerReply(kActSame)
			}
			else    -> PeerReply(kActRefused, message = "demande « ${inRequest.op} » inconnue")
		}

	/*
		Tranche le choix présenté : par élément (inChoices), ou version entière
		(inKeepPc). La version du téléphone telle que reçue devient la version
		commune : la prochaine synchronisation lui enverra le résultat.
		inChoices : version gardée pour chaque élément, par clé ; ignoré pour une version entière
		inKeepPc  : version entière : vrai pour garder le PC, faux pour garder le téléphone
	*/
	@Synchronized
	fun resolve(
		inChoices : Map<String, MergeSide> = emptyMap(),
		inKeepPc  : Boolean = true
	)
	{
		val vChoice = fChoice ?: return														// choix présenté
		val vRemote = vChoice.remote															// données du téléphone
		val vBase   = PeerBase(syncFingerprint(vRemote), vRemote)							// nouvelle version commune
		if (vChoice.conflicts.isEmpty())
		{
			if (!inKeepPc) inHost.adopt(vRemote, null)
		}
		else
		{
			val vMine = inHost.base(vChoice.peerId)											// version commune connue
			when (val vPlan = planSync(vMine?.fingerprint, vMine?.base, inHost.local(), vRemote, inChoices, inAuthor(), inNow()))
			{
				is SyncPlan.Merge -> inHost.adopt(vPlan.file, note(vChoice.peerName, vPlan))
				is SyncPlan.Ask   -> { show(vChoice.copy(conflicts = vPlan.conflicts)); return }	// le PC a changé entre-temps : nouveaux conflits
				else              -> Unit
			}
		}
		inHost.record(vChoice.peerId, vBase)
		show(null)
	}

	/*
		Synchronisation : décision, puis réponse.
		inPeerId   : téléphone
		inPeerName : son nom
		inRequest  : demande avec les données du téléphone
	*/
	private fun sync(
		inPeerId   : String,
		inPeerName : String,
		inRequest  : PeerRequest
	): PeerReply
	{
		val vRemote = when (val vRead = decodeBackup(inRequest.file ?: return PeerReply(kActRefused, message = "données absentes")))
		{
			is BackupRead.Valid   -> vRead.file
			is BackupRead.Invalid -> return PeerReply(kActRefused, message = vRead.message, newerFormat = vRead.newerFormat)
		}																						// données du téléphone, vérifiées
		fChoice?.takeIf { it.peerId == inPeerId }?.let { return PeerReply(kActWait, message = waitText(it)) }
		val vBase = inHost.base(inPeerId)														// version commune
		return when (val vPlan = planSync(vBase?.fingerprint, vBase?.base, inHost.local(), vRemote, inAuthor = inAuthor(), inNow = inNow()))
		{
			SyncPlan.Nothing   -> PeerReply(kActSame)
			SyncPlan.Record    -> { inHost.record(inPeerId, PeerBase(syncFingerprint(vRemote), vRemote)); PeerReply(kActSame) }
			is SyncPlan.Import ->
			{
				val vFile = withAuthor(vPlan.file, vBase?.base, HistoryAuthor(inPeerId, inPeerName), inNow())	// historique : changements du téléphone
				inHost.adopt(vFile, null)
				inHost.record(inPeerId, PeerBase(syncFingerprint(vFile), vFile))
				PeerReply(kActStored)
			}
			is SyncPlan.Export -> { if (vPlan.file.history != inHost.local().history) inHost.adopt(vPlan.file, null); send(inPeerId, vPlan.file) }
			is SyncPlan.Merge  ->
			{
				val vPhone = withAuthor(vRemote, vBase?.base, HistoryAuthor(inPeerId, inPeerName), inNow()).history	// historique : changements du téléphone
				val vFile  = vPlan.file.copy(history = joinHistory(vPhone, vPlan.file.history))				// fusion et historiques réunis
				inHost.adopt(vFile, note(inPeerName, vPlan))
				send(inPeerId, vFile)
			}
			is SyncPlan.Ask    -> { show(PendingChoice(inPeerId, inPeerName, vRemote, vPlan.conflicts)); PeerReply(kActWait, message = waitText(fChoice!!)) }
			is SyncPlan.Whole  -> { show(PendingChoice(inPeerId, inPeerName, vRemote, emptyList(), vPlan.reason)); PeerReply(kActWait, message = waitText(fChoice!!)) }
		}
	}

	/*
		Réponse avec des données à adopter ; la version commune attend
		l'accusé de réception.
		inPeerId : téléphone
		inFile   : données envoyées
	*/
	private fun send(
		inPeerId : String,
		inFile   : BackupFile
	): PeerReply
	{
		val vFp = syncFingerprint(inFile)														// empreinte
		fSent[inPeerId] = PeerBase(vFp, inFile)
		return PeerReply(kActAdopt, file = encodeBackup(inFile), fingerprint = vFp)
	}

	/*
		Présente ou efface le choix.
		inChoice : choix ; null pour l'effacer
	*/
	private fun show(inChoice: PendingChoice?)
	{
		fChoice = inChoice
		inHost.ask(inChoice)
	}

	/*
		Attente d'un choix, en clair (montré par le téléphone).
		inChoice : choix attendu
	*/
	private fun waitText(inChoice: PendingChoice): String =
		if (inChoice.conflicts.isEmpty()) "Choix en attente sur le PC : quelle version garder ?"
		else "Choix en attente sur le PC : ${inChoice.conflicts.size} élément(s) modifié(s) des deux côtés."

	/*
		Résultat d'une fusion, en clair.
		inPeer : nom du téléphone
		inPlan : fusion
	*/
	private fun note(
		inPeer : String,
		inPlan : SyncPlan.Merge
	): String =
		"Fusion avec $inPeer : ${inPlan.merged} changement(s) repris" +
			(if (inPlan.restored.isEmpty()) "." else " ; rétabli(s), car encore utilisé(s) : ${inPlan.restored.joinToString(", ")}.")
}

/*
	Suite à donner par le téléphone à une réponse du PC.
*/
sealed interface PhoneStep
{
	// Rien à faire.
	data object Done : PhoneStep

	/*
		Adopter les données du PC, puis accuser réception.
		file        : données vérifiées
		fingerprint : leur empreinte
	*/
	data class Adopt(
		val file        : BackupFile,
		val fingerprint : String
	) : PhoneStep

	/*
		Un choix attend l'utilisateur sur le PC.
		message : explication
	*/
	data class Wait(val message: String) : PhoneStep

	/*
		Refus.
		message     : raison
		newerFormat : format plus récent en cause ; null sinon
	*/
	data class Refused(
		val message     : String,
		val newerFormat : Int?
	) : PhoneStep
}

/*
	Lit la réponse du PC à une synchronisation (téléphone) : données jointes
	vérifiées comme un fichier d'échange.
	inReply : réponse
*/
fun phoneStep(inReply: PeerReply): PhoneStep =
	when (inReply.action)
	{
		kActSame, kActStored -> PhoneStep.Done
		kActWait             -> PhoneStep.Wait(inReply.message ?: "Choix en attente sur le PC.")
		kActAdopt            ->
			when (val vRead = decodeBackup(inReply.file.orEmpty()))
			{
				is BackupRead.Valid   -> PhoneStep.Adopt(vRead.file, inReply.fingerprint ?: syncFingerprint(vRead.file))
				is BackupRead.Invalid -> PhoneStep.Refused(vRead.message, vRead.newerFormat)
			}
		else                 -> PhoneStep.Refused(inReply.message ?: "refus du PC", inReply.newerFormat)
	}
