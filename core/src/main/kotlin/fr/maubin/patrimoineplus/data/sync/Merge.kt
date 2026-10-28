package fr.maubin.patrimoineplus.data.sync

import fr.maubin.patrimoineplus.data.backup.AccountDto
import fr.maubin.patrimoineplus.data.backup.AssetDto
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BudgetLineDto
import fr.maubin.patrimoineplus.data.backup.CategoryDto
import fr.maubin.patrimoineplus.data.backup.GoalDto
import fr.maubin.patrimoineplus.data.backup.LoanDto
import fr.maubin.patrimoineplus.data.backup.MonthGoalDto
import fr.maubin.patrimoineplus.data.backup.PriceDto
import fr.maubin.patrimoineplus.data.backup.RecurrenceDto
import fr.maubin.patrimoineplus.data.backup.StatementDto
import fr.maubin.patrimoineplus.data.backup.TransactionDto
import fr.maubin.patrimoineplus.domain.calc.kFamilyView
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.formatFrenchDate
import fr.maubin.patrimoineplus.domain.model.AccountType
import java.time.LocalDate

/*
	Version gardée pour un élément en conflit.
*/
enum class MergeSide
{
	MINE,																			// version de cet appareil
	THEIRS																			// version de l'autre appareil
}

/*
	Élément modifié différemment des deux côtés (ou supprimé d'un côté et
	modifié de l'autre) : l'utilisateur choisit la version à garder.
	key    : clé de l'élément (nature et identifiant), pour le choix
	kind   : nature, en clair (« Opération », « Compte »…)
	title  : nom de l'élément, en clair
	mine   : version de cet appareil, en clair ; null s'il l'a supprimé
	theirs : version de l'autre appareil, en clair ; null s'il l'a supprimé
*/
data class MergeConflict(
	val key    : String,
	val kind   : String,
	val title  : String,
	val mine   : String?,
	val theirs : String?
)

/*
	Résultat d'une fusion.
	file      : contenu fusionné ; pour un conflit sans choix, la version de cet
	            appareil est gardée en attendant
	conflicts : éléments en conflit sans choix
	restored  : éléments supprimés d'un côté mais rétablis, parce que des
	            éléments ajoutés ou modifiés de l'autre côté s'en servent, en clair
	merged    : nombre de changements repris de l'autre appareil (ajouts,
	            modifications, suppressions)
*/
data class MergeResult(
	val file      : BackupFile,
	val conflicts : List<MergeConflict>,
	val restored  : List<String>,
	val merged    : Int
)

