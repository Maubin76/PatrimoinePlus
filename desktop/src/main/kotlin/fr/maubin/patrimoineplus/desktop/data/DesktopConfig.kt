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
	pies       : clés des diagrammes de répartition affichés en cercle (barre par défaut)
*/
data class DesktopSettings(
	val source     : Source    = Source.None,
	val appearance : ThemeMode = ThemeMode.SYSTEM,
	val hidden     : Boolean   = false,
	val trend      : Boolean   = false,
	val pies       : Set<String> = emptySet()
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
			trend      = vProps.getProperty("trend") == "true",
			pies       = vProps.getProperty("pies").orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
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
		vProps.setProperty("pies", inSettings.pies.sorted().joinToString(","))
		inFile.parent?.let { Files.createDirectories(it) }
		val vTemp = inFile.resolveSibling("${inFile.fileName}.tmp")				// écriture provisoire
		Files.newBufferedWriter(vTemp, Charsets.UTF_8).use { vProps.store(it, "Réglages de PatrimoinePlus (PC)") }
		Files.move(vTemp, inFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
	}

	/*
		Lit le budget prévisionnel d'une source (budget.json pour vos données,
		budget-demo.json pour la démonstration, à côté des réglages) ; vide
		s'il est absent ou illisible.
		inSource : source des données
	*/
	fun loadBudget(inSource: Source): Budget =
		readFor(inSource, "budget")?.let { runCatching { decodeBudget(it) }.getOrNull() } ?: Budget()

	/*
		Enregistre le budget prévisionnel d'une source.
		inSource : source des données
		inBudget : budget
	*/
	fun saveBudget(
		inSource : Source,
		inBudget : Budget
	) = write(fileFor(inSource, "budget"), encodeBudget(inBudget))

	/*
		Lit les objectifs d'une source (goals.json pour vos données,
		goals-demo.json pour la démonstration) ; aucun s'il est absent ou
		illisible.
		inSource : source des données
	*/
	fun loadGoals(inSource: Source): Goals =
		readFor(inSource, "goals")?.let { runCatching { decodeGoals(it) }.getOrNull() } ?: Goals()

	/*
		Enregistre les objectifs d'une source.
		inSource : source des données
		inGoals  : objectifs
	*/
	fun saveGoals(
		inSource : Source,
		inGoals  : Goals
	) = write(fileFor(inSource, "goals"), encodeGoals(inGoals))

	/*
		Fichier propre à une source, à côté des réglages : la démonstration a
		le sien (« -demo »), pour que ses comptes ne se mêlent pas à ceux de vos
		données.
		inSource : source des données
		inBase   : nom de base (« budget », « goals »)
	*/
	private fun fileFor(
		inSource : Source,
		inBase   : String
	): Path = inFile.resolveSibling(if (inSource == Source.Demo) "$inBase-demo.json" else "$inBase.json")

	/*
		Contenu du fichier d'une source ; null s'il est absent. La
		démonstration sans fichier à elle reprend le fichier commun d'avant la
		séparation (même contenu au départ ; chacun évolue ensuite à part).
		inSource : source des données
		inBase   : nom de base
	*/
	private fun readFor(
		inSource : Source,
		inBase   : String
	): String? =
		listOfNotNull(fileFor(inSource, inBase), inFile.resolveSibling("$inBase.json").takeIf { inSource == Source.Demo })
			.firstOrNull { Files.isRegularFile(it) }
			?.let { runCatching { Files.readString(it) }.getOrNull() }

	/*
		Écrit un fichier (fichier temporaire puis remplacement).
		inPath : fichier
		inText : contenu
	*/
	private fun write(
		inPath : Path,
		inText : String
	)
	{
		inPath.parent?.let { Files.createDirectories(it) }
		val vTemp = inPath.resolveSibling("${inPath.fileName}.tmp")						// écriture provisoire
		Files.writeString(vTemp, inText)
		Files.move(vTemp, inPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
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
