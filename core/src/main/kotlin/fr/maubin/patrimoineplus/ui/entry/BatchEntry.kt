package fr.maubin.patrimoineplus.ui.entry

import fr.maubin.patrimoineplus.domain.calc.fold
import fr.maubin.patrimoineplus.domain.calc.labelSuggestions
import fr.maubin.patrimoineplus.domain.format.parseFrenchDate
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.Transaction
import fr.maubin.patrimoineplus.domain.model.TransactionKind

/*
	Ligne de la saisie en grille (appli PC), telle que tapée.
	key        : identifiant de la ligne dans la grille
	date       : date (JJ/MM/AAAA)
	accountId  : compte
	label      : libellé
	categoryId : catégorie choisie ; null : catégorie proposée d'après le libellé
	amount     : montant ; sans signe = dépense, « + » devant = entrée (revenu,
	             ou remboursement dans une catégorie de dépense)
*/
data class BatchRow(
	val key        : Long,
	val date       : String,
	val accountId  : String?,
	val label      : String  = "",
	val categoryId : String? = null,
	val amount     : String  = ""
)
{
	// Vrai pour une ligne laissée vide (ignorée).
	val isBlank: Boolean get() = label.isBlank() && amount.isBlank()
}

/*
	Résultat du contrôle d'une ligne : saisie prête à enregistrer, ou erreur.
	form  : saisie construite ; null si la ligne est refusée
	error : motif du refus ; null si la ligne est valable
*/
data class BatchCheck(
	val form  : EntryForm?,
	val error : String?
)

/*
	Catégorie proposée pour un libellé : celle de la dernière opération au
	même libellé (sans accents ni majuscules), sinon celle de la première
	suggestion de libellé ; null sans historique.
	inLabel        : libellé tapé
	inTransactions : opérations connues
*/
fun proposeCategory(
	inLabel        : String,
	inTransactions : List<Transaction>
): String?
{
	val vKey = fold(inLabel)																// libellé normalisé
	if (vKey.isEmpty()) return null
	return inTransactions
		.filter { it.kind == TransactionKind.STANDARD && it.categoryId != null && fold(it.label) == vKey }
		.maxByOrNull { it.date }?.categoryId
		?: labelSuggestions(inTransactions, inLabel, 1).firstOrNull()?.categoryId
}

/*
	Contrôle une ligne de la grille et construit sa saisie (mêmes contrôles
	qu'une saisie ordinaire, checkEntry). La catégorie vide prend la catégorie
	proposée ; sa nature fixe le sens : revenu pour une catégorie de revenu,
	dépense sinon, remboursement si le montant commence par « + ».
	inRow  : ligne
	inData : données de saisie
*/
fun checkBatchRow(
	inRow  : BatchRow,
	inData : EntryData
): BatchCheck
{
	val vDate     = parseFrenchDate(inRow.date) ?: return BatchCheck(null, "Date illisible (JJ/MM/AAAA).")	// date lue
	val vCategory = inRow.categoryId ?: proposeCategory(inRow.label, inData.transactions)	// catégorie retenue
	val vKind     = inData.categories.firstOrNull { it.id == vCategory }?.kind			// nature de la catégorie
	val vPlus     = inRow.amount.trim().startsWith("+")									// entrée demandée
	val vForm     = EntryForm(
		mode       = if (vKind == CategoryKind.INCOME) EntryMode.INCOME else EntryMode.EXPENSE,
		amount     = inRow.amount.trim().removePrefix("+").removePrefix("-").trim(),
		accountId  = inRow.accountId,
		categoryId = vCategory,
		refund     = vKind != CategoryKind.INCOME && vPlus,
		label      = inRow.label.trim(),
		date       = vDate
	)																						// saisie construite
	val vErrors   = checkEntry(vForm, inData)													// contrôles
	if (vErrors.isEmpty)
	{
		return BatchCheck(vForm, null)
	}
	return BatchCheck(null, listOfNotNull(vErrors.amount, vErrors.account, vErrors.category ?: if (vCategory == null) "Choisissez une catégorie." else null, vErrors.label, vErrors.general).distinct().joinToString(" "))
}
