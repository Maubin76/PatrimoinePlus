package fr.maubin.patrimoineplus.ui.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.entry.switchEntryMode
import fr.maubin.patrimoineplus.domain.calc.LabelSuggestion
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.heldQuantity
import fr.maubin.patrimoineplus.domain.calc.tradeCashCents
import fr.maubin.patrimoineplus.domain.format.editableScaled
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.formatScaled
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.format.parseScaled
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.ui.components.AmountInput
import fr.maubin.patrimoineplus.ui.components.ConfirmDialog
import fr.maubin.patrimoineplus.ui.components.DateInput
import fr.maubin.patrimoineplus.ui.components.FormSheet
import fr.maubin.patrimoineplus.ui.components.SegmentedChoice
import fr.maubin.patrimoineplus.ui.components.TextInput
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import kotlin.math.abs

import fr.maubin.patrimoineplus.ui.components.Picker

import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.accountLabel

/*
	Feuille de saisie d'une opération : dépense, revenu, virement entre deux
	de mes comptes, ordre de bourse (si un compte Bourse existe), ou
	mensualité de crédit (si un compte Crédit existe) ; en
	modification, suppression après confirmation.
	inInitial      : saisie de départ
	inData         : comptes, catégories, actifs, cours, opérations
	inSuggestions  : suggestions de libellés pour un début de saisie
	inOnSave       : enregistre la saisie et renvoie les erreurs par rappel
	inOnDelete     : supprime l'opération modifiée
	inOnDismiss    : fermeture
	inOnRepeat    : répétition choisie pour une nouvelle opération ; null : pas de répétition proposée
*/
@Composable
fun TransactionSheet(
	inInitial     : EntryForm,
	inData        : EntryData,
	inSuggestions : (String) -> List<LabelSuggestion>,
	inOnSave      : (EntryForm, (EntryErrors) -> Unit) -> Unit,
	inOnDelete    : () -> Unit,
	inOnDismiss   : () -> Unit,
	inOnRepeat    : ((Frequency?) -> Unit)? = null
)
{
	var vRepeat  by remember { mutableStateOf<Frequency?>(null) }				// répétition choisie
	var vForm    by remember { mutableStateOf(inInitial) }						// saisie en cours
	var vErrors  by remember { mutableStateOf(EntryErrors()) }				// erreurs affichées
	var vConfirm by remember { mutableStateOf(false) }						// suppression à confirmer
	val vHasBourse = inData.accounts.any { it.type == AccountType.BROKERAGE }	// un compte Bourse existe
	val vModes     = entryModes(inData, vForm).filter { it != EntryMode.TRADE || vHasBourse || vForm.mode == EntryMode.TRADE }	// modes proposés

	FormSheet(inTitle = if (vForm.id == null) "Nouvelle opération" else "Modifier l’opération", inOnDismiss = inOnDismiss) {
		SegmentedChoice(
			inOptions  = vModes,
			inSelected = vForm.mode,
			inLabel    = { it.label },
			inOnSelect = { inMode -> vForm = switchEntryMode(vForm, inMode, inData) },
			inEnabled  = vForm.id == null
		)
		when (vForm.mode)
		{
			EntryMode.EXPENSE, EntryMode.INCOME -> StandardFields(vForm, vErrors, inData, inSuggestions) { vForm = it }
			EntryMode.TRANSFER                  -> TransferFields(vForm, vErrors, inData) { vForm = it }
			EntryMode.TRADE                     -> TradeFields(vForm, vErrors, inData) { vForm = it }
			EntryMode.LOAN                      -> LoanPaymentFields(vForm, vErrors, inData) { vForm = it }
		}
		DateInput(vForm.date, { vForm = if (vForm.mode == EntryMode.LOAN && vForm.id == null && vForm.date.month != it.month) suggestLoanPayment(vForm.copy(date = it), inData) else vForm.copy(date = it) }, "Date")
		TextInput(vForm.notes, { vForm = vForm.copy(notes = it) }, "Notes (facultatif)")
		if (inOnRepeat != null && vForm.id == null && vForm.mode != EntryMode.TRADE && vForm.mode != EntryMode.LOAN)
		{
			Picker("Répéter", listOf<Pair<String?, String>>(null to "Non") + Frequency.entries.map { it.name to it.label }, vRepeat?.name, null) { inName ->
				vRepeat = inName?.let(Frequency::valueOf)
				inOnRepeat(vRepeat)
			}
		}
		vErrors.general?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		Button(
			onClick        = { inOnSave(vForm) { vErrors = it } },
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) {
			Text("Enregistrer")
		}
		if (vForm.id != null)
		{
			TextButton(onClick = { vConfirm = true }, modifier = Modifier.fillMaxWidth()) {
				Text("Supprimer l’opération", color = MaterialTheme.colorScheme.error)
			}
		}
	}

	if (vConfirm)
	{
		ConfirmDialog(
			inTitle     = "Supprimer l’opération ?",
			inText      = if (vForm.mode == EntryMode.TRANSFER || vForm.mode == EntryMode.LOAN) "L’opération sera supprimée sur les deux comptes ; leurs soldes seront recalculés."
				else "« ${vForm.label.ifBlank { proposedLabel(vForm, inData) }} » sera supprimée ; le solde sera recalculé.",
			inAction    = "Supprimer",
			inOnConfirm = {
				vConfirm = false
				inOnDelete()
			},
			inOnDismiss = { vConfirm = false }
		)
	}
}

