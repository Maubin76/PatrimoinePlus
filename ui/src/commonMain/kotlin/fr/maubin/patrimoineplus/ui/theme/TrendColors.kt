package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val kGainLight = Color(0xFF1B7A3A)										// hausse sur fond clair (contraste ≥ 4,5:1)
private val kLossLight = Color(0xFFC0262D)										// baisse sur fond clair
private val kGainDark  = Color(0xFF6FD39A)										// hausse sur fond sombre
private val kLossDark  = Color(0xFFFF8A84)										// baisse sur fond sombre

// Vrai quand l'utilisateur a choisi de colorer hausses et baisses (réglage, noir et blanc par défaut).
val LocalTrendColors = staticCompositionLocalOf { false }

/*
	Couleur d'une variation : vert pour une hausse, rouge pour une baisse,
	si le réglage est activé ; sinon (ou pour zéro) couleur non précisée, le
	texte garde celle du contenu. Le signe reste toujours écrit : la couleur
	n'est jamais la seule indication.
	inCents : variation, en centimes
*/
@Composable
fun trendColor(inCents: Long): Color
{
	if (!LocalTrendColors.current || inCents == 0L)
	{
		return Color.Unspecified
	}
	val vDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f				// fond sombre
	return when
	{
		inCents > 0 -> if (vDark) kGainDark else kGainLight
		else        -> if (vDark) kLossDark else kLossLight
	}
}
