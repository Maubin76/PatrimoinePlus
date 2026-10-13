package fr.maubin.patrimoineplus.ui.budget

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.model.AccountType
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
	Thème d'une dépense du budget : regroupe les dépenses dans le schéma, et
	donne leur réel du mois par les catégories d'opérations qui lui
	correspondent (voir themeOfCategory).
	label : nom affiché
*/
enum class ExpenseTheme(val label: String)
{
	DAILY("Vie quotidienne"),														// alimentation, santé, loisirs… : toutes les autres catégories
	SUBSCRIPTIONS("Abonnements"),													// catégorie « Abonnements »
	TRANSPORT("Transports"),														// catégorie « Transport(s) »
	HOUSING("Logement"),															// catégorie « Logement »
	CREDIT("Crédit")																// catégorie « Crédit », « Prêt » ou « Emprunt »
}

/*
	Thème d'une catégorie de dépense, d'après son nom (sans accents ni
	majuscules, singulier ou pluriel) : Logement, Transport(s), Abonnement(s),
	Crédit / Prêt / Emprunt ; toute autre catégorie relève de la vie
	quotidienne.
	inName : nom de la catégorie
*/
fun themeOfCategory(inName: String): ExpenseTheme
{
	val vKey   = java.text.Normalizer.normalize(inName.trim().lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")	// nom sans accents
	val vFirst = vKey.split(Regex("[^a-z]+")).firstOrNull { it.isNotEmpty() }.orEmpty()	// premier mot
	val vLoan  = vFirst in setOf("credit", "credits", "pret", "prets", "emprunt", "emprunts") && !Regex("^pret[^a-z]+a[^a-z]+porter").containsMatchIn(vKey)	// crédit, prêt, emprunt (pas « prêt-à-porter »)
	return when
	{
		vKey.startsWith("logement")                                                     -> ExpenseTheme.HOUSING
		vKey.startsWith("transport")                                                    -> ExpenseTheme.TRANSPORT
		vKey.startsWith("abonnement")                                                   -> ExpenseTheme.SUBSCRIPTIONS
		vLoan                                                                           -> ExpenseTheme.CREDIT
		else                                                                            -> ExpenseTheme.DAILY
	}
}

/*
	Ligne du budget prévisionnel mensuel.
	id         : identifiant
	section    : rubrique
	name       : nom libre
	amount     : montant mensuel prévu, en centimes (positif)
	categoryId : catégorie suivie (entrées) pour comparer au réel ; null sinon.
	             Ancien budget : catégorie d'une dépense, qui en donne le thème
	accountId  : compte suivi (investissements : versements reçus) ; null sinon
	theme      : thème d'une dépense ; null pour une entrée, un investissement,
	             ou une dépense d'un ancien budget (thème tiré de sa catégorie)
*/
@Serializable
data class BudgetLine(
	val id         : String,
	val section    : BudgetSection,
	val name       : String,
	val amount     : Long,
	val categoryId : String? = null,
	val accountId  : String? = null,
	val theme      : ExpenseTheme? = null
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
	line   : ligne
	actual : réel du mois ; null pour une ligne non suivie, et pour une
	         dépense (son réel est celui de son thème, voir ThemeView)
	theme  : thème d'une dépense ; null pour une entrée ou un investissement
*/
data class BudgetLineView(
	val line   : BudgetLine,
	val actual : Long?,
	val theme  : ExpenseTheme? = null
)
{
	// Écart réel − prévu ; null pour une ligne non suivie.
	val gap: Long? get() = actual?.let { it - line.amount }
}

/*
	Thème de dépenses du budget comparé au réel du mois.
	theme  : thème
	lines  : lignes de dépense du thème, dans l'ordre de saisie
	actual : dépenses nettes du mois des catégories du thème (remboursements déduits)
*/
data class ThemeView(
	val theme  : ExpenseTheme,
	val lines  : List<BudgetLineView>,
	val actual : Long
)
{
	// Total prévu du thème.
	val planned: Long get() = lines.sumOf { it.line.amount }

	// Écart réel − prévu.
	val gap: Long get() = actual - planned
}

/*
	Budget comparé au réel d'un mois.
	month    : mois comparé
	lines    : lignes, avec leur réel
	budget   : budget
	themes   : thèmes de dépenses qui ont au moins une ligne, dans l'ordre des thèmes
*/
data class BudgetView(
	val month  : YearMonth,
	val lines  : List<BudgetLineView>,
	val budget : Budget,
	val themes : List<ThemeView> = emptyList()
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
	fun actualTotal(inSection: BudgetSection): Long =
		if (inSection == BudgetSection.EXPENSE) themes.sumOf { it.actual } else section(inSection).sumOf { it.actual ?: 0L }

	// Reste réel des lignes suivies.
	val actualRemaining: Long get() = actualTotal(BudgetSection.INCOME) - actualTotal(BudgetSection.EXPENSE) - actualTotal(BudgetSection.INVESTMENT)
}

/*
	Compare chaque ligne au réel d'un mois : entrées par leur catégorie
	(tous comptes), dépenses par thème (catégories du thème, tous comptes,
	remboursements déduits ; le thème Crédit compte aussi le capital
	remboursé, virements vers un compte Crédit), investissements par
	les versements reçus sur leur compte (virements entrants moins sortants,
	achats de biens pour un compte Immobilier ou Collection,
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
	val vNames    = inData.categories.associate { it.id to it.name }					// nom des catégories
	val vThemeOf  = { inLine: BudgetLine -> inLine.theme ?: inLine.categoryId?.let(vNames::get)?.let(::themeOfCategory) ?: ExpenseTheme.DAILY }	// thème d'une dépense
	val vActual   = { inLine: BudgetLine ->
		when
		{
			inLine.section == BudgetSection.EXPENSE                                   -> null
			inLine.section == BudgetSection.INCOME && inLine.categoryId != null ->
			{
				val vSign = if (vKinds[inLine.categoryId] == CategoryKind.EXPENSE) -1L else 1L	// une dépense compte en positif
				vSign * vMonthTxs.filter { it.hasCategory && it.categoryId == inLine.categoryId }.sumOf { it.categoryAmount() }
			}
			inLine.section == BudgetSection.INVESTMENT && inLine.accountId != null ->
				vMonthTxs.filter { it.kind == TransactionKind.TRANSFER || it.kind == TransactionKind.ACQUISITION }.sumOf { inTx -> inTx.postings.filter { it.accountId == inLine.accountId }.sumOf { it.amountCents } }
			else -> null
		}
	}																				// réel d'une ligne
	val vViews  = inBudget.lines.map { BudgetLineView(it, vActual(it), if (it.section == BudgetSection.EXPENSE) vThemeOf(it) else null) }	// lignes comparées
	val vSpent  = vMonthTxs
		.filter { it.hasCategory && vKinds[it.categoryId] == CategoryKind.EXPENSE }
		.groupBy { themeOfCategory(vNames.getValue(it.categoryId!!)) }
		.mapValues { (_, inTxs) -> -inTxs.sumOf { it.categoryAmount() } }
		.toMutableMap()																// dépenses nettes du mois par thème
	val vLoans  = inData.accounts.filter { it.type == AccountType.LOAN }.map { it.id }.toSet()	// comptes Crédit
	val vTracked = inBudget.lines.filter { it.section == BudgetSection.INVESTMENT }.mapNotNull { it.accountId }.toSet()	// comptes suivis en investissement (comptés là, pas ici)
	val vRepaid = vMonthTxs.filter { it.kind == TransactionKind.TRANSFER || it.kind == TransactionKind.LOAN_PAYMENT }.sumOf { inTx -> inTx.postings.filter { it.accountId in vLoans && it.accountId !in vTracked && it.amountCents > 0 }.sumOf { it.amountCents } }	// capital remboursé dans le mois
	if (vRepaid != 0L) vSpent[ExpenseTheme.CREDIT] = (vSpent[ExpenseTheme.CREDIT] ?: 0L) + vRepaid
	val vThemes = ExpenseTheme.entries.mapNotNull { inTheme ->
		vViews.filter { it.theme == inTheme }.takeIf { it.isNotEmpty() }?.let { ThemeView(inTheme, it, vSpent[inTheme] ?: 0L) }
	}																				// thèmes du budget
	return BudgetView(inMonth, vViews, inBudget, vThemes)
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
	mois (lignes et thèmes suivis seulement). Lignes nulles ignorées. Les
	investissements forment le premier thème (« Investissements ») ; les
	dépenses sont regroupées par thème (prévu : détail des lignes ; réel :
	thème seul, son réel ne se répartit pas entre ses lignes) ; le reste
	ferme la marche.
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
			.groupBy { inLine -> inDefault ?: inLine.theme?.label ?: "\u0000${inLine.line.id}" }
			.map { (inKey, inMembers) ->
				val vItems = inMembers.map(vFlow).sortedByDescending { it.amount }			// lignes du thème
				val vName  = if (inKey.startsWith("\u0000")) vItems.single().name else inKey	// nom du thème
				CashGroup(vName, inSection, if (vItems.size == 1 && vItems.single().name == vName) emptyList() else vItems, vItems.sumOf { it.amount })
			}
			.sortedByDescending { it.amount }
	}																					// thèmes d'une rubrique
	val vIn      = vLines(BudgetSection.INCOME).map(vFlow).sortedByDescending { it.amount }	// entrées
	val vSpend   = if (inActual) inView.themes.filter { it.actual > 0 }.map { CashGroup(it.theme.label, BudgetSection.EXPENSE, emptyList(), it.actual) }.sortedByDescending { it.amount }
		else vGroups(BudgetSection.EXPENSE, null)										// dépenses par thème
	val vOut     = vGroups(BudgetSection.INVESTMENT, BudgetSection.INVESTMENT.label) + vSpend	// sorties par thème
	val vInSum   = vIn.sumOf { it.amount }												// total des entrées
	val vOutSum  = vOut.sumOf { it.amount }												// total des sorties
	return CashFlowDiagram(
		sources = if (vOutSum > vInSum) vIn + CashFlow("Déficit", vOutSum - vInSum, null) else vIn,
		groups  = if (vInSum > vOutSum) vOut + CashGroup("Reste", null, emptyList(), vInSum - vOutSum) else vOut,
		total   = maxOf(vInSum, vOutSum),
		deficit = vOutSum > vInSum
	)
}
