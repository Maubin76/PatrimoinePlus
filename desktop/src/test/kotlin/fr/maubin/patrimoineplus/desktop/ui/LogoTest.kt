package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.PatrimoineLogo
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/*
	Logo « cartes de comptes » : déclinaisons sombre et clair, arrivée de la
	carte de devant, et icône du PC (icone.png) fidèle au logo dessiné.
	Captures logo-*.png ; logo-icone-1024.png sert à refaire icone.png et
	l'icône Windows.
*/
class LogoTest
{
	/*
		Rend un contenu dans une image hors écran.
		inWidth   : largeur en pixels
		inHeight  : hauteur en pixels
		inContent : contenu
	*/
	private fun render(
		inWidth   : Int,
		inHeight  : Int,
		inContent : @androidx.compose.runtime.Composable () -> Unit
	) : Image
	{
		val vScene = ImageComposeScene(width = inWidth, height = inHeight, density = Density(1f)) { inContent() }	// scène hors écran
		val vImage = vScene.render(1_000_000_000L)								// image rendue
		vScene.close()
		return vImage
	}

	/*
		Enregistre une image dans les captures.
		inImage : image
		inName  : nom du fichier, sans extension
	*/
	private fun save(
		inImage : Image,
		inName  : String
	) : File
	{
		File("build/captures-desktop").mkdirs()
		val vFile = File("build/captures-desktop/$inName.png")						// fichier écrit
		vFile.writeBytes(inImage.encodeToData(EncodedImageFormat.PNG)!!.bytes)
		return vFile
	}

	/*
		Planche des deux déclinaisons à cinq instants de la construction, et
		rendu de l'icône en grand. Au départ (comme l'écran de démarrage) : une
		carte seule, sans tuile ni carte de derrière visible ; à la fin : tuile
		pleine, coins transparents.
	*/
	@Test
	fun variants()
	{
		listOf(true to "sombre", false to "clair").forEach { (inDark, inName) ->
			save(render(5 * 220 + 60, 240) {
				Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { PatrimoineLogo(220.dp, it, inDark) }
				}
			}, "logo-$inName")
		}
		val vIcon   = ImageIO.read(save(render(1024, 1024) { PatrimoineLogo(1024.dp, 1f, true) }, "logo-icone-1024"))	// icône en grand
		val vStart  = ImageIO.read(save(render(1024, 1024) { PatrimoineLogo(1024.dp, 0f, true) }, "logo-depart"))	// départ : une carte seule
		assertEquals(0, vIcon.getRGB(2, 2) ushr 24)										// coins transparents
		assertEquals(255, vIcon.getRGB(512, 80) ushr 24)								// tuile pleine à la fin
		assertEquals(0, vStart.getRGB(512, 80) ushr 24)								// pas de tuile au départ
		assertEquals(0, vStart.getRGB(700, 320) ushr 24)								// carte de derrière invisible au départ
		assertNotEquals(0, vIcon.getRGB(700, 320) ushr 24)
		assertTrue((vStart.getRGB(512, 640) and 0xFF) > 150)							// violet de la carte de devant, dès le départ
	}

	/*
		L'icône du PC (icone.png, 256 px) reprend le logo dessiné en verre
		sombre : écart moyen de moins de 6 niveaux par canal.
	*/
	@Test
	fun desktopIconMatchesLogo()
	{
		val vDrawn = ImageIO.read(save(render(256, 256) { PatrimoineLogo(256.dp, 1f, true) }, "logo-icone-256"))	// logo dessiné
		val vShip  = ImageIO.read(checkNotNull(javaClass.classLoader.getResourceAsStream("icone.png")))	// icône livrée
		var vGap   = 0L																	// somme des écarts
		for (inY in 0 until 256) for (inX in 0 until 256)
		{
			val vA = vDrawn.getRGB(inX, inY)											// pixel dessiné
			val vB = vShip.getRGB(inX, inY)												// pixel livré
			for (inShift in listOf(0, 8, 16, 24)) vGap += abs(((vA ushr inShift) and 0xFF) - ((vB ushr inShift) and 0xFF))
		}
		assertTrue("écart moyen ${vGap / (256 * 256 * 4)}", vGap / (256 * 256 * 4) < 6)
	}
}
