package fr.maubin.patrimoineplus.domain.calc

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.model.Account
import fr.maubin.patrimoineplus.domain.model.Ownership
import fr.maubin.patrimoineplus.domain.model.Profile

const val kDefaultProfileId   = "profil:moi"										// profil créé pour les données d'avant les profils
const val kDefaultProfileName = "Moi"												// son nom, modifiable

/*
	Profils des données, dans leur ordre ; données d'avant les profils : le
	profil « Moi ».
	inProfiles : profils lus
*/
fun profilesOrDefault(inProfiles: List<Profile>): List<Profile> =
	inProfiles.sortedBy { it.sortOrder }.ifEmpty { listOf(Profile(kDefaultProfileId, kDefaultProfileName)) }

/*
	Propriétaires d'un compte ; compte d'avant les profils : le premier
	profil, pour la part détenue.
	inAccount : compte
	inFirst   : premier profil
*/
fun ownersOrDefault(
	inAccount : Account,
	inFirst   : Profile
): List<Ownership> =
	inAccount.owners.ifEmpty { listOf(Ownership(inFirst.id, inAccount.sharePercent)) }

/*
	Données complétées pour les profils : au moins un profil, chaque compte
	avec ses propriétaires et une part comptée égale au total de leurs parts.
	Les données d'avant les profils deviennent celles du profil « Moi ».
*/
fun DataSet.withProfiles(): DataSet
{
	val vProfiles = profilesOrDefault(profiles)										// profils
	return copy(
		profiles = vProfiles,
		accounts = accounts.map { inAccount ->
			val vOwners = ownersOrDefault(inAccount, vProfiles.first())				// propriétaires
			inAccount.copy(owners = vOwners, sharePercent = vOwners.sumOf { it.percent }.coerceIn(1, 100))
		}
	)
}

/*
	Données vues par un profil, ou par toute la famille (null) : comptes du
	profil, chacun pour la part de ce profil ; opérations, relevés,
	récurrences et crédits qui touchent ces comptes. Famille : tous les
	comptes, chacun pour le total des parts de la famille. Les calculs
	(patrimoine, tableau de bord, bilan, projection, objectifs) s'appliquent
	ensuite sans changement.
	inProfileId : profil ; null pour la famille
*/
fun DataSet.scoped(inProfileId: String?): DataSet
{
	val vFull = withProfiles()														// données complétées
	if (inProfileId == null || vFull.profiles.none { it.id == inProfileId })
	{
		return vFull
	}
	val vAccounts = vFull.accounts.mapNotNull { inAccount ->
		inAccount.owners.firstOrNull { it.profileId == inProfileId }?.let { inAccount.copy(sharePercent = it.percent) }
	}																				// comptes du profil, à sa part
	val vIds      = vAccounts.map { it.id }.toSet()									// leurs identifiants
	return vFull.copy(
		accounts     = vAccounts,
		transactions = vFull.transactions.filter { inTx -> inTx.postings.any { it.accountId in vIds } },
		statements   = vFull.statements.filter { it.accountId in vIds },
		recurrences  = vFull.recurrences.filter { it.accountId in vIds || it.toAccountId in vIds },
		loans        = vFull.loans.filter { it.accountId in vIds }
	)
}

/*
	Parts d'un compte en texte : « id=50;id=50 » (classeur, base du téléphone).
	inOwners : propriétaires
*/
fun encodeShares(inOwners: List<Ownership>): String =
	inOwners.joinToString(";") { "${it.profileId}=${it.percent}" }

/*
	Parts d'un compte lues d'un texte « id=50;id=50 » ; morceau illisible ignoré.
	inText : texte
*/
fun decodeShares(inText: String): List<Ownership> =
	inText.split(";").mapNotNull { inPart ->
		val vPair = inPart.split("=")													// identifiant et part
		vPair.takeIf { it.size == 2 && it[0].isNotBlank() }?.let { inPair -> inPair[1].trim().toIntOrNull()?.let { Ownership(inPair[0].trim(), it) } }
	}

const val kFamilyView = "*"														// vue mémorisée de la famille