/*
	Champs d'une dépense ou d'un revenu : montant, compte, libellé avec
	suggestions, catégorie ; pour un revenu sur un compte Bourse, actif du
	dividende (facultatif).
	inForm        : saisie
	inErrors      : erreurs
	inData        : données
	inSuggestions : suggestions de libellés
	inOnChange    : saisie modifiée
*/
@Composable
private fun StandardFields(
	inForm        : EntryForm,
	inErrors      : EntryErrors,
	inData        : EntryData,
	inSuggestions : (String) -> List<LabelSuggestion>,
	inOnChange    : (EntryForm) -> Unit
)
{
	val vKind   = if (inForm.mode == EntryMode.EXPENSE) CategoryKind.EXPENSE else CategoryKind.INCOME	// nature
	val vByKind = inData.categories.filter { it.kind == vKind }					// catégories proposées
	val vHints  = if (inForm.id == null) inSuggestions(inForm.label) else emptyList()	// suggestions affichées
	val vBourse = inData.accounts.firstOrNull { it.id == inForm.accountId }?.type == AccountType.BROKERAGE	// compte Bourse
	AmountInput(inForm.amount, { inOnChange(inForm.copy(amount = it.replace("-", ""))) }, if (inForm.refund) "Montant remboursé" else "Montant", inErrors.amount)
	if (inForm.mode == EntryMode.EXPENSE)
	{
		Row(
			modifier          = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = inForm.refund, role = Role.Checkbox) { inOnChange(inForm.copy(refund = it)) }.padding(vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Checkbox(checked = inForm.refund, onCheckedChange = null)
			Spacer(Modifier.width(12.dp))
			Text("Remboursement (montant reçu, déduit des dépenses de la catégorie)", style = MaterialTheme.typography.bodyMedium)
		}
	}
	AccountPicker("Compte", inData.accounts, inForm.accountId, inErrors.account) { inOnChange(inForm.copy(accountId = it)) }
	TextInput(inForm.label, { inOnChange(inForm.copy(label = it)) }, "Libellé", inErrors.label)
	if (vHints.isNotEmpty())
	{
		FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			vHints.forEach { inHint ->
				Pill(inText = inHint.label, inSelected = false) {
					val vCategory = inData.categories.firstOrNull { it.id == inHint.categoryId }	// catégorie de la suggestion
					inOnChange(inForm.copy(
						label      = inHint.label,
						categoryId = vCategory?.id ?: inForm.categoryId,
						mode       = when (vCategory?.kind)
						{
							CategoryKind.EXPENSE -> EntryMode.EXPENSE
							CategoryKind.INCOME  -> EntryMode.INCOME
							null                 -> inForm.mode
						}
					))
				}
			}
		}
	}
	Text("Catégorie", style = MaterialTheme.typography.titleSmall)
	if (vByKind.isEmpty())
	{
		Text(
			text  = "Aucune catégorie de ${inForm.mode.label.lowercase()} : créez-en une dans l’onglet Catégories.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
	FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		vByKind.forEach { inCategory ->
			Pill(inText = inCategory.name, inSelected = inCategory.id == inForm.categoryId, inIcon = inCategory.icon) {
				inOnChange(inForm.copy(categoryId = inCategory.id))
			}
		}
	}
	inErrors.category?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
	if (inForm.mode == EntryMode.INCOME && vBourse && inData.assets.isNotEmpty())
	{
		Picker(
			inLabel    = "Actif du dividende (facultatif)",
			inOptions  = listOf<Pair<String?, String>>(null to "Aucun") + inData.assets.filterNot { it.archived }.map { it.id to it.name },
			inSelected = inForm.assetId,
			inError    = null
		) { inOnChange(inForm.copy(assetId = it)) }
	}
}

