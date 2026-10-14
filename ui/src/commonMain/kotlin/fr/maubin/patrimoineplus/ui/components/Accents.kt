package fr.maubin.patrimoineplus.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.theme.LocalColorTheme
import fr.maubin.patrimoineplus.ui.theme.kAmber
import fr.maubin.patrimoineplus.ui.theme.kBlueGreen
import fr.maubin.patrimoineplus.ui.theme.kDeepSpace
import fr.maubin.patrimoineplus.ui.theme.kSkyBlue
import fr.maubin.patrimoineplus.ui.theme.kTiger
import androidx.compose.runtime.staticCompositionLocalOf

// Couleurs d'accent du thème Coloré, posées sur le contenu (tuiles, pastilles)
// et pas sur les fonds, qui restent ceux des autres thèmes.

private val kLoanLight      = Color(0xFF6B7F8A)									// crédit, ardoise (fond clair)
private val kLoanDark       = Color(0xFF8FA3AE)									// crédit, ardoise (fond sombre)
private val kIceAccent      = Color(0xFFE8F3F9)									// collection, bleu glacé (fond sombre)
private val kRiseInk        = Color(0xFF136E86)									// icône d'une entrée sur pastille claire (5,5:1)
private val kFallInk        = Color(0xFFA65200)									// icône d'une sortie sur pastille claire (5,2:1)

/*
	Vrai si le thème Coloré est actif.
*/
@Composable
fun isColorful(): Boolean = LocalColorTheme.current == ColorTheme.COLORFUL

/*
	Couleur d'un type de compte dans le thème Coloré (tuiles, pastilles,
	répartition par type) : Courant bleu-vert, Épargne ambre, Bourse orange,
	Immobilier bleu nuit (bleu ciel sur fond sombre), Collection bleu ciel
	(bleu glacé sur fond sombre), Crédit ardoise ; non précisée dans les
	autres thèmes.
	inType : type de compte
*/
@Composable
fun accountAccent(inType: AccountType): Color
{
	if (!isColorful()) return Color.Unspecified
	val vDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f					// fond sombre
	return when (inType)
	{
		AccountType.CHECKING    -> kBlueGreen
		AccountType.SAVINGS     -> kAmber
		AccountType.BROKERAGE   -> kTiger
		AccountType.REAL_ESTATE -> if (vDark) kSkyBlue else kDeepSpace
		AccountType.COLLECTIBLE -> if (vDark) kIceAccent else kSkyBlue
		AccountType.LOAN        -> if (vDark) kLoanDark else kLoanLight
	}
}

/*
	Pastille d'icône d'une opération dans le thème Coloré : entrée en
	bleu-vert, sortie en orange (fond teinté, icône foncée lisible) ; null
	dans les autres thèmes ou pour un montant nul.
	inCents : montant signé de l'opération
	Retour  : fond de la pastille et teinte de l'icône
*/
@Composable
fun operationAccent(inCents: Long): Pair<Color, Color>?
{
	if (!isColorful() || inCents == 0L) return null
	val vDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f					// fond sombre
	val vBase = if (inCents > 0) kBlueGreen else kTiger									// teinte du sens
	return if (vDark) vBase.copy(alpha = 0.28f) to (if (inCents > 0) Color(0xFF4CC3E0) else Color(0xFFFFB066))
		else vBase.copy(alpha = 0.16f) to (if (inCents > 0) kRiseInk else kFallInk)
}

/*
	Sens d'un montant, pour sa couleur dans le thème Coloré (pages Bilan,
	Budget, Projection, Objectifs…).
*/
enum class FlowAccent
{
	INFLOW,																			// entrées, revenus : bleu-vert
	OUTFLOW,																		// sorties, dépenses : orange
	INVEST,																			// épargne, placements : ambre
	MAIN																			// chiffre principal : couleur principale du thème
}

/*
	Couleur d'un sens de montant dans le thème Coloré ; non précisée dans les
	autres thèmes.
	inKind : sens
*/
@Composable
fun flowAccent(inKind: FlowAccent): Color =
	if (!isColorful()) Color.Unspecified
	else when (inKind)
	{
		FlowAccent.INFLOW  -> kBlueGreen
		FlowAccent.OUTFLOW -> kTiger
		FlowAccent.INVEST  -> kAmber
		FlowAccent.MAIN    -> MaterialTheme.colorScheme.primary
	}

/*
	Couleur d'un sens de montant dans le thème Coloré, sinon la couleur donnée.
	inKind     : sens
	inFallback : couleur des thèmes Monochrome et Minimal
*/
@Composable
fun flowColorOr(
	inKind     : FlowAccent,
	inFallback : Color
): Color = flowAccent(inKind).takeIf { it != Color.Unspecified } ?: inFallback

// Couleur d'accent de la page en cours (vue d'un compte : couleur de son type) ;
// reprise par les courbes et la tuile principale. Non précisée : encre du thème.
val LocalAccent = staticCompositionLocalOf { Color.Unspecified }
