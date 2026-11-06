package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.calc.parseHexColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn

private val kInk = Color(0xFF14171C)												// encre du texte posé sur le fond (clair)

/*
	Fond choisi par l'utilisateur (réglage propre à chaque appareil).
	color : couleur choisie, pour le mode clair (le sombre en est déduit) ; null : fond par défaut
	photo : photo décodée, prioritaire sur la couleur ; null sans photo
	blur  : intensité du flou de la photo, de 0 (léger) à 1 (très flou)
	veil  : voile de lisibilité sur la photo, de 0 (photo franche) à 1 (très voilée)
*/
data class Backdrop(
	val color : Color?       = null,
	val photo : ImageBitmap? = null,
	val blur  : Float        = kBackdropBlur,
	val veil  : Float        = kBackdropVeil
)

const val kBackdropBlur = 0.6f														// flou proposé
const val kBackdropVeil = 0.4f														// voile proposé

/*
	Teinte proposée pour le fond.
	name  : nom affiché
	color : couleur (mode clair)
*/
data class BackdropTint(
	val name  : String,
	val color : Color
)

// Teintes proposées : la première est le fond par défaut ; les autres, franches
// mais toutes lisibles sous l'encre (contraste ≥ 10:1).
val kBackdropTints = listOf(
	BackdropTint("Brume", Color(0xFFE0E6EB)),
	BackdropTint("Pierre", Color(0xFFCFC6B6)),
	BackdropTint("Sable", Color(0xFFEBCD99)),
	BackdropTint("Rose poudré", Color(0xFFEFB7C3)),
	BackdropTint("Lavande", Color(0xFFCBBBF0)),
	BackdropTint("Ciel", Color(0xFFA9CBEF)),
	BackdropTint("Menthe", Color(0xFFA2D9C4)),
	BackdropTint("Sauge", Color(0xFFB6D0AA))
)

/*
	Couleur d'un code « #RRGGBB » (ou « #RGB ») ; null s'il est illisible.
	inText : code saisi
*/
fun parseBackdropColor(inText: String): Color? = parseHexColor(inText.trim())?.let { Color(it.toInt()) }

/*
	Code « #RRGGBB » d'une couleur.
	inColor : couleur
*/
fun backdropCode(inColor: Color): String = "#%06X".format(inColor.toArgb() and 0xFFFFFF)

/*
	Contraste entre l'encre du texte et une couleur de fond (de 1 à 21).
	inColor : couleur de fond (mode clair)
*/
fun backdropContrast(inColor: Color): Float
{
	val vA = inColor.luminance()														// luminance du fond
	val vB = kInk.luminance()															// luminance de l'encre
	return (max(vA, vB) + 0.05f) / (min(vA, vB) + 0.05f)
}

/*
	Version sombre d'une couleur de fond : même teinte, très foncée et peu
	saturée, pour que le texte clair reste lisible.
	inColor : couleur (mode clair)
*/
fun darkBackdrop(inColor: Color): Color
{
	val (vH, vS, _) = hsl(inColor)														// teinte et saturation
	return hslColor(vH, min(vS, 0.28f), 0.085f)
}

/*
	Deux tons d'un fond uni (haut gauche, bas droit) : la couleur, à peine
	éclaircie en haut et assombrie en bas (simple variation de ton).
	inColor : couleur choisie (mode clair)
	inDark  : mode sombre
*/
fun backdropTones(
	inColor : Color,
	inDark  : Boolean
): Pair<Color, Color>
{
	val vBase = if (inDark) darkBackdrop(inColor) else inColor							// couleur du mode
	return lerp(vBase, Color.White, if (inDark) 0.03f else 0.18f) to lerp(vBase, Color.Black, if (inDark) 0.12f else 0.03f)
}

/*
	Voile posé sur la photo : blanc en clair, noir en sombre (un voile noir
	de base assombrit toujours la photo en sombre).
	inDark : mode sombre
	inVeil : réglage du voile, de 0 à 1
*/
fun backdropVeil(
	inDark : Boolean,
	inVeil : Float
): Color = if (inDark) Color.Black.copy(alpha = 0.35f + 0.55f * inVeil) else Color.White.copy(alpha = 0.12f + 0.7f * inVeil)

