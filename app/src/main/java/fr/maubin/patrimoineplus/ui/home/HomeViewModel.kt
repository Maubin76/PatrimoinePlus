package fr.maubin.patrimoineplus.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.domain.model.AccountType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val kDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)	// format de la date de calcul

/*
	Ligne d'un compte dans l'addition de l'accueil.
	name  : nom du compte
	cents : solde ou valeur, en centimes
*/
data class HomeAccountLine(
	val name  : String,
	val cents : Long
)

/*
	Groupe de comptes d'un même type, avec son sous-total.
	type  : type des comptes du groupe
	lines : comptes du groupe, dans l'ordre d'affichage
*/
data class HomeGroup(
	val type  : AccountType,
	val lines : List<HomeAccountLine>
)
{
	// Sous-total du groupe, en centimes.
	val subtotalCents: Long get() = lines.sumOf { it.cents }
}

/*
	État de l'écran d'accueil.
	asOf   : date à laquelle le patrimoine est calculé, formatée
	groups : groupes de comptes non vides, dans l'ordre des types
*/
data class HomeUiState(
	val asOf   : String,
	val groups : List<HomeGroup>
)
{
	// Patrimoine total, en centimes : somme des sous-totaux.
	val totalCents: Long get() = groups.sumOf { it.subtotalCents }

	// Vrai tant qu'aucun compte n'existe.
	val isEmpty: Boolean get() = groups.isEmpty()
}

/*
	ViewModel de l'accueil. La base de données arrive à l'étape 2 : d'ici là,
	l'état est vide, et l'écran montre son véritable état vide.
	inClock : horloge de l'application
*/
@HiltViewModel
class HomeViewModel @Inject constructor(
	inClock : Clock
) : ViewModel()
{
	private val fState = MutableStateFlow(									// état courant
		HomeUiState(asOf = LocalDate.now(inClock).format(kDateFormat), groups = emptyList())
	)

	// État exposé à l'écran.
	val uiState: StateFlow<HomeUiState> = fState.asStateFlow()
}
