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
