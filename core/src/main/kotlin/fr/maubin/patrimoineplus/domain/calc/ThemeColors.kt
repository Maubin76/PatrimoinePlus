package fr.maubin.patrimoineplus.domain.calc

import kotlin.math.pow

// Rôles des cinq couleurs d'un thème personnalisé, dans l'ordre de saisie
// (celui des répartitions) : comme le thème Coloré.
val kThemeRoles = listOf(
	"Principale",																		// chiffre mis en avant, immobilier, courbes
	"Hausses et entrées",																// hausses, revenus, comptes courants
	"Investissements",																	// épargne, placements, livrets
	"Baisses et sorties",																// baisses, dépenses, bourse
	"Secondaire"																		// collection, cinquième part des répartitions
)

const val kThemeColors     = 5															// nombre de couleurs d'un thème
const val kLightBackground = 0xFFFFFFFFL												// fond des panneaux en clair (contraste)
const val kDarkBackground  = 0xFF222439L												// fond des panneaux en sombre (contraste)
const val kMinContrast     = 3.0															// contraste minimal conseillé (éléments graphiques, gros texte)

/*
	Couleur tapée en hexadécimal : « #RRGGBB » ou « RRGGBB » (majuscules ou
	minuscules, espaces de bord ignorés), ou la forme courte « #RGB ».
	inText : texte saisi
	Retour : couleur opaque 0xFFRRGGBB ; null si illisible
*/
fun parseHexColor(inText: String): Long?
{
	val vText = inText.trim().removePrefix("#")											// chiffres
	val vFull = when (vText.length)
	{
		3    -> vText.map { "$it$it" }.joinToString("")
		6    -> vText
		else -> return null
	}																					// six chiffres
	return vFull.toLongOrNull(16)?.let { 0xFF000000L or it }
}

/*
	Écriture normalisée d'une couleur : « #RRGGBB » en majuscules.
	inColor : couleur 0xFFRRGGBB
*/
fun hexOf(inColor: Long): String = "#%06X".format(inColor and 0xFFFFFF)

/*
	Luminance relative d'une couleur (WCAG).
	inColor : couleur 0xFFRRGGBB
*/
private fun luminance(inColor: Long): Double
{
	/*
		Composante linéarisée.
		inShift : décalage de la composante
	*/
	fun channel(inShift: Int): Double
	{
		val vC = ((inColor shr inShift) and 0xFF) / 255.0								// composante, de 0 à 1
		return if (vC <= 0.03928) vC / 12.92 else ((vC + 0.055) / 1.055).pow(2.4)
	}
	return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/*
	Contraste entre deux couleurs (WCAG), de 1 à 21.
	inA : première couleur
	inB : seconde couleur
*/
fun contrastRatio(
	inA : Long,
	inB : Long
): Double
{
	val vA = luminance(inA)																// luminance de la première
	val vB = luminance(inB)																// luminance de la seconde
	return (maxOf(vA, vB) + 0.05) / (minOf(vA, vB) + 0.05)
}

/*
	Alerte de lisibilité d'une couleur de thème : trop peu contrastée sur le
	fond clair ou sur le fond sombre des panneaux (sans l'interdire).
	inColor : couleur
	Retour : alerte ; null si lisible sur les deux fonds
*/
fun contrastWarning(inColor: Long): String?
{
	val vLight = contrastRatio(inColor, kLightBackground) < kMinContrast				// peu lisible en clair
	val vDark  = contrastRatio(inColor, kDarkBackground) < kMinContrast					// peu lisible en sombre
	return when
	{
		vLight && vDark -> "Peu lisible sur les deux fonds."
		vLight          -> "Peu lisible en clair."
		vDark           -> "Peu lisible en sombre."
		else            -> null
	}
}

/*
	Vérifie un thème personnalisé : nom non vide et libre, cinq couleurs
	lisibles en hexadécimal.
	inName   : nom
	inColors : couleurs tapées
	inTaken  : noms des autres thèmes
	Retour : refus ; null si valide
*/
fun themeError(
	inName   : String,
	inColors : List<String>,
	inTaken  : List<String>
): String? =
	when
	{
		inName.isBlank()                                                -> "Donnez un nom au thème."
		inTaken.any { it.equals(inName.trim(), ignoreCase = true) }     -> "Un thème porte déjà ce nom."
		inColors.size != kThemeColors                                   -> "Il faut cinq couleurs."
		inColors.any { parseHexColor(it) == null }                      -> "Couleur illisible : tapez un code comme #219EBC."
		else                                                            -> null
	}
