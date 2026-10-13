package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.data.demo.buildDemoData
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.dashboard.TilePreview
import fr.maubin.patrimoineplus.ui.dashboard.buildDashboard
import fr.maubin.patrimoineplus.ui.dashboard.kRecentOperations
import fr.maubin.patrimoineplus.ui.dashboard.kTileTopItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/*
	Tests du tableau de bord de l'appli PC, sur la démonstration : tuiles des
	comptes cohérentes avec l'accueil et la vue des comptes, indicateurs du
	mois, opérations récentes.
*/
class DashboardStateTest
{
	private val kToday = LocalDate.of(2026, 10, 9)								// date de calcul
	private val kDemo  = buildDemoData(kToday)									// démonstration
	private val kState = buildDashboard(kDemo, kToday)							// tableau de bord

	/*
		Une tuile par compte actif, dans l'ordre d'affichage ; parts qui font
		100 % ; courbes de douze mois au plus ; aperçu du bon type.
	*/
	@Test
	fun tilesFollowAccounts()
	{
		assertEquals(kDemo.accounts.filterNot { it.archived }.sortedBy { it.sortOrder }.map { it.id }, kState.tiles.map { it.account.id })
		assertEquals(100, (kState.tiles + kState.archived).mapNotNull { it.weight?.percent }.sum())
		kState.tiles.forEach { inTile ->
			assertTrue(inTile.series.size in 1..12)
			assertEquals(inTile.series.last().valueCents, inTile.valueCents)
			when (inTile.account.type)
			{
				AccountType.CHECKING  -> assertTrue(inTile.preview is TilePreview.Checking)
				AccountType.SAVINGS   -> assertTrue(inTile.preview is TilePreview.Savings)
				AccountType.BROKERAGE -> assertTrue(inTile.preview is TilePreview.Brokerage)
				AccountType.REAL_ESTATE, AccountType.COLLECTIBLE -> assertTrue(inTile.preview is TilePreview.Property)
				AccountType.LOAN -> assertTrue(inTile.preview is TilePreview.Loan)
			}
		}
		assertEquals(kState.home.totalCents, kState.home.groups.sumOf { it.subtotalCents })
	}

	/*
		Aperçus : dépenses du mois du compte Courant ; positions d'un compte
		Bourse dont les parts font 100 % (autres comprises) ; dernier relevé
		d'un compte Épargne.
	*/
	@Test
	fun previewsAreConsistent()
	{
		kState.tiles.forEach { inTile ->
			when (val vPreview = inTile.preview)
			{
				is TilePreview.Checking  ->
				{
					assertTrue(vPreview.categories.size <= kTileTopItems)
					assertTrue(vPreview.categories.zipWithNext().all { it.first.netCents >= it.second.netCents })
				}
				is TilePreview.Brokerage -> if (vPreview.positions.isNotEmpty())
				{
					assertEquals(100, vPreview.positions.sumOf { it.second } + vPreview.others)
				}
				is TilePreview.Property  ->
				{
					assertTrue(vPreview.items.size <= kTileTopItems)
					if (inTile.note == null) assertEquals(inTile.valueCents, vPreview.items.sumOf { it.second } + vPreview.others)	// biens détenus = valeur du compte
					else assertTrue(vPreview.items.sumOf { it.second } + vPreview.others > inTile.valueCents)	// bien financé : valeur nette, sous le prix d'achat
				}
				is TilePreview.Loan      ->
				{
					assertEquals(inTile.valueCents, vPreview.remaining)									// crédit affiché en positif : capital restant dû
					assertTrue(vPreview.next!!.date.isAfter(kToday))
				}
				is TilePreview.Savings   ->
				{
					val vLast = kDemo.statements.filter { it.accountId == inTile.account.id }.maxByOrNull { it.date }	// dernier relevé
					assertEquals(vLast?.id, vPreview.statement?.statement?.id)
				}
			}
		}
	}

	/*
		Indicateurs du mois : entrées et dépenses des comptes Courant, égales à
		la somme de leurs synthèses ; dépenses par catégorie qui redonnent les
		dépenses nettes du mois.
	*/
	@Test
	fun monthFigures()
	{
		val vLedger   = Ledger(kDemo.transactions)									// grand livre
		val vById     = kDemo.accounts.associateBy { it.id }						// comptes par identifiant
		val vExpenses = kDemo.categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()	// catégories de dépense
		val vMonth    = YearMonth.from(kToday)										// mois en cours
		val vChecking = kDemo.accounts.filter { it.type == AccountType.CHECKING && !it.archived }	// comptes Courant
		assertEquals(vChecking.sumOf { vLedger.monthSummary(it, vMonth, vById, vExpenses).expenses }, kState.expenses)
		assertEquals(vChecking.sumOf { vLedger.monthSummary(it, vMonth, vById, vExpenses).income }, kState.income)
		assertEquals(-kState.expenses, kState.categories.sumOf { it.netCents })
		assertTrue(kState.categories.zipWithNext().all { it.first.netCents >= it.second.netCents })
	}

	/*
		Opérations récentes : les plus récentes d'abord, sans opération à venir
		ni solde initial.
	*/
	@Test
	fun recentOperations()
	{
		assertTrue(kState.recent.size <= kRecentOperations)
		assertTrue(kState.recent.zipWithNext().all { !it.first.transaction.date.isBefore(it.second.transaction.date) })
		assertTrue(kState.recent.none { it.transaction.date.isAfter(kToday) })
		assertEquals(kDemo.transactions.count { it.date.isAfter(kToday) }, kState.upcoming)
	}
}
