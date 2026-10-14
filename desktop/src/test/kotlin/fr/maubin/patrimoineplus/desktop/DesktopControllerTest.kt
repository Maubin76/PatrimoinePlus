package fr.maubin.patrimoineplus.desktop

import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.DesktopSettings
import fr.maubin.patrimoineplus.desktop.data.LoadStatus
import fr.maubin.patrimoineplus.desktop.data.Source
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetLine
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.goals.Goal
import fr.maubin.patrimoineplus.ui.goals.GoalKind
import fr.maubin.patrimoineplus.ui.goals.Goals
import fr.maubin.patrimoineplus.ui.goals.encodeGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/*
	Tests du contrôleur de l'appli PC : source mémorisée et rouverte,
	démonstration, dossier, fermeture, réglages, navigation.
*/
class DesktopControllerTest
{
	@get:Rule
	val fTemp = TemporaryFolder()													// dossier jetable

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Contrôleur relié à un fichier de réglages jetable.
	*/
	private fun controller(): DesktopController =
		DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null)

	/*
		Démonstration : données chargées, choix mémorisé et rouvert au lancement
		suivant ; fermer ramène à l'écran d'accueil, aussi au lancement suivant.
	*/
	@Test
	fun sourceIsRememberedAcrossLaunches()
	{
		val vFirst = controller()												// premier lancement
		assertEquals(Source.None, vFirst.workspace.value.source)
		vFirst.openDemo()
		assertEquals(LoadStatus.Demo, vFirst.workspace.value.status)
		val vSecond = controller()												// lancement suivant
		assertEquals(Source.Demo, vSecond.workspace.value.source)
		assertTrue(vSecond.workspace.value.data.accounts.isNotEmpty())
		vSecond.closeSource()
		assertEquals(Source.None, controller().workspace.value.source)
	}

	/*
		Dossier : mémorisé tel quel ; en attente tant que le fichier d'échange
		n'y est pas.
	*/
	@Test
	fun folderIsRemembered()
	{
		val vFolder = fTemp.newFolder("Synchro").toPath()							// dossier synchronisé
		controller().openFolder(vFolder)
		val vNext = controller()												// lancement suivant
		assertEquals(Source.Folder(vFolder), vNext.workspace.value.source)
		assertEquals(LoadStatus.Waiting, vNext.workspace.value.status)
	}

	/*
		Budget et objectifs propres à chaque source : ceux de la démonstration
		ne se mêlent pas à ceux de vos données, chacun est retrouvé en
		changeant de source et au lancement suivant ; la première fois, la
		démonstration reprend le fichier commun d'avant la séparation.
	*/
	@Test
	fun budgetAndGoalsPerSource()
	{
		val vDir    = fTemp.root.toPath()														// dossier des réglages
		java.nio.file.Files.writeString(vDir.resolve("goals.json"), encodeGoals(Goals(listOf(Goal("ancien", GoalKind.TASK, "Objectif d'avant")))))	// fichier commun d'avant
		val vApp    = controller()																	// premier lancement
		vApp.openDemo()
		assertEquals(listOf("ancien"), vApp.goals.value.goals.map { it.id })					// repris par la démonstration
		vApp.setGoals(Goals(listOf(Goal("demo", GoalKind.TASK, "Objectif de démo"))))
		vApp.setBudget(Budget(listOf(BudgetLine("d1", BudgetSection.INCOME, "Salaire démo", 100))))
		val vFolder = fTemp.newFolder("Synchro").toPath()											// dossier synchronisé
		vApp.openFolder(vFolder)
		assertEquals(listOf("ancien"), vApp.goals.value.goals.map { it.id })					// vos données : fichier commun intact
		assertTrue(vApp.budget.value.lines.isEmpty())
		vApp.setGoals(Goals(listOf(Goal("reel", GoalKind.TASK, "Objectif réel"))))
		vApp.openDemo()
		assertEquals(listOf("demo"), vApp.goals.value.goals.map { it.id })
		assertEquals(listOf("d1"), vApp.budget.value.lines.map { it.id })
		val vNext   = controller()																	// lancement suivant (démonstration)
		assertEquals(listOf("demo"), vNext.goals.value.goals.map { it.id })
		vNext.openFolder(vFolder)
		assertEquals(listOf("reel"), vNext.goals.value.goals.map { it.id })
	}

	/*
		Apparence et mode discret : appliqués et mémorisés.
	*/
	@Test
	fun settingsAreRemembered()
	{
		val vFirst = controller()												// premier lancement
		vFirst.setAppearance(ThemeMode.DARK)
		vFirst.toggleHidden()
		assertEquals(DesktopSettings(Source.None, ThemeMode.DARK, hidden = true), controller().settings.value)
	}

	/*
		Navigation : écran principal, vue d'un compte et son mois, retour ;
		changer de source ramène à l'accueil.
	*/
	@Test
	fun navigation()
	{
		val vApp = controller()													// contrôleur
		vApp.openDemo()
		vApp.show(Screen.SETTINGS)
		vApp.openAccount("compte")
		vApp.showMonth(YearMonth.of(2026, 8))
		assertEquals(Navigation(Screen.DASHBOARD, "compte", YearMonth.of(2026, 8)), vApp.navigation.value)
		vApp.back()
		assertEquals(Navigation(Screen.DASHBOARD), vApp.navigation.value)
		assertNull(vApp.navigation.value.accountId)
		vApp.openAccount("compte")
		vApp.openDemo()
		assertEquals(Navigation(), vApp.navigation.value)
	}
}
