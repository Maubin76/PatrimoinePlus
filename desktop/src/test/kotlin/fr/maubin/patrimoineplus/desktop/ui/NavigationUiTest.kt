package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.ReportTab
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.search.SearchQuery
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
	Volet latéral du PC (sans écran) : huit entrées dans l'ordre voulu ;
	écran Opérations (saisie rapide puis récurrences), Bilan à deux onglets
	(mois par mois, bilan annuel), catégories dans les réglages ; une case
	de l'analyse ouvre la Recherche. Captures `22-*.png`.
*/
class NavigationUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

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
		val vScene = ImageComposeScene(width = 1440, height = 1500, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		vScene.render()
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Entrées du volet, pages fusionnées et passage de l'analyse à la
		recherche.
	*/
	@Test
	fun sidebarAndMergedPages()
	{
		assertEquals(listOf("Tableau de bord", "Objectifs", "Recherche", "Opérations", "Budget", "Projection", "Bilan", "Réglages"), Screen.entries.map { it.label })
		val vApp = demo()																	// démonstration
		fCompose.setContent { App(vApp) { null } }
		Screen.entries.forEach { inScreen -> fCompose.onAllNodesWithText(inScreen.label)[0].assertExists() }
		fCompose.onAllNodesWithText("Opérations")[0].performClick()
		fCompose.waitForIdle()
		assertEquals(Screen.OPERATIONS, vApp.navigation.value.screen)
		fCompose.onNodeWithText("Saisie rapide").assertExists()
		fCompose.onNodeWithText("Récurrences").assertExists()
		capture(vApp, "22-operations")
		fCompose.onAllNodesWithText("Bilan")[0].performClick()
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Bilan annuel").assertExists()
		capture(vApp, "22-bilan-annuel")
		fCompose.onNodeWithText("Mois par mois").performClick()
		fCompose.waitForIdle()
		assertEquals(ReportTab.MONTHLY, vApp.reportTab.value)
		fCompose.onNodeWithText("Par catégorie").assertExists()
		capture(vApp, "22-bilan-mois")
		vApp.openSearch(SearchQuery())
		assertEquals(Screen.SEARCH, vApp.navigation.value.screen)
		vApp.show(Screen.SETTINGS)
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Catégories").assertExists()
		fCompose.onNodeWithText("Ajouter une catégorie de dépense").assertExists()
		capture(vApp, "22-reglages")
	}

	/*
		Bascule barre ↔ cercle des répartitions : une icône dans la tuile ;
		chaque diagramme a sa forme, indépendante des autres (tableau de bord,
		titres du PEA), mémorisée dans les réglages et retrouvée au prochain
		passage ; la tuile garde sa taille (le titre « Comptes » sous elle ne
		bouge pas) ; légende présente une seule fois. Captures `23-*.png` en
		cercle.
	*/
	@Test
	fun proportionToggle()
	{
		val vApp = demo()																	// démonstration
		val vPea = vApp.workspace.value.data.accounts.single { it.name == "PEA" }.id		// compte Bourse
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Afficher en diagramme circulaire").performScrollTo()
		val vBefore = fCompose.onNodeWithText("Comptes").fetchSemanticsNode().positionInRoot	// position sous les tuiles
		fCompose.onNodeWithContentDescription("Afficher en diagramme circulaire").performScrollTo().performClick()	// tuile sous la partie visible de la fenêtre de test
		fCompose.waitForIdle()
		assertEquals(setOf("tableau-de-bord"), vApp.settings.value.pies)
		assertEquals(vBefore, fCompose.onNodeWithText("Comptes").fetchSemanticsNode().positionInRoot)
		fCompose.onNodeWithText("Immobilier").assertExists()									// légende unique (la forme cachée n'est pas lue)
		capture(vApp, "23-tableau-de-bord-cercle")
		vApp.openAccount(vPea)
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Afficher en diagramme circulaire").assertExists()	// le PEA garde sa barre
		fCompose.onNodeWithContentDescription("Afficher en diagramme circulaire").performScrollTo().performClick()
		fCompose.waitForIdle()
		assertEquals(setOf("tableau-de-bord", "titres:$vPea"), vApp.settings.value.pies)
		capture(vApp, "23-pea-cercle")
		vApp.back()
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Afficher en barre").performScrollTo().performClick()	// le tableau de bord, retrouvé en cercle, repasse en barre
		fCompose.waitForIdle()
		assertEquals(setOf("titres:$vPea"), vApp.settings.value.pies)							// le PEA reste en cercle
		assertEquals(setOf("titres:$vPea"), DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load().pies)	// mémorisé pour la prochaine fois
	}
}
