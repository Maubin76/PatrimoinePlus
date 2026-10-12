package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Posting
import fr.maubin.patrimoineplus.domain.model.Trade
import fr.maubin.patrimoineplus.domain.model.TradeSide
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/*
	Suggestion de libellé pour la saisie.
	label      : libellé déjà utilisé
	categoryId : catégorie de sa dernière utilisation
*/
data class LabelSuggestion(
	val label      : String,
	val categoryId : String?
)

/*
	Construit une dépense ou un revenu à partir d'une saisie : l'utilisateur
	ne tape jamais de signe, il découle de la nature choisie.
	inId         : identifiant de l'opération (nouveau ou existant)
	inPostingId  : identifiant de la jambe
	inDate       : date
	inAccountId  : compte
	inCents      : montant saisi (le signe éventuel est ignoré)
	inKind       : dépense (montant négatif) ou revenu (positif)
	inCategoryId : catégorie
	inLabel      : libellé
	inNotes      : notes ; vide = aucune
*/
fun buildStandard(
	inId         : String,
	inPostingId  : String,
	inDate       : LocalDate,
	inAccountId  : String,
	inCents      : Long,
	inKind       : CategoryKind,
	inCategoryId : String?,
	inLabel      : String,
	inNotes      : String
): Transaction
{
	val vSigned = if (inKind == CategoryKind.EXPENSE) -abs(inCents) else abs(inCents)	// montant signé
	return Transaction(
		id         = inId,
		date       = inDate,
		kind       = TransactionKind.STANDARD,
		label      = inLabel.trim(),
		categoryId = inCategoryId,
		notes      = inNotes.trim().ifEmpty { null },
		postings   = listOf(Posting(inPostingId, inAccountId, vSigned))
	)
}

/*
	Construit un virement entre deux de mes comptes : une seule opération à
	deux jambes de somme nulle.
	inId        : identifiant de l'opération
	inOutId     : identifiant de la jambe de départ
	inInId      : identifiant de la jambe d'arrivée
	inDate      : date
	inFromId    : compte débité
	inToId      : compte crédité
	inCents     : montant (le signe éventuel est ignoré)
	inLabel     : libellé
	inNotes     : notes ; vide = aucune
*/
fun buildTransfer(
	inId     : String,
	inOutId  : String,
	inInId   : String,
	inDate   : LocalDate,
	inFromId : String,
	inToId   : String,
	inCents  : Long,
	inLabel  : String,
	inNotes  : String
): Transaction
{
	val vAmount = abs(inCents)													// montant viré
	return Transaction(
		id       = inId,
		date     = inDate,
		kind     = TransactionKind.TRANSFER,
		label    = inLabel.trim(),
		notes    = inNotes.trim().ifEmpty { null },
		postings = listOf(Posting(inOutId, inFromId, -vAmount), Posting(inInId, inToId, vAmount))
	)
}

/*
	Construit un ordre de bourse : la jambe espèces vaut cours × quantité ±
	frais (achat débité, vente créditée).
	inId        : identifiant de l'opération
	inPostingId : identifiant de la jambe
	inTradeId   : identifiant de l'ordre
	inDate      : date
	inAccountId : compte Bourse
	inSide      : achat ou vente
	inAssetId   : actif
	inQuantity  : quantité, en dix-millièmes
	inUnitPrice : cours, en dix-millièmes d'euro
	inFeesCents : frais
	inLabel     : libellé
	inNotes     : notes ; vide = aucune
*/
fun buildTrade(
	inId        : String,
	inPostingId : String,
	inTradeId   : String,
	inDate      : LocalDate,
	inAccountId : String,
	inSide      : TradeSide,
	inAssetId   : String,
	inQuantity  : Long,
	inUnitPrice : Long,
	inFeesCents : Long,
	inLabel     : String,
	inNotes     : String
): Transaction =
	Transaction(
		id       = inId,
		date     = inDate,
		kind     = TransactionKind.TRADE,
		label    = inLabel.trim(),
		notes    = inNotes.trim().ifEmpty { null },
		postings = listOf(Posting(inPostingId, inAccountId, tradeCashCents(inSide, inQuantity, inUnitPrice, inFeesCents))),
		trades   = listOf(Trade(inTradeId, inAssetId, inSide, inQuantity, inUnitPrice, inFeesCents))
	)

/*
	Suggestions de libellés pour un début de saisie : libellés déjà utilisés
	sur des dépenses ou revenus, sans tenir compte des majuscules ni des
	accents, commençant par le texte (ou contenant un mot qui commence par
	lui), classés par fréquence puis par récence. Chaque suggestion porte la
	catégorie de sa dernière utilisation.
	inTransactions : opérations connues
	inPrefix       : texte saisi
	inLimit        : nombre maximum de suggestions
*/
fun labelSuggestions(
	inTransactions : List<Transaction>,
	inPrefix       : String,
	inLimit        : Int = 5
): List<LabelSuggestion>
{
	val vKey = fold(inPrefix)													// texte cherché, normalisé
	if (vKey.isEmpty())
	{
		return emptyList()
	}
	return inTransactions
		.filter { it.kind == TransactionKind.STANDARD }
		.groupBy { fold(it.label) }
		.filterKeys { inLabel -> inLabel != vKey && (inLabel.startsWith(vKey) || inLabel.split(' ').any { it.startsWith(vKey) }) }
		.values
		.sortedWith(compareByDescending<List<Transaction>> { it.size }.thenByDescending { inGroup -> inGroup.maxOf { it.date } })
		.take(inLimit)
		.map { inGroup ->
			val vLast = inGroup.maxBy { it.date }								// dernière utilisation
			LabelSuggestion(vLast.label, vLast.categoryId)
		}
}

/*
	Soldes de clôture d'un compte sur plusieurs mois consécutifs se terminant
	par un mois donné (inclus), du plus ancien au plus récent.
	inLedger    : grand livre
	inAccountId : compte
	inLastMonth : dernier mois de la série
	inCount     : nombre de mois
*/
fun balanceSeries(
	inLedger    : Ledger,
	inAccountId : String,
	inLastMonth : YearMonth,
	inCount     : Int = 12
): List<SeriesPoint> =
	(inCount - 1 downTo 0).map { inBack ->
		val vMonth = inLastMonth.minusMonths(inBack.toLong())					// mois du point
		SeriesPoint(vMonth, inLedger.balanceAt(inAccountId, vMonth.atEndOfMonth()))
	}

/*
	Opérations d'un compte sur un mois, de la plus récente à la plus ancienne.
	inLedger    : grand livre
	inAccountId : compte
	inMonth     : mois
*/
fun monthTransactions(
	inLedger    : Ledger,
	inAccountId : String,
	inMonth     : YearMonth
): List<Transaction> =
	inLedger.transactions()
		.filter { YearMonth.from(it.date) == inMonth && it.postings.any { inPosting -> inPosting.accountId == inAccountId } }
		.sortedByDescending { it.date }

/*
	Montant d'une opération vu d'un compte : somme de ses jambes sur ce compte.
	inTx        : opération
	inAccountId : compte
*/
fun amountOn(
	inTx        : Transaction,
	inAccountId : String
): Long = inTx.postings.filter { it.accountId == inAccountId }.sumOf { it.amountCents }

/*
	Texte sans accents, en minuscules, espaces réduits : pour comparer des libellés.
	inText : texte
*/
fun fold(inText: String): String =
	Normalizer.normalize(inText.trim().lowercase(), Normalizer.Form.NFD)
		.replace(Regex("\\p{M}+"), "")
		.replace(Regex("\\s+"), " ")
