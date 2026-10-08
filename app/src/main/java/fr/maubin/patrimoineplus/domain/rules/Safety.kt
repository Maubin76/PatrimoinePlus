package fr.maubin.patrimoineplus.domain.rules

const val kLockGraceMillis      = 60_000L										// délai en arrière-plan avant re-verrouillage
const val kExportReminderMillis = 30L * 24 * 60 * 60 * 1000						// délai avant le rappel d'export (30 jours)

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
	Faut-il rappeler d'exporter les données ? Oui s'il y a des données et que
	le dernier export date d'au moins trente jours (ou n'a jamais eu lieu).
	inHasData      : la base contient au moins un compte
	inLastExportAt : instant du dernier export (ms) ; null s'il n'y en a jamais eu
	inNow          : instant présent (ms)
*/
fun exportReminderDue(
	inHasData      : Boolean,
	inLastExportAt : Long?,
	inNow          : Long
): Boolean =
	inHasData && (inLastExportAt == null || inNow - inLastExportAt >= kExportReminderMillis)
