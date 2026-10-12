package fr.maubin.patrimoineplus.data.bank

import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/*
	Ligne d'un relevé exporté par la banque.
	date        : date de l'opération
	label       : libellé de la banque
	amountCents : montant signé, en centimes (négatif = débit)
*/
data class BankLine(
	val date        : LocalDate,
	val label       : String,
	val amountCents : Long
)

/*
	Résultat de la lecture d'un relevé de la banque.
*/
sealed interface BankRead
{
	/*
		Relevé lu.
		lines   : opérations, dans l'ordre du fichier
		skipped : lignes du tableau ignorées (illisibles), pour le signaler
	*/
	data class Valid(
		val lines   : List<BankLine>,
		val skipped : Int
	) : BankRead

	/*
		Fichier refusé.
		message : raison, pour l'utilisateur
	*/
	data class Invalid(val message: String) : BankRead
}

private val kDateFormats = listOf("dd/MM/uuuu", "d/M/uuuu", "uuuu-MM-dd", "dd-MM-uuuu", "dd.MM.uuuu", "dd/MM/uu")
	.map { DateTimeFormatter.ofPattern(it).withResolverStyle(ResolverStyle.STRICT) }	// formats de date des banques

/*
	Rôle d'une colonne du relevé, reconnu à son en-tête.
*/
private enum class Role { DATE, VALUE_DATE, LABEL, DETAIL, AMOUNT, DEBIT, CREDIT, OTHER }

