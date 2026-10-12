package fr.maubin.patrimoineplus.ui.budget

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.YearMonth

private val kJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }	// format du fichier du budget

/*
	Rubrique d'une ligne de budget.
	label : titre de la rubrique
*/
enum class BudgetSection(val label: String)
{
	INCOME("Entrées"),															// salaire, autres revenus
	EXPENSE("Dépenses"),														// dépenses par type
	INVESTMENT("Investissements")												// versements vers l'épargne et la bourse
}

/*
	Ligne du budget prévisionnel mensuel.
	id         : identifiant
	section    : rubrique
	name       : nom libre
	amount     : montant mensuel prévu, en centimes (positif)
	categoryId : catégorie suivie (entrées, dépenses) pour comparer au réel ; null sinon
	accountId  : compte suivi (investissements : versements reçus) ; null sinon
*/
@Serializable
data class BudgetLine(
	val id         : String,
	val section    : BudgetSection,
	val name       : String,
	val amount     : Long,
	val categoryId : String? = null,
	val accountId  : String? = null
)

/*
	Budget prévisionnel mensuel (enregistré sur le PC).
	lines : lignes, dans l'ordre de saisie
*/
@Serializable
data class Budget(val lines: List<BudgetLine> = emptyList())
{
	/*
		Total prévu d'une rubrique.
		inSection : rubrique
	*/
	fun total(inSection: BudgetSection): Long = lines.filter { it.section == inSection }.sumOf { it.amount }

	// Reste prévu : entrées − dépenses − investissements (négatif = déficit).
	val remaining: Long get() = total(BudgetSection.INCOME) - total(BudgetSection.EXPENSE) - total(BudgetSection.INVESTMENT)
}

/*
	Budget en texte (fichier du PC).
	inBudget : budget
*/
fun encodeBudget(inBudget: Budget): String = kJson.encodeToString(Budget.serializer(), inBudget)

/*
	Budget lu d'un texte ; vide s'il est illisible.
	inText : contenu du fichier
*/
fun decodeBudget(inText: String): Budget = runCatching { kJson.decodeFromString(Budget.serializer(), inText) }.getOrDefault(Budget())

/*
	Ligne du budget comparée au réel d'un mois.
	line    : ligne
	actual  : réel du mois ; null pour une ligne non suivie
*/
data class BudgetLineView(
	val line   : BudgetLine,
	val actual : Long?
)
{
	// Écart réel − prévu ; null pour une ligne non suivie.
	val gap: Long? get() = actual?.let { it - line.amount }
}

/*
	Budget comparé au réel d'un mois.
	month    : mois comparé
	lines    : lignes, avec leur réel
	budget   : budget
*/
data class BudgetView(
	val month  : YearMonth,
	val lines  : List<BudgetLineView>,
	val budget : Budget
)
{
	/*
		Lignes d'une rubrique.
		inSection : rubrique
	*/
	fun section(inSection: BudgetSection): List<BudgetLineView> = lines.filter { it.line.section == inSection }

	/*
		Réel d'une rubrique (lignes suivies seulement).
		inSection : rubrique
	*/
	fun actualTotal(inSection: BudgetSection): Long = section(inSection).sumOf { it.actual ?: 0L }

	// Reste réel des lignes suivies.
	val actualRemaining: Long get() = actualTotal(BudgetSection.INCOME) - actualTotal(BudgetSection.EXPENSE) - actualTotal(BudgetSection.INVESTMENT)
}

/*
	Compare chaque ligne au réel d'un mois : entrées et dépenses par leur
	catégorie (tous comptes, remboursements déduits), investissements par
	les versements reçus sur leur compte (virements entrants moins sortants,
	opérations à venir comprises dans le mois). Fonction pure.
	inBudget : budget
	inData   : données
	inMonth  : mois comparé
*/
fun compareBudget(
	inBudget : Budget,
	inData   : DataSet,
	inMonth  : YearMonth
): BudgetView
{
	val vMonthTxs = inData.transactions.filter { YearMonth.from(it.date) == inMonth }	// opérations du mois
	val vKinds    = inData.categories.associate { it.id to it.kind }					// nature des catégories
	val vActual   = { inLine: BudgetLine ->
		when
		{
			inLine.section != BudgetSection.INVESTMENT && inLine.categoryId != null ->
			{
				val vSign = if (vKinds[inLine.categoryId] == CategoryKind.EXPENSE) -1L else 1L	// une dépense compte en positif
				vSign * vMonthTxs.filter { it.kind == TransactionKind.STANDARD && it.categoryId == inLine.categoryId }.sumOf { inTx -> inTx.postings.sumOf { it.amountCents } }
			}
			inLine.section == BudgetSection.INVESTMENT && inLine.accountId != null ->
				vMonthTxs.filter { it.kind == TransactionKind.TRANSFER }.sumOf { inTx -> inTx.postings.filter { it.accountId == inLine.accountId }.sumOf { it.amountCents } }
			else -> null
		}
	}																				// réel d'une ligne
	return BudgetView(inMonth, inBudget.lines.map { BudgetLineView(it, vActual(it)) }, inBudget)
}

/*
	Flux d'un diagramme de cashflow.
	name   : nom affiché
	amount : montant (positif)
	kind   : rubrique ; null pour le reste ou le déficit
*/
data class CashFlow(
	val name   : String,
	val amount : Long,
	val kind   : BudgetSection?
)

/*
	Diagramme de cashflow : sources (entrées, et déficit s'il faut l'équilibrer)
	vers le total du mois, puis vers les sorties (dépenses, investissements,
	et reste s'il est positif). La somme des sources égale celle des sorties.
	sources : flux entrants
	outputs : flux sortants
	total   : montant qui traverse le budget
	deficit : vrai si les sorties dépassent les entrées
*/
data class CashFlowDiagram(
	val sources : List<CashFlow>,
	val outputs : List<CashFlow>,
	val total   : Long,
	val deficit : Boolean
)

/*
	Construit le diagramme de cashflow à partir de montants par ligne : prévu
	(inActual faux) ou réel du mois (lignes suivies seulement). Lignes nulles
	ignorées ; sorties triées par rubrique puis par montant décroissant.
	inView   : budget comparé
	inActual : vrai pour le réel du mois
*/
fun cashFlow(
	inView   : BudgetView,
	inActual : Boolean
): CashFlowDiagram
{
	val vAmount  = { inLine: BudgetLineView -> if (inActual) (inLine.actual ?: 0L) else inLine.line.amount }	// montant retenu
	val vFlows   = { inSection: BudgetSection -> inView.section(inSection).map { CashFlow(it.line.name.ifBlank { inSection.label }, vAmount(it), inSection) }.filter { it.amount > 0 }.sortedByDescending { it.amount } }	// flux d'une rubrique
	val vIn      = vFlows(BudgetSection.INCOME)											// entrées
	val vOut     = vFlows(BudgetSection.EXPENSE) + vFlows(BudgetSection.INVESTMENT)		// sorties
	val vInSum   = vIn.sumOf { it.amount }												// total des entrées
	val vOutSum  = vOut.sumOf { it.amount }												// total des sorties
	return CashFlowDiagram(
		sources = if (vOutSum > vInSum) vIn + CashFlow("Déficit", vOutSum - vInSum, null) else vIn,
		outputs = if (vInSum > vOutSum) vOut + CashFlow("Reste", vInSum - vOutSum, null) else vOut,
		total   = maxOf(vInSum, vOutSum),
		deficit = vOutSum > vInSum
	)
}
