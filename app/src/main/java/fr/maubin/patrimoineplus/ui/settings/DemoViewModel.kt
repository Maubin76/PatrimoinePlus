package fr.maubin.patrimoineplus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.demo.DemoRepository
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
	Actions de démonstration, proposées dans les Réglages en version debug
	seulement.
	inDemo     : dépôt des données de démonstration
	inSettings : réglages (assistant du premier lancement)
*/
@HiltViewModel
class DemoViewModel @Inject constructor(
	private val inDemo     : DemoRepository,
	private val inSettings : SettingsRepository
) : ViewModel()
{
	/*
		Remplace toutes les données par six mois de démonstration.
		inOnDone : appelé une fois le chargement terminé
	*/
	fun loadDemo(inOnDone: () -> Unit)
	{
		viewModelScope.launch {
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
			inDemo.clearAll()
			inSettings.setOnboardingDone(false)
			inOnDone()
		}
	}
}
