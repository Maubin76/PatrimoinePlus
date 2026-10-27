package fr.maubin.patrimoineplus.desktop

import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import fr.maubin.patrimoineplus.desktop.data.Source
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import javax.imageio.ImageIO
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess
import java.nio.file.Path
import java.time.Clock
import javax.swing.JFileChooser
import javax.swing.UIManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import fr.maubin.patrimoineplus.ui.components.titleBarColors
import fr.maubin.patrimoineplus.ui.theme.isDark
import fr.maubin.patrimoineplus.desktop.update.DesktopUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/*
	Point d'entrée de l'appli PC : fenêtre unique, réglages mémorisés dans
	%APPDATA%\PatrimoinePlus, dernière source rouverte au lancement.
*/
fun main(inArgs: Array<String>)
{
	if (inArgs.firstOrNull() == "--verifier")
	{
		exitProcess(verify(inArgs.getOrNull(1)))
	}
	runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
	application {
		val vScope      = rememberCoroutineScope()									// portée de la surveillance du fichier
		val vController = remember { DesktopController(DesktopConfig.default(), Workspace(Clock.systemDefaultZone()), vScope, inUpdater = DesktopUpdater()) }	// contrôleur
		val vExit       by vController.exitRequested.collectAsState()				// fermeture pour la mise à jour
		LaunchedEffect(Unit) { vController.checkForUpdates() }
		LaunchedEffect(vExit) { if (vExit) vController.requestExit(::exitApplication) }
		Window(
			onCloseRequest    = { vController.requestExit(::exitApplication) },
			title             = "PatrimoinePlus",
			icon              = BitmapPainter(windowIcon()),
			state             = rememberWindowState(size = DpSize(1440.dp, 900.dp)),
			onPreviewKeyEvent = { inEvent ->
				when
				{
					inEvent.type != KeyEventType.KeyDown || !inEvent.isCtrlPressed || vController.workspace.value.source == Source.None -> false
					inEvent.key == Key.N -> { vController.newOperation(); true }
					inEvent.key == Key.F -> { vController.focusSearch(); true }
					else                 -> false
				}
			}
		) {
			window.minimumSize = Dimension(980, 640)
			LaunchedEffect(Unit) {
				withTimeoutOrNull(2_000) { while (!window.isShowing) delay(16) }			// fenêtre réellement affichée
				WindowFront.bring(window)													// au lancement : fenêtre au premier plan
			}
			val vSettings by vController.settings.collectAsState()					// réglages (apparence)
			val vDark     = vSettings.appearance.isDark()								// mode sombre effectif
			LaunchedEffect(vDark, vSettings.glass) {
				val (vCaption, vText) = titleBarColors(vDark, vSettings.glass)			// couleurs de la barre de titre
				TitleBar.apply(window, vCaption, vText, vDark)
			}
			App(vController, inOnPickSaveFile = ::pickSaveFile, inOnPickBankFile = ::pickBankFile, inOnPickSave = ::pickSave, inOnPickFolder = ::pickFolder)
		}
	}
}

/*
	Icône de la fenêtre (barre de titre, barre des tâches), lue dans les
	ressources de l'appli.
*/
private fun windowIcon(): ImageBitmap =
	checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("icone.png")) { "icône absente" }.use { ImageIO.read(it).toComposeImageBitmap() }

/*
	Vérification sans fenêtre (option --verifier) : lit et vérifie un fichier
	d'échange comme à l'ouverture d'un dossier, et affiche son résumé. Sert à
	contrôler l'appli empaquetée (sa JVM embarquée) sans l'afficher.
	inPath : fichier à vérifier
	Retour : code de sortie (0 si le fichier est valide)
*/
private fun verify(inPath: String?): Int
{
	if (inPath == null)
	{
		println("Usage : PatrimoinePlus --verifier <fichier patrimoineplus-donnees.xlsx>")
		return 2
	}
	return when (val vRead = Files.newInputStream(Paths.get(inPath)).use { readBackupXlsx(it) })
	{
		is BackupRead.Valid   -> { println("Fichier valide : ${vRead.file.summary} (dates en français : ${LocalDate.of(2026, 10, 12).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE))})"); 0 }
		is BackupRead.Invalid -> { println("Fichier refusé : ${vRead.message}"); 1 }
	}
}

/*
	Choix du fichier où enregistrer une copie des données (classeur) ; null si
	l'utilisateur annule.
*/
private fun pickSaveFile(): Path?
{
	val vChooser = JFileChooser().apply {
		dialogTitle  = "Enregistrer une copie des données"
		selectedFile = java.io.File("patrimoineplus-copie-${LocalDate.now()}.xlsx")
	}																			// sélecteur
	return if (vChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) vChooser.selectedFile.toPath() else null
}

/*
	Choix d'un fichier à écrire (export PDF), avec un nom proposé ; null si
	l'utilisateur annule.
	inName : nom proposé (son extension sert de filtre)
*/
private fun pickSave(inName: String): Path?
{
	val vExtension = inName.substringAfterLast('.', "")								// extension du fichier
	val vChooser   = JFileChooser().apply {
		dialogTitle  = "Enregistrer sous"
		selectedFile = java.io.File(inName)
		if (vExtension.isNotEmpty()) fileFilter = javax.swing.filechooser.FileNameExtensionFilter(vExtension.uppercase(), vExtension)
	}																			// sélecteur
	if (vChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
	val vFile = vChooser.selectedFile.toPath()										// fichier choisi
	return if (vExtension.isEmpty() || vFile.fileName.toString().endsWith(".$vExtension", ignoreCase = true)) vFile else vFile.resolveSibling("${vFile.fileName}.$vExtension")
}

/*
	Choix d'un relevé exporté par le site de la banque (CSV) ; null si
	l'utilisateur annule.
*/
private fun pickBankFile(): Path?
{
	val vChooser = JFileChooser().apply {
		dialogTitle = "Relevé exporté par la banque"
		fileFilter  = javax.swing.filechooser.FileNameExtensionFilter("Relevé de la banque (CSV)", "csv", "txt")
	}																			// sélecteur
	return if (vChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) vChooser.selectedFile.toPath() else null
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
