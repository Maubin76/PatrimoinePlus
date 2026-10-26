package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.data.demo.kDemoPartnerId
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.domain.calc.kFamilyView
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileId
import fr.maubin.patrimoineplus.domain.calc.wealthAt
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
	Profils sur le PC (sans écran), sur la démonstration à deux profils
	(« Moi » et « Alex ») : sélecteur de vue mémorisé ; la famille est la
	somme des profils ; budget propre à chaque vue ; profils ajoutés,
	renommés, retrait refusé pour le seul propriétaire d'un compte ; parts
	du compte joint dans son panneau. Captures `28-*.png`.
*/
class ProfilesUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Démonstration ouverte en clair.
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
		Patrimoine compté de la vue affichée.
		inApp : contrôleur
	*/
	private fun wealth(inApp: DesktopController): Long =
		inApp.viewData().let { wealthAt(it.accounts, Ledger(it.transactions), inApp.today(), it.prices).totalCents }

	/*
		Vues : « Moi » par défaut, puis la famille et « Alex » choisis dans le
		sélecteur et mémorisés ; famille = Moi + Alex ; budget propre à la vue.
	*/
	@Test
	fun views()
	{
		val vApp = demo()																	// démonstration
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		assertEquals(listOf("Moi", "Alex"), vApp.workspace.value.data.profiles.map { it.name })
		assertEquals(kDefaultProfileId, vApp.viewProfile())
		val vMine = wealth(vApp)															// patrimoine de « Moi »
		vApp.setBudget(Budget(listOf(BudgetLine("1", BudgetSection.INCOME, "Salaire", 300_000))))
		fCompose.onNodeWithContentDescription("Vue : Moi").performClick()
		fCompose.onNodeWithText("Famille : tous les comptes").performClick()
		fCompose.waitForIdle()
		assertEquals(null, vApp.viewProfile())
		assertEquals(kFamilyView, DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")).load().view)
		assertTrue(vApp.budget.value.lines.isEmpty())									// budget de la famille, distinct
		val vFamily = wealth(vApp)															// patrimoine de la famille
		capture(vApp, "28-tableau-de-bord-famille")
		fCompose.onNodeWithContentDescription("Vue : Famille").performClick()
		fCompose.onNodeWithText("Alex").performClick()
		fCompose.waitForIdle()
		assertEquals(kDemoPartnerId, vApp.viewProfile())
		assertTrue(kotlin.math.abs(vFamily - (vMine + wealth(vApp))) <= 3)			// parts de 50 % arrondies au centime : un centime au plus par compte partagé (3)
		capture(vApp, "28-tableau-de-bord-alex")
		vApp.setView(kDefaultProfileId)
		assertEquals(listOf("Salaire"), vApp.budget.value.lines.map { it.name })		// budget de « Moi » retrouvé
	}

	/*
		Réglages : profil ajouté puis renommé ; retrait de « Moi » : récapitulatif
		(comptes supprimés, parts retirées), annulé sans effet. Panneau du
		compte joint : une part par profil. Retrait d'un profil sans compte :
		immédiat ; d'Alex, confirmé : sa part du compte joint disparaît.
	*/
	@Test
	fun settingsAndOwners()
	{
		val vApp = demo()																	// démonstration
		vApp.show(Screen.SETTINGS)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Nom du nouveau profil").performScrollTo().performTextInput("Sam")
		fCompose.onNodeWithText("Ajouter").performClick()
		fCompose.waitForIdle()
		assertEquals(listOf("Moi", "Alex", "Sam"), vApp.workspace.value.data.profiles.map { it.name })
		fCompose.onNodeWithContentDescription("Nom du profil Sam").performTextReplacement("Sam B.")
		fCompose.onNodeWithContentDescription("Renommer Sam").performClick()
		fCompose.waitForIdle()
		assertEquals("Sam B.", vApp.workspace.value.data.profiles.last().name)
		fCompose.onNodeWithContentDescription("Retirer Moi").performClick()
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Supprimer le profil « Moi » ?").assertExists()
		fCompose.onNodeWithText("• Compte joint : compté pour 50 % au lieu de 100 % (part de 50 % retirée)").assertExists()
		capture(vApp, "28-suppression-profil")
		fCompose.onNodeWithText("Annuler").performClick()
		fCompose.waitForIdle()
		assertEquals(3, vApp.workspace.value.data.profiles.size)
		capture(vApp, "28-reglages-profils")
		val vJoint = vApp.workspace.value.data.accounts.first { it.name == "Compte joint" }	// compte du couple
		vApp.openPanel(Panel.AccountSheet(vJoint.id))
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Part de Alex (%)").assertExists()
		fCompose.onNodeWithText("Part de Sam B. (%)").assertExists()
		capture(vApp, "28-compte-joint-parts")
		vApp.closePanel()
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Retirer Sam B.").performScrollTo().performClick()		// aucun compte : sans confirmation
		fCompose.waitForIdle()
		assertEquals(listOf("Moi", "Alex"), vApp.workspace.value.data.profiles.map { it.name })
		fCompose.onNodeWithContentDescription("Retirer Alex").performClick()
		fCompose.onNodeWithText("Supprimer le profil").performClick()
		fCompose.waitForIdle()
		assertEquals(listOf("Moi"), vApp.workspace.value.data.profiles.map { it.name })
		assertEquals(50, vApp.workspace.value.data.accounts.first { it.name == "Compte joint" }.sharePercent)
	}
}
