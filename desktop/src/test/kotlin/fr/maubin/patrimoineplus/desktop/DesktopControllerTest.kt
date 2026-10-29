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
import fr.maubin.patrimoineplus.ui.budget.encodeBudget
import fr.maubin.patrimoineplus.data.demo.kDemoPartnerId
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileId
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.demoBackupFile

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
		val vFolder = fTemp.newFolder("Synchro").toPath()							// dossier des données
		controller().openFolder(vFolder)
		val vNext = controller()												// lancement suivant
		assertEquals(Source.Folder(vFolder), vNext.workspace.value.source)
		assertEquals(LoadStatus.Waiting, vNext.workspace.value.status)
	}

	/*
		Budget et objectifs d'avant le format 8 (fichiers à côté des réglages)
		repris dans le fichier d'échange : budget de la vue Famille (celui de
		« Moi » ignoré), objectifs de chaque vue ; la démonstration les relit à
		chaque ouverture ; un dossier des données les reprend une fois, écrits
		dans le fichier, puis les fichiers d'avant sont écartés (renommés). Le
		budget est le même dans toutes les vues, les objectifs changent.
	*/
	@Test
	fun legacyPlansMovedToExchangeFile()
	{
		val vDir    = fTemp.root.toPath()														// dossier des réglages
		java.nio.file.Files.writeString(vDir.resolve("goals.json"), encodeGoals(Goals(listOf(Goal("ancien", GoalKind.TASK, "Objectif d'avant")))))	// objectifs de « Moi »
		java.nio.file.Files.writeString(vDir.resolve("budget-famille.json"), encodeBudget(Budget(listOf(BudgetLine("f1", BudgetSection.INCOME, "Salaires", 500_000)))))	// budget de la famille
		java.nio.file.Files.writeString(vDir.resolve("budget.json"), encodeBudget(Budget(listOf(BudgetLine("m1", BudgetSection.INCOME, "Salaire", 300_000)))))	// budget de « Moi », ignoré
		val vApp    = controller()																	// premier lancement
		vApp.openDemo()
		assertEquals(listOf("ancien"), vApp.goals.value.goals.map { it.id })
		assertEquals(listOf("f1"), vApp.budget.value.lines.map { it.id })
		vApp.setView(kDemoPartnerId)
		assertEquals(listOf("f1"), vApp.budget.value.lines.map { it.id })						// budget commun
		assertTrue(vApp.goals.value.goals.isEmpty())											// objectifs propres à la vue
		vApp.setGoals(Goals(listOf(Goal("alex", GoalKind.TASK, "Objectif d'Alex"))))
		assertEquals(listOf("alex"), vApp.goals.value.goals.map { it.id })
		vApp.setView(kDefaultProfileId)
		assertEquals(listOf("ancien"), vApp.goals.value.goals.map { it.id })
		val vFolder = fTemp.newFolder("Synchro").toPath()											// dossier des données, avec son fichier
		java.nio.file.Files.newOutputStream(vFolder.resolve(kSyncFileName)).use { writeBackupXlsx(demoBackupFile(), it) }
		vApp.openFolder(vFolder)
		assertEquals(listOf("ancien"), vApp.goals.value.goals.map { it.id })
		assertEquals(listOf("f1"), vApp.budget.value.lines.map { it.id })
		assertTrue(vApp.flush())
		val vOnDisk = java.nio.file.Files.newInputStream(vFolder.resolve(kSyncFileName)).use { readBackupXlsx(it) } as BackupRead.Valid	// fichier écrit
		assertEquals(listOf("f1"), vOnDisk.file.budget.map { it.id })
		assertEquals(fr.maubin.patrimoineplus.data.backup.kBackupVersion, vOnDisk.file.version)
		assertTrue(java.nio.file.Files.exists(vDir.resolve("budget-famille.json.avant-format-8")))
		assertTrue(java.nio.file.Files.exists(vDir.resolve("budget.json.avant-format-8")))		// gardé sur le disque, inutilisé
		vApp.setBudget(Budget())
		assertTrue(vApp.flush())
		val vNext   = controller()																	// lancement suivant : rien n'est repris de nouveau
		assertTrue(vNext.budget.value.lines.isEmpty())
		assertEquals(listOf("ancien"), vNext.goals.value.goals.map { it.id })
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
		val vNext  = controller().settings.value											// lancement suivant
		assertEquals(DesktopSettings(Source.None, ThemeMode.DARK, hidden = true, deviceId = vFirst.settings.value.deviceId), vNext)
		assertTrue(vNext.deviceId.isNotEmpty())											// identifiant de ce PC tiré une fois, gardé
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