/*
	Champs d'un virement : montant, compte de départ, compte d'arrivée, libellé
	(proposé s'il est laissé vide).
	inForm     : saisie
	inErrors   : erreurs
	inData     : données
	inOnChange : saisie modifiée
*/
@Composable
private fun TransferFields(
	inForm     : EntryForm,
	inErrors   : EntryErrors,
	inData     : EntryData,
	inOnChange : (EntryForm) -> Unit
)
{
	AmountInput(inForm.amount, { inOnChange(inForm.copy(amount = it.replace("-", ""))) }, "Montant", inErrors.amount)
	AccountPicker("De", inData.accounts, inForm.accountId, inErrors.account) { inOnChange(inForm.copy(accountId = it)) }
	AccountPicker("Vers", inData.accounts.filter { it.id != inForm.accountId }, inForm.toAccountId, inErrors.target) { inOnChange(inForm.copy(toAccountId = it)) }
	TextInput(inForm.label, { inOnChange(inForm.copy(label = it)) }, "Libellé", inErrors.label, inSupport = "Vide : « ${proposedLabel(inForm, inData)} »")
}

/*
	Champs d'une mensualité de crédit : crédit remboursé (la mensualité du
	mois est proposée), compte payeur, total payé, capital remboursé (le
	reste, intérêts et assurance, est une dépense), catégorie des intérêts,
	libellé (proposé s'il est laissé vide).
	inForm     : saisie
	inErrors   : erreurs
	inData     : données
	inOnChange : saisie modifiée
*/
@Composable
private fun LoanPaymentFields(
	inForm     : EntryForm,
	inErrors   : EntryErrors,
	inData     : EntryData,
	inOnChange : (EntryForm) -> Unit
)
{
	val vCharge = loanCharge(inForm)														// intérêts et assurance
	AccountPicker("Crédit remboursé", inData.accounts.filter { it.type == AccountType.LOAN }, inForm.toAccountId, inErrors.target) { inOnChange(suggestLoanPayment(inForm.copy(toAccountId = it), inData)) }
	AccountPicker("Payé depuis", inData.accounts.filter { it.type != AccountType.LOAN }, inForm.accountId, inErrors.account) { inOnChange(inForm.copy(accountId = it)) }
	AmountInput(inForm.amount, { inOnChange(inForm.copy(amount = it.replace("-", ""))) }, "Montant payé", inErrors.amount, inSupport = "Mensualité complète : capital, intérêts et assurance.")
	AmountInput(inForm.capital, { inOnChange(inForm.copy(capital = it.replace("-", ""))) }, "Dont capital remboursé", inErrors.capital,
		inSupport = when
		{
			inForm.capital.isBlank()       -> "Vide : tout en capital (remboursement anticipé)."
			vCharge == null || vCharge < 0 -> "Le reste du montant est compté en intérêts et assurance."
			LocalAmountsHidden.current     -> "Intérêts et assurance : $kMaskedAmount"
			else                           -> "Intérêts et assurance : ${formatEuros(vCharge)}"
		})
	Picker("Catégorie des intérêts", inData.categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id as String? to it.name }, inForm.categoryId, inErrors.category) { inOnChange(inForm.copy(categoryId = it)) }
	TextInput(inForm.label, { inOnChange(inForm.copy(label = it)) }, "Libellé", inErrors.label, inSupport = "Vide : « ${proposedLabel(inForm, inData)} »")
}

