package fr.maubin.patrimoineplus.desktop.update

import fr.maubin.patrimoineplus.data.update.GitHubReleaseSource
import fr.maubin.patrimoineplus.data.update.ReleaseSource
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.zip.ZipInputStream

const val kExeName = "PatrimoinePlus.exe"										// lanceur de l'appli portable

/*
	Version de l'appli, lue dans la ressource version.properties écrite à la
	construction ; null si elle est absente.
*/
fun appVersion(): String? =
	Thread.currentThread().contextClassLoader.getResourceAsStream("version.properties")?.use { inStream ->
		Properties().apply { load(inStream) }.getProperty("version")?.trim()?.takeIf { it.isNotEmpty() }
	}

/*
	Dossier de l'appli portable (celui de PatrimoinePlus.exe), d'après le
	lanceur jpackage ; null si l'appli n'a pas été lancée par lui
	(développement, tests).
*/
fun appFolder(): Path? =
	System.getProperty("jpackage.app-path")?.let { inPath -> runCatching { Paths.get(inPath).toAbsolutePath().parent }.getOrNull() }
		?.takeIf { Files.isRegularFile(it.resolve(kExeName)) }

/*
	Vrai si le dossier de l'appli peut être remplacé : son dossier parent
	accepte l'écriture (création du dossier neuf, renommages).
	inFolder : dossier de l'appli
*/
fun canReplace(inFolder: Path): Boolean
{
	val vParent = inFolder.parent ?: return false									// dossier qui contient l'appli
	return runCatching {
		val vProbe = Files.createTempFile(vParent, ".patrimoineplus-", ".tmp")	// fichier d'essai
		Files.delete(vProbe)
		true
	}.getOrDefault(false)
}

/*
	Décompresse le zip d'une nouvelle version à côté du dossier de l'appli,
	dans « <nom>.new », et renvoie ce dossier. Le zip contient un dossier
	PatrimoinePlus (ou directement l'appli) ; un chemin qui sortirait du
	dossier de destination est refusé. Lève IOException si le zip est
	illisible ou sans PatrimoinePlus.exe ; rien ne reste alors sur le disque.
	inZip    : zip téléchargé et vérifié
	inFolder : dossier de l'appli installée
*/
fun prepareUpdate(
	inZip    : File,
	inFolder : Path
): Path
{
	val vTarget = inFolder.resolveSibling("${inFolder.fileName}.new")				// dossier neuf
	val vRoot   = inFolder.resolveSibling("${inFolder.fileName}.unzip")			// décompression brute
	try
	{
		deleteTree(vTarget)
		deleteTree(vRoot)
		Files.createDirectories(vRoot)
		ZipInputStream(inZip.inputStream().buffered()).use { inZipStream ->
			while (true)
			{
				val vEntry = inZipStream.nextEntry ?: break							// élément du zip
				val vPath  = vRoot.resolve(vEntry.name).normalize()					// destination de l'élément
				if (!vPath.startsWith(vRoot)) throw IOException("chemin refusé dans le zip : ${vEntry.name}")
				if (vEntry.isDirectory) Files.createDirectories(vPath)
				else
				{
					vPath.parent?.let { Files.createDirectories(it) }
					Files.copy(inZipStream, vPath, StandardCopyOption.REPLACE_EXISTING)
				}
			}
		}
		val vApp = when
		{
			Files.isRegularFile(vRoot.resolve(kExeName)) -> vRoot
			else -> Files.list(vRoot).use { inList -> inList.filter { Files.isRegularFile(it.resolve(kExeName)) }.findFirst().orElse(null) }
		} ?: throw IOException("$kExeName absent du zip")							// dossier de l'appli dans le zip
		Files.move(vApp, vTarget)
		deleteTree(vRoot)
		return vTarget
	}
	catch (inError: Exception)
	{
		deleteTree(vRoot)
		deleteTree(vTarget)
		throw inError as? IOException ?: IOException(inError.message, inError)
	}
}

