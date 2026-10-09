package fr.maubin.patrimoineplus.data.sync

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import java.security.MessageDigest

const val kSyncFileName = "patrimoineplus-donnees.xlsx"						// fichier d'échange entre appareils

/*
	Ce que la synchronisation doit faire, d'après les empreintes.
*/
enum class SyncAction
{
	NOTHING,																	// rien n'a changé (ou rien à partager)
	EXPORT,																		// seules les données locales ont changé : écrire le fichier
	IMPORT,																		// seul le fichier a changé : remplacer les données locales
	RECORD,																		// les deux sont identiques : mémoriser l'empreinte commune
	CONFLICT																	// les deux ont changé différemment : demander à l'utilisateur
}

/*
	Empreinte du contenu d'une sauvegarde : SHA-256 de sa forme canonique
	(listes triées par identifiant, date d'export ignorée). Deux contenus
	identiques ont la même empreinte, quel que soit l'ordre de lecture ou
	l'appareil qui les a écrits.
	inFile : contenu
*/
fun syncFingerprint(inFile: BackupFile): String
{
	val vCanonical = inFile.copy(
		exportedAt   = "",
		accounts     = inFile.accounts.sortedBy { it.id },
		categories   = inFile.categories.sortedBy { it.id },
		assets       = inFile.assets.sortedBy { it.id },
		transactions = inFile.transactions.sortedBy { it.id }.map { inTx -> inTx.copy(postings = inTx.postings.sortedBy { it.id }, trades = inTx.trades.sortedBy { it.id }) },
		statements   = inFile.statements.sortedBy { it.id },
		prices       = inFile.prices.sortedBy { it.id }
	)																			// forme canonique
	val vDigest = MessageDigest.getInstance("SHA-256").digest(encodeBackup(vCanonical).toByteArray(Charsets.UTF_8))	// empreinte brute
	return vDigest.joinToString("") { "%02x".format(it) }
}

/*
	Décide de l'action de synchronisation en comparant trois empreintes : celle
	de la dernière synchronisation, celle des données locales et celle du
	fichier d'échange. Aucune date n'intervient : les horloges des appareils
	peuvent différer sans conséquence.
	- Pas de fichier : l'exporter, sauf si les données locales sont vides.
	- Première synchronisation (pas d'empreinte mémorisée) avec un fichier :
	  identiques → mémoriser ; données locales vides → importer ; sinon conflit.
	- Ensuite : seul le local a changé → exporter ; seul le fichier → importer ;
	  les deux, identiquement → mémoriser ; différemment → conflit.
	inLast       : empreinte de la dernière synchronisation ; null avant la première
	inLocal      : empreinte des données locales
	inFile       : empreinte du fichier ; null s'il n'existe pas
	inLocalEmpty : vrai si les données locales ne contiennent aucun compte
*/
fun decideSync(
	inLast       : String?,
	inLocal      : String,
	inFile       : String?,
	inLocalEmpty : Boolean
): SyncAction =
	when
	{
		inFile == null  -> if (inLocalEmpty) SyncAction.NOTHING else SyncAction.EXPORT
		inLocal == inFile -> if (inLast == inLocal) SyncAction.NOTHING else SyncAction.RECORD
		inLast == null  -> if (inLocalEmpty) SyncAction.IMPORT else SyncAction.CONFLICT
		inLocal == inLast -> SyncAction.IMPORT
		inFile == inLast  -> SyncAction.EXPORT
		else            -> SyncAction.CONFLICT
	}
