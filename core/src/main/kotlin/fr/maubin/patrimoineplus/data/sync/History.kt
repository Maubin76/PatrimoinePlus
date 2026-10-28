package fr.maubin.patrimoineplus.data.sync

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.HistoryDto

const val kHistoryKept = 20															// modifications gardées dans l'historique

/*
	Auteur des modifications envoyées par cet appareil.
	id   : identifiant de l'appareil
	name : nom de l'appareil, en clair (« PC du bureau »)
*/
data class HistoryAuthor(
	val id   : String,
	val name : String
)

/*
	Nombre d'éléments changés entre deux versions.
	added    : éléments ajoutés
	modified : éléments modifiés
	deleted  : éléments supprimés
*/
data class ChangeCount(
	val added    : Int,
	val modified : Int,
	val deleted  : Int
)
{
	// Vrai si rien n'a changé.
	val isEmpty: Boolean get() = added == 0 && modified == 0 && deleted == 0

	// Changements en clair (« 3 ajouts, 1 modification »).
	val label: String
		get() = listOfNotNull(
			plural(added, "ajout"), plural(modified, "modification"), plural(deleted, "suppression")
		).joinToString(", ").ifEmpty { "aucun changement" }

	/*
		Nombre et nom, au pluriel si besoin ; null pour zéro.
		inCount : nombre
		inWord  : nom au singulier
	*/
	private fun plural(
		inCount : Int,
		inWord  : String
	): String? = if (inCount == 0) null else "$inCount $inWord${if (inCount > 1) "s" else ""}"
}

/*
	Éléments ajoutés, modifiés et supprimés d'une version à l'autre (forme
	canonique, voir canonicalBackup ; l'historique n'est pas compté).
	inBefore : version de départ
	inAfter  : version d'arrivée
*/
fun countChanges(
	inBefore : BackupFile,
	inAfter  : BackupFile
): ChangeCount
{
	val vBefore = canonicalBackup(inBefore)												// départ
	val vAfter  = canonicalBackup(inAfter)												// arrivée
	var vAdded  = 0																		// ajouts
	var vEdited = 0																		// modifications
	var vGone   = 0																		// suppressions
	/*
		Compte les changements d'une liste.
		inOld : avant, par clé
		inNew : après, par clé
	*/
	fun count(
		inOld : Map<String, Any>,
		inNew : Map<String, Any>
	)
	{
		inNew.forEach { (inKey, inValue) -> when (inOld[inKey]) { null -> vAdded++; inValue -> Unit; else -> vEdited++ } }
		vGone += inOld.keys.count { it !in inNew }
	}
	count(vBefore.accounts.associateBy { it.id }, vAfter.accounts.associateBy { it.id })
	count(vBefore.categories.associateBy { it.id }, vAfter.categories.associateBy { it.id })
	count(vBefore.assets.associateBy { it.id }, vAfter.assets.associateBy { it.id })
	count(vBefore.transactions.associateBy { it.id }, vAfter.transactions.associateBy { it.id })
	count(vBefore.statements.associateBy { it.id }, vAfter.statements.associateBy { it.id })
	count(vBefore.prices.associateBy { it.id }, vAfter.prices.associateBy { it.id })
	count(vBefore.recurrences.associateBy { it.id }, vAfter.recurrences.associateBy { it.id })
	count(vBefore.loans.associateBy { it.id }, vAfter.loans.associateBy { it.id })
	count(vBefore.profiles.associateBy { it.id }, vAfter.profiles.associateBy { it.id })
	count(vBefore.themes.associateBy { it.id }, vAfter.themes.associateBy { it.id })
	count(vBefore.budget.associateBy { it.id }, vAfter.budget.associateBy { it.id })
	count(vBefore.goals.associateBy { it.scope + "/" + it.id }, vAfter.goals.associateBy { it.scope + "/" + it.id })
	count(vBefore.monthGoals.groupBy { it.scope + "/" + it.month }, vAfter.monthGoals.groupBy { it.scope + "/" + it.month })
	return ChangeCount(vAdded, vEdited, vGone)
}

/*
	Historiques réunis : chaque modification une fois (appareil, instant et
	changements identiques),
	de la plus récente à la plus ancienne, les plus récentes gardées.
	inLists : historiques
*/
fun joinHistory(vararg inLists: List<HistoryDto>): List<HistoryDto> =
	inLists.flatMap { it }.distinct().sortedByDescending { it.at }.take(kHistoryKept)

/*
	Contenu complété d'une entrée d'historique pour les changements de cet
	appareil depuis la dernière version commune ; inchangé s'il n'y a pas
	d'auteur ou rien de neuf.
	inFile   : contenu envoyé
	inBase   : dernière version commune ; null avant la première synchronisation
	inAuthor : auteur ; null : pas d'historique
	inNow    : instant (ms)
*/
fun withAuthor(
	inFile   : BackupFile,
	inBase   : BackupFile?,
	inAuthor : HistoryAuthor?,
	inNow    : Long
): BackupFile
{
	val vAuthor = inAuthor ?: return inFile												// auteur
	val vCount  = if (inBase == null) null else countChanges(inBase, inFile)				// changements de cet appareil
	if (vCount != null && vCount.isEmpty) return inFile
	val vEntry  = HistoryDto(vAuthor.id, vAuthor.name, inNow, vCount?.label ?: "première synchronisation")	// entrée
	return inFile.copy(history = joinHistory(listOf(vEntry), inFile.history))
}
