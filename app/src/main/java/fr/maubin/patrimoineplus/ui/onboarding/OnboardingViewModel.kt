package fr.maubin.patrimoineplus.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.Opening
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.DefaultCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/*
	Compte proposé par l'assistant du premier lancement.
	name     : nom proposé
	type     : type
	selected : vrai si l'utilisateur le garde
	opening  : solde d'aujourd'hui, tel que saisi
*/
data class ProposedAccount(
	val name     : String,
	val type     : AccountType,
	val selected : Boolean = true,
	val opening  : String  = ""
)

/*
	ViewModel de l'assistant du premier lancement : décide s'il faut
	l'afficher, crée les comptes et catégories retenus.
	inSettings   : réglages (assistant terminé)
	inAccounts   : dépôt des comptes
	inCategories : dépôt des catégories
	inClock      : horloge
*/
@HiltViewModel
class OnboardingViewModel @Inject constructor(
	private val inSettings   : SettingsRepository,
	private val inAccounts   : AccountRepository,
	private val inCategories : CategoryRepository,
	private val inClock      : Clock
) : ViewModel()
{
	// Vrai s'il faut afficher l'assistant ; null tant que ce n'est pas connu.
	val needsOnboarding: StateFlow<Boolean?> =
		combine(inSettings.onboardingDone, inAccounts.observeAccounts(), inCategories.observeCategories()) { inDone, inList, inCats ->
			!inDone && inList.isEmpty() && inCats.isEmpty()
		}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

	// Comptes proposés au départ.
	val proposals: List<ProposedAccount> = listOf(
		ProposedAccount("Compte courant", AccountType.CHECKING),
		ProposedAccount("Livret A", AccountType.SAVINGS),
		ProposedAccount("LDDS", AccountType.SAVINGS),
		ProposedAccount("PEA", AccountType.BROKERAGE)
	)

	// Noms des catégories proposées, pour l'aperçu.
	val categoryNames: List<String> = DefaultCatalog.categories().map { it.name }

	/*
		Crée les comptes cochés (solde initial daté d'aujourd'hui) et, si demandé,
		les catégories proposées, puis ferme l'assistant. Renvoie les noms des
		comptes dont le montant est illisible (rien n'est créé dans ce cas).
		inProposals      : comptes proposés, tels que modifiés
		inWithCategories : vrai pour créer les catégories proposées
		inOnDone         : reçoit les comptes en erreur (vide si tout est créé)
	*/
	fun finish(
		inProposals      : List<ProposedAccount>,
		inWithCategories : Boolean,
		inOnDone         : (List<String>) -> Unit
	)
	{
		val vChosen = inProposals.filter { it.selected }							// comptes retenus
		val vBad    = vChosen.filter { it.opening.isNotBlank() && parseEuros(it.opening) == null }.map { it.name }	// montants illisibles
		if (vBad.isNotEmpty())
		{
			inOnDone(vBad)
			return
		}
		viewModelScope.launch {
			val vToday = LocalDate.now(inClock)									// date des soldes initiaux
			vChosen.forEach { inProposal ->
				val vCents = if (inProposal.opening.isBlank()) 0L else parseEuros(inProposal.opening)!!	// solde initial
				inAccounts.create(inProposal.name, inProposal.type, null, 100, Opening(vCents, vToday))
			}
			if (inWithCategories)
			{
				inCategories.addAll(DefaultCatalog.categories())
			}
			inSettings.setOnboardingDone(true)
			inOnDone(emptyList())
		}
	}

	/*
		Ferme l'assistant sans rien créer.
	*/
	fun skip()
	{
		viewModelScope.launch { inSettings.setOnboardingDone(true) }
	}
}
