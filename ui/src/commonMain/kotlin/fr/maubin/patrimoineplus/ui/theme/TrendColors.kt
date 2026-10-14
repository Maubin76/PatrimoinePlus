package fr.maubin.patrimoineplus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val kGainLight = Color(0xFF1B7A3A)										// hausse sur fond clair (contraste ≥ 4,5:1)
private val kLossLight = Color(0xFFC0262D)										// baisse sur fond clair
private val kGainDark  = Color(0xFF6FD39A)										// hausse sur fond sombre
private val kLossDark  = Color(0xFFFF8A84)										// baisse sur fond sombre
private val kRiseLight = Color(0xFF136E86)										// thème Coloré : hausse, bleu-vert assombri (5,5:1)
private val kFallLight = Color(0xFFA65200)										// thème Coloré : baisse, orange assombri (5,2:1)
private val kRiseDark  = Color(0xFF4CC3E0)										// thème Coloré sombre : hausse (8:1)
private val kFallDark  = Color(0xFFFFB066)										// thème Coloré sombre : baisse (9:1)

/*
	Couleur d'une variation selon le thème : Minimal, vert pour une hausse et
	rouge pour une baisse ; Coloré, bleu-vert et orange ; Monochrome (ou
	zéro), couleur non précisée, le texte garde celle du contenu. Le signe
	reste toujours écrit : la couleur n'est jamais la seule indication.
	inCents : variation, en centimes
*/
@Composable
fun trendColor(inCents: Long): Color
{
	val vTheme = LocalColorTheme.current												// thème de couleurs
	if (vTheme == ColorTheme.MONOCHROME || inCents == 0L)
	{
		return Color.Unspecified
	}
	val vDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f				// fond sombre
	return when
	{
		vTheme == ColorTheme.COLORFUL && inCents > 0 -> if (vDark) kRiseDark else kRiseLight
		vTheme == ColorTheme.COLORFUL                -> if (vDark) kFallDark else kFallLight
		inCents > 0                                  -> if (vDark) kGainDark else kGainLight
		else                                         -> if (vDark) kLossDark else kLossLight
	}
}
