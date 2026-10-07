package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Palette « Calculatrice Braun » : aluminium gris chaud, anthracite, un seul
// jaune réservé à l'action (bouton de saisie) et au résultat (touche « = »).

private val kAluminium        = Color(0xFFEEECE7)						// fond clair
private val kAluminiumLowest  = Color(0xFFF7F6F2)						// conteneur le plus clair
private val kAluminiumLow     = Color(0xFFE8E6E0)						// conteneur bas
private val kAluminiumMid     = Color(0xFFE3E0DA)						// conteneur
private val kAluminiumHigh    = Color(0xFFDCD9D2)						// conteneur haut
private val kAluminiumHighest = Color(0xFFD5D2CA)						// conteneur le plus haut
private val kAnthracite       = Color(0xFF2A2724)						// encre principale
private val kWarmGrey         = Color(0xFF5E5A54)						// texte secondaire (6:1 sur l'aluminium)
private val kRuleLight        = Color(0xFFCFCBC3)						// filets
private val kOutlineLight     = Color(0xFF8C877F)						// contours de champs
private val kEqualsYellow     = Color(0xFFE2A72E)						// touche « = » de l'ET66
private val kOlive            = Color(0xFF5F7F4A)						// série de données : livrets
private val kOchre            = Color(0xFFA9742A)						// série de données : compte-titres

private val kBody             = Color(0xFF1C1A18)						// corps sombre
private val kBodyLowest       = Color(0xFF171513)						// conteneur le plus bas
private val kBodyLow          = Color(0xFF22201E)						// conteneur bas
private val kBodyMid          = Color(0xFF282624)						// conteneur
private val kBodyHigh         = Color(0xFF2E2B29)						// conteneur haut
private val kBodyHighest      = Color(0xFF363330)						// conteneur le plus haut
private val kKeyLight         = Color(0xFFECE8E0)						// encre claire
private val kKeyGrey          = Color(0xFFB5AFA6)						// texte secondaire sombre
private val kRuleDark         = Color(0xFF45413D)						// filets sombres
private val kOutlineDark      = Color(0xFF8A847B)						// contours sombres
private val kOliveLight       = Color(0xFF9DB884)						// livrets, mode sombre
private val kOchreLight       = Color(0xFFD9A85A)						// compte-titres, mode sombre

/*
	Palette claire, projetée sur les rôles Material 3. Le jaune occupe
	primaryContainer : c'est la couleur par défaut du FAB.
*/
val BraunLightColors: ColorScheme = lightColorScheme(
	primary                 = kAnthracite,
	onPrimary               = kAluminiumLowest,
	primaryContainer        = kEqualsYellow,
	onPrimaryContainer      = kAnthracite,
	secondary               = kOlive,
	onSecondary             = kAluminiumLowest,
	secondaryContainer      = kAluminiumHighest,
	onSecondaryContainer    = kAnthracite,
	tertiary                = kOchre,
	onTertiary              = kAluminiumLowest,
	tertiaryContainer       = Color(0xFFF1DDB8),
	onTertiaryContainer     = kAnthracite,
	background              = kAluminium,
	onBackground            = kAnthracite,
	surface                 = kAluminium,
	onSurface               = kAnthracite,
	surfaceVariant          = kAluminiumHigh,
	onSurfaceVariant        = kWarmGrey,
	surfaceTint             = Color.Transparent,
	inverseSurface          = kAnthracite,
	inverseOnSurface        = kAluminium,
	inversePrimary          = kEqualsYellow,
	outline                 = kOutlineLight,
	outlineVariant          = kRuleLight,
	surfaceBright           = kAluminiumLowest,
	surfaceDim              = kAluminiumHighest,
	surfaceContainerLowest  = kAluminiumLowest,
	surfaceContainerLow     = kAluminiumLow,
	surfaceContainer        = kAluminiumMid,
	surfaceContainerHigh    = kAluminiumHigh,
	surfaceContainerHighest = kAluminiumHighest,
	error                   = Color(0xFFB0392B),
	onError                 = kAluminiumLowest,
	errorContainer          = Color(0xFFF5D9D3),
	onErrorContainer        = Color(0xFF5C1A12)
)

/*
	Palette sombre : corps brun-noir de l'ET66, mêmes rôles que la palette claire.
*/
val BraunDarkColors: ColorScheme = darkColorScheme(
	primary                 = kKeyLight,
	onPrimary               = kBody,
	primaryContainer        = kEqualsYellow,
	onPrimaryContainer      = kBody,
	secondary               = kOliveLight,
	onSecondary             = kBody,
	secondaryContainer      = kBodyHighest,
	onSecondaryContainer    = kKeyLight,
	tertiary                = kOchreLight,
	onTertiary              = kBody,
	tertiaryContainer       = Color(0xFF4A3816),
	onTertiaryContainer     = kKeyLight,
	background              = kBody,
	onBackground            = kKeyLight,
	surface                 = kBody,
	onSurface               = kKeyLight,
	surfaceVariant          = kBodyHigh,
	onSurfaceVariant        = kKeyGrey,
	surfaceTint             = Color.Transparent,
	inverseSurface          = kKeyLight,
	inverseOnSurface        = kBody,
	inversePrimary          = kAnthracite,
	outline                 = kOutlineDark,
	outlineVariant          = kRuleDark,
	surfaceBright           = kBodyHighest,
	surfaceDim              = kBodyLowest,
	surfaceContainerLowest  = kBodyLowest,
	surfaceContainerLow     = kBodyLow,
	surfaceContainer        = kBodyMid,
	surfaceContainerHigh    = kBodyHigh,
	surfaceContainerHighest = kBodyHighest,
	error                   = Color(0xFFF0A597),
	onError                 = kBody,
	errorContainer          = Color(0xFF5C1A12),
	onErrorContainer        = Color(0xFFF5D9D3)
)
