package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.maubin.patrimoineplus.ui.resources.Res
import fr.maubin.patrimoineplus.ui.resources.outfit
import org.jetbrains.compose.resources.Font
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Neutres de l'appli PC, légèrement teintés de lavande (fond pastel).
private val kDeskInk          = Color(0xFF16151F)								// encre
private val kDeskInkMuted     = Color(0xFF686479)								// texte secondaire
private val kDeskOutline      = Color(0xFFA39FB5)								// contour marqué
private val kDeskRule         = Color(0xFFECE9F4)								// filet
private val kDeskLow          = Color(0xFFF6F4FC)								// survol, champ
private val kDeskMid          = Color(0xFFEFECF8)								// conteneur
private val kDeskHigh         = Color(0xFFE7E3F4)								// conteneur marqué
private val kDeskHighest      = Color(0xFFDFDAF0)								// conteneur le plus marqué
private val kDeskBack         = Color(0xFFEEEAF9)								// fond derrière les panneaux
private val kDeskNight        = Color(0xFF222439)								// panneau, sombre
private val kDeskNightBack    = Color(0xFF12131F)								// fond, sombre
private val kDeskMoon         = Color(0xFFF1EFF8)								// encre, sombre
private val kDeskMoonMuted    = Color(0xFFA9A5BF)								// texte secondaire, sombre
private val kDeskNightOutline = Color(0xFF6F6B86)								// contour marqué, sombre
private val kDeskNightRule    = Color(0xFF33354D)								// filet, sombre
private val kDeskNightLow     = Color(0xFF292B42)								// survol, sombre
private val kDeskNightMid     = Color(0xFF2F3149)								// conteneur, sombre
private val kDeskNightHigh    = Color(0xFF373952)								// conteneur marqué, sombre
private val kDeskNightHighest = Color(0xFF41435E)								// conteneur le plus marqué, sombre

/*
	Construit une graisse de la police variable Outfit (géométrique ronde de
	l'appli PC).
	inWeight : graisse voulue
*/
@Composable
private fun outfitWeight(inWeight: FontWeight): Font =
	Font(
		resource          = Res.font.outfit,
		weight            = inWeight,
		style             = FontStyle.Normal,
		variationSettings = FontVariation.Settings(FontVariation.weight(inWeight.weight))
	)

/*
	Famille Outfit : courant, moyen, demi-gras.
*/
@Composable
fun outfit(): FontFamily =
	FontFamily(
		outfitWeight(FontWeight.Normal),
		outfitWeight(FontWeight.Medium),
		outfitWeight(FontWeight.SemiBold)
	)

private val kDeskBase = Typography()										// échelle Material 3 de référence

/*
	Applique Outfit, une graisse, un interlettrage et des chiffres
	tabulaires (montants alignés) à un style de l'échelle Material.
	inFamily   : Outfit
	inStyle    : style de référence
	inWeight   : graisse
	inTracking : interlettrage, en em
	inSize     : taille, en sp ; null conserve celle du style
	inLine     : interligne, en sp ; null conserve celui du style
*/
private fun geometric(
	inFamily   : FontFamily,
	inStyle    : TextStyle,
	inWeight   : FontWeight,
	inTracking : Float,
	inSize     : Float? = null,
	inLine     : Float? = null
): TextStyle =
	inStyle.copy(
		fontFamily          = inFamily,
		fontWeight          = inWeight,
		letterSpacing       = inTracking.em,
		fontSize            = inSize?.sp ?: inStyle.fontSize,
		lineHeight          = inLine?.sp ?: inStyle.lineHeight,
		fontFeatureSettings = "tnum"
	)

