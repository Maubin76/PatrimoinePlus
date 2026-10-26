package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.shadow

// Effet verre de l'appli PC (réglage), fourni par l'appli ; faux par défaut (téléphone).
val LocalGlass = staticCompositionLocalOf { false }

private val kSmoke       = Color(0xFF111118).copy(alpha = 0.62f)						// voile fumé sous le verre sombre (lisibilité)
private val kVioletLight = Color(0xFF7B6CF6)										// violet du verre sombre, reflet
private val kVioletDeep  = Color(0xFF3B31B0)										// violet du verre sombre, profondeur

/*
	Effet verre actif.
*/
@Composable
fun isGlass(): Boolean = LocalGlass.current

/*
	Panneau de verre : aplat translucide plus clair en haut à gauche (la
	lumière), liseré lumineux qui s'éteint vers le bas à droite ; sans ombre
	portée, qui se verrait à travers. En clair, verre dépoli blanc ; en
	sombre, verre fumé.
	inShape : forme du panneau
	inDark  : fond sombre
	inTint  : teinte du verre (thème Coloré : type du compte) ; non précisée : verre neutre
	inLift  : élévation au survol, de 0 à 1
*/
fun Modifier.glassPanel(
	inShape : Shape,
	inDark  : Boolean,
	inTint  : Color = Color.Unspecified,
	inLift  : Float = 0f
): Modifier
{
	val vTinted = inTint != Color.Unspecified											// verre teinté
	val vTop    = when
	{
		inDark && vTinted -> inTint.copy(alpha = 0.20f + 0.06f * inLift)
		inDark            -> Color.White.copy(alpha = 0.09f + 0.03f * inLift)
		vTinted           -> lerp(Color.White, inTint, 0.22f).copy(alpha = 0.86f + 0.06f * inLift)
		else              -> Color.White.copy(alpha = 0.84f + 0.06f * inLift)
	}																				// verre, côté lumière
	val vLow    = when
	{
		inDark && vTinted -> inTint.copy(alpha = 0.06f)
		inDark            -> Color.White.copy(alpha = 0.03f)
		vTinted           -> lerp(Color.White, inTint, 0.32f).copy(alpha = 0.68f)
		else              -> Color.White.copy(alpha = 0.66f)
	}																				// verre, côté ombre
	val vEdge   = if (inDark) listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.03f)) else listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.30f))	// liseré
	return this																		// pas d'ombre : elle se verrait à travers le verre
		.then(if (inDark) Modifier.background(kSmoke, inShape) else Modifier)			// sombre : verre fumé, qui atténue le fond
		.background(Brush.linearGradient(listOf(vTop, vLow), start = Offset.Zero, end = Offset.Infinite), inShape)
		.border(1.dp, Brush.linearGradient(vEdge, start = Offset.Zero, end = Offset.Infinite), inShape)
}

/*
	Tuile du chiffre mis en avant pleine : thème Coloré, ou verre sombre
	(violet).
*/
@Composable
fun heroFilled(): Boolean =
	isColorful() || (isGlass() && MaterialTheme.colorScheme.surface.luminance() < 0.5f)

/*
	Verre plein du chiffre mis en avant, en sombre : dégradé violet, liseré
	lumineux, ombre profonde.
	inShape : forme du panneau
	inLift  : élévation au survol, de 0 à 1
*/
fun Modifier.glassHero(
	inShape : Shape,
	inLift  : Float = 0f
): Modifier =
	this.shadow((18f + 8f * inLift).dp, inShape, ambientColor = kVioletDeep.copy(alpha = 0.6f), spotColor = kVioletDeep.copy(alpha = 0.8f))
		.background(Brush.linearGradient(listOf(kVioletLight, kVioletDeep), start = Offset.Zero, end = Offset.Infinite), inShape)
		.border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f)), start = Offset.Zero, end = Offset.Infinite), inShape)

// Couleur moyenne du verre plein violet (texte et repères posés dessus).
val kGlassHero = Color(0xFF5A4DD8)

/*
	Fond creusé (piste d'un choix, puce, rail d'une barre, pastille) : sur le
	verre, voile d'encre translucide qui laisse passer le verre ; sinon la
	couleur donnée.
	inColor : couleur hors effet verre
*/
@Composable
fun glassWell(inColor: Color): Color =
	if (isGlass()) MaterialTheme.colorScheme.onSurface.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.08f else 0.06f) else inColor

/*
	Fond posé (ligne d'une liste, pilule au repos) : sur le verre, voile clair
	translucide ; sinon la couleur donnée.
	inColor : couleur hors effet verre
*/
@Composable
fun glassRaised(inColor: Color): Color =
	if (isGlass()) Color.White.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.04f else 0.5f) else inColor

