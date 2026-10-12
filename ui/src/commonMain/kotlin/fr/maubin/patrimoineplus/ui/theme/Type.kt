package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontStyle
import fr.maubin.patrimoineplus.ui.resources.Res
import fr.maubin.patrimoineplus.ui.resources.hanken_grotesk
import org.jetbrains.compose.resources.Font

/*
	Construit une graisse de la police variable Hanken Grotesk (ressource
	partagée par les applis Android et PC).
	inWeight : graisse voulue
*/
@Composable
private fun hanken(inWeight: FontWeight): Font =
	Font(
		resource          = Res.font.hanken_grotesk,
		weight            = inWeight,
		style             = FontStyle.Normal,
		variationSettings = FontVariation.Settings(FontVariation.weight(inWeight.weight))
	)

/*
	Grotesque unique de l'application ; ses chiffres sont à chasse fixe.
*/
@Composable
fun hankenGrotesk(): FontFamily =
	FontFamily(
		hanken(FontWeight.Normal),
		hanken(FontWeight.Medium),
		hanken(FontWeight.SemiBold)
	)

private val kBase = Typography()										// échelle Material 3 de référence

/*
	Applique la grotesque et une graisse à un style de l'échelle Material.
	inFamily   : grotesque
	inStyle    : style de référence
	inWeight   : graisse à appliquer
	inTracking : interlettrage, en em ; null conserve celui du style
*/
private fun grotesk(
	inFamily   : FontFamily,
	inStyle    : TextStyle,
	inWeight   : FontWeight,
	inTracking : Float? = null
): TextStyle =
	inStyle.copy(
		fontFamily    = inFamily,
		fontWeight    = inWeight,
		letterSpacing = inTracking?.em ?: inStyle.letterSpacing
	)

/*
	Échelle typographique : total et titres en demi-gras serré, texte courant
	sans interlettrage.
*/
@Composable
fun monoTypography(): Typography
{
	val vFamily = hankenGrotesk()												// grotesque chargée
	return Typography(
		displayLarge   = grotesk(vFamily, kBase.displayLarge, FontWeight.SemiBold, -0.035f),
		displayMedium  = grotesk(vFamily, kBase.displayMedium, FontWeight.SemiBold, -0.03f),
		displaySmall   = grotesk(vFamily, kBase.displaySmall, FontWeight.SemiBold, -0.025f),
		headlineLarge  = grotesk(vFamily, kBase.headlineLarge, FontWeight.SemiBold, -0.02f),
		headlineMedium = grotesk(vFamily, kBase.headlineMedium, FontWeight.SemiBold, -0.015f),
		headlineSmall  = grotesk(vFamily, kBase.headlineSmall, FontWeight.SemiBold, -0.01f),
		titleLarge     = grotesk(vFamily, kBase.titleLarge, FontWeight.SemiBold, -0.005f),
		titleMedium    = grotesk(vFamily, kBase.titleMedium, FontWeight.Medium, 0f),
		titleSmall     = grotesk(vFamily, kBase.titleSmall, FontWeight.Medium, 0f),
		bodyLarge      = grotesk(vFamily, kBase.bodyLarge, FontWeight.Normal, 0f),
		bodyMedium     = grotesk(vFamily, kBase.bodyMedium, FontWeight.Normal, 0f),
		bodySmall      = grotesk(vFamily, kBase.bodySmall, FontWeight.Normal, 0f),
		labelLarge     = grotesk(vFamily, kBase.labelLarge, FontWeight.Medium, 0f),
		labelMedium    = grotesk(vFamily, kBase.labelMedium, FontWeight.Medium),
		labelSmall     = grotesk(vFamily, kBase.labelSmall, FontWeight.Medium)
	)
}
