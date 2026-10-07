package fr.maubin.patrimoineplus.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.ConfirmDialog
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.components.ReorderableColumn
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.home.accountDetail
import fr.maubin.patrimoineplus.ui.shell.PrivacyAction
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin

/*
	Feuille ouverte sur l'écran des comptes.
*/
private sealed interface AccountSheetState
{
	/*
		Création d'un compte.
		form : saisie de départ
	*/
	data class Creating(val form: AccountForm) : AccountSheetState

	/*
		Modification d'un compte.
		item : compte modifié
		form : saisie de départ
	*/
	data class Editing(val item: AccountItem, val form: AccountForm) : AccountSheetState
}

/*
	Onglet des comptes : liste réordonnable avec la valeur de chaque compte,
	création et modification en feuille du bas, comptes archivés à part.
	inOnToggleHidden : bascule le mode discret
	inViewModel      : ViewModel fourni par Hilt
*/
@Composable
fun AccountsScreen(
	inOnToggleHidden : () -> Unit,
	inViewModel      : AccountsViewModel = hiltViewModel()
)
{
	val vState    by inViewModel.uiState.collectAsStateWithLifecycle()		// état à afficher
	var vSheet    by remember { mutableStateOf<AccountSheetState?>(null) }	// feuille ouverte
	var vDeleting by remember { mutableStateOf<AccountItem?>(null) }		// compte à supprimer
	var vFinal    by remember { mutableStateOf<AccountItem?>(null) }		// suppression définitive à confirmer

	AccountsContent(
		inState          = vState,
		inOnToggleHidden = inOnToggleHidden,
		inOnAdd          = { vSheet = AccountSheetState.Creating(inViewModel.newForm()) },
		inOnOpen         = { inItem -> inViewModel.editForm(inItem.account) { vSheet = AccountSheetState.Editing(inItem, it) } },
		inOnReorder      = inViewModel::reorder
	)

	when (val vOpen = vSheet)
	{
		null                          -> Unit
		is AccountSheetState.Creating -> AccountSheet(
			inInitial    = vOpen.form,
			inEditing    = null,
			inTypeLocked = false,
			inOnSave     = { inForm, inOnErrors -> inViewModel.save(inForm, null) { if (it.isEmpty) vSheet = null else inOnErrors(it) } },
			inOnArchive  = {},
			inOnDelete   = {},
			inOnDismiss  = { vSheet = null }
		)
		is AccountSheetState.Editing  -> AccountSheet(
			inInitial    = vOpen.form,
			inEditing    = vOpen.item.account,
			inTypeLocked = vOpen.item.operations > 0,
			inOnSave     = { inForm, inOnErrors -> inViewModel.save(inForm, vOpen.item.account) { if (it.isEmpty) vSheet = null else inOnErrors(it) } },
			inOnArchive  = {
				inViewModel.setArchived(vOpen.item.account, !vOpen.item.account.archived)
				vSheet = null
			},
			inOnDelete   = {
				vDeleting = vOpen.item
				vSheet    = null
			},
			inOnDismiss  = { vSheet = null }
		)
	}

	vDeleting?.let { inItem ->
		if (inItem.operations == 0)
		{
			ConfirmDialog(
				inTitle     = "Supprimer « ${inItem.account.name} » ?",
				inText      = "Ce compte n’a pas d’opération ; son solde initial éventuel sera supprimé avec lui.",
				inAction    = "Supprimer",
				inOnConfirm = {
					inViewModel.delete(inItem.account, inPermanent = false)
					vDeleting = null
				},
				inOnDismiss = { vDeleting = null }
			)
		}
		else
		{
			AlertDialog(
				onDismissRequest = { vDeleting = null },
				title            = { Text("« ${inItem.account.name} » a ${inItem.operations} opération(s)") },
				text             = { Text("L’archiver le masque des saisies en gardant son historique et sa place dans le patrimoine. La suppression définitive efface aussi toutes ses opérations.") },
				confirmButton    = {
					TextButton(onClick = {
						inViewModel.setArchived(inItem.account, true)
						vDeleting = null
					}) { Text("Archiver") }
				},
				dismissButton    = {
					Row {
						TextButton(onClick = { vDeleting = null }) { Text("Annuler") }
						TextButton(onClick = {
							vFinal    = inItem
							vDeleting = null
						}) { Text("Supprimer…", color = MaterialTheme.colorScheme.error) }
					}
				}
			)
		}
	}

	vFinal?.let { inItem ->
		ConfirmDialog(
			inTitle     = "Suppression définitive",
			inText      = "« ${inItem.account.name} » et ses ${inItem.operations} opération(s) seront effacés. Un virement avec un autre compte sera supprimé en entier, ce qui modifiera aussi le solde de cet autre compte. C’est irréversible.",
			inAction    = "Tout supprimer",
			inOnConfirm = {
				inViewModel.delete(inItem.account, inPermanent = true)
				vFinal = null
			},
			inOnDismiss = { vFinal = null }
		)
	}
}

