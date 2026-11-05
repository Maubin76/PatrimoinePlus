package fr.maubin.patrimoineplus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.theme.kLemon
import fr.maubin.patrimoineplus.ui.theme.kLemonNight
import fr.maubin.patrimoineplus.ui.theme.kOnLemon

private const val kHoverMillis = 140											// fondu du survol
private val kPill              = RoundedCornerShape(50)							// pilule
private val kChip              = RoundedCornerShape(6.dp)						// étiquette d'état
private val kRiseFill          = Color(0xFFDDF1E2)								// fond d'une hausse (clair)
private val kRiseText          = Color(0xFF1C7A3C)								// texte d'une hausse (clair, 4,9:1 sur son fond)
private val kFallFill          = Color(0xFFFBE2DF)								// fond d'une baisse (clair)
private val kFallText          = Color(0xFFB2302A)								// texte d'une baisse (clair, 5,2:1 sur son fond)
private val kWarnFill          = Color(0xFFFDEBD7)								// fond d'un avertissement (clair)
private val kWarnText          = Color(0xFFA34E07)								// texte d'un avertissement (clair)
private val kRiseNight         = Color(0xFF6FD69A)								// hausse (sombre)
private val kFallNight         = Color(0xFFFF8E83)								// baisse (sombre)
private val kWarnNight         = Color(0xFFF7A75E)								// avertissement (sombre)

/*
	Vrai si le fond courant est sombre.
*/
@Composable
fun isNight(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/*
	Ton d'une étiquette ou d'une pastille : bon (vert), mauvais (rouge),
	avertissement (orange), neutre (gris).
*/
enum class Tone
{
	GOOD,
	BAD,
	WARN,
	NEUTRAL
}

/*
	Fond et texte d'un ton, en clair ou en sombre.
	inTone : ton
	Retour : fond et texte
*/
@Composable
fun toneColors(inTone: Tone): Pair<Color, Color>
{
	val vNight = isNight()															// fond sombre
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant						// texte secondaire
	return when (inTone)
	{
		Tone.GOOD    -> if (vNight) kRiseNight.copy(alpha = 0.16f) to kRiseNight else kRiseFill to kRiseText
		Tone.BAD     -> if (vNight) kFallNight.copy(alpha = 0.16f) to kFallNight else kFallFill to kFallText
		Tone.WARN    -> if (vNight) kWarnNight.copy(alpha = 0.16f) to kWarnNight else kWarnFill to kWarnText
		Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f) to vMuted
	}
}

/*
	Pilule translucide de la brume (action secondaire) : verre blanc, liseré
	blanc, icône facultative et libellé ; plus dense au survol.
	inLabel    : libellé
	inOnClick  : action
	inModifier : modificateur appliqué
	inIcon     : icône ; null sans
	inSelected : pilule choisie (opaque, comme l'onglet actif)
*/
@Composable
fun GhostPill(
	inLabel    : String,
	inOnClick  : () -> Unit,
	inModifier : Modifier     = Modifier,
	inIcon     : ImageVector? = null,
	inSelected : Boolean      = false
)
{
	val vNight   = isNight()															// fond sombre
	val vSource  = remember { MutableInteractionSource() }							// survol
	val vHovered by vSource.collectIsHoveredAsState()								// souris au-dessus
	val vFill    by animateColorAsState(
		when
		{
			inSelected && vNight -> Color.White.copy(alpha = 0.16f)
			inSelected           -> Color.White
			vNight               -> Color.White.copy(alpha = if (vHovered) 0.11f else 0.06f)
			else                 -> Color.White.copy(alpha = if (vHovered) 0.85f else 0.55f)
		}, tween(kHoverMillis), label = "pilule")										// fond, en fondu
	Row(
		modifier          = inModifier
			.height(38.dp)
			.background(vFill, kPill)
			.border(1.dp, Color.White.copy(alpha = if (vNight) 0.1f else 0.9f), kPill)
			.hoverable(vSource)
			.clickable(interactionSource = vSource, indication = null, role = Role.Button, onClick = inOnClick)
			.padding(PaddingValues(start = if (inIcon != null) 14.dp else 18.dp, end = 18.dp)),
		verticalAlignment = Alignment.CenterVertically
	) {
		inIcon?.let { inGlyph ->
			Icon(inGlyph, contentDescription = null, modifier = Modifier.size(16.dp))
			Spacer(Modifier.width(8.dp))
		}
		Text(inLabel, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal), maxLines = 1)
	}
}

