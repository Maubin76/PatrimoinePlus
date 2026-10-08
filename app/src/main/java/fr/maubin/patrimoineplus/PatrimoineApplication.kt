package fr.maubin.patrimoineplus

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import fr.maubin.patrimoineplus.ui.lock.AppLock
import javax.inject.Inject

/*
	Point d'entrée de l'application : initialise le graphe d'injection Hilt et
	branche le verrou sur le cycle de vie du processus (premier plan,
	arrière-plan).
*/
@HiltAndroidApp
class PatrimoineApplication : Application()
{
	@Inject lateinit var fAppLock: AppLock										// verrou de l'application

	/*
		Démarrage : le verrou observe les passages au premier plan.
	*/
	override fun onCreate()
	{
		super.onCreate()
		ProcessLifecycleOwner.get().lifecycle.addObserver(fAppLock)
	}
}
