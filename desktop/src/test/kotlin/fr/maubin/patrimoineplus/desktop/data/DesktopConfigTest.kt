package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/*
	Tests des réglages de l'appli PC : aller-retour, valeurs par défaut,
	fichier abîmé.
*/
class DesktopConfigTest
{
	@get:Rule
	val fTemp = TemporaryFolder()													// dossier jetable

	/*
		Chaque source, l'apparence, le mode discret et les couleurs des hausses
		et baisses sont relus tels quels ;
		le dossier des réglages est créé s'il manque.
	*/
	@Test
	fun roundTrip()
	{
		val vConfig = DesktopConfig(fTemp.root.toPath().resolve("sous-dossier/desktop.properties"))	// réglages
		val vFolder = fTemp.newFolder("Mon dossier é").toPath()							// dossier avec espace et accent
		listOf(
			DesktopSettings(Source.Folder(vFolder), ThemeMode.DARK, hidden = true),
			DesktopSettings(Source.Demo, ThemeMode.LIGHT, hidden = false, colors = fr.maubin.patrimoineplus.ui.theme.ColorTheme.MINIMAL),
			DesktopSettings(Source.Demo, ThemeMode.DARK, hidden = false, colors = fr.maubin.patrimoineplus.ui.theme.ColorTheme.COLORFUL),
			DesktopSettings(Source.None, ThemeMode.SYSTEM, hidden = true)
		).forEach { inSettings ->
			vConfig.save(inSettings)
			assertEquals(inSettings, vConfig.load())
		}
	}

	/*
		Fichier absent ou abîmé, chemin impossible : réglages par défaut (écran
		d'accueil), sans plantage ; l'écriture ne laisse pas de fichier provisoire.
	*/
	@Test
	fun defaultsWhenMissingOrDamaged()
	{
		val vFile = fTemp.root.toPath().resolve("desktop.properties")				// fichier de réglages
		assertEquals(DesktopSettings(), DesktopConfig(vFile).load())
		Files.writeString(vFile, "source=folder\nappearance=ROSE\n")
		assertEquals(DesktopSettings(), DesktopConfig(vFile).load())
		Files.writeString(vFile, "source=folder\nfolder=C:/dossier\\u0000invalide\n")
		assertEquals(DesktopSettings(), DesktopConfig(vFile).load())				// chemin impossible : pas de plantage
		Files.writeString(vFile, "source=demo\ntrend=true\n")
		assertEquals(fr.maubin.patrimoineplus.ui.theme.ColorTheme.MINIMAL, DesktopConfig(vFile).load().colors)	// ancien réglage vert et rouge : thème Minimal
		DesktopConfig(vFile).save(DesktopSettings(colors = fr.maubin.patrimoineplus.ui.theme.ColorTheme.COLORFUL))
		assertEquals(listOf("desktop.properties"), Files.list(fTemp.root.toPath()).use { inList -> inList.map { it.fileName.toString() }.toList() })	// pas de fichier provisoire restant
	}
}
