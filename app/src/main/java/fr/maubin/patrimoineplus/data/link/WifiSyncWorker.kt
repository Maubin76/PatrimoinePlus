package fr.maubin.patrimoineplus.data.link

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

private const val kWifiSync = "synchro-wifi"										// tâche périodique

/*
	Accès Hilt depuis la tâche de fond de la synchronisation Wi-Fi.
*/
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WifiSyncEntryPoint
{
	/*
		Synchronisation Wi-Fi du téléphone.
	*/
	fun wifiSyncer(): WifiSyncer
}

/*
	Tâche de fond : synchronise avec les PC de la maison, l'appli fermée. Ne
	s'exécute que sur un réseau non facturé à l'usage ; la synchronisation
	elle-même ne fait rien hors du réseau familial.
	inContext : contexte
	inParams  : paramètres de WorkManager
*/
class WifiSyncWorker(
	inContext : Context,
	inParams  : WorkerParameters
) : CoroutineWorker(inContext, inParams)
{
	/*
		Exécute la synchronisation (un échec attend le passage suivant).
	*/
	override suspend fun doWork(): Result
	{
		runCatching { EntryPointAccessors.fromApplication(applicationContext, WifiSyncEntryPoint::class.java).wifiSyncer().sync() }
		return Result.success()
	}
}

/*
	Planifie la synchronisation Wi-Fi en arrière-plan : toutes les 15 minutes
	au mieux (Android les espace en économie d'énergie), seulement sur un
	réseau non facturé à l'usage.
	inContext : contexte de l'application
*/
fun scheduleWifiSync(inContext: Context)
{
	val vWifi = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()	// Wi-Fi requis
	WorkManager.getInstance(inContext).enqueueUniquePeriodicWork(
		kWifiSync,
		ExistingPeriodicWorkPolicy.KEEP,
		PeriodicWorkRequestBuilder<WifiSyncWorker>(15, TimeUnit.MINUTES).setConstraints(vWifi).build()
	)
}
