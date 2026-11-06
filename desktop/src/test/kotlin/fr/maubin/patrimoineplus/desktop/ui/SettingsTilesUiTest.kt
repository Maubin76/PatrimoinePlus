package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Réglages du PC en tuiles : une tuile par rubrique avec le résumé de son
	état ; un clic ouvre la rubrique, « Tous les réglages » et la page
	Réglages reviennent à la grille ; captures de la grille et d'une rubrique
	(`build/captures-brume/reglages-*.png`).
*/
class SettingsTilesUiTest
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
		DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }

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
		Grille : résumés à jour (source, apparence, mode discret) ; la tuile
		Apparence ouvre sa rubrique ; « Tous les réglages » revient à la
		grille ; la page Réglages repart toujours de la grille.
	*/
	@Test
	fun tiles()
	{
		val vApp = demo()																	// démonstration
		vApp.setNavPosition(NavPosition.LEFT)
		vApp.show(Screen.SETTINGS)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Démonstration : données fictives, jamais enregistrées").assertExists()
		fCompose.onNodeWithText("Clair · barre à gauche · fond par défaut").assertExists()
		fCompose.onNodeWithText("Montants visibles").assertExists()
		fCompose.onNodeWithText("Apparence").performClick()
		fCompose.waitForIdle()
		assertEquals(SettingsSection.APPEARANCE, vApp.navigation.value.section)
		fCompose.onNodeWithText("Barre de navigation").assertExists()
		fCompose.onNodeWithText("Tous les réglages").performClick()
		fCompose.waitForIdle()
		assertNull(vApp.navigation.value.section)
		fCompose.onNodeWithText("Catégories").assertExists()
		vApp.openSettings(SettingsSection.PRIVACY)
		vApp.show(Screen.SETTINGS)
		assertNull(vApp.navigation.value.section)
	}

	/*
		Captures : la grille en clair et en sombre, la rubrique Apparence.
	*/
	@Test
	fun captures()
	{
		val vApp = demo()																	// démonstration
		vApp.show(Screen.SETTINGS)
		capture(vApp, "reglages-tuiles-clair")
		vApp.setAppearance(ThemeMode.DARK)
		capture(vApp, "reglages-tuiles-sombre")
		vApp.setAppearance(ThemeMode.LIGHT)
		vApp.openSettings(SettingsSection.APPEARANCE)
		capture(vApp, "reglages-apparence")
	}
}
