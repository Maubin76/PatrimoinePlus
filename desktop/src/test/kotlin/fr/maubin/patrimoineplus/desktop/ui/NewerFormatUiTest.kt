package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.data.backup.xlsx.Cell
import fr.maubin.patrimoineplus.data.backup.xlsx.Column
import fr.maubin.patrimoineplus.data.backup.xlsx.Sheet
import fr.maubin.patrimoineplus.data.backup.xlsx.writeXlsx
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.LoadStatus
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import fr.maubin.patrimoineplus.data.backup.kBackupVersion

/*
	Fichier d'échange écrit par une version plus récente (le téléphone a été
	mis à jour, pas le PC) : fenêtre « Mise à jour nécessaire » qui parle
	d'une différence de version entre les appareils ; « Plus tard » la ferme ;
	le fichier n'est jamais réécrit. Capture `34-format-plus-recent.png`.
*/
class NewerFormatUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages et dossier jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Dossier des données dont le fichier annonce un format plus récent.
	*/
	private fun folder(): java.nio.file.Path
	{
		val vFolder = fTemp.newFolder("Synchro").toPath()									// dossier des données
		val vFile   = Sheet("Fichier", listOf(Column("Clé", 16.0), Column("Valeur", 40.0)), listOf(
			listOf(Cell.Text("Format"), Cell.Text("patrimoineplus")),
			listOf(Cell.Text("Version"), Cell.Number(BigDecimal(kBackupVersion + 1)))
		))																				// page de signature d'un format futur
		Files.newOutputStream(vFolder.resolve(kSyncFileName)).use { writeXlsx(listOf(vFile, Sheet("Nouveauté", listOf(Column("Inconnue", 10.0)), emptyList())), it) }
		return vFolder
	}

	/*
		Fenêtre affichée, texte explicite, fermeture ; fichier intact.
	*/
	@Test
	fun newerFileAsksForUpdate()
	{
		val vFolder = folder()																// dossier
		val vBefore = Files.readAllBytes(vFolder.resolve(kSyncFileName))					// fichier avant
		val vApp    = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { setAppearance(ThemeMode.LIGHT); openFolder(vFolder) }	// PC resté au format courant
		assertEquals(kBackupVersion + 1, (vApp.workspace.value.status as LoadStatus.Invalid).newerFormat)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Mise à jour nécessaire").assertExists()
		fCompose.onNodeWithText("différence de version entre vos appareils", substring = true).assertExists()
		val vScene  = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) { App(vApp) { null } }	// capture (relecture humaine)
		listOf(0L, 500L, 1_500L).forEach { vScene.render(it * 1_000_000L) }
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/34-format-plus-recent.png").writeBytes(vScene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
		fCompose.onNodeWithText("Plus tard").performClick()
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Mise à jour nécessaire").assertDoesNotExist()
		vApp.flush()
		assertArrayEquals(vBefore, Files.readAllBytes(vFolder.resolve(kSyncFileName)))	// jamais réécrit
	}
}
