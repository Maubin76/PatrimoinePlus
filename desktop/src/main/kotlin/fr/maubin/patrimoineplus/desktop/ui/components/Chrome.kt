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
	Info-bulle de la coque : pilule à l'encre, sous l'élément.
	inLabel   : texte
	inContent : élément survolé
*/
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChromeTooltip(
	inLabel   : String,
	inContent : @Composable () -> Unit
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	TooltipArea(
		tooltip          = {
			Surface(shape = kPill, color = vColors.inverseSurface, contentColor = vColors.inverseOnSurface, shadowElevation = 4.dp) {
				Text(inLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
			}
		},
		delayMillis      = 350,
		tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, alignment = Alignment.BottomCenter, offset = DpOffset(0.dp, 8.dp)),
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
	val vFill    by animateColorAsState(Color.White.copy(alpha = if (vNight) (if (vHovered) 0.12f else 0.06f) else (if (vHovered) 0.88f else 0.55f)), tween(kHoverMillis), label = "rond")	// fond
	ChromeTooltip(inLabel) {
		Box(
			modifier         = inModifier
				.size(kChromeButton)
				.background(vFill, CircleShape)
				.border(1.dp, Color.White.copy(alpha = if (vNight) 0.1f else 0.9f), CircleShape)
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
	inModifier : modificateur appliqué
*/
@Composable
fun NavPills(
	inEntries  : List<NavEntry>,
	inActive   : Int,
	inOnSelect : (Int) -> Unit,
	inModifier : Modifier = Modifier
)
{
	val vNight = isNight()															// fond sombre
	Row(inModifier.animateContentSize(tween(220)), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
		inEntries.forEachIndexed { inIndex, inEntry ->
			val vActive  = inIndex == inActive													// page active
			val vSource  = remember { MutableInteractionSource() }								// survol
			val vHovered by vSource.collectIsHoveredAsState()									// souris au-dessus
			val vFill    by animateColorAsState(
				when
				{
					vActive && vNight -> Color.White.copy(alpha = 0.16f)
					vActive           -> Color.White
					vNight            -> Color.White.copy(alpha = if (vHovered) 0.12f else 0.06f)
					else              -> Color.White.copy(alpha = if (vHovered) 0.88f else 0.5f)
				}, tween(kHoverMillis), label = "onglet")												// fond
			val vPill = @Composable {
				Row(
					modifier          = Modifier
						.height(kChromeButton)
						.background(vFill, kPill)
						.border(1.dp, Color.White.copy(alpha = if (vNight) 0.1f else 0.9f), kPill)
						.hoverable(vSource)
						.clickable(interactionSource = vSource, indication = null, role = Role.Tab) { inOnSelect(inIndex) }
						.semantics { contentDescription = inEntry.label; selected = vActive }
						.padding(horizontal = if (vActive) 16.dp else 11.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(inEntry.icon, contentDescription = null, modifier = Modifier.size(18.dp))
					if (vActive)
					{
						Spacer(Modifier.width(8.dp))
						Text(inEntry.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
					}
				}
			}
			if (vActive) vPill() else ChromeTooltip(inEntry.label) { vPill() }
		}
	}
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
