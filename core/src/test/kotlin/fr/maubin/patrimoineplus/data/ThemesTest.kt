package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.edit.DataEditor
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.domain.calc.contrastRatio
import fr.maubin.patrimoineplus.domain.calc.contrastWarning
import fr.maubin.patrimoineplus.domain.calc.hexOf
import fr.maubin.patrimoineplus.domain.calc.parseHexColor
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Thèmes de couleurs personnalisés : codes hexadécimaux lus (avec ou sans
	#, forme courte, casse indifférente) ; contraste (WCAG) et alertes de
	lisibilité ; création, modification, nom en double ou couleur illisible
	refusés, suppression ; relus à l'identique en JSON et en classeur.
*/
class ThemesTest
{
	private val kColors = listOf("#016b8f", "219EBC", "#fb0", "#FB8500", "#8ECAE6")	// cinq couleurs tapées

	/*
		Lecture et écriture des couleurs ; contraste et alertes.
	*/
	@Test
	fun colors()
	{
		assertEquals(0xFF219EBCL, parseHexColor("219EBC"))
		assertEquals(0xFF219EBCL, parseHexColor(" #219ebc "))
		assertEquals(0xFFFFBB00L, parseHexColor("#fb0"))
		assertNull(parseHexColor("#21GEBC"))
		assertNull(parseHexColor("#21"))
		assertEquals("#016B8F", hexOf(parseHexColor("#016b8f")!!))
		assertEquals(21.0, contrastRatio(0xFF000000L, 0xFFFFFFFFL), 0.01)
		assertEquals(1.0, contrastRatio(0xFF219EBCL, 0xFF219EBCL), 0.01)
		assertEquals("Peu lisible en clair.", contrastWarning(0xFFFFEE88L))			// jaune pâle
		assertEquals("Peu lisible en sombre.", contrastWarning(0xFF0B1B26L))			// presque noir
		assertNull(contrastWarning(0xFF7B6CF6L))										// violet : lisible sur les deux
	}

	/*
		Création (couleurs normalisées), modification, refus, suppression ;
		fichier relu à l'identique.
	*/
	@Test
	fun editing()
	{
		val vEditor  = DataEditor(Clock.fixed(Instant.parse("2026-10-26T09:00:00Z"), ZoneId.of("Europe/Paris")))	// moteur
		var vFile    = backupFileOf(DataSet(listOf(kChecking), listOf(kGroceries), emptyList(), emptyList(), emptyList(), emptyList()))	// fichier
		val vCreated = vEditor.saveTheme(vFile, null, "Océan", kColors) as EditResult.Done	// thème créé
		vFile = vCreated.file
		assertEquals(listOf("#016B8F", "#219EBC", "#FFBB00", "#FB8500", "#8ECAE6"), vFile.themes.single().colors)
		assertTrue(vEditor.saveTheme(vFile, null, "océan", kColors) is EditResult.Refused)	// nom pris
		assertTrue(vEditor.saveTheme(vFile, null, " ", kColors) is EditResult.Refused)
		assertTrue(vEditor.saveTheme(vFile, null, "Forêt", kColors.dropLast(1)) is EditResult.Refused)
		assertTrue(vEditor.saveTheme(vFile, null, "Forêt", kColors.dropLast(1) + "vert") is EditResult.Refused)
		vFile = (vEditor.saveTheme(vFile, vCreated.id, "Océan clair", kColors) as EditResult.Done).file	// renommé
		assertEquals("Océan clair", vFile.themes.single().name)
		assertEquals(BackupRead.Valid(vFile), decodeBackup(encodeBackup(vFile)))
		val vBytes   = ByteArrayOutputStream().also { writeBackupXlsx(vFile, it) }.toByteArray()	// classeur
		assertEquals(vFile.themes, (readBackupXlsx(ByteArrayInputStream(vBytes)) as BackupRead.Valid).file.themes)
		assertEquals("Océan clair", vFile.toDataSet().themes.single().name)
		vFile = (vEditor.deleteTheme(vFile, vCreated.id!!) as EditResult.Done).file
		assertTrue(vFile.themes.isEmpty())
	}
}
