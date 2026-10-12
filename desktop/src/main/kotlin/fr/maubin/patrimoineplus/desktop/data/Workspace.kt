package fr.maubin.patrimoineplus.desktop.data

import fr.maubin.patrimoineplus.data.DataSet
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.data.backup.readBackupXlsx
import fr.maubin.patrimoineplus.data.demo.buildDemoData
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.data.toDataSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate

private const val kPollMillis = 2_000L											// intervalle de surveillance du fichier

/*
	État des données lues.
*/
sealed interface LoadStatus
{
	// Pas de source.
	data object Idle : LoadStatus

	// Données de démonstration.
	data object Demo : LoadStatus

	/*
		Fichier d'échange lu.
		at : instant de la lecture (ms)
	*/
	data class Loaded(val at: Long) : LoadStatus

	// Dossier choisi, mais le fichier d'échange n'y est pas (encore).
	data object Waiting : LoadStatus

	/*
		Fichier illisible ou incohérent : les dernières données valides restent
		affichées.
		message : raison
	*/
	data class Invalid(val message: String) : LoadStatus
}

/*
	État de l'espace de travail.
	source : source choisie
	data   : données affichées
	status : état de la lecture
	today  : date de calcul
*/
data class WorkspaceState(
	val source : Source     = Source.None,
	val data   : DataSet    = DataSet.kEmpty,
	val status : LoadStatus = LoadStatus.Idle,
	val today  : LocalDate
)

/*
	Espace de travail de l'appli PC : charge la démonstration ou le fichier
	d'échange du dossier synchronisé, le surveille et le recharge quand il
	change (Syncthing). Un fichier illisible n'efface jamais les dernières
	données valides.
	inClock : horloge
*/
class Workspace(private val inClock: Clock)
{
	private val fState = MutableStateFlow(WorkspaceState(today = LocalDate.now(inClock)))	// état courant
	private var fWatch : Job? = null													// surveillance du fichier
	private var fStamp : Pair<Long, Long>? = null										// date et taille du fichier lu

	// État courant.
	val state: StateFlow<WorkspaceState> = fState

	/*
		Ouvre une source.
		inSource : source
		inScope  : portée de la surveillance du fichier ; null pour ne pas surveiller
	*/
	fun open(
		inSource : Source,
		inScope  : CoroutineScope? = null
	)
	{
		fWatch?.cancel()
		fWatch = null
		fStamp = null
		val vToday = LocalDate.now(inClock)										// date de calcul
		when (inSource)
		{
			Source.None      -> fState.value = WorkspaceState(today = vToday)
			Source.Demo      ->
			{
				val vDemo = buildDemoData(vToday)								// démonstration
				fState.value = WorkspaceState(Source.Demo, vDemo, LoadStatus.Demo, vToday)
			}
			is Source.Folder ->
			{
				fState.value = WorkspaceState(inSource, DataSet.kEmpty, LoadStatus.Waiting, vToday)
				reload()
				if (inScope != null)
				{
					fWatch = inScope.launch {
						while (isActive)
						{
							delay(kPollMillis)
							reload()
						}
					}
				}
			}
		}
	}

	/*
		Relit le fichier d'échange s'il a changé depuis la dernière lecture
		(date ou taille) ; sans effet hors dossier.
	*/
	fun reload()
	{
		val vSource = fState.value.source as? Source.Folder ?: return			// dossier ouvert
		val vFile   = vSource.path.resolve(kSyncFileName)						// fichier d'échange
		if (!Files.isRegularFile(vFile))
		{
			fStamp = null
			if (fState.value.status !is LoadStatus.Loaded)
			{
				fState.value = fState.value.copy(status = LoadStatus.Waiting)
			}
			return
		}
		val vStamp = runCatching { Files.getLastModifiedTime(vFile).toMillis() to Files.size(vFile) }.getOrNull() ?: return	// date et taille
		if (vStamp == fStamp)
		{
			return
		}
		val vRead = runCatching { Files.newInputStream(vFile).use { readBackupXlsx(it) } }
			.getOrElse { BackupRead.Invalid("Lecture impossible : ${it.message}") }	// fichier vérifié
		fStamp = vStamp
		fState.value = when (vRead)
		{
			is BackupRead.Valid   -> fState.value.copy(data = vRead.file.toDataSet(), status = LoadStatus.Loaded(inClock.millis()), today = LocalDate.now(inClock))
			is BackupRead.Invalid -> fState.value.copy(status = LoadStatus.Invalid(vRead.message))
		}
	}
}
