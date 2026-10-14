package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.Cell
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.desktop.ui.components.OperationLine
import fr.maubin.patrimoineplus.desktop.ui.components.TileGrid
import fr.maubin.patrimoineplus.desktop.ui.components.TileRow
import fr.maubin.patrimoineplus.domain.calc.CategoryLine
import fr.maubin.patrimoineplus.domain.format.formatPermille
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.BalanceChart
import fr.maubin.patrimoineplus.ui.components.BarSegment
import fr.maubin.patrimoineplus.ui.components.InfoPill
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.ProportionBar
import fr.maubin.patrimoineplus.ui.components.SeriesDot
import fr.maubin.patrimoineplus.ui.components.Sparkline
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.components.TileFigure
import fr.maubin.patrimoineplus.ui.components.typeColor
import fr.maubin.patrimoineplus.ui.dashboard.AccountTile
import fr.maubin.patrimoineplus.ui.dashboard.DashboardState
import fr.maubin.patrimoineplus.ui.dashboard.TilePreview
import fr.maubin.patrimoineplus.ui.dashboard.buildDashboard
import fr.maubin.patrimoineplus.ui.entry.editEntryForm
import fr.maubin.patrimoineplus.ui.home.accountDetail
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.maubin.patrimoineplus.ui.components.monthTitle
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.components.rankColor
import fr.maubin.patrimoineplus.ui.components.ProportionChart
import fr.maubin.patrimoineplus.ui.home.HomeGroup
import fr.maubin.patrimoineplus.ui.components.ProportionToggle

private val kShortDay   = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)		// date courte
private val kAccountMin = 300.dp													// largeur minimale d'une tuile de compte

/*
	Tableau de bord : tout le patrimoine sur un écran. En haut, les
	indicateurs (patrimoine, douze mois, mois en cours, épargne) ; puis
	l'évolution et la répartition ; puis une tuile par compte, qui montre déjà
	l'essentiel et ouvre le compte d'un clic ; enfin les dépenses du mois et
	les dernières opérations.
	inSpace      : données
	inController : contrôleur (navigation, saisies)
*/
@Composable
fun DashboardScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vState   = remember(inSpace.data, inSpace.today) { buildDashboard(inSpace.data, inSpace.today) }	// tableau de bord
	var vReorder by remember { mutableStateOf(false) }								// réorganisation des tuiles
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		if (vState.home.isEmpty)
		{
			EmptyDashboard(inController)
			return@Column
		}
		Figures(vState)
		TileRow(
			Cell(1f) { inMod -> EvolutionTile(vState, inMod) },
			Cell(1f) { inMod -> RepartitionTile(vState, inMod) }
		)
		Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
			Text("Comptes", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
			if (vState.tiles.size > 1)
			{
				TextButton(onClick = { vReorder = !vReorder }) { Text(if (vReorder) "Terminer" else "Réorganiser") }
				Spacer(Modifier.width(8.dp))
			}
			OutlinedButton(onClick = { inController.openPanel(Panel.AccountSheet(null)) }) { Text("Ajouter un compte") }
		}
		TileGrid(vState.tiles, kAccountMin) { inTile, inMod ->
			AccountTileView(
				inTile     = inTile,
				inModifier = inMod,
				inOnOpen   = if (vReorder) null else { { inController.openAccount(inTile.account.id) } },
				inMove     = if (vReorder) { inStep -> move(inController, vState, inTile, inStep) } else null,
				inIndex    = vState.tiles.indexOf(inTile),
				inCount    = vState.tiles.size
			)
		}
		if (vState.archived.isNotEmpty())
		{
			ArchivedAccounts(vState, inController)
		}
		TileRow(
			Cell(1f) { inMod -> MonthExpensesTile(vState.categories, inMod) },
			Cell(1.3f) { inMod -> RecentTile(vState, inSpace, inController, inMod) },
			inMinWidth = 960.dp
		)
	}
}

