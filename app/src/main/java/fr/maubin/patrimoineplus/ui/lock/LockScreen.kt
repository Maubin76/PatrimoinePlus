package fr.maubin.patrimoineplus.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/*
	Écran de verrouillage : aucun montant visible ; la demande d'authentification
	s'ouvre d'elle-même, et le bouton la relance après un abandon.
	inMessage  : dernier message d'échec ; null sans échec
	inOnUnlock : lance l'authentification
*/
@Composable
fun LockScreen(
	inMessage  : String?,
	inOnUnlock : () -> Unit
)
{
	LaunchedEffect(Unit) { inOnUnlock() }
	Column(
		modifier            = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surface)
			.padding(32.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Text("PatrimoinePlus", style = MaterialTheme.typography.headlineMedium)
		Spacer(Modifier.height(8.dp))
		Text("Verrouillé", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
		if (inMessage != null)
		{
			Spacer(Modifier.height(16.dp))
			Text(inMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
		}
		Spacer(Modifier.height(32.dp))
		Button(onClick = inOnUnlock, contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)) {
			Text("Déverrouiller")
		}
	}
}
