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
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileId
import fr.maubin.patrimoineplus.domain.calc.kFamilyView

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

// Assistant affiché par défaut ; coupé dans les tests (propriété patrimoineplus.assistant=off), sauf ceux qui le visent.
val kAssistantDefault = System.getProperty("patrimoineplus.assistant") != "off"

/*
	Réglages mémorisés de l'appli PC.
	source     : source des données au prochain lancement
	appearance : apparence
	hidden     : mode discret (montants masqués)
	colors     : thème de couleurs (Monochrome par défaut)
	pies       : clés des diagrammes de répartition affichés en cercle (barre par défaut)
	glass      : effet verre (désactivé par défaut)
	view       : vue affichée : "" premier profil, "*" famille, sinon identifiant du profil
	toursSeen  : visites guidées déjà vues ou passées (nom de la page)
	toursOff   : vrai si l'utilisateur a coupé les visites guidées
	palette    : thème personnalisé choisi (identifiant) ; vide : le thème de couleurs
*/
data class DesktopSettings(
	val source     : Source    = Source.None,
	val appearance : ThemeMode = ThemeMode.SYSTEM,
	val hidden     : Boolean   = false,
	val colors     : ColorTheme = ColorTheme.MONOCHROME,
	val pies       : Set<String> = emptySet(),
	val glass      : Boolean   = false,
	val view       : String    = "",
	val toursSeen  : Set<String> = emptySet(),
	val toursOff   : Boolean   = false,
	val palette    : String    = "",
	val updates    : Boolean   = true,
	val ignored    : String    = "",
	val assistant  : Boolean   = kAssistantDefault,
	val calm       : Boolean   = false,
	val quotesAt   : Long?     = null,
	val deviceId   : String    = "",
	val deviceName : String    = ""
)

/*
	Fichier des réglages de l'appli PC (texte « clé=valeur »), dans le dossier
	des données d'application de l'utilisateur. Un fichier absent ou abîmé
	donne les réglages par défaut.
	inFile : chemin du fichier
*/
class DesktopConfig(private val inFile: Path)
{
	// Dossier des réglages (appareils appairés, versions communes).
	val folder: Path get() = inFile.toAbsolutePath().parent

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
			colors     = ColorTheme.entries.firstOrNull { it.name == vProps.getProperty("theme") }
				?: if (vProps.getProperty("trend") == "true") ColorTheme.MINIMAL else ColorTheme.MONOCHROME,	// ancien réglage vert et rouge : thème Minimal
			pies       = vProps.getProperty("pies").orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
			glass      = vProps.getProperty("glass") == "true",
			view       = vProps.getProperty("view").orEmpty(),
			toursSeen  = vProps.getProperty("tours_seen").orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
			toursOff   = vProps.getProperty("tours_off") == "true",
			palette    = vProps.getProperty("palette").orEmpty(),
			updates    = vProps.getProperty("updates") != "false",
			ignored    = vProps.getProperty("update_ignored").orEmpty(),
			assistant  = vProps.getProperty("assistant")?.let { it != "false" } ?: kAssistantDefault,
			calm       = vProps.getProperty("assistant_calm") == "true",
			quotesAt   = vProps.getProperty("quotes_at")?.toLongOrNull(),
			deviceId   = vProps.getProperty("device_id").orEmpty(),
			deviceName = vProps.getProperty("device_name").orEmpty()
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
		vProps.setProperty("theme", inSettings.colors.name)
		vProps.setProperty("pies", inSettings.pies.sorted().joinToString(","))
		vProps.setProperty("glass", inSettings.glass.toString())
		vProps.setProperty("view", inSettings.view)
		vProps.setProperty("tours_seen", inSettings.toursSeen.sorted().joinToString(","))
		vProps.setProperty("tours_off", inSettings.toursOff.toString())
		vProps.setProperty("palette", inSettings.palette)
		vProps.setProperty("updates", inSettings.updates.toString())
		vProps.setProperty("update_ignored", inSettings.ignored)
		vProps.setProperty("assistant", inSettings.assistant.toString())
		vProps.setProperty("assistant_calm", inSettings.calm.toString())
		inSettings.quotesAt?.let { vProps.setProperty("quotes_at", it.toString()) }
		vProps.setProperty("device_id", inSettings.deviceId)
		vProps.setProperty("device_name", inSettings.deviceName)
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
	fun loadBudget(
		inSource  : Source,
		inProfile : String? = kDefaultProfileId
	): Budget =
		readFor(inSource, "budget" + scopeSuffix(inProfile))?.let { runCatching { decodeBudget(it) }.getOrNull() } ?: Budget()

