package fr.maubin.patrimoineplus.domain.rules

const val kLockGraceMillis      = 60_000L										// délai en arrière-plan avant re-verrouillage

/*
	Faut-il verrouiller l'appli à son retour au premier plan ? Oui si le
	verrou est activé et que l'appli démarre (aucun passage en arrière-plan
	connu) ou est restée au moins une minute en arrière-plan.
	inEnabled      : verrou activé dans les réglages
	inBackgroundAt : instant du passage en arrière-plan (ms) ; null au démarrage
	inNow          : instant présent (ms)
*/
fun shouldLock(
	inEnabled      : Boolean,
	inBackgroundAt : Long?,
	inNow          : Long
): Boolean =
	inEnabled && (inBackgroundAt == null || inNow - inBackgroundAt >= kLockGraceMillis)

/*
	Faut-il afficher l'écran de connexion ? Toujours au lancement de l'appli
	(avec ou sans verrou) ; ensuite seulement si le verrou est activé et
	qu'une authentification est due (retour après une minute ou plus).
	inColdStart : l'appli vient d'être lancée et l'écran n'a pas encore été franchi
	inEnabled   : verrou activé dans les réglages
	inNeedsAuth : authentification due
*/
fun gateShown(
	inColdStart : Boolean,
	inEnabled   : Boolean,
	inNeedsAuth : Boolean
): Boolean =
	inColdStart || (inEnabled && inNeedsAuth)
