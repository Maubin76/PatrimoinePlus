package fr.maubin.patrimoineplus.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/*
	Mémoire de la synchronisation : identité de ce téléphone.
*/
interface SyncStore
{
	/*
		Identifiant de ce téléphone, tiré au hasard la première fois puis gardé
		(historique des modifications, appairage).
	*/
	suspend fun deviceId(): String
}

/*
	Mémoire de la synchronisation dans le DataStore des réglages.
	inDataStore : magasin de préférences de l'application
*/
@Singleton
class DataStoreSyncStore @Inject constructor(
	private val inDataStore : DataStore<Preferences>
) : SyncStore
{
	private val kDeviceKey = stringPreferencesKey("device_id")					// clé de l'identifiant du téléphone

	/*
		Identifiant du téléphone, tiré la première fois.
	*/
	override suspend fun deviceId(): String =
		inDataStore.data.first()[kDeviceKey] ?: java.util.UUID.randomUUID().toString().also { inId -> inDataStore.edit { it[kDeviceKey] = inId } }
}
