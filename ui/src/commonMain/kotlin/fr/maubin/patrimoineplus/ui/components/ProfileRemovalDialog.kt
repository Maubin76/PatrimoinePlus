package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.domain.calc.ProfileRemoval

/*
	Confirmation de la suppression d'un profil qui a des comptes (téléphone
	et PC) : récapitulatif de ce que devient chaque compte. Comptes dont il
	est seul propriétaire : supprimés définitivement avec leurs opérations ;
	comptes partagés : sa part est retirée, le compte compte pour moins.
	inName      : nom du profil
	inPlan      : récapitulatif (voir profileRemoval)
	inOnConfirm : supprime le profil
	inOnDismiss : annule
*/
@Composable
fun ProfileRemovalDialog(
	inName      : String,
	inPlan      : ProfileRemoval,
	inOnConfirm : () -> Unit,
	inOnDismiss : () -> Unit
)
{
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant								// texte secondaire
	AlertDialog(
		onDismissRequest = inOnDismiss,
		title            = { Text("Supprimer le profil « $inName » ?") },
		text             = {
			Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
				if (inPlan.removed.isNotEmpty())
				{
					Text("Supprimés définitivement, avec leurs opérations ($inName en est seul propriétaire) :", style = MaterialTheme.typography.titleSmall)
					inPlan.removed.forEach { inItem ->
						Text("• ${inItem.account.name} : ${inItem.operations} opération(s)", style = MaterialTheme.typography.bodyMedium)
					}
				}
				if (inPlan.reduced.isNotEmpty())
				{
					Text("Partagés : la part de $inName est retirée", style = MaterialTheme.typography.titleSmall, modifier = Modifier.heightIn(min = 0.dp))
					inPlan.reduced.forEach { inItem ->
						Text("• ${inItem.account.name} : compté pour ${inItem.after} % au lieu de ${inItem.before} % (part de ${inItem.share} % retirée)", style = MaterialTheme.typography.bodyMedium)
					}
				}
				Text("Cette suppression ne peut pas être annulée.", style = MaterialTheme.typography.bodySmall, color = vMuted)
			}
		},
		confirmButton    = {
			TextButton(onClick = inOnConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Supprimer le profil") }
		},
		dismissButton    = { TextButton(onClick = inOnDismiss) { Text("Annuler") } }
	)
}
