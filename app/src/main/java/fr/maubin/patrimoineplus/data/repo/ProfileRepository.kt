package fr.maubin.patrimoineplus.data.repo

import androidx.room.withTransaction
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.db.ProfileEntity
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.domain.calc.decodeShares
import fr.maubin.patrimoineplus.domain.calc.encodeShares
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileId
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileName
import fr.maubin.patrimoineplus.domain.calc.kFamilyView
import fr.maubin.patrimoineplus.domain.calc.profilesOrDefault
import fr.maubin.patrimoineplus.domain.calc.scopeOf
import fr.maubin.patrimoineplus.domain.model.Profile
import fr.maubin.patrimoineplus.domain.rules.SetupError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import fr.maubin.patrimoineplus.domain.calc.ProfileRemoval
import fr.maubin.patrimoineplus.domain.calc.profileRemoval
import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.data.db.toDomain

/*
	Vue affichée : profils de la famille et profil choisi.
	profiles : profils, dans l'ordre (au moins « Moi »)
	profile  : profil affiché ; null pour la famille
*/
data class ProfileView(
	val profiles : List<Profile>,
	val profile  : String?
)

/*
	Profils de la famille sur le téléphone : lecture (une base d'avant les
	profils présente le profil « Moi »), vue choisie (mémorisée sur ce
	téléphone), ajout, renommage, retrait, avec les mêmes règles que sur le
	PC.
	inDb       : base locale
	inSettings : réglages (vue choisie)
	inClock    : horloge (horodatage des comptes modifiés)
	inAccounts : dépôt des comptes (suppression définitive)
*/
@Singleton
class ProfileRepository @Inject constructor(
	private val inDb       : PatrimoineDatabase,
	private val inSettings : SettingsRepository,
	private val inClock    : Clock,
	private val inAccounts : AccountRepository
)
{
	private val fDao = inDb.backupDao()											// profils et comptes en bloc

	/*
		Profils, suivis ; « Moi » seul pour une base d'avant les profils.
	*/
	fun observeProfiles(): Flow<List<Profile>> =
		fDao.observeProfiles().map { inList -> profilesOrDefault(inList.map { Profile(it.id, it.name, it.sortOrder) }) }

	/*
		Vue affichée, suivie : profils et profil choisi (le premier par
		défaut ; null pour la famille).
	*/
	fun observeView(): Flow<ProfileView> =
		combine(observeProfiles(), inSettings.viewProfile) { inProfiles, inView -> ProfileView(inProfiles, scopeOf(inView, inProfiles)) }

	/*
		Change de vue.
		inProfile : profil ; null pour la famille
	*/
	suspend fun setView(inProfile: String?) = inSettings.setViewProfile(inProfile ?: kFamilyView)

	/*
		Ajoute un profil ; une base d'avant les profils enregistre d'abord
		« Moi ».
		inName : nom
		Retour : refus ; null si ajouté
	*/
	suspend fun add(inName: String): SetupError? =
		inDb.withTransaction {
			val vName     = inName.trim()														// nom saisi
			val vProfiles = current()															// profils
			when
			{
				vName.isEmpty()                                                     -> SetupError.BlankName
				vProfiles.any { it.name.equals(vName, ignoreCase = true) }          -> SetupError.ProfileTaken
				else                                                                ->
				{
					fDao.insertProfiles(vProfiles.map { ProfileEntity(it.id, it.name, it.sortOrder) } + ProfileEntity(UUID.randomUUID().toString(), vName, vProfiles.maxOf { it.sortOrder } + 1))
					null
				}
			}
		}

	/*
		Renomme un profil.
		inId   : profil
		inName : nouveau nom
		Retour : refus ; null si renommé
	*/
	suspend fun rename(
		inId   : String,
		inName : String
	): SetupError? =
		inDb.withTransaction {
			val vName     = inName.trim()														// nom saisi
			val vProfiles = current()															// profils
			when
			{
				vName.isEmpty()                                                                  -> SetupError.BlankName
				vProfiles.none { it.id == inId }                                                 -> SetupError.NotFound
				vProfiles.any { it.id != inId && it.name.equals(vName, ignoreCase = true) }      -> SetupError.ProfileTaken
				else                                                                             ->
				{
					fDao.insertProfiles(vProfiles.map { ProfileEntity(it.id, if (it.id == inId) vName else it.name, it.sortOrder) })
					null
				}
			}
		}

	/*
		Récapitulatif de la suppression d'un profil : comptes dont il est seul
		propriétaire (supprimés, opérations comprises), comptes partagés dont
		sa part est retirée.
		inId : profil
	*/
	suspend fun removal(inId: String): ProfileRemoval =
		profileRemoval(
			DataSet(fDao.accounts().map { it.toDomain() }, emptyList(), emptyList(), inDb.transactionDao().getAll().map { it.toDomain() }, emptyList(), emptyList(), profiles = current()),
			inId
		)

	/*
		Retire un profil (voir removal) : les comptes dont il est seul
		propriétaire sont supprimés définitivement, avec leurs opérations ; il
		disparaît des propriétaires des comptes qu'il partage. Refusé pour le
		dernier profil.
		inId : profil
		Retour : refus ; null si retiré
	*/
	suspend fun remove(inId: String): SetupError? =
		inDb.withTransaction {
			val vProfiles = current()															// profils
			val vFirst    = vProfiles.first().id												// propriétaire des comptes d'avant les profils
			val vAccounts = fDao.accounts().map { it to decodeShares(it.owners).ifEmpty { listOf(fr.maubin.patrimoineplus.domain.model.Ownership(vFirst, it.sharePercent)) } }	// comptes et propriétaires
			when
			{
				vProfiles.none { it.id == inId }                                  -> SetupError.NotFound
				vProfiles.size == 1                                               -> SetupError.LastProfile
				else                                                              ->
				{
					vAccounts.filter { (_, inOwners) -> inOwners.singleOrNull()?.profileId == inId }.forEach { (inEntity, _) -> inAccounts.deletePermanently(inEntity.id) }
					vAccounts.filter { (_, inOwners) -> inOwners.any { it.profileId == inId } }.forEach { (inEntity, inOwners) ->
						val vKept = inOwners.filter { it.profileId != inId }								// propriétaires restants
						inDb.accountDao().update(inEntity.copy(owners = encodeShares(vKept), sharePercent = vKept.sumOf { it.percent }, updatedAt = inClock.millis()))
					}
					fDao.deleteProfiles()
					fDao.insertProfiles(vProfiles.filter { it.id != inId }.map { ProfileEntity(it.id, it.name, it.sortOrder) })
					null
				}
			}
		}

	/*
		Profils enregistrés, ou « Moi » pour une base d'avant les profils.
	*/
	private suspend fun current(): List<Profile> =
		profilesOrDefault(fDao.profiles().map { Profile(it.id, it.name, it.sortOrder) })

	companion object
	{
		// Profil d'une base d'avant les profils.
		val kDefault = Profile(kDefaultProfileId, kDefaultProfileName)
	}
}
