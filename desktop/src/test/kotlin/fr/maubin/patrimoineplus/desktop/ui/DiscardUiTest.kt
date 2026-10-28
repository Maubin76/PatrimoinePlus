package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Saisie non enregistrée sur le PC (sans écran) : panneau intact fermé sans
	question ; saisie modifiée : la croix, un autre écran, un autre panneau
	et la fermeture de l'appli demandent « Abandonner la saisie ? » ;
	« Continuer la saisie » garde tout, « Abandonner » poursuit l'action ;
	l'enregistrement ne demande rien. Capture `32-abandon-saisie.png`.
*/
class DiscardUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Contrôleur en démonstration, en clair, sur les réglages jetables.
	*/
	private fun demo(): DesktopController =
		DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }

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
		Ouvre « Nouveau compte » et tape un nom.
		inApp : contrôleur
	*/
	private fun typeAccountName(inApp: DesktopController)
	{
		inApp.openPanel(Panel.AccountSheet(null))
		fCompose.waitForIdle()
		fCompose.onNode(hasSetTextAction() and hasText("Nom")).performTextInput("Livret jeune")
		fCompose.waitForIdle()
	}

	/*
		Panneau intact : la croix ferme sans question.
	*/
	@Test
	fun untouchedClosesDirectly()
	{
		val vApp = demo()																	// démonstration
		fCompose.setContent { App(vApp) { null } }
		vApp.openPanel(Panel.AccountSheet(null))
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Fermer").performClick()
		fCompose.waitForIdle()
		assertNull(vApp.panel.value)
		assertNull(vApp.discard.value)
	}

	/*
		Croix sur une saisie modifiée : question ; continuer garde la saisie,
		abandonner ferme.
	*/
	@Test
	fun closeAsks()
	{
		val vApp = demo()																	// démonstration
		fCompose.setContent { App(vApp) { null } }
		typeAccountName(vApp)
		fCompose.onNodeWithContentDescription("Fermer").performClick()
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Abandonner la saisie ?").assertExists()
		fCompose.onNodeWithText("Continuer la saisie").performClick()
		fCompose.waitForIdle()
		assertNotNull(vApp.panel.value)
		fCompose.onNode(hasSetTextAction() and hasText("Livret jeune")).assertExists()
		fCompose.onNodeWithContentDescription("Fermer").performClick()
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Abandonner").performClick()
		fCompose.waitForIdle()
		assertNull(vApp.panel.value)
		assertNull(vApp.discard.value)
	}

	/*
		Autre écran, autre panneau, fermeture de l'appli : question avant de
		perdre la saisie.
	*/
	@Test
	fun navigationAndExitAsk()
	{
		val vApp = demo()																	// démonstration
		fCompose.setContent { App(vApp) { null } }
		typeAccountName(vApp)
		vApp.show(Screen.BUDGET)
		fCompose.waitForIdle()
		assertEquals(Screen.DASHBOARD, vApp.navigation.value.screen)
		fCompose.onNodeWithText("Abandonner la saisie ?").assertExists()
		vApp.cancelDiscard()
		vApp.newOperation()
		fCompose.waitForIdle()
		assertTrue(vApp.panel.value is Panel.AccountSheet)
		vApp.cancelDiscard()
		var vExited = false																	// appli fermée
		vApp.requestExit { vExited = true }
		fCompose.waitForIdle()
		assertFalse(vExited)
		fCompose.onNodeWithText("Abandonner").performClick()
		fCompose.waitForIdle()
		assertTrue(vExited)
		vApp.show(Screen.BUDGET)
		assertEquals(Screen.BUDGET, vApp.navigation.value.screen)
	}

	/*
		Fenêtre de confirmation (capture en dernier : la scène hors écran
		refermée oublie la saisie modifiée).
	*/
	@Test
	fun dialogCapture()
	{
		val vApp = demo()																	// démonstration
		fCompose.setContent { App(vApp) { null } }
		typeAccountName(vApp)
		fCompose.onNodeWithContentDescription("Fermer").performClick()
		fCompose.waitForIdle()
		capture(vApp, "32-abandon-saisie")
	}

	/*
		Enregistrement : aucune question.
	*/
	@Test
	fun saveDoesNotAsk()
	{
		val vApp = demo()																	// démonstration
		fCompose.setContent { App(vApp) { null } }
		typeAccountName(vApp)
		fCompose.onNodeWithText("Enregistrer").performClick()
		fCompose.waitForIdle()
		assertNull(vApp.panel.value)
		assertNull(vApp.discard.value)
		assertTrue(vApp.workspace.value.data.accounts.any { it.name == "Livret jeune" })
		vApp.show(Screen.BUDGET)
		assertEquals(Screen.BUDGET, vApp.navigation.value.screen)
	}
}
