package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.edit.DataEditor
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileId
import fr.maubin.patrimoineplus.domain.calc.scoped
import fr.maubin.patrimoineplus.domain.calc.wealthAt
import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kJoint
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.transfer
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Ownership
import fr.maubin.patrimoineplus.domain.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import fr.maubin.patrimoineplus.domain.calc.profileRemoval
import fr.maubin.patrimoineplus.data.edit.OpeningBalance
import fr.maubin.patrimoineplus.domain.calc.holderLabel
import fr.maubin.patrimoineplus.domain.calc.accountLabel
import fr.maubin.patrimoineplus.domain.model.Account

/*
	Profils : reprise des données d'avant les profils (tout au profil
	« Moi », part détenue gardée) ; vue d'un profil (ses comptes, à sa part)
	et vue Famille (tous les comptes, total des parts) ; règles d'édition des
	profils et des propriétaires ; fichier JSON et classeur relus à
	l'identique.
*/
class ProfilesTest
{
	private val kMat     = Profile("mat", "Mathieu", 0)									// premier profil
	private val kJulie   = Profile("julie", "Julie", 1)									// second profil
	private val kMine    = kChecking.copy(owners = listOf(Ownership("mat", 100)))			// compte de Mathieu
	private val kHers    = kLivret.copy(owners = listOf(Ownership("julie", 100)))			// livret de Julie
	private val kShared  = kJoint.copy(sharePercent = 100, owners = listOf(Ownership("mat", 50), Ownership("julie", 50)))	// compte joint du couple
	private val kTxs     = listOf(
		opening("2026-09-01", kMine, eur(2_000.0)),
		opening("2026-09-01", kHers, eur(10_000.0)),
		opening("2026-09-01", kShared, eur(1_000.0)),
		transfer("2026-09-10", kMine, kHers, eur(200.0)),								// Mathieu verse sur le livret de Julie
		standard("2026-09-12", kShared, -eur(100.0), kGroceries)						// courses sur le joint
	)																				// opérations
	private val kFamily  = DataSet(listOf(kMine, kHers, kShared), listOf(kGroceries), emptyList(), kTxs, emptyList(), emptyList(), profiles = listOf(kMat, kJulie))	// données du couple

	/*
		Vue d'un profil : ses comptes à sa part (joint à 50 %), les opérations
		qui touchent ses comptes (le virement vers le livret de Julie reste
		chez Mathieu) ; vue Famille : tous les comptes à 100 %.
	*/
	@Test
	fun scopes()
	{
		val vToday  = d("2026-10-01")														// date
		val vWealth = { inData: DataSet -> wealthAt(inData.accounts, Ledger(inData.transactions), vToday, emptyList()).totalCents }	// patrimoine compté
		val vMat    = kFamily.scoped("mat")													// vue de Mathieu
		assertEquals(listOf("courant", "joint"), vMat.accounts.map { it.id })
		assertEquals(listOf(100, 50), vMat.accounts.map { it.sharePercent })
		assertEquals(eur(1_800.0 + 450.0), vWealth(vMat))
		assertEquals(4, vMat.transactions.size)											// sans le solde initial du livret de Julie
		val vJulie  = kFamily.scoped("julie")												// vue de Julie
		assertEquals(eur(10_200.0 + 450.0), vWealth(vJulie))
		val vAll    = kFamily.scoped(null)													// vue Famille
		assertEquals(eur(1_800.0 + 10_200.0 + 900.0), vWealth(vAll))
		assertEquals(vWealth(vAll), vWealth(vMat) + vWealth(vJulie))						// la famille est la somme des profils
		assertEquals(kFamily.scoped(null), kFamily.scoped("inconnu"))					// profil disparu : vue Famille
	}

	/*
		Données d'avant les profils : un profil « Moi » propriétaire de chaque
		compte pour sa part détenue ; vue Famille identique à l'ancienne vue.
	*/
	@Test
	fun legacyData()
	{
		val vOld   = backupFileOf(DataSet(listOf(kChecking, kJoint), listOf(kGroceries), emptyList(), listOf(opening("2026-09-01", kJoint, eur(1_000.0))), emptyList(), emptyList()))	// fichier d'avant
		val vRead  = (decodeBackup(encodeBackup(vOld.copy(profiles = emptyList(), accounts = vOld.accounts.map { it.copy(owners = emptyList()) }))) as BackupRead.Valid).file	// relu
		assertEquals(listOf(kDefaultProfileId), vRead.profiles.map { it.id })
		assertEquals(listOf("Moi"), vRead.profiles.map { it.name })
		assertEquals(listOf(50), vRead.accounts.single { it.id == kJoint.id }.owners.map { it.percent })
		assertEquals(50, vRead.toDataSet().scoped(null).accounts.single { it.id == kJoint.id }.sharePercent)
		assertEquals(50, vRead.toDataSet().scoped(kDefaultProfileId).accounts.single { it.id == kJoint.id }.sharePercent)
	}

