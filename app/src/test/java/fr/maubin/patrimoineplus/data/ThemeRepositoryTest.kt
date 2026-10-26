package fr.maubin.patrimoineplus.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.maubin.patrimoineplus.data.backup.BackupRepository
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.repo.ThemeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Thèmes personnalisés sur le téléphone (Room en mémoire, Robolectric) :
	création (couleurs normalisées), nom en double ou code illisible
	refusés, modification, présents dans l'instantané (fichier d'échange),
	suppression.
*/
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ThemeRepositoryTest
{
	private val kClock = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
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
		Parcours complet d'un thème.
	*/
	@Test
	fun themes() = runTest {
		val vRepo = ThemeRepository(fDb)													// thèmes
		val vId   = vRepo.save(null, "Océan", listOf("#016b8f", "219EBC", "#fb0", "#FB8500", "#8ECAE6")).getOrThrow()	// thème créé
		assertEquals(listOf("#016B8F", "#219EBC", "#FFBB00", "#FB8500", "#8ECAE6"), vRepo.observeThemes().first().single().colors)
		assertTrue(vRepo.save(null, "océan", listOf("#000", "#111", "#222", "#333", "#444")).isFailure)
		assertTrue(vRepo.save(null, "Forêt", listOf("#000", "#111", "#222", "#333", "vert")).isFailure)
		vRepo.save(vId, "Océan clair", listOf("#016b8f", "219EBC", "#fb0", "#FB8500", "#8ECAE6")).getOrThrow()
		assertEquals(listOf("Océan clair"), BackupRepository(fDb, kClock).snapshot().themes.map { it.name })
		vRepo.delete(vId)
		assertTrue(vRepo.observeThemes().first().isEmpty())
	}
}
