package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.bank.BankLine
import fr.maubin.patrimoineplus.data.bank.Reconciliation
import fr.maubin.patrimoineplus.data.bank.bankEntryForm
import fr.maubin.patrimoineplus.data.bank.reconcile
import fr.maubin.patrimoineplus.desktop.BankSession
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.Cell
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.desktop.ui.components.OperationLine
import fr.maubin.patrimoineplus.desktop.ui.components.TileRow
import fr.maubin.patrimoineplus.domain.model.Category
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.components.TileFigure
import fr.maubin.patrimoineplus.ui.entry.editEntryForm
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.maubin.patrimoineplus.desktop.ui.LocalAssistantRoom

private val kDay  = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)			// date d'une ligne
private val kLong = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)		// bornes de la période

/*
	Saisie retenue pour une ligne du relevé à créer.
	checked    : ligne à créer
	label      : libellé
	categoryId : catégorie ; null sans catégorie
*/
private data class Choice(
	val checked    : Boolean,
	val label      : String,
	val categoryId : String?
)

/*
	Rapprochement d'un relevé de la banque avec un compte : chiffres de la
	comparaison, lignes à créer (proposées, à cocher et corriger, créées en
	une fois), opérations de l'appli absentes du relevé, lignes retrouvées.
	Recalculé à chaque changement des données : une ligne créée passe dans
	« Retrouvées ».
	inSpace      : données
	inController : contrôleur
	inSession    : relevé lu
*/
@Composable
fun ReconcileScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController,
	inSession    : BankSession
)
{
	val vAccount = inSpace.data.accounts.firstOrNull { it.id == inSession.accountId } ?: return	// compte rapproché
	val vResult  = remember(inSpace.data, inSession) { reconcile(inSession.lines, inSession.accountId, inSpace.data) }	// comparaison
	val vChoices = remember(inSession) { mutableStateMapOf<BankLine, Choice>() }	// saisies retenues
	var vError   by remember(inSession) { mutableStateOf<String?>(null) }			// refus de la création
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp).padding(bottom = LocalAssistantRoom.current),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = inController::closeBankStatement) { Icon(LineIcons.Back, contentDescription = "Retour au compte") }
			Spacer(Modifier.width(8.dp))
			Column(Modifier.weight(1f)) {
				Text("Rapprochement · ${vAccount.name}", style = MaterialTheme.typography.displaySmall)
				Text(
					"${inSession.fileName} : du ${vResult.from.format(kLong)} au ${vResult.to.format(kLong)}" +
						if (inSession.skipped > 0) " (${inSession.skipped} ligne(s) illisible(s) ignorée(s))" else "",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
		Figures(inSession, vResult)
		MissingTile(vResult, vChoices, inSpace.data.categories, vError) {
			val vForms = vResult.missing.mapNotNull { inMissing ->
				(vChoices[inMissing.line] ?: Choice(true, inMissing.label, inMissing.categoryId)).takeIf { it.checked }
					?.let { bankEntryForm(inMissing.line, it.label, it.categoryId, inSession.accountId, inSpace.data.categories) }
			}																		// saisies à créer
			vError = inController.createFromBank(vForms)
		}
		TileRow(
			Cell(1f) { inMod ->
				Tile(inModifier = inMod, inTitle = "Dans l’appli, pas chez la banque (${vResult.extra.size})") {
					if (vResult.extra.isEmpty()) Hint("Aucune : toutes les opérations de la période sont sur le relevé.")
					else Hint("Opération pas encore passée à la banque, ou montant différent : un clic pour la vérifier.")
					vResult.extra.forEach { inTx ->
						OperationLine(
							inIcon    = iconOf(inTx, inSpace),
							inTitle   = inTx.label,
							inDetail  = inTx.date.format(kDay),
							inCents   = inTx.postings.filter { it.accountId == inSession.accountId }.sumOf { it.amountCents },
							inOnClick = { inController.openTransaction(inTx) }
						)
					}
				}
			},
			Cell(1f) { inMod ->
				Tile(inModifier = inMod, inTitle = "Retrouvées (${vResult.matched.size})") {
					if (vResult.matched.isEmpty()) Hint("Aucune ligne du relevé n’a été retrouvée.")
					vResult.matched.forEach { inPair ->
						OperationLine(
							inIcon    = iconOf(inPair.transaction, inSpace),
							inTitle   = inPair.transaction.label,
							inDetail  = "${inPair.line.date.format(kDay)} · banque : ${inPair.line.label}",
							inCents   = inPair.line.amountCents,
							inOnClick = null
						)
					}
				}
			}
		)
	}
}

