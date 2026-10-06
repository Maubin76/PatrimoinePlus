package fr.maubin.patrimoineplus.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/*
	Écran d'accueil provisoire : le patrimoine total arrivera à l'étape 6.
	inViewModel : ViewModel fourni par Hilt
*/
@Composable
fun HomeScreen(inViewModel: HomeViewModel = hiltViewModel())
{
	val vState = inViewModel.uiState										// état à afficher
	Column(
		modifier            = Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Text("PatrimoinePlus", style = MaterialTheme.typography.headlineMedium)
		Text(vState.today, style = MaterialTheme.typography.bodyLarge)
		Text("Format des montants : ${vState.sampleAmount}", style = MaterialTheme.typography.bodyLarge)
	}
}
