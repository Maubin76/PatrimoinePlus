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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot

/*
	Assistant du PC (sans écran, démonstration) : flottant en bas à droite,
	le logo reste en haut du rail ; un clic ouvre sa bulle (résumé de
	l'humeur, remarques), au-dessus de lui ; une
	lien mène à sa page ; phrases naturelles, sans chiffres (rien à masquer
	en mode discret) ; caché quand le panneau de saisie est ouvert ;
	réglages : retiré, mode calme
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
		val vApp    = DesktopController(vConfig, Workspace(kClock), null, kClock).apply { openDemo(); setAppearance(ThemeMode.LIGHT); setAssistant(true); show(Screen.BUDGET) }	// démonstration
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Logo PatrimoinePlus").assertExists()
		val vAvatar = fCompose.onNodeWithContentDescription("Assistant :", substring = true).fetchSemanticsNode().boundsInRoot	// place de l'assistant
		val vRoot   = fCompose.onAllNodes(isRoot())[0].fetchSemanticsNode().boundsInRoot						// fenêtre
		assertTrue("assistant pas en bas à droite : $vAvatar dans $vRoot", vAvatar.right > vRoot.width * 0.8f && vAvatar.bottom > vRoot.height * 0.75f)
		fCompose.onNodeWithContentDescription("Assistant :", substring = true).performClick()
		fCompose.waitForIdle()
		File("build/captures-desktop").mkdirs()
		fCompose.onNodeWithContentDescription("Assistant dit :", substring = true).assertExists()
		fCompose.onAllNodesWithText("Votre patrimoine grimpe ce mois-ci.", substring = true)[0].assertExists()
		fCompose.onNodeWithText("Voir le tableau de bord").performClick()
		fCompose.waitForIdle()
		assertEquals(Screen.DASHBOARD, vApp.navigation.value.screen)
		vApp.toggleHidden()
		fCompose.onNodeWithContentDescription("Assistant :", substring = true).performClick()
		fCompose.waitForIdle()
		val vSaid = fCompose.onNodeWithContentDescription("Assistant dit :", substring = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()	// ce qu'il dit
		assertTrue("chiffres dans la bulle : $vSaid", vSaid.none { it.isDigit() })
		vApp.toggleHidden()
		capture(vApp, "35-assistant-coin")
		vApp.newOperation()
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Assistant :", substring = true).assertDoesNotExist()
		vApp.closePanel()
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Assistant :", substring = true).assertExists()
		vApp.setAssistantCalm(true)
		assertTrue(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load().calm)
		vApp.setAssistant(false)
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Assistant :", substring = true).assertDoesNotExist()
		assertFalse(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load().assistant)
	}
}
