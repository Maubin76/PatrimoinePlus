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
import androidx.compose.ui.window.WindowPlacement
import fr.maubin.patrimoineplus.desktop.update.appVersion
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.toDataSet
import fr.maubin.patrimoineplus.ui.dashboard.buildDashboard
import fr.maubin.patrimoineplus.data.market.YahooQuoteSource
import androidx.compose.ui.window.Tray
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
	if (inArgs.firstOrNull() == "--autotest")
	{
		exitProcess(selfTest())
	}
	runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
	application {
		val vScope      = rememberCoroutineScope()									// portée de la surveillance du fichier
		val vController = remember { DesktopController(DesktopConfig.default(), Workspace(Clock.systemDefaultZone()), vScope, inUpdater = DesktopUpdater(), inQuotes = YahooQuoteSource()) }	// contrôleur
		val vExit       by vController.exitRequested.collectAsState()				// fermeture pour la mise à jour
		LaunchedEffect(Unit) { vController.checkForUpdates() }
		LaunchedEffect(vExit) { if (vExit) vController.requestExit(::exitApplication) }
		val vSet        by vController.settings.collectAsState()					// réglages (zone de notification)
		var vVisible    by remember { mutableStateOf(!(inArgs.contains(kStartupArg) && vController.settings.value.tray)) }	// fenêtre affichée (au démarrage avec Windows : dans la zone de notification)
		if (vSet.tray)
		{
			Tray(
				icon    = BitmapPainter(windowIcon()),
				tooltip = "PatrimoinePlus",
				onAction = { vVisible = true },
				menu    = {
					Item("Ouvrir PatrimoinePlus", onClick = { vVisible = true })
					Item("Quitter", onClick = { vVisible = true; vController.requestExit(::exitApplication) })
				}
			)
		}
		Window(
			visible           = vVisible,
			onCloseRequest    = { if (vSet.tray) vVisible = false else vController.requestExit(::exitApplication) },
			title             = "PatrimoinePlus",
			icon              = BitmapPainter(windowIcon()),
			state             = rememberWindowState(placement = WindowPlacement.Maximized, size = DpSize(1440.dp, 900.dp)),	// agrandie à chaque lancement ; 1440 × 900 une fois restaurée
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
			LaunchedEffect(vVisible) {
				if (!vVisible) return@LaunchedEffect											// dans la zone de notification : rien à montrer
				withTimeoutOrNull(2_000) { while (!window.isShowing) delay(16) }			// fenêtre réellement affichée
				if (window.isShowing) WindowFront.bring(window)								// au lancement et à la réouverture : fenêtre au premier plan
			}
			val vSettings by vController.settings.collectAsState()					// réglages (apparence)
			val vDark     = vSettings.appearance.isDark()								// mode sombre effectif
			LaunchedEffect(vDark, vSettings.glass) {
				val (vCaption, vText) = titleBarColors(vDark, vSettings.glass)			// couleurs de la barre de titre
				TitleBar.apply(window, vCaption, vText, vDark)
			}
			App(vController, inOnPickSaveFile = ::pickSaveFile, inOnPickBankFile = ::pickBankFile, inOnPickSave = ::pickSave, inOnPickFolder = ::pickFolder, inWindowShown = { window.isShowing })
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
	Autocontrôle sans fenêtre (option --autotest), pour la CI sur l'appli
	empaquetée (sa JVM embarquée, ses modules, ses ressources) : version
	embarquée lue, démonstration écrite en classeur puis relue à l'identique,
	tableau de bord calculé, dates en français, icône présente.
	Retour : code de sortie (0 si tout va bien)
*/
private fun selfTest(): Int = runCatching {
	val vVersion = checkNotNull(appVersion()) { "version embarquée absente" }		// version.properties
	val vFile    = Workspace(Clock.systemDefaultZone()).apply { open(Source.Demo) }.file	// démonstration complète
	val vTemp    = Files.createTempFile("patrimoineplus-autotest-", ".xlsx")		// classeur écrit
	Files.newOutputStream(vTemp).use { writeBackupXlsx(vFile, it) }
	val vRead    = Files.newInputStream(vTemp).use { readBackupXlsx(it) }			// classeur relu
	Files.deleteIfExists(vTemp)
	check(vRead is BackupRead.Valid) { "classeur refusé : ${(vRead as BackupRead.Invalid).message}" }
	check(vRead.file.accounts.size == vFile.accounts.size && vRead.file.transactions.size == vFile.transactions.size) { "classeur relu différent" }
	val vToday   = LocalDate.now()																		// date de calcul
	val vBoard   = buildDashboard(vRead.file.toDataSet(), vToday)						// tableau de bord
	check(vBoard.tiles.isNotEmpty()) { "tableau de bord vide" }
	val vMonth   = LocalDate.of(2026, 10, 12).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE))	// date en français
	check(vMonth == "12 octobre 2026") { "dates pas en français : $vMonth" }
	checkNotNull(Thread.currentThread().contextClassLoader.getResource("icone.png")) { "icône absente" }
	println("Autotest réussi : version $vVersion, ${vRead.file.accounts.size} comptes et ${vRead.file.transactions.size} opérations relus, ${vBoard.tiles.size} tuiles, $vMonth.")
	0
}.getOrElse { inError ->
	println("Autotest échoué : ${inError.message}")
	1
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
		dialogTitle       = "Dossier des données PatrimoinePlus"
		fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
		isAcceptAllFileFilterUsed = false
	}																			// sélecteur
	return if (vChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) vChooser.selectedFile.toPath() else null
}