/*
	Indicateurs du haut : patrimoine (le chiffre qui élève la voix),
	variation sur douze mois, mois en cours des comptes Courant, épargne du mois.
	inState : tableau de bord
*/
@Composable
private fun Figures(inState: DashboardState)
{
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant							// texte secondaire
	TileRow(
		Cell(1.6f) { inMod ->
			Tile(inModifier = inMod) {
				Text(if (inState.home.debtCents != 0L) "Patrimoine net" else "Patrimoine", style = MaterialTheme.typography.titleMedium, color = vMuted)
				Spacer(Modifier.height(4.dp))
				AmountText(inCents = inState.home.totalCents, inStyle = MaterialTheme.typography.displayMedium, inRolling = true)
				Spacer(Modifier.height(8.dp))
				if (inState.home.debtCents != 0L)
				{
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text("Brut ", style = MaterialTheme.typography.bodyLarge, color = vMuted)
						AmountText(inCents = inState.home.grossCents, inStyle = MaterialTheme.typography.bodyLarge)
						Text("  ·  Crédits ", style = MaterialTheme.typography.bodyLarge, color = vMuted)
						AmountText(inCents = inState.home.debtCents, inStyle = MaterialTheme.typography.bodyLarge)
					}
					Spacer(Modifier.height(8.dp))
				}
				inState.home.monthChange?.let { inChange ->
					Row(verticalAlignment = Alignment.CenterVertically) {
						AmountText(inCents = inChange, inStyle = MaterialTheme.typography.titleMedium, inShowPlusSign = true, inTrend = true)
						Text(" ce mois-ci", style = MaterialTheme.typography.titleMedium, color = vMuted)
					}
					Spacer(Modifier.height(12.dp))
				}
				InfoPill("Calculé au ${inState.home.asOf}")
			}
		},
		Cell(1f) { inMod ->
			Tile(inModifier = inMod, inTitle = "Sur douze mois") {
				val vChange = inState.yearChange											// variation sur un an
				if (vChange == null)
				{
					Hint("Il faut un an d’historique.")
				}
				else
				{
					AmountText(inCents = vChange, inStyle = MaterialTheme.typography.headlineMedium, inShowPlusSign = true, inTrend = true)
					val vStart = inState.home.series.let { it[it.size - 13].valueCents }	// patrimoine il y a un an
					if (vStart > 0) Hint("${formatPermille(vChange * 1000 / vStart)} en un an")
				}
			}
		},
		Cell(1f) { inMod ->
			Tile(inModifier = inMod, inTitle = "Ce mois-ci") {
				TileFigure("Entrées") { AmountText(inCents = inState.income, inStyle = MaterialTheme.typography.titleLarge, inShowPlusSign = true) }
				Spacer(Modifier.height(10.dp))
				TileFigure("Dépenses") { AmountText(inCents = -inState.expenses, inStyle = MaterialTheme.typography.titleLarge) }
			}
		},
		Cell(1f) { inMod ->
			Tile(inModifier = inMod, inTitle = "Mis de côté") {
				AmountText(inCents = inState.saved, inStyle = MaterialTheme.typography.headlineMedium, inShowPlusSign = true, inTrend = true)
				Hint("Ce mois-ci, des comptes courants vers l’épargne et la bourse.")
			}
		},
		inMinWidth = 1000.dp
	)
}

/*
	Tuile de l'évolution du patrimoine sur douze mois (moitié de la largeur,
	à côté de la répartition).
	inState    : tableau de bord
	inModifier : place de la tuile
*/
@Composable
private fun EvolutionTile(
	inState    : DashboardState,
	inModifier : Modifier
)
{
	Tile(inModifier = inModifier, inTitle = "Évolution du patrimoine") {
		val vSeries = inState.home.series											// fin de chaque mois
		if (vSeries.size > 1) BalanceChart(vSeries, vSeries.last().month, inHeight = 240.dp)
		else Hint("La courbe apparaîtra après un premier mois d’historique.")
	}
}

/*
	Tuile de la répartition par type de compte, nette des crédits : types
	rangés de la plus grande part à la plus petite (barre de gauche à droite,
	puis légende), en dégradé de gris du plus marqué au plus discret.
	inState    : tableau de bord
	inModifier : place de la tuile
*/
@Composable
private fun RepartitionTile(
	inState    : DashboardState,
	inModifier : Modifier
)
{
	val vGroups = inState.home.assetGroups.sortedByDescending { it.subtotalCents }			// de la plus grande part à la plus petite
	Tile(inModifier = inModifier, inTitle = "Répartition", inAction = { ProportionToggle() }) {
		ProportionChart(
			inSegments    = vGroups.mapIndexed { inRank, inGroup -> BarSegment(inGroup.subtotalCents, rankColor(inRank, vGroups.size)) },
			inDescription = "Répartition par type de compte, nette des crédits, de la plus grande part à la plus petite"
		) {
			Column { RepartitionLegend(vGroups) }
		}
	}
}

