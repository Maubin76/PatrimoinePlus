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
