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
import fr.maubin.patrimoineplus.ui.entry.BatchRow
import fr.maubin.patrimoineplus.ui.entry.EntryData
import fr.maubin.patrimoineplus.ui.entry.EntryMode
import fr.maubin.patrimoineplus.ui.entry.buildEntry
import fr.maubin.patrimoineplus.ui.entry.checkBatchRow
import fr.maubin.patrimoineplus.ui.entry.proposeCategory
import fr.maubin.patrimoineplus.ui.projection.ProjectionInput
import fr.maubin.patrimoineplus.ui.projection.defaultProjectionInput
import fr.maubin.patrimoineplus.ui.projection.projectWealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/*
	Saisie en grille (appli PC) et projection du patrimoine.
*/
class BatchAndProjectionTest
{
	private val kHistory = listOf(
		standard("2026-09-02", kChecking, eur(-61.30), kGroceries).copy(label = "Courses Carrefour"),
		standard("2026-09-28", kChecking, eur(2_000.0), kSalary).copy(label = "Salaire")
	)																					// saisies passées
	private val kEntry = EntryData(listOf(kChecking, kLivret), listOf(kGroceries, kSalary), emptyList(), emptyList(), kHistory)	// données de saisie

	/*
		Ligne de grille : catégorie proposée d'après le libellé (exact, sans
		accents, ou début de libellé) ; montant sans signe = dépense, « + » =
		revenu ou remboursement ; erreurs lisibles (date, montant, catégorie).
	*/
	@Test
	fun batchRows()
	{
		assertEquals(kGroceries.id, proposeCategory("courses carrefour", kHistory))
		assertEquals(kGroceries.id, proposeCategory("Cour", kHistory))
		assertNull(proposeCategory("Inconnu", kHistory))
		val vSpend  = checkBatchRow(BatchRow(1, "05/10/2026", kChecking.id, "Courses Carrefour", null, "23,40"), kEntry)	// dépense
		assertNull(vSpend.error)
		assertEquals(eur(-23.40), buildEntry(vSpend.form!!, kEntry).postings.single().amountCents)
		val vPay    = checkBatchRow(BatchRow(2, "28/10/2026", kChecking.id, "Salaire", null, "+2 000"), kEntry)	// revenu
		assertEquals(EntryMode.INCOME, vPay.form!!.mode)
		assertEquals(eur(2_000.0), buildEntry(vPay.form!!, kEntry).postings.single().amountCents)
		val vRefund = checkBatchRow(BatchRow(3, "6/10/2026", kChecking.id, "Courses Carrefour", null, "+5"), kEntry)	// remboursement
		assertTrue(vRefund.form!!.refund)
		assertEquals(eur(5.0), buildEntry(vRefund.form!!, kEntry).postings.single().amountCents)
		assertNotNull(checkBatchRow(BatchRow(4, "32/10/2026", kChecking.id, "Courses", null, "5"), kEntry).error)
		assertNotNull(checkBatchRow(BatchRow(5, "05/10/2026", kChecking.id, "Inconnu", null, "5"), kEntry).error)
		assertNotNull(checkBatchRow(BatchRow(6, "05/10/2026", kChecking.id, "Courses Carrefour", null, "abc"), kEntry).error)
		assertTrue(BatchRow(7, "05/10/2026", kChecking.id).isBlank)
	}

	/*
		Projection : départ = patrimoine actuel ; sans rendement, chaque année
		ajoute douze versements ; scénarios ordonnés (prudent ≤ central ≤
		favorable) ; intérêts composés justes sur un cas simple ; hypothèses par
		défaut tirées des données (épargne moyenne, part en bourse).
	*/
	@Test
	fun projection()
	{
		val vData   = DataSet(listOf(kChecking, kLivret, kPea), listOf(kGroceries, kSalary), emptyList(),
			listOf(opening("2026-01-01", kChecking, eur(1_000.0)), opening("2026-01-01", kLivret, eur(10_000.0)), opening("2026-01-01", kPea, eur(0.0))) + kHistory,
			emptyList(), emptyList())														// données
		val vToday  = d("2026-10-12")														// date du jour
		val vResult = projectWealth(vData, vToday, ProjectionInput(years = 2, monthlySavings = eur(100.0), savingsRate = 3.0, brokerageRate = 6.0, stockShare = 0))	// projection
		assertEquals(3, vResult.points.size)
		assertEquals(vResult.start, vResult.points[0].central)
		assertEquals(vResult.start + eur(1_200.0), vResult.points[1].contributed)
		vResult.points.forEach { assertTrue(it.prudent <= it.central && it.central <= it.optimistic) }
		val vNoSave = projectWealth(vData, vToday, ProjectionInput(1, 0, 3.0, 6.0, 0))	// sans versement
		assertTrue(abs(vNoSave.points[1].central - (vNoSave.start + eur(300.0))) <= 1)	// 10 000 € à 3 % sur un an : +300 €
		val vDefault = defaultProjectionInput(vData, vToday)								// hypothèses par défaut
		assertEquals(10, vDefault.years)
		assertEquals(0, vDefault.stockShare)
		assertTrue(vDefault.monthlySavings >= 0)
	}
}
