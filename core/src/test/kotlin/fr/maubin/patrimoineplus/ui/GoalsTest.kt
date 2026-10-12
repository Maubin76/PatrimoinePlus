package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kPea
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.opening
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.transfer
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.goals.Goal
import fr.maubin.patrimoineplus.ui.goals.GoalKind
import fr.maubin.patrimoineplus.ui.goals.GoalStatus
import fr.maubin.patrimoineplus.ui.goals.Goals
import fr.maubin.patrimoineplus.ui.goals.decodeGoals
import fr.maubin.patrimoineplus.ui.goals.encodeGoals
import fr.maubin.patrimoineplus.ui.goals.evaluateGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

/*
	Objectifs (appli PC) : avancement, rythme et date estimée, effort pour
	l'échéance, objectifs mensuels, à cocher, ordre, fichier.
*/
class GoalsTest
{
	private val kToday = d("2026-10-12")												// date du jour

	/*
		Données : soldes initiaux le 1er janvier 2026 (1 000 € courant, 5 000 €
		livret), puis chaque mois 3 000 € de salaire, 1 500 € de courses et
		500 € virés sur le livret ; un PEA ouvert le 1er octobre.
	*/
	private val kData = DataSet(
		listOf(kChecking, kLivret, kPea), listOf(kGroceries, kSalary), emptyList(),
		listOf(opening("2026-01-01", kChecking, eur(1_000.0)), opening("2026-01-01", kLivret, eur(5_000.0)), opening("2026-10-01", kPea, eur(800.0))) +
			(1..10).flatMap { inMonth ->
				val vM = "2026-%02d".format(inMonth)										// mois
				listOf(standard("$vM-02", kChecking, eur(3_000.0), kSalary), standard("$vM-05", kChecking, eur(-1_500.0), kGroceries), transfer("$vM-10", kChecking, kLivret, eur(500.0)))
			},
		emptyList(), emptyList()
	)																					// données

	/*
		Évalue un seul objectif.
		inGoal : objectif
	*/
	private fun one(inGoal: Goal) = evaluateGoals(Goals(listOf(inGoal)), kData, kToday).single()

	/*
		Patrimoine : rythme depuis la première opération, sans le solde initial
		du PEA ouvert en octobre (15 000 € gagnés en 284 jours), date estimée,
		effort nécessaire pour l'échéance, en bonne voie.
	*/
	@Test
	fun wealthPaceAndDeadline()
	{
		val vView = one(Goal("w", GoalKind.WEALTH, "30 000 €", eur(30_000.0), deadline = "2027-06-30"))	// patrimoine
		assertEquals(eur(21_800.0), vView.current)
		assertEquals(72, vView.progress)
		assertEquals(eur(8_200.0), vView.remaining)
		assertEquals(Math.round(eur(15_000.0) * 365.25 / 12 / 284), vView.pace)
		assertEquals(102_500L, vView.needed)												// 8 200 € en 8 mois
		assertEquals(YearMonth.of(2027, 4), vView.eta)										// 6 mois au rythme actuel
		assertEquals(GoalStatus.ON_TRACK, vView.status)
	}

	/*
		Compte : valeur du livret, rythme insuffisant pour l'échéance ; compte
		ouvert il y a moins de 28 jours : rythme inconnu ; type de placement
		atteint.
	*/
	@Test
	fun accountAndType()
	{
		val vLivret = one(Goal("a", GoalKind.ACCOUNT, "Livret plein", eur(22_950.0), accountId = kLivret.id, deadline = "2027-01-31"))	// livret
		assertEquals(eur(10_000.0), vLivret.current)
		assertEquals(43, vLivret.progress)
		assertEquals(Math.round(eur(5_000.0) * 365.25 / 12 / 284), vLivret.pace)
		assertEquals(431_667L, vLivret.needed)												// 12 950 € en 3 mois
		assertEquals(YearMonth.of(2028, 11), vLivret.eta)
		assertEquals(GoalStatus.BEHIND, vLivret.status)
		val vPea = one(Goal("p", GoalKind.ACCOUNT, "PEA", eur(5_000.0), accountId = kPea.id))	// PEA tout neuf
		assertNull(vPea.pace)
		assertNull(vPea.eta)
		assertEquals(GoalStatus.OPEN, vPea.status)
		val vType = one(Goal("t", GoalKind.TYPE, "Épargne", eur(8_000.0), accountType = AccountType.SAVINGS))	// type
		assertEquals(eur(10_000.0), vType.current)
		assertEquals(100, vType.progress)
		assertEquals(0L, vType.remaining)
		assertEquals(GoalStatus.REACHED, vType.status)
	}

