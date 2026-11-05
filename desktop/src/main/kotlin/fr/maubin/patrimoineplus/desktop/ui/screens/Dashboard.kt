package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.AssistantHint
import fr.maubin.patrimoineplus.desktop.ui.LocalAssistantRoom
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.desktop.ui.components.PageHeader
import fr.maubin.patrimoineplus.desktop.ui.tour.tourAnchor
import fr.maubin.patrimoineplus.domain.calc.CategoryLine
import fr.maubin.patrimoineplus.domain.format.formatPermille
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.analysis.ExpenseAnalysis
import fr.maubin.patrimoineplus.ui.analysis.buildExpenseAnalysis
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.ArcGauge
import fr.maubin.patrimoineplus.ui.components.BarSegment
import fr.maubin.patrimoineplus.ui.components.DeltaPill
import fr.maubin.patrimoineplus.ui.components.GhostPill
import fr.maubin.patrimoineplus.ui.components.LemonButton
import fr.maubin.patrimoineplus.ui.components.MicroBars
import fr.maubin.patrimoineplus.ui.components.ProportionBar
import fr.maubin.patrimoineplus.ui.components.SeriesDot
import fr.maubin.patrimoineplus.ui.components.StatusChip
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.components.Tone
import fr.maubin.patrimoineplus.ui.components.glassWell
import fr.maubin.patrimoineplus.ui.components.isColorful
import fr.maubin.patrimoineplus.ui.components.isNight
import fr.maubin.patrimoineplus.ui.components.typeColor
import fr.maubin.patrimoineplus.ui.dashboard.AccountTile
import fr.maubin.patrimoineplus.ui.dashboard.DashboardState
import fr.maubin.patrimoineplus.ui.dashboard.buildDashboard
import fr.maubin.patrimoineplus.ui.goals.MonthGoalStatus
import fr.maubin.patrimoineplus.ui.goals.evaluateMonth
import fr.maubin.patrimoineplus.ui.home.accountDetail
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import fr.maubin.patrimoineplus.ui.theme.colorfulMain
import fr.maubin.patrimoineplus.ui.theme.colorfulPalette
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.maubin.patrimoineplus.ui.goals.GoalStatus
import fr.maubin.patrimoineplus.ui.goals.evaluateGoals
import fr.maubin.patrimoineplus.ui.components.MoreMenu
import fr.maubin.patrimoineplus.ui.components.ProgressTrack
import fr.maubin.patrimoineplus.ui.analysis.MonthDays
import fr.maubin.patrimoineplus.ui.analysis.buildMonthDays
import fr.maubin.patrimoineplus.ui.theme.kLemon

private val kShortDay  = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)		// date courte
private val kRowGap    = 18.dp															// écart entre les panneaux
private val kWellShape = RoundedCornerShape(12.dp)										// cadre intérieur d'un panneau (liste, tableau)

/*
	Tableau de bord : tout le patrimoine sur un écran, en panneaux de tailles
	différentes selon leur rôle. Ligne de titre et actions ; puis les
	objectifs du mois, le patrimoine net posé à même le fond et la
	répartition en jauge ; quatre indicateurs à mini-barres sur douze mois ;
	les dépenses du mois par catégorie et les dernières opérations ; enfin
	les comptes en tableau léger (un clic ouvre le compte).
	inSpace      : données
	inController : contrôleur (navigation, saisies)
*/
@Composable
fun DashboardScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vState    = remember(inSpace.data, inSpace.today) { buildDashboard(inSpace.data, inSpace.today) }	// tableau de bord
	val vAnalysis = remember(inSpace.data, inSpace.today) { buildExpenseAnalysis(inSpace.data, inSpace.today) }	// douze mois de revenus et de dépenses
	val vDays     = remember(inSpace.data, inSpace.today) { buildMonthDays(inSpace.data, inSpace.today) }		// mois en cours, jour par jour
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(top = 6.dp, bottom = 28.dp).padding(bottom = LocalAssistantRoom.current),
		verticalArrangement = Arrangement.spacedBy(kRowGap)
	) {
		PageHeader(
			inTitle   = "Tableau de bord",
			inActions = {
				GhostPill("Saisie rapide", { inController.show(Screen.OPERATIONS) }, inIcon = LineIcons.Edit)
				GhostPill("Budget", { inController.show(Screen.BUDGET) }, inIcon = LineIcons.Wallet)
				GhostPill("Bilan", { inController.show(Screen.REPORT) }, inIcon = LineIcons.Document)
			},
			inPrimary = { LemonButton("Nouvelle opération", inController::newOperation, Modifier.tourAnchor("shell.newop"), LineIcons.Plus) },
			inModifier = Modifier.padding(bottom = 6.dp)
		)
		if (vState.home.isEmpty)
		{
			EmptyDashboard(inController)
			return@Column
		}
		Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).tourAnchor("dashboard.hero"), horizontalArrangement = Arrangement.spacedBy(kRowGap)) {
			MonthGoalsPanel(inSpace, inController, Modifier.weight(0.95f).fillMaxHeight())
			NetWorth(vState, Modifier.weight(1.1f).fillMaxHeight())
			RepartitionPanel(vState, inController, Modifier.weight(0.95f).fillMaxHeight())
		}
		Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).tourAnchor("dashboard.charts"), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
			Indicators(vState, vAnalysis, vDays, inController)
		}
		Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(kRowGap)) {
			ExpensesPanel(vState.categories, vAnalysis, inController, Modifier.weight(1f).fillMaxHeight())
			RecentPanel(vState, inSpace.today, inController, Modifier.weight(1.15f).fillMaxHeight())
		}
		AccountsPanel(vState, inSpace, inController, Modifier.tourAnchor("dashboard.accounts"))
		if (vState.archived.isNotEmpty())
		{
			ArchivedAccounts(vState, inController)
		}
	}
}