	/*
		Édition : profil sans nom ou en double refusé ; renommage ; création
		d'un compte à deux propriétaires ; parts au-delà de 100 % ou profil
		inconnu refusés ; récapitulatif de suppression (comptes supprimés,
		parts retirées) ; suppression : ses comptes seuls supprimés avec leurs
		opérations, sa part retirée des comptes partagés ; dernier profil
		refusé.
	*/
	@Test
	fun editing()
	{
		val vEditor = DataEditor(Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris")))	// moteur
		var vFile   = backupFileOf(DataSet(listOf(kChecking), listOf(kGroceries), emptyList(), emptyList(), emptyList(), emptyList()))	// un profil « Moi »
		assertTrue(vEditor.addProfile(vFile, "  ") is EditResult.Refused)
		assertTrue(vEditor.addProfile(vFile, "moi") is EditResult.Refused)
		assertTrue(vEditor.removeProfile(vFile, kDefaultProfileId) is EditResult.Refused)
		val vAdded  = vEditor.addProfile(vFile, "Julie") as EditResult.Done				// second profil
		vFile = vAdded.file
		val vJulie  = vAdded.id!!																// son identifiant
		assertTrue(vEditor.renameProfile(vFile, vJulie, "Moi") is EditResult.Refused)
		vFile = (vEditor.renameProfile(vFile, vJulie, "Julie M.") as EditResult.Done).file
		assertEquals(listOf("Moi", "Julie M."), vFile.profiles.map { it.name })
		assertTrue(vEditor.createAccount(vFile, "Joint", AccountType.CHECKING, null, 100, null, listOf(Ownership(kDefaultProfileId, 60), Ownership(vJulie, 50))) is EditResult.Refused)
		assertTrue(vEditor.createAccount(vFile, "Joint", AccountType.CHECKING, null, 100, null, listOf(Ownership("personne", 50))) is EditResult.Refused)
		val vJoint  = vEditor.createAccount(vFile, "Joint", AccountType.CHECKING, null, 100, null, listOf(Ownership(kDefaultProfileId, 50), Ownership(vJulie, 50))) as EditResult.Done	// compte joint
		vFile = vJoint.file
		assertEquals(100, vFile.accounts.single { it.id == vJoint.id }.sharePercent)
		val vPlan   = profileRemoval(vFile.toDataSet(), kDefaultProfileId)					// récapitulatif pour « Moi »
		assertEquals(listOf(kChecking.id), vPlan.removed.map { it.account.id })
		assertEquals(listOf(Triple(50, 100, 50)), vPlan.reduced.map { Triple(it.share, it.before, it.after) })
		val vOwn    = vEditor.createAccount(vFile, "Livret de Julie", AccountType.SAVINGS, null, 100, OpeningBalance(eur(500.0), d("2026-09-01")), listOf(Ownership(vJulie, 100))) as EditResult.Done	// compte de Julie seule
		vFile = vOwn.file
		assertEquals(listOf(1), profileRemoval(vFile.toDataSet(), vJulie).removed.map { it.operations })
		vFile = (vEditor.removeProfile(vFile, vJulie) as EditResult.Done).file
		assertTrue(vFile.accounts.none { it.id == vOwn.id })								// son livret supprimé
		assertTrue(vFile.transactions.none { inTx -> inTx.postings.any { it.accountId == vOwn.id } })	// avec son solde initial
		assertEquals(listOf(kDefaultProfileId), vFile.accounts.single { it.id == vJoint.id }.owners.map { it.profileId })
		assertEquals(50, vFile.accounts.single { it.id == vJoint.id }.sharePercent)
		assertTrue(decodeBackup(encodeBackup(vFile)) is BackupRead.Valid)
	}

	/*
		Classeur et JSON d'une famille relus à l'identique (profils, parts).
	*/
	@Test
	fun roundTrip()
	{
		val vFile  = backupFileOf(kFamily)													// fichier du couple
		assertEquals(BackupRead.Valid(vFile), decodeBackup(encodeBackup(vFile)))
		val vBytes = ByteArrayOutputStream().also { writeBackupXlsx(vFile, it) }.toByteArray()	// classeur
		val vRead  = (readBackupXlsx(ByteArrayInputStream(vBytes)) as BackupRead.Valid).file	// classeur relu
		assertEquals(syncFingerprint(vFile), syncFingerprint(vRead))						// même contenu (l'ordre des opérations suit le classeur)
		assertEquals(vFile.profiles, vRead.profiles)
		assertEquals(vFile.accounts.map { it.owners }, vRead.accounts.map { it.owners })
		assertEquals(listOf("Mathieu", "Julie"), vFile.profiles.map { it.name })
	}

	/*
		Titulaire d'un compte, déduit de ses propriétaires : un profil, ou
		plusieurs réunis (dans l'ordre des profils) ; rien avec un seul profil ;
		nom du compte suivi du titulaire dans les listes.
	*/
	@Test
	fun holders()
	{
		val vMe    = Profile("p1", "Moi", 0)													// profils
		val vAlex  = Profile("p2", "Alex", 1)
		val vSam   = Profile("p3", "Sam", 2)
		val vAll   = listOf(vSam, vAlex, vMe)													// dans le désordre
		val vSolo  = Account("a", "Compte courant", AccountType.CHECKING, owners = listOf(Ownership("p2", 100)))	// compte d'Alex
		val vJoint = Account("b", "Compte joint", AccountType.CHECKING, owners = listOf(Ownership("p2", 50), Ownership("p1", 50)))	// compte partagé
		val vThree = vJoint.copy(owners = listOf(Ownership("p3", 40), Ownership("p1", 30), Ownership("p2", 30)))	// à trois
		assertEquals("Alex", holderLabel(vSolo, vAll))
		assertEquals("Moi et Alex", holderLabel(vJoint, vAll))
		assertEquals("Moi, Alex et Sam", holderLabel(vThree, vAll))
		assertEquals(null, holderLabel(vSolo, listOf(vAlex)))
		assertEquals(null, holderLabel(vSolo.copy(owners = emptyList()), vAll))
		assertEquals("Compte courant · Alex", accountLabel(vSolo, vAll))
		assertEquals("Compte courant", accountLabel(vSolo, listOf(vAlex)))
	}
}

