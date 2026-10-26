package fr.maubin.patrimoineplus.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import kotlin.math.max

import fr.maubin.patrimoineplus.ui.components.operationAccent
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import fr.maubin.patrimoineplus.ui.components.isGlass
import fr.maubin.patrimoineplus.ui.components.glassPanel
import fr.maubin.patrimoineplus.ui.components.glassWell
import fr.maubin.patrimoineplus.ui.components.FloatingSurface

val kTileGap = 20.dp																// espace entre deux tuiles

/*
	Cellule d'une rangée de tuiles : largeur relative et contenu.
	weight  : largeur relative
	content : contenu, avec le modificateur qui lui donne sa place
*/
class Cell(
	val weight  : Float,
	val content : @Composable (Modifier) -> Unit
)

/*
	Rangée de tuiles de même hauteur, côte à côte quand la place le permet,
	empilées sinon (fenêtre étroite, panneau de saisie ouvert).
	inCells    : cellules
	inMinWidth : largeur sous laquelle les tuiles s'empilent
*/
@Composable
fun TileRow(
	vararg inCells : Cell,
	inMinWidth     : Dp = 880.dp
)
{
	BoxWithConstraints(Modifier.fillMaxWidth()) {
		if (maxWidth >= inMinWidth)
		{
			Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(kTileGap)) {
				inCells.forEach { inCell -> inCell.content(Modifier.weight(inCell.weight).fillMaxHeight()) }
			}
		}
		else
		{
			Column(verticalArrangement = Arrangement.spacedBy(kTileGap)) {
				inCells.forEach { inCell -> inCell.content(Modifier.fillMaxWidth()) }
			}
		}
	}
}

/*
	Grille de tuiles de même largeur : autant de colonnes que la largeur en
	permet ; les tuiles d'une même ligne ont la même hauteur.
	inItems    : éléments
	inMinWidth : largeur minimale d'une tuile
	inContent  : tuile d'un élément, avec le modificateur qui lui donne sa place
*/
@Composable
fun <T> TileGrid(
	inItems    : List<T>,
	inMinWidth : Dp,
	inContent  : @Composable (T, Modifier) -> Unit
)
{
	BoxWithConstraints(Modifier.fillMaxWidth()) {
		val vColumns = max(1, ((maxWidth + kTileGap) / (inMinWidth + kTileGap)).toInt())	// colonnes
		Column(verticalArrangement = Arrangement.spacedBy(kTileGap)) {
			inItems.chunked(vColumns).forEach { inRow ->
				Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(kTileGap)) {
					inRow.forEach { inItem -> inContent(inItem, Modifier.weight(1f).fillMaxHeight()) }
					repeat(vColumns - inRow.size) { Spacer(Modifier.weight(1f)) }
				}
			}
		}
	}
}

