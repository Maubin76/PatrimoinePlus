package fr.maubin.patrimoineplus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private const val kPillMillis = 180												// fondu du choix actif

/*
	Choix segmenté en pilules (téléphone et PC) : piste arrondie (voile
	translucide avec l'effet verre), choix actif sur une pilule pleine à
	l'encre de la coque ; plus de quatre choix : texte plus petit, sur une
	ligne.
	inOptions  : choix proposés
	inSelected : choix actif
	inLabel    : libellé d'un choix
	inOnSelect : nouveau choix
	inEnabled  : choix modifiable
*/
@Composable
fun <T> PillChoice(
	inOptions  : List<T>,
	inSelected : T,
	inLabel    : (T) -> String,
	inOnSelect : (T) -> Unit,
	inEnabled  : Boolean = true
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	Row(
		modifier              = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(glassWell(vColors.surfaceContainer)).padding(4.dp).selectableGroup(),
		horizontalArrangement = Arrangement.spacedBy(4.dp)
	) {
		inOptions.forEach { inOption ->
			val vActive = inOption == inSelected										// choix actif
			val vFill   by animateColorAsState(if (vActive) vColors.primary else Color.Transparent, tween(kPillMillis), label = "choix")	// fond de la pilule
			Box(
				modifier         = Modifier
					.weight(1f)
					.height(40.dp)
					.clip(RoundedCornerShape(50))
					.background(vFill)
					.selectable(selected = vActive, enabled = inEnabled, role = Role.RadioButton) { inOnSelect(inOption) },
				contentAlignment = Alignment.Center
			) {
				Text(
					text     = inLabel(inOption),
					style    = if (inOptions.size > 4) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
					color    = when { vActive -> vColors.onPrimary; inEnabled -> vColors.onSurface; else -> vColors.onSurface.copy(alpha = 0.38f) },
					maxLines = 1,
					softWrap = false
				)
			}
		}
	}
}
