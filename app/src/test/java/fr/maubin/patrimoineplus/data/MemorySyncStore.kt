package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.sync.SyncStore

/*
	Mémoire de synchronisation en mémoire (identité fixe du téléphone).
*/
class MemorySyncStore : SyncStore
{
	/*
		Identifiant fixe du téléphone de test.
	*/
	override suspend fun deviceId(): String = "tel-test"
}