/*
	Contenu de l'écran des comptes, sans état propre (prévisualisable).
	inState          : état à afficher
	inOnToggleHidden : bascule le mode discret
	inOnAdd          : ouvre la création d'un compte
	inOnOpen         : ouvre la modification d'un compte
	inOnReorder      : enregistre un nouvel ordre des comptes actifs
*/
@Composable
fun AccountsContent(
	inState          : AccountsUiState,
	inOnToggleHidden : () -> Unit,
	inOnAdd          : () -> Unit,
	inOnOpen         : (AccountItem) -> Unit,
	inOnReorder      : (List<AccountItem>) -> Unit
)
{
	ScreenScaffold(inTitle = "Comptes", inActions = { PrivacyAction(inOnToggleHidden) }) { inPadding ->
		Column(
			Modifier
				.padding(top = inPadding.calculateTopPadding())
				.verticalScroll(rememberScrollState())
				.padding(start = kScreenMargin, end = kScreenMargin, top = 16.dp, bottom = inPadding.calculateBottomPadding() + 16.dp)
		) {
			if (inState.loaded && inState.active.isEmpty() && inState.archived.isEmpty())
			{
				BorderedCard {
					EmptyState(
						inTitle       = "Aucun compte",
						inBody        = "Créez vos comptes courants, d’épargne et de bourse, avec leur solde de départ.",
						inActionLabel = "Ajouter un compte",
						inOnAction    = inOnAdd
					)
				}
				return@Column
			}
			if (inState.active.isNotEmpty())
			{
				BorderedCard {
					ReorderableColumn(inState.active, { it.account.id }, inOnReorder) { inItem -> AccountRow(inItem, inOnOpen) }
				}
				Text(
					text     = "Appui long sur un compte pour le déplacer.",
					style    = MaterialTheme.typography.bodySmall,
					color    = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = 4.dp, top = 8.dp)
				)
			}
			Spacer(Modifier.height(16.dp))
			AddButton("Ajouter un compte", inOnAdd)
			if (inState.archived.isNotEmpty())
			{
				Spacer(Modifier.height(32.dp))
				SectionHeader(inTitle = "Archivés")
				Spacer(Modifier.height(8.dp))
				BorderedCard {
					inState.archived.forEachIndexed { inIndex, inItem ->
						if (inIndex > 0)
						{
							HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
						}
						AccountRow(inItem, inOnOpen)
					}
				}
			}
		}
	}
}

/*
	Ligne d'un compte : nom, détail (type, établissement, part), valeur.
	Toucher ouvre la modification.
	inItem   : compte affiché
	inOnOpen : ouvre la modification
*/
@Composable
private fun AccountRow(
	inItem   : AccountItem,
	inOnOpen : (AccountItem) -> Unit
)
{
	Row(
		modifier          = Modifier
			.fillMaxWidth()
			.heightIn(min = 64.dp)
			.clickable { inOnOpen(inItem) }
			.padding(horizontal = 20.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(inItem.account.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
			Text(accountDetail(inItem.account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Spacer(Modifier.width(16.dp))
		AmountText(
			inCents = inItem.valueCents,
			inStyle = MaterialTheme.typography.titleMedium,
			inColor = if (inItem.valueCents < 0) MaterialTheme.colorScheme.error else Color.Unspecified
		)
	}
}

/*
	Bouton pleine largeur d'ajout, en contour.
	inLabel   : libellé
	inOnClick : action
*/
@Composable
fun AddButton(
	inLabel   : String,
	inOnClick : () -> Unit
)
{
	OutlinedButton(
		onClick        = inOnClick,
		contentPadding = PaddingValues(vertical = 14.dp),
		modifier       = Modifier.fillMaxWidth()
	) {
		Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
		Spacer(Modifier.width(8.dp))
		Text(inLabel)
	}
}
