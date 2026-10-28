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
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.onNodeWithContentDescription
import fr.maubin.patrimoineplus.data.budgetOf
import fr.maubin.patrimoineplus.data.demo.kDemoPartnerId
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileId
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst

/*
	Budget prévisionnel dans l'interface du PC (sans écran), page de
	planification : diagramme de cashflow regroupé par thème, montants sans
	réel ni écart, ajout d'une ligne, budget enregistré et relu au lancement
	suivant ; captures pour relecture.
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
			BudgetLine("5", BudgetSection.EXPENSE, "Loisirs", 15_000, categoryId = vCat("Loisirs")),		// ancien budget : thème tiré de la catégorie
			BudgetLine("8", BudgetSection.EXPENSE, "Crédit auto", 20_000, theme = ExpenseTheme.CREDIT),
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
		fCompose.onNodeWithText("Cashflow").assertExists()
		listOf("Réel du mois", "Écart", "Prévu", "Montant", "Thème").forEach { assertTrue(it, fCompose.onAllNodesWithText(it).fetchSemanticsNodes().isEmpty()) }	// ni suivi ni en-têtes de colonnes
		ExpenseTheme.entries.forEach { fCompose.onNodeWithContentDescription("Ajouter une ligne à ${it.label}").assertExists() }	// les six thèmes, même vides, avec leur « + »
		assertTrue(fCompose.onAllNodesWithText("Ajouter une ligne").fetchSemanticsNodes().isEmpty())	// boutons réduits à « + »
		fCompose.onNodeWithContentDescription("Ajouter une ligne à Abonnements").performScrollTo().performClick()	// thème Abonnements, vide jusque-là
		fCompose.waitForIdle()
		val vNew = vApp.budget.value.lines.single { it.name.isEmpty() }					// ligne ajoutée
		File("build/captures-desktop/17-budget-ligne-vide.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(fCompose.onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)	// « 0,00 » grisé aligné sur les montants
		assertEquals(BudgetSection.EXPENSE, vNew.section)
		val vFields = fCompose.onAllNodes(hasSetTextAction())							// champs des lignes
		val vCount  = vFields.fetchSemanticsNodes().size								// nombre de champs
		assertEquals(ExpenseTheme.SUBSCRIPTIONS, vNew.theme)							// thème du « + » choisi
		vFields[vCount - 8].performTextInput("Netflix")								// nom de la nouvelle ligne (suivie de la seconde colonne : Transports, Logement et Crédit)
		vFields[vCount - 7].performTextInput("30")
		fCompose.waitForIdle()
		assertTrue(vApp.budget.value.lines.any { it.name == "Netflix" && it.amount == 3_000L && it.theme == ExpenseTheme.SUBSCRIPTIONS && it.categoryId == null })
		assertEquals(vApp.budget.value, vApp.currentFile().budgetOf())					// rangé dans les données (fichier d'échange)
	}
	/*
		Investissements par membre (démonstration à deux profils) : un groupe
		par membre avec son total et son « + », schéma « Investissements ·
		Alex » ; une ligne change de membre par son menu. Capture
		`32-budget-membres.png`.
	*/
	@Test
	fun investmentsByMember()
	{
		val vApp = demo()																	// démonstration à deux profils
		vApp.setBudget(Budget(listOf(
			BudgetLine("1", BudgetSection.INCOME, "Salaires", 520_000),
			BudgetLine("2", BudgetSection.INVESTMENT, "Livret A", 30_000),
			BudgetLine("3", BudgetSection.INVESTMENT, "PEA d'Alex", 20_000, profileId = kDemoPartnerId)
		)))
		vApp.show(Screen.BUDGET)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Commun à toute la famille").assertExists()
		fCompose.onNodeWithContentDescription("Ajouter une ligne à Alex").assertExists()
		fCompose.onNodeWithContentDescription("Cashflow", substring = true).assertTextOrDescription("Investissements · Alex")
		capture(vApp, "32-budget-membres")
		fCompose.onAllNodesWithContentDescription("Changer de membre").onFirst().performScrollTo().performClick()
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Passer à Alex").performClick()
		fCompose.waitForIdle()
		assertEquals(kDemoPartnerId, vApp.budget.value.lines.single { it.id == "2" }.profileId)
		fCompose.onNodeWithContentDescription("Ajouter une ligne à Moi").performScrollTo().performClick()
		fCompose.waitForIdle()
		assertEquals(kDefaultProfileId, vApp.budget.value.lines.last().profileId)
	}

	/*
		Vérifie qu'un nœud porte un texte dans sa description.
		inText : texte attendu
	*/
	private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTextOrDescription(inText: String)
	{
		assertTrue(fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].any { it.contains(inText) })
	}
}
