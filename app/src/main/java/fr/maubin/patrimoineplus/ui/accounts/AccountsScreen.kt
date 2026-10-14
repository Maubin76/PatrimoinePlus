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
import fr.maubin.patrimoineplus.ui.components.kAllocationSlots
import fr.maubin.patrimoineplus.ui.components.allocationColor
import fr.maubin.patrimoineplus.ui.components.SeriesDot
import fr.maubin.patrimoineplus.ui.components.ProportionBar
import fr.maubin.patrimoineplus.ui.components.BarSegment
import fr.maubin.patrimoineplus.ui.home.accountDetail
import fr.maubin.patrimoineplus.ui.shell.PrivacyAction
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin
import androidx.compose.foundation.layout.ColumnScope
import fr.maubin.patrimoineplus.ui.planning.PlanningViewModel
import fr.maubin.patrimoineplus.ui.planning.RecurrenceForm
import fr.maubin.patrimoineplus.ui.planning.RecurrencesSection
import fr.maubin.patrimoineplus.ui.planning.RecurrenceSheet
import fr.maubin.patrimoineplus.ui.planning.editRecurrenceForm
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.components.ProportionChart

/*
	Onglet des comptes : liste réordonnable avec la valeur de chaque compte,
	comptes archivés à part ; toucher un compte ouvre sa vue mois par mois,
	« Ajouter un compte » ouvre la création en feuille du bas.
	inOnToggleHidden : bascule le mode discret
	inOnOpenAccount  : ouvre la vue d'un compte
	inViewModel      : ViewModel fourni par Hilt
*/
@Composable
fun AccountsScreen(
	inOnToggleHidden : () -> Unit,
	inOnOpenAccount  : (String) -> Unit,
	inViewModel      : AccountsViewModel = hiltViewModel(),
	inPlanning       : PlanningViewModel = hiltViewModel()
)
{
	val vState    by inViewModel.uiState.collectAsStateWithLifecycle()		// état à afficher
	val vPlan     by inPlanning.state.collectAsStateWithLifecycle()			// récurrences et crédits
	var vCreating by remember { mutableStateOf<AccountForm?>(null) }		// création ouverte
	var vRec      by remember { mutableStateOf<RecurrenceForm?>(null) }		// récurrence ouverte

	AccountsContent(
		inState          = vState,
		inOnToggleHidden = inOnToggleHidden,
		inOnAdd          = { vCreating = inViewModel.newForm() },
		inOnOpen         = { inOnOpenAccount(it.account.id) },
		inOnReorder      = inViewModel::reorder,
		inExtra          = {
			Spacer(Modifier.height(32.dp))
			RecurrencesSection(vPlan.recurrences, vPlan.accounts, vPlan.categories, inPlanning.today(), { vRec = inPlanning.newForm() }) { vRec = editRecurrenceForm(it, vPlan.categories) }
		}
	)

	vRec?.let { inForm ->
		RecurrenceSheet(
			inStart      = inForm,
			inAccounts   = vPlan.accounts,
			inCategories = vPlan.categories,
			inOnSave     = { inSaved, inOnDone -> inPlanning.save(inSaved) { inErrors, inRefusal -> if (inErrors.isEmpty && inRefusal == null) vRec = null else inOnDone(inErrors, inRefusal) } },
			inOnDelete   = { inForm.id?.let(inPlanning::delete); vRec = null },
			inOnDismiss  = { vRec = null }
		)
	}

	vCreating?.let { inForm ->
		AccountSheet(
			inInitial    = inForm,
			inEditing    = null,
			inTypeLocked = false,
			inOnSave     = { inSaved, inOnErrors -> inViewModel.save(inSaved, null) { if (it.isEmpty) vCreating = null else inOnErrors(it) } },
			inOnArchive  = {},
			inOnDelete   = {},
			inOnDismiss  = { vCreating = null },
			inAccounts   = vPlan.accounts,
			inCategories = vPlan.categories,
			inOnSaveLoan = { inAccount, inLoan, inOnRefus -> inPlanning.createLoan(inAccount, inLoan) { inRefusal -> if (inRefusal == null) vCreating = null else inOnRefus(inRefusal) } },
			inProperties = vPlan.properties
		)
	}
}

