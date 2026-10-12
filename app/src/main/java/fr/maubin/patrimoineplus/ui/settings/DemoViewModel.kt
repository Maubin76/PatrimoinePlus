package fr.maubin.patrimoineplus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.demo.DemoRepository
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.data.sync.SyncStore
import fr.maubin.patrimoineplus.data.sync.Syncer
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
	Actions de démonstration, proposées dans les Réglages en version debug
	seulement. Les deux actions arrêtent d'abord la synchronisation : des
	données fictives ou une base vide ne doivent jamais remplacer le fichier
	d'échange partagé avec le PC.
	inDemo      : dépôt des données de démonstration
	inSettings  : réglages (assistant du premier lancement)
	inSyncStore : mémoire de la synchronisation (dossier choisi)
	inSyncer    : synchronisation (état affiché)
*/
@HiltViewModel
class DemoViewModel @Inject constructor(
	private val inDemo      : DemoRepository,
	private val inSettings  : SettingsRepository,
	private val inSyncStore : SyncStore,
	private val inSyncer    : Syncer
) : ViewModel()
{
	/*
		Arrête la synchronisation avant de remplacer les données.
	*/
	private suspend fun stopSync()
	{
		inSyncStore.setFolder(null)
		inSyncer.sync()
	}

	/*
		Remplace toutes les données par six mois de démonstration.
		inOnDone : appelé une fois le chargement terminé
	*/
	fun loadDemo(inOnDone: () -> Unit)
	{
		viewModelScope.launch {
			stopSync()
			inDemo.loadDemo()
			inSettings.setOnboardingDone(true)
			inOnDone()
		}
	}

	/*
		Efface toutes les données ; l'assistant du premier lancement réapparaît.
		inOnDone : appelé une fois l'effacement terminé
	*/
	fun clearAll(inOnDone: () -> Unit)
	{
		viewModelScope.launch {
			stopSync()
			inDemo.clearAll()
			inSettings.setOnboardingDone(false)
			inOnDone()
		}
	}
}
