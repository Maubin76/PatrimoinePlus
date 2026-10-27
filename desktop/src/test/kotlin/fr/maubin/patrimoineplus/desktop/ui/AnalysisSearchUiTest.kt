package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.analysis.buildExpenseAnalysis
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
import kotlin.math.roundToLong
import fr.maubin.patrimoineplus.ui.search.searchTransactions
import fr.maubin.patrimoineplus.ui.search.SearchQuery
import androidx.compose.ui.test.assertCountEquals

/*
	Écrans Analyse et Opérations du PC (sans écran) : case du tableau qui
	ouvre ses opérations, recherche au clavier (Ctrl+F), changement groupé de
	catégorie et suppression groupée ; captures pour relecture.
*/
class AnalysisSearchUiTest
{
	@get:Rule
	val fCompose = createComposeRule()											// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()													// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Contrôleur en démonstration.
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
		val vScene = ImageComposeScene(width = 1440, height = 1700, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		vScene.render()
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Analyse : le tableau montre les dépenses par catégorie ; un clic sur
		une case ouvre l'écran Opérations filtré sur cette catégorie et ce mois.
	*/
	@Test
	fun analysisCellOpensOperations()
	{
		val vApp   = demo()															// démonstration
		vApp.show(Screen.REPORT); vApp.showReportTab(fr.maubin.patrimoineplus.desktop.ReportTab.MONTHLY)
		capture(vApp, "12-analyse")
		val vTable = buildExpenseAnalysis(vApp.workspace.value.data, vApp.today())	// tableau attendu
		val vRow   = vTable.expenses.first()											// catégorie la plus dépensière
		val vCol   = vTable.firstIndex														// premier mois suivi (colonne visible)
		val vText  = (vRow.values[vCol] / 100.0).roundToLong().toString().reversed().chunked(3).joinToString(" ").reversed()	// montant affiché
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Par catégorie").performScrollTo().assertExists()
		fCompose.onAllNodes(hasText(vText) and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)	// case cliquable (pas la ligne de total), même hors de la fenêtre de test
		fCompose.waitForIdle()
		assertEquals(Screen.SEARCH, vApp.navigation.value.screen)
		assertEquals(vRow.category.id, vApp.query.value.categoryId)
		assertEquals(vTable.months[vCol].atDay(1), vApp.query.value.from)
	}

	/*
		Opérations : Ctrl+F ouvre l'écran ; recherche « loyer » ; tout
		sélectionner ; changer la catégorie ; supprimer après confirmation.
	*/
	@Test
	fun searchAndBulkEdit()
	{
		val vApp = demo()																// démonstration
		fCompose.setContent { App(vApp) { null } }
		fCompose.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.F) } }
		fCompose.waitForIdle()
		assertEquals(Screen.SEARCH, vApp.navigation.value.screen)
		fCompose.onNode(hasSetTextAction() and hasText("Rechercher", substring = true)).performTextInput("electricite")
		fCompose.waitForIdle()
		val vRents = vApp.workspace.value.data.transactions.filter { it.label.contains("Électricité", ignoreCase = true) && !it.date.isAfter(vApp.today().plusYears(1)) }	// factures d'électricité (sans accent dans la recherche)
		assertTrue(vRents.size >= 2)
		capture(vApp, "13-operations")
		fCompose.onNodeWithText("Tout sélectionner").performClick()
		fCompose.onNodeWithText("Changer la catégorie…").performClick()
		fCompose.onAllNodesWithText("Divers").onFirst().performClick()
		fCompose.waitForIdle()
		val vDivers = vApp.workspace.value.data.categories.single { it.name == "Divers" }.id	// catégorie choisie
		assertTrue(vApp.workspace.value.data.transactions.filter { it.id in vRents.map { inRent -> inRent.id } }.all { it.categoryId == vDivers })
		val vBefore = vApp.workspace.value.data.transactions.size						// opérations avant
		fCompose.onAllNodesWithText("${vRents.size} sélectionnée(s)", substring = true).onFirst().assertExists()	// sélection gardée
		fCompose.onNodeWithText("Supprimer").performClick()
		fCompose.onAllNodesWithText("Supprimer")[1].performClick()
		fCompose.waitForIdle()
		assertEquals(vBefore - vRents.size, vApp.workspace.value.data.transactions.size)
	}

	/*
		Recherche sans critère : les 20 opérations les plus récentes, puis
		« Afficher 20 de plus » en bas de la liste ; de nouveaux critères
		repartent à 20 ; plus de bouton quand tout est affiché.
	*/
	@Test
	fun searchShowsTwentyAtATime()
	{
		val vApp   = demo().apply { show(Screen.SEARCH) }							// démonstration
		val vTotal = searchTransactions(vApp.workspace.value.data, SearchQuery()).hits.size	// opérations trouvées
		assertTrue(vTotal > 40)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("20 sur $vTotal").performScrollTo().assertExists()
		capture(vApp, "33-recherche-par-20")
		fCompose.onNodeWithText("Afficher 20 de plus").performClick()
		fCompose.waitForIdle()
		fCompose.onNodeWithText("40 sur $vTotal").performScrollTo().assertExists()
		vApp.setQuery(SearchQuery(text = "a"))
		fCompose.waitForIdle()
		val vFound = searchTransactions(vApp.workspace.value.data, SearchQuery(text = "a")).hits.size	// opérations avec un « a »
		if (vFound > 20) fCompose.onNodeWithText("20 sur $vFound").performScrollTo().assertExists()
		vApp.setQuery(SearchQuery(text = "electricite"))
		fCompose.waitForIdle()
		fCompose.onAllNodesWithText("de plus", substring = true).assertCountEquals(0)
	}
}
