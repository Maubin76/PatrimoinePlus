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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn

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
	Bouton secondaire de la brume, à la place du bouton à contour Material
	(mêmes paramètres que OutlinedButton, pour le remplacer tel quel) :
	pilule de verre blanc, liseré blanc, plus dense au survol ; atténué
	quand il est inactif.
	onClick        : action
	modifier       : modificateur appliqué
	enabled        : bouton actif
	contentPadding : marges intérieures
	content        : contenu (texte, icône)
*/
@Composable
fun GhostButton(
	onClick        : () -> Unit,
	modifier       : Modifier      = Modifier,
	enabled        : Boolean       = true,
	contentPadding : PaddingValues = PaddingValues(horizontal = 18.dp),
	content        : @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
)
{
	val vNight   = isNight()															// fond sombre
	val vSource  = remember { MutableInteractionSource() }							// survol
	val vHovered by vSource.collectIsHoveredAsState()								// souris au-dessus
	val vFill    by animateColorAsState(Color.White.copy(alpha = if (vNight) (if (vHovered && enabled) 0.11f else 0.06f) else (if (vHovered && enabled) 0.85f else 0.55f)), tween(kHoverMillis), label = "pilule")	// fond
	val vInk     = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)	// encre
	Row(
		modifier              = modifier
			.heightIn(min = 40.dp)
			.background(vFill, kPill)
			.border(1.dp, Color.White.copy(alpha = if (vNight) 0.1f else 0.9f), kPill)
			.hoverable(vSource, enabled)
			.clickable(interactionSource = vSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
			.padding(contentPadding),
		horizontalArrangement = Arrangement.Center,
		verticalAlignment     = Alignment.CenterVertically
	) {
		androidx.compose.runtime.CompositionLocalProvider(
			androidx.compose.material3.LocalContentColor provides vInk,
			androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal)
		) { content() }
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
	Bouton principal d'un panneau, d'une fenêtre ou d'une feuille de saisie
	(« Enregistrer »), à la place du bouton plein Material (mêmes paramètres
	que Button, pour le remplacer tel quel) : pilule jaune citron, texte à
	l'encre ; atténué quand il est inactif.
	onClick        : action
	modifier       : modificateur appliqué
	enabled        : bouton actif
	contentPadding : marges intérieures
	content        : contenu (texte, icône)
*/
@Composable
fun LemonPillButton(
	onClick        : () -> Unit,
	modifier       : Modifier      = Modifier,
	enabled        : Boolean       = true,
	contentPadding : PaddingValues = PaddingValues(horizontal = 22.dp),
	content        : @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
)
{
	val vBase    = if (isNight()) kLemonNight else kLemon							// jaune
	val vSource  = remember { MutableInteractionSource() }							// survol
	val vHovered by vSource.collectIsHoveredAsState()								// souris au-dessus
	val vFill    by animateColorAsState(
		when
		{
			!enabled -> vBase.copy(alpha = 0.35f)
			vHovered -> lerp(vBase, Color(0xFFE0B400), 0.35f)
			else     -> vBase
		}, tween(kHoverMillis), label = "citron")										// fond
	Row(
		modifier              = modifier
			.heightIn(min = 42.dp)
			.background(vFill, kPill)
			.hoverable(vSource, enabled)
			.clickable(interactionSource = vSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
			.padding(contentPadding),
		horizontalArrangement = Arrangement.Center,
		verticalAlignment     = Alignment.CenterVertically
	) {
		androidx.compose.runtime.CompositionLocalProvider(
			androidx.compose.material3.LocalContentColor provides kOnLemon.copy(alpha = if (enabled) 1f else 0.5f),
			androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge
		) { content() }
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
	Étiquette d'état (« À venir », « 54 % ») : petit rectangle arrondi
	teinté, texte du ton (ou d'une couleur donnée, assombrie en clair pour
	rester lisible), liseré léger de la même teinte.
	inText  : texte
	inTone  : ton
	inColor : couleur de l'étiquette (couleur d'une série) ; non précisée : celle du ton
*/
@Composable
fun StatusChip(
	inText  : String,
	inTone  : Tone,
	inColor : Color = Color.Unspecified
)
{
	val vNight         = isNight()														// fond sombre
	val (vFill, vText) = if (inColor == Color.Unspecified) toneColors(inTone)
		else (if (vNight) inColor.copy(alpha = 0.18f) else inColor.copy(alpha = 0.13f)) to (if (vNight) inColor else lerp(inColor, Color.Black, 0.38f))	// couleurs
	Box(
		modifier         = Modifier
			.height(22.dp)
			.background(vFill, kChip)
			.border(1.dp, vText.copy(alpha = if (inTone == Tone.BAD) 0.45f else 0.16f), kChip)
			.padding(horizontal = 8.dp),
		contentAlignment = Alignment.Center
	) {
		Text(inText, style = MaterialTheme.typography.labelSmall, color = vText, maxLines = 1)
	}
}

/*
	Barre de progression épaisse : piste de verre pâle, part remplie arrondie.
	inPercent  : avancement, de 0 à 100
	inColor    : couleur de la part remplie
	inModifier : modificateur appliqué (largeur comprise)
*/
@Composable
fun ProgressTrack(
	inPercent  : Int,
	inColor    : Color,
	inModifier : Modifier = Modifier
)
{
	val vTrack = if (isNight()) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.7f)	// piste
	Canvas(inModifier.height(8.dp)) {
		val vR = CornerRadius(size.height / 2f, size.height / 2f)							// bouts arrondis
		drawRoundRect(vTrack, cornerRadius = vR)
		val vW = size.width * inPercent.coerceIn(0, 100) / 100f							// part remplie
		if (vW > 0f) drawRoundRect(inColor, size = Size(maxOf(vW, size.height), size.height), cornerRadius = vR)
	}
}

/*
	Bouton « … » d'un panneau : rond de verre, trois points ; ouvre un menu
	des actions du panneau.
	inItems : actions (libellé et action)
	inLabel : nom du menu (lecteurs d'écran)
*/
@Composable
fun MoreMenu(
	inItems : List<Pair<String, () -> Unit>>,
	inLabel : String = "Plus d’actions"
)
{
	var vOpen    by remember { mutableStateOf(false) }							// menu ouvert
	val vNight   = isNight()															// fond sombre
	val vSource  = remember { MutableInteractionSource() }							// survol
	val vHovered by vSource.collectIsHoveredAsState()								// souris au-dessus
	val vInk     = MaterialTheme.colorScheme.onSurface								// encre des points
	Box {
		Box(
			modifier         = Modifier
				.size(32.dp)
				.background(Color.White.copy(alpha = if (vNight) (if (vHovered) 0.12f else 0.05f) else (if (vHovered) 0.9f else 0.5f)), kPill)
				.border(1.dp, Color.White.copy(alpha = if (vNight) 0.1f else 0.9f), kPill)
				.hoverable(vSource)
				.clickable(interactionSource = vSource, indication = null, role = Role.Button) { vOpen = true }
				.semantics { contentDescription = inLabel },
			contentAlignment = Alignment.Center
		) {
			Canvas(Modifier.size(width = 14.dp, height = 4.dp)) {
				val vDot = 1.6.dp.toPx()																// rayon d'un point
				listOf(vDot, size.width / 2f, size.width - vDot).forEach { inX -> drawCircle(vInk, vDot, Offset(inX, size.height / 2f)) }
			}
		}
		DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
			inItems.forEach { (inText, inAction) ->
				DropdownMenuItem(text = { Text(inText) }, onClick = { vOpen = false; inAction() })
			}
		}
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
	inMark     : rang de la barre repérée (aujourd'hui, mois en cours) ; null sans
	inLabel    : texte écrit au-dessus de la barre repérée (variation) ; null sans
	inSpread   : part de l'écart entre deux barres gardée (1 : barres réparties sur toute la largeur ; ¼ : regroupées à gauche)
*/
@Composable
fun MicroBars(
	inValues   : List<Long>,
	inColorOf  : (Int) -> Color,
	inModifier : Modifier = Modifier,
	inBar      : Dp       = 3.dp,
	inRelative : Boolean  = false,
	inMark     : Int?     = null,
	inLabel    : String?  = null,
	inSpread   : Float    = 1f
)
{
	val vMeasurer = rememberTextMeasurer()												// mesure de l'étiquette
	val vStyle    = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)	// style de l'étiquette
	Canvas(inModifier) {
		if (inValues.isEmpty()) return@Canvas
		val vText  = inLabel?.let { vMeasurer.measure(it, vStyle) }						// étiquette mesurée
		val vRoom  = (vText?.size?.height ?: 0) + if (vText != null) 4.dp.toPx() else 0f	// place de l'étiquette en haut
		val vPlot  = Size(size.width, size.height - vRoom)									// zone des barres
		val vPitch = pitch(size.width, inBar.toPx(), inValues.size, inSpread)						// pas
		if (vText != null && inMark != null)
		{
			val vCenter = inMark * vPitch + inBar.toPx() / 2f										// milieu de la barre repérée
			drawText(vText, topLeft = Offset((vCenter - vText.size.width / 2f).coerceIn(0f, size.width - vText.size.width), 0f))
		}
		translate(top = vRoom) { bars(inValues, inColorOf, inBar.toPx(), inRelative, vPlot, inSpread) }
	}
}

/*
	Pas entre deux barres : la largeur répartie entre les barres, l'écart
	réduit à la part demandée.
	inWidth  : largeur disponible, en pixels
	inBar    : épaisseur d'une barre, en pixels
	inCount  : nombre de barres
	inSpread : part de l'écart gardée
*/
private fun pitch(
	inWidth  : Float,
	inBar    : Float,
	inCount  : Int,
	inSpread : Float
): Float = if (inCount > 1) inBar + ((inWidth - inBar) / (inCount - 1) - inBar) * inSpread else 0f

/*
	Dessine les barres des mini-graphes dans une zone.
	inValues   : valeurs
	inColorOf  : couleur de la barre d'un rang
	inBar      : épaisseur d'une barre, en pixels
	inRelative : hauteurs relatives
	inArea     : zone des barres
	inSpread   : part de l'écart entre deux barres gardée
*/
private fun DrawScope.bars(
	inValues   : List<Long>,
	inColorOf  : (Int) -> Color,
	inBar      : Float,
	inRelative : Boolean,
	inArea     : Size,
	inSpread   : Float
)
{
	val size = inArea																	// zone (masque la taille du dessin)
	run {
		if (inRelative)
		{
			val vLow   = inValues.min().toFloat()												// plus basse valeur
			val vRange = (inValues.max() - inValues.min()).toFloat()							// étendue
			val vWidth = inBar																	// épaisseur
			val vPitch = pitch(size.width, vWidth, inValues.size, inSpread)								// pas
			inValues.forEachIndexed { inIndex, inValue ->
				val vShare = if (vRange > 0f) 0.2f + 0.8f * (inValue - vLow) / vRange else 0.6f	// hauteur relative
				val vTall  = size.height * vShare												// hauteur
				drawRoundRect(inColorOf(inIndex), topLeft = Offset(inIndex * vPitch, size.height - vTall), size = Size(vWidth, vTall), cornerRadius = CornerRadius(vWidth / 2f, vWidth / 2f))
			}
			return
		}
		val vMax    = inValues.maxOf { maxOf(it, 0L) }.toFloat()						// plus haute valeur positive
		val vMin    = inValues.minOf { minOf(it, 0L) }.toFloat()						// plus basse valeur négative
		val vSpan   = (vMax - vMin).takeIf { it > 0f } ?: 1f							// étendue
		val vZero   = size.height * vMax / vSpan										// ordonnée de zéro
		val vBar    = inBar																// épaisseur
		val vStep   = pitch(size.width, vBar, inValues.size, inSpread)						// pas entre deux barres
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
	Jauge en arc (répartition, couverture) : arc ouvert vers le bas sur 220°,
	segments épais aux bouts arrondis comptés dans leur part (aucun
	chevauchement), séparés d'un petit écart ; une petite part garde au moins
	la taille d'un point ; léger reflet sur le bord extérieur de chaque
	segment, sans piste dessous. Contenu (libellé et chiffre) centré dans le
	creux de l'arc.
	inParts    : part de chaque segment (somme quelconque) et sa couleur
	inModifier : modificateur appliqué (taille comprise)
	inStroke   : épaisseur de l'arc
	inContent  : contenu centré
*/
@Composable
fun ArcGauge(
	inParts    : List<Pair<Float, Color>>,
	inModifier : Modifier = Modifier,
	inStroke   : Dp       = 20.dp,
	inContent  : @Composable BoxScope.() -> Unit = {}
)
{
	BoxWithConstraints(inModifier) {
		val vStrokeDp = inStroke																	// épaisseur
		val vRadiusDp = minOf((maxWidth - vStrokeDp) / 2, (maxHeight - vStrokeDp) / 1.342f)		// rayon qui tient (arc de 220° : hauteur 1,342 r)
		Canvas(Modifier.matchParentSize()) {
			val vStroke = vStrokeDp.toPx()																// épaisseur
			val vR      = vRadiusDp.toPx()																// rayon
			val vCenter = Offset(size.width / 2f, vStroke / 2f + vR)									// centre du cercle
			val vBox    = Rect(vCenter.x - vR, vCenter.y - vR, vCenter.x + vR, vCenter.y + vR)		// cadre du cercle
			val vCap    = Math.toDegrees((vStroke / 2f / vR).toDouble()).toFloat()					// angle d'un bout arrondi
			val vShown  = inParts.filter { it.first > 0f }												// segments visibles
			if (vShown.isEmpty()) return@Canvas
			val vGap    = 4f																			// écart visible entre deux segments
			val vFree   = 220f - vGap * (vShown.size - 1)												// angle à partager (bouts arrondis compris dans chaque part)
			val vMin    = 2f * vCap + 1.5f																// plus petite part dessinable
			val vAngles = shares(vShown.map { it.first }, vFree, vMin)								// angle de chaque segment
			var vAt     = 160f																			// départ, en bas à gauche
			vShown.forEachIndexed { inIndex, (_, inColor) ->
				val vSweep = maxOf(0.1f, vAngles[inIndex] - 2f * vCap)										// arc entre les deux bouts arrondis
				drawArc(inColor, vAt + vCap, vSweep, false, vBox.topLeft, vBox.size, style = Stroke(vStroke, cap = StrokeCap.Round))
				val vShine = vR + vStroke * 0.2f															// rayon du reflet
				drawArc(
					Color.White.copy(alpha = 0.2f), vAt + vCap, vSweep, false,
					Offset(vCenter.x - vShine, vCenter.y - vShine), Size(2f * vShine, 2f * vShine),
					style = Stroke(vStroke * 0.24f, cap = StrokeCap.Round)
				)
				vAt += vAngles[inIndex] + vGap
			}
		}
		Box(Modifier.align(Alignment.TopCenter).padding(top = vStrokeDp + vRadiusDp * 0.42f), content = inContent)
	}
}

/*
	Partage un angle entre des parts, chacune au moins à un minimum (les plus
	grosses cèdent ce que les petites reçoivent).
	inParts : parts
	inTotal : angle à partager
	inMin   : angle minimal d'une part
*/
private fun shares(
	inParts : List<Float>,
	inTotal : Float,
	inMin   : Float
): List<Float>
{
	val vSum    = inParts.sum()																	// somme des parts
	val vSmall  = inParts.map { inTotal * it / vSum < inMin }										// parts trop petites
	val vFixed  = vSmall.count { it } * inMin														// angle des petites
	val vRest   = inParts.filterIndexed { inIndex, _ -> !vSmall[inIndex] }.sum()					// parts restantes
	return inParts.mapIndexed { inIndex, inPart -> if (vSmall[inIndex]) inMin else (inTotal - vFixed) * inPart / vRest }
}

/*
	Espace horizontal entre les éléments d'une rangée de pilules.
*/
val kPillGap = Arrangement.spacedBy(8.dp)
