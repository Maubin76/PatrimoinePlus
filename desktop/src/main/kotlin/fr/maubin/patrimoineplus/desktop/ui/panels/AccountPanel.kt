package fr.maubin.patrimoineplus.desktop.ui.panels

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import fr.maubin.patrimoineplus.data.edit.OpeningBalance
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.accounts.AccountForm

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

	/*
		Enregistre : création, ou modification puis solde initial.
	*/
	fun save()
	{
		val vShare   = vForm.share.trim().toIntOrNull()							// part détenue lue
		val vCents   = if (vForm.opening.isBlank()) 0L else parseEuros(vForm.opening)	// solde initial lu
		vError = when
		{
			vShare == null -> "Part détenue illisible (1 à 100)."
			vCents == null -> "Solde initial illisible (ex. 1 250,00)."
			else           -> null
		}
		if (vError != null) return
		val vBalance = OpeningBalance(vCents!!, vForm.openingDate)				// solde initial
		val vResult  = inController.edit { inFile ->
			if (vAccount == null)
			{
				inController.editor.createAccount(inFile, vForm.name, vForm.type, vForm.institution, vShare!!, vBalance)
			}
			else
			{
				when (val vUpdated = inController.editor.updateAccount(inFile, vAccount.copy(name = vForm.name, type = vForm.type, institution = vForm.institution, sharePercent = vShare!!)))
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
		Choice(AccountType.entries.map { it to it.label }, vForm.type, { vForm = vForm.copy(type = it) }, inEnabled = vOps == 0)
		if (vOps > 0) Text("Le type ne change plus : le compte a des opérations.")
		FormField(vForm.institution, { vForm = vForm.copy(institution = it) }, "Établissement (facultatif)")
		FormField(vForm.share, { vForm = vForm.copy(share = it) }, "Part détenue (%)", inSupport = "50 pour un compte joint à deux.", inNumeric = true)
		FormField(vForm.opening, { vForm = vForm.copy(opening = it) }, "Solde initial (€)", inSupport = "Solde à la date ci-dessous ; vide : aucun.", inNumeric = true)
		DateField(vForm.openingDate, { vForm = vForm.copy(openingDate = it) }, "Date du solde initial")
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
