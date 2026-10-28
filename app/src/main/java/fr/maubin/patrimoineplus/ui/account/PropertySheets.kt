package fr.maubin.patrimoineplus.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.calc.PropertyItem
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.ui.components.AmountInput
import fr.maubin.patrimoineplus.ui.components.ConfirmDialog
import fr.maubin.patrimoineplus.ui.components.DateInput
import fr.maubin.patrimoineplus.ui.components.FormSheet
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.Picker
import fr.maubin.patrimoineplus.ui.components.TextInput
import fr.maubin.patrimoineplus.ui.property.PropertyErrors
import fr.maubin.patrimoineplus.ui.property.PurchaseForm
import fr.maubin.patrimoineplus.ui.property.SaleForm
import fr.maubin.patrimoineplus.ui.property.checkPurchase
import fr.maubin.patrimoineplus.ui.property.checkSale
import fr.maubin.patrimoineplus.ui.property.propertyNoun
import fr.maubin.patrimoineplus.ui.property.saleGain
import fr.maubin.patrimoineplus.ui.components.accountLabel

/*
	Feuille d'achat d'un bien (compte Immobilier ou Collection) : nom, prix,
	compte qui a payé ou « Déjà possédé », date, notes ; en modification,
	suppression (avec sa vente s'il est vendu).
	inStart     : saisie de départ
	inAccounts  : tous les comptes
	inSold      : vrai si le bien est vendu (compte et prix figés)
	inOnSave    : enregistre la saisie vérifiée ; reçoit le refus éventuel par rappel
	inOnDelete  : supprime le bien
	inOnDismiss : fermeture
*/
@Composable
fun PurchaseSheet(
	inStart     : PurchaseForm,
	inAccounts  : List<Account>,
	inSold      : Boolean,
	inOnSave    : (PurchaseForm, (String) -> Unit) -> Unit,
	inOnDelete  : () -> Unit,
	inOnDismiss : () -> Unit
)
{
	var vForm    by remember { mutableStateOf(inStart) }								// saisie en cours
	var vErrors  by remember { mutableStateOf(PropertyErrors()) }						// erreurs par champ
	var vGeneral by remember { mutableStateOf<String?>(null) }							// refus de l'enregistrement
	var vDelete  by remember { mutableStateOf(false) }									// confirmation de suppression
	val vNoun    = inAccounts.firstOrNull { it.id == vForm.accountId }?.let { propertyNoun(it.type) } ?: "bien"	// « bien » ou « objet »
	FormSheet(inTitle = if (vForm.id == null) "Nouvel $vNoun" else "Modifier l’$vNoun", inOnDismiss = inOnDismiss) {
		TextInput(vForm.name, { vForm = vForm.copy(name = it) }, "Nom", vErrors.name, if (vNoun == "bien") "Par exemple « Appartement Lyon 3e »" else "Par exemple « Montre de 1968 »")
		AmountInput(vForm.price, { vForm = vForm.copy(price = it) }, "Prix d’achat", vErrors.price, if (inSold) "Vendu : annulez d’abord la vente pour changer le prix." else "Compté à ce prix dans le patrimoine.")
		Picker(
			inLabel    = "Payé depuis",
			inOptions  = listOf<Pair<String?, String>>(null to "Déjà possédé (aucun compte débité)") + inAccounts.filter { !it.type.isProperty && (!it.archived || it.id == vForm.paidFrom) }.map { it.id to accountLabel(it) },
			inSelected = vForm.paidFrom,
			inError    = vErrors.account
		) { vForm = vForm.copy(paidFrom = it) }
		DateInput(vForm.date, { vForm = vForm.copy(date = it) }, "Date d’achat")
		TextInput(vForm.notes, { vForm = vForm.copy(notes = it) }, "Notes (facultatif)")
		vGeneral?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		Button(
			onClick        = {
				vErrors = checkPurchase(vForm, inAccounts)
				if (vErrors.isEmpty) inOnSave(vForm) { vGeneral = it }
			},
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) {
			Text("Enregistrer")
		}
		if (vForm.id != null)
		{
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
				TextButton(onClick = { vDelete = true }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
			}
		}
	}
	if (vDelete)
	{
		ConfirmDialog(
			inTitle     = "Supprimer « ${vForm.name.trim()} » ?",
			inText      = if (inSold) "Le $vNoun et sa vente seront supprimés ; le prix de vente sera retiré du compte qui l’a reçu." else "Le $vNoun sort du patrimoine ; le compte payeur est recrédité.",
			inAction    = "Supprimer",
			inOnConfirm = {
				vDelete = false
				inOnDelete()
			},
			inOnDismiss = { vDelete = false }
		)
	}
}

