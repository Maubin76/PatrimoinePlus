package fr.maubin.patrimoineplus

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.maubin.patrimoineplus.data.backup.scheduleBackups
import fr.maubin.patrimoineplus.data.market.scheduleQuotes
import fr.maubin.patrimoineplus.data.sync.DataChanges
import fr.maubin.patrimoineplus.ui.lock.AppLock
import javax.inject.Inject
import fr.maubin.patrimoineplus.data.repo.PlanningRepository
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import fr.maubin.patrimoineplus.data.link.WifiSyncer
import fr.maubin.patrimoineplus.data.link.scheduleWifiSync

/*
	Point d'entrée de l'application : initialise le graphe d'injection Hilt et
	branche le verrou sur le cycle de vie du processus (premier plan,
	arrière-plan), crée les échéances arrivées à chaque retour au premier
	plan, et planifie la sauvegarde et la mise à jour des cours.
*/
@HiltAndroidApp
class PatrimoineApplication : Application(), Configuration.Provider
{
	@Inject lateinit var fAppLock: AppLock										// verrou de l'application
	@Inject lateinit var fChanges: DataChanges									// signal de modification des données
	@Inject lateinit var fWifi: WifiSyncer										// synchronisation Wi-Fi avec les PC
	@Inject lateinit var fPlanning: PlanningRepository						// échéances des récurrences et des crédits

	// Configuration de WorkManager, initialisé à la première demande (et non au démarrage).
	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder().build()

	/*
		Démarrage : le verrou observe les passages au premier plan ; la
		sauvegarde du jour est planifiée (et rattrapée si elle manque), ainsi
		que la mise à jour des cours ; la synchronisation Wi-Fi (au premier plan,
	après chaque saisie, en tâche de fond) démarre.
	*/
	override fun onCreate()
	{
		super.onCreate()
		ProcessLifecycleOwner.get().lifecycle.addObserver(fAppLock)
		ProcessLifecycleOwner.get().lifecycle.addObserver(fWifi)
		fWifi.start(fChanges.flow)
		ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver
		{
			/*
				Retour au premier plan : les échéances arrivées depuis sont créées.
				inOwner : processus
			*/
			override fun onStart(inOwner: LifecycleOwner)
			{
				inOwner.lifecycleScope.launch { runCatching { fPlanning.applyDue() } }
			}
		})
		scheduleBackups(this)
		scheduleQuotes(this)
		scheduleWifiSync(this)
	}
}
