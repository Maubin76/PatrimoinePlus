package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Visites guidées du PC (sans écran) : au lancement, celle du tableau de
	bord seulement ; Suivant, Passer (la page est marquée vue, mémorisée) ;
	à la première venue sur le Budget, la sienne ; « Ne plus afficher les
	tutoriels » coupe les suivantes ; dans les réglages, « Revoir » ouvre la
	page et relance sa visite, « Réactiver » efface les pages vues. Captures
	`29-*.png`.
*/
@OptIn(ExperimentalTestApi::class)
class TourUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Visites réactivées pour ce test (coupées pour les autres).
	*/
	@Before
	fun enableTours()
	{
		System.clearProperty("patrimoineplus.tours")
	}

	/*
		Visites coupées de nouveau pour les tests suivants.
	*/
	@After
	fun disableTours()
	{
		System.setProperty("patrimoineplus.tours", "off")
	}

	/*
		Rend l'appli dans un PNG (relecture humaine).
		inApp  : contrôleur
		inName : nom du fichier
	*/
	private fun capture(inApp: DesktopController, inName: String)
	{
		val vScene = ImageComposeScene(width = 1440, height = 1000, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		vScene.render()
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Parcours complet des visites.
	*/
	@Test
	fun tours()
	{
		val vConfig = DesktopConfig(fTemp.root.toPath().resolve("desktop.properties"))	// réglages
		val vApp    = DesktopController(vConfig, Workspace(kClock), null, kClock).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }	// démonstration
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitUntil(5_000) { vApp.tour.value != null }
		assertEquals(Screen.DASHBOARD.name, vApp.tour.value!!.key)
		fCompose.onNodeWithText("Votre patrimoine net").assertExists()
		fCompose.onNodeWithText("1 / 6").assertExists()
		capture(vApp, "29-tutoriel-tableau-de-bord")
		fCompose.onNodeWithText("Suivant").performClick()
		fCompose.waitForIdle()
		assertEquals(1, vApp.tour.value!!.step)
		fCompose.onNodeWithText("Passer").performClick()
		fCompose.waitForIdle()
		assertNull(vApp.tour.value)
		assertEquals(setOf(Screen.DASHBOARD.name), vConfig.load().toursSeen)
		vApp.show(Screen.BUDGET)
		fCompose.waitUntil(5_000) { vApp.tour.value != null }
		assertEquals(Screen.BUDGET.name, vApp.tour.value!!.key)
		capture(vApp, "29-tutoriel-budget")
		fCompose.onNodeWithText("Ne plus afficher les tutoriels").performClick()
		fCompose.waitForIdle()
		assertTrue(vConfig.load().toursOff)
		vApp.show(Screen.GOALS)
		fCompose.mainClock.advanceTimeBy(1_000)
		fCompose.waitForIdle()
		assertNull(vApp.tour.value)												// visites coupées
		vApp.show(Screen.SETTINGS)
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Revoir le tutoriel Budget").performScrollTo().performClick()
		fCompose.waitForIdle()
		assertEquals(Screen.BUDGET, vApp.navigation.value.screen)
		assertEquals(Screen.BUDGET.name, vApp.tour.value!!.key)
		vApp.finishTour()
		vApp.show(Screen.SETTINGS)
		fCompose.waitForIdle()
		capture(vApp, "29-reglages-tutoriels")
		fCompose.onNodeWithText("Réactiver tous les tutoriels").performScrollTo().performClick()
		fCompose.waitForIdle()
		assertTrue(vConfig.load().toursSeen.isEmpty())
		assertTrue(!vConfig.load().toursOff)
	}
}
