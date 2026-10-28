package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.data.link.DeviceInfo
import fr.maubin.patrimoineplus.data.link.LinkClient
import fr.maubin.patrimoineplus.data.link.NetworkId
import fr.maubin.patrimoineplus.data.link.discoverPcs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Diagnostic réseau du PC (boucle locale, sans réseau réel) : lecture du
	Wi-Fi, de la passerelle et de la box dans les sorties de Windows (français
	et anglais) ; diagnostic démarré : le PC répond à la découverte et note le
	téléphone qui se présente ; arrêt.
*/
class NetworkDiagnosticTest
{
	private val kClock = Clock.fixed(Instant.parse("2026-10-28T13:05:09Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private val kPc    = DeviceInfo("pc-1", "PC-BUREAU", "windows", "1.4", 8)			// PC
	private var fDiag  : NetworkDiagnostic? = null										// diagnostic démarré

	/*
		Arrête le diagnostic.
	*/
	@After
	fun stop()
	{
		fDiag?.stop()
	}

	/*
		Sorties de Windows : nom du Wi-Fi (espace insécable du français, ligne
		BSSID ignorée), passerelle de plus petite métrique, adresse de la box.
	*/
	@Test
	fun windowsOutputs()
	{
		val vNetshFr = "\n    Nom                    : Wi-Fi\n    État                   : connecté\n    SSID                   : Maison 5G\n    BSSID                  : aa:bb:cc:dd:ee:ff\n"	// netsh en français
		val vNetshEn = "    Name                   : Wi-Fi\r\n    SSID                   : Livebox-1234\r\n    BSSID                  : aa:bb:cc:dd:ee:ff\r\n"	// netsh en anglais
		assertEquals("Maison 5G", parseSsid(vNetshFr))
		assertEquals("Livebox-1234", parseSsid(vNetshEn))
		assertNull(parseSsid("Aucune interface sans fil sur le système."))
		val vRoute = "Itinéraires actifs :\nDestination réseau    Masque réseau  Adr. passerelle   Adr. interface Métrique\n          0.0.0.0          0.0.0.0      192.168.1.1    192.168.1.20     35\n          0.0.0.0          0.0.0.0         10.0.0.1       10.0.4.33     25\n"	// route print
		assertEquals("10.0.0.1", parseGateway(vRoute))
		assertNull(parseGateway("Aucun"))
		val vArp   = "Interface : 10.0.4.33 --- 0x7\n  Adresse Internet      Adresse physique      Type\n  10.0.0.1              a4-3e-51-0b-22-9c     dynamique\n"	// arp -a
		assertEquals(NetworkId.box("a4:3e:51:0b:22:9c"), parseMac(vArp, "10.0.0.1")?.let(NetworkId::box))
		assertNull(parseMac(vArp, "10.0.0.10"))
	}

	/*
		Diagnostic démarré : découverte, présentation notée au journal ; arrêt.
	*/
	@Test
	fun answersThePhone()
	{
		val vDiag  = NetworkDiagnostic({ kPc }, kClock, { listOf(NetworkId(NetworkId.kKindWifi, "Maison")) }, 0, 0, InetAddress.getLoopbackAddress()).also { fDiag = it }	// diagnostic
		vDiag.start()
		val vState = vDiag.state.value															// état démarré
		assertTrue(vState.running)
		assertEquals(listOf(NetworkId(NetworkId.kKindWifi, "Maison")), vState.networks)
		val vFound = discoverPcs(listOf(InetAddress.getLoopbackAddress()), vDiag.beaconPort, 1_000)	// PC trouvé
		assertEquals("PC-BUREAU", vFound.single().beacon.name)
		val vPhone = LinkClient(DeviceInfo("tel-1", "Pixel 8", "android", "1.4", 8))		// téléphone
		assertEquals(kPc, vPhone.hello(InetAddress.getLoopbackAddress().hostAddress, vFound.single().beacon.port))
		assertEquals(listOf("14:05:09 — Pixel 8 (Android, version 1.4) depuis 127.0.0.1"), vDiag.state.value.visits)
		vDiag.stop()
		assertEquals(false, vDiag.state.value.running)
		assertEquals(1, vDiag.state.value.visits.size)											// journal gardé
	}
}
