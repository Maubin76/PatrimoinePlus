package fr.maubin.patrimoineplus

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.maubin.patrimoineplus.data.backup.scheduleBackups
import fr.maubin.patrimoineplus.data.market.scheduleQuotes
import fr.maubin.patrimoineplus.ui.lock.AppLock
import javax.inject.Inject

/*
	Point d'entrée de l'application : initialise le graphe d'injection Hilt et
	branche le verrou sur le cycle de vie du processus (premier plan,
	arrière-plan) et planifie la sauvegarde et la mise à jour des cours.
*/
@HiltAndroidApp
class PatrimoineApplication : Application(), Configuration.Provider
{
	@Inject lateinit var fAppLock: AppLock										// verrou de l'application

	// Configuration de WorkManager, initialisé à la première demande (et non au démarrage).
	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder().build()

	/*
		Démarrage : le verrou observe les passages au premier plan ; la
		sauvegarde du jour est planifiée (et rattrapée si elle manque), ainsi
		que la mise à jour des cours.
	*/
	override fun onCreate()
	{
		super.onCreate()
		ProcessLifecycleOwner.get().lifecycle.addObserver(fAppLock)
		scheduleBackups(this)
		scheduleQuotes(this)
	}
}
