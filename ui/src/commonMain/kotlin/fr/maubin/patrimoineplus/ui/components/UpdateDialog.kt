package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.update.UpdateOffer
import fr.maubin.patrimoineplus.data.update.sizeLabel
import kotlin.math.roundToInt

/*
	État de la fenêtre de mise à jour.
	offer    : mise à jour proposée
	progress : avancement du téléchargement, de 0 à 1 ; null avant le téléchargement
	error    : échec du téléchargement ou de l'installation ; null sans échec
	notice   : explication propre à l'appareil (autorisation à donner, dossier non modifiable) ; null sans
	action   : libellé du bouton principal
*/
data class UpdateUiState(
	val offer    : UpdateOffer,
	val progress : Float?  = null,
	val error    : String? = null,
	val notice   : String? = null,
	val action   : String  = "Mettre à jour"
)

/*
	Fenêtre « nouvelle version disponible » (téléphone et PC) : version
	proposée et installée, taille du fichier, rappel de mettre à jour l'autre
	appareil (appuyé si le format d'échange change), puis « Mettre à jour »,
	« Plus tard » et « Ignorer cette version ». Pendant le téléchargement :
	barre d'avancement et « Annuler ».
	inState    : état
	inOnUpdate : télécharge et installe (ou réessaie après un échec)
	inOnLater  : ferme ; la question reviendra au prochain lancement
	inOnIgnore : ne plus proposer cette version
	inOnCancel : interrompt le téléchargement
*/
@Composable
fun UpdateDialog(
	inState    : UpdateUiState,
	inOnUpdate : () -> Unit,
	inOnLater  : () -> Unit,
	inOnIgnore : () -> Unit,
	inOnCancel : () -> Unit
)
{
	val vOffer   = inState.offer														// mise à jour
	val vMuted   = MaterialTheme.colorScheme.onSurfaceVariant							// texte secondaire
	val vLoading = inState.progress != null && inState.error == null					// téléchargement en cours
	AlertDialog(
		onDismissRequest = { if (!vLoading) inOnLater() },
		title            = { Text("PatrimoinePlus ${vOffer.version} est disponible") },
		text             = {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				Text("Vous avez la version ${vOffer.current}. Téléchargement : ${sizeLabel(vOffer.asset.size)}.", style = MaterialTheme.typography.bodyMedium)
				if (vOffer.formatChanged)
				{
					Text(
						"Cette version change le format du fichier d’échange : mettez à jour le téléphone et le PC ensemble, sinon ils ne pourront plus se synchroniser.",
						style = MaterialTheme.typography.titleSmall,
						color = MaterialTheme.colorScheme.primary
					)
				}
				else
				{
					Text("Pensez à mettre aussi à jour votre autre appareil.", style = MaterialTheme.typography.bodySmall, color = vMuted)
				}
				inState.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = vMuted) }
				if (vLoading)
				{
					val vPercent = (inState.progress!! * 100).roundToInt()					// avancement en pour cent
					LinearProgressIndicator(
						progress = { inState.progress },
						modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Téléchargement à $vPercent %" }
					)
					Text(if (vPercent >= 100) "Installation…" else "Téléchargement… $vPercent %", style = MaterialTheme.typography.bodySmall, color = vMuted)
				}
				inState.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
			}
		},
		confirmButton    = {
			if (vLoading) TextButton(onClick = inOnCancel) { Text("Annuler") }
			else Button(onClick = inOnUpdate) { Text(if (inState.error != null) "Réessayer" else inState.action) }
		},
		dismissButton    = {
			if (!vLoading)
			{
				Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
					TextButton(onClick = inOnIgnore) { Text("Ignorer cette version") }
					TextButton(onClick = inOnLater) { Text("Plus tard") }
				}
			}
		}
	)
}
