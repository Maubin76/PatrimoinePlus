package fr.maubin.patrimoineplus.data.sync

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.backup.xlsx.isXmlChar
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
	val vDigest = MessageDigest.getInstance("SHA-256").digest(encodeBackup(canonicalBackup(inFile)).toByteArray(Charsets.UTF_8))	// empreinte brute
	return vDigest.joinToString("") { "%02x".format(it) }
}

/*
	Forme canonique d'un contenu : listes triées par identifiant, textes
	nettoyés comme à l'écriture du classeur, date d'export ignorée. Deux
	contenus identiques ont la même forme canonique, quel que soit l'ordre de
	lecture ou l'appareil qui les a écrits (empreintes, fusion).
	inFile : contenu
*/
fun canonicalBackup(inFile: BackupFile): BackupFile =
	inFile.copy(
		exportedAt   = "",
		accounts     = inFile.accounts.sortedBy { it.id }.map { it.copy(name = it.name.canonical(), institution = it.institution.canonicalOrNull()) },
		categories   = inFile.categories.sortedBy { it.id }.map { it.copy(name = it.name.canonical()) },
		assets       = inFile.assets.sortedBy { it.id }.map { it.copy(name = it.name.canonical(), isin = it.isin.canonicalOrNull(), ticker = it.ticker.canonicalOrNull()) },
		transactions = inFile.transactions.sortedBy { it.id }.map { inTx ->
			inTx.copy(label = inTx.label.canonical(), notes = inTx.notes.canonicalOrNull(), postings = inTx.postings.sortedBy { it.id }, trades = inTx.trades.sortedBy { it.id })
		},
		statements   = inFile.statements.sortedBy { it.id },
		prices       = inFile.prices.sortedBy { it.id },
		recurrences  = inFile.recurrences.sortedBy { it.id }.map { it.copy(label = it.label.canonical(), notes = it.notes.canonicalOrNull()) },
		loans        = inFile.loans.sortedBy { it.id }
	)

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

/*
	Texte tel qu'il ressort d'un aller-retour par le classeur : caractères
	interdits en XML retirés, fins de ligne unifiées, espaces de bord
	retirés. L'empreinte ne change donc pas quand seul l'aller-retour a
	retouché un texte.
*/
private fun String.canonical(): String =
	codePoints().filter { isXmlChar(it) }.toArray().let { String(it, 0, it.size) }
		.replace("\r\n", "\n").replace('\r', '\n').trim()

/*
	Texte facultatif canonique ; null s'il est vide.
*/
private fun String?.canonicalOrNull(): String? = this?.canonical()?.ifEmpty { null }