/*
	Supprime un dossier et son contenu ; sans effet s'il n'existe pas.
	inPath : dossier
*/
internal fun deleteTree(inPath: Path)
{
	if (!Files.exists(inPath)) return
	Files.walk(inPath).use { inWalk -> inWalk.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
}

/*
	Texte PowerShell entre apostrophes (apostrophes doublées).
	inText : texte
*/
private fun quoted(inText: String) = "'" + inText.replace("'", "''") + "'"

/*
	Script PowerShell qui remplace l'appli une fois fermée : attend la fin du
	processus, renomme le dossier actuel en « .old » et le dossier neuf à sa
	place, relance l'appli, puis supprime l'ancien dossier. Si un renommage
	échoue, l'ancien dossier est remis en place et l'ancienne version relancée.
	inPid      : processus de l'appli à attendre
	inFolder   : dossier de l'appli installée
	inNew      : dossier neuf (voir prepareUpdate)
	inRelaunch : relancer l'appli à la fin (faux pour les tests)
*/
fun swapScript(
	inPid      : Long,
	inFolder   : Path,
	inNew      : Path,
	inRelaunch : Boolean = true
): String
{
	val vOld = inFolder.resolveSibling("${inFolder.fileName}.old")					// ancien dossier, le temps du remplacement
	return """
		|# Mise à jour de PatrimoinePlus : remplacement du dossier de l'appli une fois fermée.
		|${'$'}ErrorActionPreference = 'Stop'
		|${'$'}app      = ${quoted(inFolder.toString())}
		|${'$'}new      = ${quoted(inNew.toString())}
		|${'$'}old      = ${quoted(vOld.toString())}
		|${'$'}relaunch = ${'$'}${inRelaunch}
		|function Start-App { if (${'$'}relaunch) { Start-Process -FilePath (Join-Path ${'$'}app '$kExeName') -WorkingDirectory ${'$'}app } }
		|while (Get-Process -Id $inPid -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 300 }
		|Start-Sleep -Milliseconds 500
		|if (Test-Path -LiteralPath ${'$'}old) { Remove-Item -LiteralPath ${'$'}old -Recurse -Force -ErrorAction SilentlyContinue }
		|${'$'}moved = ${'$'}false
		|for (${'$'}i = 0; ${'$'}i -lt 20 -and -not ${'$'}moved; ${'$'}i++) {
		|	try { Rename-Item -LiteralPath ${'$'}app -NewName (Split-Path ${'$'}old -Leaf); ${'$'}moved = ${'$'}true } catch { Start-Sleep -Milliseconds 500 }
		|}
		|if (-not ${'$'}moved) { Start-App; exit 1 }
		|try { Rename-Item -LiteralPath ${'$'}new -NewName (Split-Path ${'$'}app -Leaf) }
		|catch { Rename-Item -LiteralPath ${'$'}old -NewName (Split-Path ${'$'}app -Leaf); Start-App; exit 2 }
		|Start-App
		|Remove-Item -LiteralPath ${'$'}old -Recurse -Force -ErrorAction SilentlyContinue
		|exit 0
		|""".trimMargin()
}

/*
	Écrit le script de remplacement dans le dossier temporaire et le lance en
	arrière-plan (fenêtre cachée) ; il attend que l'appli se ferme.
	inFolder : dossier de l'appli installée
	inNew    : dossier neuf
*/
fun launchSwap(
	inFolder : Path,
	inNew    : Path
)
{
	val vScript = Files.createTempFile("patrimoineplus-maj-", ".ps1")				// script écrit
	Files.writeString(vScript, "\uFEFF" + swapScript(ProcessHandle.current().pid(), inFolder, inNew))
	ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", vScript.toString())
		.redirectErrorStream(true)
		.redirectOutput(ProcessBuilder.Redirect.DISCARD)
		.start()
}

/*
	Ce dont le contrôleur a besoin pour les mises à jour du PC, remplaçable
	dans les tests.
	source   : source de la dernière release
	version  : version installée ; null si inconnue (pas de vérification)
	folder   : dossier de l'appli portable ; null si inconnu (téléchargement seul)
	download : dossier où télécharger le zip
	install  : prépare le dossier neuf et lance le script de remplacement (l'appli doit ensuite se fermer)
	reveal   : montre un fichier téléchargé dans l'explorateur (téléchargement seul)
*/
class DesktopUpdater(
	val source   : ReleaseSource = GitHubReleaseSource(),
	val version  : String?       = appVersion(),
	val folder   : Path?         = appFolder()?.takeIf(::canReplace),
	val download : Path          = folder?.let { Paths.get(System.getProperty("java.io.tmpdir")) } ?: Paths.get(System.getProperty("user.home"), "Downloads"),
	val install  : (File, Path) -> Unit = { inZip, inFolder -> launchSwap(inFolder, prepareUpdate(inZip, inFolder)) },
	val reveal   : (File) -> Unit = { inFile -> runCatching { ProcessBuilder("explorer.exe", "/select,", inFile.absolutePath).start() } }
)