/*
	Cadre intérieur d'un panneau (liste, tableau) : verre plus dense, liseré
	blanc, coins de 12 dp.
	inModifier : modificateur appliqué
	inContent  : contenu
*/
@Composable
private fun Well(
	inModifier : Modifier = Modifier,
	inContent  : @Composable ColumnScope.() -> Unit
)
{
	val vNight = isNight()																// fond sombre
	Column(
		modifier = inModifier
			.fillMaxWidth()
			.background(Color.White.copy(alpha = if (vNight) 0.04f else 0.6f), kWellShape)
			.border(1.dp, Color.White.copy(alpha = if (vNight) 0.06f else 0.95f), kWellShape)
			.padding(horizontal = 14.dp, vertical = 6.dp),
		content  = inContent
	)
}

/*
	Patrimoine net posé à même le fond lumineux, sans panneau : libellé,
	chiffre en très grand et léger (il roule quand la valeur change), variation
	du mois en pastille, brut et crédits, date du calcul.
	inState    : tableau de bord
	inModifier : place
*/
@Composable
private fun NetWorth(
	inState    : DashboardState,
	inModifier : Modifier
)
{
	val vHome  = inState.home																// patrimoine
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	Column(inModifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
		Text(if (vHome.debtCents != 0L) "Patrimoine net" else "Patrimoine", style = MaterialTheme.typography.bodyLarge, color = vMuted)
		Spacer(Modifier.height(2.dp))
		AmountText(inCents = vHome.totalCents, inStyle = MaterialTheme.typography.displayLarge, inRolling = true)
		vHome.monthChange?.let { inChange ->
			Spacer(Modifier.height(10.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				val vBefore = vHome.totalCents - inChange											// patrimoine à la fin du mois dernier
				if (vBefore > 0) DeltaPill(formatPermille(inChange * 1000 / vBefore), if (inChange == 0L) null else inChange > 0)
				Spacer(Modifier.width(8.dp))
				AmountText(inCents = inChange, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = true, inTrend = true)
				Text(" ce mois-ci", style = MaterialTheme.typography.bodyLarge, color = vMuted)
			}
		}
		if (vHome.debtCents != 0L)
		{
			Spacer(Modifier.height(10.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text("Brut ", style = MaterialTheme.typography.bodyMedium, color = vMuted)
				AmountText(inCents = vHome.grossCents, inStyle = MaterialTheme.typography.bodyMedium)
				Text("  ·  Crédits ", style = MaterialTheme.typography.bodyMedium, color = vMuted)
				AmountText(inCents = vHome.debtCents, inStyle = MaterialTheme.typography.bodyMedium)
			}
		}
		Spacer(Modifier.height(6.dp))
		Text("Calculé au ${vHome.asOf}", style = MaterialTheme.typography.bodySmall, color = vMuted)
	}
}

/*
	Ligne de la liste des objectifs.
	label   : intitulé
	success : réussi (coché, barré)
	failed  : dépassé, manqué ou en retard sur l'échéance (rond rouge)
	percent : avancement, en % ; null sans avancement chiffré
*/
private data class GoalLine(
	val label   : String,
	val success : Boolean,
	val failed  : Boolean,
	val percent : Int?
)

/*
	Objectifs : ceux du mois en cours s'ils sont fixés, sinon les objectifs
	globaux (patrimoine, épargne, à cocher). Part des objectifs réussis en
	barre, puis la liste cochée (réussi : coché et barré ; raté : rond rouge)
	avec l'avancement de chacun ; un clic ouvre la page Objectifs. Sans aucun
	objectif : invitation à les tirer du budget.
	inSpace      : données
	inController : contrôleur (objectifs, budget, navigation)
	inModifier   : place du panneau
*/
@Composable
private fun MonthGoalsPanel(
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inModifier   : Modifier
)
{
	val vGoals  by inController.goals.collectAsState()									// objectifs de la vue
	val vBudget by inController.budget.collectAsState()									// budget (comptes suivis)
	val vPlan   = vGoals.plan(YearMonth.from(inSpace.today))							// objectifs du mois en cours
	val vLines  = remember(vGoals, vPlan, inSpace.data, vBudget, inSpace.today) {
		val vMonth = vPlan?.let { evaluateMonth(it, inSpace.data, vBudget, inSpace.today) }.orEmpty()	// objectifs du mois évalués
		if (vMonth.isNotEmpty()) vMonth.map { GoalLine(it.goal.label, it.success, it.status == MonthGoalStatus.EXCEEDED || it.status == MonthGoalStatus.MISSED, it.percent) }
		else evaluateGoals(vGoals, inSpace.data, inSpace.today).map { inView ->
			GoalLine(inView.goal.title, inView.status == GoalStatus.REACHED || inView.status == GoalStatus.DONE, inView.status == GoalStatus.OVERDUE, if (inView.goal.kind.numeric) inView.progress else null)
		}
	}																					// lignes affichées
	val vMuted  = MaterialTheme.colorScheme.onSurfaceVariant							// texte secondaire
	val vGreen  = colorfulPalette().rise												// réussite
	Tile(
		inModifier = inModifier,
		inTitle    = if (vPlan != null) "Objectifs du mois" else "Objectifs",
		inAction   = { MoreMenu(listOf("Ouvrir les objectifs" to { inController.show(Screen.GOALS) }, "Aller au budget" to { inController.show(Screen.BUDGET) }), "Actions des objectifs") }
	) {
		if (vLines.isEmpty())
		{
			Text("Pas encore d’objectifs : fixez ceux du mois depuis votre budget, ou un objectif de patrimoine ; ils se suivent ici tout seuls.", style = MaterialTheme.typography.bodyMedium, color = vMuted)
			Spacer(Modifier.height(14.dp))
			GhostPill("Aller au budget", { inController.show(Screen.BUDGET) }, inIcon = LineIcons.Wallet)
			return@Tile
		}
		val vDone    = vLines.count { it.success }											// objectifs réussis
		val vPercent = vDone * 100 / vLines.size										// part réussie
		Text("$vDone objectif(s) réussi(s) sur ${vLines.size}" + if (vPlan != null) " pour l’instant." else ".", style = MaterialTheme.typography.bodySmall, color = vMuted)
		Spacer(Modifier.height(12.dp))
		Row(verticalAlignment = Alignment.CenterVertically) {
			ProgressTrack(vPercent, vGreen, Modifier.weight(1f).semantics { contentDescription = "$vPercent % des objectifs réussis" })
			Spacer(Modifier.width(12.dp))
			Text("$vPercent %", style = MaterialTheme.typography.labelLarge, color = vGreen)
		}
		Spacer(Modifier.height(14.dp))
		Well {
			vLines.take(6).forEach { inLine ->
				Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
					GoalMark(inLine.success, inLine.failed)
					Spacer(Modifier.width(12.dp))
					Text(
						text           = inLine.label,
						style          = MaterialTheme.typography.bodyMedium,
						color          = if (inLine.success) vMuted else MaterialTheme.colorScheme.onSurface,
						textDecoration = if (inLine.success) TextDecoration.LineThrough else null,
						maxLines       = 1,
						overflow       = TextOverflow.Ellipsis,
						modifier       = Modifier.weight(1f)
					)
					inLine.percent?.let { Text("$it %", style = MaterialTheme.typography.labelSmall, color = if (inLine.failed) MaterialTheme.colorScheme.error else vMuted) }
				}
			}
			if (vLines.size > 6) Text("Et ${vLines.size - 6} autre(s)…", style = MaterialTheme.typography.bodySmall, color = vMuted, modifier = Modifier.padding(vertical = 6.dp))
		}
	}
}

/*
	Marque d'un objectif : rond coché vert (réussi), rond rouge (raté), rond
	vide sinon.
	inSuccess : objectif réussi
	inFailed  : objectif dépassé ou manqué
*/
@Composable
private fun GoalMark(
	inSuccess : Boolean,
	inFailed  : Boolean
)
{
	val vGreen = colorfulPalette().rise													// réussite
	val vRing  = when
	{
		inSuccess -> vGreen
		inFailed  -> MaterialTheme.colorScheme.error
		else      -> MaterialTheme.colorScheme.outline
	}																					// couleur du rond
	Box(
		modifier         = Modifier.size(20.dp).then(if (inSuccess) Modifier.background(vGreen.copy(alpha = 0.14f), CircleShape) else Modifier).border(1.4.dp, vRing, CircleShape),
		contentAlignment = Alignment.Center
	) {
		if (inSuccess) Icon(LineIcons.Check, contentDescription = null, tint = vGreen, modifier = Modifier.size(12.dp))
	}
}

/*
	Répartition des avoirs par type, nette des crédits : jauge en arc aux
	couleurs des types (la plus grande part d'abord), part du premier type au
	centre ; puis la légende avec montant et part.
	inState    : tableau de bord
	inModifier : place du panneau
*/
@Composable
private fun RepartitionPanel(
	inState      : DashboardState,
	inController : DesktopController,
	inModifier   : Modifier
)
{
	val vGroups = inState.home.assetGroups.sortedByDescending { it.subtotalCents }		// de la plus grande part à la plus petite
	val vMuted  = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	Tile(
		inModifier = inModifier,
		inTitle    = "Répartition",
		inAction   = { MoreMenu(listOf("Ajouter un compte" to { inController.openPanel(Panel.AccountSheet(null)) }, "Voir la projection" to { inController.show(Screen.PROJECTION) }), "Actions de la répartition") }
	) {
		val vFirst = vGroups.firstOrNull()												// plus grande part
		Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
			ArcGauge(
				inParts    = vGroups.map { it.subtotalCents.coerceAtLeast(0L).toFloat() to typeColor(it.type) },
				inModifier = Modifier.fillMaxWidth(0.82f).height(178.dp).semantics { contentDescription = "Répartition par type de compte, nette des crédits : " + vGroups.joinToString { "${it.type.label} ${it.percent} %" } },
				inStroke   = 18.dp
			) {
				if (vFirst != null)
				{
					Column(horizontalAlignment = Alignment.CenterHorizontally) {
						Text(vFirst.type.label, style = MaterialTheme.typography.bodyMedium, color = vMuted)
						Text("${vFirst.percent} %", style = MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light))
					}
				}
			}
		}
		Spacer(Modifier.height(8.dp))
		Well {
			vGroups.forEach { inGroup ->
				Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
					SeriesDot(typeColor(inGroup.type))
					Spacer(Modifier.width(10.dp))
					Text(inGroup.type.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
					AmountText(inCents = inGroup.subtotalCents, inStyle = MaterialTheme.typography.bodySmall, inColor = vMuted)
					Spacer(Modifier.width(10.dp))
					StatusChip("${inGroup.percent} %", Tone.NEUTRAL, typeColor(inGroup.type))
				}
			}
		}
	}
}

