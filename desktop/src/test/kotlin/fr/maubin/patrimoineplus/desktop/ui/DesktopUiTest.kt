package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.Appearance
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Tests de l'interface de l'appli PC : parcours complet en démonstration, et
	captures des écrans (build/captures-desktop) pour relecture humaine.
*/
class DesktopUiTest
{
	@get:Rule
	val fCompose = createComposeRule()											// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()													// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Contrôleur relié à des réglages jetables.
	*/
	private fun controller(): DesktopController =
		DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null)

	/*
		Parcours : écran d'accueil, démonstration, accueil (total, répartition),
		comptes, vue d'un livret (vue globale), réglages, fermer : retour à
		l'écran d'accueil.
	*/
	@Test
	fun demoWalkthrough()
	{
		val vApp = controller()													// contrôleur
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Essayer avec la démonstration").performClick()
		fCompose.onNodeWithText("Patrimoine").assertExists()
		fCompose.onNodeWithText("Répartition").assertExists()
		fCompose.onNodeWithText("Démonstration : données fictives, rien n’est synchronisé.").assertExists()
		fCompose.onNodeWithText("Comptes").performClick()
		fCompose.onNodeWithText("Livret A").performClick()
		fCompose.onNodeWithText("Solde au jour").assertExists()
		fCompose.onNodeWithText("Réglages").performClick()
		fCompose.onNodeWithText("Fermer").performClick()
		fCompose.onNodeWithText("Choisir le dossier synchronisé…").assertExists()
	}

	/*
		Captures des écrans principaux, en clair et en sombre (sans assertion :
		relecture humaine de la mise en page).
	*/
	@Test
	fun captures()
	{
		val vDir = File("build/captures-desktop").apply { mkdirs() }			// dossier des captures
		/*
			Rend un écran en PNG.
			inName    : nom du fichier
			inContent : écran
		*/
		fun capture(inName: String, inContent: @Composable () -> Unit)
		{
			val vScene = ImageComposeScene(width = 1320, height = 860, density = Density(1f), content = inContent)	// scène hors écran
			vScene.render()
			val vImage = vScene.render(1_000_000_000L)							// image après une seconde (animations terminées)
			File(vDir, "$inName.png").writeBytes(vImage.encodeToData(EncodedImageFormat.PNG)!!.bytes)
			vScene.close()
		}
		capture("1-bienvenue") { App(controller()) { null } }
		val vApp = controller().apply { openDemo(); setAppearance(Appearance.LIGHT) }	// démonstration, en clair
		capture("2-accueil") { App(vApp) { null } }
		vApp.show(Screen.ACCOUNTS)
		capture("3-comptes") { App(vApp) { null } }
		vApp.openAccount(vApp.workspace.value.data.accounts.first { it.name == "Compte courant" }.id)
		capture("4-compte-courant") { App(vApp) { null } }
		vApp.openAccount(vApp.workspace.value.data.accounts.first { it.name == "PEA" }.id)
		capture("5-compte-bourse") { App(vApp) { null } }
		vApp.openAccount(vApp.workspace.value.data.accounts.first { it.name == "Livret A" }.id)
		vApp.setAppearance(Appearance.DARK)
		capture("6-livret-sombre") { App(vApp) { null } }
		vApp.show(Screen.SETTINGS)
		capture("7-reglages-sombre") { App(vApp) { null } }
	}
}
