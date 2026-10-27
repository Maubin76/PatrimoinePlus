package fr.maubin.patrimoineplus.ui.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.maubin.patrimoineplus.BuildConfig
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.data.update.ApkInstaller
import fr.maubin.patrimoineplus.data.update.InstallEvent
import fr.maubin.patrimoineplus.data.update.ReleaseSource
import fr.maubin.patrimoineplus.data.update.UpdateCheck
import fr.maubin.patrimoineplus.data.update.UpdatePlatform
import fr.maubin.patrimoineplus.data.update.checkUpdate
import fr.maubin.patrimoineplus.data.update.downloadAsset
import fr.maubin.patrimoineplus.data.update.findUpdate
import fr.maubin.patrimoineplus.ui.components.UpdateDialog
import fr.maubin.patrimoineplus.ui.components.UpdateUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Named

private const val kNeedPermission = "Android doit d’abord autoriser PatrimoinePlus à installer des applis : activez « Autoriser depuis cette source » dans l’écran qui s’ouvre, puis revenez ici."	// explication de l'autorisation

/*
	Mises à jour sur le téléphone : vérification au lancement (après le
	déverrouillage), fenêtre, téléchargement vérifié dans le cache, puis
	installation par l'installeur du système ; réglages (interrupteur,
	« Vérifier maintenant »).
	inSettings  : réglages mémorisés (vérification, version ignorée)
	inSource    : source de la dernière release
	inInstaller : installeur de l'APK
	inContext   : contexte (dossier du cache)
	inVersion   : version installée
	inAutoCheck : vérifier au lancement (faux en debug : l'APK publié n'aurait pas la même signature)
*/
@HiltViewModel
class UpdateViewModel @Inject constructor(
	private val inSettings  : SettingsRepository,
	private val inSource    : ReleaseSource,
	private val inInstaller : ApkInstaller,
	@ApplicationContext private val inContext : Context,
	@Named("appVersion") private val inVersion : String,
	@Named("autoUpdateCheck") inAutoCheck : Boolean
) : ViewModel()
{
	private val fState    = MutableStateFlow<UpdateUiState?>(null)				// fenêtre ; null sans
	private var fDownload : Job? = null												// téléchargement en cours
	private var fApk      : File? = null												// APK prêt, en attente de l'autorisation

	// Fenêtre de mise à jour ; null sans.
	val state: StateFlow<UpdateUiState?> = fState

	// Version installée.
	val version: String = inVersion

	// Vérification au lancement activée.
	val enabled: StateFlow<Boolean> = inSettings.updatesEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

	init
	{
		if (inAutoCheck)
		{
			viewModelScope.launch {
				if (inSettings.updatesEnabled.first())
				{
					findUpdate(inSource, inVersion, UpdatePlatform.ANDROID, inSettings.ignoredUpdate.first())?.let { fState.value = UpdateUiState(it) }
				}
			}
		}
		viewModelScope.launch {
			inInstaller.events.collect { inEvent ->
				if (inEvent is InstallEvent.Failed) fState.update { it?.copy(progress = null, error = "La mise à jour a échoué : ${inEvent.message}.") }
			}
		}
	}

	/*
		Vérification demandée depuis les réglages : fenêtre (même pour une
		version ignorée), ou message « à jour » ou « injoignable ».
		inOnMessage : reçoit le message à montrer
	*/
	fun checkNow(inOnMessage: (String) -> Unit)
	{
		viewModelScope.launch {
			when (val vCheck = checkUpdate(inSource, inVersion, UpdatePlatform.ANDROID))
			{
				is UpdateCheck.Available -> fState.value = UpdateUiState(vCheck.offer)
				UpdateCheck.UpToDate     -> inOnMessage("PatrimoinePlus est à jour (version $inVersion).")
				UpdateCheck.Unreachable  -> inOnMessage("Vérification impossible : GitHub est injoignable.")
			}
		}
	}

	/*
		« Mettre à jour » : télécharge l'APK et vérifie son empreinte, puis
		l'installe ; sans autorisation d'installer, ouvre l'écran d'Android
		pour la donner (l'installation reprend au retour dans l'appli).
	*/
	fun update()
	{
		val vState = fState.value ?: return												// fenêtre affichée
		if (fDownload?.isActive == true) return
		fApk?.takeIf { it.exists() }?.let { return installOrAsk(it) }
		fState.value = vState.copy(progress = 0f, error = null)
		fDownload = viewModelScope.launch {
			try
			{
				val vApk = downloadAsset(vState.offer.asset, File(File(inContext.cacheDir, "updates"), vState.offer.asset.name)) { inProgress ->
					fState.update { it?.copy(progress = inProgress) }
				}																		// APK vérifié
				fApk = vApk
				installOrAsk(vApk)
			}
			catch (inCancel: CancellationException)
			{
				fState.update { it?.copy(progress = null) }
				throw inCancel
			}
			catch (inError: Exception)
			{
				fState.update { it?.copy(progress = null, error = "La mise à jour a échoué : ${inError.message ?: "erreur inconnue"}.") }
			}
		}
	}

	/*
		Installe l'APK si l'autorisation est donnée ; sinon l'explique et
		ouvre l'écran d'Android.
		inApk : APK vérifié
	*/
	private fun installOrAsk(inApk: File)
	{
		if (!inInstaller.canInstall())
		{
			fState.update { it?.copy(progress = null, notice = kNeedPermission, action = "Installer") }
			inInstaller.openPermission()
			return
		}
		fState.update { it?.copy(progress = 1f, notice = null) }
		viewModelScope.launch {
			try { inInstaller.install(inApk) }
			catch (inError: Exception) { fState.update { it?.copy(progress = null, error = "La mise à jour a échoué : ${inError.message ?: "erreur inconnue"}.") } }
		}
	}

	/*
		Retour dans l'appli : si l'APK attendait l'autorisation et qu'elle est
		maintenant donnée, l'installation reprend.
	*/
	fun onResume()
	{
		val vApk = fApk ?: return														// APK en attente
		if (fState.value?.notice == kNeedPermission && inInstaller.canInstall()) installOrAsk(vApk)
	}

	/*
		Interrompt le téléchargement ; la fenêtre reste ouverte.
	*/
	fun cancel()
	{
		fDownload?.cancel()
		fDownload = null
		fState.update { it?.copy(progress = null) }
	}

	/*
		Ferme la fenêtre ; la question reviendra au prochain lancement.
	*/
	fun later()
	{
		cancel()
		fState.value = null
	}

	/*
		Ne plus proposer la version de la fenêtre.
	*/
	fun ignore()
	{
		val vVersion = fState.value?.offer?.version ?: return							// version ignorée
		later()
		viewModelScope.launch { inSettings.setIgnoredUpdate(vVersion) }
	}

	/*
		Active ou coupe la vérification au lancement.
		inOn : vrai pour vérifier
	*/
	fun setEnabled(inOn: Boolean)
	{
		viewModelScope.launch { inSettings.setUpdatesEnabled(inOn) }
	}
}

/*
	Fenêtre de mise à jour, affichée quand une nouvelle version est
	proposée ; reprend l'installation au retour de l'écran d'autorisation.
	inViewModel : mises à jour
*/
@Composable
fun UpdatePrompt(inViewModel: UpdateViewModel)
{
	val vState by inViewModel.state.collectAsStateWithLifecycle()					// fenêtre
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { inViewModel.onResume() }
	vState?.let { inState ->
		UpdateDialog(inState, inViewModel::update, inViewModel::later, inViewModel::ignore, inViewModel::cancel)
	}
}

/*
	Version installée et vérification au lancement, pour l'injection.
*/
object AppBuild
{
	// Version de l'APK.
	val version: String = BuildConfig.VERSION_NAME

	// Vérification au lancement : en release seulement.
	val autoCheck: Boolean = !BuildConfig.DEBUG
}
