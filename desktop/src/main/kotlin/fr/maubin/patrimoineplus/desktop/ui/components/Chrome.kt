package fr.maubin.patrimoineplus.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.isNight

private const val kHoverMillis = 140											// fondu du survol
private val kPill              = RoundedCornerShape(50)							// pilule
val kChromeButton              = 40.dp											// diamètre d'un bouton rond de la coque

// Ligne d'état de la page (source des données, dernier enregistrement), fournie par la coque ; vide sans.
val LocalPageStatus = staticCompositionLocalOf { "" }

/*
	Info-bulle de la coque : pilule à l'encre, sous l'élément par défaut.
	inLabel   : texte
	inSide    : côté de l'info-bulle (BottomCenter, TopCenter, CenterEnd, CenterStart)
	inContent : élément survolé
*/
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChromeTooltip(
	inLabel   : String,
	inSide    : Alignment = Alignment.BottomCenter,
	inContent : @Composable () -> Unit
)
{
	val vOffset = when (inSide)
	{
		Alignment.CenterEnd   -> DpOffset(8.dp, 0.dp)
		Alignment.CenterStart -> DpOffset((-8).dp, 0.dp)
		Alignment.TopCenter   -> DpOffset(0.dp, (-8).dp)
		else                  -> DpOffset(0.dp, 8.dp)
	}																				// écart à l'élément survolé
	val vColors = MaterialTheme.colorScheme										// palette courante
	TooltipArea(
		tooltip          = {
			Surface(shape = kPill, color = vColors.inverseSurface, contentColor = vColors.inverseOnSurface, shadowElevation = 4.dp) {
				Text(inLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
			}
		},
		delayMillis      = 350,
		tooltipPlacement = TooltipPlacement.ComponentRect(anchor = inSide, alignment = inSide, offset = vOffset),
		content          = inContent
	)
}

/*
	Bouton rond de la coque : verre blanc translucide, liseré blanc, icône
	seule ; nom en info-bulle et pour les lecteurs d'écran.
	inIcon    : icône
	inLabel   : nom de l'action
	inOnClick : action
	inModifier : modificateur appliqué
*/
@Composable
fun ChromeButton(
	inIcon     : ImageVector,
	inLabel    : String,
	inOnClick  : () -> Unit,
	inModifier : Modifier = Modifier
)
{
	val vNight   = isNight()															// fond sombre
	val vSource  = remember { MutableInteractionSource() }							// survol
	val vHovered by vSource.collectIsHoveredAsState()								// souris au-dessus
	val vFill    by animateColorAsState((if (vNight) Color.White.copy(alpha = if (vHovered) 0.12f else 0.06f) else (if (vHovered) Color(0xFFF4F6F8) else Color.White)), tween(kHoverMillis), label = "rond")	// fond
	ChromeTooltip(inLabel) {
		Box(
			modifier         = inModifier
				.size(kChromeButton)
				.background(vFill, CircleShape)
				.border(1.dp, fr.maubin.patrimoineplus.ui.components.chromeLine(vNight), CircleShape)
				.hoverable(vSource)
				.clickable(interactionSource = vSource, indication = null, role = Role.Button, onClick = inOnClick)
				.semantics { contentDescription = inLabel },
			contentAlignment = Alignment.Center
		) {
			Icon(inIcon, contentDescription = null, modifier = Modifier.size(18.dp))
		}
	}
}

/*
	Entrée de la barre de navigation.
	label : nom de la page
	icon  : icône
*/
data class NavEntry(
	val label : String,
	val icon  : ImageVector
)

/*
	Barre de navigation en pilules : un rond translucide par page, la page
	active en pilule blanche avec son nom ; noms des autres en info-bulle et
	pour les lecteurs d'écran (onglets).
	inEntries  : pages
	inActive   : rang de la page active
	inOnSelect : change de page
	inSide     : côté des info-bulles : null ou TopCenter pour une rangée (page active avec son nom) ; CenterEnd ou CenterStart pour une colonne d'icônes
	inModifier : modificateur appliqué
*/
@Composable
fun NavPills(
	inEntries  : List<NavEntry>,
	inActive   : Int,
	inOnSelect : (Int) -> Unit,
	inModifier : Modifier   = Modifier,
	inSide     : Alignment? = null
)
{
	val vNight    = isNight()														// fond sombre
	val vVertical = inSide == Alignment.CenterEnd || inSide == Alignment.CenterStart	// colonne d'icônes
	val vItems = @Composable {
		inEntries.forEachIndexed { inIndex, inEntry ->
			val vActive  = inIndex == inActive													// page active
			val vSource  = remember { MutableInteractionSource() }								// survol
			val vHovered by vSource.collectIsHoveredAsState()									// souris au-dessus
			val vFill    by animateColorAsState(
				when
				{
					vActive && vNight -> Color.White.copy(alpha = 0.12f)
					vActive           -> Color.White
					vNight            -> Color.White.copy(alpha = if (vHovered) 0.08f else 0f)
					else              -> if (vHovered) Color.White.copy(alpha = 0.7f) else Color.Transparent
				}, tween(kHoverMillis), label = "onglet")												// fond
			val vPill = @Composable {
				Row(
					modifier          = Modifier
						.height(kChromeButton)
						.background(vFill, kPill)
						.border(1.dp, if (vActive) fr.maubin.patrimoineplus.ui.components.chromeLine(vNight) else Color.Transparent, kPill)
						.hoverable(vSource)
						.clickable(interactionSource = vSource, indication = null, role = Role.Tab) { inOnSelect(inIndex) }
						.semantics { contentDescription = inEntry.label; selected = vActive }
						.padding(horizontal = if (vActive) 16.dp else 11.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(inEntry.icon, contentDescription = null, modifier = Modifier.size(18.dp))
					if (vActive && !vVertical)
					{
						Spacer(Modifier.width(8.dp))
						Text(inEntry.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
					}
				}
			}
			if (vActive && !vVertical) vPill() else ChromeTooltip(inEntry.label, inSide ?: Alignment.BottomCenter) { vPill() }
		}
	}																				// pilules
	if (!vVertical) Row(inModifier.animateContentSize(tween(220)), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { vItems() }
	else Column(inModifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { vItems() }
}

/*
	Ligne de titre d'une page : grand titre léger et ligne d'état dessous à
	gauche, actions en pilules au milieu, action principale à droite.
	inTitle    : titre de la page
	inSubtitle : ligne sous le titre ; null : ligne d'état de la coque
	inActions  : pilules d'actions secondaires
	inPrimary  : action principale (bouton jaune) ; null sans
	inModifier : modificateur appliqué
*/
@Composable
fun PageHeader(
	inTitle    : String,
	inSubtitle : String?                           = null,
	inActions  : @Composable RowScope.() -> Unit   = {},
	inPrimary  : (@Composable () -> Unit)?         = null,
	inModifier : Modifier                          = Modifier
)
{
	Row(inModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			Text(inTitle, style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
			val vLine = inSubtitle ?: LocalPageStatus.current									// ligne sous le titre
			if (vLine.isNotEmpty())
			{
				Spacer(Modifier.height(4.dp))
				Text(vLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = inActions)
		if (inPrimary != null)
		{
			Spacer(Modifier.width(56.dp))
			inPrimary()
		}
	}
}
