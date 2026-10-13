package fr.maubin.patrimoineplus.desktop.ui.panels

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.data.edit.OpeningBalance
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.accounts.AccountForm
import fr.maubin.patrimoineplus.ui.planning.LoanForm
import fr.maubin.patrimoineplus.ui.planning.LoanErrors
import fr.maubin.patrimoineplus.ui.planning.checkLoan
import fr.maubin.patrimoineplus.ui.planning.buildLoan
import fr.maubin.patrimoineplus.ui.planning.loanPreview
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.calc.propertyItems

/*
	Création ou modification d'un compte dans le panneau latéral : nom, type
	(verrouillé dès la première opération), établissement, part détenue, solde
	initial ; archivage et suppression (simple, ou définitive après
	confirmation).
	inController : contrôleur
	inAccountId  : compte modifié ; null pour en créer un
*/
@Composable
fun AccountPanel(
	inController : DesktopController,
	inAccountId  : String?
)
{
	val vSpace   by inController.workspace.collectAsState()					// données
	val vAccount = inAccountId?.let { inId -> vSpace.data.accounts.firstOrNull { it.id == inId } }	// compte modifié
	val vOpening = inAccountId?.let { inController.editor.openingOf(inController.currentFile(), it) }	// solde initial actuel
	val vOps     = inAccountId?.let { inController.editor.operationCount(inController.currentFile(), it) } ?: 0	// opérations du compte
	var vForm    by remember(inAccountId) {
		mutableStateOf(
			AccountForm(
				name        = vAccount?.name.orEmpty(),
				type        = vAccount?.type ?: AccountType.CHECKING,
				institution = vAccount?.institution.orEmpty(),
				share       = (vAccount?.sharePercent ?: 100).toString(),
				opening     = vOpening?.cents?.takeIf { it != 0L }?.let(::editableEuros).orEmpty(),
				openingDate = vOpening?.date ?: inController.today()
			)
		)
	}																			// saisie
	var vError   by remember(inAccountId) { mutableStateOf<String?>(null) }	// erreur affichée
	var vDelete  by remember(inAccountId) { mutableStateOf(false) }			// confirmation de suppression
	var vDateBad by remember(inAccountId) { mutableStateOf(false) }			// date du solde initial illisible
	var vLoan    by remember(inAccountId) { mutableStateOf(LoanForm(firstDue = inController.today().plusMonths(1).withDayOfMonth(5), payFrom = vSpace.data.accounts.firstOrNull { it.type == AccountType.CHECKING && !it.archived }?.id)) }	// crédit à créer
	var vLoanErr by remember(inAccountId) { mutableStateOf(LoanErrors()) }	// erreurs du crédit
	val vNewLoan = vAccount == null && vForm.type == AccountType.LOAN			// création d'un crédit

	/*
		Enregistre : création, ou modification puis solde initial.
	*/
	fun save()
	{
		if (inController.panel.value == null) return						// panneau déjà fermé (double clic pendant sa disparition)
		val vShare   = vForm.share.trim().toIntOrNull()							// part détenue lue
		val vCents   = if (vForm.opening.isBlank() || vForm.type.isProperty || (vAccount == null && vForm.type == AccountType.LOAN)) 0L else parseEuros(vForm.opening)	// solde initial lu (aucun pour un compte de biens ou un crédit à créer)
		vError = when
		{
			vShare == null -> "Part détenue illisible (1 à 100)."
			vCents == null -> "Solde initial illisible (ex. 1 250,00)."
			vDateBad && !vForm.type.isProperty && !vNewLoan -> kBadDate
			else           -> null
		}
		if (vError != null) return
		if (vNewLoan)
		{
			vLoanErr = checkLoan(vLoan, vSpace.data.accounts)
			if (!vLoanErr.isEmpty) return
			when (val vResult = inController.edit { inController.editor.createLoan(it, vForm.name, vForm.institution, vShare!!, buildLoan(vLoan), inController.today()) })
			{
				is EditResult.Done    -> inController.closePanel()
				is EditResult.Refused -> vError = vResult.messages.joinToString(" ")
			}
			return
		}
		val vBalance = OpeningBalance(vCents!!, vForm.openingDate)				// solde initial
		val vResult  = inController.edit { inFile ->
			if (vAccount == null)
			{
				inController.editor.createAccount(inFile, vForm.name, vForm.type, vForm.institution, vShare!!, vBalance)
			}
			else
			{
				val vBase = if (vForm.type.isProperty) (inController.editor.setOpening(inFile, vAccount.id, OpeningBalance(0, vForm.openingDate)) as? EditResult.Done)?.file ?: inFile else inFile	// un compte de biens n'a pas de solde initial
				when (val vUpdated = inController.editor.updateAccount(vBase, vAccount.copy(name = vForm.name, type = vForm.type, institution = vForm.institution, sharePercent = vShare!!)))
				{
					is EditResult.Done    -> inController.editor.setOpening(vUpdated.file, vAccount.id, vBalance)
					is EditResult.Refused -> vUpdated
				}
			}
		}																			// résultat
		when (vResult)
		{
			is EditResult.Done    -> inController.closePanel()
			is EditResult.Refused -> vError = vResult.messages.joinToString(" ")
		}
	}

	SidePanel(
		inTitle   = if (vAccount == null) "Nouveau compte" else "Modifier le compte",
		inOnClose = inController::closePanel,
		inOnEnter = ::save,
		inActions = {
			if (vAccount != null)
			{
				OutlinedButton(onClick = {
					inController.edit { inController.editor.setArchived(it, vAccount.id, !vAccount.archived) }
					inController.closePanel()
				}) { Text(if (vAccount.archived) "Désarchiver" else "Archiver") }
				OutlinedButton(onClick = { vDelete = true }) { Text("Supprimer") }
			}
			Button(onClick = ::save) { Text("Enregistrer") }
		}
	) {
		FormError(vError)
		FormField(vForm.name, { vForm = vForm.copy(name = it) }, "Nom")
		Picker("Type", AccountType.entries.map { it to typeChoice(it) }, vForm.type, { vForm = vForm.copy(type = it) }, inEnabled = vOps == 0)
		if (vOps > 0) Text("Le type ne change plus : le compte a des opérations.")
		FormField(vForm.institution, { vForm = vForm.copy(institution = it) }, if (vForm.type.isProperty) "Description (facultatif)" else "Établissement (facultatif)")
		FormField(vForm.share, { vForm = vForm.copy(share = it) }, "Part détenue (%)", inSupport = if (vForm.type.isProperty) "50 pour des biens détenus à deux (indivision, couple)." else "50 pour un compte joint à deux.", inNumeric = true)
		if (vNewLoan)
		{
			FormField(vLoan.principal, { vLoan = vLoan.copy(principal = it) }, "Montant emprunté (€)", vLoanErr.principal, inNumeric = true)
			FormField(vLoan.rate, { vLoan = vLoan.copy(rate = it) }, "Taux annuel (%)", vLoanErr.rate, inSupport = "Taux nominal, hors assurance (ex. 3,45)", inNumeric = true)
			FormField(vLoan.months, { vLoan = vLoan.copy(months = it) }, "Durée (mois)", vLoanErr.months, inSupport = "240 pour 20 ans", inNumeric = true)
			DateField(vLoan.firstDue, { inDate -> inDate?.let { vLoan = vLoan.copy(firstDue = it) } }, "Date de la première mensualité")
			FormField(vLoan.insurance, { vLoan = vLoan.copy(insurance = it) }, "Assurance mensuelle (€, facultatif)", vLoanErr.insurance, inNumeric = true)
			Picker("Payé depuis", vSpace.data.accounts.filter { !it.archived && !it.type.isProperty && it.type != AccountType.LOAN }.map { it.id to it.name }, vLoan.payFrom, { vLoan = vLoan.copy(payFrom = it) }, vLoanErr.payFrom)
			Picker("Catégorie des intérêts", listOf<Pair<String?, String>>(null to "Crédit (créée si besoin)") + vSpace.data.categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }.map { it.id to it.name }, vLoan.categoryId, { vLoan = vLoan.copy(categoryId = it) })
			Picker("Bien financé", listOf<Pair<String?, String>>(null to "Aucun (auto, prêt personnel…)") + propertyItems(vSpace.data.transactions, vSpace.data.accounts).filter { it.heldAt(inController.today()) }.map { it.acquisition.id to it.name }, vLoan.propertyId, { vLoan = vLoan.copy(propertyId = it) })
			Text(
				(loanPreview(vLoan)?.let { "Mensualité : ${formatEuros(it)} (assurance comprise). " } ?: "") +
					"Les mensualités déjà passées sont tenues pour payées : le compte part du capital restant dû aujourd’hui. Chaque mensualité suivante créera à sa date une opération : le compte payeur débité du total, le capital remboursé ici, les intérêts et l’assurance en dépense.",
				style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		else if (vForm.type.isProperty)
		{
			Text("Pas de solde initial : ajoutez ensuite chaque bien depuis la page du compte, avec son prix d’achat. Le compte vaut le prix d’achat des biens détenus.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		else
		{
			FormField(vForm.opening, { vForm = vForm.copy(opening = it) }, "Solde initial (€)", inSupport = "Solde à la date ci-dessous ; vide : aucun.", inNumeric = true)
			DateField(vForm.openingDate, { inDate -> vDateBad = inDate == null; inDate?.let { vForm = vForm.copy(openingDate = it) } }, "Date du solde initial")
		}
	}

	if (vDelete && vAccount != null)
	{
		AlertDialog(
			onDismissRequest = { vDelete = false },
			title            = { Text("Supprimer « ${vAccount.name} » ?") },
			text             = {
				Text(
					if (vOps == 0) "Le compte n’a pas d’opération (hors solde initial) : il sera supprimé."
					else "Le compte a $vOps opération(s). L’archiver le masque en gardant l’historique ; la suppression définitive efface aussi ses opérations, virements compris (l’autre compte est modifié), et ses relevés. C’est irréversible."
				)
			},
			confirmButton    = {
				TextButton(onClick = {
					inController.edit { inController.editor.deleteAccount(it, vAccount.id, inPermanent = vOps > 0) }
					vDelete = false
					inController.closePanel()
					inController.back()
				}) { Text(if (vOps == 0) "Supprimer" else "Supprimer définitivement") }
			},
			dismissButton    = {
				if (vOps > 0)
				{
					TextButton(onClick = {
						inController.edit { inController.editor.setArchived(it, vAccount.id, true) }
						vDelete = false
						inController.closePanel()
					}) { Text("Archiver") }
				}
				TextButton(onClick = { vDelete = false }) { Text("Annuler") }
			}
		)
	}
}

/*
	Libellé d'un type dans la liste des types de compte.
	inType : type
*/
private fun typeChoice(inType: AccountType): String =
	when (inType)
	{
		AccountType.CHECKING    -> "Courant"
		AccountType.SAVINGS     -> "Épargne (livret)"
		AccountType.BROKERAGE   -> "Bourse (PEA, compte-titres)"
		AccountType.REAL_ESTATE -> "Biens immobiliers"
		AccountType.COLLECTIBLE -> "Objets de collection"
		AccountType.LOAN        -> "Crédit (prêt immobilier, auto…)"
	}
