package fr.maubin.patrimoineplus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint

private val kTileShape = RoundedCornerShape(20.dp)								// forme d'une tuile
private const val kHoverMillis = 160												// fondu du survol
private const val kPressMillis = 90												// enfoncement au clic

/*
	Tuile du tableau de bord : carte bordée, titre facultatif avec une action
	à droite, contenu libre. Cliquable, elle souligne son bord au survol de
	la souris (PC) en fondu et s'enfonce légèrement au clic, qui l'ouvre.
	inModifier : modificateur appliqué
	inTitle    : titre de la tuile ; null sans titre
	inOnClick  : action d'un clic ; null pour une tuile inerte
	inClickLabel : description de l'action (lecteurs d'écran)
	inAction   : élément à droite du titre (bouton, lien) ; null sans action
	inContent  : contenu
*/
@Composable
fun Tile(
	inModifier   : Modifier                          = Modifier,
	inTitle      : String?                           = null,
	inOnClick    : (() -> Unit)?                     = null,
	inClickLabel : String?                           = null,
	inAction     : (@Composable RowScope.() -> Unit)? = null,
	inContent    : @Composable ColumnScope.() -> Unit
)
{
	val vColors   = MaterialTheme.colorScheme										// palette courante
	val vSource   = remember { MutableInteractionSource() }						// survol et clics
	val vHovered  by vSource.collectIsHoveredAsState()							// souris au-dessus
	val vPressed  by vSource.collectIsPressedAsState()							// bouton de la souris enfoncé
	val vActive   = inOnClick != null && vHovered								// mise en avant du survol
	val vClick    = if (inOnClick != null) Modifier.clickable(interactionSource = vSource, indication = null, role = Role.Button, onClickLabel = inClickLabel, onClick = inOnClick) else Modifier
	val vFill     by animateColorAsState(if (vActive) vColors.surfaceContainerLow else vColors.surface, tween(kHoverMillis), label = "fond")	// fond, en fondu
	val vEdge     by animateColorAsState(if (vActive) vColors.outline else vColors.outlineVariant, tween(kHoverMillis), label = "bord")	// bord, en fondu
	val vScale    by animateFloatAsState(if (inOnClick != null && vPressed) 0.985f else 1f, tween(kPressMillis), label = "appui")	// léger enfoncement au clic
	Surface(
		modifier = inModifier.graphicsLayer { scaleX = vScale; scaleY = vScale }.hoverable(vSource).then(vClick),
		shape    = kTileShape,
		color    = vFill,
		border   = BorderStroke(1.dp, vEdge)
	) {
		Column(Modifier.padding(24.dp)) {
			if (inTitle != null || inAction != null)
			{
				Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
					Text(
						text     = inTitle.orEmpty(),
						style    = MaterialTheme.typography.titleMedium,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f)
					)
					inAction?.invoke(this)
				}
				androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
			}
			inContent()
		}
	}
}

/*
	Petit indicateur d'une tuile : libellé discret, puis valeur.
	inLabel   : libellé
	inContent : valeur (montant…)
*/
@Composable
fun TileFigure(
	inLabel   : String,
	inContent : @Composable () -> Unit
)
{
	Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
		Text(inLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
		inContent()
	}
}

/*
	Courbe miniature d'une tuile : la valeur en fin de mois, sans axe ni
	étiquette, avec un point sur le dernier mois. Moins de deux points : rien
	n'est dessiné (la place est gardée).
	inSeries      : valeurs en fin de mois
	inDescription : description lue par les lecteurs d'écran
	inModifier    : modificateur appliqué
	inHeight      : hauteur
*/
@Composable
fun Sparkline(
	inSeries      : List<SeriesPoint>,
	inDescription : String,
	inModifier    : Modifier = Modifier,
	inHeight      : Dp       = 48.dp
)
{
	val vLine  = MaterialTheme.colorScheme.onSurface								// trait
	Canvas(inModifier.fillMaxWidth().height(inHeight).semantics { contentDescription = inDescription }) {
		if (inSeries.size < 2)
		{
			return@Canvas
		}
		val vMin    = inSeries.minOf { it.valueCents }.toFloat()					// plus petite valeur
		val vMax    = inSeries.maxOf { it.valueCents }.toFloat()					// plus grande valeur
		val vSpan   = (vMax - vMin).takeIf { it > 0f } ?: 1f						// amplitude (courbe plate : au milieu)
		val vPad    = 4.dp.toPx()													// marge (épaisseur du trait, point final)
		val vStepX  = (size.width - vPad) / (inSeries.size - 1)						// écart entre deux mois
		val vY      = { inValue: Long -> if (vMax == vMin) size.height / 2f else vPad + (size.height - 2 * vPad) * (1f - (inValue - vMin) / vSpan) }	// ordonnée
		val vPath = smoothPath(inSeries.mapIndexed { inIndex, inPoint -> Offset(inIndex * vStepX, vY(inPoint.valueCents)) })	// tracé lissé
		drawPath(vPath, vLine, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
		drawCircle(vLine, radius = 3.5.dp.toPx(), center = Offset(size.width - vPad, vY(inSeries.last().valueCents)))
	}
}
