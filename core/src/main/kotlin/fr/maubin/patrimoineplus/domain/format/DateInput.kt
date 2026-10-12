package fr.maubin.patrimoineplus.domain.format

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val kFrenchDateIn  = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)	// date saisie
private val kFrenchDateOut = DateTimeFormatter.ofPattern("dd/MM/uuuu")								// date affichée dans un champ

/*
	Date saisie à la française (« 5/3/2026 », « 05/03/2026 ») ; null si elle
	est illisible ou impossible (31/02).
	inText : texte saisi
*/
fun parseFrenchDate(inText: String): LocalDate? = runCatching { LocalDate.parse(inText.trim(), kFrenchDateIn) }.getOrNull()

/*
	Date telle qu'elle s'écrit dans un champ de saisie (« 05/03/2026 »).
	inDate : date
*/
fun formatFrenchDate(inDate: LocalDate): String = inDate.format(kFrenchDateOut)
