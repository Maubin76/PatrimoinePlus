package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Assistant du PC (sans écran, démonstration) : en haut du rail à la place
	du logo ; un clic ouvre sa bulle (résumé de l'humeur, remarques) ; une
	remarque mène à sa page ; en mode discret, yeux fermés et montants
	masqués dans la bulle ; réglages : retiré (logo de retour), mode calme
	mémorisé. Captures `35-*.png`.
*/
class AssistantUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-27T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

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
		Bulle, remarque qui mène à sa page, mode discret, réglages.
	*/
	@Test
	fun assistant()
	{
		val vConfig = DesktopConfig(fTemp.root.toPath().resolve("desktop.properties"))	// réglages
		val vApp    = DesktopController(vConfig, Workspace(kClock), null, kClock).apply { openDemo(); setAppearance(ThemeMode.LIGHT); show(Screen.BUDGET) }	// démonstration
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Logo PatrimoinePlus").assertDoesNotExist()
		fCompose.onNodeWithContentDescription("Assistant", substring = true).performClick()
		fCompose.waitForIdle()
		fCompose.onAllNodesWithText("ce mois-ci", substring = true)[0].assertExists()
		val vRemark = fCompose.onAllNodesWithText("Patrimoine en hausse", substring = true)	// remarque du patrimoine
		vRemark[0].assertExists()
		vRemark[0].performClick()
		fCompose.waitForIdle()
		assertEquals(Screen.DASHBOARD, vApp.navigation.value.screen)
		vApp.toggleHidden()
		fCompose.onNodeWithContentDescription("Assistant", substring = true).performClick()
		fCompose.waitForIdle()
		assertTrue(fCompose.onAllNodesWithText("Patrimoine en hausse de ${fr.maubin.patrimoineplus.domain.format.kMaskedAmount}", substring = true).fetchSemanticsNodes().isNotEmpty())
		vApp.toggleHidden()
		capture(vApp, "35-assistant-rail")
		vApp.setAssistantCalm(true)
		assertTrue(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load().calm)
		vApp.setAssistant(false)
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Logo PatrimoinePlus").assertExists()
		assertFalse(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load().assistant)
	}
}