/*
	Bouton jaune citron, le seul bouton plein de l'appli (saisie d'une
	opération) : icône et libellé à l'encre, plus soutenu au survol.
	inLabel    : libellé
	inOnClick  : action
	inModifier : modificateur appliqué
	inIcon     : icône ; null sans
*/
@Composable
fun LemonButton(
	inLabel    : String,
	inOnClick  : () -> Unit,
	inModifier : Modifier     = Modifier,
	inIcon     : ImageVector? = null
)
{
	val vBase    = if (isNight()) kLemonNight else kLemon							// jaune
	val vSource  = remember { MutableInteractionSource() }							// survol
	val vHovered by vSource.collectIsHoveredAsState()								// souris au-dessus
	val vFill    by animateColorAsState(if (vHovered) lerp(vBase, Color(0xFFE0B400), 0.35f) else vBase, tween(kHoverMillis), label = "citron")	// fond, en fondu
	Row(
		modifier          = inModifier
			.height(44.dp)
			.background(vFill, kPill)
			.hoverable(vSource)
			.clickable(interactionSource = vSource, indication = null, role = Role.Button, onClick = inOnClick)
			.padding(PaddingValues(start = if (inIcon != null) 18.dp else 24.dp, end = 24.dp)),
		verticalAlignment = Alignment.CenterVertically
	) {
		inIcon?.let { inGlyph ->
			Icon(inGlyph, contentDescription = null, tint = kOnLemon, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
		}
		Text(inLabel, style = MaterialTheme.typography.labelLarge, color = kOnLemon, maxLines = 1)
	}
}

/*
	Rond jaune citron d'une seule icône (bouton de saisie du téléphone).
	inIcon        : icône
	inDescription : nom de l'action (lecteurs d'écran)
	inOnClick     : action
	inModifier    : modificateur appliqué
	inSize        : diamètre
*/
@Composable
fun LemonDisc(
	inIcon        : ImageVector,
	inDescription : String,
	inOnClick     : () -> Unit,
	inModifier    : Modifier = Modifier,
	inSize        : Dp       = 56.dp
)
{
	Box(
		modifier         = inModifier
			.size(inSize)
			.background(if (isNight()) kLemonNight else kLemon, kPill)
			.clickable(role = Role.Button, onClick = inOnClick)
			.semantics { contentDescription = inDescription },
		contentAlignment = Alignment.Center
	) {
		Icon(inIcon, contentDescription = null, tint = kOnLemon, modifier = Modifier.size(24.dp))
	}
}

/*
	Pastille de variation : petite flèche montante ou descendante et texte
	(« 18 % », « +1 200 € »), en vert pour une hausse, en rouge pour une
	baisse, en gris sans variation ; le signe reste écrit dans le texte.
	inText : texte de la variation
	inRise : vrai pour une hausse, faux pour une baisse, null sans variation
	inGood : vrai si le sens est favorable (une baisse des dépenses est en vert) ; null : la hausse l'est
*/
@Composable
fun DeltaPill(
	inText : String,
	inRise : Boolean?,
	inGood : Boolean? = null
)
{
	val vTone          = when
	{
		inRise == null               -> Tone.NEUTRAL
		(inGood ?: inRise)           -> Tone.GOOD
		else                         -> Tone.BAD
	}																				// ton de la pastille
	val (vFill, vText) = toneColors(vTone)											// couleurs
	Row(
		modifier          = Modifier.height(20.dp).background(vFill, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		if (inRise != null)
		{
			Canvas(Modifier.size(10.dp)) {
				val vW = size.width															// largeur
				val vH = size.height														// hauteur
				val vLine = Path().apply {
					if (inRise) { moveTo(0f, vH * 0.85f); lineTo(vW * 0.4f, vH * 0.45f); lineTo(vW * 0.6f, vH * 0.65f); lineTo(vW, vH * 0.2f) }
					else        { moveTo(0f, vH * 0.15f); lineTo(vW * 0.4f, vH * 0.55f); lineTo(vW * 0.6f, vH * 0.35f); lineTo(vW, vH * 0.8f) }
				}																		// trait de la tendance
				drawPath(vLine, vText, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
			}
			Spacer(Modifier.width(4.dp))
		}
		Text(inText, style = MaterialTheme.typography.labelSmall, color = vText, maxLines = 1)
	}
}

/*
	Étiquette d'état (« À venir », « Rapproché », « Brouillon ») : petit
	rectangle arrondi teinté, texte du ton ; liseré pour un état mauvais.
	inText : texte
	inTone : ton
*/
@Composable
fun StatusChip(
	inText : String,
	inTone : Tone
)
{
	val (vFill, vText) = toneColors(inTone)										// couleurs
	Box(
		modifier         = Modifier
			.height(22.dp)
			.background(vFill, kChip)
			.then(if (inTone == Tone.BAD) Modifier.border(1.dp, vText.copy(alpha = 0.45f), kChip) else Modifier)
			.padding(horizontal = 8.dp),
		contentAlignment = Alignment.Center
	) {
		Text(inText, style = MaterialTheme.typography.labelSmall, color = vText, maxLines = 1)
	}
}

/*
	Mini-graphe en barres fines (indicateurs) : une barre par valeur, du
	plus ancien au plus récent, posée sur la ligne de base, extrémité
	arrondie ; couleur donnée par barre ; les valeurs négatives descendent
	sous une ligne médiane quand il y en a. Relatif : hauteurs entre la plus
	basse et la plus haute valeur (soldes voisins, évolution lisible).
	inValues   : valeurs
	inColorOf  : couleur de la barre d'un rang
	inModifier : modificateur appliqué (hauteur comprise)
	inBar      : épaisseur d'une barre
	inRelative : hauteurs relatives (de 20 % pour la plus basse à 100 % pour la plus haute)
*/
@Composable
fun MicroBars(
	inValues   : List<Long>,
	inColorOf  : (Int) -> Color,
	inModifier : Modifier = Modifier,
	inBar      : Dp       = 3.dp,
	inRelative : Boolean  = false
)
{
	Canvas(inModifier) {
		if (inValues.isEmpty()) return@Canvas
		if (inRelative)
		{
			val vLow   = inValues.min().toFloat()												// plus basse valeur
			val vRange = (inValues.max() - inValues.min()).toFloat()							// étendue
			val vWidth = inBar.toPx()															// épaisseur
			val vPitch = if (inValues.size > 1) (size.width - vWidth) / (inValues.size - 1) else 0f	// pas
			inValues.forEachIndexed { inIndex, inValue ->
				val vShare = if (vRange > 0f) 0.2f + 0.8f * (inValue - vLow) / vRange else 0.6f	// hauteur relative
				val vTall  = size.height * vShare												// hauteur
				drawRoundRect(inColorOf(inIndex), topLeft = Offset(inIndex * vPitch, size.height - vTall), size = Size(vWidth, vTall), cornerRadius = CornerRadius(vWidth / 2f, vWidth / 2f))
			}
			return@Canvas
		}
		val vMax    = inValues.maxOf { maxOf(it, 0L) }.toFloat()						// plus haute valeur positive
		val vMin    = inValues.minOf { minOf(it, 0L) }.toFloat()						// plus basse valeur négative
		val vSpan   = (vMax - vMin).takeIf { it > 0f } ?: 1f							// étendue
		val vZero   = size.height * vMax / vSpan										// ordonnée de zéro
		val vBar    = inBar.toPx()														// épaisseur
		val vStep   = if (inValues.size > 1) (size.width - vBar) / (inValues.size - 1) else 0f	// pas entre deux barres
		val vRadius = CornerRadius(vBar / 2f, vBar / 2f)								// bouts arrondis
		inValues.forEachIndexed { inIndex, inValue ->
			val vX = inIndex * vStep														// abscisse
			val vH = maxOf(vBar, size.height * kotlin.math.abs(inValue.toFloat()) / vSpan)	// hauteur (au moins un point)
			val vY = if (inValue >= 0) vZero - vH else vZero									// haut de la barre
			drawRoundRect(inColorOf(inIndex), topLeft = Offset(vX, vY), size = Size(vBar, vH), cornerRadius = vRadius)
		}
	}
}

/*
	Jauge en arc (répartition, couverture) : arc ouvert vers le bas, segments
	épais séparés par un petit écart, bouts arrondis ; contenu centré dans
	l'arc (chiffre et libellé).
	inParts    : part de chaque segment (somme quelconque) et sa couleur
	inModifier : modificateur appliqué (taille comprise)
	inStroke   : épaisseur de l'arc
	inContent  : contenu centré
*/
@Composable
fun ArcGauge(
	inParts    : List<Pair<Float, Color>>,
	inModifier : Modifier = Modifier,
	inStroke   : Dp       = 18.dp,
	inContent  : @Composable BoxScope.() -> Unit = {}
)
{
	Box(inModifier, contentAlignment = Alignment.Center) {
		Canvas(Modifier.matchParentSize()) {
			val vStroke = inStroke.toPx()														// épaisseur
			val vSide   = minOf(size.width, size.height * 1.25f) - vStroke						// diamètre utile
			val vTop    = Offset((size.width - vSide) / 2f, vStroke / 2f + 2f)					// coin du cercle
			val vSweep  = 220f																	// ouverture de l'arc
			val vStart  = 160f																	// départ, en bas à gauche
			val vGap    = 7f																	// écart entre segments, en degrés (bouts arrondis compris)
			val vTotal  = inParts.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: return@Canvas	// total
			val vShown  = inParts.filter { it.first > 0f }										// segments visibles
			val vFree   = vSweep - vGap * (vShown.size - 1)									// angle à partager
			var vAt     = vStart																// angle courant
			drawArc(Color.Black.copy(alpha = 0.05f), vStart, vSweep, false, vTop, Size(vSide, vSide), style = Stroke(vStroke, cap = StrokeCap.Round))
			vShown.forEach { (inPart, inColor) ->
				val vAngle = maxOf(0.5f, vFree * inPart / vTotal)									// angle du segment
				drawArc(inColor, vAt, vAngle, false, vTop, Size(vSide, vSide), style = Stroke(vStroke, cap = StrokeCap.Round))
				vAt += vAngle + vGap
			}
		}
		inContent()
	}
}

/*
	Espace horizontal entre les éléments d'une rangée de pilules.
*/
val kPillGap = Arrangement.spacedBy(8.dp)
