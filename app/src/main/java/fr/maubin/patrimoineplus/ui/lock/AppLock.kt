package fr.maubin.patrimoineplus.ui.lock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.domain.rules.gateShown
import fr.maubin.patrimoineplus.domain.rules.shouldLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/*
	État du verrou de l'application.
*/
enum class LockState
{
	UNKNOWN,																	// réglage pas encore lu : l'écran de démarrage reste affiché
	LOCKED,																		// écran de connexion affiché
	UNLOCKED																	// contenu accessible
}

/*
	Verrou de l'application : l'écran de connexion est toujours franchi au
	lancement (bouton « Entrer », ou authentification si le verrou est activé) ;
	ensuite, il revient au premier plan après une minute ou plus en
	arrière-plan (règle shouldLock), si le verrou est activé. Observe le cycle de vie du processus entier, pas d'une
	seule activité : passer par l'écran du code du téléphone n'est pas compté
	comme une sortie prolongée.
	inSettings : réglages (verrou activé)
	inClock    : horloge
*/
@Singleton
class AppLock @Inject constructor(
	private val inSettings : SettingsRepository,
	private val inClock    : Clock
) : DefaultLifecycleObserver
{
	private val fScope        = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)	// portée du verrou
	private val fEnabled      = inSettings.lockEnabled.stateIn(fScope, SharingStarted.Eagerly, null)	// verrou activé ; null avant lecture
	private val fNeedsAuth    = MutableStateFlow(true)							// authentification due (vrai au démarrage)
	private val fColdStart    = MutableStateFlow(true)							// lancement : écran de connexion pas encore franchi
	private val fIntro        = MutableStateFlow(true)							// animation du logo encore à jouer
	private var fBackgroundAt : Long? = null									// instant du passage en arrière-plan

	// État du verrou, à suivre par l'interface.
	val state: StateFlow<LockState> =
		combine(fEnabled, fNeedsAuth, fColdStart) { inEnabled, inNeeds, inCold ->
			when
			{
				inEnabled == null                       -> LockState.UNKNOWN
				gateShown(inCold, inEnabled, inNeeds)   -> LockState.LOCKED
				else                                    -> LockState.UNLOCKED
			}
		}.stateIn(fScope, SharingStarted.Eagerly, LockState.UNKNOWN)

	// Vrai si l'écran de connexion demande une authentification (verrou activé).
	val authRequired: StateFlow<Boolean> =
		fEnabled.map { it == true }.stateIn(fScope, SharingStarted.Eagerly, false)

	// Vrai tant que l'animation du logo du lancement n'a pas été jouée.
	val introPending: StateFlow<Boolean> = fIntro

	/*
		L'animation du lancement est terminée : elle ne sera plus rejouée.
	*/
	fun introDone()
	{
		fIntro.value = false
	}

	/*
		Retour au premier plan : verrouille si l'absence a duré une minute ou plus.
		inOwner : processus
	*/
	override fun onStart(inOwner: LifecycleOwner)
	{
		val vSince = fBackgroundAt ?: return									// rien à faire au démarrage
		if (shouldLock(fEnabled.value == true, vSince, inClock.millis()))
		{
			fNeedsAuth.value = true
		}
		fBackgroundAt = null
	}

	/*
		Passage en arrière-plan : mémorise l'instant.
		inOwner : processus
	*/
	override fun onStop(inOwner: LifecycleOwner)
	{
		fBackgroundAt = inClock.millis()
	}

	/*
		Franchit l'écran de connexion (après une authentification réussie si le
		verrou est activé).
	*/
	fun unlock()
	{
		fNeedsAuth.value = false
		fColdStart.value = false
		fIntro.value     = false
	}

	/*
		Active ou désactive le verrou (l'activation suit une authentification
		réussie : l'appli reste déverrouillée).
		inEnabled : vrai pour activer
	*/
	fun setEnabled(inEnabled: Boolean)
	{
		fNeedsAuth.value = false
		fScope.launch { inSettings.setLockEnabled(inEnabled) }
	}
}
