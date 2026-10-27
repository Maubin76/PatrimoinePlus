package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlin.math.abs

/*
	Lancement du PC (sans écran) : d'abord le fond seul, puis le cadre, le
	rail, la barre du haut et l'écran arrivent l'un après l'autre (tableau de
	bord de la démonstration, écran d'accueil sans données). Captures
	`32-lancement-*.png`, une par instant.
*/
class LaunchUiTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock  = Clock.fixed(Instant.parse("2026-10-27T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private val kFrames = listOf(0L, 150L, 350L, 550L, 750L, 1_000L)					// instants capturés (ms)

	/*
		Rend le lancement image par image et renvoie les images.
		inApp  : contrôleur
		inName : préfixe des captures
	*/
	private fun frames(
		inApp  : DesktopController,
		inName : String
	): List<java.awt.image.BufferedImage>
	{
		val vScene = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		File("build/captures-desktop").mkdirs()
		val vImages = kFrames.map { inMillis ->
			val vFile = File("build/captures-desktop/32-lancement-$inName-${"%04d".format(inMillis)}.png")	// capture
			vFile.writeBytes(vScene.render(inMillis * 1_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
			ImageIO.read(vFile)
		}																				// images du lancement
		vScene.close()
		return vImages
	}

	/*
		Écart moyen entre deux images (0 à 255).
		inA : première image
		inB : seconde image
	*/
	private fun gap(
		inA : java.awt.image.BufferedImage,
		inB : java.awt.image.BufferedImage
	): Double
	{
		var vSum = 0L																	// somme des écarts
		for (inY in 0 until inA.height step 6) for (inX in 0 until inA.width step 6)
		{
			val vA = inA.getRGB(inX, inY)
			val vB = inB.getRGB(inX, inY)
			for (inShift in listOf(0, 8, 16)) vSum += abs(((vA ushr inShift) and 0xFF) - ((vB ushr inShift) and 0xFF))
		}
		return vSum.toDouble() / ((inA.width / 6) * (inA.height / 6) * 3)
	}

	/*
		Tableau de bord : la 1re image n'est que le fond (texte absent), la
		dernière montre tout ; les images intermédiaires changent bien.
	*/
	@Test
	fun dashboard()
	{
		val vApp    = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("a.properties")), Workspace(kClock), null, kClock).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }	// démonstration
		val vImages = frames(vApp, "tableau")												// lancement
		val vTitle  = vImages.first().getRGB(200, 90)										// zone du titre au départ
		assertTrue("titre déjà dessiné au départ", (vTitle and 0xFF) > 150)
		assertTrue("titre absent à la fin", (vImages.last().getRGB(200, 90) and 0xFF) < 150 || gap(vImages.first(), vImages.last()) > 5)
		assertTrue("fond seul pendant les 150 premières ms", gap(vImages[0], vImages[1]) < 0.2)
		vImages.zipWithNext().drop(1).dropLast(1).forEach { (inA, inB) -> assertTrue("image figée pendant le lancement", gap(inA, inB) > 0.2) }
		assertTrue("lancement pas fini à 1 s", gap(vImages[vImages.size - 2], vImages.last()) < 2)
	}

	/*
		Écran d'accueil sans données : même déroulé (logo, titre, texte, boutons).
	*/
	@Test
	fun welcome()
	{
		val vApp    = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("b.properties")), Workspace(kClock), null, kClock).apply { setAppearance(ThemeMode.DARK) }	// sans données
		val vImages = frames(vApp, "accueil")												// lancement
		assertTrue("le lancement ne change rien", gap(vImages.first(), vImages.last()) > 2)
	}
}
