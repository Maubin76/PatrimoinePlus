package fr.maubin.patrimoineplus.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.components.AmountInput
import fr.maubin.patrimoineplus.ui.components.DateInput
import fr.maubin.patrimoineplus.ui.components.FormSheet
import fr.maubin.patrimoineplus.ui.components.SegmentedChoice
import fr.maubin.patrimoineplus.ui.components.TextInput

/*
	Feuille de création ou de modification d'un compte : nom, type,
	établissement, part détenue, solde initial et sa date ; en modification,
	archivage et suppression.
	inInitial     : saisie de départ
	inEditing     : compte modifié ; null pour une création
	inTypeLocked  : vrai si le type ne peut plus changer (le compte a des opérations)
	inOnSave      : enregistre la saisie et renvoie les erreurs par rappel
	inOnArchive   : archive ou désarchive le compte modifié
	inOnDelete    : demande la suppression du compte modifié
	inOnDismiss   : fermeture de la feuille
*/
@Composable
fun AccountSheet(
	inInitial    : AccountForm,
	inEditing    : Account?,
	inTypeLocked : Boolean,
	inOnSave     : (AccountForm, (AccountFormErrors) -> Unit) -> Unit,
	inOnArchive  : () -> Unit,
	inOnDelete   : () -> Unit,
	inOnDismiss  : () -> Unit
)
{
	var vForm   by remember { mutableStateOf(inInitial) }						// saisie en cours
	var vErrors by remember { mutableStateOf(AccountFormErrors()) }			// erreurs affichées
	FormSheet(inTitle = if (inEditing == null) "Nouveau compte" else "Modifier le compte", inOnDismiss = inOnDismiss) {
		TextInput(vForm.name, { vForm = vForm.copy(name = it) }, "Nom", vErrors.name)
		SegmentedChoice(
			inOptions  = AccountType.entries,
			inSelected = vForm.type,
			inLabel    = { it.label },
			inOnSelect = { vForm = vForm.copy(type = it) },
			inEnabled  = !inTypeLocked
		)
		if (inTypeLocked)
		{
			Text(
				text  = "Le type ne peut plus changer : ce compte a des opérations.",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		TextInput(vForm.institution, { vForm = vForm.copy(institution = it) }, "Établissement (facultatif)")
		OutlinedTextField(
			value           = vForm.share,
			onValueChange   = { inText -> vForm = vForm.copy(share = inText.filter { it.isDigit() }.take(3)) },
			label           = { Text("Part détenue") },
			suffix          = { Text("%") },
			singleLine      = true,
			isError         = vErrors.share != null,
			supportingText  = { Text(vErrors.share ?: "50 % pour un compte joint : seule votre part compte dans le patrimoine.") },
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
			modifier        = Modifier.fillMaxWidth()
		)
		AmountInput(
			inValue    = vForm.opening,
			inOnChange = { vForm = vForm.copy(opening = it) },
			inLabel    = if (vForm.type == AccountType.BROKERAGE) "Espèces au départ" else "Solde initial",
			inError    = vErrors.opening,
			inSupport  = if (vForm.type == AccountType.BROKERAGE) "Les positions initiales se saisiront avec les actifs (étape 5)." else "Le solde lu dans l’appli de la banque ; un signe moins pour un découvert."
		)
		DateInput(vForm.openingDate, { vForm = vForm.copy(openingDate = it) }, "Date du solde initial")
		vErrors.general?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		Button(
			onClick        = { inOnSave(vForm) { vErrors = it } },
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) {
			Text(if (inEditing == null) "Créer le compte" else "Enregistrer")
		}
		if (inEditing != null)
		{
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
				TextButton(onClick = inOnArchive) { Text(if (inEditing.archived) "Désarchiver" else "Archiver") }
				TextButton(onClick = inOnDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
			}
		}
	}
}
