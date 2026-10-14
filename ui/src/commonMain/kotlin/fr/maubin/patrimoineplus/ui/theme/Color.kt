package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Palette monochrome : noir sur blanc, gris pour le secondaire, aucun accent.
// Seuls les montants négatifs reçoivent un rouge discret (leur signe reste écrit).

private val kWhite          = Color(0xFFFFFFFF)							// fond clair
private val kGrey50         = Color(0xFFF7F7F7)							// conteneur bas
private val kGrey100        = Color(0xFFF2F2F2)							// conteneur
private val kGrey150        = Color(0xFFEDEDED)							// pastille de sélection
private val kGrey200        = Color(0xFFE6E6E6)							// conteneur le plus haut
private val kRule           = Color(0xFFEBEBEB)							// filets et bordures
private val kOutline        = Color(0xFF9E9E9E)							// contours de champs
private val kGrey600        = Color(0xFF6B6B6B)							// texte secondaire (5,3:1 sur blanc)
private val kInk            = Color(0xFF0A0A0A)							// encre principale
private val kSeriesMid      = Color(0xFF8A8A8A)							// série de données : livrets
private val kSeriesLight    = Color(0xFFC4C4C4)							// série de données : compte-titres
private val kNegative       = Color(0xFFC62828)							// montants négatifs

private val kBlack          = Color(0xFF0B0B0B)							// fond sombre
private val kNight50        = Color(0xFF141414)							// conteneur bas sombre
private val kNight100       = Color(0xFF1A1A1A)							// conteneur sombre (pilule)
private val kNight150       = Color(0xFF333333)							// pastille de sélection sombre
private val kNight200       = Color(0xFF303030)							// conteneur le plus haut sombre
private val kRuleDark       = Color(0xFF262626)							// filets sombres
private val kOutlineDark    = Color(0xFF6E6E6E)							// contours sombres
private val kGrey400        = Color(0xFFA3A3A3)							// texte secondaire sombre
private val kPaper          = Color(0xFFF5F5F5)							// encre claire
private val kSeriesMidDark  = Color(0xFF9E9E9E)							// livrets, mode sombre
private val kSeriesLowDark  = Color(0xFF5C5C5C)							// compte-titres, mode sombre
private val kNegativeDark   = Color(0xFFEF9A9A)							// négatifs, mode sombre

/*
	Palette claire, projetée sur les rôles Material 3. Le noir occupe
	primaryContainer : c'est la couleur du bouton de saisie.
*/
val MonoLightColors: ColorScheme = lightColorScheme(
	primary                 = kInk,
	onPrimary               = kWhite,
	primaryContainer        = kInk,
	onPrimaryContainer      = kWhite,
	secondary               = kSeriesMid,
	onSecondary             = kWhite,
	secondaryContainer      = kGrey150,
	onSecondaryContainer    = kInk,
	tertiary                = kSeriesLight,
	onTertiary              = kInk,
	tertiaryContainer       = kGrey100,
	onTertiaryContainer     = kInk,
	background              = kWhite,
	onBackground            = kInk,
	surface                 = kWhite,
	onSurface               = kInk,
	surfaceVariant          = kGrey100,
	onSurfaceVariant        = kGrey600,
	surfaceTint             = Color.Transparent,
	inverseSurface          = kInk,
	inverseOnSurface        = kWhite,
	inversePrimary          = kWhite,
	outline                 = kOutline,
	outlineVariant          = kRule,
	surfaceBright           = kWhite,
	surfaceDim              = kGrey200,
	surfaceContainerLowest  = kWhite,
	surfaceContainerLow     = kGrey50,
	surfaceContainer        = kGrey100,
	surfaceContainerHigh    = kGrey150,
	surfaceContainerHighest = kGrey200,
	error                   = kNegative,
	onError                 = kWhite,
	errorContainer          = Color(0xFFFDECEC),
	onErrorContainer        = Color(0xFF5F1111)
)

/*
	Palette sombre : blanc sur noir, mêmes rôles que la palette claire.
*/
val MonoDarkColors: ColorScheme = darkColorScheme(
	primary                 = kPaper,
	onPrimary               = kBlack,
	primaryContainer        = kPaper,
	onPrimaryContainer      = kBlack,
	secondary               = kSeriesMidDark,
	onSecondary             = kBlack,
	secondaryContainer      = kNight150,
	onSecondaryContainer    = kPaper,
	tertiary                = kSeriesLowDark,
	onTertiary              = kPaper,
	tertiaryContainer       = kNight100,
	onTertiaryContainer     = kPaper,
	background              = kBlack,
	onBackground            = kPaper,
	surface                 = kBlack,
	onSurface               = kPaper,
	surfaceVariant          = kNight100,
	onSurfaceVariant        = kGrey400,
	surfaceTint             = Color.Transparent,
	inverseSurface          = kPaper,
	inverseOnSurface        = kBlack,
	inversePrimary          = kInk,
	outline                 = kOutlineDark,
	outlineVariant          = kRuleDark,
	surfaceBright           = kNight100,
	surfaceDim              = kBlack,
	surfaceContainerLowest  = kBlack,
	surfaceContainerLow     = kNight50,
	surfaceContainer        = kNight100,
	surfaceContainerHigh    = kNight150,
	surfaceContainerHighest = kNight200,
	error                   = kNegativeDark,
	onError                 = kBlack,
	errorContainer          = Color(0xFF4A1515),
	onErrorContainer        = Color(0xFFFDECEC)
)

