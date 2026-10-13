package fr.maubin.patrimoineplus.ui.planning

import fr.maubin.patrimoineplus.domain.calc.amortization
import fr.maubin.patrimoineplus.domain.calc.occurrences
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.domain.model.Loan
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.ui.entry.EntryForm
import fr.maubin.patrimoineplus.ui.entry.EntryMode
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

// Saisie des crédits et des opérations récurrentes, partagée par le
// téléphone et le PC.

/*
	Crédit tel que saisi.
	principal  : montant emprunté (texte)
	rate       : taux annuel en % (texte, « 3,45 »)
	months     : durée en mois (texte)
	firstDue   : date de la première mensualité
	insurance  : assurance mensuelle (texte, vide : aucune)
	payFrom    : compte payeur
	categoryId : catégorie des intérêts ; null : « Crédit », créée si besoin
	propertyId : bien financé (achat) ; null sans bien
*/
data class LoanForm(
	val principal  : String  = "",
	val rate       : String  = "",
	val months     : String  = "",
	val firstDue   : LocalDate,
	val insurance  : String  = "",
	val payFrom    : String? = null,
	val categoryId : String? = null,
	val propertyId : String? = null
)

/*
	Erreurs d'une saisie de crédit, par champ.
*/
data class LoanErrors(
	val principal : String? = null,
	val rate      : String? = null,
	val months    : String? = null,
	val insurance : String? = null,
	val payFrom   : String? = null
)
{
	// Vrai sans aucune erreur.
	val isEmpty: Boolean get() = listOf(principal, rate, months, insurance, payFrom).all { it == null }
}

/*
	Taux lu en points de base (« 3,45 » → 345) ; null s'il est illisible ou
	hors de 0 à 30 %.
	inText : taux saisi, en %
*/
fun parseRateBp(inText: String): Int? =
	inText.trim().removeSuffix("%").trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..30.0 }?.let { (it * 100).roundToLong().toInt() }

/*
	Taux en points de base pour un champ (« 3,45 »).
	inBp : taux, en points de base
*/
fun editableRate(inBp: Int): String = if (inBp % 100 == 0) (inBp / 100).toString() else "${inBp / 100},${(inBp % 100).toString().padStart(2, '0').trimEnd('0')}"

/*
	Vérifie la saisie d'un crédit.
	inForm     : saisie
	inAccounts : comptes (compte payeur)
*/
fun checkLoan(
	inForm     : LoanForm,
	inAccounts : List<Account>
): LoanErrors
{
	val vPrincipal = parseEuros(inForm.principal)												// montant lu
	return LoanErrors(
		principal = if (vPrincipal == null || vPrincipal <= 0) "Montant illisible ou nul (ex. 15 000)." else null,
		rate      = if (parseRateBp(inForm.rate) == null) "Taux illisible : de 0 à 30 % (ex. 3,45)." else null,
		months    = if (inForm.months.trim().toIntOrNull()?.takeIf { it in 1..600 } == null) "Durée en mois : de 1 à 600." else null,
		insurance = if (inForm.insurance.isNotBlank() && (parseEuros(inForm.insurance) ?: -1L) < 0) "Assurance illisible (ex. 12,50)." else null,
		payFrom   = if (inAccounts.none { it.id == inForm.payFrom && !it.type.isProperty && it.type != AccountType.LOAN }) "Choisissez le compte qui paie les mensualités." else null
	)
}

/*
	Crédit d'une saisie vérifiée (compte Crédit à créer : identifiant vide).
	inForm : saisie vérifiée
*/
fun buildLoan(inForm: LoanForm): Loan =
	Loan(
		id         = "",
		accountId  = "",
		principal  = parseEuros(inForm.principal)!!,
		rateBp     = parseRateBp(inForm.rate)!!,
		months     = inForm.months.trim().toInt(),
		firstDue   = inForm.firstDue,
		insurance  = if (inForm.insurance.isBlank()) 0L else parseEuros(inForm.insurance)!!,
		payFrom    = inForm.payFrom!!,
		categoryId = inForm.categoryId.orEmpty(),
		propertyId = inForm.propertyId
	)