/*
	Fusionne deux versions d'un contenu à partir de leur dernière version
	commune, élément par élément (identifiant) : un élément ajouté, modifié ou
	supprimé d'un seul côté prend ce changement ; un élément changé pareil des
	deux côtés est gardé ; un élément changé différemment des deux côtés (ou
	supprimé d'un côté et modifié de l'autre) est un conflit, tranché par
	inChoices ou laissé à l'utilisateur. Un élément supprimé d'un côté mais
	encore utilisé par un élément gardé (compte d'une opération ajoutée…) est
	rétabli. Les éléments sont comparés dans leur forme canonique (voir
	canonicalBackup), mais le résultat garde les éléments d'origine ; il est à
	vérifier (validateBackup) avant usage.
	inBase    : dernière version commune
	inMine    : version de cet appareil
	inTheirs  : version de l'autre appareil
	inChoices : versions choisies pour les conflits, par clé
*/
fun mergeBackups(
	inBase    : BackupFile,
	inMine    : BackupFile,
	inTheirs  : BackupFile,
	inChoices : Map<String, MergeSide> = emptyMap()
): MergeResult
{
	val vNames  = Names(inMine, inTheirs, inBase)										// noms pour les descriptions
	val vMerger = Merger(inChoices, inBase, inMine, inTheirs)							// fusion des listes
	val vFile   = inMine.copy(
		accounts     = vMerger.list("comptes", "Compte", { it.accounts }, { it.id }, { it.name }, vNames::account),
		categories   = vMerger.list("categories", "Catégorie", { it.categories }, { it.id }, { it.name }, vNames::category),
		assets       = vMerger.list("actifs", "Actif", { it.assets }, { it.id }, { it.name }, vNames::asset),
		transactions = vMerger.list("operations", "Opération", { it.transactions }, { it.id }, { it.label.ifBlank { "Opération" } }, vNames::transaction),
		statements   = vMerger.list("releves", "Relevé", { it.statements }, { it.id }, { "Relevé de ${vNames.accountName(it.accountId)}" }, vNames::statement),
		prices       = vMerger.list("cours", "Cours", { it.prices }, { it.id }, { "Cours de ${vNames.assetName(it.assetId)}" }, vNames::price),
		recurrences  = vMerger.list("recurrences", "Opération récurrente", { it.recurrences }, { it.id }, { it.label }, vNames::recurrence),
		loans        = vMerger.list("credits", "Crédit", { it.loans }, { it.id }, { "Crédit ${vNames.accountName(it.accountId)}" }, vNames::loan),
		profiles     = vMerger.list("profils", "Profil", { it.profiles }, { it.id }, { it.name }, { it.name }),
		themes       = vMerger.list("themes", "Thème", { it.themes }, { it.id }, { it.name }, { it.colors.joinToString(" ") }),
		budget       = vMerger.list("budget", "Ligne du budget", { it.budget }, { it.id }, { it.name.ifBlank { "Ligne sans nom" } }, vNames::budget),
		goals        = vMerger.list("objectifs", "Objectif", { it.goals }, { it.scope + "/" + it.id }, { it.title }, vNames::goal),
		monthGoals   = vMerger.list("mois", "Objectifs du mois", { months(it.monthGoals) }, { it.key }, { "Objectifs de ${it.key.substringAfter('/')}" }, { inPlan -> "Fixés le ${inPlan.rows.first().confirmed}, ${inPlan.rows.count { it.kind != null }} objectif(s)" }).flatMap { it.rows }
	)																					// fusion sans les rétablissements
	val vRestored = mutableListOf<String>()												// éléments rétablis
	val vJoined   = vFile.copy(history = joinHistory(inMine.history, inTheirs.history))	// historiques réunis
	return MergeResult(restore(vJoined, inMine, inTheirs, inBase, vRestored), vMerger.conflicts, vRestored, vMerger.merged)
}

/*
	Objectifs fixés pour un mois d'une vue, traités comme un seul élément.
	key  : vue et mois (« profil:moi/2026-10 »)
	rows : objectifs du mois
*/
private data class MonthRows(
	val key  : String,
	val rows : List<MonthGoalDto>
)

/*
	Objectifs du mois regroupés par vue et par mois.
	inRows : objectifs du mois
*/
private fun months(inRows: List<MonthGoalDto>): List<MonthRows> =
	inRows.groupBy { it.scope + "/" + it.month }.map { (inKey, inGroup) -> MonthRows(inKey, inGroup) }