/*
	Icône d'une opération : celle de sa catégorie, ou de sa nature.
	inTx    : opération
	inSpace : données (catégories)
*/
private fun iconOf(
	inTx    : fr.maubin.patrimoineplus.domain.model.Transaction,
	inSpace : WorkspaceState
): String =
	when (inTx.kind)
	{
		fr.maubin.patrimoineplus.domain.model.TransactionKind.TRANSFER -> "repeat"
		fr.maubin.patrimoineplus.domain.model.TransactionKind.TRADE    -> "chart"
		else                                                            -> inSpace.data.categories.firstOrNull { it.id == inTx.categoryId }?.icon ?: "dots"
	}

/*
	Chiffres de la comparaison : lignes du relevé, retrouvées, à créer, et
	totaux de la période des deux côtés.
	inSession : relevé lu
	inResult  : comparaison
*/
@Composable
private fun Figures(
	inSession : BankSession,
	inResult  : Reconciliation
)
{
	TileRow(
		Cell(1f) { inMod -> Tile(inModifier = inMod) { TileFigure("Lignes du relevé") { Text("${inSession.lines.size}", style = MaterialTheme.typography.headlineMedium) } } },
		Cell(1f) { inMod -> Tile(inModifier = inMod) { TileFigure("Retrouvées") { Text("${inResult.matched.size}", style = MaterialTheme.typography.headlineMedium) } } },
		Cell(1f) { inMod -> Tile(inModifier = inMod) { TileFigure("À créer") { Text("${inResult.missing.size}", style = MaterialTheme.typography.headlineMedium) } } },
		Cell(1.4f) { inMod ->
			Tile(inModifier = inMod) {
				TileFigure("Total banque / appli (lignes comparées)") {
					Row(verticalAlignment = Alignment.CenterVertically) {
						AmountText(inCents = inResult.bankTotal, inStyle = MaterialTheme.typography.titleLarge, inShowPlusSign = true)
						Text("  /  ", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
						AmountText(inCents = inResult.appTotal, inStyle = MaterialTheme.typography.titleLarge, inShowPlusSign = true)
					}
				}
				Hint(if (inResult.bankTotal == inResult.appTotal) "Les deux côtés concordent." else "Écart à expliquer avec les listes ci-dessous.")
			}
		},
		inMinWidth = 900.dp
	)
}

/*
	Tuile des lignes à créer : une ligne par opération de la banque absente de
	l'appli, avec case à cocher, libellé et catégorie modifiables ; bouton de
	création en lot.
	inResult     : comparaison
	inChoices    : saisies retenues, par ligne
	inCategories : catégories
	inError      : refus de la dernière création ; null sinon
	inOnCreate   : crée les lignes cochées
*/
@Composable
private fun MissingTile(
	inResult     : Reconciliation,
	inChoices    : MutableMap<BankLine, Choice>,
	inCategories : List<Category>,
	inError      : String?,
	inOnCreate   : () -> Unit
)
{
	val vChecked = inResult.missing.count { inChoices[it.line]?.checked ?: true }	// lignes cochées (cochées par défaut)
	Tile(
		inTitle  = "Chez la banque, pas dans l’appli (${inResult.missing.size})",
		inAction = { Button(onClick = inOnCreate, enabled = vChecked > 0) { Text("Créer les $vChecked opération(s) cochée(s)") } }
	) {
		if (inResult.missing.isEmpty())
		{
			Hint("Rien à créer : chaque ligne du relevé a son opération dans l’appli.")
			return@Tile
		}
		Hint("Libellé et catégorie sont proposés d’après vos saisies passées ; corrigez-les si besoin. Un virement entre vos comptes se saisit plutôt avec « Nouvelle opération ».")
		if (inError != null) Text(inError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
		Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
			Spacer(Modifier.width(48.dp))
			listOf("Date" to 0.7f, "Libellé de la banque" to 2.2f, "Libellé" to 2f, "Catégorie" to 1.6f, "Montant" to 1f).forEach { (inHead, inWeight) ->
				Text(inHead, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(inWeight))
			}
		}
		HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
		inResult.missing.forEach { inMissing ->
			val vChoice = inChoices[inMissing.line] ?: Choice(true, inMissing.label, inMissing.categoryId)	// saisie retenue
			val vKinds  = if (inMissing.line.amountCents < 0) listOf(CategoryKind.EXPENSE) else listOf(CategoryKind.INCOME, CategoryKind.EXPENSE)	// natures possibles
			Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
				Checkbox(checked = vChoice.checked, onCheckedChange = { inChoices[inMissing.line] = vChoice.copy(checked = it) })
				Text(inMissing.line.date.format(kDay), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.7f))
				Text(inMissing.line.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2.2f).padding(end = 12.dp))
				InlineField(vChoice.label, { inChoices[inMissing.line] = vChoice.copy(label = it) }, Modifier.weight(2f).padding(end = 12.dp))
				CategoryChoice(
					inSelected = vChoice.categoryId,
					inOptions  = inCategories.filter { it.kind in vKinds && !it.archived }.sortedWith(compareBy({ it.kind }, { it.sortOrder })),
					inOnSelect = { inChoices[inMissing.line] = vChoice.copy(categoryId = it) },
					inModifier = Modifier.weight(1.6f)
				)
				AmountText(inCents = inMissing.line.amountCents, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = true, inModifier = Modifier.weight(1f))
			}
		}
	}
}

