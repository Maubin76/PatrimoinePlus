package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Thèmes personnalisés sur le PC (sans écran) : création depuis les
	réglages (nom, cinq codes, aperçu), le thème créé devient le thème
	choisi et mémorisé ; code illisible signalé ; tableau de bord aux
	couleurs du thème, en clair et en sombre ; choisir un thème fixe le
	quitte ; suppression : retour au thème Coloré. Captures `30-*.png`.
*/
class ThemesUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-26T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Rend l'appli dans un PNG (relecture humaine).
		inApp  : contrôleur
		inName : nom du fichier
	*/
	private fun capture(inApp: DesktopController, inName: String)
	{
		val vScene = ImageComposeScene(width = 1440, height = 1100, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		listOf(0L, 300L, 1_000L).forEach { vScene.render(it * 1_000_000L) }
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(1_500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Parcours complet d'un thème personnalisé.
	*/
	@Test
	fun customTheme()
	{
		val vConfig = DesktopConfig(fTemp.root.toPath().resolve("desktop.properties"))	// réglages
		val vApp    = DesktopController(vConfig, Workspace(kClock), null, kClock).apply { openDemo(); setAppearance(ThemeMode.LIGHT); show(Screen.SETTINGS) }	// démonstration
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Créer un thème").performScrollTo().performClick()
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Nom du thème").performTextReplacement("Lavande")
		fCompose.onNodeWithText("Principale").performTextReplacement("#7b6cf6")
		fCompose.onNodeWithText("Hausses et entrées").performTextReplacement("#1F9D8B")
		fCompose.onNodeWithText("Investissements").performTextReplacement("zz")
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Code illisible (ex. #219EBC).").assertExists()
		fCompose.onNodeWithText("Corrigez les codes illisibles pour voir l’aperçu.").assertExists()
		fCompose.onNodeWithText("Investissements").performTextReplacement("#E9A23B")
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Aperçu du thème").assertExists()
		capture(vApp, "30-editeur-theme")
		fCompose.onNodeWithText("Enregistrer").performClick()
		fCompose.waitForIdle()
		val vTheme = vApp.workspace.value.data.themes.single()							// thème créé
		assertEquals("Lavande", vTheme.name)
		assertEquals(listOf("#7B6CF6", "#1F9D8B", "#E9A23B", "#FB8500", "#8ECAE6"), vTheme.colors)
		assertEquals(vTheme.id, vConfig.load().palette)
		vApp.show(Screen.DASHBOARD)
		capture(vApp, "30-tableau-de-bord-theme")
		vApp.setAppearance(ThemeMode.DARK)
		capture(vApp, "30-tableau-de-bord-theme-sombre")
		vApp.setAppearance(ThemeMode.LIGHT)
		vApp.setColorTheme(ColorTheme.MINIMAL)
		assertEquals("", vConfig.load().palette)											// thème fixe : le personnalisé est quitté
		vApp.setCustomTheme(vTheme.id)
		vApp.deleteTheme(vTheme.id)
		assertTrue(vApp.workspace.value.data.themes.isEmpty())
		assertEquals(ColorTheme.COLORFUL, vConfig.load().colors)						// supprimé alors que choisi : thème Coloré
		assertEquals("", vConfig.load().palette)
	}
}
