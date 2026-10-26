package fr.maubin.patrimoineplus.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.maubin.patrimoineplus.data.backup.BackupRepository
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.ProfileRepository
import fr.maubin.patrimoineplus.data.repo.SaveResult
import fr.maubin.patrimoineplus.data.settings.DataStoreSettingsRepository
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileId
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Ownership
import fr.maubin.patrimoineplus.domain.rules.SetupError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import fr.maubin.patrimoineplus.data.repo.Opening

/*
	Profils sur le téléphone (Room en mémoire, Robolectric) : une base
	d'avant les profils présente « Moi » ; le premier ajout l'enregistre ;
	nom vide ou en double refusé ; récapitulatif de retrait (comptes seuls
	supprimés, parts retirées) ; retrait : ses comptes seuls supprimés avec
	leurs opérations, sa part des comptes partagés retirée ; dernier profil
	refusé ; vue choisie mémorisée (famille : null) ; instantané au format
	des profils.
*/
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProfileRepositoryTest
{
	/*
		Magasin de préférences en mémoire.
	*/
	private class MemoryStore : DataStore<Preferences>
	{
		private val fData = MutableStateFlow(emptyPreferences())					// préférences en cours

		override val data: Flow<Preferences> = fData

		/*
			Applique une modification et garde le résultat.
			inTransform : modification
		*/
		override suspend fun updateData(inTransform: suspend (Preferences) -> Preferences): Preferences =
			inTransform(fData.value).also { fData.value = it }
	}

	private val kClock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private lateinit var fDb : PatrimoineDatabase									// base en mémoire

	/*
		Ouvre une base vierge.
	*/
	@Before
	fun open()
	{
		fDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PatrimoineDatabase::class.java).allowMainThreadQueries().build()
	}

	/*
		Ferme la base.
	*/
	@After
	fun close() = fDb.close()

	/*
		Parcours complet : « Moi » implicite, ajout de Julie, compte joint à
		deux, retraits refusés puis accepté, vues, instantané.
	*/
	@Test
	fun profiles() = runTest {
		val vAccounts = AccountRepository(fDb, kClock)									// comptes
		val vProfiles = ProfileRepository(fDb, DataStoreSettingsRepository(MemoryStore()), kClock, vAccounts)	// profils
		assertEquals(listOf("Moi"), vProfiles.observeProfiles().first().map { it.name })
		val vMine     = (vAccounts.create("Courant", AccountType.CHECKING) as SaveResult.Saved).id	// compte d'avant les profils
		assertEquals(SetupError.BlankName, vProfiles.add(" "))
		assertEquals(SetupError.ProfileTaken, vProfiles.add("moi"))
		assertNull(vProfiles.add("Julie"))
		val vJulie    = vProfiles.observeProfiles().first().single { it.name == "Julie" }.id	// second profil
		assertEquals(listOf(kDefaultProfileId, vJulie), vProfiles.observeProfiles().first().map { it.id })
		val vJoint    = (vAccounts.create("Joint", AccountType.CHECKING, null, 100, null, listOf(Ownership(kDefaultProfileId, 50), Ownership(vJulie, 50))) as SaveResult.Saved).id	// compte du couple
		val vPlan     = vProfiles.removal(kDefaultProfileId)								// récapitulatif pour « Moi »
		assertEquals(listOf(vMine), vPlan.removed.map { it.account.id })
		assertEquals(listOf(Triple(vJoint, 100, 50)), vPlan.reduced.map { Triple(it.account.id, it.before, it.after) })
		val vHers     = (vAccounts.create("Livret de Julie", AccountType.SAVINGS, null, 100, Opening(50_000, java.time.LocalDate.parse("2026-09-01")), listOf(Ownership(vJulie, 100))) as SaveResult.Saved).id	// compte de Julie seule
		assertEquals(listOf(1), vProfiles.removal(vJulie).removed.map { it.operations })
		assertEquals(kDefaultProfileId, vProfiles.observeView().first().profile)
		vProfiles.setView(null)
		assertNull(vProfiles.observeView().first().profile)
		vProfiles.setView(vJulie)
		assertEquals(vJulie, vProfiles.observeView().first().profile)
		val vFile     = BackupRepository(fDb, kClock).snapshot()						// instantané
		assertEquals(listOf("Moi", "Julie"), vFile.profiles.map { it.name })
		assertEquals(listOf(50, 50), vFile.accounts.single { it.id == vJoint }.owners.map { it.percent })
		assertNull(vProfiles.remove(vJulie))
		assertNull(fDb.accountDao().getById(vHers))										// son livret supprimé
		assertEquals(listOf(kDefaultProfileId to 50), fDb.accountDao().getById(vJoint)!!.let { fr.maubin.patrimoineplus.domain.calc.decodeShares(it.owners) }.map { it.profileId to it.percent })
		assertEquals(kDefaultProfileId, vProfiles.observeView().first().profile)		// profil disparu : premier profil
		assertEquals(SetupError.LastProfile, vProfiles.remove(kDefaultProfileId))
		assertEquals(100, fDb.accountDao().getById(vMine)!!.sharePercent)
	}
}
