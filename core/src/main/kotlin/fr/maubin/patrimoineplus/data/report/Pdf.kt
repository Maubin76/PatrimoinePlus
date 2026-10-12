package fr.maubin.patrimoineplus.data.report

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.Normalizer
import java.util.Locale

private val kWinAnsi = Charset.forName("windows-1252")							// encodage des polices standard

// Largeurs (millièmes de cadratin) des caractères 32 à 126 d'Helvetica.
private val kRegularWidths = intArrayOf(
	278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
	556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
	1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
	667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
	333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
	556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584
)

// Largeurs des caractères 32 à 126 d'Helvetica-Bold.
private val kBoldWidths = intArrayOf(
	278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
	556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
	975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
	667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
	333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
	611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584
)

/*
	Alignement horizontal d'un texte par rapport à son abscisse.
*/
enum class Align { LEFT, RIGHT, CENTER }

/*
	Page d'un document PDF en construction : textes (Helvetica, normal ou
	gras), filets et rectangles gris. Coordonnées en points depuis le coin
	en haut à gauche (converties pour le PDF, dont l'origine est en bas).
	inHeight : hauteur de la page, en points
*/
class PdfPage internal constructor(private val inHeight: Float)
{
	internal val fContent = StringBuilder()										// flux de contenu de la page

	/*
		Écrit un texte sur une ligne.
		inX     : abscisse de référence
		inY     : ordonnée de la ligne de base, depuis le haut
		inText  : texte
		inSize  : corps, en points
		inBold  : gras
		inGrey  : niveau de gris (0 noir, 1 blanc)
		inAlign : alignement par rapport à inX
	*/
	fun text(
		inX     : Float,
		inY     : Float,
		inText  : String,
		inSize  : Float   = 10f,
		inBold  : Boolean = false,
		inGrey  : Float   = 0f,
		inAlign : Align   = Align.LEFT
	)
	{
		val vClean = pdfText(inText)													// texte représentable
		val vWidth = textWidth(vClean, inSize, inBold)									// largeur
		val vLeft  = when (inAlign) { Align.LEFT -> inX; Align.RIGHT -> inX - vWidth; Align.CENTER -> inX - vWidth / 2f }	// abscisse de départ
		fContent.append("BT ${num(inGrey)} g /${if (inBold) "F2" else "F1"} ${num(inSize)} Tf ${num(vLeft)} ${num(inHeight - inY)} Td (")
		vClean.toByteArray(kWinAnsi).forEach { inByte ->
			val vCode = inByte.toInt() and 0xFF												// octet du caractère
			when
			{
				vCode == '('.code || vCode == ')'.code || vCode == '\\'.code -> fContent.append('\\').append(vCode.toChar())
				vCode < 32 || vCode > 126                                   -> fContent.append('\\').append(Integer.toOctalString(vCode).padStart(3, '0'))
				else                                                        -> fContent.append(vCode.toChar())
			}
		}
		fContent.append(") Tj ET\n")
	}

	/*
		Trace un filet horizontal.
		inX1    : début
		inX2    : fin
		inY     : ordonnée, depuis le haut
		inGrey  : niveau de gris
		inWidth : épaisseur, en points
	*/
	fun line(
		inX1    : Float,
		inX2    : Float,
		inY     : Float,
		inGrey  : Float = 0.85f,
		inWidth : Float = 0.6f
	)
	{
		fContent.append("${num(inGrey)} G ${num(inWidth)} w ${num(inX1)} ${num(inHeight - inY)} m ${num(inX2)} ${num(inHeight - inY)} l S\n")
	}

	/*
		Remplit un rectangle.
		inX      : gauche
		inY      : haut, depuis le haut de la page
		inWidth  : largeur
		inHeight : hauteur
		inGrey   : niveau de gris
	*/
	fun rect(
		inX      : Float,
		inY      : Float,
		inWidth  : Float,
		inHeight : Float,
		inGrey   : Float
	)
	{
		fContent.append("${num(inGrey)} g ${num(inX)} ${num(this.inHeight - inY - inHeight)} ${num(inWidth)} ${num(inHeight)} re f\n")
	}
}

