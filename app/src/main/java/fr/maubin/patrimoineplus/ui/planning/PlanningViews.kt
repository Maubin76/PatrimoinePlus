package fr.maubin.patrimoineplus.ui.planning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.calc.Installment
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.ui.components.AmountInput
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.DateInput
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.components.FormSheet
import fr.maubin.patrimoineplus.ui.components.Picker
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.components.SegmentedChoice
import fr.maubin.patrimoineplus.ui.components.TextInput
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.maubin.patrimoineplus.domain.format.formatFrenchDate
import fr.maubin.patrimoineplus.domain.format.parseFrenchDate
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.domain.calc.PropertyItem
import fr.maubin.patrimoineplus.ui.account.AccountExtras

private val kDay = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)			// date d'une échéance

/*
	Section des opérations récurrentes (onglet Comptes) : libellé, compte,
	fréquence et prochaine échéance, montant ; toucher pour modifier ;
	« Ajouter ».
	inRecs       : récurrences
	inAccounts   : comptes (noms)
	inCategories : catégories (noms)
	inToday      : date du jour
	inOnAdd      : nouvelle récurrence
	inOnOpen     : ouvre une récurrence
*/
@Composable
fun RecurrencesSection(
	inRecs       : List<Recurrence>,
	inAccounts   : List<Account>,
	inCategories : List<Category>,
	inToday      : LocalDate,
	inOnAdd      : () -> Unit,
	inOnOpen     : (Recurrence) -> Unit
)
{
	val vNames  = inAccounts.associate { it.id to it.name }								// noms des comptes
	val vColors = MaterialTheme.colorScheme												// palette courante
	SectionHeader(inTitle = "Opérations récurrentes", inLinkLabel = "Ajouter", inOnLink = inOnAdd)
	Spacer(Modifier.height(8.dp))
	BorderedCard {
		if (inRecs.isEmpty())
		{
			EmptyState(inTitle = "Aucune récurrence", inBody = "Salaire, loyer, abonnements, versements : chaque échéance crée l’opération toute seule. Ou choisissez « Répéter » en saisissant une opération.")
		}
		inRecs.sortedBy { nextOccurrence(it, inToday) ?: LocalDate.MAX }.forEachIndexed { inIndex, inRec ->
			if (inIndex > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = vColors.outlineVariant)
			val vNext = nextOccurrence(inRec, inToday)												// prochaine échéance
			Row(
				modifier          = Modifier.fillMaxWidth().clickable(onClickLabel = "Modifier") { inOnOpen(inRec) }.padding(horizontal = 20.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(Modifier.weight(1f)) {
					Text(inRec.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
					Text(
						text     = (vNames[inRec.accountId].orEmpty() + (inRec.toAccountId?.let { " → ${vNames[it].orEmpty()}" } ?: inCategories.firstOrNull { it.id == inRec.categoryId }?.let { " · ${it.name}" }.orEmpty())),
						style    = MaterialTheme.typography.bodyMedium,
						color    = vColors.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					Text(inRec.frequency.label + " · " + (vNext?.let { "prochaine le ${it.format(kDay)}" } ?: "terminée"), style = MaterialTheme.typography.bodySmall, color = vColors.onSurfaceVariant)
				}
				AmountText(inCents = inRec.amountCents, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = inRec.toAccountId == null)
			}
		}
	}
}

/*
	Feuille de saisie d'une récurrence : nature, libellé, montant, compte
	(et compte d'arrivée ou catégorie), fréquence, première échéance, notes ;
	en modification, suppression.
	inStart      : saisie de départ
	inAccounts   : comptes
	inCategories : catégories
	inOnSave     : enregistre ; reçoit les erreurs et le refus éventuel
	inOnDelete   : supprime la récurrence modifiée
	inOnDismiss  : fermeture
*/
@Composable
fun RecurrenceSheet(
	inStart      : RecurrenceForm,
	inAccounts   : List<Account>,
	inCategories : List<Category>,
	inOnSave     : (RecurrenceForm, (RecurrenceErrors, String?) -> Unit) -> Unit,
	inOnDelete   : () -> Unit,
	inOnDismiss  : () -> Unit
)
{
	var vForm    by remember { mutableStateOf(inStart) }									// saisie en cours
	var vErrors  by remember { mutableStateOf(RecurrenceErrors()) }						// erreurs par champ
	var vGeneral by remember { mutableStateOf<String?>(null) }								// refus
	var vEndText by remember { mutableStateOf(inStart.end?.let(::formatFrenchDate).orEmpty()) }	// fin tapée
	val vEnd     = if (vEndText.isBlank()) null else parseFrenchDate(vEndText)				// fin lue
	val vEndError = if (vEndText.isNotBlank() && vEnd == null) "Date illisible : JJ/MM/AAAA." else null	// fin illisible
	val vKinds   = listOf("Dépense", "Revenu", "Virement")									// natures
	val vKind    = when { vForm.transfer -> 2; vForm.expense -> 0; else -> 1 }				// nature choisie
	val vUsable  = inAccounts.filter { !it.type.isProperty && (!it.archived || it.id == vForm.accountId || it.id == vForm.toAccountId) }	// comptes proposés
	FormSheet(inTitle = if (vForm.id == null) "Nouvelle récurrence" else "Modifier la récurrence", inOnDismiss = inOnDismiss) {
		SegmentedChoice(vKinds.indices.toList(), vKind, { vKinds[it] }, { inKind -> vForm = vForm.copy(transfer = inKind == 2, expense = inKind == 0, categoryId = null) })
		TextInput(vForm.label, { vForm = vForm.copy(label = it) }, "Libellé", vErrors.label)
		AmountInput(vForm.amount, { vForm = vForm.copy(amount = it.replace("-", "")) }, "Montant", vErrors.amount)
		Picker(if (vForm.transfer) "Depuis" else "Compte", vUsable.map { it.id as String? to it.name }, vForm.accountId, vErrors.account) { vForm = vForm.copy(accountId = it) }
		if (vForm.transfer)
		{
			Picker("Vers", vUsable.map { it.id as String? to it.name }, vForm.toAccountId, vErrors.target) { vForm = vForm.copy(toAccountId = it) }
		}
		else
		{
			val vCatKind = if (vForm.expense) CategoryKind.EXPENSE else CategoryKind.INCOME		// nature des catégories
			Picker("Catégorie", inCategories.filter { it.kind == vCatKind && (!it.archived || it.id == vForm.categoryId) }.map { it.id as String? to it.name }, vForm.categoryId, vErrors.category) { vForm = vForm.copy(categoryId = it) }
		}
		SegmentedChoice(Frequency.entries, vForm.frequency, { it.label.removePrefix("Chaque ") }, { vForm = vForm.copy(frequency = it) })
		DateInput(vForm.start, { vForm = vForm.copy(start = it) }, "Première échéance")
		TextInput(vEndText, { vEndText = it }, "Fin (facultatif)", vErrors.end ?: vEndError, "JJ/MM/AAAA ; vide : sans fin")
		TextInput(vForm.notes, { vForm = vForm.copy(notes = it) }, "Notes (facultatif)")
		Text("Chaque échéance crée l’opération à sa date ; une échéance déjà passée est créée dès l’enregistrement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
		vGeneral?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
		Button(
			onClick        = { if (vEndError == null) inOnSave(vForm.copy(end = vEnd)) { inErrors, inRefusal -> vErrors = inErrors; vGeneral = inRefusal } },
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) {
			Text("Enregistrer")
		}
		if (vForm.id != null)
		{
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
				TextButton(onClick = inOnDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
			}
		}
	}
}

/*
	Champs d'un nouveau crédit dans la feuille du compte : montant, taux,
	durée, première mensualité, assurance, compte payeur, catégorie des
	intérêts ; mensualité calculée en direct.
	inForm       : saisie du crédit
	inErrors     : erreurs par champ
	inAccounts   : comptes (payeur)
	inCategories : catégories (intérêts)
	inProperties : biens détenus qu'il peut financer
	inOnChange   : saisie modifiée
*/
@Composable
fun LoanFields(
	inForm       : LoanForm,
	inErrors     : LoanErrors,
	inAccounts   : List<Account>,
	inCategories : List<Category>,
	inProperties : List<PropertyItem>,
	inOnChange   : (LoanForm) -> Unit
)
{
	AmountInput(inForm.principal, { inOnChange(inForm.copy(principal = it)) }, "Montant emprunté", inErrors.principal)
	TextInput(inForm.rate, { inOnChange(inForm.copy(rate = it)) }, "Taux annuel (%)", inErrors.rate, "Taux nominal, hors assurance (ex. 3,45)")
	TextInput(inForm.months, { inOnChange(inForm.copy(months = it.filter(Char::isDigit))) }, "Durée (mois)", inErrors.months, "240 pour 20 ans")
	DateInput(inForm.firstDue, { inOnChange(inForm.copy(firstDue = it)) }, "Première mensualité")
	AmountInput(inForm.insurance, { inOnChange(inForm.copy(insurance = it)) }, "Assurance mensuelle (facultatif)", inErrors.insurance)
	Picker("Payé depuis", inAccounts.filter { !it.archived && !it.type.isProperty && it.type != AccountType.LOAN }.map { it.id as String? to it.name }, inForm.payFrom, inErrors.payFrom) { inOnChange(inForm.copy(payFrom = it)) }
	Picker("Catégorie des intérêts", listOf<Pair<String?, String>>(null to "Crédit (créée si besoin)") + inCategories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }.map { it.id to it.name }, inForm.categoryId, null) { inOnChange(inForm.copy(categoryId = it)) }
	Picker("Bien financé", listOf<Pair<String?, String>>(null to "Aucun (auto, prêt personnel…)") + inProperties.map { it.acquisition.id to it.name }, inForm.propertyId, null) { inOnChange(inForm.copy(propertyId = it)) }
	Text(
		(loanPreview(inForm)?.let { "Mensualité : ${formatEuros(it)} (assurance comprise). " } ?: "") +
			"Les mensualités déjà passées sont tenues pour payées ; chaque mensualité suivante créera une opération : le compte payeur débité du total, le capital remboursé, les intérêts et l’assurance en dépense.",
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant
	)
}

/*
	Section d'un compte Crédit : capital restant dû, mensualité et fin, puis
	les prochaines mensualités (capital, intérêts, restant dû).
	inLoan     : crédit ; null sans tableau d'amortissement
	inSchedule : tableau d'amortissement
	inLeft     : capital restant dû (positif)
	inToday    : date du jour
	inExtras   : bien financé, sa valeur nette et biens possibles
	inOnLink   : lie le crédit à un bien (null : aucun)
*/
@Composable
fun LoanSection(
	inLoan     : Loan?,
	inSchedule : List<Installment>,
	inLeft     : Long,
	inToday    : LocalDate,
	inExtras   : AccountExtras        = AccountExtras(),
	inOnLink   : (String?) -> Unit    = {}
)
{
	val vColors = MaterialTheme.colorScheme												// palette courante
	val vNext   = inSchedule.filter { it.date.isAfter(inToday) }							// mensualités à venir
	Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		AmountText(inCents = inLeft, inStyle = MaterialTheme.typography.displaySmall)
		Spacer(Modifier.height(8.dp))
		Text("Capital restant dû", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
	}
	Spacer(Modifier.height(16.dp))
	if (inLoan == null)
	{
		BorderedCard { EmptyState(inTitle = "Sans tableau d’amortissement", inBody = "Les remboursements de ce compte se saisissent comme des virements.") }
		return
	}
	SectionHeader(inTitle = "Bien financé")
	Spacer(Modifier.height(8.dp))
	BorderedCard {
		Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Picker("Bien", listOf<Pair<String?, String>>(null to "Aucun (auto, prêt personnel…)") + inExtras.candidates.map { it.acquisition.id to it.name }, inLoan.propertyId, null, inOnLink)
			val vNet = inExtras.financedNet													// valeur nette du bien
			when
			{
				vNet != null               ->
				{
					NetLine("Prix d’achat", vNet.item.cost)
					NetLine(if (vNet.loans.size > 1) "Crédits restant dus" else "Restant dû", -vNet.debt)
					NetLine("Valeur nette", vNet.net, true)
				}
				inExtras.financed != null -> Text("Bien vendu : il ne compte plus dans le patrimoine.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				else                      -> Text("Liez le crédit au bien qu’il finance pour voir sa valeur nette.", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
			}
		}
	}
	Spacer(Modifier.height(20.dp))
	SectionHeader(inTitle = "Prochaines mensualités")
	Spacer(Modifier.height(8.dp))
	BorderedCard {
		if (vNext.isEmpty()) EmptyState(inTitle = "Crédit remboursé", inBody = "Toutes les mensualités sont passées.")
		vNext.take(12).forEachIndexed { inIndex, inRow ->
			if (inIndex > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = vColors.outlineVariant)
			Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text("${inRow.date.format(kDay)} · ${inRow.index}/${inLoan.months}", style = MaterialTheme.typography.bodyLarge)
					Row {
						Text("capital ", style = MaterialTheme.typography.bodySmall, color = vColors.onSurfaceVariant)
						AmountText(inCents = inRow.capital, inStyle = MaterialTheme.typography.bodySmall, inColor = vColors.onSurfaceVariant)
						Text(" · intérêts ", style = MaterialTheme.typography.bodySmall, color = vColors.onSurfaceVariant)
						AmountText(inCents = inRow.interest, inStyle = MaterialTheme.typography.bodySmall, inColor = vColors.onSurfaceVariant)
					}
				}
				AmountText(inCents = inRow.total, inStyle = MaterialTheme.typography.bodyLarge)
			}
		}
	}
	Spacer(Modifier.height(8.dp))
	Text(
		"Dernière mensualité le ${inSchedule.last().date.format(kDay)} · intérêts restants ${if (LocalAmountsHidden.current) kMaskedAmount else formatEuros(vNext.sumOf { it.interest })}",
		style    = MaterialTheme.typography.bodySmall,
		color    = vColors.onSurfaceVariant,
		modifier = Modifier.padding(start = 4.dp)
	)
}

/*
	Ligne libellé et montant de la valeur nette d'un bien.
	inLabel  : libellé
	inCents  : montant
	inStrong : ligne mise en avant
*/
@Composable
private fun NetLine(
	inLabel  : String,
	inCents  : Long,
	inStrong : Boolean = false
)
{
	val vStyle = if (inStrong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge	// style de la ligne
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Text(inLabel, style = vStyle, modifier = Modifier.weight(1f))
		AmountText(inCents = inCents, inStyle = vStyle)
	}
}