/*
	Rangée des quatre indicateurs : patrimoine sur douze mois, revenus,
	dépenses et taux d'épargne du mois, chacun avec sa variation et ses
	mini-barres mois par mois (le mois en cours plus marqué).
	inState    : tableau de bord
	inAnalysis : douze mois de revenus et de dépenses
*/
@Composable
private fun RowScope.Indicators(
	inState      : DashboardState,
	inAnalysis   : ExpenseAnalysis,
	inDays       : MonthDays,
	inController : DesktopController
)
{
	val vReport  = listOf("Ouvrir le bilan" to { inController.show(Screen.REPORT) })		// action des indicateurs
	val vToday   = inDays.todayIndex														// aujourd'hui
	val vDayFade = { inColor: Color, inIndex: Int -> when { inIndex > vToday -> Color.Unspecified; inIndex == vToday -> inColor; else -> inColor.copy(alpha = 0.6f) } }	// jours passés plus discrets, à venir en gris
	val vPalette = colorfulPalette()														// couleurs des données
	val vGrey    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)			// barre neutre
	val vNow     = inAnalysis.currentIndex												// mois en cours
	val vFirst   = inAnalysis.firstIndex													// premier mois suivi
	val vFade    = { inColor: Color, inIndex: Int -> if (inIndex == vNow) inColor else inColor.copy(alpha = 0.55f) }	// mois passés plus discrets
	val vSeries  = inState.home.series														// patrimoine en fin de mois
	val vSteps   = vSeries.zipWithNext { inA, inB -> inB.valueCents - inA.valueCents }		// variation de chaque mois
	val vYear    = inState.yearChange ?: if (vSeries.size > 1) vSeries.last().valueCents - vSeries.first().valueCents else null	// variation sur la période suivie
	val vStart   = vSeries.firstOrNull()?.valueCents ?: 0L								// patrimoine au début
	Indicator(
		inTitle   = "Patrimoine",
		inValue   = { if (vYear != null) AmountText(inCents = vYear, inStyle = MaterialTheme.typography.headlineMedium, inShowPlusSign = true) else Text("–", style = MaterialTheme.typography.headlineMedium) },
		inDelta   = if (vYear != null && vStart > 0) formatPermille(vYear * 1000 / vStart) to (vYear > 0) else null,
		inCaption = if (inState.yearChange != null) "Variation sur douze mois" else if (vSeries.size > 1) "Variation depuis le début du suivi" else "Il faut un mois d’historique",
		inBars    = vSteps,
		inColorOf = { inIndex -> val vValue = vSteps[inIndex]; vFade(if (vValue >= 0) vPalette.rise else vPalette.fall, if (inIndex == vSteps.lastIndex) vNow else -1) },
		inMark    = vSteps.lastIndex.takeIf { it >= 0 },
		inBar     = 6.dp,
		inMenu    = vReport
	)
	val vIncome  = inAnalysis.totalIncome[vNow]												// revenus du mois
	val vIncPrev = inAnalysis.totalIncome.getOrNull(vNow - 1) ?: 0L						// revenus du mois dernier
	Indicator(
		inTitle   = "Revenus du mois",
		inValue   = { AmountText(inCents = vIncome, inStyle = MaterialTheme.typography.headlineMedium, inShowPlusSign = vIncome > 0) },
		inDelta   = change(vIncome, vIncPrev)?.let { it to (vIncome >= vIncPrev) },
		inCaption = "Comparés au mois dernier ; jour par jour",
		inBars    = inDays.income,
		inColorOf = { inIndex -> vDayFade(vPalette.rise, inIndex).takeIf { it != Color.Unspecified } ?: vGrey },
		inMark    = vToday,
		inMenu    = vReport
	)
	val vSpent   = inAnalysis.totalExpense[vNow]												// dépenses du mois
	val vSpPrev  = inAnalysis.totalExpense.getOrNull(vNow - 1) ?: 0L						// dépenses du mois dernier
	val vAverage = inAnalysis.averageExpense												// dépenses moyennes
	Indicator(
		inTitle   = "Dépenses du mois",
		inValue   = { AmountText(inCents = vSpent, inStyle = MaterialTheme.typography.headlineMedium) },
		inDelta   = change(vSpent, vSpPrev)?.let { it to (vSpent >= vSpPrev) },
		inGood    = vSpent <= vSpPrev,
		inCaption = "Comparées au mois dernier ; jour par jour",
		inBars    = inDays.expense,
		inColorOf = { inIndex -> vDayFade(vPalette.invest, inIndex).takeIf { it != Color.Unspecified } ?: vGrey },
		inMark    = vToday,
		inMenu    = vReport
	)
	val vRate    = inAnalysis.savingsRate[vNow]												// taux d'épargne du mois, en pour mille
	val vAvgRate = inAnalysis.averageRate													// taux moyen
	Indicator(
		inTitle   = "Taux d’épargne",
		inValue   = { Text(vRate?.let { "${it / 10},${kotlin.math.abs(it % 10)} %" } ?: "–", style = MaterialTheme.typography.headlineMedium) },
		inDelta   = if (vRate != null && vAvgRate != null) "${formatPermille(vRate - vAvgRate).removeSuffix(" %")} pt" to (vRate >= vAvgRate) else null,
		inCaption = "Écart à la moyenne ; mis de côté ce mois-ci : ",
		inAmount  = inState.saved,
		inBars    = inAnalysis.savingsRate.map { it ?: 0L },
		inColorOf = { inIndex -> if (inIndex < vFirst) vGrey else vFade(if ((inAnalysis.savingsRate[inIndex] ?: 0L) >= 0) vPalette.rise else vPalette.fall, inIndex) },
		inMark    = vNow,
		inBar     = 6.dp,
		inMenu    = vReport
	)
}