/*
	Document PDF minimal (PDF 1.4) : pages A4, polices standard Helvetica et
	Helvetica-Bold (aucune police à embarquer, accents et euro par
	l'encodage WinAnsi), textes, filets et rectangles. Suffisant pour un bilan
	imprimable, sans bibliothèque externe.
*/
class PdfDocument
{
	private val fPages = mutableListOf<PdfPage>()									// pages

	// Largeur d'une page A4, en points.
	val width = 595.28f

	// Hauteur d'une page A4, en points.
	val height = 841.89f

	/*
		Ajoute une page et la rend pour y dessiner.
	*/
	fun newPage(): PdfPage = PdfPage(height).also { fPages += it }

	// Nombre de pages.
	val pageCount: Int get() = fPages.size

	// Pages, dans l'ordre.
	val pages: List<PdfPage> get() = fPages

	/*
		Contenu du fichier PDF.
		inTitle : titre du document (propriétés du fichier)
	*/
	fun toBytes(inTitle: String): ByteArray
	{
		val vOut     = ByteArrayOutputStream()										// fichier
		val vOffsets = mutableListOf<Int>()											// position de chaque objet
		val vWrite   = { inText: String -> vOut.write(inText.toByteArray(kWinAnsi)) }	// écriture d'un texte
		val vObject  = { inBody: String -> vOffsets += vOut.size(); vWrite("${vOffsets.size} 0 obj\n$inBody\nendobj\n") }	// écriture d'un objet
		vWrite("%PDF-1.4\n%âãÏÓ\n")
		val vPageIds = fPages.indices.map { 6 + it * 2 }								// numéros des objets page
		vObject("<< /Type /Catalog /Pages 2 0 R >>")
		vObject("<< /Type /Pages /Kids [${vPageIds.joinToString(" ") { "$it 0 R" }}] /Count ${fPages.size} >>")
		vObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
		vObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
		vObject("<< /Title (${pdfText(inTitle).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")}) /Producer (PatrimoinePlus) >>")
		fPages.forEach { inPage ->
			val vNumber  = vOffsets.size + 1											// numéro de l'objet page
			val vContent = inPage.fContent.toString()									// flux de la page
			vObject("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(width)} ${num(height)}] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${vNumber + 1} 0 R >>")
			vObject("<< /Length ${vContent.toByteArray(kWinAnsi).size} >>\nstream\n$vContent\nendstream")
		}
		val vXref = vOut.size()														// position de la table des objets
		vWrite("xref\n0 ${vOffsets.size + 1}\n0000000000 65535 f \n")
		vOffsets.forEach { vWrite("${it.toString().padStart(10, '0')} 00000 n \n") }
		vWrite("trailer\n<< /Size ${vOffsets.size + 1} /Root 1 0 R /Info 5 0 R >>\nstartxref\n$vXref\n%%EOF\n")
		return vOut.toByteArray()
	}
}

/*
	Largeur d'un texte en points, d'après les métriques d'Helvetica (lettres
	accentuées comptées comme leur lettre de base).
	inText : texte
	inSize : corps
	inBold : gras
*/
fun textWidth(
	inText : String,
	inSize : Float,
	inBold : Boolean = false
): Float
{
	val vTable = if (inBold) kBoldWidths else kRegularWidths							// métriques
	return inText.sumOf { inChar ->
		val vBase = Normalizer.normalize(inChar.toString(), Normalizer.Form.NFD).first()	// lettre de base
		when
		{
			vBase.code in 32..126 -> vTable[vBase.code - 32]
			inChar == '€' || inChar == '«' || inChar == '»' -> 556
			inChar == '…' || inChar == '—'                -> 1000
			else                                            -> 556
		}
	} * inSize / 1000f
}

/*
	Texte représentable par les polices standard (WinAnsi) : espaces fines et
	signes absents remplacés par leur équivalent le plus proche.
	inText : texte
*/
internal fun pdfText(inText: String): String =
	inText
		.replace('\u202F', ' ').replace('\u2009', ' ').replace('\u2212', '-').replace("\u2192", "->")
		.map { inChar -> if (kWinAnsi.newEncoder().canEncode(inChar)) inChar else '?' }
		.joinToString("")

/*
	Nombre écrit pour le PDF (point décimal, deux décimales au plus).
	inValue : nombre
*/
private fun num(inValue: Float): String = String.format(Locale.ROOT, "%.2f", inValue).trimEnd('0').trimEnd('.')