	/*
		Objectifs mensuels : épargne mensuelle (mois complets depuis la
		première opération, mois en cours exclu) et taux d'épargne.
	*/
	@Test
	fun monthlyGoals()
	{
		val vSaved = one(Goal("m", GoalKind.MONTHLY, "500 €/mois", eur(500.0)))			// épargne mensuelle
		assertEquals(eur(500.0), vSaved.current)
		assertEquals(eur(500.0), vSaved.last)
		assertEquals(9, vSaved.months)
		assertEquals(9, vSaved.hits)
		assertEquals(GoalStatus.REACHED, vSaved.status)
		val vRate = one(Goal("r", GoalKind.RATE, "60 %", 600))							// taux d'épargne
		assertEquals(500L, vRate.current)
		assertEquals(83, vRate.progress)
		assertEquals(0, vRate.hits)
		assertEquals(GoalStatus.BEHIND, vRate.status)
	}

	/*
		Montant à la main et objectifs à cocher : échéance dépassée, à faire,
		fait ; ordre : en cours par échéance, puis terminés dans l'ordre de
		saisie.
	*/
	@Test
	fun manualTasksAndOrder()
	{
		val vGoals = Goals(listOf(
			Goal("fait", GoalKind.TASK, "Ouvrir un PEA", done = true),
			Goal("type", GoalKind.TYPE, "Épargne", eur(8_000.0), accountType = AccountType.SAVINGS),
			Goal("patrimoine", GoalKind.WEALTH, "30 000 €", eur(30_000.0), deadline = "2027-06-30"),
			Goal("apport", GoalKind.MANUAL, "Apport", eur(20_000.0), current = eur(5_000.0), deadline = "2026-09-30"),
			Goal("taux", GoalKind.RATE, "60 %", 600),
			Goal("livret", GoalKind.ACCOUNT, "Livret plein", eur(22_950.0), accountId = kLivret.id, deadline = "2027-01-31"),
			Goal("afaire", GoalKind.TASK, "Assurance vie", deadline = "2027-03-01"),
			Goal("mensuel", GoalKind.MONTHLY, "500 €/mois", eur(500.0))
		))																					// objectifs
		val vViews = evaluateGoals(vGoals, kData, kToday)									// évalués
		assertEquals(listOf("apport", "livret", "afaire", "patrimoine", "taux", "fait", "type", "mensuel"), vViews.map { it.goal.id })
		val vById = vViews.associateBy { it.goal.id }										// par identifiant
		assertEquals(GoalStatus.OVERDUE, vById.getValue("apport").status)
		assertEquals(25, vById.getValue("apport").progress)
		assertNull(vById.getValue("apport").needed)
		assertEquals(GoalStatus.TODO, vById.getValue("afaire").status)
		assertEquals(GoalStatus.DONE, vById.getValue("fait").status)
		assertEquals(GoalStatus.OVERDUE, evaluateGoals(vGoals, kData, d("2027-03-02")).single { it.goal.id == "afaire" }.status)
	}

	/*
		Fichier : aller-retour exact ; fichier illisible = aucun objectif ;
		échéance illisible ignorée.
	*/
	@Test
	fun fileRoundTrip()
	{
		val vGoals = Goals(listOf(Goal("1", GoalKind.TYPE, "Bourse", eur(50_000.0), accountType = AccountType.BROKERAGE, deadline = "2030-12-31", note = "PEA + CTO")))	// objectifs
		assertEquals(vGoals, decodeGoals(encodeGoals(vGoals)))
		assertEquals(Goals(), decodeGoals("pas du JSON"))
		assertNull(Goal("2", GoalKind.TASK, "x", deadline = "bientôt").due)
	}
}
