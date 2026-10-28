package fr.maubin.patrimoineplus.data.link

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import fr.maubin.patrimoineplus.BuildConfig
import fr.maubin.patrimoineplus.data.backup.kBackupVersion

/*
	Réseau du téléphone, tel que la synchronisation le voit.
	onWifi     : relié à un Wi-Fi
	unmetered  : réseau non facturé à l'usage
	wifi       : Wi-Fi reconnu par son nom ; null sans Wi-Fi ou sans autorisation de localisation
	permission : autorisation de localisation accordée (lecture du nom du Wi-Fi)
*/
data class PhoneNetwork(
	val onWifi     : Boolean,
	val unmetered  : Boolean,
	val wifi       : NetworkId?,
	val permission : Boolean
)

/*
	Vrai si l'autorisation de localisation, nécessaire pour lire le nom du
	Wi-Fi depuis Android 10, est accordée.
	inContext : contexte
*/
fun hasWifiNamePermission(inContext: Context): Boolean =
	inContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/*
	Réseau courant du téléphone : Wi-Fi ou non, facturé ou non, nom du Wi-Fi
	si l'autorisation est accordée (seul le nom est lu, jamais une position).
	inContext : contexte
*/
fun currentPhoneNetwork(inContext: Context): PhoneNetwork
{
	val vManager = inContext.getSystemService(ConnectivityManager::class.java)		// connexions
	val vCaps    = vManager?.getNetworkCapabilities(vManager.activeNetwork)			// réseau actif
	val vOnWifi  = vCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true	// Wi-Fi
	val vAllowed = hasWifiNamePermission(inContext)									// autorisation
	@Suppress("DEPRECATION")
	val vSsid    = if (vOnWifi && vAllowed) inContext.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid else null	// nom du Wi-Fi
	return PhoneNetwork(
		onWifi     = vOnWifi,
		unmetered  = vCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
		wifi       = NetworkId.wifi(vSsid),
		permission = vAllowed
	)
}

/*
	Nom du téléphone : celui donné dans les réglages d'Android, sinon le modèle.
	inContext : contexte
*/
fun phoneName(inContext: Context): String =
	Settings.Global.getString(inContext.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

/*
	Présentation de ce téléphone.
	inContext : contexte
	inId      : identifiant de l'appareil
*/
fun phoneInfo(
	inContext : Context,
	inId      : String
): DeviceInfo = DeviceInfo(inId, phoneName(inContext), "android", BuildConfig.VERSION_NAME, kBackupVersion)
