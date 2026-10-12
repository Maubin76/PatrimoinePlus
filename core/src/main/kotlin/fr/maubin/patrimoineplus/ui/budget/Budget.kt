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
	group      : thème qui regroupe des lignes dans le schéma (« Logement »,
	             « Vie quotidienne ») ; vide : la ligne forme son propre thème
	             (investissements : « Investissements »)
*/
@Serializable
data class BudgetLine(
	val id         : String,
	val section    : BudgetSection,
	val name       : String,
	val amount     : Long,
	val categoryId : String? = null,
	val accountId  : String? = null,
	val group      : String  = ""
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
	Thème des sorties : un nœud du schéma, puis le détail de ses lignes.
	name   : nom du thème
	kind   : rubrique (investissements ou dépenses) ; null pour le reste
	items  : lignes du thème, de la plus forte à la plus faible ; vide si le
	         thème n'a qu'une ligne du même nom (pas de détail à montrer)
	amount : total du thème
*/
data class CashGroup(
	val name   : String,
	val kind   : BudgetSection?,
	val items  : List<CashFlow>,
	val amount : Long
)

/*
	Diagramme de cashflow sur quatre colonnes : entrées (et déficit s'il
	faut équilibrer) → total du mois → thèmes des sorties (investissements
	d'abord, puis dépenses de la plus forte à la plus faible, puis reste) →
	détail de chaque thème.
	sources : flux entrants
	groups  : thèmes des sorties, dans l'ordre du schéma
	total   : montant qui traverse le budget
	deficit : vrai si les sorties dépassent les entrées
*/
data class CashFlowDiagram(
	val sources : List<CashFlow>,
	val groups  : List<CashGroup>,
	val total   : Long,
	val deficit : Boolean
)
{
	// Sorties à plat (détail des thèmes, ou thème seul), dans l'ordre du schéma.
	val outputs: List<CashFlow> get() = groups.flatMap { inGroup -> inGroup.items.ifEmpty { listOf(CashFlow(inGroup.name, inGroup.amount, inGroup.kind)) } }
}

/*
	Construit le diagramme de cashflow : prévu (inActual faux) ou réel du
	mois (lignes suivies seulement). Lignes nulles ignorées. Les
	investissements forment le premier thème (« Investissements » si leurs
	lignes n'en précisent pas) ; les dépenses sont regroupées par thème, une
	ligne sans thème formant le sien ; le reste ferme la marche.
	inView   : budget comparé
	inActual : vrai pour le réel du mois
*/
fun cashFlow(
	inView   : BudgetView,
	inActual : Boolean
): CashFlowDiagram
{
	val vAmount = { inLine: BudgetLineView -> if (inActual) (inLine.actual ?: 0L) else inLine.line.amount }	// montant retenu
	val vLines  = { inSection: BudgetSection -> inView.section(inSection).filter { vAmount(it) > 0 } }	// lignes non nulles d'une rubrique
	val vFlow   = { inLine: BudgetLineView -> CashFlow(inLine.line.name.ifBlank { inLine.line.section.label }, vAmount(inLine), inLine.line.section) }	// flux d'une ligne
	val vGroups = { inSection: BudgetSection, inDefault: String? ->
		vLines(inSection)
			.groupBy { inLine -> inLine.line.group.trim().ifEmpty { inDefault ?: "\u0000${inLine.line.id}" } }
			.map { (inKey, inMembers) ->
				val vItems = inMembers.map(vFlow).sortedByDescending { it.amount }			// lignes du thème
				val vName  = if (inKey.startsWith("\u0000")) vItems.single().name else inKey	// nom du thème
				CashGroup(vName, inSection, if (vItems.size == 1 && vItems.single().name == vName) emptyList() else vItems, vItems.sumOf { it.amount })
			}
			.sortedByDescending { it.amount }
	}																					// thèmes d'une rubrique
	val vIn      = vLines(BudgetSection.INCOME).map(vFlow).sortedByDescending { it.amount }	// entrées
	val vOut     = vGroups(BudgetSection.INVESTMENT, BudgetSection.INVESTMENT.label) + vGroups(BudgetSection.EXPENSE, null)	// sorties par thème
	val vInSum   = vIn.sumOf { it.amount }												// total des entrées
	val vOutSum  = vOut.sumOf { it.amount }												// total des sorties
	return CashFlowDiagram(
		sources = if (vOutSum > vInSum) vIn + CashFlow("Déficit", vOutSum - vInSum, null) else vIn,
		groups  = if (vInSum > vOutSum) vOut + CashGroup("Reste", null, emptyList(), vInSum - vOutSum) else vOut,
		total   = maxOf(vInSum, vOutSum),
		deficit = vOutSum > vInSum
	)
}
