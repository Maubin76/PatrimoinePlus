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
import fr.maubin.patrimoineplus.ui.components.Picker
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.ui.planning.LoanForm
import fr.maubin.patrimoineplus.ui.planning.LoanErrors
import fr.maubin.patrimoineplus.ui.planning.LoanFields
import fr.maubin.patrimoineplus.ui.planning.checkLoan
import fr.maubin.patrimoineplus.domain.calc.PropertyItem
import fr.maubin.patrimoineplus.domain.model.Profile

/*
	Feuille de création ou de modification d'un compte : nom, type,
	établissement, part détenue (une part par profil à partir de deux
	profils), solde initial et sa date ; en modification, archivage et
	suppression.
	inInitial     : saisie de départ
	inEditing     : compte modifié ; null pour une création
	inTypeLocked  : vrai si le type ne peut plus changer (le compte a des opérations)
	inOnSave      : enregistre la saisie et renvoie les erreurs par rappel
	inOnArchive   : archive ou désarchive le compte modifié
	inOnDelete    : demande la suppression du compte modifié
	inOnDismiss   : fermeture de la feuille
	inAccounts    : comptes (compte payeur d'un crédit)
	inCategories  : catégories (intérêts d'un crédit)
	inOnSaveLoan  : crée un compte Crédit et son crédit ; reçoit le refus par rappel ; null : pas de création de crédit
	inProperties  : biens (bien financé par un crédit)
	inProfiles    : profils de la famille ; deux ou plus : une part par profil
*/
@Composable
fun AccountSheet(
	inInitial    : AccountForm,
	inEditing    : Account?,
	inTypeLocked : Boolean,
	inOnSave     : (AccountForm, (AccountFormErrors) -> Unit) -> Unit,
	inOnArchive  : () -> Unit,
	inOnDelete   : () -> Unit,
	inOnDismiss  : () -> Unit,
	inAccounts   : List<Account>  = emptyList(),
	inCategories : List<Category> = emptyList(),
	inOnSaveLoan : ((AccountForm, LoanForm, (String?) -> Unit) -> Unit)? = null,
	inProperties : List<PropertyItem> = emptyList(),
	inProfiles   : List<Profile> = emptyList()
)
{
	var vForm    by remember { mutableStateOf(inInitial) }						// saisie en cours
	var vErrors  by remember { mutableStateOf(AccountFormErrors()) }			// erreurs affichées
	var vLoan    by remember { mutableStateOf(LoanForm(firstDue = inInitial.openingDate.plusMonths(1).withDayOfMonth(5), payFrom = inAccounts.firstOrNull { it.type == AccountType.CHECKING && !it.archived }?.id)) }	// crédit à créer
	var vLoanErr by remember { mutableStateOf(LoanErrors()) }					// erreurs du crédit
	val vLoanAt  = remember { vLoan }											// crédit proposé à l'ouverture
	val vNewLoan = inEditing == null && vForm.type == AccountType.LOAN && inOnSaveLoan != null	// création d'un crédit
	FormSheet(inTitle = if (inEditing == null) "Nouveau compte" else "Modifier le compte", inOnDismiss = inOnDismiss, inDirty = vForm != inInitial || vLoan != vLoanAt) {
		TextInput(vForm.name, { vForm = vForm.copy(name = it) }, "Nom", vErrors.name)
		if (inTypeLocked)
		{
			Text("Type : ${accountTypeChoice(vForm.type)}", style = MaterialTheme.typography.bodyLarge)
		}
		else
		{
			Picker("Type", AccountType.entries.map { it.name as String? to accountTypeChoice(it) }, vForm.type.name, null) { inName -> inName?.let { vForm = vForm.copy(type = AccountType.valueOf(it)) } }
		}
		if (inTypeLocked)
		{
			Text(
				text  = "Le type ne peut plus changer : ce compte a des opérations.",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		TextInput(vForm.institution, { vForm = vForm.copy(institution = it) }, if (vForm.type.isProperty) "Description (facultatif)" else "Établissement (facultatif)")
		if (inProfiles.size > 1)
		{
			inProfiles.forEachIndexed { inIndex, inProfile ->
				val vLast = inIndex == inProfiles.size - 1										// dernier champ : aide et erreur
				OutlinedTextField(
					value           = vForm.owners[inProfile.id].orEmpty(),
					onValueChange   = { inText -> vForm = vForm.copy(owners = vForm.owners + (inProfile.id to inText.filter { it.isDigit() }.take(3))) },
					label           = { Text("Part de ${inProfile.name}") },
					suffix          = { Text("%") },
					singleLine      = true,
					isError         = vLast && vErrors.share != null,
					supportingText  = if (vLast) { { Text(vErrors.share ?: "Vide : pas propriétaire. 50 et 50 pour un compte ou un bien du couple.") } } else null,
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
					modifier        = Modifier.fillMaxWidth()
				)
			}
		}
		else OutlinedTextField(
			value           = vForm.share,
			onValueChange   = { inText -> vForm = vForm.copy(share = inText.filter { it.isDigit() }.take(3)) },
			label           = { Text("Part détenue") },
			suffix          = { Text("%") },
			singleLine      = true,
			isError         = vErrors.share != null,
			supportingText  = { Text(vErrors.share ?: if (vForm.type.isProperty) "50 % pour des biens détenus à deux : seule votre part compte dans le patrimoine." else "50 % pour un compte joint : seule votre part compte dans le patrimoine.") },
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
			modifier        = Modifier.fillMaxWidth()
		)
		if (vNewLoan)
		{
			LoanFields(vLoan, vLoanErr, inAccounts, inCategories, inProperties) { vLoan = it }
		}
		else if (vForm.type.isProperty)
		{
			Text(
				text  = "Pas de solde initial : ajoutez ensuite chaque bien depuis la page du compte, avec son prix d’achat. Le compte vaut le prix d’achat des biens détenus.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		else
		{
			AmountInput(
				inValue    = vForm.opening,
				inOnChange = { vForm = vForm.copy(opening = it) },
				inLabel    = if (vForm.type == AccountType.BROKERAGE) "Espèces au départ" else "Solde initial",
				inError    = vErrors.opening,
				inSupport  = if (vForm.type == AccountType.BROKERAGE) "Les positions initiales se saisiront avec les actifs (étape 5)." else "Le solde lu dans l’appli de la banque ; un signe moins pour un découvert."
			)
			DateInput(vForm.openingDate, { vForm = vForm.copy(openingDate = it) }, "Date du solde initial")
		}
		vErrors.general?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		Button(
			onClick        = {
				if (vNewLoan)
				{
					vLoanErr = checkLoan(vLoan, inAccounts)
					if (vForm.name.isBlank()) vErrors = AccountFormErrors(name = "Le nom est obligatoire.")
					else if (vLoanErr.isEmpty) inOnSaveLoan!!(vForm, vLoan) { inRefusal -> vErrors = AccountFormErrors(general = inRefusal) }
				}
				else inOnSave(vForm) { vErrors = it }
			},
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

/*
	Libellé d'un type dans la liste des types de compte.
	inType : type
*/
fun accountTypeChoice(inType: AccountType): String =
	when (inType)
	{
		AccountType.CHECKING    -> "Courant"
		AccountType.SAVINGS     -> "Épargne (livret)"
		AccountType.BROKERAGE   -> "Bourse (PEA, compte-titres)"
		AccountType.REAL_ESTATE -> "Biens immobiliers"
		AccountType.COLLECTIBLE -> "Objets de collection"
		AccountType.LOAN        -> "Crédit (prêt immobilier, auto…)"
	}
