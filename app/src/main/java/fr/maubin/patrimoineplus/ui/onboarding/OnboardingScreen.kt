package fr.maubin.patrimoineplus.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin

/*
	Assistant du premier lancement : propose les comptes de départ (avec le
	solde d'aujourd'hui) et les catégories ; tout reste modifiable ensuite.
	inProposals     : comptes proposés
	inCategoryNames : catégories proposées, pour l'aperçu
	inOnFinish      : crée ce qui est retenu ; reçoit les comptes en erreur par rappel
	inOnSkip        : ferme l'assistant sans rien créer
*/
@Composable
fun OnboardingScreen(
	inProposals     : List<ProposedAccount>,
	inCategoryNames : List<String>,
	inOnFinish      : (List<ProposedAccount>, Boolean, (List<String>) -> Unit) -> Unit,
	inOnSkip        : () -> Unit
)
{
	var vAccounts   by remember { mutableStateOf(inProposals) }				// comptes, tels que modifiés
	var vCategories by remember { mutableStateOf(true) }						// créer les catégories proposées
	var vErrors     by remember { mutableStateOf(emptyList<String>()) }		// comptes au montant illisible
	val vColors     = MaterialTheme.colorScheme								// palette courante
	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(vColors.surface)
			.systemBarsPadding()
			.imePadding()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = kScreenMargin, vertical = 24.dp)
	) {
		Text("Bienvenue", style = MaterialTheme.typography.displaySmall)
		Spacer(Modifier.height(12.dp))
		Text(
			text  = "PatrimoinePlus calcule votre patrimoine à partir de vos comptes et de leurs opérations. Tout reste sur ce téléphone.",
			style = MaterialTheme.typography.bodyLarge,
			color = vColors.onSurfaceVariant
		)
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Vos comptes")
		Text(
			text  = "Cochez ceux que vous avez et indiquez leur solde d’aujourd’hui. Noms, types et soldes restent modifiables.",
			style = MaterialTheme.typography.bodyMedium,
			color = vColors.onSurfaceVariant
		)
		Spacer(Modifier.height(12.dp))
		BorderedCard {
			vAccounts.forEachIndexed { inIndex, inProposal ->
				if (inIndex > 0)
				{
					HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = vColors.outlineVariant)
				}
				Row(
					modifier          = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Checkbox(
						checked         = inProposal.selected,
						onCheckedChange = { inChecked -> vAccounts = vAccounts.toMutableList().also { it[inIndex] = inProposal.copy(selected = inChecked) } },
						colors          = CheckboxDefaults.colors(checkedColor = vColors.onSurface, checkmarkColor = vColors.surface)
					)
					Column(Modifier.weight(1f)) {
						Text(inProposal.name, style = MaterialTheme.typography.titleMedium)
						Text(inProposal.type.label, style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
					}
					Spacer(Modifier.width(12.dp))
					OutlinedTextField(
						value           = inProposal.opening,
						onValueChange   = { inText -> vAccounts = vAccounts.toMutableList().also { it[inIndex] = inProposal.copy(opening = inText.filter { c -> c.isDigit() || c in ",.- " }) } },
						placeholder     = { Text("0,00") },
						suffix          = { Text("€") },
						enabled         = inProposal.selected,
						isError         = inProposal.name in vErrors,
						singleLine      = true,
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
						modifier        = Modifier.width(140.dp)
					)
				}
			}
		}
		if (vErrors.isNotEmpty())
		{
			Text(
				text     = "Montant illisible pour : ${vErrors.joinToString()} (ex. 1 234,56).",
				style    = MaterialTheme.typography.bodyMedium,
				color    = vColors.error,
				modifier = Modifier.padding(top = 8.dp)
			)
		}
		Spacer(Modifier.height(32.dp))
		SectionHeader(inTitle = "Catégories")
		Spacer(Modifier.height(8.dp))
		BorderedCard {
			Row(
				modifier          = Modifier
					.fillMaxWidth()
					.heightIn(min = 56.dp)
					.toggleable(value = vCategories, role = Role.Checkbox) { vCategories = it }
					.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
				verticalAlignment = Alignment.Top
			) {
				Checkbox(
					checked         = vCategories,
					onCheckedChange = null,
					colors          = CheckboxDefaults.colors(checkedColor = vColors.onSurface, checkmarkColor = vColors.surface),
					modifier        = Modifier.padding(12.dp)
				)
				Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
					Text("Créer les ${inCategoryNames.size} catégories proposées", style = MaterialTheme.typography.titleMedium)
					Text(inCategoryNames.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				}
			}
		}
		Spacer(Modifier.height(32.dp))
		Button(
			onClick        = { inOnFinish(vAccounts, vCategories) { vErrors = it } },
			contentPadding = PaddingValues(vertical = 14.dp),
			modifier       = Modifier.fillMaxWidth()
		) {
			Text("Commencer")
		}
		TextButton(onClick = inOnSkip, modifier = Modifier.fillMaxWidth()) {
			Text("Passer, je configurerai plus tard")
		}
	}
}
