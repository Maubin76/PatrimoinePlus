package fr.maubin.patrimoineplus.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import fr.maubin.patrimoineplus.data.settings.DataStoreSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/*
	Réglages du téléphone : forme des diagrammes de répartition mémorisée
	diagramme par diagramme (accueil, onglet Comptes, titres d'un compte),
	indépendante d'un diagramme à l'autre. Magasin en mémoire (le DataStore
	sur fichier ne sait pas remplacer son fichier sous Windows en test JVM).
*/
class SettingsRepositoryTest
{
	/*
		Magasin de préférences en mémoire, pour les tests.
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

	/*
		Chaque répartition garde sa forme, indépendamment des autres ; une
		autre lecture du même magasin retrouve les choix.
	*/
	@Test
	fun pieChartsAreRememberedPerChart() = runTest {
		val vStore = MemoryStore()															// magasin
		val vRepo  = DataStoreSettingsRepository(vStore)									// réglages
		assertEquals(emptySet<String>(), vRepo.pieCharts.first())
		vRepo.setPieChart("accueil", true)
		vRepo.setPieChart("titres:pea", true)
		vRepo.setPieChart("accueil", false)
		assertEquals(setOf("titres:pea"), vRepo.pieCharts.first())
		assertEquals(setOf("titres:pea"), DataStoreSettingsRepository(vStore).pieCharts.first())	// relu au prochain lancement
	}
}
