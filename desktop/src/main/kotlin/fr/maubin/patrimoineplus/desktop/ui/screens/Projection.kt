package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.Cell
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.desktop.ui.components.TileRow
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.SeriesDot
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.components.TileFigure
import fr.maubin.patrimoineplus.ui.projection.Projection
import fr.maubin.patrimoineplus.ui.projection.ProjectionInput
import fr.maubin.patrimoineplus.ui.projection.defaultProjectionInput
import fr.maubin.patrimoineplus.ui.projection.kProjectionSpreadBrokerage
import fr.maubin.patrimoineplus.ui.projection.kProjectionSpreadSavings
import fr.maubin.patrimoineplus.ui.projection.projectWealth
import fr.maubin.patrimoineplus.ui.components.smoothPath
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.flowAccent
import fr.maubin.patrimoineplus.ui.components.flowColorOr
import fr.maubin.patrimoineplus.ui.components.FlowAccent
import fr.maubin.patrimoineplus.ui.components.isColorful

private val kHorizons = listOf(5, 10, 20, 30)											// horizons proposés, en années

/*
	Projection du patrimoine : hypothèses modifiables (horizon, épargne
	mensuelle, rendements, part en bourse), patrimoine estimé à l'horizon
	selon trois scénarios et courbe année par année. Une estimation, pas une
	prévision : rendements constants, sans impôts ni inflation.
	inSpace      : données
	inController : contrôleur
*/
@Composable
fun ProjectionScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vStored  by inController.projection.collectAsState()							// hypothèses choisies
	val vDefault = remember(inSpace.data, inSpace.today) { defaultProjectionInput(inSpace.data, inSpace.today) }	// hypothèses par défaut
	val vInput   = vStored ?: vDefault													// hypothèses retenues
	val vResult  = remember(inSpace.data, inSpace.today, vInput) { projectWealth(inSpace.data, inSpace.today, vInput) }	// projection
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		Column {
			Text("Projection", style = MaterialTheme.typography.headlineMedium)
			Text("Estimation du patrimoine selon vos hypothèses : comptes courants et biens (au prix d’achat) stables, crédits remboursés selon leur tableau d’amortissement (le capital de chaque mensualité est pris sur l’épargne mensuelle), épargne et bourse capitalisées chaque mois. Rendements constants, sans impôts ni inflation : un ordre de grandeur, pas une prévision.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Inputs(vInput, vDefault) { inController.setProjection(it) }
		TileRow(
			Cell(1f) { inMod -> Tile(inModifier = inMod) { TileFigure("Aujourd’hui") { AmountText(inCents = vResult.start, inStyle = MaterialTheme.typography.headlineSmall) } } },
			Cell(1f) { inMod -> Tile(inModifier = inMod, inAccent = flowAccent(FlowAccent.INFLOW)) { TileFigure("Dans ${vInput.years} ans, prudent") { AmountText(inCents = vResult.last.prudent, inStyle = MaterialTheme.typography.headlineSmall) } } },
			Cell(1.2f) { inMod -> Tile(inModifier = inMod, inFilled = isColorful()) { TileFigure("Dans ${vInput.years} ans, central") { AmountText(inCents = vResult.last.central, inStyle = MaterialTheme.typography.headlineMedium) } } },
			Cell(1f) { inMod -> Tile(inModifier = inMod, inAccent = flowAccent(FlowAccent.INFLOW)) { TileFigure("Dans ${vInput.years} ans, favorable") { AmountText(inCents = vResult.last.optimistic, inStyle = MaterialTheme.typography.headlineSmall) } } },
			Cell(1f) { inMod ->
				Tile(inModifier = inMod) {
					TileFigure("Dont versements") { AmountText(inCents = vResult.last.contributed - vResult.start, inStyle = MaterialTheme.typography.headlineSmall) }
					Spacer(Modifier.height(10.dp))
					TileFigure("Gain des rendements") { AmountText(inCents = vResult.last.central - vResult.last.contributed, inStyle = MaterialTheme.typography.titleMedium, inShowPlusSign = true) }
					if (vResult.points.first().debt > 0)
					{
						Spacer(Modifier.height(10.dp))
						TileFigure("Crédits restant dus") { AmountText(inCents = vResult.last.debt, inStyle = MaterialTheme.typography.titleMedium) }
						Hint("${if (LocalAmountsHidden.current) kMaskedAmount else formatEuros(vResult.points.first().debt)} aujourd’hui")
					}
				}
			},
			inMinWidth = 1000.dp
		)
		Tile(inTitle = "Année par année") {
			Chart(vResult)
		}
	}
}

