package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.data.backupFileOf
import fr.maubin.patrimoineplus.data.edit.DataEditor
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.data.toDataSet
import fr.maubin.patrimoineplus.domain.d
import fr.maubin.patrimoineplus.domain.eur
import fr.maubin.patrimoineplus.domain.kChecking
import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLeisure
import fr.maubin.patrimoineplus.domain.kLivret
import fr.maubin.patrimoineplus.domain.kSalary
import fr.maubin.patrimoineplus.domain.standard
import fr.maubin.patrimoineplus.domain.transfer
import fr.maubin.patrimoineplus.ui.analysis.buildExpenseAnalysis
import fr.maubin.patrimoineplus.ui.search.SearchQuery
import fr.maubin.patrimoineplus.ui.search.searchTransactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/*
	Tableau d'analyse des dépenses (appli PC), recherche dans les opérations
	et changement groupé de catégorie.
*/
class AnalysisAndSearchTest
{
	private val kToday = d("2026-10-12")												// date du jour

	// Trois mois de dépenses et de salaire, un remboursement, un virement et une dépense à venir.
	private val kData = DataSet(
		listOf(kChecking, kLivret), listOf(kGroceries, kLeisure, kSalary), emptyList(),
		listOf(
			standard("2026-08-03", kChecking, eur(-100.0), kGroceries).copy(label = "Courses Carrefour"),
			standard("2026-08-28", kChecking, eur(2_000.0), kSalary).copy(label = "Salaire"),
			standard("2026-09-04", kChecking, eur(-160.0), kGroceries).copy(label = "Courses Carrefour", notes = "anniversaire"),
			standard("2026-09-10", kChecking, eur(20.0), kGroceries).copy(label = "Remboursement Carrefour"),
			standard("2026-09-20", kChecking, eur(-45.0), kLeisure).copy(label = "Cinéma"),
			standard("2026-09-28", kChecking, eur(2_000.0), kSalary).copy(label = "Salaire"),
			transfer("2026-09-29", kChecking, kLivret, eur(300.0)),
			standard("2026-10-05", kChecking, eur(-54.90), kGroceries).copy(label = "Courses Carrefour"),
			standard("2026-10-20", kChecking, eur(-999.0), kLeisure).copy(label = "Voyage à venir")
		),
		emptyList(), emptyList()
	)																					// données

	/*
		Tableau : douze mois jusqu'au mois en cours ; dépenses nettes (le
		remboursement réduit sa catégorie), virements et opérations à venir
		exclus ; moyennes sur les mois complets suivis seulement (depuis la
		première opération) ; taux d'épargne ; hausses du dernier mois.
	*/
	@Test
	fun expenseTable()
	{
		val vTable  = buildExpenseAnalysis(kData, kToday)									// tableau
		assertEquals(12, vTable.months.size)
		assertEquals(YearMonth.of(2026, 10), vTable.months.last())
		val vSep    = vTable.months.indexOf(YearMonth.of(2026, 9))						// colonne de septembre
		val vFood   = vTable.expenses.single { it.category.id == kGroceries.id }		// alimentation
		assertEquals(eur(140.0), vFood.values[vSep])
		assertEquals(eur(54.90), vFood.values.last())
		assertEquals(eur(120.0), vFood.average)											// (100 + 140) / 2 : août et septembre seulement
		assertEquals(eur(40.0), vFood.lastGap)											// septembre − août
		assertEquals(eur(185.0), vTable.totalExpense[vSep])
		assertEquals(eur(2_000.0), vTable.totalIncome[vSep])
		assertEquals(907L, vTable.savingsRate[vSep])										// (2000 − 185) / 2000, en pour mille
		assertEquals(eur(54.90), vTable.totalExpense.last())								// voyage à venir exclu
		assertEquals(kGroceries.id, vTable.expenses.first().category.id)
		assertEquals(listOf(kLeisure.id, kGroceries.id), vTable.rises.map { it.category.id })		// +45 € puis +40 €
		assertNull(vTable.savingsRate.first())
	}

	/*
		Recherche : mots sans accents ni majuscules dans le libellé, les notes,
		la catégorie ou les comptes ; montant exact ; filtres de compte, de
		catégorie, de période et de montant ; montant illisible signalé.
	*/
	@Test
	fun searchFilters()
	{
		assertEquals(4, searchTransactions(kData, SearchQuery(text = "CARREFOUR")).hits.size)
		assertEquals(1, searchTransactions(kData, SearchQuery(text = "cinema")).hits.size)
		assertEquals(1, searchTransactions(kData, SearchQuery(text = "anniversaire")).hits.size)
		assertEquals(1, searchTransactions(kData, SearchQuery(text = "54,90")).hits.size)
		assertEquals(1, searchTransactions(kData, SearchQuery(text = "livret")).hits.size)
		val vSept = searchTransactions(kData, SearchQuery(categoryId = kGroceries.id, from = d("2026-09-01"), to = d("2026-09-30")))	// alimentation de septembre
		assertEquals(2, vSept.hits.size)
		assertEquals(eur(-140.0), vSept.total)
		assertEquals(listOf(d("2026-09-10"), d("2026-09-04")), vSept.hits.map { it.transaction.date })
		assertEquals(3, searchTransactions(kData, SearchQuery(minAmount = "100", maxAmount = "999")).hits.count { it.amount < 0 })
		assertEquals(eur(-300.0), searchTransactions(kData, SearchQuery(text = "virement", accountId = kChecking.id)).hits.single().amount)
		assertNotNull(searchTransactions(kData, SearchQuery(minAmount = "abc")).badQuery)
		assertTrue(SearchQuery().isEmpty)
	}

	/*
		Changement groupé de catégorie : des dépenses vers une autre catégorie
		de dépense ; refusé, sans rien changer, si la sélection contient un
		revenu ou un virement.
	*/
	@Test
	fun bulkRecategorize()
	{
		val vEditor = DataEditor(Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris")))	// moteur
		val vFile   = backupFileOf(kData)												// contenu
		val vFood   = kData.transactions.filter { it.categoryId == kGroceries.id }.map { it.id }	// alimentation
		val vDone   = vEditor.recategorize(vFile, vFood, kLeisure.id)					// vers loisirs
		assertTrue(vDone is EditResult.Done)
		assertTrue((vDone as EditResult.Done).file.toDataSet().transactions.filter { it.id in vFood }.all { it.categoryId == kLeisure.id })
		val vMixed  = vFood + kData.transactions.first { it.categoryId == kSalary.id }.id	// avec un salaire
		assertTrue(vEditor.recategorize(vFile, vMixed, kLeisure.id) is EditResult.Refused)
		val vTransfer = kData.transactions.first { it.categoryId == null }.id			// virement
		assertTrue(vEditor.recategorize(vFile, listOf(vTransfer), kLeisure.id) is EditResult.Refused)
	}
}
