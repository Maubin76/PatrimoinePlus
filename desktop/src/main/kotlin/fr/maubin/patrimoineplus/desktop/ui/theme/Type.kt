package fr.maubin.patrimoineplus.desktop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/*
	Construit une graisse de la police variable Hanken Grotesk.
	inWeight : graisse voulue
*/
private fun hanken(inWeight: FontWeight): androidx.compose.ui.text.font.Font =
	Font(
		identity          = "HankenGrotesk-${inWeight.weight}",
		data              = kFontData,
		weight            = inWeight,
		variationSettings = FontVariation.Settings(FontVariation.weight(inWeight.weight))
	)

// Fichier de la police variable, lu une fois dans les ressources de l'appli.
private val kFontData: ByteArray by lazy {
	checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("font/hanken_grotesk.ttf")) { "police absente" }.use { it.readBytes() }
}

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

// Échelle typographique : total et titres en demi-gras serré, texte courant sans interlettrage.
val MonoTypography = Typography(
	displayLarge   = grotesk(kBase.displayLarge, FontWeight.SemiBold, -0.035f),
	displayMedium  = grotesk(kBase.displayMedium, FontWeight.SemiBold, -0.03f),
	displaySmall   = grotesk(kBase.displaySmall, FontWeight.SemiBold, -0.025f),
	headlineLarge  = grotesk(kBase.headlineLarge, FontWeight.SemiBold, -0.02f),
	headlineMedium = grotesk(kBase.headlineMedium, FontWeight.SemiBold, -0.015f),
	headlineSmall  = grotesk(kBase.headlineSmall, FontWeight.SemiBold, -0.01f),
	titleLarge     = grotesk(kBase.titleLarge, FontWeight.SemiBold, -0.005f),
	titleMedium    = grotesk(kBase.titleMedium, FontWeight.Medium, 0f),
	titleSmall     = grotesk(kBase.titleSmall, FontWeight.Medium, 0f),
	bodyLarge      = grotesk(kBase.bodyLarge, FontWeight.Normal, 0f),
	bodyMedium     = grotesk(kBase.bodyMedium, FontWeight.Normal, 0f),
	bodySmall      = grotesk(kBase.bodySmall, FontWeight.Normal, 0f),
	labelLarge     = grotesk(kBase.labelLarge, FontWeight.Medium, 0f),
	labelMedium    = grotesk(kBase.labelMedium, FontWeight.Medium),
	labelSmall     = grotesk(kBase.labelSmall, FontWeight.Medium)
)
