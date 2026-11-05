package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Captures de la refonte « brume lumineuse » au format d'une fenêtre
	(1440 × 900, première vue) et en pleine hauteur, en clair et en sombre
	(sans assertion : relecture humaine de la mise en page).
*/
class MistCapturesTest
{
	@get:Rule
	val fTemp = TemporaryFolder()													// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Rend un écran en PNG.
		inName    : nom du fichier
		inHeight  : hauteur de la fenêtre
		inContent : écran
	*/
	private fun capture(
		inName    : String,
		inHeight  : Int,
		inContent : @Composable () -> Unit
	)
	{
		val vDir   = File("build/captures-brume").apply { mkdirs() }			// dossier des captures
		val vScene = ImageComposeScene(width = 1440, height = inHeight, density = Density(1f), content = inContent)	// scène hors écran
		vScene.render()
		val vImage = vScene.render(1_000_000_000L)								// image après une seconde (animations terminées)
		File(vDir, "$inName.png").writeBytes(vImage.encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Tableau de bord de démonstration, en clair et en sombre.
	*/
	@Test
	fun dashboard()
	{
		val vApp = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }	// démonstration
		capture("tableau-de-bord-clair", 900) { App(vApp) { null } }
		capture("tableau-de-bord-clair-complet", 1700) { App(vApp) { null } }
		vApp.setAppearance(ThemeMode.DARK)
		capture("tableau-de-bord-sombre", 900) { App(vApp) { null } }
	}

	/*
		Chaque page principale et quatre vues de compte, en clair, au format
		d'une fenêtre.
	*/
	@Test
	fun pages()
	{
		val vApp = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }	// démonstration
		Screen.entries.forEach { inScreen ->
			vApp.show(inScreen)
			capture("page-${inScreen.name.lowercase()}", 900) { App(vApp) { null } }
		}
		listOf("Compte courant", "PEA", "Livret A", "Prêt immobilier").forEach { inName ->
			vApp.openAccount(vApp.workspace.value.data.accounts.first { it.name == inName }.id)
			capture("compte-${inName.lowercase().replace(' ', '-')}", 900) { App(vApp) { null } }
		}
	}
}
