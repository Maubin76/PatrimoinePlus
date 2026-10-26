package fr.maubin.patrimoineplus.desktop.ui.panels

import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import fr.maubin.patrimoineplus.ui.entry.withReferences
import fr.maubin.patrimoineplus.ui.entry.switchEntryMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.domain.calc.labelSuggestions
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.ui.entry.EntryData
import fr.maubin.patrimoineplus.ui.entry.EntryErrors
import fr.maubin.patrimoineplus.ui.entry.EntryForm
import fr.maubin.patrimoineplus.ui.entry.EntryMode
import fr.maubin.patrimoineplus.ui.entry.buildEntry
import fr.maubin.patrimoineplus.ui.entry.checkEntry
import fr.maubin.patrimoineplus.ui.entry.kNewAsset
import fr.maubin.patrimoineplus.ui.entry.proposedLabel
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.ui.planning.recurrenceFromEntry
import fr.maubin.patrimoineplus.domain.format.formatFrenchDate
import fr.maubin.patrimoineplus.ui.entry.entryModes
import fr.maubin.patrimoineplus.ui.entry.loanCharge
import fr.maubin.patrimoineplus.ui.entry.suggestLoanPayment
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

/*
	Saisie ou modification d'une opération dans le panneau latéral : dépense
	(ou remboursement), revenu, virement, ordre de bourse, mensualité de
	crédit (capital et intérêts en une opération). Mêmes contrôles et
	même construction que sur le téléphone (checkEntry, buildEntry) ; un nouvel
	actif est créé avec l'ordre, en une seule modification.
	inController : contrôleur
	inInitial    : formulaire de départ
*/
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OperationPanel(
	inController : DesktopController,
	inInitial    : EntryForm
)
{
	val vSpace  by inController.workspace.collectAsState()					// données
	val vData   = vSpace.data														// données du domaine
	var vForm   by remember(inInitial) { mutableStateOf(inInitial) }				// saisie
	var vErrors by remember(inInitial) { mutableStateOf(EntryErrors()) }		// erreurs affichées
	var vBadDay by remember(inInitial) { mutableStateOf(false) }				// date illisible
	var vDelete by remember(inInitial) { mutableStateOf(false) }				// suppression demandée
	var vRepeat by remember(inInitial) { mutableStateOf<Frequency?>(null) }	// répétition demandée (nouvelle opération)
	val vEntry  = EntryData(vData.accounts.filterNot { it.archived || it.type.isProperty }, vData.categories.filterNot { it.archived }.sortedBy { it.sortOrder }, vData.assets, vData.prices, vData.transactions, loans = vData.loans)
		.withReferences(vForm, vData.accounts, vData.categories)				// données de saisie (comptes archivés de l'opération compris)

	/*
		Enregistre : contrôles, puis création éventuelle de l'actif et
		enregistrement de l'opération en une seule modification.
	*/
	fun save()
	{
		if (inController.panel.value == null) return						// panneau déjà fermé (double clic pendant sa disparition)
		val vChecks = checkEntry(vForm, vEntry)									// erreurs de saisie
		if (!vChecks.isEmpty || vBadDay)
		{
			vErrors = if (vBadDay) vChecks.copy(general = kBadDate) else vChecks
			return
		}
		val vResult = inController.edit { inFile ->
			if (vForm.mode == EntryMode.TRADE && vForm.assetId == kNewAsset)
			{
				when (val vAsset = inController.editor.createAsset(inFile, vForm.newAsset, vForm.newIsin, null))
				{
					is EditResult.Done    -> inController.editor.saveTransaction(vAsset.file, buildEntry(vForm, vEntry, vAsset.id))
					is EditResult.Refused -> vAsset
				}
			}
			else
			{
				val vTx    = buildEntry(vForm, vEntry)										// opération construite
				val vSaved = inController.editor.saveTransaction(inFile, vTx)				// opération enregistrée
				val vRec   = vRepeat?.takeIf { vForm.id == null }?.let { recurrenceFromEntry(vForm, vTx.label, it) }	// récurrence demandée
				if (vSaved is EditResult.Done && vRec != null) inController.editor.saveRecurrence(vSaved.file, vRec).let { if (it is EditResult.Done) it.copy(id = vSaved.id) else it } else vSaved
			}
		}																			// résultat
		when (vResult)
		{
			is EditResult.Done    -> inController.closePanel()
			is EditResult.Refused -> vErrors = EntryErrors(general = vResult.messages.joinToString(" "))
		}
	}

	SidePanel(
		inTitle   = if (vForm.id == null) "Nouvelle opération" else "Modifier l’opération",
		inOnClose = inController::closePanel,
		inOnEnter = ::save,
		inActions = {
			if (vForm.id != null)
			{
				OutlinedButton(onClick = { vDelete = true }) { Text("Supprimer") }
			}
			Button(onClick = ::save) { Text("Enregistrer") }
		}
	) {
		FormError(vErrors.general)
		Choice(entryModes(vEntry, vForm).map { it to it.label }, vForm.mode, { vForm = switchEntryMode(vForm, it, vEntry) }, inEnabled = vForm.id == null)
		when (vForm.mode)
		{
			EntryMode.EXPENSE, EntryMode.INCOME ->
			{
				FormField(vForm.amount, { vForm = vForm.copy(amount = it.replace("-", "")) }, if (vForm.refund) "Montant remboursé (€)" else "Montant (€)", vErrors.amount, inNumeric = true)
				if (vForm.mode == EntryMode.EXPENSE)
				{
					Row(Modifier.fillMaxWidth().toggleable(value = vForm.refund, role = Role.Checkbox) { vForm = vForm.copy(refund = it) }, verticalAlignment = Alignment.CenterVertically) {
						Checkbox(checked = vForm.refund, onCheckedChange = null)
						Spacer(Modifier.width(10.dp))
						Text("Remboursement (montant reçu, déduit des dépenses de la catégorie)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
					}
				}
				Picker("Compte", vEntry.accounts.map { it.id to it.name }, vForm.accountId, { vForm = vForm.copy(accountId = it) }, vErrors.account)
				FormField(vForm.label, { vForm = vForm.copy(label = it) }, "Libellé", vErrors.label)
				val vHints = if (vForm.id == null && vForm.label.isNotBlank()) labelSuggestions(vEntry.transactions, vForm.label).take(6) else emptyList()	// suggestions
				if (vHints.isNotEmpty())
				{
					FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						vHints.forEach { inHint ->
							SuggestionChip(onClick = {
								val vCategory = vEntry.categories.firstOrNull { it.id == inHint.categoryId }	// catégorie de la suggestion
								vForm = vForm.copy(
									label      = inHint.label,
									categoryId = vCategory?.id ?: vForm.categoryId,
									mode       = when (vCategory?.kind) { CategoryKind.EXPENSE -> EntryMode.EXPENSE; CategoryKind.INCOME -> EntryMode.INCOME; null -> vForm.mode }
								)
							}, label = { Text(inHint.label) })
						}
					}
				}
				val vKind = if (vForm.mode == EntryMode.EXPENSE) CategoryKind.EXPENSE else CategoryKind.INCOME	// nature des catégories
				Picker("Catégorie", vEntry.categories.filter { it.kind == vKind }.map { it.id to it.name }, vForm.categoryId, { vForm = vForm.copy(categoryId = it) }, vErrors.category)
			}
			EntryMode.TRANSFER                  ->
			{
				FormField(vForm.amount, { vForm = vForm.copy(amount = it.replace("-", "")) }, "Montant (€)", vErrors.amount, inNumeric = true)
				Picker("Depuis", vEntry.accounts.map { it.id to it.name }, vForm.accountId, { vForm = vForm.copy(accountId = it) }, vErrors.account)
				Picker("Vers", vEntry.accounts.map { it.id to it.name }, vForm.toAccountId, { vForm = vForm.copy(toAccountId = it) }, vErrors.target)
				FormField(vForm.label, { vForm = vForm.copy(label = it) }, "Libellé (facultatif)", inSupport = "Vide : « ${proposedLabel(vForm, vEntry)} »")
			}
			EntryMode.TRADE                     ->
			{
				Picker("Compte Bourse", vEntry.accounts.filter { it.type == AccountType.BROKERAGE }.map { it.id to it.name }, vForm.accountId, { vForm = vForm.copy(accountId = it) }, vErrors.account)
				Choice(listOf(TradeSide.BUY to "Achat", TradeSide.SELL to "Vente"), vForm.side, { vForm = vForm.copy(side = it) })
				Picker("Actif", vEntry.assets.filterNot { it.archived }.map { it.id to it.name } + (kNewAsset to "Nouvel actif…"), vForm.assetId, { vForm = vForm.copy(assetId = it) }, vErrors.asset.takeIf { vForm.assetId != kNewAsset })
				if (vForm.assetId == kNewAsset)
				{
					FormField(vForm.newAsset, { vForm = vForm.copy(newAsset = it) }, "Nom du nouvel actif", vErrors.asset)
					FormField(vForm.newIsin, { vForm = vForm.copy(newIsin = it) }, "ISIN (facultatif)", inSupport = "Le téléphone en déduira les cours automatiques.")
				}
				FormField(vForm.quantity, { vForm = vForm.copy(quantity = it) }, "Quantité", vErrors.quantity, inNumeric = true)
				FormField(vForm.price, { vForm = vForm.copy(price = it) }, "Cours (€)", vErrors.price, inNumeric = true)
				FormField(vForm.fees, { vForm = vForm.copy(fees = it) }, "Frais (€, facultatif)", vErrors.fees, inNumeric = true)
				FormField(vForm.label, { vForm = vForm.copy(label = it) }, "Libellé (facultatif)", inSupport = "Vide : « ${proposedLabel(vForm, vEntry)} »")
			}
			EntryMode.LOAN                      ->
			{
				val vCharge = loanCharge(vForm)																// intérêts et assurance
				Picker("Crédit remboursé", vEntry.accounts.filter { it.type == AccountType.LOAN }.map { it.id to it.name }, vForm.toAccountId, { vForm = suggestLoanPayment(vForm.copy(toAccountId = it), vEntry) }, vErrors.target)
				Picker("Payé depuis", vEntry.accounts.filter { it.type != AccountType.LOAN }.map { it.id to it.name }, vForm.accountId, { vForm = vForm.copy(accountId = it) }, vErrors.account)
				FormField(vForm.amount, { vForm = vForm.copy(amount = it.replace("-", "")) }, "Montant payé (€)", vErrors.amount, inNumeric = true, inSupport = "Mensualité complète : capital, intérêts et assurance.")
				FormField(vForm.capital, { vForm = vForm.copy(capital = it.replace("-", "")) }, "Dont capital remboursé (€)", vErrors.capital, inNumeric = true,
					inSupport = when
					{
						vForm.capital.isBlank()      -> "Vide : tout en capital (remboursement anticipé)."
						vCharge == null || vCharge < 0 -> "Le reste du montant est compté en intérêts et assurance."
						LocalAmountsHidden.current   -> "Intérêts et assurance : $kMaskedAmount (dépense, le reste fait baisser le capital dû)."
						else                         -> "Intérêts et assurance : ${formatEuros(vCharge)} (dépense, le reste fait baisser le capital dû)."
					})
				Picker("Catégorie des intérêts", vEntry.categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id to it.name }, vForm.categoryId, { vForm = vForm.copy(categoryId = it) }, vErrors.category)
				FormField(vForm.label, { vForm = vForm.copy(label = it) }, "Libellé (facultatif)", inSupport = "Vide : « ${proposedLabel(vForm, vEntry)} »")
			}
		}
		DateField(vForm.date, { inDate -> vBadDay = inDate == null; inDate?.let { vForm = if (vForm.mode == EntryMode.LOAN && vForm.id == null && vForm.date.month != it.month) suggestLoanPayment(vForm.copy(date = it), vEntry) else vForm.copy(date = it) } }, "Date")
		FormField(vForm.notes, { vForm = vForm.copy(notes = it) }, "Notes (facultatif)")
		if (vForm.id == null && vForm.mode != EntryMode.TRADE && vForm.mode != EntryMode.LOAN)
		{
			Picker("Répéter", listOf<Pair<Frequency?, String>>(null to "Non") + Frequency.entries.map { it to it.label }, vRepeat, { vRepeat = it })
			vRepeat?.let { Text("Créée automatiquement ${it.label.lowercase()}, à partir du ${formatFrenchDate(vForm.date.plusMonths(it.months.toLong()))}. Modifiable dans Récurrences.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
		}
	}

	if (vDelete && vForm.id != null)
	{
		val vTransfer = vForm.mode == EntryMode.TRANSFER							// virement
		AlertDialog(
			onDismissRequest = { vDelete = false },
			title            = { Text("Supprimer l’opération ?") },
			text             = { Text(if (vTransfer) "Le virement sera supprimé sur les deux comptes." else "Les soldes seront recalculés.") },
			confirmButton    = {
				TextButton(onClick = {
					vDelete = false
					when (val vResult = inController.edit { inController.editor.deleteTransactions(it, listOf(vForm.id!!)) })
					{
						is EditResult.Done    -> inController.closePanel()
						is EditResult.Refused -> vErrors = EntryErrors(general = vResult.messages.joinToString(" "))
					}
				}) { Text("Supprimer") }
			},
			dismissButton    = { TextButton(onClick = { vDelete = false }) { Text("Annuler") } }
		)
	}
}
