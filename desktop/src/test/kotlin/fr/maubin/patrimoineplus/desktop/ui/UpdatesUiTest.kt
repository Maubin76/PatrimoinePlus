package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.sun.net.httpserver.HttpServer
import fr.maubin.patrimoineplus.data.update.Release
import fr.maubin.patrimoineplus.data.update.ReleaseAsset
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.desktop.update.DesktopUpdater
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import fr.maubin.patrimoineplus.data.backup.kBackupVersion

/*
	Mises à jour du PC (sans écran, réseau local) : fenêtre proposée au
	lancement (version, taille, rappel de l'autre appareil, sans liste des
	commits), « Ignorer cette version » mémorisé, téléchargement vérifié puis
	installation et fermeture demandée ; empreinte fausse : échec affiché,
	« Réessayer » ; dossier non remplaçable : téléchargement seul ; réglages :
	interrupteur et « Vérifier maintenant ». Captures `31-*.png`.
*/
class UpdatesUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages et téléchargements jetables

	private val kClock   = Clock.fixed(Instant.parse("2026-10-27T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private val kPayload = ByteArray(200_000) { (it % 97).toByte() }					// zip servi (contenu factice)
	private val fScope   = CoroutineScope(SupervisorJob() + Dispatchers.Default)		// portée des vérifications
	private lateinit var fServer : HttpServer											// serveur local du fichier
	private var fInstalls = mutableListOf<Pair<File, Path>>()							// installations demandées
	private var fRevealed = mutableListOf<File>()										// fichiers montrés

	/*
		Démarre le serveur du fichier.
	*/
	@Before
	fun start()
	{
		fServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		fServer.createContext("/zip") { inExchange ->
			inExchange.sendResponseHeaders(200, kPayload.size.toLong()); inExchange.responseBody.use { it.write(kPayload) }
		}
		fServer.start()
	}

	/*
		Arrête le serveur et la portée.
	*/
	@After
	fun stop()
	{
		fServer.stop(0)
		fScope.cancel()
	}

	/*
		Release 1.3 servie localement.
		inSha    : empreinte annoncée du zip
		inFormat : format d'échange annoncé
	*/
	private fun release(
		inSha    : String = MessageDigest.getInstance("SHA-256").digest(kPayload).joinToString("") { "%02x".format(it) },
		inFormat : Int    = kBackupVersion
	) = Release("1.3", inFormat, listOf(
		ReleaseAsset("PatrimoinePlus-1.3.apk", 5_100_305, "http://127.0.0.1:${fServer.address.port}/apk", null),
		ReleaseAsset("PatrimoinePlus-1.3-windows.zip", kPayload.size.toLong(), "http://127.0.0.1:${fServer.address.port}/zip", inSha)
	))

	/*
		Contrôleur en démonstration avec des mises à jour factices.
		inRelease : release servie ; null : GitHub injoignable
		inFolder  : dossier de l'appli ; null : téléchargement seul
	*/
	private fun controller(
		inRelease : Release?,
		inFolder  : Path? = fTemp.root.toPath().resolve("PatrimoinePlus")
	): DesktopController
	{
		val vUpdater = DesktopUpdater(
			source   = { inRelease ?: throw IOException("hors ligne") },
			version  = "1.2",
			folder   = inFolder,
			download = fTemp.root.toPath().resolve("telechargements"),
			install  = { inZip, inTarget -> fInstalls += inZip to inTarget },
			reveal   = { fRevealed += it }
		)																				// mises à jour factices
		return DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), fScope, kClock, vUpdater).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }
	}

	/*
		Rend l'appli dans un PNG (relecture humaine).
		inApp  : contrôleur
		inName : nom du fichier
	*/
	private fun capture(inApp: DesktopController, inName: String)
	{
		val vScene = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		listOf(0L, 300L, 1_000L).forEach { vScene.render(it * 1_000_000L) }
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(1_500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Lancement : fenêtre proposée, puis mise à jour complète.
	*/
	@Test
	fun offerAndInstall()
	{
		val vApp = controller(release())												// contrôleur
		fCompose.setContent { App(vApp) { null } }
		vApp.checkForUpdates()
		fCompose.waitUntil(5_000) { vApp.update.value != null }
		fCompose.onNodeWithText("PatrimoinePlus 1.3 est disponible").assertExists()
		fCompose.onNodeWithText("Vous avez la version 1.2. Téléchargement : 200 ko.").assertExists()
		fCompose.onNodeWithText("Pensez à mettre aussi à jour votre autre appareil.").assertExists()
		capture(vApp, "31-mise-a-jour")
		fCompose.onNodeWithText("Mettre à jour").performClick()
		fCompose.waitUntil(5_000) { vApp.exitRequested.value }
		assertEquals(1, fInstalls.size)
		assertTrue(fInstalls.single().first.readBytes().contentEquals(kPayload))
		assertEquals("PatrimoinePlus-1.3-windows.zip", fInstalls.single().first.name)
		assertEquals(fTemp.root.toPath().resolve("PatrimoinePlus"), fInstalls.single().second)
	}

	/*
		Changement de format annoncé, « Ignorer cette version » mémorisé : plus
		de fenêtre au lancement suivant.
	*/
	@Test
	fun formatAndIgnore()
	{
		val vApp = controller(release(inFormat = kBackupVersion + 1))										// release d'un format plus récent
		fCompose.setContent { App(vApp) { null } }
		vApp.checkForUpdates()
		fCompose.waitUntil(5_000) { vApp.update.value != null }
		fCompose.onNodeWithText("Cette version change le format du fichier d’échange : mettez à jour le téléphone et le PC ensemble, sinon ils ne pourront plus se synchroniser.").assertExists()
		capture(vApp, "31-mise-a-jour-format")
		fCompose.onNodeWithText("Ignorer cette version").performClick()
		fCompose.waitForIdle()
		assertNull(vApp.update.value)
		assertEquals("1.3", vApp.settings.value.ignored)
		val vNext = controller(release(inFormat = kBackupVersion + 1))										// lancement suivant
		vNext.checkForUpdates()
		Thread.sleep(500)
		assertNull(vNext.update.value)
	}

	/*
		Empreinte fausse : échec affiché, rien d'installé, « Réessayer » ;
		« Plus tard » ferme.
	*/
	@Test
	fun corruptedDownload()
	{
		val vApp = controller(release(inSha = "0".repeat(64)))								// empreinte fausse
		fCompose.setContent { App(vApp) { null } }
		vApp.checkForUpdates()
		fCompose.waitUntil(5_000) { vApp.update.value != null }
		fCompose.onNodeWithText("Mettre à jour").performClick()
		fCompose.waitUntil(5_000) { vApp.update.value?.error != null }
		fCompose.onNodeWithText("La mise à jour a échoué : fichier altéré (empreinte différente).").assertExists()
		fCompose.onNodeWithText("Réessayer").assertExists()
		capture(vApp, "31-mise-a-jour-echec")
		assertTrue(fInstalls.isEmpty())
		assertFalse(vApp.exitRequested.value)
		assertFalse(fTemp.root.resolve("telechargements/PatrimoinePlus-1.3-windows.zip").exists())
		fCompose.onNodeWithText("Plus tard").performClick()
		fCompose.waitForIdle()
		assertNull(vApp.update.value)
	}

	/*
		Dossier non remplaçable : « Télécharger », zip montré, pas de fermeture.
	*/
	@Test
	fun downloadOnly()
	{
		val vApp = controller(release(), inFolder = null)									// téléchargement seul
		fCompose.setContent { App(vApp) { null } }
		vApp.checkForUpdates()
		fCompose.waitUntil(5_000) { vApp.update.value != null }
		fCompose.onNodeWithText("Télécharger").performClick()
		fCompose.waitUntil(5_000) { fRevealed.isNotEmpty() }
		assertEquals("PatrimoinePlus-1.3-windows.zip", fRevealed.single().name)
		assertTrue(fInstalls.isEmpty())
		assertFalse(vApp.exitRequested.value)
		fCompose.waitUntil(5_000) { vApp.update.value == null }
	}

	/*
		Réglages : interrupteur mémorisé (plus de vérification au lancement),
		« Vérifier maintenant » : à jour, injoignable, ou fenêtre même pour une
		version ignorée.
	*/
	@Test
	fun settings()
	{
		val vApp = controller(release())													// contrôleur
		vApp.show(Screen.SETTINGS)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Version installée : 1.2").performScrollTo().assertExists()
		fCompose.onNodeWithContentDescription("Vérifier les mises à jour au démarrage").performClick()
		fCompose.waitForIdle()
		assertFalse(vApp.settings.value.updates)
		vApp.checkForUpdates()
		Thread.sleep(500)
		assertNull(vApp.update.value)
		fCompose.onNodeWithText("Vérifier maintenant").performClick()
		fCompose.waitUntil(5_000) { vApp.update.value != null }
		vApp.ignoreUpdate()
		vApp.checkUpdatesNow()
		fCompose.waitUntil(5_000) { vApp.update.value != null }
		vApp.postponeUpdate()
		val vOffline = controller(null)														// GitHub injoignable
		vOffline.checkUpdatesNow()
		fCompose.waitUntil(5_000) { vOffline.notice.value != null }
		assertEquals("Vérification impossible : GitHub est injoignable.", vOffline.notice.value)
		val vCurrent = controller(release().copy(version = "1.2"))							// déjà à jour
		vCurrent.checkUpdatesNow()
		fCompose.waitUntil(5_000) { vCurrent.notice.value != null }
		assertEquals("PatrimoinePlus est à jour (version 1.2).", vCurrent.notice.value)
	}
}
