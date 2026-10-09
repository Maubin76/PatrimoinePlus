package fr.maubin.patrimoineplus.data.market

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

private const val kDailyQuotes   = "cours-quotidiens"							// tâche périodique
private const val kStartupQuotes = "cours-demarrage"							// tâche au démarrage

/*
	Accès Hilt depuis la tâche de fond des cours.
*/
@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuoteEntryPoint
{
	/*
		Mise à jour des cours avec son rythme.
	*/
	fun quoteRefresher(): QuoteRefresher
}

/*
	Tâche de fond : met à jour les cours s'ils datent de plus de quatre heures.
	Ne s'exécute qu'avec une connexion réseau ; un échec attend le passage
	suivant (le dernier cours connu reste utilisé).
	inContext : contexte
	inParams  : paramètres de WorkManager
*/
class QuoteWorker(
	inContext : Context,
	inParams  : WorkerParameters
) : CoroutineWorker(inContext, inParams)
{
	/*
		Exécute la mise à jour.
	*/
	override suspend fun doWork(): Result
	{
		EntryPointAccessors.fromApplication(applicationContext, QuoteEntryPoint::class.java).quoteRefresher().refreshIfStale()
		return Result.success()
	}
}

/*
	Planifie la mise à jour quotidienne des cours et une mise à jour au
	démarrage, toutes deux dès qu'une connexion est disponible.
	inContext : contexte de l'application
*/
fun scheduleQuotes(inContext: Context)
{
	val vNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()	// réseau requis
	val vWork    = WorkManager.getInstance(inContext)							// planificateur
	vWork.enqueueUniquePeriodicWork(
		kDailyQuotes,
		ExistingPeriodicWorkPolicy.KEEP,
		PeriodicWorkRequestBuilder<QuoteWorker>(1, TimeUnit.DAYS).setConstraints(vNetwork).build()
	)
	vWork.enqueueUniqueWork(kStartupQuotes, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<QuoteWorker>().setConstraints(vNetwork).build())
}
