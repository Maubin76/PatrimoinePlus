package fr.maubin.patrimoineplus.data.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.maubin.patrimoineplus.data.backup.kBackupMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/*
	Dossier synchronisé (par Syncthing ou autre) où se trouve le fichier
	d'échange.
*/
interface SyncFolder
{
	/*
		Contenu du fichier d'échange ; null s'il n'existe pas.
	*/
	suspend fun read(): ByteArray?

	/*
		Écrit le fichier d'échange (créé s'il n'existe pas, remplacé sinon).
		inBytes : contenu
	*/
	suspend fun write(inBytes: ByteArray)
}

/*
	Ouvre le dossier synchronisé désigné par son adresse.
*/
fun interface SyncFolderFactory
{
	/*
		Dossier correspondant à une adresse.
		inUri : adresse du dossier
	*/
	fun open(inUri: String): SyncFolder
}

/*
	Dossier choisi par l'utilisateur via le sélecteur de dossiers du système
	(Storage Access Framework) : l'autorisation, mémorisée par Android, donne
	accès aux fichiers déposés par d'autres applis (Syncthing).
	inContext : contexte de l'application
	inTree    : adresse du dossier choisi
*/
class SafSyncFolder(
	private val inContext : Context,
	private val inTree    : Uri
) : SyncFolder
{
	private val fParent = DocumentsContract.buildDocumentUriUsingTree(inTree, DocumentsContract.getTreeDocumentId(inTree))	// document du dossier

	/*
		Contenu du fichier d'échange ; null s'il n'existe pas.
	*/
	override suspend fun read(): ByteArray? =
		withContext(Dispatchers.IO) {
			find()?.let { inUri -> inContext.contentResolver.openInputStream(inUri)?.use { it.readBytes() } }
		}

	/*
		Écrit le fichier d'échange.
		inBytes : contenu
	*/
	override suspend fun write(inBytes: ByteArray)
	{
		withContext(Dispatchers.IO) {
			val vUri = find() ?: DocumentsContract.createDocument(inContext.contentResolver, fParent, kBackupMime, kSyncFileName)
				?: throw IOException("création du fichier refusée")				// fichier d'échange
			val vStream = inContext.contentResolver.openOutputStream(vUri, "wt") ?: throw IOException("écriture impossible")	// flux d'écriture
			vStream.use { it.write(inBytes) }
		}
	}

	/*
		Adresse du fichier d'échange dans le dossier ; null s'il n'existe pas.
	*/
	private fun find(): Uri?
	{
		val vChildren = DocumentsContract.buildChildDocumentsUriUsingTree(inTree, DocumentsContract.getTreeDocumentId(inTree))	// contenu du dossier
		inContext.contentResolver.query(
			vChildren,
			arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
			null, null, null
		)?.use { inCursor ->
			while (inCursor.moveToNext())
			{
				if (inCursor.getString(1) == kSyncFileName)
				{
					return DocumentsContract.buildDocumentUriUsingTree(inTree, inCursor.getString(0))
				}
			}
		}
		return null
	}
}

/*
	Fabrique des dossiers choisis par le sélecteur du système.
	inContext : contexte de l'application
*/
@Singleton
class SafSyncFolderFactory @Inject constructor(
	@ApplicationContext private val inContext : Context
) : SyncFolderFactory
{
	/*
		Dossier correspondant à une adresse.
		inUri : adresse du dossier
	*/
	override fun open(inUri: String): SyncFolder = SafSyncFolder(inContext, Uri.parse(inUri))
}

/*
	Mémoire de la synchronisation : dossier choisi, empreinte du contenu à la
	dernière synchronisation, instant de celle-ci.
*/
interface SyncStore
{
	// Adresse du dossier synchronisé ; null si la synchronisation est arrêtée.
	val folder: Flow<String?>

	// Instant de la dernière synchronisation réussie (ms) ; null avant la première.
	val lastSyncAt: Flow<Long?>

	/*
		Empreinte du contenu à la dernière synchronisation ; null avant la première.
	*/
	suspend fun fingerprint(): String?

	/*
		Choisit le dossier synchronisé (null pour arrêter) ; oublie l'empreinte.
		inUri : adresse du dossier
	*/
	suspend fun setFolder(inUri: String?)

	/*
		Mémorise une synchronisation réussie.
		inFingerprint : empreinte du contenu commun
		inAt          : instant (ms)
	*/
	suspend fun recordSync(
		inFingerprint : String,
		inAt          : Long
	)
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
	private val kFolderKey      = stringPreferencesKey("sync_folder")		// clé du dossier
	private val kFingerprintKey = stringPreferencesKey("sync_fingerprint")	// clé de l'empreinte
	private val kLastSyncKey    = longPreferencesKey("sync_last")			// clé de l'instant

	override val folder: Flow<String?> = inDataStore.data.map { it[kFolderKey] }

	override val lastSyncAt: Flow<Long?> = inDataStore.data.map { it[kLastSyncKey] }

	/*
		Empreinte mémorisée.
	*/
	override suspend fun fingerprint(): String? = inDataStore.data.first()[kFingerprintKey]

	/*
		Choisit le dossier ; oublie l'empreinte et l'instant.
		inUri : adresse du dossier ; null pour arrêter
	*/
	override suspend fun setFolder(inUri: String?)
	{
		inDataStore.edit { inPreferences ->
			if (inUri == null) inPreferences.remove(kFolderKey) else inPreferences[kFolderKey] = inUri
			inPreferences.remove(kFingerprintKey)
			inPreferences.remove(kLastSyncKey)
		}
	}

	/*
		Mémorise une synchronisation réussie.
		inFingerprint : empreinte du contenu commun
		inAt          : instant (ms)
	*/
	override suspend fun recordSync(
		inFingerprint : String,
		inAt          : Long
	)
	{
		inDataStore.edit { inPreferences ->
			inPreferences[kFingerprintKey] = inFingerprint
			inPreferences[kLastSyncKey]    = inAt
		}
	}
}
