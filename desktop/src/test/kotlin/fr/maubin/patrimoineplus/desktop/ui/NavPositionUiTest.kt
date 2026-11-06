package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.SettingsSection
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.NavPosition
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
	Position de la barre de navigation du PC : réglage mémorisé et relu,
	choisi dans Réglages › Apparence, pages toujours atteignables quelle que
	soit la position ; captures des quatre positions
	(`build/captures-brume/navigation-*.png`).
*/
class NavPositionUiTest
{
	@get:Rule
	val fCompose = createComposeRule()											// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()													// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Contrôleur de démonstration relié à des réglages jetables.
	*/
	private fun demo(): DesktopController =
		DesktopController(config(), Workspace(kClock), null).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }

	/*
		Réglages jetables (relus par le test).
	*/
	private fun config(): DesktopConfig = DesktopConfig(fTemp.root.toPath().resolve("desktop.properties"))

	/*
		Rend l'appli en PNG.
		inApp  : contrôleur
		inName : nom du fichier
	*/
	private fun capture(
		inApp  : DesktopController,
		inName : String
	)
	{
		val vDir   = File("build/captures-brume").apply { mkdirs() }						// dossier des captures
		val vScene = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		vScene.render()
		File(vDir, "$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Haut par défaut ; chaque position est mémorisée et relue ; une valeur
		inconnue revient en haut.
	*/
	@Test
	fun setting()
	{
		val vApp = demo()																	// démonstration
		assertEquals(NavPosition.TOP, vApp.settings.value.nav)
		NavPosition.entries.forEach { inPosition ->
			vApp.setNavPosition(inPosition)
			assertEquals(inPosition, config().load().nav)
		}
		val vFile = fTemp.root.toPath().resolve("desktop.properties")						// fichier des réglages
		vFile.toFile().writeText(vFile.toFile().readText().replace("nav_position=RIGHT", "nav_position=DIAGONALE"))
		assertEquals(NavPosition.TOP, config().load().nav)
	}

	/*
		Réglages › Apparence : « Gauche » place la barre à gauche ; la page
		Budget reste atteignable par son icône (une seule barre affichée).
	*/
	@Test
	fun choice()
	{
		val vApp = demo()																	// démonstration
		vApp.show(Screen.SETTINGS)
		vApp.openSettings(SettingsSection.APPEARANCE)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Gauche").performScrollTo().performClick()
		fCompose.waitForIdle()
		assertEquals(NavPosition.LEFT, vApp.settings.value.nav)
		assertEquals(1, fCompose.onAllNodesWithContentDescription("Budget").fetchSemanticsNodes().size)
		fCompose.onNodeWithContentDescription("Budget").performClick()
		fCompose.waitForIdle()
		assertEquals(Screen.BUDGET, vApp.navigation.value.screen)
	}

	/*
		Captures du tableau de bord avec la barre en haut, en bas, à gauche et
		à droite (en sombre pour la droite).
	*/
	@Test
	fun captures()
	{
		val vApp = demo()																	// démonstration
		NavPosition.entries.forEach { inPosition ->
			vApp.setNavPosition(inPosition)
			if (inPosition == NavPosition.RIGHT) vApp.setAppearance(ThemeMode.DARK)
			capture(vApp, "navigation-${inPosition.name.lowercase()}")
		}
	}
}
