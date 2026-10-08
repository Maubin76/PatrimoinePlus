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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.calc.Position
import fr.maubin.patrimoineplus.domain.calc.averagePrice
import fr.maubin.patrimoineplus.domain.calc.roundDiv
import fr.maubin.patrimoineplus.domain.format.editableScaled
import fr.maubin.patrimoineplus.domain.format.formatPrice
import fr.maubin.patrimoineplus.domain.format.formatScaled
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.format.parseScaled
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.ui.components.AmountInput
import fr.maubin.patrimoineplus.ui.components.AmountText
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
			StatRow("Plus-value latente", vLatent, true, if (inPortfolio.costCents > 0) "${if (vRate >= 0) "+" else "-"}${abs(vRate) / 10},${abs(vRate) % 10} %" else null)
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
		AmountText(inCents = inCents, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = inSigned)
	}
}

/*
	Positions d'un compte Bourse : actif, quantité, valeur ; PRU, cours et
	plus-value latente dessous ; puis les actions (cours, positions initiales).
	inPortfolio : portefeuille valorisé
	inOpenings  : positions initiales
	inOnPrices  : ouvre la mise à jour des cours
	inOnAddOpen : ouvre l'ajout d'une position initiale
	inOnRemove  : retire une position initiale
*/
@Composable
fun PositionsSection(
	inPortfolio : PortfolioView,
	inOpenings  : List<OpeningPositionItem>,
	inOnPrices  : () -> Unit,
	inOnAddOpen : () -> Unit,
	inOnRemove  : (OpeningPositionItem) -> Unit
)
{
	val vPositions = inPortfolio.valuation.positions							// positions
	SectionHeader(inTitle = "Positions", inLinkLabel = if (vPositions.isEmpty()) null else "Mettre à jour les cours", inOnLink = inOnPrices)
	Spacer(Modifier.height(8.dp))
	BorderedCard {
		if (vPositions.isEmpty())
		{
			EmptyState(inTitle = "Aucune position", inBody = "Saisissez un ordre d’achat avec « + », ou ajoutez vos positions initiales ci-dessous.")
		}
		vPositions.forEachIndexed { inIndex, inPosition ->
			if (inIndex > 0)
			{
				HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
			}
			PositionRow(inPosition, inPortfolio.names[inPosition.assetId] ?: "Actif supprimé")
		}
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
	TextButton(onClick = inOnAddOpen, modifier = Modifier.padding(top = 4.dp)) { Text("Ajouter une position initiale") }
}

/*
	Ligne d'une position.
	inPosition : position valorisée
	inName     : nom de l'actif
*/
@Composable
private fun PositionRow(
	inPosition : Position,
	inName     : String
)
{
	val vColors = MaterialTheme.colorScheme									// palette courante
	val vHidden = LocalAmountsHidden.current									// mode discret actif
	val vAvg    = averagePrice(inPosition.costCents, inPosition.quantity)	// PRU
	Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(inName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
			Spacer(Modifier.width(12.dp))
			AmountText(inCents = inPosition.valueCents, inStyle = MaterialTheme.typography.titleMedium)
		}
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(
				text     = buildString {
					append("${formatScaled(inPosition.quantity)} parts")
					if (!vHidden)
					{
						append(" · PRU ${formatPrice(roundDiv(vAvg, 100) * 100)}")
						append(if (inPosition.isUnpriced) " · sans cours" else " · ${formatPrice(inPosition.unitPrice)} au ${inPosition.priceDate!!.format(kShortDate)}")
					}
				},
				style    = MaterialTheme.typography.bodyMedium,
				color    = vColors.onSurfaceVariant,
				modifier = Modifier.weight(1f)
			)
			Spacer(Modifier.width(12.dp))
			AmountText(inCents = inPosition.unrealizedCents, inStyle = MaterialTheme.typography.bodyMedium, inColor = vColors.onSurfaceVariant, inShowPlusSign = true)
		}
	}
}

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
	FormSheet(inTitle = "Nouveau relevé", inOnDismiss = inOnDismiss) {
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
	FormSheet(inTitle = "Mettre à jour les cours", inOnDismiss = inOnDismiss) {
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
	FormSheet(inTitle = "Position initiale", inOnDismiss = inOnDismiss) {
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
