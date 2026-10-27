package fr.maubin.patrimoineplus.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val kInstallAction = "fr.maubin.patrimoineplus.action.INSTALL_STATUS"	// résultat de l'installeur du système

/*
	Résultat d'une installation, tel que l'installeur du système le rapporte
	(un succès n'arrive guère : Android ferme l'appli pour la remplacer).
*/
sealed interface InstallEvent
{
	// Installation terminée.
	data object Done : InstallEvent

	// Installation refusée ou annulée.
	data class Failed(val message: String) : InstallEvent
}

/*
	Installation d'un APK téléchargé, remplaçable dans les tests.
*/
interface ApkInstaller
{
	// Résultats des installations lancées.
	val events: SharedFlow<InstallEvent>

	/*
		Vrai si l'utilisateur a autorisé l'appli à installer des applis
		(« Installer des applis inconnues »).
	*/
	fun canInstall(): Boolean

	/*
		Ouvre l'écran d'Android où donner cette autorisation.
	*/
	fun openPermission()

	/*
		Confie l'APK à l'installeur du système ; le résultat arrive par events.
		Sur Android 12 et plus, l'installation se fait sans écran de
		confirmation quand l'appli a elle-même installé sa version actuelle.
		inApk : APK téléchargé et vérifié
	*/
	suspend fun install(inApk: File)
}

/*
	Installation par PackageInstaller : session alimentée avec l'APK, puis
	résultat reçu par UpdateInstallReceiver.
	inContext : contexte de l'application
*/
@Singleton
class PackageApkInstaller @Inject constructor(
	@ApplicationContext private val inContext : Context
) : ApkInstaller
{
	override val events: SharedFlow<InstallEvent> = InstallEvents.flow

	/*
		Autorisation d'installer.
	*/
	override fun canInstall(): Boolean = inContext.packageManager.canRequestPackageInstalls()

	/*
		Écran de l'autorisation, pour cette appli.
	*/
	override fun openPermission()
	{
		inContext.startActivity(
			Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${inContext.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		)
	}

	/*
		Ouvre une session, y copie l'APK et la valide.
		inApk : APK
	*/
	override suspend fun install(inApk: File) = withContext(Dispatchers.IO) {
		val vInstaller = inContext.packageManager.packageInstaller					// installeur du système
		val vParams    = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
			setAppPackageName(inContext.packageName)
			setSize(inApk.length())
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
		}																			// réglages de la session
		val vId = vInstaller.createSession(vParams)										// session
		vInstaller.openSession(vId).use { inSession ->
			inSession.openWrite("PatrimoinePlus.apk", 0, inApk.length()).use { inOutput ->
				inApk.inputStream().use { it.copyTo(inOutput) }
				inSession.fsync(inOutput)
			}
			val vIntent  = Intent(kInstallAction).setPackage(inContext.packageName)		// rappel du résultat
			val vPending = PendingIntent.getBroadcast(inContext, vId, vIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)	// rempli par l'installeur
			inSession.commit(vPending.intentSender)
		}
	}
}

/*
	Canal des résultats d'installation, du récepteur vers l'interface.
*/
object InstallEvents
{
	// Résultats (le dernier est rejoué à un abonné qui arrive après lui).
	val flow = MutableSharedFlow<InstallEvent>(replay = 1, extraBufferCapacity = 4)
}

/*
	Reçoit le résultat de l'installeur du système : confirmation demandée
	(ouvre son écran), succès ou échec (transmis à l'interface).
*/
class UpdateInstallReceiver : BroadcastReceiver()
{
	/*
		Résultat reçu.
		inContext : contexte
		inIntent  : résultat (statut, message, écran de confirmation)
	*/
	override fun onReceive(
		inContext : Context,
		inIntent  : Intent
	)
	{
		when (val vStatus = inIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE))	// statut
		{
			PackageInstaller.STATUS_PENDING_USER_ACTION ->
			{
				@Suppress("DEPRECATION")
				val vConfirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) inIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else inIntent.getParcelableExtra(Intent.EXTRA_INTENT)	// écran de confirmation
				vConfirm?.let { inContext.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
			}
			PackageInstaller.STATUS_SUCCESS             -> InstallEvents.flow.tryEmit(InstallEvent.Done)
			else                                        -> InstallEvents.flow.tryEmit(InstallEvent.Failed(installMessage(vStatus, inIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))))
		}
	}
}

/*
	Message lisible d'un échec de l'installeur.
	inStatus  : statut
	inMessage : message technique ; null sans
*/
internal fun installMessage(
	inStatus  : Int,
	inMessage : String?
): String = when (inStatus)
{
	PackageInstaller.STATUS_FAILURE_ABORTED      -> "installation annulée"
	PackageInstaller.STATUS_FAILURE_CONFLICT     -> "l’APK n’est pas signé avec la même clé que l’appli installée (version de développement ?)"
	PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK incompatible avec ce téléphone"
	PackageInstaller.STATUS_FAILURE_STORAGE      -> "pas assez de place sur le téléphone"
	PackageInstaller.STATUS_FAILURE_BLOCKED      -> "installation bloquée par le téléphone"
	else                                         -> inMessage ?: "échec de l’installation"
}
