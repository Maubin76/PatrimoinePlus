package fr.maubin.patrimoineplus.data.bank

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.calc.amountOn
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.ui.entry.EntryForm
import fr.maubin.patrimoineplus.ui.entry.EntryMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

const val kMatchDays = 5L															// écart de dates accepté entre la banque et la saisie

// Mots des libellés de banque qui ne disent rien du marchand.
private val kNoise = setOf(
	"cb", "carte", "prlv", "prelevement", "prelev", "sepa", "vir", "virement", "recu", "emis", "inst", "instantane",
	"paiement", "achat", "retrait", "dab", "fact", "facture", "ech", "echeance", "frais", "remise", "cheque", "chq",
	"de", "du", "des", "la", "le", "les", "et", "sur", "par", "pour", "avec", "en", "au", "aux", "un", "une",
	"ref", "mandat", "ics", "rum", "num", "no", "ope", "operation", "sarl", "sas", "sa", "eur", "euros", "fr", "france"
)

/*
	Ligne du relevé retrouvée dans l'appli.
	line        : ligne de la banque
	transaction : opération saisie correspondante
*/
data class MatchedLine(
	val line        : BankLine,
	val transaction : Transaction
)

/*
	Ligne du relevé absente de l'appli, avec une saisie proposée.
	line       : ligne de la banque
	label      : libellé proposé (celui d'une saisie passée du même marchand, sinon tiré du libellé de la banque)
	categoryId : catégorie proposée ; null sans proposition
*/
data class MissingLine(
	val line       : BankLine,
	val label      : String,
	val categoryId : String?
)

/*
	Rapprochement d'un relevé de la banque avec un compte.
	from      : première date du relevé
	to        : dernière date du relevé
	matched   : lignes retrouvées
	missing   : lignes absentes de l'appli
	extra     : opérations de l'appli sur la période, absentes du relevé
	bankTotal : somme des lignes du relevé
	appTotal  : somme, sur le compte, des opérations retrouvées et de celles en trop
	            (l'écart avec bankTotal vient donc des seules listes affichées)
*/
data class Reconciliation(
	val from      : LocalDate,
	val to        : LocalDate,
	val matched   : List<MatchedLine>,
	val missing   : List<MissingLine>,
	val extra     : List<Transaction>,
	val bankTotal : Long,
	val appTotal  : Long
)

/*
	Compare un relevé de la banque aux opérations d'un compte : une ligne et
	une opération correspondent si leurs montants sont égaux et leurs dates à
	cinq jours au plus (la banque date souvent au débit, la saisie au jour de
	l'achat) ; les paires les plus proches sont retenues d'abord, chaque
	opération ne sert qu'une fois. Aucune donnée n'est modifiée.
	inLines     : lignes du relevé
	inAccountId : compte comparé
	inData      : données
*/
fun reconcile(
	inLines     : List<BankLine>,
	inAccountId : String,
	inData      : DataSet
): Reconciliation
{
	val vFrom  = inLines.minOf { it.date }											// début du relevé
	val vTo    = inLines.maxOf { it.date }											// fin du relevé
	val vMine  = inData.transactions.filter { inTx ->
		inTx.kind != TransactionKind.OPENING && inTx.postings.any { it.accountId == inAccountId } &&
			!inTx.date.isBefore(vFrom.minusDays(kMatchDays)) && !inTx.date.isAfter(vTo.plusDays(kMatchDays))
	}																				// opérations candidates
	val vPairs = inLines.indices.flatMap { inLine ->
		vMine.indices.mapNotNull { inTx ->
			val vGap = abs(ChronoUnit.DAYS.between(inLines[inLine].date, vMine[inTx].date))	// écart de dates
			if (amountOn(vMine[inTx], inAccountId) == inLines[inLine].amountCents && vGap <= kMatchDays) Triple(inLine, inTx, vGap) else null
		}
	}.sortedBy { it.third }															// paires possibles, les plus proches d'abord
	val vLineUsed = mutableSetOf<Int>()												// lignes déjà associées
	val vTxUsed   = mutableSetOf<Int>()												// opérations déjà associées
	val vMatched  = mutableListOf<MatchedLine>()										// correspondances
	vPairs.forEach { (inLine, inTx, _) ->
		if (inLine !in vLineUsed && inTx !in vTxUsed)
		{
			vLineUsed += inLine
			vTxUsed   += inTx
			vMatched  += MatchedLine(inLines[inLine], vMine[inTx])
		}
	}
	val vExtra   = vMine.indices.filter { it !in vTxUsed && !vMine[it].date.isBefore(vFrom) && !vMine[it].date.isAfter(vTo) }.map { vMine[it] }.sortedBy { it.date }	// en trop
	val vHistory = inData.transactions.filter { it.kind == TransactionKind.STANDARD && it.categoryId != null }	// saisies passées
	return Reconciliation(
		from      = vFrom,
		to        = vTo,
		matched   = vMatched.sortedBy { it.line.date },
		missing   = inLines.indices.filter { it !in vLineUsed }.map { suggest(inLines[it], vHistory, inData) }.sortedBy { it.line.date },
		extra     = vExtra,
		bankTotal = inLines.sumOf { it.amountCents },
		appTotal  = (vMatched.map { it.transaction } + vExtra).sumOf { amountOn(it, inAccountId) }
	)
}

