package fr.maubin.patrimoineplus.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.format.formatPermille
import fr.maubin.patrimoineplus.domain.calc.AllocationLine
import fr.maubin.patrimoineplus.domain.calc.Position
import fr.maubin.patrimoineplus.domain.calc.allocation
import fr.maubin.patrimoineplus.domain.format.editableScaled
import fr.maubin.patrimoineplus.domain.format.formatPrice
import fr.maubin.patrimoineplus.domain.format.formatScaled
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.format.parseScaled
import fr.maubin.patrimoineplus.data.market.Listing
import fr.maubin.patrimoineplus.data.market.toPriceUnits
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.ui.components.AmountInput
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.BarSegment
import fr.maubin.patrimoineplus.ui.components.ProportionBar
import fr.maubin.patrimoineplus.ui.components.SeriesDot
import fr.maubin.patrimoineplus.ui.components.allocationColor
import fr.maubin.patrimoineplus.ui.components.kAllocationSlots
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.DateInput
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.components.FormSheet
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.components.SegmentedChoice
import fr.maubin.patrimoineplus.ui.components.TextInput
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import fr.maubin.patrimoineplus.domain.calc.PropertyItem
import fr.maubin.patrimoineplus.domain.calc.PropertyNet
import fr.maubin.patrimoineplus.ui.components.ProportionChart
import fr.maubin.patrimoineplus.ui.components.ProportionToggle

private val kShortDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)	// date courte

/*
	En-tête d'un compte Bourse : valeur totale, puis espèces, plus-value
	latente (avec sa part du prix de revient) et plus-values réalisées.
	inPortfolio : portefeuille valorisé
*/
@Composable
fun PortfolioHeader(inPortfolio: PortfolioView)
{
	val vColors = MaterialTheme.colorScheme									// palette courante
	val vLatent = inPortfolio.valuation.unrealizedCents						// plus-value latente
	val vRate   = if (inPortfolio.costCents > 0) vLatent * 1000 / inPortfolio.costCents else 0L	// rendement, en pour mille
	Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		AmountText(inCents = inPortfolio.valuation.valueCents, inStyle = MaterialTheme.typography.displaySmall)
		Spacer(Modifier.height(8.dp))
		Text("Valeur du compte, espèces comprises", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
	}
	Spacer(Modifier.height(12.dp))
	BorderedCard {
		Column(Modifier.padding(vertical = 8.dp)) {
			StatRow("Espèces", inPortfolio.valuation.cashCents, false)
			StatRow("Plus-value latente", vLatent, true, if (inPortfolio.costCents > 0) formatPermille(vRate) else null)
			StatRow("Plus-values réalisées", inPortfolio.realizedCents, true)
		}
	}
}