/*
	Hypothèses modifiables ; « Valeurs par défaut » revient à celles tirées
	des données.
	inInput   : hypothèses retenues
	inDefault : hypothèses par défaut
	inOnInput : nouvelles hypothèses ; null pour celles par défaut
*/
@Composable
private fun Inputs(
	inInput   : ProjectionInput,
	inDefault : ProjectionInput,
	inOnInput : (ProjectionInput?) -> Unit
)
{
	var vMonthly by remember(inInput.monthlySavings) { mutableStateOf(editableEuros(inInput.monthlySavings)) }	// épargne mensuelle tapée
	var vSaving  by remember(inInput.savingsRate) { mutableStateOf(rateText(inInput.savingsRate)) }			// rendement de l'épargne tapé
	var vStock   by remember(inInput.brokerageRate) { mutableStateOf(rateText(inInput.brokerageRate)) }		// rendement de la bourse tapé
	var vShare   by remember(inInput.stockShare) { mutableStateOf(inInput.stockShare.toString()) }			// part en bourse tapée
	Tile(
		inTitle  = "Hypothèses",
		inAction = { if (inInput != inDefault) TextButton(onClick = { inOnInput(null) }) { Text("Valeurs par défaut") } }
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text("Horizon", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(120.dp))
			kHorizons.forEach { inYears ->
				FilterChip(selected = inInput.years == inYears, onClick = { inOnInput(inInput.copy(years = inYears)) }, label = { Text("$inYears ans") }, modifier = Modifier.padding(end = 8.dp))
			}
		}
		Spacer(Modifier.height(12.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			OutlinedTextField(
				value = vMonthly, singleLine = true, modifier = Modifier.weight(1f),
				label = { Text("Épargne mensuelle (€)") },
				supportingText = { Text("Par défaut : épargne moyenne des 12 derniers mois") },
				isError = parseEuros(vMonthly) == null,
				onValueChange = { inText -> vMonthly = inText; parseEuros(inText)?.takeIf { it >= 0 }?.let { inOnInput(inInput.copy(monthlySavings = it)) } }
			)
			OutlinedTextField(
				value = vSaving, singleLine = true, modifier = Modifier.weight(1f),
				label = { Text("Rendement de l’épargne (%/an)") },
				supportingText = { Text("Scénarios : ± ${rateText(kProjectionSpreadSavings)} point") },
				isError = parseRate(vSaving) == null,
				onValueChange = { inText -> vSaving = inText; parseRate(inText)?.let { inOnInput(inInput.copy(savingsRate = it)) } }
			)
			OutlinedTextField(
				value = vStock, singleLine = true, modifier = Modifier.weight(1f),
				label = { Text("Rendement de la bourse (%/an)") },
				supportingText = { Text("Scénarios : ± ${rateText(kProjectionSpreadBrokerage)} points") },
				isError = parseRate(vStock) == null,
				onValueChange = { inText -> vStock = inText; parseRate(inText)?.let { inOnInput(inInput.copy(brokerageRate = it)) } }
			)
			OutlinedTextField(
				value = vShare, singleLine = true, modifier = Modifier.weight(1f),
				label = { Text("Part versée en bourse (%)") },
				supportingText = { Text("Par défaut : répartition actuelle") },
				isError = vShare.trim().toIntOrNull()?.let { it in 0..100 } != true,
				onValueChange = { inText -> vShare = inText; inText.trim().toIntOrNull()?.takeIf { it in 0..100 }?.let { inOnInput(inInput.copy(stockShare = it)) } }
			)
		}
	}
}

