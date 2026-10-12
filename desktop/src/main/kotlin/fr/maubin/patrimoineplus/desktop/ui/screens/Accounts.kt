package fr.maubin.patrimoineplus.desktop.ui.screens

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.icons.LineIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.size
import fr.maubin.patrimoineplus.desktop.ui.components.AmountText
import fr.maubin.patrimoineplus.desktop.ui.components.BarSegment
import fr.maubin.patrimoineplus.desktop.ui.components.BorderedCard
import fr.maubin.patrimoineplus.desktop.ui.components.EmptyState
import fr.maubin.patrimoineplus.desktop.ui.components.ProportionBar
import fr.maubin.patrimoineplus.desktop.ui.components.SectionHeader
import fr.maubin.patrimoineplus.desktop.ui.components.SeriesDot
import fr.maubin.patrimoineplus.desktop.ui.components.allocationColor
import fr.maubin.patrimoineplus.desktop.ui.components.kAllocationSlots
import fr.maubin.patrimoineplus.ui.accounts.AccountItem
import fr.maubin.patrimoineplus.ui.accounts.buildAccountsState
import fr.maubin.patrimoineplus.ui.home.accountDetail

/*
	Liste des comptes : répartition du patrimoine par compte, puis comptes
	actifs et archivés ; un clic ouvre la vue du compte. Mêmes calculs que
	l'appli Android (buildAccountsState).
	inSpace      : données
	inController : contrôleur (ouverture, ajout, ordre)
*/
@Composable
fun AccountsScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vState = remember(inSpace.data, inSpace.today) {
		buildAccountsState(inSpace.data.accounts, inSpace.data.transactions, inSpace.data.prices, inSpace.today)
	}																			// état de la liste
	Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text("Comptes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
			OutlinedButton(onClick = { inController.openPanel(Panel.AccountSheet(null)) }) { Text("Ajouter un compte") }
		}
		Spacer(Modifier.height(32.dp))
		if (vState.active.isEmpty() && vState.archived.isEmpty())
		{
			BorderedCard { EmptyState(inTitle = "Aucun compte", inBody = "Ajoutez vos comptes courants, d’épargne et de bourse, avec leur solde de départ.") }
			return@Column
		}
		val vWeighted = (vState.active + vState.archived).filter { it.weight != null }	// comptes qui comptent
		if (vWeighted.size > 1)
		{
			SectionHeader(inTitle = "Répartition")
			Spacer(Modifier.height(12.dp))
			ProportionBar(
				inSegments    = vWeighted.groupBy { it.weight!!.rank.coerceAtMost(kAllocationSlots - 1) }.toSortedMap()
					.map { (inRank, inGroup) -> BarSegment(inGroup.sumOf { it.weight!!.counted }, allocationColor(inRank)) },
				inDescription = "Part de chaque compte dans le patrimoine"
			)
			Spacer(Modifier.height(28.dp))
		}
		AccountList(vState.active, inController, inReorder = true)
		if (vState.archived.isNotEmpty())
		{
			Spacer(Modifier.height(36.dp))
			SectionHeader(inTitle = "Archivés")
			Spacer(Modifier.height(12.dp))
			AccountList(vState.archived, inController, inReorder = false)
		}
	}
}

/*
	Carte d'une liste de comptes.
	inItems      : comptes
	inController : contrôleur
	inReorder    : flèches de classement (comptes actifs)
*/
@Composable
private fun AccountList(
	inItems      : List<AccountItem>,
	inController : DesktopController,
	inReorder    : Boolean
)
{
	BorderedCard {
		inItems.forEachIndexed { inIndex, inItem ->
			if (inIndex > 0)
			{
				HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
			}
			Row(
				modifier          = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { inController.openAccount(inItem.account.id) }.padding(start = 24.dp, end = if (inReorder) 8.dp else 24.dp, top = 12.dp, bottom = 12.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				inItem.weight?.let { SeriesDot(allocationColor(it.rank)); Spacer(Modifier.width(14.dp)) }
				Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
					Text(inItem.account.name, style = MaterialTheme.typography.titleMedium)
					Text(accountDetail(inItem.account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				Text("${inItem.weight?.percent?.let { "$it %" } ?: ""}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
				Spacer(Modifier.width(32.dp))
				AmountText(
					inCents = inItem.valueCents,
					inStyle = MaterialTheme.typography.titleMedium,
					inColor = if (inItem.valueCents < 0) MaterialTheme.colorScheme.error else Color.Unspecified
				)
				if (inReorder)
				{
					Spacer(Modifier.width(8.dp))
					val vIds = inItems.map { it.account.id }								// ordre actuel
					IconButton(onClick = { reorder(inController, vIds, inIndex, -1) }, enabled = inIndex > 0) {
						Icon(LineIcons.ChevronUp, contentDescription = "Monter ${inItem.account.name}", modifier = Modifier.size(18.dp))
					}
					IconButton(onClick = { reorder(inController, vIds, inIndex, 1) }, enabled = inIndex < inItems.lastIndex) {
						Icon(LineIcons.ChevronDown, contentDescription = "Descendre ${inItem.account.name}", modifier = Modifier.size(18.dp))
					}
				}
			}
		}
	}
}

/*
	Déplace un compte d'un rang dans l'ordre d'affichage.
	inController : contrôleur
	inIds        : ordre actuel des comptes actifs
	inIndex      : rang du compte
	inStep       : -1 pour monter, +1 pour descendre
*/
private fun reorder(
	inController : DesktopController,
	inIds        : List<String>,
	inIndex      : Int,
	inStep       : Int
)
{
	val vIds = inIds.toMutableList()											// nouvel ordre
	java.util.Collections.swap(vIds, inIndex, inIndex + inStep)
	inController.edit { inController.editor.reorderAccounts(it, vIds) }
}
