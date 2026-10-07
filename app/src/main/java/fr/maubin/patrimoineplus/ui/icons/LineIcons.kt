package fr.maubin.patrimoineplus.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val kStroke = 1.6f											// épaisseur du trait, sur une grille de 24

/*
	Construit une icône au trait fin (grille 24 × 24, extrémités arrondies) à
	partir de tracés SVG. La couleur est donnée par la teinte de l'icône.
	inName  : nom de l'icône
	inPaths : tracés SVG, chacun dessiné en contour
*/
internal fun lineIcon(
	inName  : String,
	vararg inPaths : String
): ImageVector
{
	val vBuilder = ImageVector.Builder(										// icône en construction
		name           = inName,
		defaultWidth   = 24.dp,
		defaultHeight  = 24.dp,
		viewportWidth  = 24f,
		viewportHeight = 24f
	)
	inPaths.forEach { inPath ->
		vBuilder.addPath(
			pathData       = addPathNodes(inPath),
			fill           = null,
			stroke         = SolidColor(Color.Black),
			strokeLineWidth = kStroke,
			strokeLineCap  = StrokeCap.Round,
			strokeLineJoin = StrokeJoin.Round
		)
	}
	return vBuilder.build()
}

/*
	Cercle en tracé SVG.
	inX, inY : centre
	inR      : rayon
*/
internal fun circle(
	inX : Float,
	inY : Float,
	inR : Float
): String =
	"M${inX - inR} $inY a$inR $inR 0 1 0 ${2 * inR} 0 a$inR $inR 0 1 0 ${-2 * inR} 0"

/*
	Jeu d'icônes au trait fin de l'application, dessiné pour s'accorder à la
	grotesque : géométrie simple, trait de 1,6, aucun aplat.
*/
object LineIcons
{
	// Accueil : maison sobre, toit et porte.
	val Home: ImageVector = lineIcon(
		"Accueil",
		"M3.5 11 L12 4 L20.5 11",
		"M6 9.5 V19.5 H18 V9.5",
		"M10 19.5 V14.5 H14 V19.5"
	)

	// Comptes : portefeuille.
	val Wallet: ImageVector = lineIcon(
		"Comptes",
		"M6 7.5 H18 A2.5 2.5 0 0 1 20.5 10 V17 A2.5 2.5 0 0 1 18 19.5 H6 A2.5 2.5 0 0 1 3.5 17 V10 A2.5 2.5 0 0 1 6 7.5 Z",
		"M5 7.6 L15 4.6 A1.5 1.5 0 0 1 17 6 V7.5",
		"M20.5 11.5 H17 A2 2 0 0 0 17 15.5 H20.5"
	)

	// Catégories : grille de quatre cases arrondies.
	val Grid: ImageVector = lineIcon(
		"Catégories",
		"M6 4 H9 A2 2 0 0 1 11 6 V9 A2 2 0 0 1 9 11 H6 A2 2 0 0 1 4 9 V6 A2 2 0 0 1 6 4 Z",
		"M15 4 H18 A2 2 0 0 1 20 6 V9 A2 2 0 0 1 18 11 H15 A2 2 0 0 1 13 9 V6 A2 2 0 0 1 15 4 Z",
		"M6 13 H9 A2 2 0 0 1 11 15 V18 A2 2 0 0 1 9 20 H6 A2 2 0 0 1 4 18 V15 A2 2 0 0 1 6 13 Z",
		"M15 13 H18 A2 2 0 0 1 20 15 V18 A2 2 0 0 1 18 20 H15 A2 2 0 0 1 13 18 V15 A2 2 0 0 1 15 13 Z"
	)

	// Réglages : trois réglettes.
	val Sliders: ImageVector = lineIcon(
		"Réglages",
		"M4 7 H6.5", "M10.5 7 H20", circle(8.5f, 7f, 2f),
		"M4 12 H13.5", "M17.5 12 H20", circle(15.5f, 12f, 2f),
		"M4 17 H8.5", "M12.5 17 H20", circle(10.5f, 17f, 2f)
	)

	// Retour : flèche vers la gauche.
	val Back: ImageVector = lineIcon("Retour", "M19 12 H5", "M11 6 L5 12 L11 18")

	// Mois précédent.
	val ChevronLeft: ImageVector = lineIcon("Précédent", "M15 5 L8 12 L15 19")

	// Mois suivant.
	val ChevronRight: ImageVector = lineIcon("Suivant", "M9 5 L16 12 L9 19")

	// Modifier : crayon.
	val Edit: ImageVector = lineIcon("Modifier", "M4 20 L4.8 16.2 L16 5 A2.1 2.1 0 0 1 19 8 L7.8 19.2 Z", "M14 7 L17 10")

	// Supprimer : corbeille.
	val Trash: ImageVector = lineIcon("Supprimer", "M4 7 H20", "M9 7 V4.5 H15 V7", "M6.5 7 L7.5 20 H16.5 L17.5 7", "M10 11 V16", "M14 11 V16")

	// Fermer : croix.
	val Close: ImageVector = lineIcon("Fermer", "M6 6 L18 18", "M18 6 L6 18")

	// Coche (élément sélectionné).
	val Check: ImageVector = lineIcon("Sélectionné", "M5 12.5 L10 17.5 L19 7")
}
