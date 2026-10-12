package fr.maubin.patrimoineplus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
	État partagé par toute l'interface : mode discret, apparence et couleurs
	des hausses et des baisses.
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

	// Vrai pour colorer hausses et baisses.
	val trendColors: StateFlow<Boolean> =
		inSettings.trendColors.stateIn(viewModelScope, SharingStarted.Eagerly, false)

	/*
		Active ou désactive les couleurs des hausses et des baisses.
		inEnabled : vrai pour colorer
	*/
	fun setTrendColors(inEnabled: Boolean)
	{
		viewModelScope.launch { inSettings.setTrendColors(inEnabled) }
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
