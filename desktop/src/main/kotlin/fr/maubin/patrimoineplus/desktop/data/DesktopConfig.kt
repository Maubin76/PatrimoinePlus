package fr.maubin.patrimoineplus.desktop.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
	Apparence de l'appli PC.
*/
enum class Appearance(val label: String)
{
	SYSTEM("Système"),															// suit Windows
	LIGHT("Clair"),																// noir sur blanc
	DARK("Sombre")																// blanc sur noir
}

/*
	Réglages mémorisés de l'appli PC.
	source     : source des données au prochain lancement
	appearance : apparence
	hidden     : mode discret (montants masqués)
*/
data class DesktopSettings(
	val source     : Source     = Source.None,
	val appearance : Appearance = Appearance.SYSTEM,
	val hidden     : Boolean    = false
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
			"folder" -> vProps.getProperty("folder")?.let { Source.Folder(Paths.get(it)) } ?: Source.None
			else     -> Source.None
		}																			// source mémorisée
		return DesktopSettings(
			source     = vSource,
			appearance = Appearance.entries.firstOrNull { it.name == vProps.getProperty("appearance") } ?: Appearance.SYSTEM,
			hidden     = vProps.getProperty("hidden") == "true"
		)
	}

	/*
		Enregistre les réglages.
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
		inFile.parent?.let { Files.createDirectories(it) }
		Files.newBufferedWriter(inFile, Charsets.UTF_8).use { vProps.store(it, "Réglages de PatrimoinePlus (PC)") }
	}

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
