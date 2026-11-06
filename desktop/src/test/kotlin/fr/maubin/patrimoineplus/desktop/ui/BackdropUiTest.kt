package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.desktop.kPhotoBackground
import fr.maubin.patrimoineplus.ui.components.Backdrop
import fr.maubin.patrimoineplus.ui.components.backdropCode
import fr.maubin.patrimoineplus.ui.components.backdropContrast
import fr.maubin.patrimoineplus.ui.components.backdropTop
import fr.maubin.patrimoineplus.ui.components.darkBackdrop
import fr.maubin.patrimoineplus.ui.components.kBackdropTints
import fr.maubin.patrimoineplus.ui.components.parseBackdropColor
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.imageio.ImageIO

/*
	Fond personnalisable du PC : teintes proposées lisibles, version sombre
	déduite, codes lus et écrits ; couleur choisie, photo copiée, floutée et
	retirée, image illisible refusée, réglages mémorisés ; captures du fond en
	couleur et en photo, en clair et en sombre (`build/captures-brume/fond-*.png`).
*/
class BackdropUiTest
{
	@get:Rule
	val fCompose = createComposeRule()											// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()													// réglages et photo jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Contrôleur de démonstration relié à des réglages jetables.
	*/
	private fun demo(): DesktopController =
		DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }

	/*
		Photo de test : dégradé chaud et froid, 1 200 × 800 (aucune image extérieure).
	*/
	private fun photo(): Path
	{
		val vImage = BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB)				// image dessinée
		vImage.createGraphics().apply {
			paint = GradientPaint(0f, 0f, java.awt.Color(0xF2, 0xA6, 0x5A), 1200f, 800f, java.awt.Color(0x3C, 0x6E, 0xB4))
			fillRect(0, 0, 1200, 800)
			color = java.awt.Color(0xFF, 0xF1, 0xB0)
			fillOval(780, 120, 260, 260)
			dispose()
		}
		val vFile = fTemp.newFile("coucher.png")										// fichier écrit
		ImageIO.write(vImage, "png", vFile)
		return vFile.toPath()
	}

	/*
		Rend l'appli en PNG.
		inApp  : contrôleur
		inName : nom du fichier
	*/
	private fun capture(
		inApp  : DesktopController,
		inName : String
	)
	{
		val vDir   = File("build/captures-brume").apply { mkdirs() }						// dossier des captures
		val vScene = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		vScene.render()
		File(vDir, "$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Calculs : teintes proposées lisibles sous l'encre ; version sombre très
		foncée ; codes lus et écrits ; haut du fond selon la couleur.
	*/
	@Test
	fun colors()
	{
		kBackdropTints.forEach { assertTrue("${it.name} : ${backdropContrast(it.color)}", backdropContrast(it.color) >= 10f) }
		kBackdropTints.forEach { assertTrue(darkBackdrop(it.color).luminance() < 0.02f) }
		assertEquals(Color(0xFFEDE6DA), parseBackdropColor(" #EDE6DA "))
		assertEquals("#EDE6DA", backdropCode(Color(0xFFEDE6DA)))
		assertNull(parseBackdropColor("#XYZ"))
		assertTrue(backdropContrast(Color(0xFF333333)) < 4.5f)
		assertEquals(Color(0xFF123456), backdropTop(false, Backdrop(), Color(0xFF123456)))
		assertTrue(backdropTop(true, Backdrop(color = Color(0xFFEDE6DA)), Color.White).luminance() < 0.05f)
	}

	/*
		Couleur choisie et mémorisée ; code illisible refusé avec un message ;
		photo copiée, prête à dessiner, puis retirée (copie supprimée) ; image
		illisible refusée ; flou et voile mémorisés.
	*/
	@Test
	fun settings()
	{
		val vApp    = demo()																// démonstration
		val vConfig = DesktopConfig(fTemp.root.toPath().resolve("desktop.properties"))	// réglages relus
		vApp.setBackgroundColor("#EBCD99")
		assertEquals("#EBCD99", vConfig.load().background)
		assertEquals(Color(0xFFEBCD99), vApp.backdrop.value.color)
		vApp.setBackgroundColor("rouge")
		assertEquals("Couleur illisible : saisissez un code comme #E0E6EB.", vApp.notice.value)
		assertEquals("#EBCD99", vConfig.load().background)
		vApp.setBackgroundPhoto(photo())
		assertEquals(kPhotoBackground, vConfig.load().background)
		assertTrue(Files.isRegularFile(vConfig.photoFile))
		assertNotNull(vApp.backdrop.value.photo)
		assertNull(vApp.backdrop.value.color)
		vApp.setBackgroundBlur(80)
		vApp.setBackgroundVeil(150)
		assertEquals(80, vConfig.load().blur)
		assertEquals(100, vConfig.load().veil)
		val vBroken = fTemp.newFile("cassee.jpg").toPath().also { Files.writeString(it, "pas une image") }	// image illisible
		vApp.setBackgroundPhoto(vBroken)
		assertEquals("Image illisible : choisissez une photo JPEG ou PNG.", vApp.notice.value)
		assertEquals(kPhotoBackground, vConfig.load().background)
		vApp.removeBackgroundPhoto()
		assertEquals("", vConfig.load().background)
		assertFalse(Files.exists(vConfig.photoFile))
		assertNull(vApp.backdrop.value.photo)
	}

	/*
		Réglages : la pastille « Sable » choisit la teinte ; captures du fond en
		couleur (clair, sombre) et en photo (clair, sombre), et de la section
		Apparence avec la photo.
	*/
	@Test
	fun captures()
	{
		val vApp = demo()																	// démonstration
		vApp.show(Screen.SETTINGS)
		fCompose.setContent { App(vApp) { null } }
		fCompose.waitForIdle()
		fCompose.onNodeWithContentDescription("Fond Sable").performScrollTo().performClick()
		fCompose.waitForIdle()
		assertEquals("#EBCD99", vApp.settings.value.background)
		vApp.show(Screen.DASHBOARD)
		capture(vApp, "fond-couleur-clair")
		vApp.setAppearance(ThemeMode.DARK)
		capture(vApp, "fond-couleur-sombre")
		vApp.setBackgroundPhoto(photo())
		capture(vApp, "fond-photo-sombre")
		vApp.setAppearance(ThemeMode.LIGHT)
		capture(vApp, "fond-photo-clair")
		vApp.show(Screen.SETTINGS)
		capture(vApp, "fond-reglages")
	}
}
