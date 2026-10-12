package fr.maubin.patrimoineplus.desktop

import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.DesktopSettings
import fr.maubin.patrimoineplus.desktop.data.LoadStatus
import fr.maubin.patrimoineplus.desktop.data.Source
import fr.maubin.patrimoineplus.desktop.data.Workspace
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
		vApp.show(Screen.CATEGORIES)
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