/*
	Fusion des listes d'éléments, avec les conflits relevés. Les éléments sont
	comparés dans leur forme canonique (textes nettoyés, lignes triées), mais le
	résultat garde les éléments d'origine : l'ordre des lignes d'une opération
	compte pour ses règles (mensualité : compte payeur d'abord).
	inChoices : versions choisies pour les conflits, par clé
	inBase    : version commune
	inMine    : version de cet appareil
	inTheirs  : version de l'autre
*/
private class Merger(
	private val inChoices : Map<String, MergeSide>,
	private val inBase    : BackupFile,
	private val inMine    : BackupFile,
	private val inTheirs  : BackupFile
)
{
	private val fBase   = canonicalBackup(inBase)										// version commune, canonique
	private val fMine   = canonicalBackup(inMine)										// cet appareil, canonique
	private val fTheirs = canonicalBackup(inTheirs)										// l'autre, canonique

	// Conflits sans choix.
	val conflicts = mutableListOf<MergeConflict>()

	// Changements repris de l'autre appareil.
	var merged = 0

	/*
		Fusionne une liste : ordre de cet appareil, puis éléments ajoutés de
		l'autre côté dans leur ordre.
		inName     : nom de la liste (préfixe des clés)
		inKind     : nature des éléments, en clair
		inPick     : liste d'un contenu
		inKey      : identifiant d'un élément
		inTitle    : nom d'un élément, en clair
		inDescribe : description d'une version, en clair
	*/
	fun <T> list(
		inName     : String,
		inKind     : String,
		inPick     : (BackupFile) -> List<T>,
		inKey      : (T) -> String,
		inTitle    : (T) -> String,
		inDescribe : (T) -> String
	): List<T>
	{
		val vBase   = inPick(fBase).associateBy(inKey)										// version commune (comparaison)
		val vMine   = inPick(fMine).associateBy(inKey)										// cet appareil (comparaison)
		val vTheirs = inPick(fTheirs).associateBy(inKey)									// l'autre (comparaison)
		val vMineO  = inPick(inMine).associateBy(inKey)										// cet appareil (origine)
		val vTheirO = inPick(inTheirs).associateBy(inKey)									// l'autre (origine)
		val vOrder  = (inPick(inMine).map(inKey) + inPick(inTheirs).map(inKey) + inPick(inBase).map(inKey)).distinct()	// ordre du résultat
		return vOrder.mapNotNull { inId ->
			val vB = vBase[inId]																// version commune
			val vM = vMine[inId]																// cet appareil
			val vT = vTheirs[inId]																// l'autre
			when
			{
				vM == vT -> vMineO[inId]														// même résultat des deux côtés
				vM == vB -> vTheirO[inId].also { merged++ }									// changé de l'autre côté seulement
				vT == vB -> vMineO[inId]														// changé ici seulement
				else     ->
				{
					val vKey = "$inName/$inId"													// clé du conflit
					when (inChoices[vKey])
					{
						MergeSide.MINE   -> vMineO[inId]
						MergeSide.THEIRS -> vTheirO[inId].also { merged++ }
						null             ->
						{
							conflicts += MergeConflict(vKey, inKind, inTitle(vM ?: vT ?: vB!!), vM?.let(inDescribe), vT?.let(inDescribe))
							vMineO[inId]
						}
					}
				}
			}
		}
	}
}

