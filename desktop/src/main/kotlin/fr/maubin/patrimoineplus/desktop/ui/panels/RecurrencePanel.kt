package fr.maubin.patrimoineplus.desktop.ui.panels

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.domain.format.formatFrenchDate
import fr.maubin.patrimoineplus.domain.format.parseFrenchDate
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.ui.planning.RecurrenceErrors
import fr.maubin.patrimoineplus.ui.planning.RecurrenceForm
import fr.maubin.patrimoineplus.ui.planning.buildRecurrence
import fr.maubin.patrimoineplus.ui.planning.checkRecurrence
import fr.maubin.patrimoineplus.ui.components.accountLabel

/*
	Nature d'une récurrence dans le panneau.
*/
private enum class RecurringKind(val label: String)
{
	EXPENSE("Dépense"),
	INCOME("Revenu"),
	TRANSFER("Virement")
}

/*
	Création ou modification d'une opération récurrente : nature, libellé,
	montant, compte (et compte d'arrivée ou catégorie), fréquence, première
	échéance, fin facultative, notes ; suppression (les opérations déjà
	créées restent).
	inController : contrôleur
	inStart      : saisie de départ
*/
@Composable
fun RecurrencePanel(
	inController : DesktopController,
	inStart      : RecurrenceForm
)
{
	val vSpace   by inController.workspace.collectAsState()							// données
	val vData    = vSpace.data															// données courantes
	var vForm    by remember(inStart) { mutableStateOf(inStart) }						// saisie
	var vErrors  by remember(inStart) { mutableStateOf(RecurrenceErrors()) }			// erreurs par champ
	var vGeneral by remember(inStart) { mutableStateOf<String?>(null) }				// refus général
	var vBadDay  by remember(inStart) { mutableStateOf(false) }						// première échéance illisible
	var vEndText by remember(inStart) { mutableStateOf(inStart.end?.let(::formatFrenchDate).orEmpty()) }	// fin tapée
	val vKind    = when { vForm.transfer -> RecurringKind.TRANSFER; vForm.expense -> RecurringKind.EXPENSE; else -> RecurringKind.INCOME }	// nature
	val vAccounts = vData.accounts.filter { !it.type.isProperty && (!it.archived || it.id == vForm.accountId || it.id == vForm.toAccountId) }.map { it.id to accountLabel(it) }	// comptes proposés

	/*
		Enregistre la récurrence si la saisie est valide.
	*/
	fun save()
	{
		if (inController.panel.value == null) return						// panneau déjà fermé (double clic pendant sa disparition)
		val vEnd = if (vEndText.isBlank()) null else parseFrenchDate(vEndText)		// fin lue
		if (vBadDay || (vEndText.isNotBlank() && vEnd == null))
		{
			vGeneral = kBadDate
			return
		}
		val vReady = vForm.copy(end = vEnd)												// saisie complète
		vErrors = checkRecurrence(vReady, vData.accounts, vData.categories)
		if (!vErrors.isEmpty) return
		finish(inController, inController.edit { inController.editor.saveRecurrence(it, buildRecurrence(vReady)) }) { vGeneral = it }
	}

	SidePanel(
		inTitle   = if (vForm.id == null) "Nouvelle récurrence" else "Modifier la récurrence",
		inOnClose = inController::closePanel,
		inOnEnter = ::save,
		inActions = {
			vForm.id?.let { inId -> OutlinedButton(onClick = { finish(inController, inController.edit { inController.editor.deleteRecurrence(it, inId) }) { vGeneral = it } }) { Text("Supprimer") } }
			Button(onClick = ::save) { Text("Enregistrer") }
		}
	) {
		FormError(vGeneral)
		Choice(RecurringKind.entries.map { it to it.label }, vKind, { inKind ->
			vForm = vForm.copy(transfer = inKind == RecurringKind.TRANSFER, expense = inKind == RecurringKind.EXPENSE, categoryId = null)
		})
		FormField(vForm.label, { vForm = vForm.copy(label = it) }, "Libellé", vErrors.label)
		FormField(vForm.amount, { vForm = vForm.copy(amount = it.replace("-", "")) }, "Montant (€)", vErrors.amount, inNumeric = true)
		Picker(if (vForm.transfer) "Depuis" else "Compte", vAccounts, vForm.accountId, { vForm = vForm.copy(accountId = it) }, vErrors.account)
		if (vForm.transfer)
		{
			Picker("Vers", vAccounts, vForm.toAccountId, { vForm = vForm.copy(toAccountId = it) }, vErrors.target)
		}
		else
		{
			val vCatKind = if (vForm.expense) CategoryKind.EXPENSE else CategoryKind.INCOME	// nature des catégories
			Picker("Catégorie", vData.categories.filter { it.kind == vCatKind && (!it.archived || it.id == vForm.categoryId) }.sortedBy { it.sortOrder }.map { it.id to it.name }, vForm.categoryId, { vForm = vForm.copy(categoryId = it) }, vErrors.category)
		}
		Choice(Frequency.entries.map { it to it.label }, vForm.frequency, { vForm = vForm.copy(frequency = it) })
		DateField(vForm.start, { inDate -> vBadDay = inDate == null; inDate?.let { vForm = vForm.copy(start = it) } }, "Première échéance")
		FormField(vEndText, { vEndText = it }, "Fin (facultatif)", vErrors.end, inSupport = "JJ/MM/AAAA ; vide : sans fin")
		FormField(vForm.notes, { vForm = vForm.copy(notes = it) }, "Notes (facultatif)")
		Text("Chaque échéance crée l’opération à sa date. Une échéance déjà passée est créée dès l’enregistrement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
	}
}