/*
	Mensualité (assurance comprise) d'une saisie de crédit, pour l'afficher
	pendant la saisie ; null tant que la saisie est incomplète.
	inForm : saisie
*/
fun loanPreview(inForm: LoanForm): Long? =
	runCatching { amortization(buildLoan(inForm.copy(payFrom = inForm.payFrom ?: "-"))).firstOrNull()?.total }.getOrNull()

/*
	Opération récurrente telle que saisie.
	id          : récurrence modifiée ; null pour une nouvelle
	transfer    : virement (sinon dépense ou revenu)
	expense     : dépense (sinon revenu), hors virement
	label       : libellé
	amount      : montant (texte, sans signe)
	accountId   : compte (de départ d'un virement)
	toAccountId : compte d'arrivée d'un virement
	categoryId  : catégorie d'une dépense ou d'un revenu
	frequency   : fréquence
	start       : première échéance
	end         : dernière échéance possible ; null sans fin
	notes       : notes
	refund      : remboursement (montant reçu dans une catégorie de dépense)
	last        : dernière échéance déjà créée (gardée en modification)
*/
data class RecurrenceForm(
	val id          : String?    = null,
	val transfer    : Boolean    = false,
	val expense     : Boolean    = true,
	val label       : String     = "",
	val amount      : String     = "",
	val accountId   : String?    = null,
	val toAccountId : String?    = null,
	val categoryId  : String?    = null,
	val frequency   : Frequency  = Frequency.MONTHLY,
	val start       : LocalDate,
	val end         : LocalDate? = null,
	val notes       : String     = "",
	val refund      : Boolean    = false,
	val last        : LocalDate? = null
)

/*
	Erreurs d'une saisie de récurrence, par champ.
*/
data class RecurrenceErrors(
	val label    : String? = null,
	val amount   : String? = null,
	val account  : String? = null,
	val target   : String? = null,
	val category : String? = null,
	val end      : String? = null
)
{
	// Vrai sans aucune erreur.
	val isEmpty: Boolean get() = listOf(label, amount, account, target, category, end).all { it == null }
}

/*
	Saisie pré-remplie d'une récurrence existante.
	inRec        : récurrence
	inCategories : catégories (nature d'une dépense ou d'un revenu)
*/
fun editRecurrenceForm(
	inRec        : Recurrence,
	inCategories : List<Category>
): RecurrenceForm =
	RecurrenceForm(
		id          = inRec.id,
		transfer    = inRec.toAccountId != null,
		expense     = inRec.toAccountId == null && (inCategories.firstOrNull { it.id == inRec.categoryId }?.kind ?: if (inRec.amountCents < 0) CategoryKind.EXPENSE else CategoryKind.INCOME) == CategoryKind.EXPENSE,
		label       = inRec.label,
		amount      = editableEuros(abs(inRec.amountCents)),
		accountId   = inRec.accountId,
		toAccountId = inRec.toAccountId,
		categoryId  = inRec.categoryId,
		frequency   = inRec.frequency,
		start       = inRec.start,
		end         = inRec.end,
		notes       = inRec.notes.orEmpty(),
		refund      = inRec.toAccountId == null && inRec.amountCents > 0 && inCategories.firstOrNull { it.id == inRec.categoryId }?.kind == CategoryKind.EXPENSE,
		last        = inRec.last
	)

