package fr.maubin.patrimoineplus.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.budget.CashFlowDiagram
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.theme.LocalColorTheme
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

private const val kExportWidth = 1200											// largeur de l'image, en dp
private const val kExportScale = 2f												// pixels par dp (image nette à l'impression)

/*
	Apparence de l'appli reprise dans l'image exportée.
	colors     : couleurs de l'appli (fond de l'image : couleur de surface, opaque)
	typography : polices
	theme      : thème de couleurs (teintes du schéma)
	hidden     : mode discret (montants masqués)
*/
data class SankeyLook(
	val colors     : ColorScheme,
	val typography : Typography,
	val theme      : ColorTheme,
	val hidden     : Boolean
)

/*
	Apparence courante de l'appli, pour l'image exportée.
*/
@Composable
fun currentSankeyLook(): SankeyLook =
	SankeyLook(MaterialTheme.colorScheme, MaterialTheme.typography, LocalColorTheme.current, LocalAmountsHidden.current)

/*
	Exporte un diagramme de cashflow en image PNG : titre puis schéma, sur un
	fond opaque aux couleurs de l'appli (effet verre sans transparence),
	rendu hors écran à une largeur fixe et en double résolution, quelle que
	soit la taille de la fenêtre. Rend null si l'image est écrite, sinon la
	raison de l'échec.
	inDiagram     : diagramme
	inCenterTitle : nom du nœud du budget
	inHeading     : titre de l'image
	inLook        : apparence de l'appli
	inPath        : fichier à écrire
*/
fun exportSankeyPng(
	inDiagram     : CashFlowDiagram,
	inCenterTitle : String,
	inHeading     : String,
	inLook        : SankeyLook,
	inPath        : Path
): String? =
	runCatching {
		var vHeight = 0																	// hauteur du contenu, en pixels
		val vContent: @Composable () -> Unit = {
			MaterialTheme(colorScheme = inLook.colors, typography = inLook.typography) {
				CompositionLocalProvider(LocalColorTheme provides inLook.theme, LocalAmountsHidden provides inLook.hidden) {
					Column(
						modifier            = Modifier.width(kExportWidth.dp).onSizeChanged { vHeight = it.height }.background(inLook.colors.surface).padding(40.dp),
						verticalArrangement = Arrangement.spacedBy(24.dp)
					) {
						Text(inHeading, style = MaterialTheme.typography.headlineSmall, color = inLook.colors.onSurface)
						SankeyDiagram(inDiagram, inCenterTitle)
					}
				}
			}
		}																				// image : titre et schéma
		val vWidth   = (kExportWidth * kExportScale).toInt()								// largeur en pixels
		val vMeasure = ImageComposeScene(vWidth, 20_000, Density(kExportScale), content = vContent)	// première passe : hauteur du contenu
		vMeasure.render()
		vMeasure.close()
		check(vHeight > 0) { "schéma vide" }
		val vScene   = ImageComposeScene(vWidth, vHeight, Density(kExportScale), content = vContent)	// rendu à la bonne hauteur
		try
		{
			val vData = vScene.render().encodeToData(EncodedImageFormat.PNG) ?: error("encodage PNG impossible")	// image encodée
			inPath.parent?.let { Files.createDirectories(it) }
			Files.write(inPath, vData.bytes)
		}
		finally
		{
			vScene.close()
		}
	}.exceptionOrNull()?.let { it.message ?: "erreur inconnue" }
