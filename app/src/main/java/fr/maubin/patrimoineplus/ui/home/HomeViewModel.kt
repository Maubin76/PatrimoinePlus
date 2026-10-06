package fr.maubin.patrimoineplus.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.domain.format.formatEuros
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val kDateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)	// format de la date du jour

/*
	État de l'écran d'accueil.
	today        : date du jour, formatée
	sampleAmount : montant d'exemple, pour vérifier le format des montants
*/
data class HomeUiState(
	val today        : String,
	val sampleAmount : String
)

/*
	ViewModel de l'accueil. À l'étape 1, il ne sert qu'à vérifier la chaîne
	Hilt → ViewModel → écran, avec l'horloge injectée.
	inClock : horloge de l'application
*/
@HiltViewModel
class HomeViewModel @Inject constructor(
	inClock : Clock
) : ViewModel()
{
	val uiState = HomeUiState(												// état affiché, constant à cette étape
		today        = LocalDate.now(inClock).format(kDateFormat),
		sampleAmount = formatEuros(-123456789)
	)
}
