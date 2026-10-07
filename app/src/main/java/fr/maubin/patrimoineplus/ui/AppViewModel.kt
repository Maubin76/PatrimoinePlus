package fr.maubin.patrimoineplus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.data.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
	État partagé par toute l'interface : mode discret et apparence.
	inSettings : réglages mémorisés
*/
@HiltViewModel
class AppViewModel @Inject constructor(
	private val inSettings : SettingsRepository
) : ViewModel()
{
	// Vrai quand les montants sont masqués.
	val amountsHidden: StateFlow<Boolean> =
		inSettings.amountsHidden.stateIn(viewModelScope, SharingStarted.Eagerly, false)

	/*
		Bascule le mode discret.
	*/
	fun toggleAmountsHidden()
	{
		setAmountsHidden(!amountsHidden.value)
	}

	/*
		Fixe le mode discret.
		inHidden : vrai pour masquer les montants
	*/
	fun setAmountsHidden(inHidden: Boolean)
	{
		viewModelScope.launch { inSettings.setAmountsHidden(inHidden) }
	}

	// Apparence choisie ; « Système » tant que le réglage n'est pas lu.
	val themeMode: StateFlow<ThemeMode> =
		inSettings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

	/*
		Fixe l'apparence de l'application.
		inMode : apparence choisie
	*/
	fun setThemeMode(inMode: ThemeMode)
	{
		viewModelScope.launch { inSettings.setThemeMode(inMode) }
	}
}
