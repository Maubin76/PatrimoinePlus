package fr.maubin.patrimoineplus.desktop

import androidx.compose.ui.graphics.Color
import fr.maubin.patrimoineplus.ui.components.titleBarColors
import org.junit.Assert.assertEquals
import org.junit.Test

/*
	Barre de titre : couleurs converties au format de Windows (0x00BBGGRR) ;
	fond de la barre pris en haut du fond de l'appli selon le thème ; sans
	effet hors Windows ou sur une fenêtre non affichée.
*/
class TitleBarTest
{
	/*
		Conversion et choix des couleurs.
	*/
	@Test
	fun colors()
	{
		assertEquals(0x00F46A79, TitleBar.colorRef(Color(0xFF796AF4)))
		assertEquals(0x001C1616, TitleBar.colorRef(Color(0xFF16161C)))
		assertEquals(Color(0xFFE6E0FA), titleBarColors(inDark = false, inGlass = false).first)
		assertEquals(Color(0xFF07070B), titleBarColors(inDark = true, inGlass = true).first)
		assertEquals(Color(0xFFF3F3F7), titleBarColors(inDark = true, inGlass = true).second)
		TitleBar.apply(java.awt.Frame(), Color.Black, Color.White, inDark = true)		// fenêtre non affichée : rien
	}
}