/*
	Variation d'une valeur sur la précédente, en pour cent signé ; null si la
	précédente est nulle.
	inNow    : valeur actuelle
	inBefore : valeur précédente
*/
private fun change(
	inNow    : Long,
	inBefore : Long
): String? = if (inBefore <= 0L) null else formatPermille((inNow - inBefore) * 1000 / inBefore)

/*
	Indicateur : titre discret, valeur en grand et pastille de variation,
	explication, puis mini-barres.
	inTitle   : titre
	inValue   : valeur
	inDelta   : texte de la variation et son sens (vrai : hausse) ; null sans
	inGood    : sens favorable (null : la hausse l'est)
	inCaption : explication
	inAmount  : montant ajouté à la fin de l'explication ; null sans
	inBars    : valeurs des barres, du plus ancien au plus récent
	inColorOf : couleur de la barre d'un rang
	inMark    : rang de la barre repérée (aujourd'hui, mois en cours), qui porte la variation ; null sans
	inBar     : épaisseur d'une barre
	inMenu    : actions du bouton « … »
*/
@Composable
private fun RowScope.Indicator(
	inTitle   : String,
	inValue   : @Composable () -> Unit,
	inDelta   : Pair<String, Boolean>?,
	inCaption : String,
	inBars    : List<Long>,
	inColorOf : (Int) -> Color,
	inGood    : Boolean?                       = null,
	inAmount  : Long?                          = null,
	inMark    : Int?                           = null,
	inBar     : androidx.compose.ui.unit.Dp    = 3.dp,
	inMenu    : List<Pair<String, () -> Unit>> = emptyList()
)
{
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	Tile(
		inModifier   = Modifier.weight(1f).fillMaxHeight(),
		inTitle      = inTitle,
		inTitleStyle = MaterialTheme.typography.bodyLarge.copy(color = vMuted),
		inAction     = if (inMenu.isEmpty()) null else { { MoreMenu(inMenu, "Actions : $inTitle") } }
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			inValue()
			inDelta?.let { (inText, inRise) ->
				Spacer(Modifier.width(10.dp))
				DeltaPill(inText, inRise, inGood)
			}
		}
		Spacer(Modifier.height(2.dp))
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(inCaption, style = MaterialTheme.typography.bodySmall, color = vMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
			if (inAmount != null) AmountText(inCents = inAmount, inStyle = MaterialTheme.typography.bodySmall, inShowPlusSign = true, inColor = vMuted)
		}
		Spacer(Modifier.weight(1f))
		Spacer(Modifier.height(16.dp))
		MicroBars(inBars, inColorOf, Modifier.fillMaxWidth().height(58.dp).semantics { contentDescription = "$inTitle, barre par barre" }, inBar, inMark = inMark, inLabel = inDelta?.first?.removePrefix("+"))
	}
}