/*
	Courbe de la projection : trois scénarios (central en trait plein,
	prudent et favorable en pointillés) et versements seuls (trait fin gris),
	avec les années et les valeurs extrêmes.
	inResult : projection
*/
@Composable
private fun Chart(inResult: Projection)
{
	val vInk   = flowColorOr(FlowAccent.MAIN, MaterialTheme.colorScheme.onSurface)		// scénario central
	val vSoft  = MaterialTheme.colorScheme.onSurfaceVariant							// textes des axes
	val vSide  = flowColorOr(FlowAccent.INFLOW, vSoft)								// autres scénarios (bleu-vert en thème Coloré)
	val vLight = flowColorOr(FlowAccent.INVEST, MaterialTheme.colorScheme.outline)	// versements seuls (ambre en thème Coloré)
	val vRule  = MaterialTheme.colorScheme.outlineVariant								// filets
	Row(verticalAlignment = Alignment.CenterVertically) {
		listOf(vInk to "Central", vSide to "Prudent et favorable", vLight to "Versements seuls").forEach { (inColor, inLabel) ->
			SeriesDot(inColor); Spacer(Modifier.width(8.dp)); Text(inLabel, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.width(20.dp))
		}
	}
	Spacer(Modifier.height(12.dp))
	val vMax = inResult.points.maxOf { it.optimistic }.coerceAtLeast(1L)				// plus grande valeur
	val vMin = inResult.points.minOf { minOf(it.prudent, it.contributed) }.coerceAtMost(0L)	// plus petite valeur
	Row {
		Column(Modifier.height(260.dp).width(110.dp), verticalArrangement = Arrangement.SpaceBetween) {
			AmountText(inCents = vMax, inStyle = MaterialTheme.typography.labelMedium, inColor = vSoft)
			AmountText(inCents = vMin, inStyle = MaterialTheme.typography.labelMedium, inColor = vSoft)
		}
		Canvas(Modifier.weight(1f).height(260.dp).semantics { contentDescription = "Patrimoine projeté année par année" }) {
			val vSpan  = (vMax - vMin).toFloat()													// amplitude
			val vX     = { inYear: Int -> size.width * inYear / inResult.input.years.coerceAtLeast(1) }	// abscisse d'une année
			val vY     = { inValue: Long -> size.height * (1f - (inValue - vMin) / vSpan) }	// ordonnée d'une valeur
			drawLine(vRule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
			/*
				Trace une série.
				inValue : valeur d'un point
				inColor : couleur
				inWidth : épaisseur
				inDash  : pointillés
			*/
			fun series(inValue: (Int) -> Long, inColor: Color, inWidth: Float, inDash: Boolean)
			{
				val vPath = smoothPath(inResult.points.mapIndexed { inIndex, inPoint -> Offset(vX(inPoint.year), vY(inValue(inIndex))) })	// tracé lissé
				drawPath(vPath, inColor, style = Stroke(width = inWidth, pathEffect = if (inDash) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null))
			}
			series({ inResult.points[it].contributed }, vLight, 1.5.dp.toPx(), false)
			series({ inResult.points[it].prudent }, vSide, 1.5.dp.toPx(), true)
			series({ inResult.points[it].optimistic }, vSide, 1.5.dp.toPx(), true)
			series({ inResult.points[it].central }, vInk, 2.5.dp.toPx(), false)
		}
	}
	Row(Modifier.fillMaxWidth().padding(start = 110.dp, top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
		val vStep = (inResult.input.years / 5).coerceAtLeast(1)							// pas des étiquettes
		inResult.points.filter { it.year % vStep == 0 }.forEach { Text(if (it.year == 0) "Aujourd’hui" else "+${it.year} ans", style = MaterialTheme.typography.labelMedium, color = vSoft) }
	}
}

/*
	Taux écrit à la française (« 2,5 »).
	inRate : taux, en %
*/
private fun rateText(inRate: Double): String = (if (inRate % 1.0 == 0.0) inRate.toLong().toString() else inRate.toString()).replace('.', ',')

/*
	Taux lu (« 2,5 », « 6 », « -1 ») ; null s'il est illisible ou hors de −50 à 50 %.
	inText : texte
*/
private fun parseRate(inText: String): Double? = inText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -50.0..50.0 }
