package fr.maubin.patrimoineplus.ui

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sun.net.httpserver.HttpServer
import fr.maubin.patrimoineplus.data.settings.DataStoreSettingsRepository
import fr.maubin.patrimoineplus.data.update.ApkInstaller
import fr.maubin.patrimoineplus.data.update.InstallEvent
import fr.maubin.patrimoineplus.data.update.Release
import fr.maubin.patrimoineplus.data.update.ReleaseAsset
import fr.maubin.patrimoineplus.data.update.installMessage
import fr.maubin.patrimoineplus.ui.update.UpdateViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest

/*
	Mises à jour sur le téléphone (Robolectric, réseau local, installeur
	factice) : fenêtre au lancement, rien si la vérification est coupée ou
	la version ignorée ; sans autorisation d'installer, l'écran d'Android
	s'ouvre et l'installation reprend au retour ; échec de l'installeur
	affiché ; « Vérifier maintenant » : à jour ou injoignable.
*/
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UpdateViewModelTest
{
	/*
		Magasin de préférences en mémoire.
	*/
	private class MemoryStore : DataStore<Preferences>
	{
		private val fData = MutableStateFlow(emptyPreferences())					// préférences en cours

		override val data: Flow<Preferences> = fData

		/*
			Applique une modification et garde le résultat.
			inTransform : modification
		*/
		override suspend fun updateData(inTransform: suspend (Preferences) -> Preferences): Preferences =
			inTransform(fData.value).also { fData.value = it }
	}

	/*
		Installeur factice : autorisation réglable, installations notées.
	*/
	private class FakeInstaller : ApkInstaller
	{
		val fEvents      = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 4)	// résultats à émettre
		var fAllowed     = true															// autorisation d'installer
		var fAsked       = 0															// ouvertures de l'écran d'autorisation
		val fInstalled   = mutableListOf<File>()										// APK installés

		override val events: SharedFlow<InstallEvent> = fEvents
		override fun canInstall(): Boolean = fAllowed
		override fun openPermission() { fAsked++ }
		override suspend fun install(inApk: File) { fInstalled += inApk }
	}

	private val kPayload = ByteArray(120_000) { (it % 89).toByte() }					// APK servi (contenu factice)
	private lateinit var fServer : HttpServer											// serveur local
	private val fStore     = MemoryStore()												// réglages
	private val fInstaller = FakeInstaller()											// installeur

	/*
		Démarre le serveur de l'APK.
	*/
	@Before
	fun start()
	{
		fServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		fServer.createContext("/apk") { inExchange ->
			inExchange.sendResponseHeaders(200, kPayload.size.toLong()); inExchange.responseBody.use { it.write(kPayload) }
		}
		fServer.start()
	}

	/*
		Arrête le serveur.
	*/
	@After
	fun stop() = fServer.stop(0)

	/*
		Release 1.3 servie localement.
	*/
	private fun release() = Release("1.3", 7, listOf(
		ReleaseAsset("PatrimoinePlus-1.3.apk", kPayload.size.toLong(), "http://127.0.0.1:${fServer.address.port}/apk", MessageDigest.getInstance("SHA-256").digest(kPayload).joinToString("") { "%02x".format(it) }),
		ReleaseAsset("PatrimoinePlus-1.3-windows.zip", 77_000_000, "http://127.0.0.1:${fServer.address.port}/zip", null)
	))

	/*
		ViewModel des mises à jour, version installée 1.2.
		inRelease : release servie ; null : GitHub injoignable
		inAuto    : vérification au lancement
	*/
	private fun viewModel(
		inRelease : Release? = release(),
		inAuto    : Boolean  = true
	) = UpdateViewModel(DataStoreSettingsRepository(fStore), { inRelease ?: throw IOException("hors ligne") }, fInstaller, ApplicationProvider.getApplicationContext<Context>(), "1.2", inAuto)

	/*
		Attend une condition en faisant tourner le fil principal.
		inCondition : condition attendue
	*/
	private fun waitFor(inCondition: () -> Boolean)
	{
		repeat(250) {
			shadowOf(Looper.getMainLooper()).idle()
			if (inCondition()) return
			Thread.sleep(20)
		}
		throw AssertionError("condition jamais remplie")
	}

	/*
		Fenêtre au lancement, téléchargement vérifié, installation.
	*/
	@Test
	fun offerAndInstall()
	{
		val vModel = viewModel()																// mises à jour
		waitFor { vModel.state.value != null }
		assertEquals("PatrimoinePlus-1.3.apk", vModel.state.value!!.offer.asset.name)
		vModel.update()
		waitFor { fInstaller.fInstalled.isNotEmpty() }
		assertTrue(fInstaller.fInstalled.single().readBytes().contentEquals(kPayload))
		assertEquals(1f, vModel.state.value!!.progress)
	}

	/*
		Rien au lancement si la vérification est coupée, en debug, ou si la
		version est ignorée ; « Ignorer » mémorise la version.
	*/
	@Test
	fun silentCases()
	{
		val vFirst = viewModel()																// premier lancement
		waitFor { vFirst.state.value != null }
		vFirst.ignore()
		waitFor { runBlocking { DataStoreSettingsRepository(fStore).ignoredUpdate.first() } == "1.3" }
		assertNull(vFirst.state.value)
		val vNext = viewModel()																// lancement suivant
		Thread.sleep(300); shadowOf(Looper.getMainLooper()).idle()
		assertNull(vNext.state.value)
		runBlocking { DataStoreSettingsRepository(fStore).setIgnoredUpdate("") }
		vNext.setEnabled(false)
		waitFor { !vNext.enabled.value }
		val vOff = viewModel()																// vérification coupée
		Thread.sleep(300); shadowOf(Looper.getMainLooper()).idle()
		assertNull(vOff.state.value)
		runBlocking { DataStoreSettingsRepository(fStore).setUpdatesEnabled(true) }
		val vDebug = viewModel(inAuto = false)												// version debug
		Thread.sleep(300); shadowOf(Looper.getMainLooper()).idle()
		assertNull(vDebug.state.value)
	}

	/*
		Sans autorisation : écran d'Android ouvert, explication et « Installer »
		dans la fenêtre ; au retour avec l'autorisation, installation.
	*/
	@Test
	fun permission()
	{
		fInstaller.fAllowed = false
		val vModel = viewModel()																// mises à jour
		waitFor { vModel.state.value != null }
		vModel.update()
		waitFor { fInstaller.fAsked == 1 }
		assertEquals("Installer", vModel.state.value!!.action)
		assertTrue(vModel.state.value!!.notice!!.contains("Autoriser depuis cette source"))
		vModel.onResume()
		assertTrue(fInstaller.fInstalled.isEmpty())
		fInstaller.fAllowed = true
		vModel.onResume()
		waitFor { fInstaller.fInstalled.isNotEmpty() }
		assertNull(vModel.state.value!!.notice)
	}

	/*
		Échec rapporté par l'installeur : message dans la fenêtre.
	*/
	@Test
	fun installFailure()
	{
		val vModel = viewModel()																// mises à jour
		waitFor { vModel.state.value != null }
		vModel.update()
		waitFor { fInstaller.fInstalled.isNotEmpty() }
		fInstaller.fEvents.tryEmit(InstallEvent.Failed(installMessage(PackageInstaller.STATUS_FAILURE_CONFLICT, null)))
		waitFor { vModel.state.value?.error != null }
		assertEquals("La mise à jour a échoué : l’APK n’est pas signé avec la même clé que l’appli installée (version de développement ?).", vModel.state.value!!.error)
		assertNull(vModel.state.value!!.progress)
	}

	/*
		« Vérifier maintenant » : fenêtre même pour une version ignorée, « à
		jour », « injoignable ».
	*/
	@Test
	fun checkNow()
	{
		runBlocking { DataStoreSettingsRepository(fStore).setIgnoredUpdate("1.3") }
		val vMessages = mutableListOf<String>()												// messages montrés
		val vModel    = viewModel()															// mises à jour
		vModel.checkNow { vMessages += it }
		waitFor { vModel.state.value != null }
		viewModel(release().copy(version = "1.2"), inAuto = false).checkNow { vMessages += it }
		viewModel(null, inAuto = false).checkNow { vMessages += it }
		waitFor { vMessages.size == 2 }
		assertEquals(setOf("PatrimoinePlus est à jour (version 1.2).", "Vérification impossible : GitHub est injoignable."), vMessages.toSet())
	}
}
