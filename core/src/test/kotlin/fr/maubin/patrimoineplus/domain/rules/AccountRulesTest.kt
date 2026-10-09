package fr.maubin.patrimoineplus.domain.rules

import fr.maubin.patrimoineplus.domain.kGroceries
import fr.maubin.patrimoineplus.domain.kLeisure
import fr.maubin.patrimoineplus.domain.kSalary
import org.junit.Assert.assertEquals
import org.junit.Test

/*
	Tests des règles de saisie des comptes et des catégories.
*/
class AccountRulesTest
{
	/*
		Un compte exige un nom et une part détenue entre 1 et 100 %.
	*/
	@Test
	fun accountFields()
	{
		assertEquals(emptyList<SetupError>(), validateAccount("Livret A", 100))
		assertEquals(emptyList<SetupError>(), validateAccount("Joint", 1))
		assertEquals(listOf(SetupError.BlankName), validateAccount("  ", 50))
		assertEquals(listOf(SetupError.InvalidShare), validateAccount("Joint", 0))
		assertEquals(listOf(SetupError.BlankName, SetupError.InvalidShare), validateAccount("", 101))
	}

	/*
		Une catégorie ne se réaffecte qu'à une autre catégorie de même nature.
	*/
	@Test
	fun reassignment()
	{
		assertEquals(emptyList<SetupError>(), validateReassignment(kGroceries, kLeisure))
		assertEquals(listOf(SetupError.KindMismatch), validateReassignment(kGroceries, kSalary))
		assertEquals(listOf(SetupError.SameCategory), validateReassignment(kGroceries, kGroceries))
	}
}
