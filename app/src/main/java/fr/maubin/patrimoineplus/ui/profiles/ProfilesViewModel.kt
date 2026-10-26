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
import fr.maubin.patrimoineplus.domain.calc.ProfileRemoval

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

	private val fPending = MutableStateFlow<PendingRemoval?>(null)				// suppression à confirmer

	// Suppression d'un profil à confirmer (récapitulatif) ; null sans demande.
	val pending: StateFlow<PendingRemoval?> = fPending

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
		Demande le retrait d'un profil : sans compte, il est retiré tout de
		suite ; sinon le récapitulatif attend la confirmation.
		inId : profil
	*/
	fun askRemove(inId: String)
	{
		viewModelScope.launch {
			val vPlan = inProfiles.removal(inId)											// ce que deviennent ses comptes
			val vName = view.value.profiles.firstOrNull { it.id == inId }?.name.orEmpty()	// nom affiché
			if (vPlan.isEmpty) remove(inId) else fPending.value = PendingRemoval(inId, vName, vPlan)
		}
	}

	/*
		Confirme le retrait demandé.
	*/
	fun confirmRemove()
	{
		fPending.value?.let { remove(it.id) }
		fPending.value = null
	}

	/*
		Annule le retrait demandé.
	*/
	fun cancelRemove()
	{
		fPending.value = null
	}

	/*
		Retire un profil ; s'il était affiché, la vue revient au premier.
		inId : profil
	*/
	private fun remove(inId: String)
	{
		viewModelScope.launch {
			fError.value = inProfiles.remove(inId)?.message
			if (fError.value == null && view.value.profile == inId) inProfiles.setView(view.value.profiles.firstOrNull { it.id != inId }?.id)
		}
	}
}

/*
	Suppression d'un profil en attente de confirmation.
	id   : profil
	name : nom affiché
	plan : ce que deviennent ses comptes
*/
data class PendingRemoval(
	val id   : String,
	val name : String,
	val plan : ProfileRemoval
)
