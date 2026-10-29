package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.TransactionDto
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.backup.kBackupVersion
import fr.maubin.patrimoineplus.data.link.PairingOffer
import fr.maubin.patrimoineplus.data.link.PeerBase
import fr.maubin.patrimoineplus.data.link.PeerHost
import fr.maubin.patrimoineplus.data.link.PeerRequest
import fr.maubin.patrimoineplus.data.link.PeerSyncServer
import fr.maubin.patrimoineplus.data.link.PendingChoice
import fr.maubin.patrimoineplus.data.link.PhoneStep
import fr.maubin.patrimoineplus.data.link.kActRefused
import fr.maubin.patrimoineplus.data.link.kOpAck
import fr.maubin.patrimoineplus.data.link.kOpSync
import fr.maubin.patrimoineplus.data.link.newLinkKey
import fr.maubin.patrimoineplus.data.link.phoneStep
import fr.maubin.patrimoineplus.data.sync.HistoryAuthor
import fr.maubin.patrimoineplus.data.sync.MergeSide
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	PC simulé : ses données, sa mémoire par téléphone, le choix présenté.
	inName : nom du PC
	inData : données de départ
*/
private class FakePc(
	val inName : String,
	inData     : BackupFile
) : PeerHost
{
	var data   : BackupFile = inData													// données du PC
	var note   : String?    = null															// dernier résultat de fusion
	var choice : PendingChoice? = null														// choix présenté
	val bases  = mutableMapOf<String, PeerBase>()											// versions communes par téléphone
	val server = PeerSyncServer(this, { HistoryAuthor("id-$inName", inName) }, { 1_000L })	// serveur de synchronisation

	override fun local(): BackupFile = data
	override fun adopt(inFile: BackupFile, inNote: String?) { data = inFile; note = inNote }
	override fun base(inPeerId: String): PeerBase? = bases[inPeerId]
	override fun record(inPeerId: String, inBase: PeerBase) { bases[inPeerId] = inBase }
	override fun ask(inChoice: PendingChoice?) { choice = inChoice }
}

/*
	Téléphone simulé : ses données.
	inData : données de départ
*/
private class FakePhone(var data: BackupFile)
{
	/*
		Synchronise avec un PC : envoie ses données, adopte la réponse si elles
		n'ont pas changé entre-temps (sinon, pas d'accusé), accuse réception.
		inPc       : PC
		inMeantime : changement fait sur le téléphone pendant l'échange ; null sans
	*/
	fun sync(
		inPc       : FakePc,
		inMeantime : ((BackupFile) -> BackupFile)? = null
	): PhoneStep
	{
		val vStep = phoneStep(inPc.server.answer("tel-1", "Pixel", PeerRequest(kOpSync, file = encodeBackup(data))))	// réponse
		inMeantime?.let { data = it(data) }
		if (vStep is PhoneStep.Adopt && inMeantime == null)
		{
			data = vStep.file
			inPc.server.answer("tel-1", "Pixel", PeerRequest(kOpAck, fingerprint = vStep.fingerprint))
		}
		return vStep
	}
}

/*
	Synchronisation PC ↔ téléphone, le téléphone servant de relais entre
	plusieurs PC : première synchronisation, relais d'un PC à l'autre,
	changements des deux côtés fusionnés, même élément choisi sur le PC (le
	téléphone attend), données du téléphone modifiées pendant l'échange (rien
	perdu), format plus récent refusé, offre d'appairage du QR code.
*/
class PeerSyncTest
{
	private val kBase = demoBackupFile()													// données communes de départ

	/*
		Nouvelle dépense, copie d'une dépense de la démonstration.
		inId    : identifiant
		inLabel : libellé
	*/
	private fun expense(
		inId    : String,
		inLabel : String
	): TransactionDto =
		kBase.transactions.first { it.kind == "STANDARD" && it.postings.size == 1 && it.postings.single().amountCents < 0 && it.categoryId != null }.let {
			it.copy(id = inId, label = inLabel, postings = it.postings.map { inPosting -> inPosting.copy(id = "$inId-p") })
		}

	/*
		Contenu avec une dépense de plus.
		inFile  : contenu
		inId    : identifiant
		inLabel : libellé
	*/
	private fun add(
		inFile  : BackupFile,
		inId    : String,
		inLabel : String
	): BackupFile = inFile.copy(transactions = inFile.transactions + expense(inId, inLabel))

