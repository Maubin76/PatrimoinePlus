package fr.maubin.patrimoineplus.desktop.ui

import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
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
import androidx.compose.ui.test.onNodeWithContentDescription

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
		Parcours : écran d'accueil, démonstration, tableau de bord (patrimoine,
		répartition, tuiles des comptes, dernières opérations), tuile d'un
		livret qui ouvre sa vue globale, réglages, fermer : retour à l'écran
		d'accueil.
	*/
	@Test
	fun demoWalkthrough()
	{
		val vApp = controller()													// contrôleur
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Essayer avec la démonstration").performClick()
		fCompose.onNodeWithText("Patrimoine net").assertExists()													// démonstration avec crédits : net, puis brut et crédits
		fCompose.onNodeWithText("Brut ").assertExists()
		fCompose.onNodeWithText("Répartition").assertExists()
		fCompose.onNodeWithText("Démonstration : données fictives, modifiables, jamais enregistrées.").assertExists()
		fCompose.onNodeWithText("Comptes").assertExists()
		fCompose.onNodeWithText("Dernières opérations").assertExists()
		fCompose.onNodeWithText("Livret A").performScrollTo().performClick()
		fCompose.onNodeWithText("Solde au jour").assertExists()
		fCompose.onNodeWithContentDescription("Réglages").performClick()					// rail d'icônes : nom de l'écran en description
		fCompose.onNodeWithText("Fermer").performClick()
		fCompose.onNodeWithText("Choisir le dossier des données…").assertExists()
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
			val vScene = ImageComposeScene(width = 1440, height = 2400, density = Density(1f), content = inContent)	// scène hors écran
			vScene.render()
			val vImage = vScene.render(1_000_000_000L)							// image après une seconde (animations terminées)
			File(vDir, "$inName.png").writeBytes(vImage.encodeToData(EncodedImageFormat.PNG)!!.bytes)
			vScene.close()
		}
		capture("01-bienvenue") { App(controller()) { null } }
		val vApp = controller().apply { openDemo(); setAppearance(ThemeMode.LIGHT) }	// démonstration, en clair
		capture("02-tableau-de-bord") { App(vApp) { null } }
		vApp.openAccount(vApp.workspace.value.data.accounts.first { it.name == "Compte courant" }.id)
		capture("03-compte-courant") { App(vApp) { null } }
		vApp.openAccount(vApp.workspace.value.data.accounts.first { it.name == "PEA" }.id)
		capture("04-compte-bourse") { App(vApp) { null } }
		vApp.openAccount(vApp.workspace.value.data.accounts.first { it.name == "Livret A" }.id)
		vApp.setAppearance(ThemeMode.DARK)
		capture("05-livret-sombre") { App(vApp) { null } }
		vApp.back()
		vApp.setColorTheme(fr.maubin.patrimoineplus.ui.theme.ColorTheme.MINIMAL)
		capture("06-tableau-de-bord-sombre-couleurs") { App(vApp) { null } }
		vApp.setColorTheme(fr.maubin.patrimoineplus.ui.theme.ColorTheme.COLORFUL)
		capture("06-tableau-de-bord-sombre-colore") { App(vApp) { null } }
		vApp.setColorTheme(fr.maubin.patrimoineplus.ui.theme.ColorTheme.MONOCHROME)
		vApp.show(Screen.SETTINGS)
		capture("07-reglages-sombre") { App(vApp) { null } }
		vApp.setAppearance(ThemeMode.LIGHT)
		vApp.openAccount(vApp.workspace.value.data.accounts.first { it.name == "Compte courant" }.id)
		vApp.newOperation()
		capture("08-saisie-panneau") { App(vApp) { null } }
		vApp.closePanel()
		vApp.show(Screen.SETTINGS)
		capture("09-categories") { App(vApp) { null } }
		vApp.openPanel(fr.maubin.patrimoineplus.desktop.Panel.AccountSheet(vApp.workspace.value.data.accounts.first { it.name == "PEA" }.id))
		capture("10-fiche-compte") { App(vApp) { null } }
	}
}