/*
	Légende de la répartition : un type par ligne, dans l'ordre du
	diagramme, avec sa teinte, sa part et son montant.
	inGroups : types, de la plus grande part à la plus petite
*/
@Composable
private fun RepartitionLegend(inGroups: List<HomeGroup>)
{
	inGroups.forEachIndexed { inRank, inGroup ->
		Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
			SeriesDot(rankColor(inRank, inGroups.size))
			Spacer(Modifier.width(12.dp))
			Text(inGroup.type.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
			Text("${inGroup.percent} %", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Spacer(Modifier.width(16.dp))
			AmountText(inCents = inGroup.subtotalCents, inStyle = MaterialTheme.typography.bodyLarge)
		}
	}
}

/*
	Tuile d'un compte : type et nom, valeur, variation du mois, petite courbe
	et aperçu propre au type. Un clic ouvre le compte ; en réorganisation, des
	flèches le déplacent dans la grille.
	inTile     : tuile calculée
	inModifier : place de la tuile
	inOnOpen   : ouverture ; null en réorganisation
	inMove     : déplacement d'un rang (−1 / +1) ; null hors réorganisation
	inIndex    : rang du compte
	inCount    : nombre de comptes
*/
@Composable
private fun AccountTileView(
	inTile     : AccountTile,
	inModifier : Modifier,
	inOnOpen   : (() -> Unit)?,
	inMove     : ((Int) -> Unit)?,
	inIndex    : Int,
	inCount    : Int
)
{
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant							// texte secondaire
	Tile(inModifier = inModifier, inOnClick = inOnOpen, inClickLabel = "Ouvrir ${inTile.account.name}") {
		Row(verticalAlignment = Alignment.Top) {
			Column(Modifier.weight(1f)) {
				Text(inTile.account.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text(accountDetail(inTile.account), style = MaterialTheme.typography.bodyMedium, color = vMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			if (inMove != null)
			{
				IconButton(onClick = { inMove(-1) }, enabled = inIndex > 0) { Icon(LineIcons.ChevronLeft, contentDescription = "Avancer ${inTile.account.name}") }
				IconButton(onClick = { inMove(1) }, enabled = inIndex < inCount - 1) { Icon(LineIcons.ChevronRight, contentDescription = "Reculer ${inTile.account.name}") }
			}
			else
			{
				inTile.weight?.let { Text("${it.percent} %", style = MaterialTheme.typography.bodyMedium, color = vMuted) }
			}
		}
		Spacer(Modifier.height(16.dp))
		AmountText(inCents = inTile.valueCents, inStyle = MaterialTheme.typography.headlineMedium)
		inTile.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = vMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
		inTile.monthChange?.let { inChange ->
			Row(verticalAlignment = Alignment.CenterVertically) {
				AmountText(inCents = inChange, inStyle = MaterialTheme.typography.bodyMedium, inShowPlusSign = true, inTrend = inTile.account.type != AccountType.LOAN)	// crédit : une baisse n'est pas une perte
				Text(" ce mois-ci", style = MaterialTheme.typography.bodyMedium, color = vMuted)
			}
		}
		Spacer(Modifier.height(12.dp))
		Sparkline(inTile.series, "Évolution de ${inTile.account.name} sur ${inTile.series.size} mois")
		Spacer(Modifier.height(16.dp))
		HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
		Spacer(Modifier.height(12.dp))
		TilePreviewView(inTile.preview)
	}
}

/*
	Aperçu d'un compte selon son type, sous sa courbe.
	inPreview : aperçu calculé
*/
@Composable
private fun TilePreviewView(inPreview: TilePreview)
{
	when (inPreview)
	{
		is TilePreview.Checking  ->
		{
			PreviewLine("Entrées du mois", inPreview.income)
			PreviewLine("Dépenses du mois", -inPreview.expenses, inSigned = false)
			if (inPreview.categories.isNotEmpty())
			{
				Spacer(Modifier.height(8.dp))
				inPreview.categories.forEach { inLine -> CategoryBar(inLine, inPreview.categories.first().netCents, inCompact = true) }
			}
		}
		is TilePreview.Savings   ->
		{
			PreviewLine("Intérêts et revenus ${inPreview.year}", inPreview.yearIncome)
			val vStatement = inPreview.statement											// dernier relevé
			if (vStatement == null)
			{
				Hint("Aucun relevé de la banque.")
			}
			else
			{
				PreviewLine("Relevé du ${vStatement.statement.date.format(kShortDay)}", vStatement.statement.balanceCents, inSigned = false)
				if (vStatement.gapCents != 0L) PreviewLine("Écart avec le solde calculé", vStatement.gapCents)
				else Hint("Relevé conforme au solde calculé.")
			}
		}
		is TilePreview.Brokerage ->
		{
			Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
				Text("Plus-value latente", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
				inPreview.ratePermille?.takeIf { !LocalAmountsHidden.current }?.let {
					Text(formatPermille(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					Spacer(Modifier.width(10.dp))
				}
				AmountText(inCents = inPreview.unrealizedCents, inStyle = MaterialTheme.typography.bodyMedium, inShowPlusSign = true, inTrend = true)
			}
			PreviewLine("Espèces", inPreview.cashCents, inSigned = false)
			if (inPreview.positions.isNotEmpty())
			{
				Spacer(Modifier.height(8.dp))
				inPreview.positions.forEach { (inName, inPercent) -> ShareLine(inName, inPercent) }
				if (inPreview.others > 0) ShareLine("Autres", inPreview.others)
			}
		}
		is TilePreview.Loan      ->
		{
			val vNext = inPreview.next																		// prochaine mensualité
			if (vNext == null) Hint(if (inPreview.end == null) "Sans tableau d’amortissement." else "Dernière mensualité passée.")
			else PreviewLine("Mensualité du ${vNext.date.format(kShortDay)}", vNext.total, inSigned = false)
			inPreview.paid?.let { inPaid ->
				Row(Modifier.fillMaxWidth().heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
					Text("Remboursé", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
					MiniBar(inPaid / 100f, Modifier.width(72.dp))
					Spacer(Modifier.width(10.dp))
					Text("$inPaid %", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
				}
			}
			inPreview.end?.let { Hint("Fin en ${monthTitle(java.time.YearMonth.from(it)).lowercase()}") }
		}
		is TilePreview.Property  ->
		{
			if (inPreview.count == 0) Hint("Aucun bien détenu.")
			inPreview.items.forEach { (inName, inCost) -> PreviewLine(inName, inCost, inSigned = false) }
			if (inPreview.others > 0) PreviewLine("Autres (${inPreview.count - inPreview.items.size})", inPreview.others, inSigned = false)
			if (inPreview.gains != 0L)
			{
				Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
					Text("Plus-values des ventes", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
					AmountText(inCents = inPreview.gains, inStyle = MaterialTheme.typography.bodyMedium, inShowPlusSign = true, inTrend = true)
				}
			}
		}
	}
}

/*
	Ligne libellé / montant d'un aperçu.
	inLabel  : libellé
	inCents  : montant
	inSigned : montant signé (« + » devant les entrées)
*/
@Composable
private fun PreviewLine(
	inLabel  : String,
	inCents  : Long,
	inSigned : Boolean = true
)
{
	Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
		Text(inLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
		AmountText(inCents = inCents, inStyle = MaterialTheme.typography.bodyMedium, inShowPlusSign = inSigned)
	}
}

/*
	Part d'une position : nom, petite barre, pourcentage.
	inName    : nom
	inPercent : part, en %
*/
@Composable
private fun ShareLine(
	inName    : String,
	inPercent : Int
)
{
	Row(Modifier.fillMaxWidth().heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
		Text(inName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
		Spacer(Modifier.width(12.dp))
		MiniBar(inPercent / 100f, Modifier.width(72.dp))
		Spacer(Modifier.width(10.dp))
		Text("$inPercent %", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
	}
}

/*
	Ligne d'une dépense par catégorie : icône, nom, barre relative à la plus
	grosse, montant dépensé (sans signe : c'est une dépense).
	inLine    : dépense nette de la catégorie
	inMax     : plus grosse dépense (échelle des barres)
	inCompact : ligne resserrée (aperçu d'un compte)
*/
@Composable
fun CategoryBar(
	inLine    : CategoryLine,
	inMax     : Long,
	inCompact : Boolean = false
)
{
	Row(Modifier.fillMaxWidth().heightIn(min = if (inCompact) 28.dp else 44.dp), verticalAlignment = Alignment.CenterVertically) {
		Icon(CategoryIcons.of(inLine.category.icon), contentDescription = null, modifier = Modifier.size(if (inCompact) 16.dp else 20.dp))
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
			Text(inLine.category.name, style = if (inCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
			if (!inCompact) MiniBar(if (inMax > 0) inLine.netCents.coerceAtLeast(0L).toFloat() / inMax else 0f, Modifier.fillMaxWidth())
		}
		Spacer(Modifier.width(16.dp))
		if (!inCompact)
		{
			Text("${inLine.percent} %", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Spacer(Modifier.width(16.dp))
		}
		AmountText(inCents = inLine.netCents, inStyle = if (inCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
	}
}

/*
	Petite barre de proportion (rail et part remplie).
	inFraction : part remplie, de 0 à 1
	inModifier : place de la barre
*/
@Composable
fun MiniBar(
	inFraction : Float,
	inModifier : Modifier
)
{
	ProportionBar(
		inSegments    = listOf(BarSegment((inFraction.coerceIn(0f, 1f) * 1000).toLong(), MaterialTheme.colorScheme.onSurface), BarSegment(((1f - inFraction.coerceIn(0f, 1f)) * 1000).toLong(), MaterialTheme.colorScheme.surfaceContainerHigh)),
		inDescription = "${(inFraction * 100).toInt()} %",
		inModifier    = inModifier
	)
}

/*
	Tuile des dépenses du mois, tous comptes Courant, par catégorie.
	inLines    : dépenses nettes par catégorie
	inModifier : place de la tuile
*/
@Composable
private fun MonthExpensesTile(
	inLines    : List<CategoryLine>,
	inModifier : Modifier
)
{
	Tile(inModifier = inModifier, inTitle = "Dépenses du mois") {
		val vShown = inLines.filter { it.netCents > 0 }								// catégories dépensières
		if (vShown.isEmpty()) Hint("Aucune dépense ce mois-ci sur les comptes courants.")
		vShown.take(8).forEach { inLine -> CategoryBar(inLine, vShown.first().netCents) }
		if (vShown.size > 8) Hint("Et ${vShown.size - 8} autre(s) catégorie(s).")
	}
}

/*
	Tuile des dernières opérations, tous comptes ; un clic ouvre l'opération.
	inState      : tableau de bord
	inSpace      : données (catégories du formulaire)
	inController : contrôleur
	inModifier   : place de la tuile
*/
@Composable
private fun RecentTile(
	inState      : DashboardState,
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inModifier   : Modifier
)
{
	Tile(
		inModifier = inModifier,
		inTitle    = "Dernières opérations",
		inAction   = if (inState.upcoming > 0) { { InfoPill("${inState.upcoming} à venir") } } else null
	) {
		if (inState.recent.isEmpty()) Hint("Aucune opération pour l’instant.")
		inState.recent.forEach { inOp ->
			OperationLine(
				inIcon    = inOp.icon,
				inTitle   = inOp.title,
				inDetail  = "${inOp.transaction.date.format(kShortDay)} · ${inOp.detail}",
				inCents   = inOp.amount,
				inOnClick = { inController.openTransaction(inOp.transaction) }
			)
		}
	}
}

/*
	Comptes archivés : une ligne par compte, cliquable.
	inState      : tableau de bord
	inController : contrôleur
*/
@Composable
private fun ArchivedAccounts(
	inState      : DashboardState,
	inController : DesktopController
)
{
	var vOpen by remember { mutableStateOf(false) }									// liste dépliée
	TextButton(onClick = { vOpen = !vOpen }) { Text(if (vOpen) "Masquer les comptes archivés" else "Comptes archivés (${inState.archived.size})") }
	if (vOpen)
	{
		Tile {
			inState.archived.forEach { inTile ->
				Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
					TextButton(onClick = { inController.openAccount(inTile.account.id) }) { Text(inTile.account.name) }
					Text(accountDetail(inTile.account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
					AmountText(inCents = inTile.valueCents, inStyle = MaterialTheme.typography.bodyLarge)
				}
			}
		}
	}
}

/*
	Tableau de bord sans compte : invitation à créer le premier.
	inController : contrôleur
*/
@Composable
private fun EmptyDashboard(inController: DesktopController)
{
	Tile(inTitle = "Aucun compte") {
		Hint("Le patrimoine se calcule à partir de vos comptes et de leurs opérations. Créez un premier compte, ou attendez le fichier du téléphone.")
		Spacer(Modifier.height(12.dp))
		OutlinedButton(onClick = { inController.openPanel(Panel.AccountSheet(null)) }) { Text("Ajouter un compte") }
	}
}

/*
	Déplace un compte d'un rang dans l'ordre d'affichage.
	inController : contrôleur
	inState      : tableau de bord (ordre actuel)
	inTile       : compte déplacé
	inStep       : −1 pour avancer, +1 pour reculer
*/
private fun move(
	inController : DesktopController,
	inState      : DashboardState,
	inTile       : AccountTile,
	inStep       : Int
)
{
	val vIds   = inState.tiles.map { it.account.id }.toMutableList()				// ordre actuel
	val vIndex = vIds.indexOf(inTile.account.id)									// rang du compte
	if (vIndex + inStep !in vIds.indices) return
	java.util.Collections.swap(vIds, vIndex, vIndex + inStep)
	inController.edit { inController.editor.reorderAccounts(it, vIds) }
}