/*
	Saisie proposée pour une ligne absente : la saisie passée dont le libellé
	partage le plus de mots significatifs avec celui de la banque (la plus
	récente à égalité) donne son libellé et sa catégorie, si la catégorie est
	compatible avec le sens du montant (une entrée ne prend une catégorie de
	dépense que pour un remboursement : saisie passée déjà remboursée, ou
	libellé « REMB… », « avoir », « retour ») ; sinon le libellé de la banque,
	nettoyé.
	inLine    : ligne de la banque
	inHistory : saisies passées catégorisées
	inData    : données (catégories)
*/
private fun suggest(
	inLine    : BankLine,
	inHistory : List<Transaction>,
	inData    : DataSet
): MissingLine
{
	val vWords = words(inLine.label)													// mots du libellé de la banque
	val vBest  = inHistory
		.map { it to words(it.label).count { inWord -> inWord in vWords } }
		.filter { it.second > 0 }
		.maxWithOrNull(compareBy<Pair<Transaction, Int>> { it.second }.thenBy { it.first.date })
		?.first																			// saisie passée la plus proche
	val vKind  = vBest?.categoryId?.let { inId -> inData.categories.firstOrNull { it.id == inId }?.kind }	// nature de sa catégorie
	val vSign  = vBest?.postings?.sumOf { it.amountCents } ?: 0L						// sens de la saisie passée
	val vRefund = vSign > 0 || vWords.any { it.startsWith("remb") || it == "avoir" || it.startsWith("retour") }	// remboursement déjà vu, ou annoncé par la banque
	val vFits  = (vKind == CategoryKind.EXPENSE && (inLine.amountCents < 0 || vRefund)) || (vKind == CategoryKind.INCOME && inLine.amountCents > 0)	// catégorie possible pour ce montant
	return MissingLine(
		line       = inLine,
		label      = vBest?.label ?: cleanLabel(inLine.label),
		categoryId = if (vFits) vBest?.categoryId else null
	)
}

/*
	Mots significatifs d'un libellé : sans accents, sans chiffres ni mots
	génériques de banque (CB, PRLV SEPA…), de trois lettres au moins.
	inLabel : libellé
*/
internal fun words(inLabel: String): Set<String> =
	plain(inLabel).split(' ').filter { it.length >= 3 && it.none(Char::isDigit) && it !in kNoise }.toSet()

/*
	Libellé lisible tiré de celui de la banque : ses mots significatifs, avec
	une majuscule (« CB CARREFOUR MARKET 12/10 » → « Carrefour Market ») ; le
	libellé brut s'il n'en reste aucun.
	inLabel : libellé de la banque
*/
internal fun cleanLabel(inLabel: String): String
{
	val vWords = inLabel.split(Regex("[\\s/*.,;:]+"))
		.filter { inWord -> plain(inWord).let { it.length >= 3 && it.none(Char::isDigit) && it !in kNoise } }
		.take(4)																		// mots gardés, dans l'ordre
	return if (vWords.isEmpty()) inLabel.trim() else vWords.joinToString(" ") { inWord -> inWord.lowercase().replaceFirstChar { it.titlecase() } }
}

/*
	Formulaire de saisie d'une ligne de relevé à créer : dépense ou revenu
	selon le sens du montant et la catégorie choisie (un montant positif dans
	une catégorie de dépense est un remboursement). Construit et contrôlé
	ensuite comme une saisie ordinaire (checkEntry, buildEntry).
	inLine       : ligne de la banque
	inLabel      : libellé retenu
	inCategoryId : catégorie retenue ; null si aucune
	inAccountId  : compte
	inCategories : catégories (nature de la catégorie)
*/
fun bankEntryForm(
	inLine       : BankLine,
	inLabel      : String,
	inCategoryId : String?,
	inAccountId  : String,
	inCategories : List<fr.maubin.patrimoineplus.domain.model.Category>
): EntryForm
{
	val vKind = inCategories.firstOrNull { it.id == inCategoryId }?.kind		// nature de la catégorie
	val vMode = when
	{
		vKind == CategoryKind.INCOME                       -> EntryMode.INCOME
		vKind == null && inLine.amountCents > 0            -> EntryMode.INCOME
		else                                               -> EntryMode.EXPENSE
	}																			// mode de saisie
	return EntryForm(
		mode       = vMode,
		amount     = editableEuros(abs(inLine.amountCents)),
		accountId  = inAccountId,
		categoryId = inCategoryId,
		refund     = vMode == EntryMode.EXPENSE && inLine.amountCents > 0,
		label      = inLabel,
		date       = inLine.date
	)
}
