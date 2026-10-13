package fr.maubin.patrimoineplus.desktop.ui.panels

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.domain.calc.propertyItems
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.property.PropertyErrors
import fr.maubin.patrimoineplus.ui.property.PurchaseForm
import fr.maubin.patrimoineplus.ui.property.SaleForm
import fr.maubin.patrimoineplus.ui.property.buildPurchase
import fr.maubin.patrimoineplus.ui.property.buildSale
import fr.maubin.patrimoineplus.ui.property.checkPurchase
import fr.maubin.patrimoineplus.ui.property.checkSale
import fr.maubin.patrimoineplus.ui.property.propertyNoun
import fr.maubin.patrimoineplus.ui.property.saleGain

private const val kOwned = "__deja_possede__"										// choix « Déjà possédé » / « Aucun versement »

/*
	Achat d'un bien (compte Immobilier ou Collection) : nom, prix, date, et
	compte qui a payé, ou « Déjà possédé ». Supprimer un bien vendu supprime
	aussi sa vente.
	inController : contrôleur
	inStart      : saisie de départ
*/
@Composable
fun PurchasePanel(
	inController : DesktopController,
	inStart      : PurchaseForm
)
{
	val vSpace   by inController.workspace.collectAsState()							// données
	val vData    = vSpace.data															// données courantes
	var vForm    by remember(inStart) { mutableStateOf(inStart) }						// saisie
	var vErrors  by remember(inStart) { mutableStateOf(PropertyErrors()) }			// erreurs par champ
	var vGeneral by remember(inStart) { mutableStateOf<String?>(null) }				// refus général
	var vBadDay  by remember(inStart) { mutableStateOf(false) }						// date illisible
	var vDelete  by remember(inStart) { mutableStateOf(false) }						// confirmation de suppression
	val vAccount = vData.accounts.firstOrNull { it.id == vForm.accountId }			// compte de biens
	val vNoun    = vAccount?.let { propertyNoun(it.type) } ?: "bien"				// « bien » ou « objet »
	val vSale    = vData.transactions.firstOrNull { it.linkedId != null && it.linkedId == vForm.id }	// vente du bien

	/*
		Enregistre l'achat si la saisie est valide.
	*/
	fun save()
	{
		if (inController.panel.value == null) return						// panneau déjà fermé (double clic pendant sa disparition)
		vErrors = checkPurchase(vForm, vData.accounts)
		if (!vErrors.isEmpty || vBadDay) return
		finish(inController, inController.edit { inController.editor.saveTransaction(it, buildPurchase(vForm)) }) { vGeneral = it }
	}

	SidePanel(
		inTitle   = if (vForm.id == null) "Nouvel $vNoun" else "Modifier l’$vNoun",
		inOnClose = inController::closePanel,
		inOnEnter = ::save,
		inActions = {
			if (vForm.id != null) OutlinedButton(onClick = { vDelete = true }) { Text("Supprimer") }
			Button(onClick = ::save) { Text("Enregistrer") }
		}
	) {
		FormError(vGeneral)
		Picker("Compte", vData.accounts.filter { it.type.isProperty && (!it.archived || it.id == vForm.accountId) }.map { it.id to it.name }, vForm.accountId, { vForm = vForm.copy(accountId = it) }, vErrors.account.takeIf { vAccount == null }, inEnabled = vSale == null)
		FormField(vForm.name, { vForm = vForm.copy(name = it) }, "Nom", vErrors.name, inSupport = if (vNoun == "bien") "Par exemple « Appartement Lyon 3e »" else "Par exemple « Montre de 1968 »")
		FormField(vForm.price, { vForm = vForm.copy(price = it) }, "Prix d’achat (€)", vErrors.price, inSupport = if (vSale != null) "Vendu : annulez d’abord la vente pour changer le prix." else "Compté à ce prix dans le patrimoine", inNumeric = true)
		Picker(
			inLabel    = "Payé depuis",
			inOptions  = listOf(kOwned to "Déjà possédé (aucun compte débité)") + vData.accounts.filter { !it.type.isProperty && (!it.archived || it.id == vForm.paidFrom) }.map { it.id to it.name },
			inSelected = vForm.paidFrom ?: kOwned,
			inOnSelect = { vForm = vForm.copy(paidFrom = it.takeIf { inId -> inId != kOwned }) },
			inError    = vErrors.account.takeIf { vAccount != null }
		)
		DateField(vForm.date, { inDate -> vBadDay = inDate == null; inDate?.let { vForm = vForm.copy(date = it) } }, "Date d’achat")
		FormField(vForm.notes, { vForm = vForm.copy(notes = it) }, "Notes (facultatif)")
	}

	if (vDelete && vForm.id != null)
	{
		AlertDialog(
			onDismissRequest = { vDelete = false },
			title            = { Text("Supprimer « ${vForm.name.trim()} » ?") },
			text             = { Text(if (vSale != null) "Le $vNoun et sa vente seront supprimés ; le prix de vente sera retiré du compte qui l’a reçu." else "Le $vNoun sort du patrimoine ; le compte payeur est recrédité.") },
			confirmButton    = {
				TextButton(onClick = {
					vDelete = false
					when (val vResult = inController.edit { inController.editor.deleteTransactions(it, listOfNotNull(vForm.id, vSale?.id)) })
					{
						is EditResult.Done    -> inController.closePanel()
						is EditResult.Refused -> vGeneral = vResult.messages.joinToString(" ")
					}
				}) { Text("Supprimer") }
			},
			dismissButton    = { TextButton(onClick = { vDelete = false }) { Text("Annuler") } }
		)
	}
}

