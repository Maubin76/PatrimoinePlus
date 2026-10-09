package fr.maubin.patrimoineplus.data.backup.xlsx

import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val kExcelEpoch = LocalDate.of(1899, 12, 30)							// origine des dates Excel

/*
	Présentation d'une cellule (indice dans styles.xml).
*/
enum class CellStyle(val index: Int)
{
	TEXT(0),																	// texte ordinaire
	HEADER(1),																	// en-tête : gras, filet dessous
	TECH_HEADER(2),																// en-tête technique : gris
	DATE(3),																	// date jj/mm/aaaa
	EUROS(4),																	// montant en euros, négatifs en rouge
	PRICE(5),																	// cours : 2 à 4 décimales
	TECH(6),																	// valeur technique : gris
	NUMBER(7)																	// nombre brut (quantité, pourcentage)
}

/*
	Cellule d'une feuille.
*/
sealed interface Cell
{
	/*
		Texte.
		text  : contenu
		style : présentation
	*/
	data class Text(val text: String, val style: CellStyle = CellStyle.TEXT) : Cell

	/*
		Nombre décimal exact.
		value : valeur
		style : présentation
	*/
	data class Number(val value: BigDecimal, val style: CellStyle = CellStyle.NUMBER) : Cell

	/*
		Date, écrite en numéro de série Excel.
		date : date
	*/
	data class Date(val date: LocalDate) : Cell

	// Cellule vide.
	data object Empty : Cell
}

/*
	Colonne d'une feuille.
	header    : intitulé (sert aussi de clé à la lecture)
	width     : largeur, en caractères
	technical : colonne technique (identifiants) : grisée et repliée
*/
data class Column(
	val header    : String,
	val width     : Double,
	val technical : Boolean = false
)

/*
	Feuille du classeur.
	name    : nom de l'onglet (31 caractères au plus)
	columns : colonnes, dans l'ordre
	rows    : lignes de données (sans l'en-tête), autant de cellules que de colonnes au plus
*/
data class Sheet(
	val name    : String,
	val columns : List<Column>,
	val rows    : List<List<Cell>>
)

/*
	Écrit un classeur .xlsx (Office Open XML) : en-têtes figés et filtrables,
	largeurs de colonnes, formats de dates et de montants, colonnes techniques
	repliées. Aucune dépendance : archive ZIP et XML écrits à la main.
	inSheets : feuilles, dans l'ordre des onglets
	inOut    : flux de sortie (non fermé)
*/
fun writeXlsx(
	inSheets : List<Sheet>,
	inOut    : OutputStream
)
{
	val vZip = ZipOutputStream(inOut)											// archive du classeur
	/*
		Ajoute un fichier XML à l'archive.
		inPath : chemin dans l'archive
		inXml  : contenu
	*/
	fun put(
		inPath : String,
		inXml  : String
	)
	{
		vZip.putNextEntry(ZipEntry(inPath))
		vZip.write(inXml.toByteArray(Charsets.UTF_8))
		vZip.closeEntry()
	}
	put("[Content_Types].xml", contentTypes(inSheets.size))
	put("_rels/.rels", kRootRels)
	put("xl/workbook.xml", workbook(inSheets))
	put("xl/_rels/workbook.xml.rels", workbookRels(inSheets.size))
	put("xl/styles.xml", kStyles)
	inSheets.forEachIndexed { inIndex, inSheet -> put("xl/worksheets/sheet${inIndex + 1}.xml", sheetXml(inSheet)) }
	vZip.finish()
}

/*
	Référence de colonne Excel : 0 → A, 25 → Z, 26 → AA.
	inIndex : indice de colonne (à partir de 0)
*/
internal fun columnName(inIndex: Int): String
{
	var vIndex = inIndex + 1													// numéro à partir de 1
	val vName  = StringBuilder()												// lettres, de droite à gauche
	while (vIndex > 0)
	{
		val vRest = (vIndex - 1) % 26											// lettre courante
		vName.insert(0, 'A' + vRest)
		vIndex = (vIndex - 1) / 26
	}
	return vName.toString()
}

/*
	Numéro de série Excel d'une date (jours depuis le 30/12/1899).
	inDate : date
*/
internal fun excelSerial(inDate: LocalDate): Long = ChronoUnit.DAYS.between(kExcelEpoch, inDate)

/*
	Date d'un numéro de série Excel.
	inSerial : numéro de série (partie entière)
*/
internal fun fromExcelSerial(inSerial: Long): LocalDate = kExcelEpoch.plusDays(inSerial)

/*
	Échappe un texte pour le XML (caractères de contrôle interdits retirés).
	inText : texte
*/
private fun escape(inText: String): String
{
	val vOut = StringBuilder(inText.length)									// texte échappé
	inText.forEach { inChar ->
		when
		{
			inChar == '&'                                      -> vOut.append("&amp;")
			inChar == '<'                                      -> vOut.append("&lt;")
			inChar == '>'                                      -> vOut.append("&gt;")
			inChar == '"'                                      -> vOut.append("&quot;")
			inChar < ' ' && inChar != '\t' && inChar != '\n' && inChar != '\r' -> Unit
			else                                               -> vOut.append(inChar)
		}
	}
	return vOut.toString()
}

