package fr.maubin.patrimoineplus.data.backup.xlsx

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

private const val kMainNs = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"			// espace de noms des feuilles
private const val kRelNs  = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"	// espace des relations
private const val kMaxEntryBytes = 64L * 1024 * 1024											// taille maximale d'une partie

/*
	Feuille lue : intitulés de la première ligne, puis lignes de données sous
	forme intitulé → valeur brute (texte, ou nombre tel qu'écrit dans le
	fichier). Les lignes entièrement vides sont ignorées.
	name    : nom de l'onglet
	headers : intitulés, dans l'ordre
	rows    : lignes de données
*/
data class ReadSheet(
	val name    : String,
	val headers : List<String>,
	val rows    : List<Map<String, String>>
)

/*
	Lit un classeur .xlsx, qu'il vienne de l'appli ou ait été réenregistré par
	un tableur (chaînes partagées, cellules en ligne, colonnes manquantes).
	Lève une exception si le fichier n'est pas un classeur lisible.
	inStream : contenu du fichier
*/
fun readXlsx(inStream: InputStream): List<ReadSheet>
{
	val vParts = unzip(inStream)												// parties de l'archive
	val vBook  = parse(vParts["xl/workbook.xml"] ?: error("pas de classeur"))	// classeur
	val vRels  = vParts["xl/_rels/workbook.xml.rels"]?.let { parse(it) }		// relations du classeur
	val vTargets = vRels?.let { inRels ->
		elements(inRels.documentElement, "Relationship").associate { it.getAttribute("Id") to it.getAttribute("Target") }
	}.orEmpty()																	// cible de chaque relation
	val vShared = vParts["xl/sharedStrings.xml"]?.let { inXml ->
		elements(parse(inXml).documentElement, kMainNs, "si").map { inSi -> elements(inSi, kMainNs, "t").joinToString("") { it.textContent } }
	}.orEmpty()																	// chaînes partagées
	return elements(vBook.documentElement, kMainNs, "sheet").map { inSheet ->
		val vTarget = vTargets[inSheet.getAttributeNS(kRelNs, "id")] ?: error("feuille introuvable")	// chemin de la feuille
		val vPath   = if (vTarget.startsWith("/")) vTarget.removePrefix("/") else "xl/${vTarget}"		// chemin dans l'archive
		readSheet(inSheet.getAttribute("name"), parse(vParts[vPath] ?: error("feuille absente : $vPath")), vShared)
	}
}

/*
	Lit les lignes d'une feuille.
	inName   : nom de l'onglet
	inDoc    : XML de la feuille
	inShared : chaînes partagées
*/
private fun readSheet(
	inName   : String,
	inDoc    : Document,
	inShared : List<String>
): ReadSheet
{
	val vRows = elements(inDoc.documentElement, kMainNs, "row").map { inRow ->
		val vCells = sortedMapOf<Int, String>()									// valeur par indice de colonne
		var vNext  = 0															// colonne implicite suivante
		elements(inRow, kMainNs, "c").forEach { inCell ->
			val vRef   = inCell.getAttribute("r")								// référence (A1), facultative
			val vIndex = if (vRef.isNotEmpty()) columnIndex(vRef) else vNext		// colonne de la cellule
			vNext = vIndex + 1
			cellText(inCell, inShared)?.let { vCells[vIndex] = it }
		}
		vCells
	}.filter { inRow -> inRow.values.any { it.isNotBlank() } }					// lignes non vides
	if (vRows.isEmpty())
	{
		return ReadSheet(inName, emptyList(), emptyList())
	}
	val vHeaderRow = vRows.first()											// ligne d'en-tête
	val vHeaders   = vHeaderRow.mapValues { it.value.trim() }				// intitulé par colonne
	return ReadSheet(
		name    = inName,
		headers = vHeaders.values.toList(),
		rows    = vRows.drop(1).map { inRow ->
			inRow.entries.mapNotNull { (inIndex, inValue) -> vHeaders[inIndex]?.let { it to inValue } }.toMap()
		}
	)
}