/*
	Ligne de statistique : libellé, montant, complément facultatif.
	inLabel  : libellé
	inCents  : montant
	inSigned : affiche le signe « + »
	inExtra  : complément (pourcentage) ; null sans complément
*/
@Composable
private fun StatRow(
	inLabel  : String,
	inCents  : Long,
	inSigned : Boolean,
	inExtra  : String? = null
)
{
	val vHidden = LocalAmountsHidden.current									// mode discret actif
	Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
		Text(inLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
		if (inExtra != null && !vHidden)
		{
			Text(inExtra, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Spacer(Modifier.width(12.dp))
		}
		AmountText(inCents = inCents, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = inSigned, inTrend = inSigned)
	}
}

/*
	Positions d'un compte Bourse : barre de répartition des titres, puis une
	ligne par titre (pastille de sa teinte, nom, part, valeur), de la plus
	grosse à la plus petite ; puis les actions (cours, positions initiales).
	Au-delà de quatre titres, les plus petits partagent la teinte « Autres ».
	inPortfolio : portefeuille valorisé
	inOpenings  : positions initiales
	inOnPrices  : ouvre la saisie manuelle des cours
	inOnAddOpen : ouvre l'ajout d'une position initiale
	inOnRemove  : retire une position initiale
	inOnRefresh : met à jour les cours automatiquement
	inOnAsset   : ouvre la fiche d'un actif
	inQuotesAt  : instant de la dernière mise à jour automatique (ms) ; null avant la première
	inChartKey  : clé de la répartition des titres (forme mémorisée pour ce compte)
*/
@Composable
fun PositionsSection(
	inPortfolio : PortfolioView,
	inOpenings  : List<OpeningPositionItem>,
	inOnPrices  : () -> Unit,
	inOnAddOpen : () -> Unit,
	inOnRemove  : (OpeningPositionItem) -> Unit,
	inOnRefresh : () -> Unit       = {},
	inOnAsset   : (String) -> Unit = {},
	inQuotesAt  : Long?            = null,
	inChartKey  : String           = "titres"
)
{
	val vPositions = inPortfolio.valuation.positions							// positions
	val vLines     = allocation(vPositions)										// répartition des titres
	SectionHeader(inTitle = "Positions", inLinkLabel = if (vPositions.isEmpty()) null else "Actualiser", inOnLink = inOnRefresh, inAction = if (vLines.size > 1) ({ ProportionToggle(inChartKey) }) else null)
	Spacer(Modifier.height(8.dp))
	if (vLines.size > 1)
	{
		ProportionChart(
			inKey         = inChartKey,
			inSegments    = vLines.groupBy { slotOf(it, vLines) }.toSortedMap().map { (inSlot, inGroup) -> BarSegment(inGroup.sumOf { it.position.valueCents }, allocationColor(inSlot)) },
			inDescription = "Répartition des titres : " + vLines.joinToString(", ") { "${inPortfolio.names[it.position.assetId] ?: "Actif supprimé"} ${it.percent}\u202F%" },
			inStable      = false
		)
		Spacer(Modifier.height(16.dp))
	}
	BorderedCard {
		if (vPositions.isEmpty())
		{
			EmptyState(inTitle = "Aucune position", inBody = "Saisissez un ordre d’achat avec « + », ou ajoutez vos positions initiales ci-dessous.")
		}
		vLines.forEachIndexed { inIndex, inLine ->
			if (inIndex > 0)
			{
				HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
			}
			PositionRow(
				inName    = inPortfolio.names[inLine.position.assetId] ?: "Actif supprimé",
				inLine    = inLine,
				inColor   = if (vLines.size > 1) allocationColor(slotOf(inLine, vLines)) else null,
				inOnClick = { inOnAsset(inLine.position.assetId) }
			)
		}
	}
	if (vPositions.isNotEmpty())
	{
		val vManual = vPositions.mapNotNull { inPortfolio.assets[it.assetId] }.filter { it.ticker == null }.map { it.name }	// titres sans cours automatique
		Text(
			text     = buildString {
				append(inQuotesAt?.let { "Cours Yahoo Finance mis à jour le ${formatQuotesTime(it)}." } ?: "Cours automatiques pas encore récupérés.")
				if (vManual.isNotEmpty()) append(" Sans cours automatique : ${vManual.joinToString(", ")} (touchez le titre pour indiquer son ISIN).")
			},
			style    = MaterialTheme.typography.bodyMedium,
			color    = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp)
		)
	}
	if (inOpenings.isNotEmpty())
	{
		Text(
			text     = "Positions initiales",
			style    = MaterialTheme.typography.labelLarge,
			color    = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
		)
		BorderedCard {
			inOpenings.forEachIndexed { inIndex, inItem ->
				if (inIndex > 0)
				{
					HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
				}
				Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
					Column(Modifier.weight(1f)) {
						Text(inItem.assetName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
						Text(
							text  = "${formatScaled(inItem.trade.quantity)} × ${formatPrice(inItem.trade.unitPrice)}",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
					IconButton(onClick = { inOnRemove(inItem) }) { Icon(LineIcons.Close, contentDescription = "Retirer la position initiale ${inItem.assetName}") }
				}
			}
		}
	}
	Row(Modifier.padding(top = 4.dp)) {
		TextButton(onClick = inOnAddOpen) { Text("Ajouter une position initiale") }
		if (vPositions.isNotEmpty())
		{
			TextButton(onClick = inOnPrices) { Text("Saisir des cours") }
		}
	}
}

/*
	Ligne d'une position : pastille de sa teinte dans la répartition, nom ;
	à droite, valeur et part des titres dessous.
	inName    : nom de l'actif
	inLine    : position et sa part
	inColor   : teinte dans la répartition ; null pour un titre unique
	inOnClick : ouvre la fiche de l'actif
*/
@Composable
private fun PositionRow(
	inName    : String,
	inLine    : AllocationLine,
	inColor   : Color?,
	inOnClick : () -> Unit
)
{
	Row(
		modifier          = Modifier.fillMaxWidth().clickable(onClickLabel = "Fiche de l’actif", onClick = inOnClick).heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		if (inColor != null)
		{
			SeriesDot(inColor)
			Spacer(Modifier.width(12.dp))
		}
		Text(inName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
		Spacer(Modifier.width(16.dp))
		Column(horizontalAlignment = Alignment.End) {
			AmountText(inCents = inLine.position.valueCents, inStyle = MaterialTheme.typography.titleMedium)
			Text("${inLine.percent}\u202F%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}
}

/*
	Rang de valeur d'une position dans la répartition.
	inLine  : position et sa part
	inLines : répartition complète, triée
*/
private fun slotOf(
	inLine  : AllocationLine,
	inLines : List<AllocationLine>
): Int = inLines.indexOf(inLine).coerceAtMost(kAllocationSlots - 1)

/*
	Relevés de la banque d'un compte Courant ou Épargne, avec l'écart au solde
	calculé et, s'il n'est pas nul, la création de l'opération d'ajustement.
	inStatements : relevés, du plus récent au plus ancien
	inOnAdd      : ouvre la saisie d'un relevé
	inOnAdjust   : crée l'opération qui comble l'écart d'un relevé
	inOnDelete   : supprime un relevé
*/
@Composable
fun StatementsSection(
	inStatements : List<StatementItem>,
	inOnAdd      : () -> Unit,
	inOnAdjust   : (StatementItem) -> Unit,
	inOnDelete   : (StatementItem) -> Unit
)
{
	val vColors = MaterialTheme.colorScheme									// palette courante
	SectionHeader(inTitle = "Relevés", inLinkLabel = "Ajouter", inOnLink = inOnAdd)
	Spacer(Modifier.height(8.dp))
	BorderedCard {
		if (inStatements.isEmpty())
		{
			EmptyState(inTitle = "Aucun relevé", inBody = "Saisissez le solde indiqué par la banque : l’appli le compare au solde calculé (intérêts non saisis, oubli…).")
		}
		inStatements.take(3).forEachIndexed { inIndex, inItem ->
			if (inIndex > 0)
			{
				HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = vColors.outlineVariant)
			}
			Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Column(Modifier.weight(1f)) {
						Text(inItem.statement.date.format(kShortDate), style = MaterialTheme.typography.bodyLarge)
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text(if (inItem.gapCents == 0L) "Concorde avec le solde calculé" else "Écart ", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
							if (inItem.gapCents != 0L)
							{
								AmountText(inCents = inItem.gapCents, inStyle = MaterialTheme.typography.bodyMedium, inColor = vColors.onSurfaceVariant, inShowPlusSign = true)
							}
						}
					}
					AmountText(inCents = inItem.statement.balanceCents, inStyle = MaterialTheme.typography.bodyLarge)
					IconButton(onClick = { inOnDelete(inItem) }) { Icon(LineIcons.Close, contentDescription = "Supprimer le relevé") }
				}
				if (inItem.gapCents != 0L)
				{
					TextButton(onClick = { inOnAdjust(inItem) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
						Text(if (inItem.gapCents > 0) "Créer l’opération Intérêts" else "Créer l’opération d’ajustement")
					}
				}
			}
		}
	}
}

/*
	Feuille de saisie d'un relevé : date et solde indiqué par la banque.
	inToday     : date proposée
	inOnSave    : enregistre (date, montant)
	inOnDismiss : fermeture
*/
@Composable
fun StatementSheet(
	inToday     : LocalDate,
	inOnSave    : (LocalDate, Long) -> Unit,
	inOnDismiss : () -> Unit
)
{
	var vDate   by remember { mutableStateOf(inToday) }						// date du relevé
	var vAmount by remember { mutableStateOf("") }							// solde saisi
	var vError  by remember { mutableStateOf<String?>(null) }				// erreur affichée
	FormSheet(inTitle = "Nouveau relevé", inOnDismiss = inOnDismiss, inDirty = vAmount.isNotBlank()) {
		AmountInput(vAmount, { vAmount = it }, "Solde indiqué par la banque", vError)
		DateInput(vDate, { vDate = it }, "Date du relevé")
		Button(
			onClick        = {
				val vCents = parseEuros(vAmount)									// montant lu
				if (vCents == null) vError = "Montant illisible (ex. 12 038,20)." else inOnSave(vDate, vCents)
			},
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) { Text("Enregistrer") }
	}
}

/*
	Feuille de mise à jour des cours : un champ par actif détenu (pré-rempli
	avec le dernier cours), une date pour tous ; seuls les cours modifiés ou
	saisis sont enregistrés.
	inPositions : positions du compte
	inNames     : noms des actifs
	inToday     : date proposée
	inOnSave    : enregistre (date, cours par actif)
	inOnDismiss : fermeture
*/
@Composable
fun PricesSheet(
	inPositions : List<Position>,
	inNames     : Map<String, String>,
	inToday     : LocalDate,
	inOnSave    : (LocalDate, Map<String, Long>) -> Unit,
	inOnDismiss : () -> Unit
)
{
	var vDate   by remember { mutableStateOf(inToday) }						// date des cours
	val vStart  = remember { inPositions.associate { it.assetId to if (it.isUnpriced) "" else editableScaled(it.unitPrice) } }	// cours de départ
	var vValues by remember { mutableStateOf(vStart) }							// cours saisis
	var vError  by remember { mutableStateOf<String?>(null) }				// erreur affichée
	FormSheet(inTitle = "Mettre à jour les cours", inOnDismiss = inOnDismiss, inDirty = vValues != vStart) {
		DateInput(vDate, { vDate = it }, "Date des cours")
		inPositions.forEach { inPosition ->
			OutlinedTextField(
				value           = vValues[inPosition.assetId].orEmpty(),
				onValueChange   = { inText -> vValues = vValues + (inPosition.assetId to inText.filter { it.isDigit() || it in ",. " }) },
				label           = { Text(inNames[inPosition.assetId] ?: "Actif") },
				suffix          = { Text("€") },
				singleLine      = true,
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
				modifier        = Modifier.fillMaxWidth()
			)
		}
		vError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		Button(
			onClick        = {
				val vChanged = vValues.filter { (inId, inText) -> inText.isNotBlank() && (inText != vStart[inId] || vDate != inToday) }	// cours à enregistrer
				val vParsed  = vChanged.mapValues { parseScaled(it.value) }		// cours lus
				if (vParsed.values.any { it == null || it == 0L }) vError = "Un cours est illisible (ex. 144,91)."
				else inOnSave(vDate, vParsed.mapValues { it.value!! })
			},
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) { Text("Enregistrer les cours") }
	}
}

/*
	Feuille d'ajout d'une position initiale : actif (existant ou nouveau),
	quantité, prix de revient unitaire.
	inAssets    : actifs existants
	inOnSave    : enregistre (actif existant ou null, nom du nouvel actif, quantité, PRU)
	inOnDismiss : fermeture
*/
@Composable
fun OpeningPositionSheet(
	inAssets    : List<Asset>,
	inOnSave    : (String?, String, Long, Long) -> Unit,
	inOnDismiss : () -> Unit
)
{
	var vNew      by remember { mutableStateOf(inAssets.isEmpty()) }			// créer un nouvel actif
	var vAssetId  by remember { mutableStateOf(inAssets.firstOrNull()?.id) }	// actif existant choisi
	var vName     by remember { mutableStateOf("") }							// nom du nouvel actif
	var vQuantity by remember { mutableStateOf("") }							// quantité saisie
	var vPrice    by remember { mutableStateOf("") }							// PRU saisi
	var vError    by remember { mutableStateOf<String?>(null) }				// erreur affichée
	FormSheet(inTitle = "Position initiale", inOnDismiss = inOnDismiss, inDirty = listOf(vName, vQuantity, vPrice).any { it.isNotBlank() }) {
		if (inAssets.isNotEmpty())
		{
			SegmentedChoice(listOf(false, true), vNew, { if (it) "Nouvel actif" else "Actif existant" }, { vNew = it })
		}
		if (vNew)
		{
			TextInput(vName, { vName = it }, "Nom de l’actif")
		}
		else
		{
			inAssets.forEach { inAsset ->
				Row(
					modifier          = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vAssetId = inAsset.id },
					verticalAlignment = Alignment.CenterVertically
				) {
					RadioButton(selected = inAsset.id == vAssetId, onClick = null)
					Spacer(Modifier.width(12.dp))
					Text(inAsset.name)
				}
			}
		}
		OutlinedTextField(
			value           = vQuantity,
			onValueChange   = { inText -> vQuantity = inText.filter { it.isDigit() || it in ",. " } },
			label           = { Text("Quantité") },
			singleLine      = true,
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
			modifier        = Modifier.fillMaxWidth()
		)
		OutlinedTextField(
			value           = vPrice,
			onValueChange   = { inText -> vPrice = inText.filter { it.isDigit() || it in ",. " } },
			label           = { Text("Prix de revient unitaire (PRU)") },
			suffix          = { Text("€") },
			singleLine      = true,
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
			modifier        = Modifier.fillMaxWidth()
		)
		vError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		OutlinedButton(
			onClick        = {
				val vQty = parseScaled(vQuantity)									// quantité lue
				val vPru = parseScaled(vPrice)										// PRU lu
				vError = when
				{
					vNew && vName.isBlank()                -> "Nommez l’actif."
					!vNew && vAssetId == null              -> "Choisissez un actif."
					vQty == null || vQty == 0L             -> "Quantité illisible ou nulle."
					vPru == null || vPru == 0L             -> "Prix de revient illisible ou nul."
					else                                   -> null
				}
				if (vError == null) inOnSave(if (vNew) null else vAssetId, vName, vQty!!, vPru!!)
			},
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) { Text("Ajouter la position") }
	}
}

/*
	Instant d'une mise à jour, formaté (« 9 oct. à 17:35 »).
	inAt : instant (ms)
*/
private fun formatQuotesTime(inAt: Long): String =
	java.time.Instant.ofEpochMilli(inAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM 'à' HH:mm", Locale.FRANCE))

/*
	Fiche d'un actif : nom, ISIN et symbole de cotation. « Chercher la
	cotation » interroge la source de cours à partir de l'ISIN et montre le
	titre trouvé (nom, place, cours) avant de l'adopter ; une cotation trouvée
	sur une autre place que celle de la recherche est signalée.
	inAsset     : actif
	inOnLookup  : cherche une cotation (ISIN, réponse : cotation ou null, message d'erreur)
	inOnSave    : enregistre (nom, ISIN, symbole)
	inOnDismiss : fermeture
*/
@Composable
fun AssetSheet(
	inAsset     : Asset,
	inOnLookup  : (String, (Listing?, String?) -> Unit) -> Unit,
	inOnSave    : (String, String, String) -> Unit,
	inOnDismiss : () -> Unit
)
{
	var vName    by remember { mutableStateOf(inAsset.name) }					// nom saisi
	var vIsin    by remember { mutableStateOf(inAsset.isin.orEmpty()) }		// ISIN saisi
	var vTicker  by remember { mutableStateOf(inAsset.ticker.orEmpty()) }		// symbole saisi
	var vFound   by remember { mutableStateOf<Listing?>(null) }				// cotation trouvée
	var vInfo    by remember { mutableStateOf<String?>(null) }				// résultat de la recherche
	var vBusy    by remember { mutableStateOf(false) }						// recherche en cours
	val vIsinOk  = Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]").matches(vIsin.trim().uppercase())	// ISIN bien formé
	FormSheet(inTitle = "Actif", inOnDismiss = inOnDismiss, inDirty = vName != inAsset.name || vIsin != inAsset.isin.orEmpty() || vTicker != inAsset.ticker.orEmpty()) {
		TextInput(vName, { vName = it }, "Nom", if (vName.isBlank()) "Le nom est obligatoire." else null)
		TextInput(vIsin, { vIsin = it; vFound = null; vInfo = null }, "ISIN", if (vIsin.isNotBlank() && !vIsinOk) "12 caractères, ex. LU1681043599." else null, "Sur vos relevés ; sert à trouver le cours.")
		OutlinedButton(
			onClick  = {
				vBusy = true
				vInfo = null
				inOnLookup(vIsin) { inListing, inError ->
					vBusy  = false
					vFound = inListing
					vInfo  = inError ?: if (inListing == null) "Aucune cotation en euros trouvée pour cet ISIN : saisissez le symbole à la main, ou les cours." else null
				}
			},
			enabled  = vIsinOk && !vBusy,
			modifier = Modifier.fillMaxWidth()
		) { Text(if (vBusy) "Recherche…" else "Chercher la cotation") }
		vFound?.let { inListing ->
			BorderedCard {
				Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
					Text("${inListing.symbol} · ${inListing.exchange.orEmpty()} · ${formatPrice(toPriceUnits(inListing.price))}", style = MaterialTheme.typography.titleMedium)
					Text(inListing.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					if (inListing.guessed)
					{
						Text("Trouvée sur une autre place que celle de la recherche : vérifiez qu’il s’agit bien de votre titre.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					}
					TextButton(onClick = { vTicker = inListing.symbol; vFound = null }) { Text("Utiliser ${inListing.symbol}") }
				}
			}
		}
		vInfo?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
		TextInput(vTicker, { vTicker = it }, "Symbole de cotation", null, "Cotation en euros, ex. CW8.PA. Vide : cours saisis à la main.")
		Button(
			onClick        = { inOnSave(vName, vIsin, vTicker) },
			enabled        = vName.isNotBlank() && (vIsin.isBlank() || vIsinOk),
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) { Text("Enregistrer") }
	}
}

/*
	En-tête d'un compte Immobilier ou Collection : valeur au prix d'achat des
	biens détenus, nombre de biens et plus-values des ventes.
	inItems  : biens du compte
	inToday  : date du jour
	inShare  : part détenue, en %
	inNets   : valeur nette des biens financés par un crédit, par achat
*/
@Composable
fun PropertyHeader(
	inItems : List<PropertyItem>,
	inToday : LocalDate,
	inShare : Int,
	inNets  : Map<String, PropertyNet> = emptyMap()
)
{
	val vColors = MaterialTheme.colorScheme											// palette courante
	val vHeld   = inItems.filter { it.heldAt(inToday) }								// biens détenus
	val vSold   = inItems.filter { inItem -> inItem.disposal?.date?.isAfter(inToday) == false }	// biens vendus
	Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		AmountText(inCents = vHeld.sumOf { it.cost }, inStyle = MaterialTheme.typography.displaySmall)
		Spacer(Modifier.height(8.dp))
		Text(if (inShare < 100) "Valeur au prix d’achat · comptée pour $inShare %" else "Valeur au prix d’achat", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
		if (inNets.isNotEmpty())
		{
			Spacer(Modifier.height(4.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text("Valeur nette ", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				AmountText(inCents = vHeld.sumOf { it.cost } - inNets.values.sumOf { it.debt }, inStyle = MaterialTheme.typography.bodyMedium, inColor = vColors.onSurfaceVariant)
				Text(" · crédits déduits", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
			}
		}
	}
	if (vSold.isNotEmpty())
	{
		Spacer(Modifier.height(12.dp))
		BorderedCard {
			Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
				Text("Plus-values des ventes", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
				AmountText(inCents = vSold.sumOf { it.gain ?: 0L }, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = true, inTrend = true)
			}
		}
	}
}

/*
	Biens d'un compte Immobilier ou Collection : détenus (toucher pour
	modifier, « Vendre »), puis vendus avec leur plus-value (toucher pour
	modifier la vente) ; ajout d'un bien.
	inItems   : biens du compte
	inToday   : date du jour
	inNoun    : « bien » ou « objet »
	inNames   : noms des comptes par identifiant
	inOnAdd   : ajout d'un bien
	inOnOpen  : ouvre l'achat d'un bien
	inOnSell  : ouvre la vente d'un bien (nouvelle ou existante)
	inNets    : valeur nette des biens financés par un crédit, par achat
*/
@Composable
fun PropertySection(
	inItems  : List<PropertyItem>,
	inToday  : LocalDate,
	inNoun   : String,
	inNames  : Map<String, String>,
	inOnAdd  : () -> Unit,
	inOnOpen : (PropertyItem) -> Unit,
	inOnSell : (PropertyItem) -> Unit,
	inNets   : Map<String, PropertyNet> = emptyMap()
)
{
	val vColors = MaterialTheme.colorScheme											// palette courante
	val vSold   = inItems.filter { inItem -> inItem.disposal?.date?.isAfter(inToday) == false }	// biens vendus
	val vHeld   = inItems - vSold.toSet()												// biens détenus (ou achetés plus tard)
	SectionHeader(inTitle = if (inNoun == "bien") "Biens détenus" else "Objets détenus", inLinkLabel = "Ajouter", inOnLink = inOnAdd)
	Spacer(Modifier.height(8.dp))
	BorderedCard {
		if (vHeld.isEmpty())
		{
			EmptyState(inTitle = "Aucun $inNoun", inBody = "Ajoutez-en un avec son prix d’achat : payé depuis un compte, il en débite le prix ; déjà possédé, il n’en débite aucun.")
		}
		vHeld.forEachIndexed { inIndex, inItem ->
			if (inIndex > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = vColors.outlineVariant)
			Row(
				modifier          = Modifier.fillMaxWidth().clickable(onClickLabel = "Modifier") { inOnOpen(inItem) }.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(Modifier.weight(1f)) {
					Text(inItem.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
					Text(
						text     = inItem.acquisition.date.format(kShortDate) + " · " + (inItem.paidFrom?.let { inNames[it] ?: "compte supprimé" } ?: "déjà possédé"),
						style    = MaterialTheme.typography.bodyMedium,
						color    = vColors.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					AmountText(inCents = inItem.cost, inStyle = MaterialTheme.typography.bodyMedium)
					inNets[inItem.acquisition.id]?.let { inNet ->
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text("reste dû ", style = MaterialTheme.typography.bodySmall, color = vColors.onSurfaceVariant)
							AmountText(inCents = inNet.debt, inStyle = MaterialTheme.typography.bodySmall, inColor = vColors.onSurfaceVariant)
							Text(" · net ", style = MaterialTheme.typography.bodySmall, color = vColors.onSurfaceVariant)
							AmountText(inCents = inNet.net, inStyle = MaterialTheme.typography.bodySmall, inColor = vColors.onSurfaceVariant)
						}
					}
				}
				OutlinedButton(onClick = { inOnSell(inItem) }) { Text(if (inItem.disposal != null) "Vente prévue" else "Vendre") }
			}
		}
	}
	if (vSold.isNotEmpty())
	{
		Spacer(Modifier.height(20.dp))
		SectionHeader(inTitle = if (inNoun == "bien") "Biens vendus" else "Objets vendus")
		Spacer(Modifier.height(8.dp))
		BorderedCard {
			vSold.forEachIndexed { inIndex, inItem ->
				if (inIndex > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = vColors.outlineVariant)
				Row(
					modifier          = Modifier.fillMaxWidth().clickable(onClickLabel = "Modifier la vente") { inOnSell(inItem) }.padding(horizontal = 20.dp, vertical = 12.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Column(Modifier.weight(1f)) {
						Text(inItem.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
						Text("Vendu le ${inItem.disposal!!.date.format(kShortDate)}", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
					}
					Column(horizontalAlignment = Alignment.End) {
						AmountText(inCents = inItem.salePrice ?: 0L, inStyle = MaterialTheme.typography.bodyLarge)
						AmountText(inCents = inItem.gain ?: 0L, inStyle = MaterialTheme.typography.bodyMedium, inShowPlusSign = true, inTrend = true)
					}
				}
			}
		}
	}
}
