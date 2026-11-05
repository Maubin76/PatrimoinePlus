package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.maubin.patrimoineplus.ui.components.LocalGlass
import fr.maubin.patrimoineplus.ui.resources.Res
import fr.maubin.patrimoineplus.ui.resources.outfit
import fr.maubin.patrimoineplus.ui.resources.urbanist
import org.jetbrains.compose.resources.Font

// Brume lumineuse, clair : neutres gris-bleu, encre presque noire.
private val kInk          = Color(0xFF14171C)									// encre
private val kInkMuted     = Color(0xFF636B77)									// texte secondaire (5,1:1 sur le verre blanc)
private val kOutline      = Color(0xFFA2ABB6)									// contour marqué
private val kRule         = Color(0xFFE1E6EC)									// filet
private val kLow          = Color(0xFFF4F7FA)									// survol, champ
private val kMid          = Color(0xFFECF0F4)									// conteneur
private val kHigh         = Color(0xFFE3E9EF)									// conteneur marqué
private val kHighest      = Color(0xFFD9E0E7)									// conteneur le plus marqué
private val kBack         = Color(0xFFDCE3EA)									// fond derrière les panneaux

// Brume lumineuse, sombre : ardoise profonde, encre claire.
private val kNightInk     = Color(0xFFEDF0F4)									// encre
private val kNightMuted   = Color(0xFF9AA3AF)									// texte secondaire
private val kNightOutline = Color(0xFF5C6572)									// contour marqué
private val kNightRule    = Color(0xFF29303A)									// filet
private val kNightPanel   = Color(0xFF181D24)									// menus, fenêtres (opaques)
private val kNightLow     = Color(0xFF1E242C)									// survol
private val kNightMid     = Color(0xFF232A33)									// conteneur
private val kNightHigh    = Color(0xFF2A323C)									// conteneur marqué
private val kNightHighest = Color(0xFF333C47)									// conteneur le plus marqué
private val kNightBack    = Color(0xFF0E1217)									// fond

// Jaune citron du seul bouton plein de l'appli (« Nouvelle opération », saisie).
val kLemon      = Color(0xFFF8E24B)												// clair
val kLemonNight = Color(0xFFF2D838)												// sombre
val kOnLemon    = Color(0xFF1A1A10)												// texte sur le jaune (14:1)

/*
	Construit une graisse de la police variable Urbanist (géométrique fine
	des deux applis).
	inWeight : graisse voulue
*/
@Composable
private fun urbanistWeight(inWeight: FontWeight): Font =
	Font(
		resource          = Res.font.urbanist,
		weight            = inWeight,
		style             = FontStyle.Normal,
		variationSettings = FontVariation.Settings(FontVariation.weight(inWeight.weight))
	)

/*
	Famille Urbanist : légère, courante, moyenne, demi-grasse.
*/
@Composable
fun urbanist(): FontFamily =
	FontFamily(
		urbanistWeight(FontWeight.Light),
		urbanistWeight(FontWeight.Normal),
		urbanistWeight(FontWeight.Medium),
		urbanistWeight(FontWeight.SemiBold)
	)

/*
	Construit une graisse de la police variable Outfit (gardée pour le logo
	et les écrans qui la demandent encore).
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
	Applique Urbanist, une graisse, un interlettrage, une taille et des
	chiffres tabulaires (montants alignés) à un style de l'échelle Material.
	inFamily   : Urbanist
	inStyle    : style de référence
	inWeight   : graisse
	inTracking : interlettrage, en em
	inSize     : taille, en sp
	inLine     : interligne, en sp
*/
private fun geometric(
	inFamily   : FontFamily,
	inStyle    : TextStyle,
	inWeight   : FontWeight,
	inTracking : Float,
	inSize     : Float,
	inLine     : Float
): TextStyle =
	inStyle.copy(
		fontFamily          = inFamily,
		fontWeight          = inWeight,
		letterSpacing       = inTracking.em,
		fontSize            = inSize.sp,
		lineHeight          = inLine.sp,
		fontFeatureSettings = "tnum"
	)

/*
	Échelle typographique des deux applis : chiffres et titres de page grands
	et légers, titres de panneau en graisse moyenne, texte courant net.
*/
@Composable
fun deskTypography(): Typography
{
	val vFamily = urbanist()														// Urbanist chargée
	return Typography(
		displayLarge   = geometric(vFamily, kDeskBase.displayLarge, FontWeight.Light, -0.025f, 58f, 64f),
		displayMedium  = geometric(vFamily, kDeskBase.displayMedium, FontWeight.Light, -0.02f, 46f, 52f),
		displaySmall   = geometric(vFamily, kDeskBase.displaySmall, FontWeight.Normal, -0.02f, 38f, 46f),
		headlineLarge  = geometric(vFamily, kDeskBase.headlineLarge, FontWeight.Normal, -0.015f, 32f, 40f),
		headlineMedium = geometric(vFamily, kDeskBase.headlineMedium, FontWeight.Normal, -0.01f, 28f, 34f),
		headlineSmall  = geometric(vFamily, kDeskBase.headlineSmall, FontWeight.Normal, -0.01f, 24f, 30f),
		titleLarge     = geometric(vFamily, kDeskBase.titleLarge, FontWeight.Medium, -0.005f, 20f, 26f),
		titleMedium    = geometric(vFamily, kDeskBase.titleMedium, FontWeight.Medium, 0f, 16f, 22f),
		titleSmall     = geometric(vFamily, kDeskBase.titleSmall, FontWeight.Medium, 0f, 14f, 20f),
		bodyLarge      = geometric(vFamily, kDeskBase.bodyLarge, FontWeight.Normal, 0.005f, 15f, 22f),
		bodyMedium     = geometric(vFamily, kDeskBase.bodyMedium, FontWeight.Normal, 0.008f, 13.5f, 19f),
		bodySmall      = geometric(vFamily, kDeskBase.bodySmall, FontWeight.Normal, 0.01f, 12f, 16f),
		labelLarge     = geometric(vFamily, kDeskBase.labelLarge, FontWeight.Medium, 0.005f, 14f, 20f),
		labelMedium    = geometric(vFamily, kDeskBase.labelMedium, FontWeight.Medium, 0.01f, 12f, 16f),
		labelSmall     = geometric(vFamily, kDeskBase.labelSmall, FontWeight.Medium, 0.015f, 11f, 14f)
	)
}