/*
	Champs d'un ordre de bourse : compte Bourse, achat ou vente, actif
	(existant ou nouveau), quantité, cours, frais ; montant calculé en direct
	et quantité détenue rappelée pour une vente.
	inForm     : saisie
	inErrors   : erreurs
	inData     : données
	inOnChange : saisie modifiée
*/
@Composable
private fun TradeFields(
	inForm     : EntryForm,
	inErrors   : EntryErrors,
	inData     : EntryData,
	inOnChange : (EntryForm) -> Unit
)
{
	val vColors   = MaterialTheme.colorScheme									// palette courante
	val vQuantity = parseScaled(inForm.quantity)								// quantité lue
	val vPrice    = parseScaled(inForm.price)									// cours lu
	val vFees     = if (inForm.fees.isBlank()) 0L else parseEuros(inForm.fees)	// frais lus
	AccountPicker("Compte Bourse", inData.accounts.filter { it.type == AccountType.BROKERAGE }, inForm.accountId, inErrors.account) { inOnChange(inForm.copy(accountId = it)) }
	SegmentedChoice(
		inOptions  = TradeSide.entries,
		inSelected = inForm.side,
		inLabel    = { if (it == TradeSide.BUY) "Achat" else "Vente" },
		inOnSelect = { inOnChange(inForm.copy(side = it)) }
	)
	Picker(
		inLabel    = "Actif",
		inOptions  = inData.assets.filterNot { it.archived }.map { it.id as String? to it.name } + (kNewAsset as String? to "Nouvel actif…"),
		inSelected = inForm.assetId,
		inError    = inErrors.asset.takeIf { inForm.assetId != kNewAsset }
	) { inId ->
		val vLast = inData.prices.filter { it.assetId == inId }.maxByOrNull { it.date }	// dernier cours connu
		inOnChange(inForm.copy(assetId = inId, price = if (inForm.price.isBlank() && vLast != null) editableScaled(vLast.unitPrice) else inForm.price))
	}
	if (inForm.assetId == kNewAsset)
	{
		TextInput(inForm.newAsset, { inOnChange(inForm.copy(newAsset = it)) }, "Nom du nouvel actif", inErrors.asset)
		TextInput(inForm.newIsin, { inOnChange(inForm.copy(newIsin = it)) }, "ISIN (facultatif)", null, "Sur vos relevés, ex. LU1681043599 : les cours seront récupérés automatiquement.")
	}
	val vSellAccount = inForm.accountId											// compte de la vente
	val vSellAsset   = inForm.assetId											// actif vendu
	if (inForm.side == TradeSide.SELL && vSellAccount != null && vSellAsset != null && vSellAsset != kNewAsset)
	{
		val vOthers = inData.transactions.filter { it.id != inForm.id }		// autres opérations
		val vHeld   = heldQuantity(Ledger(vOthers), vSellAccount, vSellAsset, inForm.date)	// quantité détenue
		Text("Détenu à cette date : ${formatScaled(vHeld)}", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
	}
	DecimalField(inForm.quantity, { inOnChange(inForm.copy(quantity = it)) }, "Quantité", null, inErrors.quantity)
	DecimalField(inForm.price, { inOnChange(inForm.copy(price = it)) }, "Cours", "€", inErrors.price)
	AmountInput(inForm.fees, { inOnChange(inForm.copy(fees = it.replace("-", ""))) }, "Frais", inErrors.fees, inSupport = "Vide = sans frais")
	if (vQuantity != null && vPrice != null && vFees != null)
	{
		val vCash = tradeCashCents(inForm.side, vQuantity, vPrice, vFees)		// montant de l'ordre
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			Text(if (inForm.side == TradeSide.BUY) "Montant débité" else "Montant crédité", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
			Text(formatEuros(abs(vCash)), style = MaterialTheme.typography.titleMedium)
		}
	}
	TextInput(inForm.label, { inOnChange(inForm.copy(label = it)) }, "Libellé", inErrors.label, inSupport = "Vide : « ${proposedLabel(inForm, inData)} »")
}

/*
	Champ décimal (quantité, cours) : jusqu'à quatre décimales.
	inValue    : texte saisi
	inOnChange : modification
	inLabel    : libellé
	inSuffix   : suffixe (« € ») ; null sans suffixe
	inError    : message d'erreur ; null sans erreur
*/
@Composable
private fun DecimalField(
	inValue    : String,
	inOnChange : (String) -> Unit,
	inLabel    : String,
	inSuffix   : String?,
	inError    : String?
)
{
	OutlinedTextField(
		value           = inValue,
		onValueChange   = { inText -> inOnChange(inText.filter { it.isDigit() || it in ",. " }) },
		label           = { Text(inLabel) },
		suffix          = inSuffix?.let { { Text(it) } },
		singleLine      = true,
		isError         = inError != null,
		supportingText  = inError?.let { { Text(it) } },
		keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
		modifier        = Modifier.fillMaxWidth()
	)
}

/*
	Choix d'un compte dans une liste déroulante.
	inLabel    : libellé du champ
	inAccounts : comptes proposés
	inSelected : compte choisi
	inError    : message d'erreur ; null sans erreur
	inOnSelect : choix d'un compte
*/
@Composable
private fun AccountPicker(
	inLabel    : String,
	inAccounts : List<Account>,
	inSelected : String?,
	inError    : String?,
	inOnSelect : (String) -> Unit
)
{
	Picker(inLabel, inAccounts.map { it.id as String? to "${accountLabel(it)}  ·  ${it.type.label}" }, inSelected, inError) { it?.let(inOnSelect) }
}

/*
	Pastille de choix (catégorie, suggestion) : bordée au repos, à l'encre
	quand elle est choisie ; icône facultative.
	inText     : texte
	inSelected : choisie
	inIcon     : clé d'icône de catégorie ; null sans icône
	inOnClick  : choix
*/
@Composable
private fun Pill(
	inText     : String,
	inSelected : Boolean,
	inIcon     : String? = null,
	inOnClick  : () -> Unit
)
{
	val vColors  = MaterialTheme.colorScheme									// palette courante
	val vContent = if (inSelected) vColors.surface else vColors.onSurface		// couleur du contenu
	Surface(
		shape    = CircleShape,
		color    = if (inSelected) vColors.onSurface else vColors.surface,
		border   = BorderStroke(1.dp, if (inSelected) vColors.onSurface else vColors.outlineVariant),
		modifier = Modifier.semantics { selected = inSelected }
	) {
		Row(
			modifier          = Modifier
				.heightIn(min = 40.dp)
				.clickable(role = Role.Button, onClick = inOnClick)
				.padding(horizontal = 14.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			if (inIcon != null)
			{
				Icon(CategoryIcons.of(inIcon), contentDescription = null, tint = vContent, modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(8.dp))
			}
			Text(inText, style = MaterialTheme.typography.labelLarge, color = vContent)
		}
	}
}
