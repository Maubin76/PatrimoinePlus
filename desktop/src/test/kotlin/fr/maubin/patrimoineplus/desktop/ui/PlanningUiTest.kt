package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Saisie rapide, bilan annuel et projection dans l'interface du PC (sans
	écran) ; captures pour relecture.
*/
class PlanningUiTest
{
	@get:Rule
	val fCompose = createComposeRule()											// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()													// réglages et fichiers jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Contrôleur en démonstration, en clair.
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
		val vScene = ImageComposeScene(width = 1440, height = 1500, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		vScene.render()
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Saisie rapide : une dépense (catégorie proposée d'après le libellé),
		Entrée pour une nouvelle ligne, un revenu avec « + », Ctrl+Entrée
		enregistre tout ; une ligne fausse bloque l'enregistrement.
	*/
	@Test
	fun batchEntry()
	{
		val vApp = demo()																// démonstration
		vApp.show(Screen.OPERATIONS)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		val vBefore = vApp.workspace.value.data.transactions.size						// opérations avant
		val vFields = { fCompose.onAllNodes(hasSetTextAction()) }						// champs de la grille
		vFields()[1].performTextInput("Courses")
		vFields()[2].performTextInput("12,30")
		vFields()[2].performKeyInput { pressKey(Key.Enter) }
		fCompose.waitForIdle()
		assertEquals(2, vApp.batch.value.size)
		vFields()[3].performTextReplacement("32/10/2026")
		vFields()[4].performTextInput("Salaire")
		vFields()[5].performTextInput("+100")
		vFields()[5].performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
		fCompose.waitForIdle()
		assertEquals(vBefore, vApp.workspace.value.data.transactions.size)			// date fausse : rien n'est enregistré
		fCompose.onNodeWithText("Date illisible (JJ/MM/AAAA).").assertExists()
		vFields()[3].performTextReplacement("10/10/2026")
		capture(vApp, "14-saisie-rapide")
		fCompose.onNodeWithText("Enregistrer").performClick()
		fCompose.waitForIdle()
		val vAfter = vApp.workspace.value.data.transactions								// opérations après
		assertEquals(vBefore + 2, vAfter.size)
		val vFood  = vApp.workspace.value.data.categories.single { it.name == "Alimentation" }.id	// catégorie proposée
		assertTrue(vAfter.any { it.label == "Courses" && it.categoryId == vFood && it.postings.single().amountCents == -1_230L })
		assertTrue(vAfter.any { it.label == "Salaire" && it.postings.single().amountCents == 10_000L && it.date.toString() == "2026-10-10" })
		assertEquals(1, vApp.batch.value.size)
	}

	/*
		Bilan annuel : écran de l'année en cours, export en PDF valide.
	*/
	@Test
	fun annualReport()
	{
		val vApp = demo()																// démonstration
		vApp.show(Screen.REPORT)
		capture(vApp, "15-bilan")
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Bilan 2026").assertExists()
		fCompose.onNodeWithText("Exporter en PDF…").assertExists()
		val vFile = fTemp.root.toPath().resolve("bilan.pdf")							// fichier exporté
		assertNull(vApp.exportReport(2026, vFile))
		assertTrue(String(Files.readAllBytes(vFile), Charsets.ISO_8859_1).startsWith("%PDF-1.4"))
		assertTrue(vApp.notice.value!!.startsWith("Bilan 2026 enregistré"))
	}

	/*
		Projection : chiffres à l'horizon par défaut (dix ans), horizon changé
		par une puce, retour aux valeurs par défaut.
	*/
	@Test
	fun projection()
	{
		val vApp = demo()																// démonstration
		vApp.show(Screen.PROJECTION)
		capture(vApp, "16-projection")
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Dans 10 ans, central").assertExists()
		fCompose.onNodeWithText("20 ans").performClick()
		fCompose.waitForIdle()
		assertEquals(20, vApp.projection.value!!.years)
		fCompose.onNodeWithText("Dans 20 ans, central").assertExists()
		fCompose.onNodeWithText("Valeurs par défaut").performClick()
		fCompose.waitForIdle()
		assertNull(vApp.projection.value)
	}
}
