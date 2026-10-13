package fr.maubin.patrimoineplus.ui.components

import fr.maubin.patrimoineplus.domain.model.AccountType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import fr.maubin.patrimoineplus.ui.theme.kAllocationLight
import fr.maubin.patrimoineplus.ui.theme.kAllocationDark
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private val kCardShape = RoundedCornerShape(16.dp)							// coins des cartes

/*
	Point identifiant une série (un type de compte), en niveau de gris.
	inColor    : couleur de la série
	inModifier : modificateur appliqué
*/
@Composable
fun SeriesDot(
	inColor    : Color,
	inModifier : Modifier = Modifier
)
{
	Box(modifier = inModifier.size(10.dp).background(inColor, CircleShape))
}

/*
	Pastille bordée portant une information courte (date de calcul, statut).
	Elle n'est pas cliquable : elle ne porte donc pas de chevron.
	inText     : texte de la pastille
	inModifier : modificateur appliqué
*/
@Composable
fun InfoPill(
	inText     : String,
	inModifier : Modifier = Modifier
)
{
	Surface(
		modifier = inModifier,
		shape    = CircleShape,
		color    = MaterialTheme.colorScheme.surface,
		border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
	) {
		Text(
			text     = inText,
			style    = MaterialTheme.typography.labelLarge,
			color    = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
		)
	}
}

/*
	Titre de section en gras, avec un lien souligné facultatif à droite.
	inTitle     : titre de la section
	inLinkLabel : libellé du lien ; null pour ne pas en afficher
	inOnLink    : action du lien
	inModifier  : modificateur appliqué
*/
@Composable
fun SectionHeader(
	inTitle     : String,
	inLinkLabel : String?     = null,
	inOnLink    : () -> Unit  = {},
	inModifier  : Modifier    = Modifier
)
{
	Row(
		modifier          = inModifier.fillMaxWidth().heightIn(min = 48.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text     = inTitle,
			style    = MaterialTheme.typography.headlineSmall,
			modifier = Modifier.weight(1f).semantics { heading() }
		)
		if (inLinkLabel != null)
		{
			Box(
				modifier         = Modifier
					.heightIn(min = 48.dp)
					.clickable(role = Role.Button, onClick = inOnLink)
					.padding(horizontal = 4.dp),
				contentAlignment = Alignment.Center
			) {
				Text(
					text  = inLinkLabel,
					style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline)
				)
			}
		}
	}
}

/*
	Carte bordée de 1 dp, sans ombre ni teinte : regroupe des lignes liées.
	inModifier : modificateur appliqué
	inContent  : lignes de la carte
*/
@Composable
fun BorderedCard(
	inModifier : Modifier = Modifier,
	inContent  : @Composable ColumnScope.() -> Unit
)
{
	Surface(
		modifier = inModifier.fillMaxWidth(),
		shape    = kCardShape,
		color    = MaterialTheme.colorScheme.surface,
		border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
	) {
		Column(content = inContent)
	}
}

/*
	Segment d'une barre de répartition.
	value : poids du segment (montant positif, en centimes)
	color : couleur de la série
*/
data class BarSegment(
	val value : Long,
	val color : Color
)

/*
	Barre de répartition proportionnelle, sur une rampe fixe : la largeur
	totale vaut toujours 100 %. Sans segment, seul le rail est dessiné.
	inSegments    : segments, dans l'ordre d'affichage ; les poids négatifs sont ignorés
	inDescription : description lue par TalkBack
	inModifier    : modificateur appliqué
*/
@Composable
fun ProportionBar(
	inSegments    : List<BarSegment>,
	inDescription : String,
	inModifier    : Modifier = Modifier
)
{
	val vTrack = MaterialTheme.colorScheme.surfaceContainerHigh				// couleur du rail
	Canvas(
		modifier = inModifier
			.fillMaxWidth()
			.height(10.dp)
			.semantics { contentDescription = inDescription }
	) {
		val vRadius   = CornerRadius(size.height / 2f)						// arrondi des extrémités
		val vGap      = 3.dp.toPx()											// espace entre segments
		val vPositive = inSegments.filter { it.value > 0 }					// segments dessinables
		val vTotal    = vPositive.sumOf { it.value }.toFloat()				// somme des poids
		drawRoundRect(color = vTrack, cornerRadius = vRadius)
		if (vTotal <= 0f)
		{
			return@Canvas
		}
		val vUsable = size.width - vGap * (vPositive.size - 1)				// largeur hors espaces
		var vX      = 0f													// abscisse du segment courant
		vPositive.forEach { inSegment ->
			val vWidth = vUsable * inSegment.value / vTotal					// largeur proportionnelle
			drawRoundRect(
				color        = inSegment.color,
				topLeft      = Offset(vX, 0f),
				size         = Size(vWidth, size.height),
				cornerRadius = vRadius
			)
			vX += vWidth + vGap
		}
	}
}

const val kAllocationSlots = 4													// teintes de répartition (la dernière : « Autres »)

/*
	Teinte de répartition d'un rang de valeur (0 : le plus gros) : rampe de
	gris validée, selon le mode clair ou sombre ; les rangs au-delà du
	quatrième partagent la dernière teinte.
	inRank : rang de valeur
*/
@Composable
fun allocationColor(inRank: Int): Color =
	(if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) kAllocationDark else kAllocationLight)[inRank.coerceIn(0, kAllocationSlots - 1)]

/*
	Teinte d'un type de compte (répartitions par type) : courant, épargne,
	bourse, immobilier, collection, du plus foncé au plus clair.
	inType : type de compte
*/
@Composable
fun typeColor(inType: AccountType): Color =
	when (inType)
	{
		AccountType.CHECKING  -> MaterialTheme.colorScheme.onSurface
		AccountType.SAVINGS   -> MaterialTheme.colorScheme.secondary
		AccountType.BROKERAGE -> MaterialTheme.colorScheme.tertiary
		AccountType.REAL_ESTATE -> MaterialTheme.colorScheme.onSurfaceVariant
		AccountType.COLLECTIBLE -> MaterialTheme.colorScheme.outline
	}
