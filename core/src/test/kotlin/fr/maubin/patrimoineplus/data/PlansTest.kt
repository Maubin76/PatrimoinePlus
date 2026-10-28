package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.decodePlans
import fr.maubin.patrimoineplus.data.backup.encodePlans
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.backup.validateBackup
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.backup.xlsx.readXlsx
import fr.maubin.patrimoineplus.data.sync.syncFingerprint
import fr.maubin.patrimoineplus.domain.calc.kDefaultProfileId
import fr.maubin.patrimoineplus.domain.calc.kFamilyView
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.budget.Budget
import fr.maubin.patrimoineplus.ui.budget.BudgetLine
import fr.maubin.patrimoineplus.ui.budget.BudgetSection
import fr.maubin.patrimoineplus.ui.budget.ExpenseTheme
import fr.maubin.patrimoineplus.ui.goals.Goal
import fr.maubin.patrimoineplus.ui.goals.GoalKind
import fr.maubin.patrimoineplus.ui.goals.Goals
import fr.maubin.patrimoineplus.ui.goals.MonthGoal
import fr.maubin.patrimoineplus.ui.goals.MonthGoalKind
import fr.maubin.patrimoineplus.ui.goals.MonthPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import fr.maubin.patrimoineplus.data.backup.backupSheets
import fr.maubin.patrimoineplus.data.backup.xlsx.Cell
import fr.maubin.patrimoineplus.data.backup.xlsx.Sheet
import fr.maubin.patrimoineplus.data.backup.xlsx.Column
import fr.maubin.patrimoineplus.data.backup.xlsx.writeXlsx
import fr.maubin.patrimoineplus.data.backup.encodeBackup
import fr.maubin.patrimoineplus.data.backup.decodeBackup
import fr.maubin.patrimoineplus.data.backup.newerFormatText

import fr.maubin.patrimoineplus.data.backup.kBackupVersion

/*
	Budget et objectifs dans le fichier d'échange (format 8) : aller-retour
	exact par le classeur et par le texte gardé sur le téléphone, onglets
	lisibles, budget commun et objectifs par vue, valeur inconnue refusée,
	empreinte qui change avec le budget, fichier d'avant le format 8 sans
	budget.
*/
class PlansTest
{
	private val kBudget = Budget(listOf(
		BudgetLine("b1", BudgetSection.INCOME, "Salaires", 420_000, categoryId = null),
		BudgetLine("b2", BudgetSection.EXPENSE, "Courses", 60_000, theme = ExpenseTheme.DAILY),
		BudgetLine("b3", BudgetSection.INVESTMENT, "Livret A", 20_000, accountId = "acc-livret", profileId = kDefaultProfileId)
	))																			// budget de la famille

	private val kGoals = Goals(
		goals  = listOf(
			Goal("g1", GoalKind.WEALTH, "Cap des 100 k€", 10_000_000, deadline = "2028-12-31"),
			Goal("g2", GoalKind.RATE, "Épargner 15 %", 150),
			Goal("g3", GoalKind.TYPE, "Bourse", 2_000_000, accountType = AccountType.BROKERAGE),
			Goal("g4", GoalKind.TASK, "Ouvrir un PER", done = true, note = "avant décembre")
		),
		months = listOf(
			MonthPlan("2026-09", "2026-09-01", listOf(MonthGoal("2026-09:DAILY", MonthGoalKind.SPENDING, 60_000, ExpenseTheme.DAILY), MonthGoal("2026-09:EPARGNE", MonthGoalKind.SAVING, 20_000))),
			MonthPlan("2026-10", "2026-10-02", emptyList())
		)
	)																			// objectifs d'une vue

	/*
		Démonstration avec un budget, les objectifs de « Moi » et une tâche
		de la famille.
	*/
	private fun file(): BackupFile =
		demoBackupFile()
			.withBudget(kBudget)
			.withGoals(kDefaultProfileId, kGoals)
			.withGoals(kFamilyView, Goals(listOf(Goal("f1", GoalKind.TASK, "Faire le point en famille"))))

	/*
		Classeur écrit en mémoire.
		inFile : contenu
	*/
	private fun workbook(inFile: BackupFile): ByteArray = ByteArrayOutputStream().also { writeBackupXlsx(inFile, it) }.toByteArray()

