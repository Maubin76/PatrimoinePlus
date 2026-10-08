package fr.maubin.patrimoineplus.ui.lock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.domain.rules.shouldLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
	UNKNOWN,																	// réglage pas encore lu : rien n'est affiché
	LOCKED,																		// authentification requise
	UNLOCKED																	// contenu accessible
}

/*
	Verrou de l'application : verrouille au démarrage et au retour au premier
	plan après une minute ou plus en arrière-plan (règle shouldLock), si le
	verrou est activé. Observe le cycle de vie du processus entier, pas d'une
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
	private var fBackgroundAt : Long? = null									// instant du passage en arrière-plan

	// État du verrou, à suivre par l'interface.
	val state: StateFlow<LockState> =
		combine(fEnabled, fNeedsAuth) { inEnabled, inNeeds ->
			when
			{
				inEnabled == null       -> LockState.UNKNOWN
				inEnabled && inNeeds    -> LockState.LOCKED
				else                    -> LockState.UNLOCKED
			}
		}.stateIn(fScope, SharingStarted.Eagerly, LockState.UNKNOWN)

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
		Déverrouille après une authentification réussie.
	*/
	fun unlock()
	{
		fNeedsAuth.value = false
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
