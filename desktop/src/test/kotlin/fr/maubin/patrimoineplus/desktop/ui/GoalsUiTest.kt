package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.goals.Goal
import fr.maubin.patrimoineplus.ui.goals.GoalKind
import fr.maubin.patrimoineplus.ui.goals.Goals
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
	Objectifs dans l'interface du PC (sans écran) : premier pas, objectifs
	évalués sur la démonstration, création par le panneau, case cochée,
	objectifs relus au lancement suivant ; captures pour relecture.
*/
class GoalsUiTest
{
	@get:Rule
	val fCompose = createComposeRule()											// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()													// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

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
		val vScene = ImageComposeScene(width = 1440, height = 1100, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		vScene.render()
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Écran vide (idées), puis objectifs variés ; création d'un objectif par
		le panneau ; objectif à cocher coché ; objectifs relus par un nouveau
		contrôleur (lancement suivant).
	*/
	@Test
	fun goalsScreen()
	{
		val vApp  = demo()																// démonstration
		val vData = vApp.workspace.value.data											// données
		vApp.show(Screen.GOALS)
		capture(vApp, "18-objectifs-vide")
		vApp.setGoals(Goals(listOf(
			Goal("1", GoalKind.WEALTH, "Patrimoine de 100 000 €", 10_000_000, deadline = "2030-12-31"),
			Goal("2", GoalKind.ACCOUNT, "Livret A plein", 2_295_000, accountId = vData.accounts.first { it.name == "Livret A" }.id, deadline = "2027-06-30"),
			Goal("3", GoalKind.TYPE, "Bourse : 20 000 €", 2_000_000, accountType = AccountType.BROKERAGE),
			Goal("4", GoalKind.MONTHLY, "500 € de côté par mois", 50_000),
			Goal("5", GoalKind.RATE, "Épargner 20 % des revenus", 200),
			Goal("6", GoalKind.MANUAL, "Apport pour un achat", 3_000_000, current = 1_200_000, deadline = "2028-09-01", note = "Avec l’épargne salariale"),
			Goal("7", GoalKind.TASK, "Ouvrir une assurance vie", deadline = "2027-03-31")
		)))
		capture(vApp, "18-objectifs")
		vApp.setAppearance(ThemeMode.DARK)
		capture(vApp, "18-objectifs-sombre")
		vApp.setAppearance(ThemeMode.LIGHT)
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Livret A plein").assertExists()
		fCompose.onNodeWithText("Marquer comme fait").performScrollTo().performClick()
		fCompose.waitForIdle()
		assertTrue(vApp.goals.value.goals.single { it.id == "7" }.done)
		fCompose.onNodeWithText("Nouvel objectif").performScrollTo().performClick()				// en haut de la page, au-dessus de la section du mois
		fCompose.waitForIdle()
		fCompose.onAllNodes(hasSetTextAction() and hasText("Intitulé")).onFirst().performTextInput("Patrimoine de 50 000 €")
		fCompose.onAllNodes(hasSetTextAction() and hasText("Cible (€)")).onFirst().performTextInput("50000")
		fCompose.onAllNodes(hasSetTextAction() and hasText("Échéance (facultative)")).onFirst().performTextInput("31/12/2027")
		File("build/captures-desktop/18-objectifs-panneau.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(fCompose.onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)	// panneau ouvert
		fCompose.onAllNodesWithText("Enregistrer").onFirst().performClick()
		fCompose.waitForIdle()
		val vNew = vApp.goals.value.goals.single { it.title == "Patrimoine de 50 000 €" }	// objectif créé
		assertEquals(GoalKind.WEALTH, vNew.kind)
		assertEquals(5_000_000L, vNew.target)
		assertEquals("2027-12-31", vNew.deadline)
		val vNext = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock)	// lancement suivant
		assertEquals(vApp.goals.value, vNext.goals.value)
	}
}