// Rampe de répartition des titres, de la plus grosse position à « Autres » ; validée
// (écart ΔE ≥ 15 entre voisins, y compris daltonisme), chaque teinte étant doublée d'une étiquette.
val kAllocationLight = listOf(Color(0xFF0A0A0A), Color(0xFF5C5C5C), Color(0xFF9E9E9E), Color(0xFFCFCFCF))	// mode clair
val kAllocationDark  = listOf(Color(0xFFF5F5F5), Color(0xFFABABAB), Color(0xFF6E6E6E), Color(0xFF3D3D3D))	// mode sombre

// ---------- Thème Coloré ----------
// Palette « Refreshing Summer Fun » sur toute l'interface : fonds teintés de bleu
// ciel (clair) ou de bleu nuit (sombre), encre bleu nuit, accents de la palette.
// Les textes secondaires et négatifs sont assombris (clair) ou éclaircis
// (sombre) pour garder un contraste d'au moins 4,5:1.

private val kSkyPaper       = Color(0xFFF3F9FC)							// fond clair teinté de bleu ciel
private val kSky50          = Color(0xFFEAF4F9)							// conteneur bas
private val kSky100         = Color(0xFFE1EFF6)							// conteneur
private val kSky150         = Color(0xFFD6E9F2)							// pastille de sélection
private val kSky200         = Color(0xFFCBE3EE)							// conteneur le plus haut
private val kSkyRule        = Color(0xFFD6E6EE)							// filets
private val kSkyOutline     = Color(0xFF8AA9B9)							// contours de champs
private val kSkyMuted       = Color(0xFF3F6478)							// texte secondaire (6:1 sur le fond)
private val kNegativeWarm   = Color(0xFFA65200)							// négatifs, orange assombri (5,2:1)

private val kNight          = Color(0xFF01202F)							// fond sombre bleu nuit
private val kNightLow       = Color(0xFF022839)							// conteneur bas sombre
private val kNightMid       = Color(0xFF023047)							// conteneur sombre (bleu nuit de la palette)
private val kNightHigh      = Color(0xFF0A3A54)							// pastille de sélection sombre
private val kNightTop       = Color(0xFF12445F)							// conteneur le plus haut sombre
private val kNightOutline   = Color(0xFF4F7A90)							// contours sombres
private val kNightMuted     = Color(0xFF9FC4D6)							// texte secondaire sombre
private val kIce            = Color(0xFFEAF6FB)							// encre claire
private val kNegativeLight  = Color(0xFFFFB066)							// négatifs, mode sombre

/*
	Palette claire du thème Coloré.
*/
val ColorfulLightColors: ColorScheme = lightColorScheme(
	primary                 = kDeepSpace,
	onPrimary               = kWhite,
	primaryContainer        = kDeepSpace,
	onPrimaryContainer      = kWhite,
	secondary               = kBlueGreen,
	onSecondary             = kWhite,
	secondaryContainer      = kSky150,
	onSecondaryContainer    = kDeepSpace,
	tertiary                = kAmber,
	onTertiary              = kDeepSpace,
	tertiaryContainer       = kSky100,
	onTertiaryContainer     = kDeepSpace,
	background              = kSkyPaper,
	onBackground            = kDeepSpace,
	surface                 = kSkyPaper,
	onSurface               = kDeepSpace,
	surfaceVariant          = kSky100,
	onSurfaceVariant        = kSkyMuted,
	surfaceTint             = Color.Transparent,
	inverseSurface          = kDeepSpace,
	inverseOnSurface        = kWhite,
	inversePrimary          = kSkyBlue,
	outline                 = kSkyOutline,
	outlineVariant          = kSkyRule,
	surfaceBright           = kWhite,
	surfaceDim              = kSky200,
	surfaceContainerLowest  = kWhite,
	surfaceContainerLow     = kSky50,
	surfaceContainer        = kSky100,
	surfaceContainerHigh    = kSky150,
	surfaceContainerHighest = kSky200,
	error                   = kNegativeWarm,
	onError                 = kWhite,
	errorContainer          = Color(0xFFFFEBD6),
	onErrorContainer        = Color(0xFF5A2C00)
)

/*
	Palette sombre du thème Coloré : fond bleu nuit, encre glacée.
*/
val ColorfulDarkColors: ColorScheme = darkColorScheme(
	primary                 = kSkyBlue,
	onPrimary               = kDeepSpace,
	primaryContainer        = kSkyBlue,
	onPrimaryContainer      = kDeepSpace,
	secondary               = kBlueGreen,
	onSecondary             = kIce,
	secondaryContainer      = kNightHigh,
	onSecondaryContainer    = kIce,
	tertiary                = kAmber,
	onTertiary              = kDeepSpace,
	tertiaryContainer       = kNightMid,
	onTertiaryContainer     = kIce,
	background              = kNight,
	onBackground            = kIce,
	surface                 = kNight,
	onSurface               = kIce,
	surfaceVariant          = kNightMid,
	onSurfaceVariant        = kNightMuted,
	surfaceTint             = Color.Transparent,
	inverseSurface          = kIce,
	inverseOnSurface        = kNight,
	inversePrimary          = kDeepSpace,
	outline                 = kNightOutline,
	outlineVariant          = kNightTop,
	surfaceBright           = kNightMid,
	surfaceDim              = kNight,
	surfaceContainerLowest  = kNight,
	surfaceContainerLow     = kNightLow,
	surfaceContainer        = kNightMid,
	surfaceContainerHigh    = kNightHigh,
	surfaceContainerHighest = kNightTop,
	error                   = kNegativeLight,
	onError                 = kNight,
	errorContainer          = Color(0xFF4A2500),
	onErrorContainer        = Color(0xFFFFE3C7)
)
