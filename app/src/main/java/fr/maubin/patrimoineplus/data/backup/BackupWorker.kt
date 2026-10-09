package fr.maubin.patrimoineplus.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

private const val kDailyWork   = "sauvegarde-quotidienne"						// tâche périodique
private const val kCatchUpWork = "sauvegarde-rattrapage"						// tâche de rattrapage au démarrage
private const val kMaxAttempts = 3												// essais avant d'abandonner jusqu'au lendemain

/*
	Accès Hilt depuis la tâche de fond (créée par WorkManager, hors injection).
*/
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupEntryPoint
{
	/*
		Service de sauvegarde.
	*/
	fun autoBackup(): AutoBackup
}

/*
	Tâche de fond : sauvegarde du jour si elle n'est pas encore faite. Un échec
	est retenté quelques fois, puis abandonné jusqu'au passage suivant.
	inContext : contexte
	inParams  : paramètres de WorkManager
*/
class BackupWorker(
	inContext : Context,
	inParams  : WorkerParameters
) : CoroutineWorker(inContext, inParams)
{
	/*
		Exécute la sauvegarde.
	*/
	override suspend fun doWork(): Result
	{
		val vBackup = EntryPointAccessors.fromApplication(applicationContext, BackupEntryPoint::class.java).autoBackup()	// service
		return when (vBackup.backupIfDue())
		{
			is BackupOutcome.Failed -> if (runAttemptCount + 1 < kMaxAttempts) Result.retry() else Result.failure()
			else                    -> Result.success()
		}
	}
}

/*
	Planifie la sauvegarde quotidienne (une fois par jour, conservée d'un
	lancement à l'autre) et un rattrapage immédiat si celle du jour manque.
	inContext : contexte de l'application
*/
fun scheduleBackups(inContext: Context)
{
	val vWork = WorkManager.getInstance(inContext)								// planificateur
	vWork.enqueueUniquePeriodicWork(
		kDailyWork,
		ExistingPeriodicWorkPolicy.KEEP,
		PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build()
	)
	vWork.enqueueUniqueWork(kCatchUpWork, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<BackupWorker>().build())
}
