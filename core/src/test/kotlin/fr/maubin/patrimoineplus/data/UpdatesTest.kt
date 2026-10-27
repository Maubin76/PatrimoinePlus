package fr.maubin.patrimoineplus.data

import com.sun.net.httpserver.HttpServer
import fr.maubin.patrimoineplus.data.update.GitHubReleaseSource
import fr.maubin.patrimoineplus.data.update.Release
import fr.maubin.patrimoineplus.data.update.ReleaseAsset
import fr.maubin.patrimoineplus.data.update.UpdatePlatform
import fr.maubin.patrimoineplus.data.update.downloadAsset
import fr.maubin.patrimoineplus.data.update.findUpdate
import fr.maubin.patrimoineplus.data.update.isNewer
import fr.maubin.patrimoineplus.data.update.parseRelease
import fr.maubin.patrimoineplus.data.update.sizeLabel
import fr.maubin.patrimoineplus.data.update.updateOffer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
import fr.maubin.patrimoineplus.data.update.UpdateCheck
import fr.maubin.patrimoineplus.data.update.checkUpdate

/*
	Mises à jour : lecture de la release GitHub, comparaison des versions,
	choix du fichier, offre (version ignorée, changement de format), et
	téléchargement vérifié par l'empreinte, sur un serveur HTTP local.
*/
class UpdatesTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// dossier de téléchargement

	private lateinit var fServer : HttpServer											// serveur local
	private var fRelease : String = ""													// réponse servie pour la release
	private val kPayload = ByteArray(300_000) { (it % 251).toByte() }					// fichier servi

	/*
		Réponse de l'API GitHub, réduite aux champs lus.
		inTag    : tag
		inBody   : notes
		inDigest : empreinte de l'APK
	*/
	private fun releaseJson(
		inTag    : String,
		inBody   : String,
		inDigest : String = "sha256:" + sha(kPayload)
	) = """
		{"tag_name": "$inTag", "name": "PatrimoinePlus", "body": "$inBody", "draft": false,
		 "assets": [
			{"name": "PatrimoinePlus-1.3-windows.zip", "size": 77832250, "digest": "sha256:ABCD", "browser_download_url": "https://exemple/zip"},
			{"name": "PatrimoinePlus-1.3.apk", "size": ${kPayload.size}, "digest": "$inDigest", "browser_download_url": "${url("/apk")}"},
			{"name": "sans adresse"}
		 ]}"""

	/*
		Empreinte SHA-256 en hexadécimal.
		inBytes : contenu
	*/
	private fun sha(inBytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(inBytes).joinToString("") { "%02x".format(it) }

	/*
		Adresse d'un chemin du serveur local.
		inPath : chemin
	*/
	private fun url(inPath: String) = "http://127.0.0.1:${fServer.address.port}$inPath"

	/*
		Démarre le serveur : /latest (release), /apk (fichier), /absent (404),
		/erreur (500).
	*/
	@Before
	fun start()
	{
		fServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		fServer.createContext("/latest") { inExchange ->
			val vBytes = fRelease.toByteArray()													// réponse
			inExchange.sendResponseHeaders(200, vBytes.size.toLong()); inExchange.responseBody.use { it.write(vBytes) }
		}
		fServer.createContext("/apk") { inExchange ->
			inExchange.sendResponseHeaders(200, kPayload.size.toLong()); inExchange.responseBody.use { it.write(kPayload) }
		}
		fServer.createContext("/absent") { inExchange -> inExchange.sendResponseHeaders(404, -1); inExchange.close() }
		fServer.createContext("/erreur") { inExchange -> inExchange.sendResponseHeaders(500, -1); inExchange.close() }
		fServer.start()
	}

	/*
		Arrête le serveur.
	*/
	@After
	fun stop() = fServer.stop(0)

	/*
		Comparaison des versions, nombre par nombre.
	*/
	@Test
	fun versions()
	{
		assertTrue(isNewer("1.3", "1.2"))
		assertTrue(isNewer("1.10", "1.9"))
		assertTrue(isNewer("v2.0", "1.12"))
		assertTrue(isNewer("1.2.1", "1.2"))
		assertFalse(isNewer("1.2", "1.2"))
		assertFalse(isNewer("1.2.0", "1.2"))
		assertFalse(isNewer("1.1", "1.2"))
		assertFalse(isNewer("1.9", "1.10"))
		assertEquals("5 Mo", sizeLabel(5_100_305))
		assertEquals("78 Mo", sizeLabel(77_832_250))
		assertEquals("850 ko", sizeLabel(850_000))
	}

	/*
		Lecture de la release : tag sans « v », format annoncé, fichiers
		lisibles avec leur empreinte ; réponse illisible ou sans tag : null.
	*/
	@Test
	fun parsing()
	{
		val vRelease = parseRelease(releaseJson("v1.3", "Format d'échange : 7\\n\\n- feat: x"))!!	// release lue
		assertEquals("1.3", vRelease.version)
		assertEquals(7, vRelease.format)
		assertEquals(listOf("PatrimoinePlus-1.3-windows.zip", "PatrimoinePlus-1.3.apk"), vRelease.assets.map { it.name })
		assertEquals("abcd", vRelease.assets[0].sha256)
		assertEquals(77_832_250L, vRelease.assets[0].size)
		assertNull(parseRelease(releaseJson("v1.3", "- feat: x"))!!.format)
		assertNull(parseRelease("pas du json"))
		assertNull(parseRelease("""{"name": "sans tag"}"""))
		assertEquals(emptyList<ReleaseAsset>(), parseRelease("""{"tag_name": "v1.4", "assets": "bizarre"}""")!!.assets)
	}

	/*
		Offre : fichier de la plateforme, version ignorée, release pas plus
		récente, plateforme sans fichier, changement de format.
	*/
	@Test
	fun offers()
	{
		val vRelease = Release("1.3", 7, listOf(ReleaseAsset("PatrimoinePlus-1.3.apk", 5L, "u", null), ReleaseAsset("PatrimoinePlus-1.3-windows.zip", 7L, "v", null)))	// release
		assertEquals("PatrimoinePlus-1.3.apk", updateOffer(vRelease, "1.2", UpdatePlatform.ANDROID, inFormat = 7)!!.asset.name)
		assertEquals("PatrimoinePlus-1.3-windows.zip", updateOffer(vRelease, "1.2", UpdatePlatform.WINDOWS, inFormat = 7)!!.asset.name)
		assertFalse(updateOffer(vRelease, "1.2", UpdatePlatform.ANDROID, inFormat = 7)!!.formatChanged)
		assertTrue(updateOffer(vRelease, "1.2", UpdatePlatform.ANDROID, inFormat = 6)!!.formatChanged)
		assertFalse(updateOffer(vRelease.copy(format = null), "1.2", UpdatePlatform.ANDROID, inFormat = 6)!!.formatChanged)
		assertNull(updateOffer(vRelease, "1.2", UpdatePlatform.ANDROID, inIgnored = "1.3"))
		assertEquals("1.3", updateOffer(vRelease, "1.2", UpdatePlatform.ANDROID, inIgnored = "1.2.5")!!.version)
		assertNull(updateOffer(vRelease, "1.3", UpdatePlatform.ANDROID))
		assertNull(updateOffer(vRelease.copy(assets = vRelease.assets.take(1)), "1.2", UpdatePlatform.WINDOWS))
	}

	/*
		Vérification par le réseau : offre trouvée ; 404, erreur 500 ou
		serveur injoignable : aucune offre, sans exception.
	*/
	@Test
	fun check() = runBlocking {
		fRelease = releaseJson("v1.3", "Format d'échange : 7")
		assertEquals("1.3", findUpdate(GitHubReleaseSource(url("/latest")), "1.2", UpdatePlatform.ANDROID)!!.version)
		assertNull(findUpdate(GitHubReleaseSource(url("/latest")), "1.3", UpdatePlatform.ANDROID))
		assertNull(findUpdate(GitHubReleaseSource(url("/absent")), "1.2", UpdatePlatform.ANDROID))
		assertNull(findUpdate(GitHubReleaseSource(url("/erreur")), "1.2", UpdatePlatform.ANDROID))
		assertNull(findUpdate(GitHubReleaseSource("http://127.0.0.1:1/latest"), "1.2", UpdatePlatform.ANDROID))
		assertEquals(UpdateCheck.UpToDate, checkUpdate(GitHubReleaseSource(url("/latest")), "1.2", UpdatePlatform.ANDROID, "1.3"))
		assertEquals(UpdateCheck.Unreachable, checkUpdate(GitHubReleaseSource(url("/erreur")), "1.2", UpdatePlatform.ANDROID))
		assertEquals(UpdateCheck.Unreachable, checkUpdate(GitHubReleaseSource(url("/absent")), "1.2", UpdatePlatform.ANDROID))
	}

	/*
		Téléchargement : fichier complet, empreinte juste, progression jusqu'à
		1 ; empreinte fausse : refus, rien ne reste sur le disque.
	*/
	@Test
	fun download() = runBlocking {
		fRelease = releaseJson("v1.3", "")
		val vAsset    = parseRelease(fRelease)!!.assets.first { it.name.endsWith(".apk") }	// APK servi
		val vSteps    = mutableListOf<Float>()													// avancements reçus
		val vFile     = downloadAsset(vAsset, fTemp.root.resolve("maj/PatrimoinePlus-1.3.apk")) { vSteps += it }	// fichier reçu
		assertTrue(vFile.readBytes().contentEquals(kPayload))
		assertEquals(1f, vSteps.last())
		assertTrue(vSteps.zipWithNext().all { (inA, inB) -> inB >= inA })
		val vTarget = fTemp.root.resolve("faux.apk")											// cible du fichier altéré
		try
		{
			downloadAsset(vAsset.copy(sha256 = "0".repeat(64)), vTarget)
			fail("empreinte fausse acceptée")
		}
		catch (inError: IOException)
		{
			assertTrue(inError.message!!.contains("altéré"))
		}
		assertFalse(vTarget.exists())
		assertFalse(fTemp.root.resolve("faux.apk.part").exists())
	}
}
