package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import fr.maubin.patrimoineplus.desktop.ui.components.qrModules
import fr.maubin.patrimoineplus.data.link.PairingOffer
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import fr.maubin.patrimoineplus.data.backup.DeviceLinkDto
import fr.maubin.patrimoineplus.data.edit.EditResult

/*
	Page « Appareils » du PC (sans écran) : entrée du rail, ce PC (nom
	modifiable, réseau, adresses), diagnostic réseau prêt à lancer (non lancé
	ici : pas d'écoute sur le réseau pendant les tests). Capture
	`35-appareils.png`.
*/
class DevicesUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Page affichée depuis le rail ; renommage du PC mémorisé.
	*/
	@Test
	fun devicesPage()
	{
		val vApp = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }	// démonstration
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithContentDescription("Appareils").performClick()
		fCompose.waitForIdle()
		assertEquals(Screen.DEVICES, vApp.navigation.value.screen)
		fCompose.onNodeWithText("Ce PC").assertExists()
		fCompose.onNodeWithText("Lancer le diagnostic").assertExists()
		fCompose.onNode(hasSetTextAction()).performTextClearance()
		fCompose.onNode(hasSetTextAction()).performTextInput("PC du bureau")
		fCompose.onNodeWithText("Renommer").performClick()
		fCompose.waitForIdle()
		assertEquals("PC du bureau", DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load().deviceName)
		val vScene = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) { App(vApp) { null } }	// capture (relecture humaine)
		listOf(0L, 500L, 1_500L).forEach { vScene.render(it * 1_000_000L) }
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/35-appareils.png").writeBytes(vScene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}
	/*
		Appairage : fenêtre avec le QR code (lisible : son contenu relu est
		l'offre), temps restant ; « Annuler » retire l'offre. Dossier de données
		ouvert ; écoute sur la boucle locale seulement. Capture `37-appairage.png`.
	*/
	@Test
	fun pairingDialog()
	{
		val vFolder = fTemp.newFolder("donnees").toPath()										// dossier de données
		java.nio.file.Files.newOutputStream(vFolder.resolve(fr.maubin.patrimoineplus.data.sync.kSyncFileName)).use { fr.maubin.patrimoineplus.data.backup.writeBackupXlsx(fr.maubin.patrimoineplus.data.demoBackupFile(), it) }
		val vApp    = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock, inLocalOnly = true).apply { setAppearance(ThemeMode.LIGHT); openFolder(vFolder); show(Screen.DEVICES) }	// contrôleur
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Appairer le téléphone").assertDoesNotExist()						// désactivée par défaut
		fCompose.onNodeWithContentDescription("Synchronisation Wi-Fi").performScrollTo().performClick()
		fCompose.waitForIdle()
		assertTrue(vApp.settings.value.wifiSync)
		fCompose.onNodeWithText("Appairer le téléphone").performScrollTo().performClick()
		fCompose.waitForIdle()
		val vOffer  = vApp.peers.state.value.offer!!												// offre affichée
		assertTrue(vApp.peers.state.value.running)
		fCompose.onNodeWithContentDescription("QR code d’appairage").assertExists()
		val vModules = qrModules(vOffer.encode())													// modules du QR code
		val vMatrix  = com.google.zxing.common.BitMatrix((vModules.size + 8) * 6).apply { vModules.forEachIndexed { inY, inRow -> inRow.forEachIndexed { inX, inDark -> if (inDark) setRegion((inX + 4) * 6, (inY + 4) * 6, 6, 6) } } }	// QR code agrandi, avec sa marge
		assertEquals(vOffer, PairingOffer.decode(com.google.zxing.qrcode.QRCodeReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(BitMatrixSource(vMatrix)))).text))
		val vScene  = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) { App(vApp) { null } }	// capture (relecture humaine)
		listOf(0L, 500L, 1_500L).forEach { vScene.render(it * 1_000_000L) }
		File("build/captures-desktop/37-appairage.png").writeBytes(vScene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
		fCompose.onNodeWithText("Annuler").performClick()
		fCompose.waitForIdle()
		assertNull(vApp.peers.state.value.offer)
		vApp.peers.stop()
	}

	/*
		Source de luminance tirée d'un QR code (noir sur blanc), pour le relire.
		inMatrix : modules
	*/
	private class BitMatrixSource(private val inMatrix: com.google.zxing.common.BitMatrix) : com.google.zxing.LuminanceSource(inMatrix.width, inMatrix.height)
	{
		override fun getRow(inY: Int, inRow: ByteArray?): ByteArray = ByteArray(width) { inX -> if (inMatrix[inX, inY]) 0 else -1 }
		override fun getMatrix(): ByteArray = ByteArray(width * height) { inI -> if (inMatrix[inI % width, inI / width]) 0 else -1 }
	}
	/*
		Schéma de l'écosystème : trois PC et le téléphone, d'après les couples
		du fichier d'échange ; ce PC mis en avant ; couple retiré absent.
		Capture `38-ecosysteme.png`.
	*/
	@Test
	fun ecosystem()
	{
		val vApp  = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openDemo(); setAppearance(ThemeMode.LIGHT); setWifiSync(true); show(Screen.DEVICES) }	// démonstration
		val vSelf = vApp.settings.value.deviceId													// ce PC
		val vNow  = kClock.millis()																	// maintenant
		vApp.edit { EditResult.Done(it.copy(devices = listOf(
			DeviceLinkDto(vSelf, "PC du bureau", "tel-1", "Pixel 8", vNow - 9_000_000, vNow - 3_600_000, updatedAt = vNow),
			DeviceLinkDto("pc-2", "PC du salon", "tel-1", "Pixel 8", vNow - 9_000_000, vNow - 86_400_000, updatedAt = vNow),
			DeviceLinkDto("pc-3", "PC portable", "tel-1", "Pixel 8", vNow - 9_000_000, null, updatedAt = vNow),
			DeviceLinkDto("pc-4", "Ancien PC", "tel-1", "Pixel 8", vNow - 9_000_000, null, removed = true, updatedAt = vNow)
		))) }
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Téléphone (relais)").assertExists()
		fCompose.onNodeWithText("PC portable").assertExists()
		fCompose.onNodeWithText("Ancien PC").assertDoesNotExist()
		val vScene = ImageComposeScene(width = 1440, height = 1500, density = Density(1f)) { App(vApp) { null } }	// capture (relecture humaine)
		listOf(0L, 500L, 1_500L).forEach { vScene.render(it * 1_000_000L) }
		File("build/captures-desktop/38-ecosysteme.png").writeBytes(vScene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}
}