/*
	Palette des deux applis (brume lumineuse) : neutres gris-bleu, encre
	presque noire (lune en sombre) pour les boutons ordinaires, surfaces
	opaques pour les menus et les fenêtres ; les panneaux de verre et le
	jaune citron sont posés par leurs composants. Les couleurs des données
	viennent de la palette (voir colorfulPalette). Le thème de couleurs et
	l'effet verre d'avant n'ont plus d'effet : un seul style.
	inDark       : mode sombre
	inColorTheme : sans effet (gardé pour les appels existants)
	inGlass      : sans effet (gardé pour les appels existants)
*/
fun deskColors(
	inDark       : Boolean,
	inColorTheme : ColorTheme = ColorTheme.COLORFUL,
	inGlass      : Boolean    = true
): ColorScheme =
	if (inDark)
	{
		darkColorScheme(
			primary                 = kNightInk,
			onPrimary               = kNightBack,
			primaryContainer        = kNightHigh,
			onPrimaryContainer      = kNightInk,
			secondary               = kNightMuted,
			onSecondary             = kNightBack,
			secondaryContainer      = kNightHigh,
			onSecondaryContainer    = kNightInk,
			tertiary                = kLemonNight,
			onTertiary              = kOnLemon,
			background              = kNightBack,
			onBackground            = kNightInk,
			surface                 = kNightPanel,
			onSurface               = kNightInk,
			surfaceVariant          = kNightMid,
			onSurfaceVariant        = kNightMuted,
			surfaceTint             = Color.Transparent,
			inverseSurface          = kNightInk,
			inverseOnSurface        = kNightBack,
			error                   = Color(0xFFFF8478),
			onError                 = kNightBack,
			outline                 = kNightOutline,
			outlineVariant          = kNightRule,
			surfaceBright           = kNightHigh,
			surfaceDim              = kNightBack,
			surfaceContainerLowest  = kNightPanel,
			surfaceContainerLow     = kNightLow,
			surfaceContainer        = kNightMid,
			surfaceContainerHigh    = kNightHigh,
			surfaceContainerHighest = kNightHighest
		)
	}
	else
	{
		lightColorScheme(
			primary                 = kInk,
			onPrimary               = Color.White,
			primaryContainer        = kHigh,
			onPrimaryContainer      = kInk,
			secondary               = kInkMuted,
			onSecondary             = Color.White,
			secondaryContainer      = kHigh,
			onSecondaryContainer    = kInk,
			tertiary                = kLemon,
			onTertiary              = kOnLemon,
			background              = kBack,
			onBackground            = kInk,
			surface                 = Color.White,
			onSurface               = kInk,
			surfaceVariant          = kMid,
			onSurfaceVariant        = kInkMuted,
			surfaceTint             = Color.Transparent,
			inverseSurface          = kInk,
			inverseOnSurface        = Color.White,
			error                   = Color(0xFFC2352D),
			onError                 = Color.White,
			outline                 = kOutline,
			outlineVariant          = kRule,
			surfaceBright           = Color.White,
			surfaceDim              = kBack,
			surfaceContainerLowest  = Color.White,
			surfaceContainerLow     = kLow,
			surfaceContainer        = kMid,
			surfaceContainerHigh    = kHigh,
			surfaceContainerHighest = kHighest
		)
	}

// Formes : champs et menus arrondis, à l'échelle des panneaux de 18 dp.
private val kDeskShapes = Shapes(
	extraSmall = RoundedCornerShape(8.dp),
	small      = RoundedCornerShape(12.dp),
	medium     = RoundedCornerShape(16.dp),
	large      = RoundedCornerShape(18.dp),
	extraLarge = RoundedCornerShape(24.dp)
)

/*
	Thème des deux applis : brume lumineuse (palette gris-bleu, Urbanist,
	panneaux de verre), en clair ou en sombre ; données aux couleurs de la
	palette, ou à celles d'un thème personnalisé.
	inDarkTheme  : mode sombre
	inColorTheme : sans effet (un seul style ; gardé pour les appels existants)
	inGlass      : sans effet (panneaux de verre toujours)
	inPalette    : couleurs des données d'un thème personnalisé ; null : palette de la brume
	inContent    : contenu
*/
@Composable
fun DeskTheme(
	inDarkTheme  : Boolean,
	inColorTheme : ColorTheme = ColorTheme.COLORFUL,
	inGlass      : Boolean    = true,
	inPalette    : Palette?   = null,
	inContent    : @Composable () -> Unit
)
{
	MaterialTheme(colorScheme = deskColors(inDarkTheme), typography = deskTypography(), shapes = kDeskShapes) {
		CompositionLocalProvider(LocalColorTheme provides ColorTheme.COLORFUL, LocalPalette provides inPalette, LocalGlass provides true, LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = inContent)	// encre par défaut : écrans transparents sur le fond
	}
}
