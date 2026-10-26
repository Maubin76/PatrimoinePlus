package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

private val kShadowTint = Color(0xFF5B4E9E)											// teinte des ombres (lavande)

/*
	Surface flottante (téléphone et PC) : fond des panneaux, sans bord, posée sur
	le fond par une ombre douce teintée de lavande (noire en sombre) ; avec
	l'effet verre, panneau de verre translucide.
	inShape    : forme
	inModifier : modificateur appliqué
	inSolid    : posée au-dessus d'un contenu qui défile (pilule de navigation) : sous le verre, un
	             voile du fond des panneaux, pour que le contenu ne se lise pas à travers
	inContent  : contenu
*/
@Composable
fun FloatingSurface(
	inShape    : Shape    = RoundedCornerShape(28.dp),
	inModifier : Modifier = Modifier,
	inSolid    : Boolean  = false,
	inContent  : @Composable () -> Unit
)
{
	val vDark  = MaterialTheme.colorScheme.surface.luminance() < 0.5f				// fond sombre
	val vShade = if (vDark) Color.Black else kShadowTint								// teinte de l'ombre
	val vGlass = isGlass()															// effet verre
	Surface(
		modifier     = if (vGlass) inModifier.then(if (inSolid) Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), inShape) else Modifier).glassPanel(inShape, vDark) else inModifier.shadow(12.dp, inShape, ambientColor = vShade.copy(alpha = if (vDark) 0.5f else 0.10f), spotColor = vShade.copy(alpha = if (vDark) 0.6f else 0.16f)),
		shape        = inShape,
		color        = if (vGlass) Color.Transparent else MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border       = if (vDark && !vGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) else null,
		content      = inContent
	)
}
