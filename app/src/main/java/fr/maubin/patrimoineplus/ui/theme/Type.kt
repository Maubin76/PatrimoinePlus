package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import fr.maubin.patrimoineplus.R

/*
	Construit une graisse de la police variable Hanken Grotesk.
	inWeight : graisse voulue
*/
private fun hanken(inWeight: FontWeight): Font =
	Font(
		resId            = R.font.hanken_grotesk,
		weight           = inWeight,
		variationSettings = FontVariation.Settings(FontVariation.weight(inWeight.weight))
	)

// Grotesque unique de l'application ; ses chiffres sont à chasse fixe.
val HankenGrotesk = FontFamily(
	hanken(FontWeight.Normal),
	hanken(FontWeight.Medium),
	hanken(FontWeight.SemiBold)
)

private val kBase = Typography()										// échelle Material 3 de référence

/*
	Applique la grotesque et une graisse à un style de l'échelle Material.
	inStyle    : style de référence
	inWeight   : graisse à appliquer
	inTracking : interlettrage, en em ; null conserve celui du style
*/
private fun grotesk(
	inStyle    : TextStyle,
	inWeight   : FontWeight,
	inTracking : Float? = null
): TextStyle =
	inStyle.copy(
		fontFamily    = HankenGrotesk,
		fontWeight    = inWeight,
		letterSpacing = inTracking?.em ?: inStyle.letterSpacing
	)

// Échelle typographique : la hiérarchie vient de la taille, les graisses restent sobres.
val BraunTypography = Typography(
	displayLarge   = grotesk(kBase.displayLarge, FontWeight.Medium, -0.03f),
	displayMedium  = grotesk(kBase.displayMedium, FontWeight.Medium, -0.025f),
	displaySmall   = grotesk(kBase.displaySmall, FontWeight.Medium, -0.02f),
	headlineLarge  = grotesk(kBase.headlineLarge, FontWeight.Medium, -0.015f),
	headlineMedium = grotesk(kBase.headlineMedium, FontWeight.Medium, -0.01f),
	headlineSmall  = grotesk(kBase.headlineSmall, FontWeight.Medium),
	titleLarge     = grotesk(kBase.titleLarge, FontWeight.Medium),
	titleMedium    = grotesk(kBase.titleMedium, FontWeight.Medium),
	titleSmall     = grotesk(kBase.titleSmall, FontWeight.Medium),
	bodyLarge      = grotesk(kBase.bodyLarge, FontWeight.Normal, 0f),
	bodyMedium     = grotesk(kBase.bodyMedium, FontWeight.Normal, 0f),
	bodySmall      = grotesk(kBase.bodySmall, FontWeight.Normal),
	labelLarge     = grotesk(kBase.labelLarge, FontWeight.Medium),
	labelMedium    = grotesk(kBase.labelMedium, FontWeight.Medium),
	labelSmall     = grotesk(kBase.labelSmall, FontWeight.Medium)
)