	/*
		Identifiants des opérations d'un contenu.
		inFile : contenu
	*/
	private fun ids(inFile: BackupFile): Set<String> = inFile.transactions.map { it.id }.toSet()

	/*
		Relais : une saisie sur le PC du bureau passe par le téléphone et arrive
		sur le PC du salon ; puis saisies des deux côtés fusionnées.
	*/
	@Test
	fun phoneRelaysBetweenPcs()
	{
		val vDesk  = FakePc("PC du bureau", kBase)												// premier PC
		val vLiving = FakePc("PC du salon", kBase)												// second PC
		val vPhone = FakePhone(kBase)																// téléphone
		assertEquals(PhoneStep.Done, vPhone.sync(vDesk))										// première fois : mêmes données
		assertEquals(PhoneStep.Done, vPhone.sync(vLiving))
		vDesk.data = add(vDesk.data, "t-bureau", "Saisi au bureau")
		assertTrue(vPhone.sync(vDesk) is PhoneStep.Adopt)										// le téléphone reçoit la saisie
		assertTrue("t-bureau" in ids(vPhone.data))
		assertEquals(PhoneStep.Done, vPhone.sync(vLiving))										// le salon l'adopte
		assertTrue("t-bureau" in ids(vLiving.data))
		vLiving.data = add(vLiving.data, "t-salon", "Saisi au salon")
		vPhone.data  = add(vPhone.data, "t-tel", "Saisi sur le téléphone")
		assertTrue(vPhone.sync(vLiving) is PhoneStep.Adopt)									// fusion
		assertTrue(ids(vLiving.data).containsAll(listOf("t-bureau", "t-salon", "t-tel")))
		assertEquals(ids(vLiving.data), ids(vPhone.data))
		assertEquals("Fusion avec Pixel : 1 changement(s) repris.", vLiving.note)
		assertTrue(vLiving.data.history.map { it.device }.containsAll(listOf("PC du bureau", "PC du salon", "Pixel")))	// historique : les trois appareils
		assertEquals(PhoneStep.Done, vPhone.sync(vLiving))										// rien de neuf
		assertEquals(PhoneStep.Done, vPhone.sync(vDesk))										// le bureau, inchangé, adopte tout
		assertEquals(ids(vLiving.data), ids(vDesk.data))
	}

	/*
		Même opération renommée sur le PC et sur le téléphone : le téléphone
		attend, le PC présente le choix ; une fois tranché, le téléphone reçoit
		le résultat.
	*/
	@Test
	fun sameElementIsChosenOnThePc()
	{
		val vPc    = FakePc("PC du bureau", kBase)												// PC
		val vPhone = FakePhone(kBase)																// téléphone
		vPhone.sync(vPc)
		val vId    = kBase.transactions.first { it.kind == "STANDARD" }.id						// opération renommée des deux côtés
		vPc.data    = vPc.data.copy(transactions = vPc.data.transactions.map { if (it.id == vId) it.copy(label = "Nom du PC", updatedAt = 5) else it })
		vPhone.data = vPhone.data.copy(transactions = vPhone.data.transactions.map { if (it.id == vId) it.copy(label = "Nom du téléphone", updatedAt = 6) else it })
		val vWait  = vPhone.sync(vPc) as PhoneStep.Wait											// attente
		assertTrue(vWait.message.contains("1 élément"))
		assertEquals(listOf("operations/$vId"), vPc.choice!!.conflicts.map { it.key })
		assertTrue(vPhone.sync(vPc) is PhoneStep.Wait)											// toujours en attente
		vPc.server.resolve(mapOf("operations/$vId" to MergeSide.THEIRS))
		assertNull(vPc.choice)
		assertEquals("Nom du téléphone", vPc.data.transactions.single { it.id == vId }.label)
		assertTrue(vPhone.sync(vPc) is PhoneStep.Adopt || vPhone.data.transactions.single { it.id == vId }.label == "Nom du téléphone")
		assertEquals("Nom du téléphone", vPhone.data.transactions.single { it.id == vId }.label)
		assertEquals(PhoneStep.Done, vPhone.sync(vPc))
	}

