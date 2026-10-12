package fr.maubin.patrimoineplus.ui

import fr.maubin.patrimoineplus.data.demo.buildDemoData
import fr.maubin.patrimoineplus.domain.calc.shareOf
import fr.maubin.patrimoineplus.ui.accounts.buildAccountsState
import fr.maubin.patrimoineplus.ui.home.buildHomeState
import fr.maubin.patrimoineplus.ui.home.homeDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/*
	Tests des états d'écran partagés par l'appli Android et l'appli PC, sur la
	démonstration : accueil et liste des comptes cohérents entre eux.
*/
class ScreenStatesTest
{
	private val kToday = LocalDate.of(2026, 10, 9)								// date de calcul
	private val kDemo  = buildDemoData(kToday)									// démonstration

	/*
		Accueil : total = somme des valeurs comptées des comptes ; courbe sur
		douze mois au plus ; date de calcul en français.
	*/
	@Test
	fun homeTotalMatchesAccounts()
	{
		val vHome     = buildHomeState(kDemo.accounts, kDemo.transactions, kDemo.prices, kToday)	// accueil
		val vAccounts = buildAccountsState(kDemo.accounts, kDemo.transactions, kDemo.prices, kToday)	// comptes
		val vCounted  = (vAccounts.active + vAccounts.archived).sumOf { shareOf(it.valueCents, it.account.sharePercent) }	// patrimoine recompté
		assertEquals(vCounted, vHome.totalCents)
		assertTrue(vHome.series.size in 2..12)
		assertEquals("9 octobre 2026", homeDate(kToday))
		assertEquals(vHome.asOf, homeDate(kToday))
	}

	/*
		Comptes : actifs et archivés séparés, parts de patrimoine qui font 100,
		opérations comptées hors solde initial.
	*/
	@Test
	fun accountsStateWeightsAndCounts()
	{
		val vState = buildAccountsState(kDemo.accounts, kDemo.transactions, kDemo.prices, kToday)	// comptes
		assertTrue(vState.loaded)
		assertEquals(kDemo.accounts.count { !it.archived }, vState.active.size)
		assertEquals(100, (vState.active + vState.archived).mapNotNull { it.weight?.percent }.sum())
		assertTrue(vState.active.all { it.operations >= 0 })
	}
}