/*
	Couleur moyenne d'une image (barre de titre du PC sur une photo).
	inImage : image
*/
fun averageColor(inImage: ImageBitmap): Color
{
	val vDot = ImageBitmap(1, 1)														// image d'un pixel
	CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f), LayoutDirection.Ltr, Canvas(vDot), Size(1f, 1f)) {
		drawImage(inImage, srcOffset = IntOffset.Zero, srcSize = IntSize(inImage.width, inImage.height), dstSize = IntSize(1, 1), filterQuality = FilterQuality.High)
	}
	val vPixels = IntArray(1)															// pixel lu
	vDot.readPixels(vPixels)
	return Color(vPixels[0])
}

/*
	Couleur du haut du fond (barre de titre du PC), selon le fond choisi.
	inDark     : mode sombre
	inBackdrop : fond choisi
	inDefault  : couleur du fond par défaut
*/
fun backdropTop(
	inDark     : Boolean,
	inBackdrop : Backdrop,
	inDefault  : Color
): Color
{
	val vPhoto = inBackdrop.photo																	// photo choisie
	return when
	{
		vPhoto != null           -> backdropVeil(inDark, inBackdrop.veil).let { inVeil -> lerp(averageColor(vPhoto), inVeil.copy(alpha = 1f), inVeil.alpha) }
		inBackdrop.color != null -> backdropTones(inBackdrop.color, inDark).first
		else                     -> inDefault
	}
}

/*
	Fond uni de la couleur choisie (deux tons du même neutre).
	inColor : couleur choisie (mode clair)
	inDark  : mode sombre
*/
@Composable
fun ColorBackdrop(
	inColor : Color,
	inDark  : Boolean
)
{
	val (vTop, vBottom) = backdropTones(inColor, inDark)								// deux tons
	Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(vTop, vBottom), start = Offset.Zero, end = Offset.Infinite)))
}

/*
	Photo en fond : recadrée pour couvrir la zone, floutée (petite image
	agrandie par étapes, calculée une fois pour toutes tant que la taille et
	les réglages ne changent pas), puis voilée pour la lisibilité.
	inPhoto : photo décodée
	inDark  : mode sombre
	inBlur  : intensité du flou, de 0 à 1
	inVeil  : voile, de 0 à 1
*/
@Composable
fun PhotoBackdrop(
	inPhoto : ImageBitmap,
	inDark  : Boolean,
	inBlur  : Float,
	inVeil  : Float
)
{
	key(inPhoto, inDark, inBlur, inVeil) {
		Box(
			Modifier
				.fillMaxSize()
				.background(if (inDark) Color(0xFF0F1318) else Color(0xFFE0E6EB))
				.drawWithCache {
					val vRadius = (6f + 70f * inBlur.coerceIn(0f, 1f)).dp.toPx()				// rayon du flou
					val vImage  = softImage(this, vRadius) {
						val vScale = max(size.width / inPhoto.width, size.height / inPhoto.height)	// recadrage : la photo couvre la zone
						val vW     = inPhoto.width * vScale											// largeur dessinée
						val vH     = inPhoto.height * vScale										// hauteur dessinée
						drawImage(
							inPhoto,
							dstOffset     = IntOffset(((size.width - vW) / 2f).roundToInt(), ((size.height - vH) / 2f).roundToInt()),
							dstSize       = IntSize(max(1, vW.roundToInt()), max(1, vH.roundToInt())),
							filterQuality = FilterQuality.High
						)
					}																		// photo floutée, calculée une fois
					val vVeil = backdropVeil(inDark, inVeil)										// voile
					onDrawBehind {
						drawImage(vImage)
						drawRect(vVeil)
					}
				}
		)
	}
}

