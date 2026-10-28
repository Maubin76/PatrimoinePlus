package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
