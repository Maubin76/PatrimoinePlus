package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.ui.components.PatrimoineLogo

/*
	Écran d'accueil (aucune source choisie) : ouvrir le dossier synchronisé
	avec le téléphone, ou essayer l'appli avec des données de démonstration,
	sans synchronisation.
	inOnFolder : choisir le dossier synchronisé
	inOnDemo   : ouvrir la démonstration
*/
@Composable
fun WelcomeScreen(
	inOnFolder : () -> Unit,
	inOnDemo   : () -> Unit
)
{
	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
			PatrimoineLogo(96.dp)
			Spacer(Modifier.height(28.dp))
			Text("PatrimoinePlus", style = MaterialTheme.typography.headlineLarge)
			Spacer(Modifier.height(12.dp))
			Text(
				text      = "Ouvrez le dossier synchronisé avec votre téléphone (par exemple Documents\\PatrimoinePlus, partagé par Syncthing) : " +
					"l’appli y lit le fichier $kSyncFileName. Ou essayez-la avec des données fictives.",
				style     = MaterialTheme.typography.bodyLarge,
				color     = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center
			)
			Spacer(Modifier.height(36.dp))
			Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
				Button(onClick = inOnFolder, contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)) {
					Text("Choisir le dossier synchronisé…")
				}
				OutlinedButton(onClick = inOnDemo, contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)) {
					Text("Essayer avec la démonstration")
				}
			}
		}
	}
}