/*
	Teinte, saturation et luminosité (TSL) d'une couleur, de 0 à 1.
	inColor : couleur
*/
private fun hsl(inColor: Color): Triple<Float, Float, Float>
{
	val vMax = max(inColor.red, max(inColor.green, inColor.blue))						// composante la plus forte
	val vMin = min(inColor.red, min(inColor.green, inColor.blue))						// composante la plus faible
	val vL   = (vMax + vMin) / 2f															// luminosité
	if (vMax == vMin) return Triple(0f, 0f, vL)
	val vD = vMax - vMin																// écart
	val vS = if (vL > 0.5f) vD / (2f - vMax - vMin) else vD / (vMax + vMin)			// saturation
	val vH = when (vMax)
	{
		inColor.red   -> ((inColor.green - inColor.blue) / vD + (if (inColor.green < inColor.blue) 6f else 0f)) / 6f
		inColor.green -> ((inColor.blue - inColor.red) / vD + 2f) / 6f
		else          -> ((inColor.red - inColor.green) / vD + 4f) / 6f
	}																					// teinte
	return Triple(vH, vS, vL)
}

/*
	Couleur d'une teinte, d'une saturation et d'une luminosité (TSL).
	inH : teinte, de 0 à 1
	inS : saturation, de 0 à 1
	inL : luminosité, de 0 à 1
*/
private fun hslColor(
	inH : Float,
	inS : Float,
	inL : Float
): Color
{
	val vC = (1f - abs(2f * inL - 1f)) * inS											// chroma
	val vX = vC * (1f - abs((inH * 6f) % 2f - 1f))										// composante intermédiaire
	val vM = inL - vC / 2f																// décalage
	val (vR, vG, vB) = when ((inH * 6f).toInt().coerceIn(0, 5))
	{
		0    -> Triple(vC, vX, 0f)
		1    -> Triple(vX, vC, 0f)
		2    -> Triple(0f, vC, vX)
		3    -> Triple(0f, vX, vC)
		4    -> Triple(vX, 0f, vC)
		else -> Triple(vC, 0f, vX)
	}
	return Color(vR + vM, vG + vM, vB + vM)
}

/*
	Réglage du fond (téléphone et PC, propre à chaque appareil) : teintes
	proposées (la première, « Brume », est le fond par défaut), couleur libre
	en code « #RRGGBB » avec une alerte si le texte devient peu lisible, photo
	à choisir ou retirer, puis flou et voile de la photo (enregistrés au
	lâcher du curseur : le flou n'est pas recalculé pendant le glissement).
	inCode          : fond choisi : vide (défaut), « #RRGGBB » ou « photo »
	inBlur          : flou de la photo, de 0 à 100
	inVeil          : voile de la photo, de 0 à 100
	inOnColor       : choisit une couleur (code) ; null pour le fond par défaut
	inOnPickPhoto   : ouvre le choix d'une photo
	inOnRemovePhoto : retire la photo
	inOnBlur        : règle le flou
	inOnVeil        : règle le voile
*/
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackdropSettings(
	inCode          : String,
	inBlur          : Int,
	inVeil          : Int,
	inOnColor       : (String?) -> Unit,
	inOnPickPhoto   : () -> Unit,
	inOnRemovePhoto : () -> Unit,
	inOnBlur        : (Int) -> Unit,
	inOnVeil        : (Int) -> Unit
)
{
	val vMuted  = MaterialTheme.colorScheme.onSurfaceVariant									// texte secondaire
	val vPhoto  = inCode == "photo"																// photo choisie
	val vChosen = if (inCode.startsWith("#")) parseBackdropColor(inCode) else null			// couleur choisie
	var vText   by remember(inCode) { mutableStateOf(if (vChosen != null) backdropCode(vChosen) else "") }	// couleur libre saisie
	val vTyped  = parseBackdropColor(vText)														// couleur libre lue
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text("Fond", style = MaterialTheme.typography.titleMedium)
		Text("Une couleur unie, ou une photo floutée ; propre à cet appareil.", style = MaterialTheme.typography.bodyMedium, color = vMuted)
		FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			kBackdropTints.forEachIndexed { inIndex, inTint ->
				val vSelected = !vPhoto && if (inIndex == 0) vChosen == null else vChosen == inTint.color	// teinte choisie
				Swatch(inTint, vSelected) { inOnColor(if (inIndex == 0) null else backdropCode(inTint.color)) }
			}
		}
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			OutlinedTextField(
				value         = vText,
				onValueChange = { vText = it.take(7) },
				label         = { Text("Couleur libre") },
				placeholder   = { Text("#E0E6EB") },
				singleLine    = true,
				modifier      = Modifier.weight(1f, fill = false).widthIn(max = 200.dp)
			)
			GhostButton(onClick = { inOnColor(vText) }, enabled = vTyped != null) { Text("Appliquer", maxLines = 1) }
		}
		if (vTyped != null && backdropContrast(vTyped) < 4.5f)
		{
			Text(
				"Peu lisible : le texte manquerait de contraste sur cette couleur (${(backdropContrast(vTyped) * 10).roundToInt() / 10f}:1, il en faut 4,5:1).",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.error
			)
		}
		FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			GhostButton(onClick = inOnPickPhoto) { Text(if (vPhoto) "Changer de photo…" else "Choisir une photo…", maxLines = 1) }
			if (vPhoto) GhostButton(onClick = inOnRemovePhoto) { Text("Retirer la photo", maxLines = 1) }
		}
		if (vPhoto)
		{
			BackdropSlider("Flou", "Léger", "Très flou", inBlur, inOnBlur)
			BackdropSlider("Voile", "Photo franche", "Très voilée", inVeil, inOnVeil)
		}
	}
}

