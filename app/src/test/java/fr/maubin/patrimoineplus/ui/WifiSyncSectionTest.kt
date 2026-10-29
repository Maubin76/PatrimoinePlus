package fr.maubin.patrimoineplus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import fr.maubin.patrimoineplus.data.MemorySyncStore
import fr.maubin.patrimoineplus.data.backup.BackupRepository
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.link.WifiSyncer
import fr.maubin.patrimoineplus.data.sync.SyncHooks
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.settings.WifiSyncSection
import fr.maubin.patrimoineplus.ui.settings.WifiSyncViewModel
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock

/*
	Section « Synchronisation Wi-Fi » des réglages du téléphone (Robolectric) :
	PC appairés avec leur dernière synchronisation, réseau familial, bouton de
	scan. Capture `synchro-wifi.png`.
*/
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class WifiSyncSectionTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	private val fFile = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "appareils.json")	// appareils du téléphone

	/*
		Efface les appareils.
	*/
	@After
	fun clean()
	{
		fFile.delete()
	}

	/*
		Deux PC appairés et le Wi-Fi de la maison.
	*/
	@Test
	fun section()
	{
		fFile.writeText("""{"pcs":[{"id":"pc-1","name":"PC du bureau","key":"x","port":47801,"lastSync":1792134000000},{"id":"pc-2","name":"PC du salon","key":"x","port":47801}],"home":[{"kind":"wifi","value":"Maison"}],"enabled":true}""")
		val vDb     = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PatrimoineDatabase::class.java).allowMainThreadQueries().build()	// base vide
		val vSyncer = WifiSyncer(ApplicationProvider.getApplicationContext(), BackupRepository(vDb, Clock.systemDefaultZone()), MemorySyncStore(null), object : SyncHooks
		{
			override suspend fun beforeImport(): Boolean = true
			override suspend fun afterImport() {}
		}, Clock.systemDefaultZone())																// synchronisation Wi-Fi
		fCompose.setContent { PatrimoineTheme(inDarkTheme = false) { Column { BorderedCard { WifiSyncSection(WifiSyncViewModel(vSyncer)) } } } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("PC du salon").assertExists()
		fCompose.onNodeWithText("Scanner le QR code du PC").assertExists()
		fCompose.onNodeWithText("Wi-Fi « Maison »").assertExists()
		fCompose.onRoot().captureRoboImage("build/outputs/roborazzi/synchro-wifi.png")
		vDb.close()
	}
}
