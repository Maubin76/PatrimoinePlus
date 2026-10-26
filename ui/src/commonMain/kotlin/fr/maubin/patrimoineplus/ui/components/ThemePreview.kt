package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.calc.parseHexColor
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.theme.LocalColorTheme
import fr.maubin.patrimoineplus.ui.theme.LocalPalette
import fr.maubin.patrimoineplus.ui.theme.Palette

/*
	Pastilles des couleurs d'un thème (liste des thèmes, éditeur) : une par
	couleur ; une couleur illisible est un rond vide.
	inColors : couleurs « #RRGGBB »
	inSize   : diamètre d'une pastille
*/
@Composable
fun ThemeSwatches(
	inColors : List<String>,
	inSize   : Dp = 16.dp
)
{
	Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
		inColors.forEach { inText ->
			val vColor = parseHexColor(inText)?.let { Color(it.toInt()) }					// couleur lue ; null si illisible
			Box(
				Modifier
					.size(inSize)
					.clip(CircleShape)
					.background(vColor ?: Color.Transparent)
					.border(1.dp, if (vColor == null) MaterialTheme.colorScheme.outline else Color.Black.copy(alpha = 0.08f), CircleShape)
			)
		}
	}
}

/*
	Aperçu d'un thème personnalisé, aux couleurs de sa palette (comme le
	thème Coloré) : tuile pleine du chiffre mis en avant, variations à la
	hausse et à la baisse, répartition par type de compte.
	inPalette : palette prévisualisée
*/
@Composable
fun ThemePreview(inPalette: Palette)
{
	CompositionLocalProvider(LocalColorTheme provides ColorTheme.COLORFUL, LocalPalette provides inPalette) {
		Column(Modifier.fillMaxWidth().semantics { contentDescription = "Aperçu du thème" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
			Tile(inFilled = true) {
				TileFigure("Patrimoine net") { AmountText(inCents = 6_194_843, inStyle = MaterialTheme.typography.headlineSmall) }
			}
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text("Ce mois-ci ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				AmountText(inCents = 209_338, inShowPlusSign = true, inTrend = true)
				Spacer(Modifier.width(16.dp))
				AmountText(inCents = -45_000, inShowPlusSign = true, inTrend = true)
			}
			ProportionBar(
				inSegments    = listOf(AccountType.REAL_ESTATE to 54L, AccountType.SAVINGS to 23L, AccountType.CHECKING to 12L, AccountType.BROKERAGE to 7L, AccountType.COLLECTIBLE to 4L)
					.map { (inType, inShare) -> BarSegment(inShare, accountAccent(inType)) },
				inDescription = "Répartition d'exemple",
				inModifier    = Modifier.fillMaxWidth().height(12.dp)
			)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
				listOf("Immobilier" to AccountType.REAL_ESTATE, "Épargne" to AccountType.SAVINGS, "Courant" to AccountType.CHECKING, "Bourse" to AccountType.BROKERAGE, "Collection" to AccountType.COLLECTIBLE).forEach { (inLabel, inType) ->
					Row(verticalAlignment = Alignment.CenterVertically) {
						Box(Modifier.size(8.dp).clip(CircleShape).background(accountAccent(inType)))
						Spacer(Modifier.width(4.dp))
						Text(inLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
			}
		}
	}
}
