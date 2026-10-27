package fr.maubin.patrimoineplus.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.theme.LocalColorTheme
import fr.maubin.patrimoineplus.ui.theme.colorfulPalette
import androidx.compose.runtime.staticCompositionLocalOf
import fr.maubin.patrimoineplus.ui.theme.colorfulMain
import fr.maubin.patrimoineplus.ui.theme.LocalPalette

// Couleurs d'accent du thème Coloré, posées sur le contenu (tuiles, pastilles)
// et pas sur les fonds, qui restent ceux des autres thèmes.

private val kLoanLight      = Color(0xFF6B7F8A)									// crédit, ardoise (fond clair)
private val kLoanDark       = Color(0xFF8FA3AE)									// crédit, ardoise (fond sombre)
private val kRiseInk        = Color(0xFF0A6B59)									// icône d'une entrée sur pastille claire (menthe foncée)
private val kFallInk        = Color(0xFF9A2042)									// icône d'une sortie sur pastille claire (corail foncé)

/*
	Vrai si le thème Coloré est actif.
*/
@Composable
fun isColorful(): Boolean = LocalColorTheme.current == ColorTheme.COLORFUL

/*
	Couleur d'un type de compte dans le thème Coloré (tuiles, pastilles,
	répartition par type) : Courant menthe, Épargne ambre, Bourse corail,
	Immobilier indigo, Collection pervenche, Crédit ardoise (ou les rôles du
	thème personnalisé) ; non précisée dans les autres thèmes.
	inType : type de compte
*/
@Composable
fun accountAccent(inType: AccountType): Color
{
	if (!isColorful()) return Color.Unspecified
	val vDark    = MaterialTheme.colorScheme.surface.luminance() < 0.5f				// fond sombre
	val vPalette = colorfulPalette()													// palette en cours
	return when (inType)
	{
		AccountType.CHECKING    -> vPalette.rise
		AccountType.SAVINGS     -> vPalette.invest
		AccountType.BROKERAGE   -> vPalette.fall
		AccountType.REAL_ESTATE -> vPalette.main
		AccountType.COLLECTIBLE -> vPalette.second
		AccountType.LOAN        -> if (vDark) kLoanDark else kLoanLight
	}
}

/*
	Pastille d'icône d'une opération dans le thème Coloré : entrée en
	menthe, sortie en corail (fond teinté, icône foncée lisible) ; null
	dans les autres thèmes ou pour un montant nul.
	inCents : montant signé de l'opération
	Retour  : fond de la pastille et teinte de l'icône
*/
@Composable
fun operationAccent(inCents: Long): Pair<Color, Color>?
{
	if (!isColorful() || inCents == 0L) return null
	val vDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f					// fond sombre
	val vCustom = LocalPalette.current != null											// thème personnalisé
	val vPalette = colorfulPalette()													// palette en cours
	val vTint    = if (inCents > 0) vPalette.rise else vPalette.fall						// teinte du sens
	val vInk     = if (vDark || vCustom) vTint else if (inCents > 0) kRiseInk else kFallInk	// icône : foncée sur pastille claire
	return vTint.copy(alpha = if (vDark) 0.28f else 0.16f) to vInk
}

/*
	Sens d'un montant, pour sa couleur dans le thème Coloré (pages Bilan,
	Budget, Projection, Objectifs…).
*/
enum class FlowAccent
{
	INFLOW,																			// entrées, revenus : menthe
	OUTFLOW,																		// sorties, dépenses : corail
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
		FlowAccent.INFLOW  -> colorfulPalette().rise
		FlowAccent.OUTFLOW -> colorfulPalette().fall
		FlowAccent.INVEST  -> colorfulPalette().invest
		FlowAccent.MAIN    -> colorfulMain()
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
