package fr.maubin.patrimoineplus.data

import fr.maubin.patrimoineplus.data.demo.buildDemoData
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.statementGap
import fr.maubin.patrimoineplus.domain.calc.valuePortfolio
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.rules.TransactionValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/*
	Tests des données de démonstration : elles respectent toutes les règles de
	saisie et restent vraisemblables, quelle que soit la date du jour.
*/
class DemoDataTest
{
	private val kDays = listOf("2026-10-07", "2026-02-28", "2026-03-31", "2027-01-01").map(LocalDate::parse)	// dates d'essai

	/*
		Chaque opération, ajoutée dans l'ordre chronologique, passe le validateur.
	*/
	@Test
	fun everyDemoOperationIsValid()
	{
		kDays.forEach { inToday ->
			val vDemo      = buildDemoData(inToday)								// démonstration
			val vValidator = TransactionValidator(
				vDemo.accounts.associateBy { it.id },
				vDemo.categories.associateBy { it.id },
				vDemo.assets.associateBy { it.id }
			)																	// règles de saisie
			val vDone      = mutableListOf<fr.maubin.patrimoineplus.domain.model.Transaction>()	// opérations déjà validées
			vDemo.transactions.sortedBy { it.date }.forEach { inTx ->
				assertEquals("$inToday ${inTx.label}", emptyList<Any>(), vValidator.validate(inTx, Ledger(vDone)))
				vDone += inTx
			}
		}
	}

	/*
		Aucun compte ne finit à découvert ; une seule opération est à venir ; le
		relevé du livret montre 38,20 € d'intérêts non saisis.
	*/
	@Test
	fun demoIsPlausible()
	{
		kDays.forEach { inToday ->
			val vDemo   = buildDemoData(inToday)								// démonstration
			val vLedger = Ledger(vDemo.transactions)							// grand livre
			vDemo.accounts.forEach { inAccount ->
				val vValue = if (inAccount.type == AccountType.BROKERAGE) valuePortfolio(vLedger, inAccount.id, inToday, vDemo.prices).cashCents
					else vLedger.balanceAt(inAccount.id, inToday)			// solde ou espèces
				assertTrue("$inToday ${inAccount.name} : $vValue", vValue >= 0)
			}
			assertEquals(1, vDemo.transactions.count { it.isUpcoming(inToday) })
			assertEquals(3_820L, statementGap(vDemo.statements.single(), vLedger))
		}
	}
}