/*
	Boîtes de suppression d'un compte : confirmation simple s'il n'a pas
	d'opération ; sinon choix entre archivage et suppression définitive, cette
	dernière demandant une seconde confirmation.
	inItem      : compte à supprimer
	inOnArchive : archive le compte
	inOnDelete  : supprime le compte (vrai = définitivement, avec ses opérations)
	inOnClose   : fermeture des boîtes (après action ou annulation)
*/
@Composable
fun AccountDeletion(
	inItem      : AccountItem,
	inOnArchive : () -> Unit,
	inOnDelete  : (Boolean) -> Unit,
	inOnClose   : () -> Unit
)
{
	var vFinal by remember { mutableStateOf(false) }							// seconde confirmation
	when
	{
		inItem.operations == 0 -> ConfirmDialog(
			inTitle     = "Supprimer « ${inItem.account.name} » ?",
			inText      = "Ce compte n’a pas d’opération ; son solde initial éventuel sera supprimé avec lui.",
			inAction    = "Supprimer",
			inOnConfirm = {
				inOnDelete(false)
				inOnClose()
			},
			inOnDismiss = inOnClose
		)
		vFinal                 -> ConfirmDialog(
			inTitle     = "Suppression définitive",
			inText      = "« ${inItem.account.name} » et ses ${inItem.operations} opération(s) seront effacés. Un virement avec un autre compte sera supprimé en entier, ce qui modifiera aussi le solde de cet autre compte. C’est irréversible.",
			inAction    = "Tout supprimer",
			inOnConfirm = {
				inOnDelete(true)
				inOnClose()
			},
			inOnDismiss = inOnClose
		)
		else                   -> AlertDialog(
			onDismissRequest = inOnClose,
			title            = { Text("« ${inItem.account.name} » a ${inItem.operations} opération(s)") },
			text             = { Text("L’archiver le masque des saisies en gardant son historique et sa place dans le patrimoine. La suppression définitive efface aussi toutes ses opérations.") },
			confirmButton    = {
				TextButton(onClick = {
					inOnArchive()
					inOnClose()
				}) { Text("Archiver") }
			},
			dismissButton    = {
				Row {
					TextButton(onClick = inOnClose) { Text("Annuler") }
					TextButton(onClick = { vFinal = true }) { Text("Supprimer…", color = MaterialTheme.colorScheme.error) }
				}
			}
		)
	}
}

/*
	Contenu de l'écran des comptes, sans état propre (prévisualisable).
	inState          : état à afficher
	inOnToggleHidden : bascule le mode discret
	inOnAdd          : ouvre la création d'un compte
	inOnOpen         : ouvre la vue d'un compte
	inOnReorder      : enregistre un nouvel ordre des comptes actifs
	inExtra          : contenu ajouté après les comptes (opérations récurrentes)
*/
@Composable
fun AccountsContent(
	inState          : AccountsUiState,
	inOnToggleHidden : () -> Unit,
	inOnAdd          : () -> Unit,
	inOnOpen         : (AccountItem) -> Unit,
	inOnReorder      : (List<AccountItem>) -> Unit,
	inExtra          : @Composable ColumnScope.() -> Unit = {}
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
			val vWeighted = (inState.active + inState.archived).filter { it.weight != null }	// comptes qui comptent dans le patrimoine
			if (vWeighted.size > 1)
			{
				SectionHeader(inTitle = "Répartition")
				Spacer(Modifier.height(8.dp))
				ProportionChart(
					inSegments    = vWeighted.groupBy { it.weight!!.rank.coerceAtMost(kAllocationSlots - 1) }.toSortedMap()
						.map { (inRank, inGroup) -> BarSegment(inGroup.sumOf { it.weight!!.counted }, allocationColor(inRank)) },
					inDescription = "Part de chaque compte dans le patrimoine : " +
						vWeighted.sortedBy { it.weight!!.rank }.joinToString(", ") { "${it.account.name} ${it.weight!!.percent}\u202F%" },
					inStable      = false
				)
				Spacer(Modifier.height(24.dp))
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
			inExtra()
		}
	}
}

/*
	Ligne d'un compte : pastille de sa teinte dans la répartition, nom, détail
	(type, établissement, part détenue) ; à droite, valeur et part du
	patrimoine dessous. Toucher ouvre la vue du compte.
	inItem   : compte affiché
	inOnOpen : ouvre la vue du compte
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
		inItem.weight?.let { inWeight ->
			SeriesDot(allocationColor(inWeight.rank))
			Spacer(Modifier.width(12.dp))
		}
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(inItem.account.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
			Text(accountDetail(inItem.account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			if (inItem.account.type == AccountType.LOAN)
			{
				Text("Restant dû" + (inItem.deductedInto?.let { " · déduit de $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
		Spacer(Modifier.width(16.dp))
		Column(horizontalAlignment = Alignment.End) {
			AmountText(
				inCents = inItem.valueCents,
				inStyle = MaterialTheme.typography.titleMedium,
				inColor = if (inItem.valueCents < 0) MaterialTheme.colorScheme.error else Color.Unspecified
			)
			inItem.weight?.let { inWeight ->
				Text("${inWeight.percent}\u202F%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
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
