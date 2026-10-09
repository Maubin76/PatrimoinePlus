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
	Test de migration de la base : une vraie base de la version 1, créée à
	partir du schéma exporté, est ouverte par la version actuelle sans perte.
*/
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest
{
	/*
		Version 1 → 2 : la colonne du symbole de cotation est ajoutée aux actifs ;
		les actifs existants sont conservés tels quels, sans symbole.
	*/
	@Test
	fun version1To2KeepsAssets() = runTest {
		val vContext = ApplicationProvider.getApplicationContext<Context>()		// contexte
		val vName    = "migration-test.db"										// fichier de test
		vContext.deleteDatabase(vName)
		val vSchema  = Json.parseToJsonElement(File("schemas/fr.maubin.patrimoineplus.data.db.PatrimoineDatabase/1.json").readText())
			.jsonObject.getValue("database").jsonObject									// schéma de la version 1
		SQLiteDatabase.openOrCreateDatabase(vContext.getDatabasePath(vName).apply { parentFile?.mkdirs() }, null).use { inDb ->
			vSchema.getValue("entities").jsonArray.forEach { inEntity ->
				val vTable = inEntity.jsonObject.getValue("tableName").jsonPrimitive.content	// table
				inDb.execSQL(inEntity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", vTable))
				inEntity.jsonObject["indices"]?.jsonArray?.forEach { inIndex ->
					inDb.execSQL(inIndex.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", vTable))
				}
			}
			vSchema.getValue("setupQueries").jsonArray.forEach { inDb.execSQL(it.jsonPrimitive.content) }
			inDb.execSQL("INSERT INTO asset (id, name, isin, archived, createdAt, updatedAt) VALUES ('a1', 'ETF Monde', 'LU1681043599', 0, 1, 2)")
			inDb.version = 1
		}
		val vDb = Room.databaseBuilder(vContext, PatrimoineDatabase::class.java, vName).allowMainThreadQueries().build()	// version actuelle
		val vAssets = vDb.assetDao().observeAll().first()						// actifs après migration
		assertEquals(listOf("ETF Monde"), vAssets.map { it.name })
		assertEquals("LU1681043599", vAssets.single().isin)
		assertNull(vAssets.single().ticker)
		vDb.assetDao().update(vAssets.single().copy(ticker = "CW8.PA"))
		assertEquals("CW8.PA", vDb.assetDao().observeAll().first().single().ticker)
		vDb.close()
	}
}