	/*
		Aller-retour par le classeur : budget et objectifs relus à l'identique,
		dans les données de l'appli.
	*/
	@Test
	fun workbookRoundTrip()
	{
		val vRead = readBackupXlsx(ByteArrayInputStream(workbook(file())))		// relecture
		assertTrue("lecture refusée : $vRead", vRead is BackupRead.Valid)
		val vData = (vRead as BackupRead.Valid).file.toDataSet()					// données relues
		assertEquals(kBudget, vData.budget)
		assertEquals(kGoals, vData.goals[kDefaultProfileId])
		assertEquals("Faire le point en famille", vData.goals.getValue(kFamilyView).goals.single().title)
		assertEquals(kBackupVersion, vRead.file.version)
	}

	/*
		Onglets lisibles : rubriques, thèmes, membres, vues et taux en clair.
	*/
	@Test
	fun readableSheets()
	{
		val vSheets = readXlsx(ByteArrayInputStream(workbook(file()))).associateBy { it.name }	// onglets
		val vBudget = vSheets.getValue("Budget").rows										// lignes du budget
		assertEquals(listOf("Entrées", "Dépenses", "Investissements"), vBudget.map { it["Rubrique"] })
		assertEquals("Vie quotidienne", vBudget[1]["Thème"])
		assertEquals("Moi", vBudget[2]["Membre"])
		val vGoals = vSheets.getValue("Objectifs").rows										// objectifs
		assertEquals("Moi", vGoals.first()["Vue"])
		assertEquals("Famille", vGoals.last()["Vue"])
		assertEquals(15.0, vGoals.single { it["Intitulé"] == "Épargner 15 %" }["Cible"]!!.toDouble(), 0.0)
		val vMonths = vSheets.getValue("Objectifs du mois").rows							// objectifs du mois
		assertEquals(listOf("Dépenses", "Épargne", ""), vMonths.map { it["Nature"].orEmpty() })
	}

	/*
		Texte gardé par le téléphone : aller-retour exact ; illisible : rien.
	*/
	@Test
	fun phoneTextRoundTrip()
	{
		val vFile = file()																	// contenu
		assertEquals(vFile.plans, decodePlans(encodePlans(vFile.plans)))
		assertTrue(decodePlans("pas du json").budget.isEmpty())
	}

	/*
		Valeur inconnue refusée ; empreinte différente avec un autre budget ;
		fichier sans budget (format 7) : budget vide, aucun objectif.
	*/
	@Test
	fun validationAndFingerprint()
	{
		val vBad = file().let { it.copy(budget = it.budget.map { inLine -> inLine.copy(section = "AUTRE") }) }	// rubrique inconnue
		assertTrue(validateBackup(vBad) is BackupRead.Invalid)
		assertNotEquals(syncFingerprint(file()), syncFingerprint(file().withBudget(Budget())))
		val vOld = demoBackupFile().copy(version = 7).toDataSet()								// fichier d'avant le format 8
		assertEquals(Budget(), vOld.budget)
		assertTrue(vOld.goals.isEmpty())
	}
	/*
		Fichier écrit par une version plus récente (autre appareil mis à
		jour) : refusé avant toute lecture, avec son format, que ce soit un
		classeur aux onglets inconnus ou un export JSON aux champs inconnus ;
		l'explication invite à mettre à jour cette appli.
	*/
	@Test
	fun newerFormatIsRecognized()
	{
		val vNewer  = kBackupVersion + 1														// format plus récent
		val vSheets = backupSheets(file()).map { inSheet ->
			if (inSheet.name != "Fichier") inSheet
			else inSheet.copy(rows = inSheet.rows.map { if ((it.first() as? Cell.Text)?.text == "Version") listOf(it.first(), Cell.Number(java.math.BigDecimal(vNewer))) else it })
		} + Sheet("Nouveauté", listOf(Column("Colonne inconnue", 10.0)), emptyList())		// classeur d'un format plus récent
		val vBytes  = ByteArrayOutputStream().also { writeXlsx(vSheets, it) }.toByteArray()	// classeur écrit
		val vRead   = readBackupXlsx(ByteArrayInputStream(vBytes)) as BackupRead.Invalid	// lecture refusée
		assertEquals(vNewer, vRead.newerFormat)
		val vJson   = encodeBackup(file().copy(version = vNewer)).replaceFirst("{", "{\"nouveau\": 1,")	// export JSON d'un format plus récent
		assertEquals(vNewer, (decodeBackup(vJson) as BackupRead.Invalid).newerFormat)
		assertEquals(null, (readBackupXlsx(ByteArrayInputStream(byteArrayOf(1, 2, 3))) as BackupRead.Invalid).newerFormat)	// autre problème : pas de format
		assertTrue(newerFormatText(vNewer, "le PC").contains("différence de version"))
	}
}
