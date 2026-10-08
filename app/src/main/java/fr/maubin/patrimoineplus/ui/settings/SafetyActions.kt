package fr.maubin.patrimoineplus.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.backup.BackupRead
import fr.maubin.patrimoineplus.ui.lock.authenticate
import fr.maubin.patrimoineplus.ui.lock.canAuthenticate
import kotlinx.coroutines.launch

private val kImportTypes = arrayOf("application/json", "application/octet-stream", "text/plain")	// types acceptés à l'import

/*
	Branche le verrou et la sauvegarde des réglages : authentification avant
	d'activer le verrou, choix des fichiers par le système, vérification puis
	confirmation de l'import. Les résultats s'affichent en message éphémère.
	inSnackbar  : hôte des messages éphémères
	inViewModel : ViewModel du verrou et de la sauvegarde
*/
@Composable
fun rememberSafetyActions(
	inSnackbar  : SnackbarHostState,
	inViewModel : BackupViewModel = hiltViewModel()
): SafetyActions
{
	val vActivity = LocalActivity.current as? FragmentActivity				// activité, pour l'authentification
	val vScope    = rememberCoroutineScope()									// portée des messages
	val vLock     by inViewModel.lockEnabled.collectAsStateWithLifecycle()	// verrou activé
	val vLast     by inViewModel.lastExportAt.collectAsStateWithLifecycle()	// dernier export
	var vRead     by remember { mutableStateOf<BackupRead?>(null) }		// fichier lu, en attente de décision
	val vNotify   = { inMessage: String -> vScope.launch { inSnackbar.showSnackbar(inMessage) }; Unit }	// affiche un message

	val vExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { inUri ->
		if (inUri != null) inViewModel.export(inUri, vNotify)
	}																			// choix du fichier d'export
	val vImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { inUri ->
		if (inUri != null) inViewModel.read(inUri) { vRead = it }
	}																			// choix du fichier à importer

	ImportDialog(
		inRead      = vRead,
		inOnConfirm = { inFile ->
			vRead = null
			inViewModel.replaceAll(inFile, vNotify)
		},
		inOnDismiss = { vRead = null }
	)

	return SafetyActions(
		lockEnabled  = vLock,
		lastExport   = vLast?.let { formatExportDate(it) },
		onLockChange = { inEnable ->
			when
			{
				!inEnable                                       -> inViewModel.setLockEnabled(false)
				vActivity == null || !canAuthenticate(vActivity) -> vNotify("Aucune empreinte ni code n’est configuré sur le téléphone.")
				else                                            -> authenticate(
					inActivity  = vActivity,
					inTitle     = "Activer le verrou",
					inOnSuccess = { inViewModel.setLockEnabled(true) },
					inOnFailure = { vNotify("Verrou non activé : $it") }
				)
			}
		},
		onExport     = { vExporter.launch(inViewModel.exportFileName()) },
		onImport     = { vImporter.launch(kImportTypes) }
	)
}

/*
	Décision sur un fichier lu : confirmation avec le résumé de son contenu
	s'il est valide, motif du refus sinon. Rien n'est affiché sans fichier lu.
	inRead      : fichier lu ; null sans fichier en attente
	inOnConfirm : remplace les données par le contenu du fichier
	inOnDismiss : ferme sans rien modifier
*/
@Composable
private fun ImportDialog(
	inRead      : BackupRead?,
	inOnConfirm : (BackupFile) -> Unit,
	inOnDismiss : () -> Unit
)
{
	when (inRead)
	{
		null                 -> Unit
		is BackupRead.Valid   -> AlertDialog(
			onDismissRequest = inOnDismiss,
			title            = { Text("Remplacer toutes les données ?") },
			text             = { Text("Le fichier contient : ${inRead.file.summary}.\n\nToutes les données actuelles seront remplacées. C’est irréversible : exportez-les d’abord si besoin.") },
			confirmButton    = { TextButton(onClick = { inOnConfirm(inRead.file) }) { Text("Remplacer") } },
			dismissButton    = { TextButton(onClick = inOnDismiss) { Text("Annuler") } }
		)
		is BackupRead.Invalid -> AlertDialog(
			onDismissRequest = inOnDismiss,
			title            = { Text("Fichier refusé") },
			text             = { Text("${inRead.message}\n\nAucune donnée n’a été modifiée.") },
			confirmButton    = { TextButton(onClick = inOnDismiss) { Text("Fermer") } }
		)
	}
}