/*
	Dépenses du mois par catégorie : total et écart à la moyenne, barre
	segmentée des trois plus grosses catégories (chacune dans sa matière :
	fines barres, aplat, pavés) et des autres, légende, puis la catégorie la
	plus en hausse le mois dernier.
	inLines    : dépenses nettes du mois par catégorie
	inAnalysis : douze mois de dépenses
	inModifier : place du panneau
*/
@Composable
private fun ExpensesPanel(
	inLines      : List<CategoryLine>,
	inAnalysis   : ExpenseAnalysis,
	inController : DesktopController,
	inModifier   : Modifier
)
{
	val vMuted   = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	val vPalette = colorfulPalette()														// couleurs des données
	val vShown   = inLines.filter { it.netCents > 0 }										// catégories dépensières
	val vTotal   = vShown.sumOf { it.netCents }												// dépenses du mois
	val vTop     = vShown.take(3)															// trois plus grosses
	val vRest    = vTotal - vTop.sumOf { it.netCents }										// autres catégories
	val vColors  = listOf(vPalette.main, vPalette.invest, vPalette.rise, MaterialTheme.colorScheme.outline)	// teinte de chaque segment
	Tile(
		inModifier = inModifier,
		inTitle    = "Dépenses par catégorie",
		inAction   = { MoreMenu(listOf("Ouvrir le bilan" to { inController.show(Screen.REPORT) }, "Rechercher une opération" to inController::focusSearch, "Voir le budget" to { inController.show(Screen.BUDGET) }), "Actions des dépenses") }
	) {
		if (vShown.isEmpty())
		{
			Hint("Aucune dépense ce mois-ci sur les comptes courants.")
			return@Tile
		}
		Row(verticalAlignment = Alignment.CenterVertically) {
			AmountText(inCents = vTotal, inStyle = MaterialTheme.typography.headlineMedium)
			val vAverage = inAnalysis.averageExpense												// dépenses moyennes
			if (vAverage > 0)
			{
				Spacer(Modifier.width(10.dp))
				DeltaPill(formatPermille((vTotal - vAverage) * 1000 / vAverage), vTotal > vAverage, vTotal <= vAverage)
				Spacer(Modifier.width(8.dp))
				Text("par rapport à la moyenne", style = MaterialTheme.typography.bodySmall, color = vMuted)
			}
		}
		Spacer(Modifier.height(18.dp))
		val vParts = vTop.map { it.netCents } + listOfNotNull(vRest.takeIf { it > 0 })			// segments
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			vParts.forEachIndexed { inIndex, inPart ->
				Column(Modifier.weight(inPart.toFloat().coerceAtLeast(1f))) {
					Text("${inPart * 100 / vTotal} %", style = MaterialTheme.typography.labelLarge, maxLines = 1)
					Spacer(Modifier.height(4.dp))
					Box(Modifier.width(1.5.dp).height(9.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)))
				}
			}
		}
		Spacer(Modifier.height(2.dp))
		SegmentedBar(vParts, vColors, Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription = "Dépenses du mois : " + vTop.joinToString { "${it.category.name} ${it.netCents * 100 / vTotal} %" } })
		Spacer(Modifier.height(14.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
			vTop.forEachIndexed { inIndex, inLine ->
				Row(verticalAlignment = Alignment.CenterVertically) {
					SeriesDot(vColors[inIndex])
					Spacer(Modifier.width(8.dp))
					Text(inLine.category.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
				}
			}
			if (vRest > 0)
			{
				Row(verticalAlignment = Alignment.CenterVertically) {
					SeriesDot(vColors[3])
					Spacer(Modifier.width(8.dp))
					Text("Autres", style = MaterialTheme.typography.bodyMedium)
				}
			}
		}
		Spacer(Modifier.height(16.dp))
		Well {
			vShown.take(5).forEachIndexed { inIndex, inLine ->
				if (inIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
				Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
					Icon(CategoryIcons.of(inLine.category.icon), contentDescription = null, tint = vMuted, modifier = Modifier.size(16.dp))
					Spacer(Modifier.width(12.dp))
					Text(inLine.category.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
					Text("${inLine.netCents * 100 / vTotal} %", style = MaterialTheme.typography.labelSmall, color = vMuted)
					Spacer(Modifier.width(16.dp))
					Box(Modifier.width(110.dp), contentAlignment = Alignment.CenterEnd) { AmountText(inCents = inLine.netCents, inStyle = MaterialTheme.typography.labelLarge) }
				}
			}
		}
		inAnalysis.rises.firstOrNull()?.let { inRise ->
			Spacer(Modifier.weight(1f))
			Spacer(Modifier.height(12.dp))
			AlertRow {
				Icon(CategoryIcons.of(inRise.category.icon), contentDescription = null, tint = vPalette.invest, modifier = Modifier.size(16.dp))
				Spacer(Modifier.width(12.dp))
				Text("Le mois dernier, ${inRise.category.name} a dépassé sa moyenne", style = MaterialTheme.typography.bodyMedium, color = vMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
				AmountText(inCents = inRise.lastGap ?: 0L, inStyle = MaterialTheme.typography.titleSmall, inShowPlusSign = true)
			}
		}
	}
}

/*
	Ligne d'alerte d'un panneau : cadre teinté de jaune citron, liseré du même
	ton ; contenu en rangée (icône, texte, valeur).
	inContent : contenu
*/
@Composable
private fun AlertRow(inContent: @Composable RowScope.() -> Unit)
{
	val vNight = isNight()																// fond sombre
	Row(
		modifier          = Modifier
			.fillMaxWidth()
			.heightIn(min = 46.dp)
			.background(kLemon.copy(alpha = if (vNight) 0.08f else 0.22f), kWellShape)
			.border(1.dp, kLemon.copy(alpha = if (vNight) 0.25f else 0.6f), kWellShape)
			.padding(horizontal = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		content           = inContent
	)
}

/*
	Barre segmentée des dépenses : un segment par part, séparés d'un petit
	écart, chacun dans sa matière (fines barres, aplat arrondi, pavés, fines
	barres à nouveau).
	inParts    : montant de chaque segment
	inColors   : teinte de chaque segment
	inModifier : modificateur appliqué (taille comprise)
*/
@Composable
private fun SegmentedBar(
	inParts    : List<Long>,
	inColors   : List<Color>,
	inModifier : Modifier
)
{
	Canvas(inModifier) {
		val vTotal = inParts.sum().toFloat().takeIf { it > 0f } ?: return@Canvas			// total
		val vGap   = 6.dp.toPx()															// écart entre segments
		val vFree  = size.width - vGap * (inParts.size - 1)									// largeur à partager
		val vR     = CornerRadius(3.dp.toPx(), 3.dp.toPx())									// arrondi
		var vX     = 0f																	// abscisse courante
		inParts.forEachIndexed { inIndex, inPart ->
			val vW     = vFree * inPart / vTotal												// largeur du segment
			val vColor = inColors[inIndex.coerceAtMost(inColors.lastIndex)]					// teinte
			when (inIndex % 3)
			{
				0 ->
				{
					val vBar  = 3.dp.toPx()															// fine barre
					val vStep = 6.dp.toPx()															// pas
					var vAt   = vX																	// position
					while (vAt + vBar <= vX + vW) { drawRoundRect(vColor, Offset(vAt, 0f), Size(vBar, size.height), CornerRadius(vBar / 2, vBar / 2)); vAt += vStep }
				}
				1 -> drawRoundRect(vColor, Offset(vX, 0f), Size(vW, size.height), vR)
				else ->
				{
					val vBlock = size.height * 0.85f												// pavé
					val vCount = maxOf(1, ((vW + 4.dp.toPx()) / (vBlock + 4.dp.toPx())).toInt())	// pavés
					val vEach  = (vW - 4.dp.toPx() * (vCount - 1)) / vCount						// largeur d'un pavé
					repeat(vCount) { inBlock -> drawRoundRect(vColor, Offset(vX + inBlock * (vEach + 4.dp.toPx()), (size.height - vBlock) / 2), Size(vEach, vBlock), vR) }
				}
			}
			vX += vW + vGap
		}
	}
}

/*
	Dernières opérations en tableau léger : libellé, date, compte et
	catégorie, montant ; « À venir » pour une opération datée plus tard ; un
	clic ouvre l'opération.
	inState      : tableau de bord
	inToday      : date du jour
	inController : contrôleur
	inModifier   : place du panneau
*/
@Composable
private fun RecentPanel(
	inState      : DashboardState,
	inToday      : LocalDate,
	inController : DesktopController,
	inModifier   : Modifier
)
{
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	Tile(
		inModifier = inModifier,
		inTitle    = "Dernières opérations",
		inAction   = {
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
				if (inState.upcoming > 0) StatusChip("${inState.upcoming} à venir", Tone.WARN)
				MoreMenu(listOf("Nouvelle opération" to inController::newOperation, "Rechercher une opération" to inController::focusSearch, "Saisie rapide" to { inController.show(Screen.OPERATIONS) }), "Actions des opérations")
			}
		}
	) {
		if (inState.recent.isEmpty())
		{
			AssistantHint("Aucune opération pour l’instant : « Nouvelle opération » (Ctrl+N) pour la première.")
			return@Tile
		}
		Well {
			Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
				Text("Libellé", style = MaterialTheme.typography.labelMedium, color = vMuted, modifier = Modifier.weight(1.3f))
				Text("Date", style = MaterialTheme.typography.labelMedium, color = vMuted, modifier = Modifier.width(70.dp))
				Text("Compte et catégorie", style = MaterialTheme.typography.labelMedium, color = vMuted, modifier = Modifier.weight(1.4f))
				Text("Montant", style = MaterialTheme.typography.labelMedium, color = vMuted, modifier = Modifier.width(110.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
			}
			inState.recent.forEach { inOp ->
				HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
				Row(
					modifier          = Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "Ouvrir l’opération") { inController.openTransaction(inOp.transaction) },
					verticalAlignment = Alignment.CenterVertically
				) {
					Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically) {
						Text(inOp.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
						if (inOp.transaction.date.isAfter(inToday))
						{
							Spacer(Modifier.width(8.dp))
							StatusChip("À venir", Tone.WARN)
						}
					}
					Text(inOp.transaction.date.format(kShortDay), style = MaterialTheme.typography.bodySmall, color = vMuted, modifier = Modifier.width(70.dp))
					Text(inOp.detail, style = MaterialTheme.typography.bodySmall, color = vMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f))
					Box(Modifier.width(110.dp), contentAlignment = Alignment.CenterEnd) {
						AmountText(inCents = inOp.amount, inStyle = MaterialTheme.typography.labelLarge, inShowPlusSign = inOp.amount > 0, inTrend = inOp.amount > 0)
					}
				}
			}
		}
	}
}

/*
	Comptes en tableau léger : nom et type, évolution en mini-barres,
	variation du mois, part du patrimoine, valeur ; un clic ouvre le compte.
	Réorganisation : flèches pour déplacer un compte d'un rang.
	inState      : tableau de bord
	inSpace      : données (profils, vue Famille)
	inController : contrôleur
	inModifier   : place du panneau
*/
@Composable
private fun AccountsPanel(
	inState      : DashboardState,
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inModifier   : Modifier
)
{
	var vReorder by remember { mutableStateOf(false) }									// réorganisation
	val vMuted   = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	val vHolders = if (inController.viewProfile() == null && inSpace.data.profiles.size > 1)
		inSpace.data.accounts.associate { inAccount -> inAccount.id to inAccount.owners.singleOrNull()?.takeIf { it.percent == 100 }?.let { inOwner -> inSpace.data.profiles.firstOrNull { it.id == inOwner.profileId }?.name } }
	else emptyMap()																		// vue Famille : titulaire de chaque compte détenu à 100 % par un profil
	Tile(
		inModifier = inModifier,
		inTitle    = "Comptes",
		inAction   = {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				if (inState.tiles.size > 1) GhostPill(if (vReorder) "Terminer" else "Réorganiser", { vReorder = !vReorder })
				GhostPill("Ajouter un compte", { inController.openPanel(Panel.AccountSheet(null)) }, inIcon = LineIcons.Plus)
			}
		}
	) {
		Well {
			Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
				Text("Compte", style = MaterialTheme.typography.labelMedium, color = vMuted, modifier = Modifier.weight(1.6f))
				Text("Douze mois", style = MaterialTheme.typography.labelMedium, color = vMuted, modifier = Modifier.weight(1f))
				Text("Ce mois-ci", style = MaterialTheme.typography.labelMedium, color = vMuted, modifier = Modifier.width(140.dp))
				Text("Part", style = MaterialTheme.typography.labelMedium, color = vMuted, modifier = Modifier.width(70.dp))
				Text("Valeur", style = MaterialTheme.typography.labelMedium, color = vMuted, modifier = Modifier.width(140.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
				if (vReorder) Spacer(Modifier.width(88.dp))
			}
			inState.tiles.forEachIndexed { inIndex, inTile ->
				HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
				AccountRow(
					inTile   = inTile,
					inHolder = vHolders[inTile.account.id],
					inOnOpen = if (vReorder) null else { { inController.openAccount(inTile.account.id) } },
					inMove   = if (vReorder) { inStep -> move(inController, inState, inTile, inStep) } else null,
					inFirst  = inIndex == 0,
					inLast   = inIndex == inState.tiles.lastIndex
				)
			}
		}
	}
}

/*
	Ligne d'un compte dans le tableau des comptes.
	inTile   : compte calculé
	inHolder : titulaire (vue Famille) ; null sans
	inOnOpen : ouverture ; null en réorganisation
	inMove   : déplacement d'un rang (−1 / +1) ; null hors réorganisation
	inFirst  : premier compte (pas de montée)
	inLast   : dernier compte (pas de descente)
*/
@Composable
private fun AccountRow(
	inTile   : AccountTile,
	inHolder : String?,
	inOnOpen : (() -> Unit)?,
	inMove   : ((Int) -> Unit)?,
	inFirst  : Boolean,
	inLast   : Boolean
)
{
	val vMuted  = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	val vAccent = typeColor(inTile.account.type)												// couleur du type
	val vSteps  = inTile.series.map { it.valueCents }											// valeur en fin de mois
	Row(
		modifier          = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(6.dp)).then(if (inOnOpen != null) Modifier.clickable(onClickLabel = "Ouvrir ${inTile.account.name}", onClick = inOnOpen) else Modifier),
		verticalAlignment = Alignment.CenterVertically
	) {
		Row(Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
			SeriesDot(vAccent)
			Spacer(Modifier.width(12.dp))
			Column {
				Text(inTile.account.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text(listOfNotNull(accountDetail(inTile.account), inTile.note).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = vMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
				inHolder?.let { Text("Titulaire : $it", style = MaterialTheme.typography.bodySmall, color = vMuted, maxLines = 1) }
			}
		}
		Box(Modifier.weight(1f).padding(end = 24.dp)) {
			if (vSteps.size > 1) MicroBars(vSteps, { inIndex -> if (inIndex == vSteps.lastIndex) vAccent else vAccent.copy(alpha = 0.45f) }, Modifier.fillMaxWidth().height(26.dp), 3.dp, inRelative = true)
		}
		Box(Modifier.width(140.dp)) {
			inTile.monthChange?.let { inChange -> AmountText(inCents = inChange, inStyle = MaterialTheme.typography.bodyMedium, inShowPlusSign = true, inTrend = true) }
		}
		Box(Modifier.width(70.dp)) {
			inTile.weight?.let { inWeight -> StatusChip("${inWeight.percent} %", Tone.NEUTRAL) }
		}
		Box(Modifier.width(140.dp), contentAlignment = Alignment.CenterEnd) {
			AmountText(inCents = inTile.valueCents, inStyle = MaterialTheme.typography.titleMedium)
		}
		if (inMove != null)
		{
			Spacer(Modifier.width(8.dp))
			IconButton(onClick = { inMove(-1) }, enabled = !inFirst) { Icon(LineIcons.ChevronUp, contentDescription = "Monter ${inTile.account.name}") }
			IconButton(onClick = { inMove(1) }, enabled = !inLast) { Icon(LineIcons.ChevronDown, contentDescription = "Descendre ${inTile.account.name}") }
		}
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
	Petite barre de proportion (rail et part remplie) ; part remplie à
	l'accent du thème Coloré, sinon à l'encre.
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
		inSegments    = listOf(BarSegment((inFraction.coerceIn(0f, 1f) * 1000).toLong(), if (isColorful()) colorfulMain() else MaterialTheme.colorScheme.primary), BarSegment(((1f - inFraction.coerceIn(0f, 1f)) * 1000).toLong(), glassWell(MaterialTheme.colorScheme.surfaceContainerHigh))),
		inDescription = "${(inFraction * 100).toInt()} %",
		inModifier    = inModifier
	)
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
		AssistantHint("Le patrimoine se calcule à partir de vos comptes et de leurs opérations. Créez un premier compte, ou attendez le fichier du téléphone.")
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
