package fr.maubin.patrimoineplus.desktop.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.model.CustomTheme
import fr.maubin.patrimoineplus.ui.budget.CashFlowDiagram
import fr.maubin.patrimoineplus.ui.components.AppGround
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.theme.DeskTheme
import fr.maubin.patrimoineplus.ui.theme.Palette
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path

private const val kExportWidth = 1200											// largeur de l'image, en dp
const val kExportScale = 2f														// pixels par dp de l'image exportée (nette à l'impression)

/*
	Apparence de l'image exportée, choisie dans la prévisualisation.
	dark    : mode sombre
	theme   : thème de couleurs (Monochrome, Minimal, Coloré)
	glass   : effet verre (fond de formes floues, panneau translucide)
	palette : thème personnalisé enregistré (traité comme le thème Coloré) ; null : palette par défaut
	hidden  : mode discret (montants masqués)
*/
data class SankeyStyle(
	val dark    : Boolean,
	val theme   : ColorTheme,
	val glass   : Boolean,
	val palette : CustomTheme?,
	val hidden  : Boolean
)

/*
	Contenu de l'image : fond de l'appli (pastel, ou formes floues de l'effet
	verre) et tuile du schéma avec son titre, à largeur fixe.
	inDiagram     : diagramme
	inCenterTitle : nom du nœud du budget
	inHeading     : titre de la tuile
	inStyle       : apparence
	inOnHeight    : hauteur du contenu, en pixels
*/
@Composable
private fun ExportContent(
	inDiagram     : CashFlowDiagram,
	inCenterTitle : String,
	inHeading     : String,
	inStyle       : SankeyStyle,
	inOnHeight    : (Int) -> Unit
)
{
	DeskTheme(inDarkTheme = inStyle.dark, inColorTheme = inStyle.theme, inGlass = inStyle.glass, inPalette = inStyle.palette?.let { Palette.of(it.colors) }) {
		CompositionLocalProvider(LocalAmountsHidden provides inStyle.hidden) {
			Box(Modifier.width(kExportWidth.dp).onSizeChanged { inOnHeight(it.height) }) {
				Box(Modifier.matchParentSize()) { AppGround(inStyle.dark, inStyle.glass) }
				Box(Modifier.padding(32.dp)) {
					Tile(inTitle = inHeading, inTitleStyle = MaterialTheme.typography.headlineSmall) { SankeyDiagram(inDiagram, inCenterTitle) }
				}
			}
		}
	}
}

/*
	Rend l'image du schéma hors écran, à largeur fixe quelle que soit la
	taille de la fenêtre : une première passe mesure la hauteur, la seconde
	rend l'image à la bonne taille.
	inDiagram     : diagramme
	inCenterTitle : nom du nœud du budget
	inHeading     : titre de la tuile
	inStyle       : apparence
	inScale       : pixels par dp (1 pour la prévisualisation, kExportScale pour le fichier)
*/
fun renderSankey(
	inDiagram     : CashFlowDiagram,
	inCenterTitle : String,
	inHeading     : String,
	inStyle       : SankeyStyle,
	inScale       : Float
): Image
{
	var vHeight = 0																		// hauteur du contenu, en pixels
	val vContent: @Composable () -> Unit = { ExportContent(inDiagram, inCenterTitle, inHeading, inStyle) { vHeight = it } }	// image
	val vWidth   = (kExportWidth * inScale).toInt()										// largeur en pixels
	val vMeasure = ImageComposeScene(vWidth, (12_000 * inScale).toInt(), Density(inScale), content = vContent)	// première passe : hauteur du contenu
	try { vMeasure.render() } finally { vMeasure.close() }
	check(vHeight > 0) { "schéma vide" }
	val vScene   = ImageComposeScene(vWidth, vHeight, Density(inScale), content = vContent)	// rendu à la bonne hauteur
	return try { listOf(0L, 1_000_000_000L).map { vScene.render(it) }.last() } finally { vScene.close() }
}

/*
	Exporte le schéma en image PNG, dans l'apparence choisie et en double
	résolution. Rend null si l'image est écrite, sinon la raison de l'échec.
	inDiagram     : diagramme
	inCenterTitle : nom du nœud du budget
	inHeading     : titre de la tuile
	inStyle       : apparence
	inPath        : fichier à écrire
*/
fun exportSankeyPng(
	inDiagram     : CashFlowDiagram,
	inCenterTitle : String,
	inHeading     : String,
	inStyle       : SankeyStyle,
	inPath        : Path
): String? =
	runCatching {
		val vData = renderSankey(inDiagram, inCenterTitle, inHeading, inStyle, kExportScale).encodeToData(EncodedImageFormat.PNG) ?: error("encodage PNG impossible")	// image encodée
		inPath.parent?.let { Files.createDirectories(it) }
		Files.write(inPath, vData.bytes)
	}.exceptionOrNull()?.let { it.message ?: "erreur inconnue" }
