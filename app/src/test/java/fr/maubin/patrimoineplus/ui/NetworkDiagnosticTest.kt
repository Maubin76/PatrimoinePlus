package fr.maubin.patrimoineplus.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.maubin.patrimoineplus.data.link.DeviceInfo
import fr.maubin.patrimoineplus.data.link.LinkServer
import fr.maubin.patrimoineplus.ui.settings.NetworkDiagnosticSheet
import fr.maubin.patrimoineplus.ui.settings.NetworkDiagnosticViewModel
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.InetAddress
import android.os.Looper
import org.robolectric.Shadows

/*
	Diagnostic réseau du téléphone (Robolectric) : feuille affichée ; une
	adresse saisie à la main joint le serveur d'un PC sur la boucle locale, qui
	note la présentation du téléphone ; un PC absent est signalé. Capture
	`diagnostic-reseau.png`.
*/
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class NetworkDiagnosticTest
{
	@get:Rule
	val fCompose = createComposeRule()											// interface testée

	private val fHellos = mutableListOf<String>()									// présentations reçues par le PC
	private val fServer = LinkServer(
		inInfo    = { DeviceInfo("pc-1", "PC-BUREAU", "windows", "1.4", 8) },
		inKeyOf   = { null },
		inHandler = { error("non appairé") },
		inOnHello = { inPhone, _ -> fHellos += inPhone.platform }
	).also { it.start(0, InetAddress.getLoopbackAddress()) }						// PC sur la boucle locale

	/*
		Arrête le PC.
	*/
	@After
	fun stop()
	{
		fServer.stop()
	}

	/*
		Essai d'une adresse : le PC répond ; une adresse sans PC : refus.
	*/
	@Test
	fun manualAddress()
	{
		val vModel = NetworkDiagnosticViewModel(ApplicationProvider.getApplicationContext())	// diagnostic
		fCompose.setContent { PatrimoineTheme(inDarkTheme = false) { NetworkDiagnosticSheet(vModel) {} } }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Chercher les PC").assertExists()
		vModel.tryAddress("127.0.0.1:${fServer.port}")
		fCompose.waitUntil(10_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); vModel.state.value.probes.isNotEmpty() && !vModel.state.value.searching }
		val vProbe = vModel.state.value.probes.single()											// essai
		assertTrue(vProbe.detail, vProbe.ok)
		assertEquals("PC-BUREAU", vProbe.name)
		assertEquals(listOf("android"), fHellos)
		fCompose.waitForIdle()
		captureScreenRoboImage("build/outputs/roborazzi/diagnostic-reseau.png")
		vModel.tryAddress("127.0.0.1:1")
		fCompose.waitUntil(10_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !vModel.state.value.searching && !vModel.state.value.probes.first().ok }
		assertTrue(vModel.state.value.probes.first().detail.startsWith("Ne répond pas"))				// même adresse, autre port : résultat remplacé
	}
}
