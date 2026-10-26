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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.shadow
import fr.maubin.patrimoineplus.ui.theme.colorfulMain
import androidx.compose.runtime.CompositionLocalProvider
import fr.maubin.patrimoineplus.ui.theme.LocalColorTheme
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextAlign

private val kTileShape = RoundedCornerShape(28.dp)								// forme d'une tuile : panneau très arrondi
private val kShadowTint = Color(0xFF5B4E9E)										// ombre teintée de lavande (clair)
private const val kHoverMillis = 160												// fondu du survol
private const val kPressMillis = 90												// enfoncement au clic

/*
	Tuile du tableau de bord : panneau flottant sans bord, posé sur le fond
	par une ombre douce, titre facultatif avec une action à droite, contenu
	libre. Cliquable, elle s'élève au survol de la souris (ombre plus
	ample, fond légèrement teinté) et s'enfonce légèrement au clic, qui l'ouvre.
	Effet verre : panneau de verre translucide (tuile pleine en verre violet
	en sombre).
	inModifier : modificateur appliqué
	inTitle    : titre de la tuile ; null sans titre
	inOnClick  : action d'un clic ; null pour une tuile inerte
	inClickLabel : description de l'action (lecteurs d'écran)
	inAction   : élément à droite du titre (bouton, lien) ; null sans action
	inAccent   : couleur de la tuile (thème Coloré : type du compte) : aplat
	             pastel de cette couleur ; non précisée : tuile neutre
	inFilled   : tuile pleine, à la couleur principale, texte inversé (chiffre mis en avant)
	inTitleStyle : style du titre ; null : titre de tuile ordinaire
	inFooter   : mention en petit, en bas au milieu de la tuile (même si une voisine la rend plus haute) ; null sans
	inContent  : contenu
*/
@Composable
fun Tile(
	inModifier   : Modifier                          = Modifier,
	inTitle      : String?                           = null,
	inOnClick    : (() -> Unit)?                     = null,
	inClickLabel : String?                           = null,
	inAction     : (@Composable RowScope.() -> Unit)? = null,
	inAccent     : Color                             = Color.Unspecified,
	inFilled     : Boolean                           = false,
	inTitleStyle : TextStyle?                        = null,
	inFooter     : String?                           = null,
	inContent    : @Composable ColumnScope.() -> Unit
)
{
	val vBase     = MaterialTheme.colorScheme										// palette de l'appli
	val vDark     = vBase.surface.luminance() < 0.5f								// fond sombre
	val vHero     = when
	{
		isGlass() && vDark -> kGlassHero													// verre sombre : violet
		isColorful()       -> colorfulMain()												// accent Coloré
		else               -> vBase.primary												// encre
	}																				// fond d'une tuile pleine
	val vOnHero   = if (vHero.luminance() < 0.5f) Color.White else Color(0xFF0B1B26)	// texte sur la tuile pleine
	val vColors   = if (inFilled) vBase.copy(primary = vOnHero, onPrimary = vHero, surface = vHero, onSurface = vOnHero, onSurfaceVariant = vOnHero.copy(alpha = 0.78f),
		surfaceContainerLow = lerp(vHero, vOnHero, 0.08f), surfaceContainerHigh = lerp(vHero, vOnHero, 0.2f),
		outline = vOnHero.copy(alpha = 0.5f), outlineVariant = vHero) else vBase	// tuile pleine : couleurs inversées
	val vGlassy   = isGlass() && (!inFilled || vDark)								// panneau de verre (la tuile pleine reste pleine en clair)
	val vTint     = inAccent != Color.Unspecified									// tuile teintée
	val vSource   = remember { MutableInteractionSource() }						// survol et clics
	val vHovered  by vSource.collectIsHoveredAsState()							// souris au-dessus
	val vPressed  by vSource.collectIsPressedAsState()							// bouton de la souris enfoncé
	val vActive   = inOnClick != null && vHovered								// mise en avant du survol
	val vClick    = if (inOnClick != null) Modifier.clickable(interactionSource = vSource, indication = null, role = Role.Button, onClickLabel = inClickLabel, onClick = inOnClick) else Modifier
	val vRest     = if (vTint) lerp(vColors.surface, inAccent, if (vDark) 0.13f else 0.17f) else vColors.surface	// fond au repos : aplat pastel du type
	val vHover    = if (vTint) lerp(vColors.surface, inAccent, if (vDark) 0.19f else 0.25f) else vColors.surfaceContainerLow	// fond survolé
	val vFill     by animateColorAsState(if (vActive) vHover else vRest, tween(kHoverMillis), label = "fond")	// fond, en fondu
	val vLift     by animateFloatAsState(if (vActive) 1f else 0f, tween(kHoverMillis), label = "élévation")	// élévation au survol, de 0 à 1
	val vScale    by animateFloatAsState(if (inOnClick != null && vPressed) 0.985f else 1f, tween(kPressMillis), label = "appui")	// léger enfoncement au clic
	val vShade    = if (vDark) Color.Black else kShadowTint							// teinte de l'ombre
	Surface(
		modifier = inModifier
			.graphicsLayer { scaleX = vScale; scaleY = vScale; translationY = -2.dp.toPx() * vLift }
			.then(
				when
				{
					vGlassy && inFilled -> Modifier.glassHero(kTileShape, vLift)
					vGlassy             -> Modifier.glassPanel(kTileShape, vDark, inAccent, vLift)
					else                -> Modifier.shadow((10f + 10f * vLift).dp, kTileShape, ambientColor = vShade.copy(alpha = if (vDark) 0.5f else 0.10f), spotColor = vShade.copy(alpha = if (vDark) 0.6f else 0.16f))
				}
			)
			.hoverable(vSource).then(vClick),
		shape    = kTileShape,
		color    = if (vGlassy) Color.Transparent else vFill,
		contentColor = vColors.onSurface,
		border   = if (vDark && !vGlassy) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) else null	// sombre : arête à peine lisible (le verre a son liseré)
	) {
		MaterialTheme(colorScheme = vColors) {
			CompositionLocalProvider(LocalColorTheme provides if (inFilled) ColorTheme.MONOCHROME else LocalColorTheme.current) {	// tuile pleine : variations à l'encre de la tuile (lisibles sur l'aplat), signe écrit
				Column {
					Column(if (inFooter != null) Modifier.fillMaxHeight().padding(26.dp) else Modifier.padding(26.dp)) {
						if (inTitle != null || inAction != null)
						{
							Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
								Text(
									text     = inTitle.orEmpty(),
									style    = inTitleStyle ?: MaterialTheme.typography.titleMedium,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									modifier = Modifier.weight(1f)
								)
								inAction?.invoke(this)
							}
							androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
						}
						inContent()
					inFooter?.let { inText ->
						androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
						androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
						Text(inText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
					}
					}
				}
			}
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
	inColor       : couleur du trait ; non précisée : encre du thème
*/
@Composable
fun Sparkline(
	inSeries      : List<SeriesPoint>,
	inDescription : String,
	inModifier    : Modifier = Modifier,
	inHeight      : Dp       = 48.dp,
	inColor       : Color    = Color.Unspecified
)
{
	val vLine  = listOf(inColor, LocalAccent.current).firstOrNull { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface	// trait : couleur donnée, de la page, ou encre
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