/*
	Champ de texte compact, souligné, pour une ligne de tableau.
	inValue    : texte
	inOnChange : modification
	inModifier : place du champ
*/
@Composable
private fun InlineField(
	inValue    : String,
	inOnChange : (String) -> Unit,
	inModifier : Modifier
)
{
	Column(inModifier) {
		BasicTextField(
			value         = inValue,
			onValueChange = inOnChange,
			singleLine    = true,
			textStyle     = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
			cursorBrush   = SolidColor(MaterialTheme.colorScheme.onSurface),
			modifier      = Modifier.fillMaxWidth().padding(vertical = 4.dp)
		)
		HorizontalDivider(color = MaterialTheme.colorScheme.outline)
	}
}

/*
	Choix d'une catégorie dans une ligne de tableau (menu déroulant).
	inSelected : catégorie choisie ; null sans catégorie
	inOptions  : catégories proposées
	inOnSelect : nouveau choix
	inModifier : place du choix
*/
@Composable
private fun CategoryChoice(
	inSelected : String?,
	inOptions  : List<Category>,
	inOnSelect : (String) -> Unit,
	inModifier : Modifier
)
{
	var vOpen by remember { mutableStateOf(false) }									// menu ouvert
	Column(inModifier) {
		TextButton(onClick = { vOpen = true }) {
			Text(inOptions.firstOrNull { it.id == inSelected }?.name ?: "Choisir…", maxLines = 1, overflow = TextOverflow.Ellipsis)
		}
		DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
			inOptions.forEach { inCategory ->
				DropdownMenuItem(
					text    = { Text(if (inCategory.kind == CategoryKind.INCOME) "${inCategory.name} (revenu)" else inCategory.name) },
					onClick = { vOpen = false; inOnSelect(inCategory.id) }
				)
			}
		}
	}
}
