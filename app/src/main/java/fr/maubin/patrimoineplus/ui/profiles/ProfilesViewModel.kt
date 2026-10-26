package fr.maubin.patrimoineplus.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.ProfileRepository
import fr.maubin.patrimoineplus.data.repo.ProfileView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
	ViewModel des profils : vue affichée (sélecteur de la barre du haut) et
	gestion des profils (réglages) ; le dernier refus est exposé.
	inProfiles : dépôt des profils
*/
@HiltViewModel
class ProfilesViewModel @Inject constructor(
	private val inProfiles : ProfileRepository
) : ViewModel()
{
	// Profils et vue affichée.
	val view: StateFlow<ProfileView> =
		inProfiles.observeView().stateIn(viewModelScope, SharingStarted.Eagerly, ProfileView(listOf(ProfileRepository.kDefault), ProfileRepository.kDefault.id))

	private val fError = MutableStateFlow<String?>(null)							// dernier refus

	// Dernier refus d'une modification ; null après une réussite.
	val error: StateFlow<String?> = fError

	/*
		Change de vue.
		inProfile : profil ; null pour la famille
	*/
	fun setView(inProfile: String?)
	{
		viewModelScope.launch { inProfiles.setView(inProfile) }
	}

	/*
		Ajoute un profil.
		inName : nom
		inOnDone : appelé après un ajout réussi
	*/
	fun add(
		inName   : String,
		inOnDone : () -> Unit = {}
	)
	{
		viewModelScope.launch { fError.value = inProfiles.add(inName)?.message; if (fError.value == null) inOnDone() }
	}

	/*
		Renomme un profil.
		inId   : profil
		inName : nouveau nom
	*/
	fun rename(
		inId   : String,
		inName : String
	)
	{
		viewModelScope.launch { fError.value = inProfiles.rename(inId, inName)?.message }
	}

	/*
		Retire un profil ; s'il était affiché, la vue revient au premier.
		inId : profil
	*/
	fun remove(inId: String)
	{
		viewModelScope.launch {
			fError.value = inProfiles.remove(inId)?.message
			if (fError.value == null && view.value.profile == inId) inProfiles.setView(view.value.profiles.firstOrNull { it.id != inId }?.id)
		}
	}
}