/*
	Valeur brute d'une cellule : texte (partagé, en ligne, formule texte) ou
	nombre tel qu'écrit ; null si la cellule est vide.
	inCell   : élément <c>
	inShared : chaînes partagées
*/
private fun cellText(
	inCell   : Element,
	inShared : List<String>
): String?
{
	val vType  = inCell.getAttribute("t")										// type de la cellule
	val vValue = elements(inCell, kMainNs, "v").firstOrNull()?.textContent		// valeur brute
	return when (vType)
	{
		"s"         -> vValue?.toIntOrNull()?.let { inShared.getOrNull(it) }
		"inlineStr" -> elements(inCell, kMainNs, "is").firstOrNull()?.let { inIs -> elements(inIs, kMainNs, "t").joinToString("") { it.textContent } }
		"b"         -> if (vValue == "1") "1" else "0"
		else        -> vValue
	}
}

/*
	Indice de colonne d'une référence : « C12 » → 2.
	inRef : référence de cellule
*/
internal fun columnIndex(inRef: String): Int
{
	var vIndex = 0																// numéro à partir de 1
	inRef.takeWhile { it.isLetter() }.uppercase().forEach { vIndex = vIndex * 26 + (it - 'A' + 1) }
	return vIndex - 1
}

/*
	Décompresse les parties XML utiles de l'archive, avec une taille bornée.
	inStream : contenu du fichier
*/
private fun unzip(inStream: InputStream): Map<String, ByteArray>
{
	val vParts = mutableMapOf<String, ByteArray>()							// parties lues
	ZipInputStream(inStream).use { inZip ->
		var vEntry = inZip.nextEntry												// partie courante
		while (vEntry != null)
		{
			val vName = vEntry.name.removePrefix("/")							// nom normalisé
			if (vName.endsWith(".xml") || vName.endsWith(".rels"))
			{
				vParts[vName] = readBounded(inZip, vName)
			}
			vEntry = inZip.nextEntry
		}
	}
	require(vParts.isNotEmpty()) { "archive vide ou illisible" }
	return vParts
}

/*
	Lit la partie courante d'une archive, en refusant une partie démesurée
	(fichier piégé). InputStream.readNBytes n'existe qu'à partir d'Android 13.
	inZip  : archive positionnée sur la partie
	inName : nom de la partie (message d'erreur)
*/
private fun readBounded(
	inZip  : ZipInputStream,
	inName : String
): ByteArray
{
	val vOut    = java.io.ByteArrayOutputStream()							// contenu lu
	val vBuffer = ByteArray(64 * 1024)											// tampon de lecture
	var vRead   = inZip.read(vBuffer)											// octets lus
	while (vRead >= 0)
	{
		vOut.write(vBuffer, 0, vRead)
		require(vOut.size() <= kMaxEntryBytes) { "partie trop volumineuse : $inName" }
		vRead = inZip.read(vBuffer)
	}
	return vOut.toByteArray()
}

/*
	Analyse un document XML, sans entités externes ni DTD.
	inBytes : contenu
*/
private fun parse(inBytes: ByteArray): Document
{
	val vFactory = DocumentBuilderFactory.newInstance()						// fabrique d'analyseurs
	vFactory.isNamespaceAware = true
	vFactory.isExpandEntityReferences = false
	runCatching { vFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
	return vFactory.newDocumentBuilder().parse(ByteArrayInputStream(inBytes))
}

/*
	Enfants directs d'un élément portant un nom local, quel que soit l'espace
	de noms.
	inParent : élément parent
	inLocal  : nom local
*/
private fun elements(
	inParent : Element,
	inLocal  : String
): List<Element> = elements(inParent, null, inLocal)

/*
	Enfants directs d'un élément portant un nom local dans un espace de noms
	(ou dans n'importe lequel si inNs est null).
	inParent : élément parent
	inNs     : espace de noms ; null pour tous
	inLocal  : nom local
*/
private fun elements(
	inParent : Element,
	inNs     : String?,
	inLocal  : String
): List<Element>
{
	val vFound = mutableListOf<Element>()										// éléments trouvés
	/*
		Parcourt le sous-arbre en profondeur.
		inNode : élément visité
	*/
	fun walk(inNode: Element)
	{
		var vChild = inNode.firstChild											// enfant courant
		while (vChild != null)
		{
			if (vChild is Element)
			{
				if ((vChild.localName ?: vChild.nodeName) == inLocal && (inNs == null || vChild.namespaceURI == inNs))
				{
					vFound += vChild
				}
				else
				{
					walk(vChild)
				}
			}
			vChild = vChild.nextSibling
		}
	}
	walk(inParent)
	return vFound
}