/*
	Ligne d'opération dans une tuile : pastille d'icône, libellé et détail,
	montant signé ; cliquable pour la modifier.
	inIcon    : clé d'icône
	inTitle   : libellé
	inDetail  : détail (date, compte, catégorie)
	inCents   : montant signé
	inOnClick : ouverture ; null pour une ligne inerte
*/
@Composable
fun OperationLine(
	inIcon    : String,
	inTitle   : String,
	inDetail  : String,
	inCents   : Long,
	inOnClick : (() -> Unit)?
)
{
	val vColors = MaterialTheme.colorScheme											// palette courante
	Row(
		modifier          = Modifier
			.fillMaxWidth()
			.heightIn(min = 52.dp)
			.clip(MaterialTheme.shapes.small)
			.then(if (inOnClick != null) Modifier.clickable(onClick = inOnClick) else Modifier)
			.padding(horizontal = 8.dp, vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		val vAccent = operationAccent(inCents)												// entrée ou sortie (thème Coloré)
		Box(Modifier.size(32.dp).clip(CircleShape).background(vAccent?.first ?: glassWell(vColors.surfaceContainer)), contentAlignment = Alignment.Center) {
			Icon(CategoryIcons.of(inIcon), contentDescription = null, tint = vAccent?.second ?: LocalContentColor.current, modifier = Modifier.size(18.dp))
		}
		Spacer(Modifier.width(14.dp))
		Column(Modifier.weight(1f)) {
			Text(inTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(inDetail, style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
		}
		Spacer(Modifier.width(16.dp))
		AmountText(inCents = inCents, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = true)
	}
}

/*
	Texte discret d'une tuile vide ou d'une indication.
	inText : texte
*/
@Composable
fun Hint(inText: String)
{
	Text(inText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
}

/*
	Champ de texte compact et souligné, pour une case de tableau (grille de
	saisie, rapprochement).
	inValue       : texte
	inOnChange    : modification
	inModifier    : place du champ
	inPlaceholder : texte grisé quand le champ est vide
	inEnd         : texte aligné à droite (montants), texte grisé compris
	inError       : bordure d'erreur
	inUnderline   : filet sous le champ (toujours affiché en cas d'erreur)
*/
@Composable
fun CellField(
	inValue       : String,
	inOnChange    : (String) -> Unit,
	inModifier    : Modifier = Modifier,
	inPlaceholder : String   = "",
	inEnd         : Boolean  = false,
	inError       : Boolean  = false,
	inUnderline   : Boolean  = true
)
{
	val vColors = MaterialTheme.colorScheme											// palette courante
	val vStyle  = MaterialTheme.typography.bodyLarge.copy(color = vColors.onSurface, textAlign = if (inEnd) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start)	// style du texte
	Column(inModifier.fillMaxWidth()) {
		androidx.compose.foundation.text.BasicTextField(
			value         = inValue,
			onValueChange = inOnChange,
			singleLine    = true,
			textStyle     = vStyle,
			cursorBrush   = androidx.compose.ui.graphics.SolidColor(vColors.onSurface),
			modifier      = Modifier.fillMaxWidth().padding(vertical = 6.dp),
			decorationBox = { inField ->
				Box(Modifier.fillMaxWidth(), contentAlignment = if (inEnd) Alignment.CenterEnd else Alignment.CenterStart) {	// toute la largeur : alignement à droite exact, avec ou sans filet
					if (inValue.isEmpty() && inPlaceholder.isNotEmpty()) Text(inPlaceholder, style = vStyle.copy(color = vColors.onSurfaceVariant), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
					inField()
				}
			}
		)
		if (inUnderline || inError) androidx.compose.material3.HorizontalDivider(color = if (inError) vColors.error else vColors.outlineVariant)
	}
}

/*
	Case à choix (compte, catégorie) : bouton discret et menu.
	inText     : texte affiché
	inOptions  : choix (valeur, texte)
	inOnSelect : nouveau choix
	inModifier : place
	inMuted    : texte grisé (valeur proposée, pas encore choisie)
*/
@Composable
fun MenuCell(
	inText     : String,
	inOptions  : List<Pair<String, String>>,
	inOnSelect : (String) -> Unit,
	inModifier : Modifier,
	inMuted    : Boolean = false
)
{
	var vOpen by remember { mutableStateOf(false) }										// menu ouvert
	Box(inModifier) {
		androidx.compose.material3.TextButton(onClick = { vOpen = true }, modifier = Modifier.fillMaxWidth()) {
			Text(inText, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (inMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
		}
		androidx.compose.material3.DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
			inOptions.forEach { (inValue, inLabel) -> androidx.compose.material3.DropdownMenuItem(text = { Text(inLabel) }, onClick = { vOpen = false; inOnSelect(inValue) }) }
		}
	}
}

/*
	Carte flottante (réglages, listes) : surface flottante pleine largeur,
	contenu en colonne.
	inContent : contenu
*/
@Composable
fun FloatingCard(inContent: @Composable ColumnScope.() -> Unit)
{
	FloatingSurface(inModifier = Modifier.fillMaxWidth()) { Column(content = inContent) }
}