/*
	Lit un relevé exporté au format CSV par le site d'une banque, sans
	réglage : encodage (UTF-8 ou Windows-1252), séparateur (« ; », « , »,
	tabulation), lignes d'informations avant le tableau, colonnes reconnues à
	leur en-tête (date d'opération, libellé, montant signé ou colonnes Débit et
	Crédit), dates et montants à la française.
	inBytes : contenu du fichier
*/
fun readBankCsv(inBytes: ByteArray): BankRead
{
	val vText  = decode(inBytes).removePrefix("\uFEFF")							// texte du fichier
	val vRows  = vText.lines().filter { it.isNotBlank() }							// lignes non vides
	if (vRows.isEmpty())
	{
		return BankRead.Invalid("Fichier vide.")
	}
	val vSep   = listOf(';', '\t', ',').maxBy { inSep -> vRows.take(30).count { it.count { inChar -> inChar == inSep } >= 2 } }	// séparateur le plus présent
	val vCells = vRows.map { splitCsv(it, vSep) }									// cellules de chaque ligne
	val vHead  = vCells.indexOfFirst { inRow -> roles(inRow).let { Role.DATE in it && (Role.AMOUNT in it || Role.DEBIT in it || Role.CREDIT in it) } }	// ligne d'en-tête
	if (vHead < 0)
	{
		return BankRead.Invalid("En-tête du tableau introuvable : il faut au moins une colonne de date et une colonne de montant (ou Débit et Crédit).")
	}
	val vRoles  = roles(vCells[vHead])												// rôle de chaque colonne
	val vDate   = vRoles.indexOf(Role.DATE)										// colonne de la date
	val vLabels = vRoles.indices.filter { vRoles[it] == Role.LABEL }.ifEmpty { vRoles.indices.filter { vRoles[it] == Role.DETAIL } }	// colonnes du libellé
	val vDetail = vRoles.indices.filter { vRoles[it] == Role.DETAIL && it !in vLabels }	// compléments du libellé
	var vSkipped = 0																	// lignes illisibles
	val vLines = vCells.drop(vHead + 1).mapNotNull { inRow ->
		val vDay    = inRow.getOrNull(vDate)?.let(::parseDay)						// date lue
		val vAmount = amountOf(inRow, vRoles)										// montant lu
		if (vDay == null || vAmount == null)
		{
			if (inRow.any { it.isNotBlank() }) vSkipped++
			return@mapNotNull null
		}
		val vLabel  = (vLabels + vDetail).mapNotNull { inRow.getOrNull(it)?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString(" ")	// libellé complet
		BankLine(vDay, vLabel.replace(Regex("\\s+"), " "), vAmount)
	}																				// opérations lues
	if (vLines.isEmpty())
	{
		return BankRead.Invalid("Aucune opération lisible sous l’en-tête du tableau.")
	}
	return BankRead.Valid(vLines, vSkipped)
}

/*
	Texte du fichier : UTF-8 s'il est valide, sinon Windows-1252 (encodage
	habituel des exports bancaires sous Windows).
	inBytes : contenu
*/
private fun decode(inBytes: ByteArray): String =
	try
	{
		Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
			.decode(ByteBuffer.wrap(inBytes)).toString()
	}
	catch (inError: CharacterCodingException)
	{
		String(inBytes, Charset.forName("windows-1252"))
	}

/*
	Découpe une ligne CSV (guillemets doubles, guillemet doublé à l'intérieur).
	inLine : ligne
	inSep  : séparateur
*/
internal fun splitCsv(
	inLine : String,
	inSep  : Char
): List<String>
{
	val vCells  = mutableListOf<String>()												// cellules
	val vCell   = StringBuilder()														// cellule en cours
	var vQuoted = false																	// entre guillemets
	var vIndex  = 0																		// position
	while (vIndex < inLine.length)
	{
		val vChar = inLine[vIndex]														// caractère
		when
		{
			vChar == '"' && vQuoted && inLine.getOrNull(vIndex + 1) == '"' -> { vCell.append('"'); vIndex++ }
			vChar == '"'                                                 -> vQuoted = !vQuoted
			vChar == inSep && !vQuoted                                   -> { vCells += vCell.toString().trim(); vCell.clear() }
			else                                                         -> vCell.append(vChar)
		}
		vIndex++
	}
	vCells += vCell.toString().trim()
	return vCells
}

/*
	Rôle de chaque colonne d'une ligne d'en-tête possible.
	inRow : cellules
*/
private fun roles(inRow: List<String>): List<Role>
{
	var vDateSeen = false																// date d'opération déjà trouvée
	return inRow.map { inCell ->
		val vName = plain(inCell)														// en-tête simplifié
		when
		{
			vName.contains("valeur") && vName.contains("date") -> Role.VALUE_DATE
			vName.startsWith("date") || vName == "dateop" || vName.contains("date operation") || vName.contains("date de l operation") ->
				if (vDateSeen) Role.VALUE_DATE else Role.DATE.also { vDateSeen = true }
			vName.contains("debit")                     -> Role.DEBIT
			vName.contains("credit")                    -> Role.CREDIT
			vName.contains("montant") || vName == "amount" || vName.startsWith("amount") -> Role.AMOUNT
			vName.contains("libelle") || vName.contains("detail") || vName == "label" || vName.contains("intitule") || vName.contains("description") || vName.contains("nature") -> if (vName.contains("court") || vName == "label" || vName == "libelle") Role.LABEL else Role.DETAIL
			else                                        -> Role.OTHER
		}
	}
}

/*
	Montant signé d'une ligne : colonne Montant, ou Crédit moins Débit (le
	débit peut être écrit avec ou sans signe) ; null si illisible.
	inRow   : cellules
	inRoles : rôle de chaque colonne
*/
private fun amountOf(
	inRow   : List<String>,
	inRoles : List<Role>
): Long?
{
	val vAmount = inRoles.indexOf(Role.AMOUNT)											// colonne du montant
	if (vAmount >= 0)
	{
		return inRow.getOrNull(vAmount)?.let(::parseCents)
	}
	val vDebit  = inRoles.indexOf(Role.DEBIT).let { inRow.getOrNull(it) }?.let(::parseCents)	// débit
	val vCredit = inRoles.indexOf(Role.CREDIT).let { inRow.getOrNull(it) }?.let(::parseCents)	// crédit
	if (vDebit == null && vCredit == null)
	{
		return null
	}
	return (vCredit?.let { kotlin.math.abs(it) } ?: 0L) - (vDebit?.let { kotlin.math.abs(it) } ?: 0L)
}

/*
	Montant en centimes d'un texte de banque (« -1 234,56 », « 1.234,56 € »,
	« +12.5 ») ; null s'il est vide ou illisible.
	inText : texte
*/
internal fun parseCents(inText: String): Long?
{
	var vClean = inText.replace("€", "").replace("EUR", "").replace(" ", "").replace("\u00A0", "").replace("\u202F", "").replace("'", "").trim()	// sans espaces ni devise
	if (vClean.isEmpty()) return null
	val vNegative = vClean.startsWith("-") || vClean.endsWith("-") || (vClean.startsWith("(") && vClean.endsWith(")"))	// montant négatif
	vClean = vClean.trim('-', '+', '(', ')')
	val vComma = vClean.lastIndexOf(',')												// dernière virgule
	val vDot   = vClean.lastIndexOf('.')												// dernier point
	vClean = when
	{
		vComma > vDot -> vClean.replace(".", "").replace(',', '.')
		vDot > vComma -> vClean.replace(",", "")
		else          -> vClean
	}
	val vValue = vClean.toBigDecimalOrNull() ?: return null							// montant lu
	val vCents = vValue.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()	// en centimes
	return if (vNegative) -vCents else vCents
}

/*
	Date d'une cellule, dans l'un des formats des banques ; null sinon.
	inText : texte
*/
internal fun parseDay(inText: String): LocalDate?
{
	val vText = inText.trim().take(10)													// partie date (sans heure)
	return kDateFormats.firstNotNullOfOrNull { inFormat -> runCatching { LocalDate.parse(vText, inFormat) }.getOrNull() }
}

/*
	Texte en minuscules, sans accents ni ponctuation (comparaison d'en-têtes
	et de libellés).
	inText : texte
*/
internal fun plain(inText: String): String =
	Normalizer.normalize(inText.lowercase(), Normalizer.Form.NFD)
		.replace(Regex("\\p{M}"), "")
		.replace(Regex("[^a-z0-9]+"), " ")
		.trim()
