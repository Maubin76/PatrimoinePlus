package fr.maubin.patrimoineplus.data.demo

import androidx.room.withTransaction
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.db.toEntity
import fr.maubin.patrimoineplus.data.db.toHeaderEntity
import fr.maubin.patrimoineplus.data.db.toPostingEntities
import fr.maubin.patrimoineplus.data.db.toTradeEntities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/*
	Chargement et effacement des données de démonstration. Réservé à la
	version debug (l'interface ne l'expose pas en release).
	inDb    : base locale
	inClock : horloge
*/
@Singleton
class DemoRepository @Inject constructor(
	private val inDb    : PatrimoineDatabase,
	private val inClock : Clock
)
{
	/*
		Efface toutes les données puis charge six mois de démonstration.
	*/
	suspend fun loadDemo()
	{
		val vBundle = buildDemoData(LocalDate.now(inClock))						// données générées
		val vNow    = inClock.millis()											// horodatage
		clearAll()
		inDb.withTransaction {
			vBundle.accounts.forEach { inDb.accountDao().insert(it.toEntity(vNow, vNow)) }
			inDb.categoryDao().insertAll(vBundle.categories.map { it.toEntity(vNow, vNow) })
			inDb.assetDao().insertAll(vBundle.assets.map { it.toEntity(vNow, vNow) })
			vBundle.transactions.forEach { inTx ->
				inDb.transactionDao().save(inTx.toHeaderEntity(vNow, vNow), inTx.toPostingEntities(), inTx.toTradeEntities())
			}
			vBundle.prices.forEach { inDb.assetDao().putPrice(it.toEntity()) }
			vBundle.statements.forEach { inDb.statementDao().upsert(it.toEntity()) }
			inDb.planningDao().upsertRecurrences(vBundle.recurrences.map { it.toEntity() })
			inDb.planningDao().upsertLoans(vBundle.loans.map { it.toEntity() })
		}
	}

	/*
		Efface toutes les données de la base (irréversible).
	*/
	suspend fun clearAll()
	{
		withContext(Dispatchers.IO) { inDb.clearAllTables() }
	}
}
