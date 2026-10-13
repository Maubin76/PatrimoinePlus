package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetLine
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
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
	Budget prévisionnel dans l'interface du PC (sans écran) : diagramme de
	cashflow prévu et réel regroupé par catégorie, ajout d'une ligne, budget enregistré et relu au
	lancement suivant ; captures pour relecture.
*/
class BudgetUiTest
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
		val vScene = ImageComposeScene(width = 1440, height = 1900, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		vScene.render()
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Budget suivi sur les catégories et comptes de la démonstration : écran
		et schéma ; ligne ajoutée par l'interface ; budget relu par un nouveau
		contrôleur (lancement suivant).
	*/
	@Test
	fun budgetScreen()
	{
		val vApp  = demo()																// démonstration
		val vData = vApp.workspace.value.data											// données
		val vCat  = { inName: String -> vData.categories.single { it.name == inName }.id }	// catégorie par nom
		vApp.setBudget(Budget(listOf(
			BudgetLine("1", BudgetSection.INCOME, "Salaire", 320_000, categoryId = vCat("Salaire")),
			BudgetLine("2", BudgetSection.EXPENSE, "Loyer", 95_000, categoryId = vCat("Logement")),
			BudgetLine("3", BudgetSection.EXPENSE, "Courses", 45_000, categoryId = vCat("Alimentation")),
			BudgetLine("4", BudgetSection.EXPENSE, "Transport", 12_000, categoryId = vCat("Transport")),
			BudgetLine("5", BudgetSection.EXPENSE, "Loisirs", 15_000, categoryId = vCat("Loisirs")),
			BudgetLine("6", BudgetSection.INVESTMENT, "Livret A", 50_000, accountId = vData.accounts.first { it.name == "Livret A" }.id),
			BudgetLine("7", BudgetSection.INVESTMENT, "PEA", 40_000, accountId = vData.accounts.first { it.name == "PEA" }.id)
		)))
		vApp.show(Screen.BUDGET)
		capture(vApp, "17-budget")
		vApp.setAppearance(ThemeMode.DARK)
		capture(vApp, "17-budget-sombre")
		vApp.setAppearance(ThemeMode.LIGHT)
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Budget prévisionnel").assertExists()
		fCompose.onNodeWithText("Cashflow prévu").assertExists()
		fCompose.onAllNodesWithText("Ajouter une ligne")[1].performScrollTo().performClick()		// rubrique Dépenses
		fCompose.waitForIdle()
		val vNew = vApp.budget.value.lines.single { it.name.isEmpty() }					// ligne ajoutée
		assertEquals(BudgetSection.EXPENSE, vNew.section)
		val vFields = fCompose.onAllNodes(hasSetTextAction())							// champs des lignes
		val vCount  = vFields.fetchSemanticsNodes().size								// nombre de champs
		vFields[vCount - 6].performTextInput("Abonnements")							// nom de la nouvelle ligne (dernière des dépenses, avant les 2 lignes d'investissement)
		vFields[vCount - 5].performTextInput("30")
		fCompose.onNodeWithText("Sans catégorie").performScrollTo().performClick()		// catégorie de la nouvelle ligne
		fCompose.waitForIdle()
		fCompose.onAllNodesWithText("Loisirs").onLast().performClick()					// choix du menu (dernier nœud affiché)
		fCompose.waitForIdle()
		assertTrue(vApp.budget.value.lines.any { it.name == "Abonnements" && it.amount == 3_000L && it.categoryId == vCat("Loisirs") })
		fCompose.onAllNodesWithText("Réel du mois")[0].performScrollTo().performClick()			// puce du schéma (avant les en-têtes de colonnes)
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Cashflow réel de octobre 2026").assertExists()
		val vNext = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock)	// lancement suivant
		assertEquals(vApp.budget.value, vNext.budget.value)
	}
}
