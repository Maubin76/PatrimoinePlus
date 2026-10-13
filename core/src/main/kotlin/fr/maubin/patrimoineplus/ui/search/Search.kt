package fr.maubin.patrimoineplus.ui.search

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.calc.fold
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import java.time.LocalDate
import kotlin.math.abs
import fr.maubin.patrimoineplus.ui.property.propertyNature

/*
	Critères de recherche dans les opérations ; un critère vide ne filtre pas.
	text       : mots cherchés dans le libellé, les notes, la catégorie et les comptes (sans accents ni majuscules)
	accountId  : compte ; null pour tous
	categoryId : catégorie ; null pour toutes
	from       : première date, incluse ; null sans limite
	to         : dernière date, incluse ; null sans limite
	minAmount  : montant minimal, en valeur absolue (texte) ; vide sans limite
	maxAmount  : montant maximal, en valeur absolue (texte) ; vide sans limite
*/
data class SearchQuery(
	val text       : String     = "",
	val accountId  : String?    = null,
	val categoryId : String?    = null,
	val from       : LocalDate? = null,
	val to         : LocalDate? = null,
	val minAmount  : String     = "",
	val maxAmount  : String     = ""
)
{
	// Vrai si aucun critère n'est posé.
	val isEmpty: Boolean get() = this == SearchQuery()
}

/*
	Opération trouvée.
	transaction : opération
	amount      : montant affiché (vu du compte filtré ; sinon de la jambe principale, positif pour un virement)
	detail      : comptes et catégorie, en clair
	icon        : clé d'icône
*/
data class SearchHit(
	val transaction : Transaction,
	val amount      : Long,
	val detail      : String,
	val icon        : String
)

/*
	Résultat d'une recherche.
	hits     : opérations trouvées, de la plus récente à la plus ancienne
	total    : somme des montants affichés
	badQuery : critère illisible (montant), à signaler ; null sinon
*/
data class SearchResult(
	val hits     : List<SearchHit>,
	val total    : Long,
	val badQuery : String? = null
)

/*
	Cherche les opérations qui répondent à tous les critères (soldes
	initiaux exclus). Chaque mot du texte doit apparaître dans le libellé, les
	notes, la catégorie ou le nom d'un compte de l'opération ; un montant tapé
	comme mot (« 54,90 ») cherche aussi ce montant exact. Fonction pure.
	inData  : données
	inQuery : critères
*/
fun searchTransactions(
	inData  : DataSet,
	inQuery : SearchQuery
): SearchResult
{
	val vMin = inQuery.minAmount.takeIf { it.isNotBlank() }?.let { parseEuros(it) ?: return SearchResult(emptyList(), 0, "Montant minimal illisible.") }	// borne basse
	val vMax = inQuery.maxAmount.takeIf { it.isNotBlank() }?.let { parseEuros(it) ?: return SearchResult(emptyList(), 0, "Montant maximal illisible.") }	// borne haute
	val vAccounts   = inData.accounts.associateBy { it.id }							// comptes par identifiant
	val vCategories = inData.categories.associateBy { it.id }						// catégories par identifiant
	val vWords      = fold(inQuery.text).split(' ').filter { it.isNotEmpty() }		// mots cherchés
	val vHits = inData.transactions
		.asSequence()
		.filter { it.kind != TransactionKind.OPENING }
		.filter { inTx -> inQuery.accountId == null || inTx.postings.any { it.accountId == inQuery.accountId } }
		.filter { inQuery.categoryId == null || it.categoryId == inQuery.categoryId }
		.filter { inQuery.from == null || !it.date.isBefore(inQuery.from) }
		.filter { inQuery.to == null || !it.date.isAfter(inQuery.to) }
		.map { inTx -> hit(inTx, inQuery.accountId, vAccounts.mapValues { it.value.name }, vCategories.mapValues { it.value.name to it.value.icon }) }
		.filter { inHit -> abs(inHit.amount).let { (vMin == null || it >= abs(vMin)) && (vMax == null || it <= abs(vMax)) } }
		.filter { inHit -> vWords.all { inWord -> matches(inHit, inWord) } }
		.sortedWith(compareByDescending<SearchHit> { it.transaction.date }.thenByDescending { it.transaction.id })
		.toList()
	return SearchResult(vHits, vHits.sumOf { it.amount })
}

/*
	Opération mise en forme pour la liste des résultats.
	inTx         : opération
	inAccountId  : compte filtré ; null sinon
	inAccounts   : nom des comptes
	inCategories : nom et icône des catégories
*/
private fun hit(
	inTx         : Transaction,
	inAccountId  : String?,
	inAccounts   : Map<String, String>,
	inCategories : Map<String, Pair<String, String>>
): SearchHit
{
	val vName = { inId: String -> inAccounts[inId] ?: "Compte supprimé" }			// nom d'un compte
	val vMain = inTx.postings.firstOrNull { it.accountId == inAccountId } ?: inTx.postings.first()	// jambe affichée
	return when (inTx.kind)
	{
		TransactionKind.TRANSFER ->
		{
			val vFrom = inTx.postings.firstOrNull { it.amountCents < 0 } ?: vMain		// compte débité
			val vTo   = inTx.postings.firstOrNull { it.amountCents > 0 } ?: vMain		// compte crédité
			SearchHit(inTx, if (inAccountId != null) vMain.amountCents else vTo.amountCents, "${vName(vFrom.accountId)} → ${vName(vTo.accountId)} · Virement", "repeat")
		}
		TransactionKind.TRADE    -> SearchHit(inTx, vMain.amountCents, "${vName(vMain.accountId)} · Ordre de bourse", "chart")
		TransactionKind.ACQUISITION, TransactionKind.DISPOSAL -> SearchHit(inTx, vMain.amountCents, "${vName(vMain.accountId)} · ${propertyNature(inTx)}", "home")
		else                     ->
		{
			val vCategory = inTx.categoryId?.let { inCategories[it] }					// catégorie
			SearchHit(inTx, vMain.amountCents, "${vName(vMain.accountId)} · ${vCategory?.first ?: "Sans catégorie"}", vCategory?.second ?: "dots")
		}
	}
}

/*
	Vrai si un mot cherché apparaît dans l'opération : libellé, notes,
	détail (comptes, catégorie), ou montant égal à un montant tapé.
	inHit  : opération trouvée
	inWord : mot cherché, sans accents ni majuscules
*/
private fun matches(
	inHit  : SearchHit,
	inWord : String
): Boolean
{
	val vText = fold("${inHit.transaction.label} ${inHit.transaction.notes.orEmpty()} ${inHit.detail}")	// texte de l'opération
	if (vText.contains(inWord)) return true
	val vCents = if (inWord.any(Char::isDigit)) parseEuros(inWord) else null		// mot lu comme un montant
	return vCents != null && abs(vCents) == abs(inHit.amount)
}
