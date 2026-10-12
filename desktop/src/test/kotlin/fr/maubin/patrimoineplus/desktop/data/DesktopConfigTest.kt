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
		Chaque source, l'apparence et le mode discret sont relus tels quels ;
		le dossier des réglages est créé s'il manque.
	*/
	@Test
	fun roundTrip()
	{
		val vConfig = DesktopConfig(fTemp.root.toPath().resolve("sous-dossier/desktop.properties"))	// réglages
		val vFolder = fTemp.newFolder("Mon dossier é").toPath()							// dossier avec espace et accent
		listOf(
			DesktopSettings(Source.Folder(vFolder), ThemeMode.DARK, hidden = true),
			DesktopSettings(Source.Demo, ThemeMode.LIGHT, hidden = false),
			DesktopSettings(Source.None, ThemeMode.SYSTEM, hidden = true)
		).forEach { inSettings ->
			vConfig.save(inSettings)
			assertEquals(inSettings, vConfig.load())
		}
	}

	/*
		Fichier absent ou abîmé : réglages par défaut (écran d'accueil).
	*/
	@Test
	fun defaultsWhenMissingOrDamaged()
	{
		val vFile = fTemp.root.toPath().resolve("desktop.properties")				// fichier de réglages
		assertEquals(DesktopSettings(), DesktopConfig(vFile).load())
		Files.writeString(vFile, "source=folder\nappearance=ROSE\n")
		assertEquals(DesktopSettings(), DesktopConfig(vFile).load())
	}
}