/*
	Feuille de vente d'un bien : prix (plus- ou moins-value en direct),
	compte qui reçoit le prix ou aucun (don, perte), date, notes ; en
	modification, annulation de la vente.
	inStart        : saisie de départ
	inItem         : bien vendu
	inAccounts     : tous les comptes
	inOnSave       : enregistre la saisie vérifiée ; reçoit le refus éventuel par rappel
	inOnCancelSale : supprime la vente (le bien redevient détenu)
	inOnDismiss    : fermeture
*/
@Composable
fun SaleSheet(
	inStart        : SaleForm,
	inItem         : PropertyItem,
	inAccounts     : List<Account>,
	inOnSave       : (SaleForm, (String) -> Unit) -> Unit,
	inOnCancelSale : () -> Unit,
	inOnDismiss    : () -> Unit
)
{
	var vForm    by remember { mutableStateOf(inStart) }								// saisie en cours
	var vErrors  by remember { mutableStateOf(PropertyErrors()) }						// erreurs par champ
	var vGeneral by remember { mutableStateOf<String?>(null) }							// refus de l'enregistrement
	val vHidden  = LocalAmountsHidden.current											// mode discret
	val vGain    = saleGain(vForm, inItem)												// plus- ou moins-value
	FormSheet(inTitle = "Vente de « ${inItem.name} »", inOnDismiss = inOnDismiss) {
		Text(
			text  = "Acheté ${if (vHidden) kMaskedAmount else formatEuros(inItem.cost)}.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		AmountInput(
			inValue    = vForm.price,
			inOnChange = { vForm = vForm.copy(price = it) },
			inLabel    = "Prix de vente",
			inError    = vErrors.price,
			inSupport  = when
			{
				vGain == null -> "0 pour un don ou une perte."
				vHidden       -> "Plus-value masquée."
				vGain >= 0    -> "Plus-value : ${formatEuros(vGain, true)}"
				else          -> "Moins-value : ${formatEuros(vGain)}"
			}
		)
		Picker(
			inLabel    = "Prix versé sur",
			inOptions  = listOf<Pair<String?, String>>(null to "Aucun compte (don, perte)") + inAccounts.filter { !it.type.isProperty && (!it.archived || it.id == vForm.soldTo) }.map { it.id to accountLabel(it) },
			inSelected = vForm.soldTo,
			inError    = vErrors.account
		) { vForm = vForm.copy(soldTo = it) }
		DateInput(vForm.date, { vForm = vForm.copy(date = it) }, "Date de vente")
		vErrors.date?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		TextInput(vForm.notes, { vForm = vForm.copy(notes = it) }, "Notes (facultatif)")
		vGeneral?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		Button(
			onClick        = {
				vErrors = checkSale(vForm, inItem, inAccounts)
				if (vErrors.isEmpty) inOnSave(vForm) { vGeneral = it }
			},
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) {
			Text("Enregistrer la vente")
		}
		if (vForm.id != null)
		{
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
				TextButton(onClick = inOnCancelSale) { Text("Annuler la vente", color = MaterialTheme.colorScheme.error) }
			}
		}
	}
}
