package fr.maubin.patrimoineplus.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/*
	Tests de migration de la base : une vraie base d'une ancienne version,
	créée à partir du schéma exporté, est ouverte par la version actuelle sans perte.
*/
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest
{
	/*
		Crée une vraie base d'une ancienne version à partir de son schéma
		exporté, puis y exécute des insertions.
		inVersion : version du schéma
		inName    : nom du fichier
		inInserts : requêtes d'insertion
	*/
	private fun oldDatabase(
		inVersion : Int,
		inName    : String,
		vararg inInserts : String
	)
	{
		val vContext = ApplicationProvider.getApplicationContext<Context>()		// contexte
		vContext.deleteDatabase(inName)
		val vSchema  = Json.parseToJsonElement(File("schemas/fr.maubin.patrimoineplus.data.db.PatrimoineDatabase/$inVersion.json").readText())
			.jsonObject.getValue("database").jsonObject									// schéma de la version
		SQLiteDatabase.openOrCreateDatabase(vContext.getDatabasePath(inName).apply { parentFile?.mkdirs() }, null).use { inDb ->
			vSchema.getValue("entities").jsonArray.forEach { inEntity ->
				val vTable = inEntity.jsonObject.getValue("tableName").jsonPrimitive.content	// table
				inDb.execSQL(inEntity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", vTable))
				inEntity.jsonObject["indices"]?.jsonArray?.forEach { inIndex ->
					inDb.execSQL(inIndex.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", vTable))
				}
			}
			vSchema.getValue("setupQueries").jsonArray.forEach { inDb.execSQL(it.jsonPrimitive.content) }
			inInserts.forEach { inDb.execSQL(it) }
			inDb.version = inVersion
		}
	}

	/*
		Version 1 → 2 : la colonne du symbole de cotation est ajoutée aux actifs ;
		les actifs existants sont conservés tels quels, sans symbole.
	*/
	@Test
	fun version1To2KeepsAssets() = runTest {
		val vContext = ApplicationProvider.getApplicationContext<Context>()		// contexte
		oldDatabase(1, "migration-test.db", "INSERT INTO asset (id, name, isin, archived, createdAt, updatedAt) VALUES ('a1', 'ETF Monde', 'LU1681043599', 0, 1, 2)")
		val vDb = Room.databaseBuilder(vContext, PatrimoineDatabase::class.java, "migration-test.db").allowMainThreadQueries().build()	// version actuelle
		val vAssets = vDb.assetDao().observeAll().first()						// actifs après migration
		assertEquals(listOf("ETF Monde"), vAssets.map { it.name })
		assertEquals("LU1681043599", vAssets.single().isin)
		assertNull(vAssets.single().ticker)
		vDb.assetDao().update(vAssets.single().copy(ticker = "CW8.PA"))
		assertEquals("CW8.PA", vDb.assetDao().observeAll().first().single().ticker)
		vDb.close()
	}

	/*
		Version 2 → 3 : la colonne de l'opération liée est ajoutée ; les
		opérations existantes sont conservées, sans lien.
	*/
	@Test
	fun version2To3KeepsTransactions() = runTest {
		val vContext = ApplicationProvider.getApplicationContext<Context>()		// contexte
		oldDatabase(
			2, "migration-test-3.db",
			"INSERT INTO account (id, name, type, institution, sharePercent, sortOrder, archived, createdAt, updatedAt) VALUES ('c1', 'Courant', 'CHECKING', NULL, 100, 0, 0, 1, 2)",
			"INSERT INTO txn (id, date, kind, label, categoryId, assetId, notes, createdAt, updatedAt) VALUES ('t1', '2026-10-01', 'OPENING', 'Solde initial', NULL, NULL, NULL, 1, 2)",
			"INSERT INTO posting (id, transactionId, accountId, amountCents) VALUES ('p1', 't1', 'c1', 150000)"
		)
		val vDb = Room.databaseBuilder(vContext, PatrimoineDatabase::class.java, "migration-test-3.db").allowMainThreadQueries().build()	// version actuelle
		val vTxs = vDb.transactionDao().getAll()									// opérations après migration
		assertEquals(listOf("Solde initial"), vTxs.map { it.transaction.label })
		assertNull(vTxs.single().transaction.linkedId)
		assertEquals(150_000L, vTxs.single().postings.single().amountCents)
		vDb.close()
	}

	/*
		Version 3 → 4 : les tables des récurrences et des crédits sont créées,
		vides ; les opérations existantes sont conservées.
	*/
	@Test
	fun version3To4AddsPlanningTables() = runTest {
		val vContext = ApplicationProvider.getApplicationContext<Context>()		// contexte
		oldDatabase(
			3, "migration-test-4.db",
			"INSERT INTO account (id, name, type, institution, sharePercent, sortOrder, archived, createdAt, updatedAt) VALUES ('c1', 'Courant', 'CHECKING', NULL, 100, 0, 0, 1, 2)",
			"INSERT INTO txn (id, date, kind, label, categoryId, assetId, notes, createdAt, updatedAt, linkedId) VALUES ('t1', '2026-10-01', 'OPENING', 'Solde initial', NULL, NULL, NULL, 1, 2, NULL)",
			"INSERT INTO posting (id, transactionId, accountId, amountCents) VALUES ('p1', 't1', 'c1', 150000)"
		)
		val vDb = Room.databaseBuilder(vContext, PatrimoineDatabase::class.java, "migration-test-4.db").allowMainThreadQueries().build()	// version actuelle
		assertEquals(listOf("Solde initial"), vDb.transactionDao().getAll().map { it.transaction.label })
		assertEquals(0, vDb.planningDao().recurrences().size)
		assertEquals(0, vDb.planningDao().loans().size)
		vDb.close()
	}

	/*
		Version 4 → 5 : la colonne du bien financé est ajoutée aux crédits,
		vide ; le crédit existant est conservé.
	*/
	@Test
	fun version4To5AddsFinancedProperty() = runTest {
		val vContext = ApplicationProvider.getApplicationContext<Context>()		// contexte
		oldDatabase(
			4, "migration-test-5.db",
			"INSERT INTO account (id, name, type, institution, sharePercent, sortOrder, archived, createdAt, updatedAt) VALUES ('c1', 'Courant', 'CHECKING', NULL, 100, 0, 0, 1, 2)",
			"INSERT INTO account (id, name, type, institution, sharePercent, sortOrder, archived, createdAt, updatedAt) VALUES ('l1', 'Crédit auto', 'LOAN', NULL, 100, 1, 0, 1, 2)",
			"INSERT INTO category (id, name, icon, kind, sortOrder, archived, createdAt, updatedAt) VALUES ('k1', 'Crédit', 'bank', 'EXPENSE', 0, 0, 1, 2)",
			"INSERT INTO loan (id, accountId, principal, rateBp, months, firstDue, insurance, payFrom, categoryId, last) VALUES ('p1', 'l1', 1000000, 300, 12, '2026-08-10', 500, 'c1', 'k1', '2026-09-10')"
		)
		val vDb = Room.databaseBuilder(vContext, PatrimoineDatabase::class.java, "migration-test-5.db").allowMainThreadQueries().build()	// version actuelle
		val vLoan = vDb.planningDao().loans().single()							// crédit relu
		assertEquals("2026-09-10", vLoan.last.toString())
		assertEquals(null, vLoan.propertyId)
		vDb.close()
	}
}
