package fr.maubin.patrimoineplus.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/*
	Dossier des sauvegardes dans le stockage partagé du téléphone :
	Documents/PatrimoinePlus, écrit par MediaStore (aucune autorisation n'est
	nécessaire depuis Android 10). L'appli ne voit et ne supprime que les
	fichiers qu'elle a créés elle-même depuis son installation.
	inContext : contexte de l'application
*/
@Singleton
class BackupStorage @Inject constructor(
	@ApplicationContext private val inContext : Context
)
{
	private val fCollection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)	// fichiers du stockage partagé
	private val fPath       = "${Environment.DIRECTORY_DOCUMENTS}/$kBackupFolder/"				// chemin relatif du dossier

	// Chemin affiché à l'utilisateur.
	val displayPath: String get() = "${Environment.DIRECTORY_DOCUMENTS}/$kBackupFolder"

	/*
		Écrit un fichier dans le dossier, en remplaçant celui de même nom, puis
		applique la rotation. Le nouveau fichier est d'abord écrit sous un nom
		provisoire, invisible tant qu'il n'est pas complet : l'ancien n'est
		supprimé qu'une fois le nouveau entièrement écrit. En cas d'échec, le
		fichier provisoire est retiré et l'ancien reste en place.
		inName  : nom du fichier
		inWrite : écrit le contenu dans le flux
	*/
	fun write(
		inName  : String,
		inWrite : (OutputStream) -> Unit
	)
	{
		val vResolver = inContext.contentResolver								// accès aux fichiers
		val vPartial  = "$inName.partiel"										// nom provisoire
		list().filter { it.first == vPartial }.forEach { vResolver.delete(it.second, null, null) }
		val vValues = ContentValues().apply {
			put(MediaStore.MediaColumns.DISPLAY_NAME, vPartial)
			put(MediaStore.MediaColumns.MIME_TYPE, kBackupMime)
			put(MediaStore.MediaColumns.RELATIVE_PATH, fPath)
			put(MediaStore.MediaColumns.IS_PENDING, 1)
		}																		// description du fichier
		val vUri = vResolver.insert(fCollection, vValues) ?: throw IOException("création du fichier refusée")	// fichier créé
		try
		{
			val vStream = vResolver.openOutputStream(vUri, "w") ?: throw IOException("écriture impossible")	// flux d'écriture
			vStream.use(inWrite)
			list().filter { it.first == inName }.forEach { vResolver.delete(it.second, null, null) }
			vResolver.update(vUri, ContentValues().apply {
				put(MediaStore.MediaColumns.DISPLAY_NAME, inName)
				put(MediaStore.MediaColumns.IS_PENDING, 0)
			}, null, null)
		}
		catch (inError: Exception)
		{
			vResolver.delete(vUri, null, null)
			throw inError
		}
		rotate()
	}

	/*
		Supprime les sauvegardes au-delà des plus récentes.
	*/
	private fun rotate()
	{
		val vFiles  = list()														// sauvegardes présentes
		val vDelete = backupsToDelete(vFiles.map { it.first }).toSet()			// noms à supprimer
		vFiles.filter { it.first in vDelete }.forEach { inContext.contentResolver.delete(it.second, null, null) }
	}

	/*
		Fichiers de l'appli présents dans le dossier : nom et adresse.
	*/
	fun list(): List<Pair<String, Uri>>
	{
		val vFound = mutableListOf<Pair<String, Uri>>()						// fichiers trouvés
		inContext.contentResolver.query(
			fCollection,
			arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
			"${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
			arrayOf(fPath),
			null
		)?.use { inCursor ->
			val vId   = inCursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)			// colonne de l'identifiant
			val vName = inCursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)	// colonne du nom
			while (inCursor.moveToNext())
			{
				vFound += inCursor.getString(vName) to Uri.withAppendedPath(fCollection, inCursor.getLong(vId).toString())
			}
		}
		return vFound
	}
}