/*
	Rétablit les éléments supprimés d'un côté mais encore utilisés par un
	élément gardé (compte, catégorie, actif, opération liée, profil
	propriétaire), dans la version la plus récente disponible (cet appareil,
	puis l'autre, puis la version commune), jusqu'à ce que plus rien ne manque.
	inFile    : contenu fusionné
	inMine    : version de cet appareil
	inTheirs  : version de l'autre
	inBase    : version commune
	inOut     : éléments rétablis, en clair
*/
private fun restore(
	inFile   : BackupFile,
	inMine   : BackupFile,
	inTheirs : BackupFile,
	inBase   : BackupFile,
	inOut    : MutableList<String>
): BackupFile
{
	var vFile = inFile																	// contenu en cours
	val vSides = listOf(inMine, inTheirs, inBase)										// où retrouver un élément
	repeat(10) {
		val vAccounts = vFile.accounts.map { it.id }.toSet()								// comptes présents
		val vCats     = vFile.categories.map { it.id }.toSet()								// catégories présentes
		val vAssets   = vFile.assets.map { it.id }.toSet()									// actifs présents
		val vTxs      = vFile.transactions.map { it.id }.toSet()							// opérations présentes
		val vProfiles = vFile.profiles.map { it.id }.toSet()								// profils présents
		val vNeedAcc  = (vFile.transactions.flatMap { inTx -> inTx.postings.map { it.accountId } } + vFile.statements.map { it.accountId } +
			vFile.recurrences.flatMap { listOfNotNull(it.accountId, it.toAccountId) } + vFile.loans.flatMap { listOf(it.accountId, it.payFrom) }).toSet() - vAccounts	// comptes manquants
		val vNeedCat  = (vFile.transactions.mapNotNull { it.categoryId } + vFile.recurrences.mapNotNull { it.categoryId } + vFile.loans.map { it.categoryId }).toSet() - vCats	// catégories manquantes
		val vNeedAst  = (vFile.transactions.flatMap { inTx -> inTx.trades.map { it.assetId } + listOfNotNull(inTx.assetId) } + vFile.prices.map { it.assetId }).toSet() - vAssets	// actifs manquants
		val vNeedTx   = (vFile.transactions.mapNotNull { it.linkedId } + vFile.loans.mapNotNull { it.propertyId }).toSet() - vTxs	// opérations manquantes
		val vNeedPro  = vFile.accounts.flatMap { inAccount -> inAccount.owners.map { it.profileId } }.toSet() - vProfiles	// profils manquants
		if (vNeedAcc.isEmpty() && vNeedCat.isEmpty() && vNeedAst.isEmpty() && vNeedTx.isEmpty() && vNeedPro.isEmpty()) return vFile
		val vAcc = vNeedAcc.mapNotNull { inId -> vSides.firstNotNullOfOrNull { inSide -> inSide.accounts.firstOrNull { it.id == inId } } }	// comptes rétablis
		val vCat = vNeedCat.mapNotNull { inId -> vSides.firstNotNullOfOrNull { inSide -> inSide.categories.firstOrNull { it.id == inId } } }	// catégories rétablies
		val vAst = vNeedAst.mapNotNull { inId -> vSides.firstNotNullOfOrNull { inSide -> inSide.assets.firstOrNull { it.id == inId } } }	// actifs rétablis
		val vTx  = vNeedTx.mapNotNull { inId -> vSides.firstNotNullOfOrNull { inSide -> inSide.transactions.firstOrNull { it.id == inId } } }	// opérations rétablies
		val vPro = vNeedPro.mapNotNull { inId -> vSides.firstNotNullOfOrNull { inSide -> inSide.profiles.firstOrNull { it.id == inId } } }	// profils rétablis
		if (vAcc.isEmpty() && vCat.isEmpty() && vAst.isEmpty() && vTx.isEmpty() && vPro.isEmpty()) return vFile		// introuvables : la vérification le dira
		inOut += vAcc.map { "Compte « ${it.name} »" } + vCat.map { "Catégorie « ${it.name} »" } + vAst.map { "Actif « ${it.name} »" } +
			vTx.map { "Opération « ${it.label} »" } + vPro.map { "Profil « ${it.name} »" }
		vFile = vFile.copy(
			accounts     = vFile.accounts + vAcc,
			categories   = vFile.categories + vCat,
			assets       = vFile.assets + vAst,
			transactions = vFile.transactions + vTx,
			profiles     = vFile.profiles + vPro
		)
	}
	return vFile
}