/*
	Profil d'une vue mémorisée : "" premier profil, "*" famille (null),
	sinon le profil s'il existe encore (le premier sinon). Sans profils
	encore lus : le profil « Moi ».
	inView     : vue mémorisée
	inProfiles : profils des données
*/
fun scopeOf(
	inView     : String,
	inProfiles : List<Profile>
): String? =
	when
	{
		inView == kFamilyView                  -> null
		inProfiles.any { it.id == inView }     -> inView
		else                                   -> inProfiles.firstOrNull()?.id ?: kDefaultProfileId
	}

/*
	Compte supprimé définitivement avec un profil, dont il était le seul
	propriétaire.
	account    : compte
	operations : opérations qui le touchent (supprimées avec lui)
*/
data class RemovedAccount(
	val account    : Account,
	val operations : Int
)

/*
	Compte partagé dont la part d'un profil retiré disparaît.
	account : compte
	share   : part retirée, en %
	before  : part comptée avant, en %
	after   : part comptée après, en %
*/
data class ReducedAccount(
	val account : Account,
	val share   : Int,
	val before  : Int,
	val after   : Int
)

/*
	Ce que deviennent les comptes d'un profil qu'on supprime.
	removed : comptes dont il est seul propriétaire, supprimés définitivement
	reduced : comptes partagés, dont sa part est retirée
*/
data class ProfileRemoval(
	val removed : List<RemovedAccount>,
	val reduced : List<ReducedAccount>
)
{
	// Aucun compte concerné : la suppression se fait sans confirmation.
	val isEmpty: Boolean
		get() = removed.isEmpty() && reduced.isEmpty()
}

/*
	Récapitulatif de la suppression d'un profil : comptes supprimés (il en
	est seul propriétaire, opérations comprises) et comptes partagés dont sa
	part disparaît (part comptée avant et après).
	inData      : données
	inProfileId : profil supprimé
*/
fun profileRemoval(
	inData      : DataSet,
	inProfileId : String
): ProfileRemoval
{
	val vData = inData.withProfiles()													// propriétaires complétés
	val vMine = vData.accounts.filter { inAccount -> inAccount.owners.any { it.profileId == inProfileId } }	// comptes du profil
	return ProfileRemoval(
		removed = vMine.filter { it.owners.size == 1 }.map { inAccount -> RemovedAccount(inAccount, vData.transactions.count { inTx -> inTx.postings.any { it.accountId == inAccount.id } }) },
		reduced = vMine.filter { it.owners.size > 1 }.map { inAccount ->
			val vShare = inAccount.owners.first { it.profileId == inProfileId }.percent		// part retirée
			val vTotal = inAccount.owners.sumOf { it.percent }								// part comptée avant
			ReducedAccount(inAccount, vShare, vTotal, vTotal - vShare)
		}
	)
}

/*
	Titulaire d'un compte, déduit de ses propriétaires : le nom du profil
	(« Alex »), ou les noms réunis pour un compte partagé (« Moi et Alex »),
	dans l'ordre des profils ; null avec un seul profil (rien à distinguer)
	ou sans propriétaire connu.
	inAccount  : compte
	inProfiles : profils de la famille
*/
fun holderLabel(
	inAccount  : Account,
	inProfiles : List<Profile>
): String?
{
	if (inProfiles.size < 2) return null
	val vIds   = inAccount.owners.filter { it.percent > 0 }.map { it.profileId }.toSet()	// propriétaires
	val vNames = inProfiles.sortedBy { it.sortOrder }.filter { it.id in vIds }.map { it.name }	// leurs noms, dans l'ordre
	return when (vNames.size)
	{
		0    -> null
		1    -> vNames.single()
		else -> vNames.dropLast(1).joinToString(", ") + " et " + vNames.last()
	}
}

/*
	Nom d'un compte suivi de son titulaire quand la famille a plusieurs
	profils (« Compte courant · Alex ») : deux comptes de même nom se
	distinguent partout où l'on choisit un compte.
	inAccount  : compte
	inProfiles : profils de la famille
*/
fun accountLabel(
	inAccount  : Account,
	inProfiles : List<Profile>
): String = holderLabel(inAccount, inProfiles)?.let { "${inAccount.name} · $it" } ?: inAccount.name