	/*
		Lit les objectifs d'une source (goals.json pour vos données,
		goals-demo.json pour la démonstration) ; aucun s'il est absent ou
		illisible.
		inSource : source des données
	*/
	fun loadGoals(
		inSource  : Source,
		inProfile : String? = kDefaultProfileId
	): Goals =
		readFor(inSource, "goals" + scopeSuffix(inProfile))?.let { runCatching { decodeGoals(it) }.getOrNull() } ?: Goals()

	/*
		Budget et objectifs d'avant le format 8, rangés à côté des réglages,
		à reprendre dans le fichier d'échange : le budget de la vue Famille,
		sinon celui du premier profil qui en a un (les autres restent sur le
		disque, inutilisés) ; les objectifs de chaque vue. Null s'il n'y a
		rien à reprendre.
		inSource   : source des données
		inProfiles : identifiants des profils, dans l'ordre
	*/
	fun legacyPlans(
		inSource   : Source,
		inProfiles : List<String>
	): LegacyPlans?
	{
		val vScopes = listOf<String?>(null) + inProfiles.ifEmpty { listOf(kDefaultProfileId) }	// vues : famille puis profils
		val vBudget = vScopes.map { loadBudget(inSource, it) }.firstOrNull { it.lines.isNotEmpty() }	// budget repris
		val vGoals  = vScopes.associate { (it ?: kFamilyView) to loadGoals(inSource, it) }.filterValues { it != Goals() }	// objectifs repris, par vue
		val vFiles  = vScopes.flatMap { inScope -> listOf("budget", "goals").map { fileFor(inSource, it + scopeSuffix(inScope)) } }.filter { Files.isRegularFile(it) }	// fichiers d'avant
		return if (vBudget == null && vGoals.isEmpty()) null else LegacyPlans(vBudget ?: Budget(), vGoals, vFiles)
	}

	/*
		Écarte les fichiers repris (renommés « .avant-format-8 », gardés sur le
		disque) : ils ne seront plus repris.
		inPlans : budget et objectifs repris
	*/
	fun retireLegacyPlans(inPlans: LegacyPlans)
	{
		inPlans.files.forEach { inPath -> runCatching { Files.move(inPath, inPath.resolveSibling("${inPath.fileName}.avant-format-8"), StandardCopyOption.REPLACE_EXISTING) } }
	}

	/*
		Suffixe des fichiers d'une vue : aucun pour le profil « Moi » (fichiers
		d'avant les profils), « -famille » pour la famille, sinon l'identifiant
		du profil (caractères sûrs seulement).
		inProfile : profil ; null pour la famille
	*/
	private fun scopeSuffix(inProfile: String?): String =
		when (inProfile)
		{
			null              -> "-famille"
			kDefaultProfileId -> ""
			else              -> "-" + inProfile.replace(Regex("[^A-Za-z0-9-]"), "_")
		}

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

/*
	Budget et objectifs d'avant le format 8, à reprendre dans le fichier d'échange.
	budget : budget de la famille
	goals  : objectifs par vue (identifiant du profil, ou kFamilyView)
	files  : fichiers lus
*/
data class LegacyPlans(
	val budget : Budget,
	val goals  : Map<String, Goals>,
	val files  : List<Path>
)