/*
	Échelle typographique de l'appli PC : titres de page en grand et en
	graisse moyenne, serrés ; texte courant légèrement ouvert.
*/
@Composable
fun deskTypography(): Typography
{
	val vFamily = outfit()														// Outfit chargée
	return Typography(
		displayLarge   = geometric(vFamily, kDeskBase.displayLarge, FontWeight.Medium, -0.035f),
		displayMedium  = geometric(vFamily, kDeskBase.displayMedium, FontWeight.Medium, -0.03f),
		displaySmall   = geometric(vFamily, kDeskBase.displaySmall, FontWeight.Medium, -0.03f, 42f, 50f),
		headlineLarge  = geometric(vFamily, kDeskBase.headlineLarge, FontWeight.Medium, -0.02f),
		headlineMedium = geometric(vFamily, kDeskBase.headlineMedium, FontWeight.Medium, -0.02f),
		headlineSmall  = geometric(vFamily, kDeskBase.headlineSmall, FontWeight.Medium, -0.015f),
		titleLarge     = geometric(vFamily, kDeskBase.titleLarge, FontWeight.Medium, -0.01f),
		titleMedium    = geometric(vFamily, kDeskBase.titleMedium, FontWeight.Medium, -0.005f, 17f, 24f),
		titleSmall     = geometric(vFamily, kDeskBase.titleSmall, FontWeight.Medium, 0f),
		bodyLarge      = geometric(vFamily, kDeskBase.bodyLarge, FontWeight.Normal, 0.005f),
		bodyMedium     = geometric(vFamily, kDeskBase.bodyMedium, FontWeight.Normal, 0.01f),
		bodySmall      = geometric(vFamily, kDeskBase.bodySmall, FontWeight.Normal, 0.015f),
		labelLarge     = geometric(vFamily, kDeskBase.labelLarge, FontWeight.Medium, 0.01f),
		labelMedium    = geometric(vFamily, kDeskBase.labelMedium, FontWeight.Medium, 0.015f),
		labelSmall     = geometric(vFamily, kDeskBase.labelSmall, FontWeight.Medium, 0.02f)
	)
}

/*
	Palette de l'appli PC : celle du thème de couleurs pour les données
	(hausses et baisses, accents), avec des neutres teintés de lavande ;
	encre de la coque (pilules, disque du rail, boutons) presque noire, ou
	lune en sombre, quel que soit le thème ; panneaux blancs (nuit indigo en
	sombre) posés sur le fond pastel.
	inDark       : mode sombre
	inColorTheme : thème de couleurs
*/
fun deskColors(
	inDark       : Boolean,
	inColorTheme : ColorTheme
): ColorScheme
{
	val vColorful = inColorTheme == ColorTheme.COLORFUL								// thème Coloré
	return if (inDark)
	{
		val vBase = if (vColorful) ColorfulDarkColors else MonoDarkColors			// palette du thème
		vBase.copy(
			primary                 = kDeskMoon,
			onPrimary               = kDeskInk,
			inverseSurface          = kDeskMoon,
			inverseOnSurface        = kDeskInk,
			background              = kDeskNightBack,
			surface                 = kDeskNight,
			onSurface               = kDeskMoon,
			onBackground            = kDeskMoon,
			onSurfaceVariant        = kDeskMoonMuted,
			outline                 = kDeskNightOutline,
			outlineVariant          = kDeskNightRule,
			surfaceContainerLowest  = kDeskNight,
			surfaceContainerLow     = kDeskNightLow,
			surfaceContainer        = kDeskNightMid,
			surfaceContainerHigh    = kDeskNightHigh,
			surfaceContainerHighest = kDeskNightHighest
		)
	}
	else
	{
		val vBase = if (vColorful) ColorfulLightColors else MonoLightColors		// palette du thème
		vBase.copy(
			primary                 = kDeskInk,
			onPrimary               = Color.White,
			inverseSurface          = kDeskInk,
			inverseOnSurface        = Color.White,
			background              = kDeskBack,
			surface                 = Color.White,
			onSurface               = kDeskInk,
			onBackground            = kDeskInk,
			onSurfaceVariant        = kDeskInkMuted,
			outline                 = kDeskOutline,
			outlineVariant          = kDeskRule,
			surfaceContainerLowest  = Color.White,
			surfaceContainerLow     = kDeskLow,
			surfaceContainer        = kDeskMid,
			surfaceContainerHigh    = kDeskHigh,
			surfaceContainerHighest = kDeskHighest
		)
	}
}

// Formes de l'appli PC : champs et menus arrondis, à l'échelle des panneaux de 28 dp.
private val kDeskShapes = Shapes(
	extraSmall = RoundedCornerShape(14.dp),
	small      = RoundedCornerShape(16.dp),
	medium     = RoundedCornerShape(20.dp),
	large      = RoundedCornerShape(28.dp),
	extraLarge = RoundedCornerShape(32.dp)
)

/*
	Thème de l'appli PC : palette à neutres lavande et Outfit, même thème de
	couleurs que l'appli Android pour les données.
	inDarkTheme  : mode sombre
	inColorTheme : thème de couleurs
	inContent    : contenu
*/
@Composable
fun DeskTheme(
	inDarkTheme  : Boolean,
	inColorTheme : ColorTheme,
	inContent    : @Composable () -> Unit
)
{
	MaterialTheme(colorScheme = deskColors(inDarkTheme, inColorTheme), typography = deskTypography(), shapes = kDeskShapes) {
		CompositionLocalProvider(LocalColorTheme provides inColorTheme, content = inContent)
	}
}