/*
	Vérifie une saisie de récurrence.
	inForm       : saisie
	inAccounts   : comptes
	inCategories : catégories
*/
fun checkRecurrence(
	inForm       : RecurrenceForm,
	inAccounts   : List<Account>,
	inCategories : List<Category>
): RecurrenceErrors
{
	val vCents = parseEuros(inForm.amount)														// montant lu
	val vUsable = { inId: String? -> inAccounts.any { it.id == inId && !it.type.isProperty } }	// compte utilisable
	return RecurrenceErrors(
		label    = if (inForm.label.isBlank()) "Le libellé est obligatoire." else null,
		amount   = if (vCents == null || vCents <= 0) "Montant illisible ou nul (ex. 12,50)." else null,
		account  = if (!vUsable(inForm.accountId)) "Choisissez un compte." else null,
		target   = when
		{
			!inForm.transfer                         -> null
			!vUsable(inForm.toAccountId)             -> "Choisissez le compte d’arrivée."
			inForm.toAccountId == inForm.accountId   -> "Choisissez deux comptes différents."
			else                                     -> null
		},
		category = if (!inForm.transfer && inCategories.none { it.id == inForm.categoryId && it.kind == (if (inForm.expense) CategoryKind.EXPENSE else CategoryKind.INCOME) }) "Choisissez une catégorie." else null,
		end      = if (inForm.end != null && inForm.end.isBefore(inForm.start)) "La fin précède la première échéance." else null
	)
}

/*
	Récurrence d'une saisie vérifiée.
	inForm : saisie vérifiée
*/
fun buildRecurrence(inForm: RecurrenceForm): Recurrence
{
	val vCents = parseEuros(inForm.amount)!!														// montant lu
	return Recurrence(
		id          = inForm.id ?: UUID.randomUUID().toString(),
		label       = inForm.label.trim(),
		accountId   = inForm.accountId!!,
		toAccountId = inForm.toAccountId.takeIf { inForm.transfer },
		categoryId  = inForm.categoryId.takeIf { !inForm.transfer },
		amountCents = if (!inForm.transfer && inForm.expense && !inForm.refund) -vCents else vCents,
		frequency   = inForm.frequency,
		start       = inForm.start,
		end         = inForm.end,
		last        = inForm.last,
		notes       = inForm.notes.trim().ifEmpty { null }
	)
}

/*
	Récurrence qui reprend une opération saisie (« Répéter ») : mêmes compte,
	catégorie, montant et libellé ; la série part de la date de l'opération
	(qui en est la première échéance, déjà saisie) : un 31 donne ensuite le
	dernier jour des mois plus courts sans dériver. Null pour un ordre de
	bourse ou une saisie illisible.
	inForm      : saisie de l'opération (vérifiée)
	inLabel     : libellé retenu pour l'opération
	inFrequency : fréquence choisie
*/
fun recurrenceFromEntry(
	inForm      : EntryForm,
	inLabel     : String,
	inFrequency : Frequency
): Recurrence?
{
	if (inForm.mode == EntryMode.TRADE || inForm.mode == EntryMode.LOAN) return null
	val vCents = parseEuros(inForm.amount)?.let(::abs) ?: return null						// montant lu
	return Recurrence(
		id          = UUID.randomUUID().toString(),
		label       = inLabel,
		accountId   = inForm.accountId ?: return null,
		toAccountId = inForm.toAccountId.takeIf { inForm.mode == EntryMode.TRANSFER },
		categoryId  = inForm.categoryId.takeIf { inForm.mode != EntryMode.TRANSFER },
		amountCents = if (inForm.mode == EntryMode.EXPENSE && !inForm.refund) -vCents else vCents,
		frequency   = inFrequency,
		start       = inForm.date,
		last        = inForm.date,
		notes       = inForm.notes.trim().ifEmpty { null }
	)
}

/*
	Prochaine échéance d'une récurrence à partir d'une date (celle du jour
	comprise tant qu'elle n'est pas créée) ; null si elle est terminée.
	inRec   : récurrence
	inToday : date du jour
*/
fun nextOccurrence(
	inRec   : Recurrence,
	inToday : LocalDate
): LocalDate? = occurrences(inRec, listOfNotNull(inRec.last, inToday.minusDays(1)).max(), maxOf(inToday, inRec.start).plusYears(2)).firstOrNull()