/*
	XML d'une feuille : volet figé sous l'en-tête, largeurs, données, filtre.
	inSheet : feuille
*/
private fun sheetXml(inSheet: Sheet): String
{
	val vLast = columnName(inSheet.columns.size - 1)							// dernière colonne
	val vXml  = StringBuilder()													// contenu
	vXml.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
	vXml.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
	vXml.append("""<sheetPr><outlinePr summaryRight="0"/></sheetPr>""")
	vXml.append("""<dimension ref="A1:$vLast${inSheet.rows.size + 1}"/>""")
	vXml.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
	vXml.append("""<sheetFormatPr defaultRowHeight="15" outlineLevelCol="1"/>""")
	vXml.append("<cols>")
	inSheet.columns.forEachIndexed { inIndex, inColumn ->
		val vTech = if (inColumn.technical) """ hidden="1" outlineLevel="1"""" else ""	// colonne technique repliée
		vXml.append("""<col min="${inIndex + 1}" max="${inIndex + 1}" width="${inColumn.width}" customWidth="1"$vTech/>""")
	}
	vXml.append("</cols><sheetData>")
	vXml.append("""<row r="1">""")
	inSheet.columns.forEachIndexed { inIndex, inColumn ->
		appendCell(vXml, "${columnName(inIndex)}1", Cell.Text(inColumn.header, if (inColumn.technical) CellStyle.TECH_HEADER else CellStyle.HEADER))
	}
	vXml.append("</row>")
	inSheet.rows.forEachIndexed { inRow, inCells ->
		vXml.append("""<row r="${inRow + 2}">""")
		inCells.forEachIndexed { inIndex, inCell -> appendCell(vXml, "${columnName(inIndex)}${inRow + 2}", inCell) }
		vXml.append("</row>")
	}
	vXml.append("</sheetData>")
	vXml.append("""<autoFilter ref="A1:$vLast${inSheet.rows.size + 1}"/>""")
	vXml.append("""<pageMargins left="0.5" right="0.5" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>""")
	vXml.append("</worksheet>")
	return vXml.toString()
}

/*
	Ajoute une cellule à une ligne ; une cellule vide n'est pas écrite.
	inXml  : XML en cours
	inRef  : référence (A1)
	inCell : cellule
*/
private fun appendCell(
	inXml  : StringBuilder,
	inRef  : String,
	inCell : Cell
)
{
	when (inCell)
	{
		is Cell.Text   -> if (inCell.text.isNotEmpty())
		{
			inXml.append("""<c r="$inRef" s="${inCell.style.index}" t="inlineStr"><is><t xml:space="preserve">${escape(inCell.text)}</t></is></c>""")
		}
		is Cell.Number -> inXml.append("""<c r="$inRef" s="${inCell.style.index}"><v>${inCell.value.toPlainString()}</v></c>""")
		is Cell.Date   -> inXml.append("""<c r="$inRef" s="${CellStyle.DATE.index}"><v>${excelSerial(inCell.date)}</v></c>""")
		Cell.Empty     -> Unit
	}
}

/*
	Déclaration des types de contenu.
	inCount : nombre de feuilles
*/
private fun contentTypes(inCount: Int): String =
	"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
	"""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
	"""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
	"""<Default Extension="xml" ContentType="application/xml"/>""" +
	"""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
	"""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
	(1..inCount).joinToString("") { """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } +
	"</Types>"

/*
	Classeur : onglets et zones filtrées.
	inSheets : feuilles
*/
private fun workbook(inSheets: List<Sheet>): String =
	"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
	"""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
	"<sheets>" +
	inSheets.mapIndexed { inIndex, inSheet -> """<sheet name="${escape(inSheet.name)}" sheetId="${inIndex + 1}" r:id="rId${inIndex + 1}"/>""" }.joinToString("") +
	"</sheets><definedNames>" +
	inSheets.mapIndexed { inIndex, inSheet ->
		"""<definedName name="_xlnm._FilterDatabase" localSheetId="$inIndex" hidden="1">'${escape(inSheet.name)}'!${'$'}A${'$'}1:${'$'}${columnName(inSheet.columns.size - 1)}${'$'}${inSheet.rows.size + 1}</definedName>"""
	}.joinToString("") +
	"</definedNames></workbook>"

/*
	Relations du classeur : feuilles et styles.
	inCount : nombre de feuilles
*/
private fun workbookRels(inCount: Int): String =
	"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
	"""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
	(1..inCount).joinToString("") { """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>""" } +
	"""<Relationship Id="rId${inCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
	"</Relationships>"

private const val kRootRels =
	"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
	"""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
	"""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
	"</Relationships>"																// relations du paquet

// Styles : polices (normale, grasse, grise), filet sous l'en-tête, formats de nombres.
private const val kStyles =
	"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
	"""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
	"""<numFmts count="3">""" +
	"""<numFmt numFmtId="164" formatCode="dd/mm/yyyy"/>""" +
	"""<numFmt numFmtId="165" formatCode="#,##0.00\ &quot;€&quot;;[Red]\-#,##0.00\ &quot;€&quot;"/>""" +
	"""<numFmt numFmtId="166" formatCode="#,##0.00##"/>""" +
	"</numFmts>" +
	"""<fonts count="3">""" +
	"""<font><sz val="11"/><color rgb="FF0A0A0A"/><name val="Calibri"/><family val="2"/></font>""" +
	"""<font><b/><sz val="11"/><color rgb="FF0A0A0A"/><name val="Calibri"/><family val="2"/></font>""" +
	"""<font><sz val="10"/><color rgb="FF8A8A8A"/><name val="Calibri"/><family val="2"/></font>""" +
	"</fonts>" +
	"""<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
	"""<borders count="2">""" +
	"""<border><left/><right/><top/><bottom/><diagonal/></border>""" +
	"""<border><left/><right/><top/><bottom style="thin"><color rgb="FF0A0A0A"/></bottom><diagonal/></border>""" +
	"</borders>" +
	"""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
	"""<cellXfs count="8">""" +
	"""<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
	"""<xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"/>""" +
	"""<xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"/>""" +
	"""<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
	"""<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
	"""<xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
	"""<xf numFmtId="1" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/>""" +
	"""<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
	"</cellXfs>" +
	"""<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
	"</styleSheet>"
