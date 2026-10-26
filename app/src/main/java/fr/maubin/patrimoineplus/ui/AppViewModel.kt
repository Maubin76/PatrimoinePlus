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
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.data.repo.ThemeRepository
import fr.maubin.patrimoineplus.domain.model.CustomTheme

/*
	État partagé par toute l'interface : mode discret, apparence et couleurs
	des hausses et des baisses.
	inSettings : réglages mémorisés
*/
@HiltViewModel
class AppViewModel @Inject constructor(
	private val inSettings : SettingsRepository,
	private val inThemes   : ThemeRepository
) : ViewModel()
{
	// Thèmes de couleurs créés (partagés par le fichier d'échange).
	val themes: StateFlow<List<CustomTheme>> =
		inThemes.observeThemes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

	// Thème personnalisé choisi sur ce téléphone (identifiant) ; vide sans.
	val customTheme: StateFlow<String> =
		inSettings.customTheme.stateIn(viewModelScope, SharingStarted.Eagerly, "")

	/*
		Choisit un thème personnalisé.
		inId : thème
	*/
	fun setCustomTheme(inId: String)
	{
		viewModelScope.launch { inSettings.setCustomTheme(inId) }
	}

	/*
		Crée ou modifie un thème ; un thème créé devient le thème choisi.
		inId     : thème modifié ; null pour en créer un
		inName   : nom
		inColors : couleurs tapées
		inOnDone : reçoit le refus ; null si enregistré
	*/
	fun saveTheme(
		inId     : String?,
		inName   : String,
		inColors : List<String>,
		inOnDone : (String?) -> Unit
	)
	{
		viewModelScope.launch {
			inThemes.save(inId, inName, inColors)
				.onSuccess { inSaved -> if (inId == null) inSettings.setCustomTheme(inSaved); inOnDone(null) }
				.onFailure { inOnDone(it.message) }
		}
	}

	/*
		Supprime un thème ; s'il était choisi, le téléphone revient au thème Coloré.
		inId : thème
	*/
	fun deleteTheme(inId: String)
	{
		viewModelScope.launch {
			inThemes.delete(inId)
			if (customTheme.value == inId) { inSettings.setCustomTheme(""); inSettings.setColorTheme(ColorTheme.COLORFUL) }
		}
	}
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

	// Thème de couleurs choisi.
	val colorTheme: StateFlow<ColorTheme> =
		inSettings.colorTheme.stateIn(viewModelScope, SharingStarted.Eagerly, ColorTheme.MONOCHROME)

	/*
		Choisit le thème de couleurs.
		inTheme : thème
	*/
	fun setColorTheme(inTheme: ColorTheme)
	{
		viewModelScope.launch { inSettings.setColorTheme(inTheme); inSettings.setCustomTheme("") }
	}

	// Effet verre choisi.
	val glassEffect: StateFlow<Boolean> =
		inSettings.glassEffect.stateIn(viewModelScope, SharingStarted.Eagerly, false)

	/*
		Active ou retire l'effet verre.
		inGlass : vrai pour l'effet verre
	*/
	fun setGlassEffect(inGlass: Boolean)
	{
		viewModelScope.launch { inSettings.setGlassEffect(inGlass) }
	}

	// Clés des diagrammes de répartition affichés en cercle.
	val pieCharts: StateFlow<Set<String>> =
		inSettings.pieCharts.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

	/*
		Choisit la forme d'un diagramme de répartition.
		inKey : clé du diagramme
		inPie : vrai pour le cercle
	*/
	fun setPieChart(
		inKey : String,
		inPie : Boolean
	)
	{
		viewModelScope.launch { inSettings.setPieChart(inKey, inPie) }
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