	/*
		Le téléphone change pendant l'échange (il n'adopte pas, pas d'accusé) :
		la synchronisation suivante fusionne sans rien perdre.
	*/
	@Test
	fun noAckLosesNothing()
	{
		val vPc    = FakePc("PC du bureau", kBase)												// PC
		val vPhone = FakePhone(kBase)																// téléphone
		vPhone.sync(vPc)
		vPc.data = add(vPc.data, "t-pc", "Saisi sur le PC")
		vPhone.sync(vPc) { add(it, "t-tel", "Saisi pendant l'échange") }						// pas adopté
		assertTrue("t-pc" !in ids(vPhone.data))
		assertTrue(vPhone.sync(vPc) is PhoneStep.Adopt)
		assertTrue(ids(vPhone.data).containsAll(listOf("t-pc", "t-tel")))
		assertTrue(ids(vPc.data).containsAll(listOf("t-pc", "t-tel")))
	}

	/*
		Version entière (fusion impossible : pas de version commune connue, et
		données différentes) : choix sur le PC ; « le téléphone » l'adopte.
	*/
	@Test
	fun wholeVersionIsChosenOnThePc()
	{
		val vPc    = FakePc("PC du bureau", add(kBase, "t-pc", "Saisi sur le PC"))			// PC
		val vPhone = FakePhone(add(kBase, "t-tel", "Saisi sur le téléphone"))				// téléphone, jamais synchronisé avec ce PC
		assertTrue(vPhone.sync(vPc) is PhoneStep.Wait)
		assertTrue(vPc.choice!!.conflicts.isEmpty())
		vPc.server.resolve(inKeepPc = false)
		assertTrue("t-tel" in ids(vPc.data) && "t-pc" !in ids(vPc.data))
		assertEquals(PhoneStep.Done, vPhone.sync(vPc))
	}

	/*
		Données d'un format plus récent : refusées avec le format.
	*/
	@Test
	fun newerFormatIsRefused()
	{
		val vPc    = FakePc("PC du bureau", kBase)												// PC
		val vReply = vPc.server.answer("tel-1", "Pixel", PeerRequest(kOpSync, file = encodeBackup(kBase.copy(version = kBackupVersion + 1))))	// réponse
		assertEquals(kActRefused, vReply.action)
		assertEquals(kBackupVersion + 1, vReply.newerFormat)
		assertEquals(PhoneStep.Refused(vReply.message!!, kBackupVersion + 1), phoneStep(vReply))
	}

	/*
		Offre d'appairage : aller-retour par le texte du QR code ; autre
		contenu ou clé abîmée : refusé.
	*/
	@Test
	fun pairingOffer()
	{
		val vOffer = PairingOffer("pc-1", "PC du bureau & salon", listOf("192.168.1.20", "10.0.0.5"), 47801, newLinkKey(), 1_000_000)	// offre
		assertEquals(vOffer, PairingOffer.decode(vOffer.encode()))
		assertNull(PairingOffer.decode("https://exemple.fr"))
		assertNull(PairingOffer.decode(vOffer.encode().replace(Regex("cle=[^&]+"), "cle=abc")))
		assertNotNull(syncFingerprint(kBase))
	}
	/*
		Téléphone neuf (vide) appairé avec un PC rempli : il reçoit les données
		du PC sans question ; PC neuf (vide) : il adopte celles du téléphone.
	*/
	@Test
	fun newDeviceTakesTheData()
	{
		val vEmpty = kBase.copy(accounts = emptyList(), transactions = emptyList(), statements = emptyList(), prices = emptyList(), recurrences = emptyList(), loans = emptyList())	// appareil neuf
		val vPc    = FakePc("PC du bureau", kBase)												// PC rempli
		val vPhone = FakePhone(vEmpty)																// téléphone neuf
		assertTrue(vPhone.sync(vPc) is PhoneStep.Adopt)
		assertEquals(ids(kBase), ids(vPhone.data))
		assertNull(vPc.choice)
		val vNewPc = FakePc("PC neuf", vEmpty)														// PC neuf
		assertEquals(PhoneStep.Done, FakePhone(kBase).sync(vNewPc))
		assertEquals(ids(kBase), ids(vNewPc.data))
	}
}
