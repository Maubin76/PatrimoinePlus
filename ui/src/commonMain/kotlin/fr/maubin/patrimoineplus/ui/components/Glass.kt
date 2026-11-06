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

private val kSmoke       = Color(0xFF0E1217).copy(alpha = 0.58f)						// voile d'ardoise sous le verre sombre (lisibilité)
private val kVioletLight = Color(0xFF7B6CF6)										// violet du verre sombre, reflet
private val kVioletDeep  = Color(0xFF3B31B0)										// violet du verre sombre, profondeur

/*
	Effet verre actif.
*/
@Composable
fun isGlass(): Boolean = LocalGlass.current

/*
	Panneau de l'appli : surface unie (blanche en clair, un cran au-dessus du
	fond en sombre), filet à l'encre très léger, ombre neutre courte et douce
	en clair seulement ; un peu plus soutenu au survol.
	inShape : forme du panneau
	inDark  : fond sombre
	inTint  : sans effet (panneaux neutres ; gardé pour les appels existants)
	inLift  : survol, de 0 à 1
*/
fun Modifier.glassPanel(
	inShape : Shape,
	inDark  : Boolean,
	inTint  : Color = Color.Unspecified,
	inLift  : Float = 0f
): Modifier
{
	val vFill = if (inDark) lerp(Color(0xFF181D24), Color(0xFF1E242C), inLift) else lerp(Color.White, Color(0xFFFBFCFD), inLift)	// surface
	val vLine = if (inDark) Color.White.copy(alpha = 0.07f) else Color(0xFF14171C).copy(alpha = 0.07f)							// filet
	return this
		.then(if (inDark) Modifier else Modifier.shadow((4f + 4f * inLift).dp, inShape, ambientColor = Color(0xFF14171C).copy(alpha = 0.05f), spotColor = Color(0xFF14171C).copy(alpha = 0.07f)))
		.background(vFill, inShape)
		.border(1.dp, vLine, inShape)
}

/*
	Tuile du chiffre mis en avant pleine : plus jamais (brume lumineuse : le
	chiffre se pose sur le verre ou à même le fond).
*/
@Composable
fun heroFilled(): Boolean = false

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
	Fond creusé (piste d'un choix, puce, rail d'une barre, pastille) : voile
	d'encre léger, sur toute surface ; sinon la couleur donnée.
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

