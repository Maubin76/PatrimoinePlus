package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.decodeBudget
import fr.maubin.patrimoineplus.ui.budget.encodeBudget
import fr.maubin.patrimoineplus.ui.goals.Goals
import fr.maubin.patrimoineplus.ui.goals.decodeGoals
import fr.maubin.patrimoineplus.ui.goals.encodeGoals
import java.nio.file.StandardCopyOption
import java.util.Properties

/*
	Source des données de l'appli PC.
*/
sealed interface Source
{
	// Rien de choisi : écran d'accueil.
	data object None : Source

	// Données de démonstration, en mémoire, sans synchronisation.
	data object Demo : Source

	/*
		Dossier synchronisé contenant le fichier d'échange.
		path : chemin du dossier
	*/
	data class Folder(val path: Path) : Source
}

/*
	Réglages mémorisés de l'appli PC.
	source     : source des données au prochain lancement
	appearance : apparence
	hidden     : mode discret (montants masqués)
	trend      : hausses en vert, baisses en rouge (noir et blanc par défaut)
*/
data class DesktopSettings(
	val source     : Source    = Source.None,
	val appearance : ThemeMode = ThemeMode.SYSTEM,
	val hidden     : Boolean   = false,
	val trend      : Boolean   = false
)

/*
	Fichier des réglages de l'appli PC (texte « clé=valeur »), dans le dossier
	des données d'application de l'utilisateur. Un fichier absent ou abîmé
	donne les réglages par défaut.
	inFile : chemin du fichier
*/
class DesktopConfig(private val inFile: Path)
{
	/*
		Lit les réglages.
	*/
	fun load(): DesktopSettings
	{
		if (!Files.isRegularFile(inFile))
		{
			return DesktopSettings()
		}
		val vProps = Properties()													// valeurs lues
		runCatching { Files.newBufferedReader(inFile, Charsets.UTF_8).use { vProps.load(it) } }.onFailure { return DesktopSettings() }
		val vSource = when (vProps.getProperty("source"))
		{
			"demo"   -> Source.Demo
			"folder" -> vProps.getProperty("folder")?.let { inPath -> runCatching { Source.Folder(Paths.get(inPath)) }.getOrNull() } ?: Source.None
			else     -> Source.None
		}																			// source mémorisée
		return DesktopSettings(
			source     = vSource,
			appearance = ThemeMode.entries.firstOrNull { it.name == vProps.getProperty("appearance") } ?: ThemeMode.SYSTEM,
			hidden     = vProps.getProperty("hidden") == "true",
			trend      = vProps.getProperty("trend") == "true"
		)
	}

	/*
		Enregistre les réglages (fichier temporaire puis remplacement : une
		coupure pendant l'écriture ne perd pas les réglages précédents).
		inSettings : réglages
	*/
	fun save(inSettings: DesktopSettings)
	{
		val vProps = Properties()													// valeurs à écrire
		when (val vSource = inSettings.source)
		{
			Source.None      -> vProps.setProperty("source", "none")
			Source.Demo      -> vProps.setProperty("source", "demo")
			is Source.Folder ->
			{
				vProps.setProperty("source", "folder")
				vProps.setProperty("folder", vSource.path.toString())
			}
		}
		vProps.setProperty("appearance", inSettings.appearance.name)
		vProps.setProperty("hidden", inSettings.hidden.toString())
		vProps.setProperty("trend", inSettings.trend.toString())
		inFile.parent?.let { Files.createDirectories(it) }
		val vTemp = inFile.resolveSibling("${inFile.fileName}.tmp")				// écriture provisoire
		Files.newBufferedWriter(vTemp, Charsets.UTF_8).use { vProps.store(it, "Réglages de PatrimoinePlus (PC)") }
		Files.move(vTemp, inFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
	}

	/*
		Lit le budget prévisionnel (fichier budget.json à côté des réglages) ;
		vide s'il est absent ou illisible.
	*/
	fun loadBudget(): Budget =
		budgetFile().takeIf { Files.isRegularFile(it) }?.let { inPath -> runCatching { decodeBudget(Files.readString(inPath)) }.getOrNull() } ?: Budget()

	/*
		Enregistre le budget prévisionnel (fichier temporaire puis remplacement).
		inBudget : budget
	*/
	fun saveBudget(inBudget: Budget)
	{
		val vFile = budgetFile()														// fichier du budget
		vFile.parent?.let { Files.createDirectories(it) }
		val vTemp = vFile.resolveSibling("budget.json.tmp")								// écriture provisoire
		Files.writeString(vTemp, encodeBudget(inBudget))
		Files.move(vTemp, vFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
	}

	/*
		Fichier du budget, à côté de celui des réglages.
	*/
	private fun budgetFile(): Path = inFile.resolveSibling("budget.json")

	/*
		Lit les objectifs (fichier goals.json à côté des réglages) ; aucun s'il
		est absent ou illisible.
	*/
	fun loadGoals(): Goals =
		goalsFile().takeIf { Files.isRegularFile(it) }?.let { inPath -> runCatching { decodeGoals(Files.readString(inPath)) }.getOrNull() } ?: Goals()

	/*
		Enregistre les objectifs (fichier temporaire puis remplacement).
		inGoals : objectifs
	*/
	fun saveGoals(inGoals: Goals)
	{
		val vFile = goalsFile()															// fichier des objectifs
		vFile.parent?.let { Files.createDirectories(it) }
		val vTemp = vFile.resolveSibling("goals.json.tmp")								// écriture provisoire
		Files.writeString(vTemp, encodeGoals(inGoals))
		Files.move(vTemp, vFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
	}

	/*
		Fichier des objectifs, à côté de celui des réglages.
	*/
	private fun goalsFile(): Path = inFile.resolveSibling("goals.json")

	companion object
	{
		/*
			Fichier de réglages par défaut : %APPDATA%\PatrimoinePlus sous
			Windows, ~/.patrimoineplus ailleurs.
		*/
		fun default(): DesktopConfig
		{
			val vAppData = System.getenv("APPDATA")									// données d'application (Windows)
			val vFolder  = if (vAppData != null) Paths.get(vAppData, "PatrimoinePlus") else Paths.get(System.getProperty("user.home"), ".patrimoineplus")	// dossier des réglages
			return DesktopConfig(vFolder.resolve("desktop.properties"))
		}
	}
}