/*
	Vente d'un bien : prix, date, compte qui reçoit le prix (ou aucun, pour
	un don ou une perte) ; plus- ou moins-value affichée en direct.
	« Annuler la vente » supprime la vente : le bien redevient détenu.
	inController : contrôleur
	inStart      : saisie de départ
*/
@Composable
fun SalePanel(
	inController : DesktopController,
	inStart      : SaleForm
)
{
	val vSpace   by inController.workspace.collectAsState()							// données
	val vData    = vSpace.data															// données courantes
	val vItem    = remember(vData, inStart) { propertyItems(vData.transactions, vData.accounts).firstOrNull { it.acquisition.id == inStart.purchaseId } }	// bien vendu
	var vForm    by remember(inStart) { mutableStateOf(inStart) }						// saisie
	var vErrors  by remember(inStart) { mutableStateOf(PropertyErrors()) }			// erreurs par champ
	var vGeneral by remember(inStart) { mutableStateOf<String?>(null) }				// refus général
	var vBadDay  by remember(inStart) { mutableStateOf(false) }						// date illisible
	val vHidden  = LocalAmountsHidden.current											// mode discret
	if (vItem == null) return
	val vNoun    = vData.accounts.firstOrNull { it.id == vItem.accountId }?.let { propertyNoun(it.type) } ?: "bien"	// « bien » ou « objet »

	/*
		Enregistre la vente si la saisie est valide.
	*/
	fun save()
	{
		if (inController.panel.value == null) return						// panneau déjà fermé (double clic pendant sa disparition)
		vErrors = checkSale(vForm, vItem, vData.accounts)
		if (!vErrors.isEmpty || vBadDay) return
		finish(inController, inController.edit { inController.editor.saveTransaction(it, buildSale(vForm, vItem)) }) { vGeneral = it }
	}

	/*
		Annule la vente : le bien redevient détenu.
	*/
	fun cancelSale()
	{
		finish(inController, inController.edit { inController.editor.deleteTransactions(it, listOf(vForm.id!!)) }) { vGeneral = it }
	}

	SidePanel(
		inTitle   = if (vForm.id == null) "Vendre « ${vItem.name} »" else "Vente de « ${vItem.name} »",
		inOnClose = inController::closePanel,
		inOnEnter = ::save,
		inActions = {
			if (vForm.id != null) OutlinedButton(onClick = ::cancelSale) { Text("Annuler la vente") }
			Button(onClick = ::save) { Text("Enregistrer") }
		}
	) {
		FormError(vGeneral)
		Text("Acheté ${if (vHidden) kMaskedAmount else formatEuros(vItem.cost)} le ${fr.maubin.patrimoineplus.domain.format.formatFrenchDate(vItem.acquisition.date)}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		FormField(
			inValue    = vForm.price,
			inOnChange = { vForm = vForm.copy(price = it) },
			inLabel    = "Prix de vente (€)",
			inError    = vErrors.price,
			inSupport  = saleGain(vForm, vItem)?.let { inGain -> if (vHidden) "Plus-value masquée" else if (inGain >= 0) "Plus-value : ${formatEuros(inGain, true)}" else "Moins-value : ${formatEuros(inGain)}" } ?: "0 pour un don ou une perte",
			inNumeric  = true
		)
		Picker(
			inLabel    = "Prix versé sur",
			inOptions  = listOf(kOwned to "Aucun compte (don, perte)") + vData.accounts.filter { !it.type.isProperty && (!it.archived || it.id == vForm.soldTo) }.map { it.id to it.name },
			inSelected = vForm.soldTo ?: kOwned,
			inOnSelect = { vForm = vForm.copy(soldTo = it.takeIf { inId -> inId != kOwned }) },
			inError    = vErrors.account
		)
		DateField(vForm.date, { inDate -> vBadDay = inDate == null; inDate?.let { vForm = vForm.copy(date = it) } }, "Date de vente")
		vErrors.date?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		FormField(vForm.notes, { vForm = vForm.copy(notes = it) }, "Notes (facultatif)")
		if (vNoun == "bien") Text("Le bien sort du patrimoine à son prix d’achat ; le prix de vente entre sur le compte choisi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}