/*
	Noms et descriptions des éléments, d'après les trois versions (un élément
	supprimé d'un côté garde son nom).
	inMine   : version de cet appareil
	inTheirs : version de l'autre
	inBase   : version commune
*/
private class Names(
	inMine   : BackupFile,
	inTheirs : BackupFile,
	inBase   : BackupFile
)
{
	private val fAccounts = listOf(inBase, inTheirs, inMine).flatMap { it.accounts }.associate { it.id to it.name }	// comptes
	private val fCats     = listOf(inBase, inTheirs, inMine).flatMap { it.categories }.associate { it.id to it.name }	// catégories
	private val fAssets   = listOf(inBase, inTheirs, inMine).flatMap { it.assets }.associate { it.id to it.name }	// actifs
	private val fProfiles = listOf(inBase, inTheirs, inMine).flatMap { it.profiles }.associate { it.id to it.name }	// profils

	/*
		Nom d'un compte.
		inId : identifiant
	*/
	fun accountName(inId: String): String = fAccounts[inId] ?: "compte supprimé"

	/*
		Nom d'un actif.
		inId : identifiant
	*/
	fun assetName(inId: String): String = fAssets[inId] ?: "actif supprimé"

	/*
		Date en clair.
		inIso : date AAAA-MM-JJ
	*/
	private fun day(inIso: String): String = runCatching { formatFrenchDate(LocalDate.parse(inIso)) }.getOrDefault(inIso)

	/*
		Description d'un compte.
		inAccount : compte
	*/
	fun account(inAccount: AccountDto): String =
		listOfNotNull(
			inAccount.name,
			runCatching { AccountType.valueOf(inAccount.type).label }.getOrDefault(inAccount.type),
			inAccount.institution,
			if (inAccount.sharePercent != 100) "part ${inAccount.sharePercent} %" else null,
			inAccount.owners.takeIf { it.size > 1 }?.joinToString(", ") { "${fProfiles[it.profileId] ?: "?"} ${it.percent} %" },
			if (inAccount.archived) "archivé" else null
		).joinToString(" · ")

	/*
		Description d'une catégorie.
		inCategory : catégorie
	*/
	fun category(inCategory: CategoryDto): String =
		listOfNotNull(inCategory.name, if (inCategory.kind == "INCOME") "revenu" else "dépense", if (inCategory.archived) "archivée" else null).joinToString(" · ")

	/*
		Description d'un actif.
		inAsset : actif
	*/
	fun asset(inAsset: AssetDto): String = listOfNotNull(inAsset.name, inAsset.isin, inAsset.ticker, if (inAsset.archived) "archivé" else null).joinToString(" · ")

	/*
		Description d'une opération : date, libellé, montants par compte,
		catégorie, notes.
		inTx : opération
	*/
	fun transaction(inTx: TransactionDto): String =
		listOfNotNull(
			day(inTx.date),
			inTx.label.ifBlank { null },
			inTx.postings.joinToString(", ") { "${formatEuros(it.amountCents)} (${accountName(it.accountId)})" }.ifEmpty { null },
			inTx.trades.takeIf { it.isNotEmpty() }?.joinToString(", ") { "${if (it.side == "BUY") "achat" else "vente"} ${assetName(it.assetId)}" },
			inTx.categoryId?.let { fCats[it] },
			inTx.notes
		).joinToString(" · ")

	/*
		Description d'un relevé.
		inStatement : relevé
	*/
	fun statement(inStatement: StatementDto): String = "${day(inStatement.date)} · ${formatEuros(inStatement.balanceCents)}"

	/*
		Description d'un cours.
		inPrice : cours
	*/
	fun price(inPrice: PriceDto): String = "${day(inPrice.date)} · ${inPrice.unitPrice / 10_000.0} €"

	/*
		Description d'une opération récurrente.
		inRec : opération récurrente
	*/
	fun recurrence(inRec: RecurrenceDto): String =
		listOfNotNull(inRec.label, formatEuros(inRec.amountCents), accountName(inRec.accountId), "dès le ${day(inRec.start)}", inRec.end?.let { "jusqu’au ${day(it)}" }).joinToString(" · ")

	/*
		Description d'un crédit.
		inLoan : crédit
	*/
	fun loan(inLoan: LoanDto): String = "${formatEuros(inLoan.principal)} sur ${inLoan.months} mois · ${inLoan.rateBp / 100.0} % · 1re mensualité le ${day(inLoan.firstDue)}"

	/*
		Description d'une ligne du budget.
		inLine : ligne
	*/
	fun budget(inLine: BudgetLineDto): String =
		listOfNotNull(inLine.name.ifBlank { null }, formatEuros(inLine.amount), inLine.profileId?.let { fProfiles[it] }).joinToString(" · ")

	/*
		Description d'un objectif.
		inGoal : objectif
	*/
	fun goal(inGoal: GoalDto): String =
		listOfNotNull(
			inGoal.title,
			if (inGoal.scope == kFamilyView) "Famille" else fProfiles[inGoal.scope],
			if (inGoal.kind == "TASK") (if (inGoal.done) "fait" else "à faire") else if (inGoal.kind == "RATE") "${inGoal.target / 10.0} %" else formatEuros(inGoal.target),
			inGoal.deadline?.let { "échéance ${day(it)}" },
			inGoal.note.ifBlank { null }
		).joinToString(" · ")
}
