package fr.maubin.patrimoineplus.desktop.ui.screens

import fr.maubin.patrimoineplus.ui.components.typeColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.BalanceChart
import fr.maubin.patrimoineplus.ui.components.BarSegment
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.components.InfoPill
import fr.maubin.patrimoineplus.ui.components.ProportionBar
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.desktop.ui.components.TwoColumns
import fr.maubin.patrimoineplus.ui.components.SeriesDot
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.home.buildHomeState

/*
	Accueil : à gauche le patrimoine total, sa variation du mois et sa
	répartition par type de compte ; à droite son évolution sur douze mois.
	Mêmes calculs que l'appli Android (buildHomeState).
	inSpace    : données
	inOnTypes  : ouvre la liste des comptes
*/
@Composable
fun HomeScreen(
	inSpace   : WorkspaceState,
	inOnTypes : () -> Unit
)
{
	val vState = remember(inSpace.data, inSpace.today) {
		buildHomeState(inSpace.data.accounts, inSpace.data.transactions, inSpace.data.prices, inSpace.today)
	}																			// état de l'accueil
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp)) {
		Text("Patrimoine", style = MaterialTheme.typography.headlineMedium)
		Spacer(Modifier.height(32.dp))
		if (vState.isEmpty)
		{
			BorderedCard {
				EmptyState(inTitle = "Aucun compte", inBody = "Les comptes saisis sur le téléphone apparaîtront ici dès que le fichier d’échange sera synchronisé.")
			}
			return@Column
		}
		TwoColumns(
			inRatio = 1.2f,
			inLeft  = {
				AmountText(inCents = vState.totalCents, inStyle = MaterialTheme.typography.displayMedium)
				Spacer(Modifier.height(8.dp))
				vState.monthChange?.let { inChange ->
					Row(verticalAlignment = Alignment.CenterVertically) {
						AmountText(inCents = inChange, inStyle = MaterialTheme.typography.titleMedium, inShowPlusSign = true)
						Text(" ce mois-ci", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
				}
				Spacer(Modifier.height(16.dp))
				InfoPill("Calculé au ${vState.asOf}")
				Spacer(Modifier.height(40.dp))
				SectionHeader(inTitle = "Répartition")
				Spacer(Modifier.height(12.dp))
				ProportionBar(
					inSegments    = vState.groups.map { BarSegment(it.subtotalCents, typeColor(it.type)) },
					inDescription = "Répartition par type de compte"
				)
				Spacer(Modifier.height(12.dp))
				vState.groups.forEach { inGroup ->
					val vPercent = inGroup.percent										// part entière
					Row(
						modifier          = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = inOnTypes),
						verticalAlignment = Alignment.CenterVertically
					) {
						SeriesDot(typeColor(inGroup.type))
						Spacer(Modifier.width(12.dp))
						Text(inGroup.type.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
						Text("$vPercent %", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
						Spacer(Modifier.width(24.dp))
						AmountText(inCents = inGroup.subtotalCents, inStyle = MaterialTheme.typography.bodyLarge)
					}
				}
			},
			inRight = {
				SectionHeader(inTitle = "Évolution")
				Spacer(Modifier.height(12.dp))
				if (vState.series.size > 1)
				{
					BalanceChart(vState.series, vState.series.last().month)
				}
				else
				{
					Text("La courbe apparaîtra après un premier mois d’historique.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
		)
	}
}

