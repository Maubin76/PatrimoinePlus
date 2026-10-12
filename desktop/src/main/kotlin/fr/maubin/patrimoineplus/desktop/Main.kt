package fr.maubin.patrimoineplus.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.desktop.ui.App
import java.awt.Dimension
import java.nio.file.Path
import java.time.Clock
import javax.swing.JFileChooser
import javax.swing.UIManager

/*
	Point d'entrée de l'appli PC : fenêtre unique, réglages mémorisés dans
	%APPDATA%\PatrimoinePlus, dernière source rouverte au lancement.
*/
fun main()
{
	runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
	application {
		val vScope      = rememberCoroutineScope()									// portée de la surveillance du fichier
		val vController = remember { DesktopController(DesktopConfig.default(), Workspace(Clock.systemDefaultZone()), vScope) }	// contrôleur
		Window(
			onCloseRequest = { vController.flush(); exitApplication() },
			title          = "PatrimoinePlus",
			state          = rememberWindowState(size = DpSize(1320.dp, 860.dp))
		) {
			window.minimumSize = Dimension(980, 640)
			App(vController, ::pickFolder)
		}
	}
}

/*
	Sélecteur de dossier natif de Windows ; null si l'utilisateur annule.
*/
private fun pickFolder(): Path?
{
	val vChooser = JFileChooser().apply {
		dialogTitle       = "Dossier synchronisé avec le téléphone"
		fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
		isAcceptAllFileFilterUsed = false
	}																			// sélecteur
	return if (vChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) vChooser.selectedFile.toPath() else null
}
