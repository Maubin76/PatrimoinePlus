package fr.maubin.patrimoineplus.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/*
	Réglages de l'application, mémorisés entre les sessions.
*/
interface SettingsRepository
{
	// Vrai quand les montants sont masqués (mode discret).
	val amountsHidden: Flow<Boolean>

	/*
		Active ou désactive le mode discret.
		inHidden : vrai pour masquer les montants
	*/
	suspend fun setAmountsHidden(inHidden: Boolean)
}

/*
	Réglages stockés dans un DataStore de préférences, sur le téléphone.
	inDataStore : magasin de préférences de l'application
*/
@Singleton
class DataStoreSettingsRepository @Inject constructor(
	private val inDataStore : DataStore<Preferences>
) : SettingsRepository
{
	private val kAmountsHiddenKey = booleanPreferencesKey("amounts_hidden")	// clé du mode discret

	override val amountsHidden: Flow<Boolean> =
		inDataStore.data.map { inPreferences -> inPreferences[kAmountsHiddenKey] ?: false }

	/*
		Enregistre le mode discret.
		inHidden : vrai pour masquer les montants
	*/
	override suspend fun setAmountsHidden(inHidden: Boolean)
	{
		inDataStore.edit { inPreferences -> inPreferences[kAmountsHiddenKey] = inHidden }
	}
}