/*
	Pastille ronde d'une teinte proposée : anneau à l'encre quand elle est
	choisie ; nom pour les lecteurs d'écran.
	inTint     : teinte
	inSelected : teinte choisie
	inOnClick  : choix
*/
@Composable
private fun Swatch(
	inTint     : BackdropTint,
	inSelected : Boolean,
	inOnClick  : () -> Unit
)
{
	val vInk = MaterialTheme.colorScheme.onSurface												// encre de l'anneau
	Box(
		modifier = Modifier
			.size(40.dp)
			.then(if (inSelected) Modifier.border(2.dp, vInk, CircleShape) else Modifier)
			.padding(4.dp)
			.background(inTint.color, CircleShape)
			.border(1.dp, chromeLine(isNight()), CircleShape)
			.clickable(role = Role.RadioButton, onClick = inOnClick)
			.semantics { contentDescription = "Fond ${inTint.name}"; selected = inSelected }
	)
}

/*
	Curseur d'un réglage de la photo (0 à 100) : libellé et bornes ; la
	valeur suit le doigt et n'est enregistrée qu'au lâcher.
	inLabel   : libellé
	inLow     : sens de la borne basse
	inHigh    : sens de la borne haute
	inValue   : valeur enregistrée
	inOnValue : enregistre la valeur
*/
@Composable
private fun BackdropSlider(
	inLabel   : String,
	inLow     : String,
	inHigh    : String,
	inValue   : Int,
	inOnValue : (Int) -> Unit
)
{
	var vValue by remember(inValue) { mutableFloatStateOf(inValue.toFloat()) }				// valeur pendant le glissement
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant										// texte secondaire
	Column {
		Text(inLabel, style = MaterialTheme.typography.bodyLarge)
		Slider(
			value                 = vValue,
			onValueChange         = { vValue = it },
			onValueChangeFinished = { inOnValue(vValue.roundToInt()) },
			valueRange            = 0f..100f,
			modifier              = Modifier.fillMaxWidth().widthIn(max = 360.dp).semantics { contentDescription = inLabel }
		)
		Row(Modifier.fillMaxWidth().widthIn(max = 360.dp)) {
			Text(inLow, style = MaterialTheme.typography.bodySmall, color = vMuted, modifier = Modifier.weight(1f))
			Text(inHigh, style = MaterialTheme.typography.bodySmall, color = vMuted)
		}
	}
}
