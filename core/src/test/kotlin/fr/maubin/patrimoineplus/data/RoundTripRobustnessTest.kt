package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.decimal
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.validateBackup
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/*
	Aller-retour par le classeur avec des textes et des valeurs inhabituels :
	l'empreinte de synchronisation ne doit pas changer (sinon : import inutile
	ou faux conflit), le classeur doit rester lisible, et les valeurs saisies
	à la main dans un tableur doivent être lues correctement.
*/
class RoundTripRobustnessTest
{
	/*
		Textes avec espaces de bord, retour chariot, caractère de contrôle,
		U+FFFE et moitié isolée de caractère composé : le classeur reste
		lisible et l'empreinte du contenu relu est celle du contenu écrit.
	*/
	@Test
	fun oddTextsKeepFingerprint()
	{
		val vBase = demoBackupFile()												// démonstration
		val vOdd  = vBase.copy(
			accounts     = vBase.accounts.mapIndexed { i, a -> if (i == 0) a.copy(name = " ${a.name} ", institution = "Banque\u0007") else a },
			transactions = vBase.transactions.mapIndexed { i, t -> if (i == 0) t.copy(label = "Ligne 1\r\nLigne 2￾", notes = "note\uD800 ") else t }
		)																			// textes inhabituels
		val vBytes = ByteArrayOutputStream().also { writeBackupXlsx(vOdd, it) }.toByteArray()	// classeur
		val vRead  = readBackupXlsx(ByteArrayInputStream(vBytes))					// relecture
		assertTrue("lecture refusée : $vRead", vRead is BackupRead.Valid)
		assertEquals(syncFingerprint(vOdd), syncFingerprint((vRead as BackupRead.Valid).file))
	}

	/*
		Montants tapés en texte dans un tableur, à la française ou à
		l'anglaise, avec séparateurs de milliers.
	*/
	@Test
	fun decimalsTypedByHand()
	{
		assertEquals(BigDecimal("1234.56"), decimal("1.234,56"))
		assertEquals(BigDecimal("1234.56"), decimal("1,234.56"))
		assertEquals(BigDecimal("1234.56"), decimal("1 234,56 €"))
		assertEquals(BigDecimal("1234.56"), decimal("1234.56"))
		assertEquals(BigDecimal("-12.5"), decimal("-12,5"))
		assertEquals(BigDecimal("1234567.8"), decimal("1.234.567,8"))
	}

	/*
		Part détenue hors de 1 à 100 % : fichier refusé (avant : accepté, le
		compte comptait pour 0 % ou 150 % du patrimoine).
	*/
	@Test
	fun shareOutOfRangeIsRefused()
	{
		val vBase = demoBackupFile()												// démonstration
		listOf(0, 150).forEach { inShare ->
			val vBad = vBase.copy(accounts = vBase.accounts.mapIndexed { i, a -> if (i == 0) a.copy(sharePercent = inShare) else a })	// part invalide
			assertTrue(validateBackup(vBad) is BackupRead.Invalid)
		}
	}
}
