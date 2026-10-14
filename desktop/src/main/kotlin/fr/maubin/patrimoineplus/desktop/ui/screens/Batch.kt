package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.CellField
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.desktop.ui.components.MenuCell
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.entry.BatchRow
import fr.maubin.patrimoineplus.ui.entry.proposeCategory
import fr.maubin.patrimoineplus.ui.icons.LineIcons

/*
	Saisie rapide : dépenses et revenus en série, dans une grille au clavier.
	Tab passe d'une case à l'autre, Entrée ajoute une ligne (curseur sur sa
	date), Ctrl+Entrée enregistre tout. La catégorie est proposée d'après le
	libellé ; montant sans signe = dépense, « + » = entrée. Tout est contrôlé
	puis enregistré en une fois ; les lignes refusées montrent leur erreur.
	Les lignes restent si l'on change d'écran. Section de l'écran Opérations.
	inSpace      : données
	inController : contrôleur
*/
@Composable
fun BatchSection(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vRows    by inController.batch.collectAsState()								// lignes de la grille
	val vErrors  = remember { mutableStateMapOf<Long, String>() }						// erreurs par ligne
	val vFocus   = remember { mutableStateMapOf<Long, FocusRequester>() }				// focus de la date de chaque ligne
	var vFocusOn by remember { mutableStateOf<Long?>(null) }							// ligne dont la date doit prendre le focus
	LaunchedEffect(Unit) { if (vRows.isEmpty()) inController.setBatch(listOf(inController.newBatchRow(null))) }
	LaunchedEffect(vFocusOn) { vFocusOn?.let { inKey -> runCatching { vFocus[inKey]?.requestFocus() } } }

	/*
		Ajoute une ligne après une autre et y place le curseur.
		inAfter : ligne précédente
	*/
	fun addRow(inAfter: BatchRow)
	{
		val vNew   = inController.newBatchRow(inAfter)									// nouvelle ligne
		val vIndex = vRows.indexOfFirst { it.key == inAfter.key }						// rang de la précédente
		inController.setBatch(vRows.toMutableList().apply { add(vIndex + 1, vNew) })
		vFocusOn = vNew.key
	}

	/*
		Enregistre toutes les lignes.
	*/
	fun save()
	{
		vErrors.clear()
		vErrors.putAll(inController.saveBatch())
		inController.batch.value.firstOrNull()?.let { if (vErrors.isEmpty()) vFocusOn = it.key }
	}

	val vFilled  = vRows.filterNot { it.isBlank }										// lignes remplies
	val vAmounts = vFilled.mapNotNull { inRow -> parseEuros(inRow.amount.trim().removePrefix("+"))?.let { if (inRow.amount.trim().startsWith("+")) it else -it } }	// montants lisibles
	Column(
		modifier            = Modifier.fillMaxWidth()
			.onPreviewKeyEvent { inEvent -> if (inEvent.type == KeyEventType.KeyDown && inEvent.isCtrlPressed && (inEvent.key == Key.Enter || inEvent.key == Key.NumPadEnter)) { save(); true } else false },
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		Column {
			Text("Saisie rapide", style = MaterialTheme.typography.headlineSmall)
			Text("Dépenses et revenus en série. Tab : case suivante · Entrée : nouvelle ligne · Ctrl+Entrée : tout enregistrer. Montant sans signe = dépense ; « + » devant = entrée (revenu, remboursement).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		Tile(
			inTitle  = "${vFilled.size} ligne(s) à enregistrer",
			inAction = {
				Text("Dépenses ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				AmountText(inCents = -vAmounts.filter { it < 0 }.sum(), inStyle = MaterialTheme.typography.titleMedium)
				Spacer(Modifier.width(16.dp))
				Text("Entrées ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				AmountText(inCents = vAmounts.filter { it > 0 }.sum(), inStyle = MaterialTheme.typography.titleMedium)
				Spacer(Modifier.width(16.dp))
				Button(onClick = ::save, enabled = vFilled.isNotEmpty()) { Text("Enregistrer") }
			}
		) {
			vErrors[-1L]?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
			Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
				listOf("Date" to 1f, "Compte" to 1.4f, "Libellé" to 2.6f, "Catégorie" to 1.6f, "Montant" to 1f).forEach { (inHead, inWeight) ->
					Text(inHead, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(inWeight).padding(end = 12.dp))
				}
				Spacer(Modifier.width(40.dp))
			}
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			vRows.forEach { inRow ->
				val vRequester = vFocus.getOrPut(inRow.key) { FocusRequester() }			// focus de la date
				val vUpdate    = { inNew: BatchRow -> inController.setBatch(vRows.map { if (it.key == inRow.key) inNew else it }); vErrors.remove(inRow.key); Unit }	// modification de la ligne
				BatchLine(
					inSpace    = inSpace,
					inRow      = inRow,
					inError    = vErrors[inRow.key],
					inFocus    = vRequester,
					inOnChange = vUpdate,
					inOnEnter  = { addRow(inRow) },
					inOnRemove = if (vRows.size > 1) { { inController.setBatch(vRows.filterNot { it.key == inRow.key }); vErrors.remove(inRow.key) } } else null
				)
			}
			Spacer(Modifier.padding(top = 8.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				OutlinedButton(onClick = { addRow(vRows.last()) }) { Text("Ajouter une ligne") }
				TextButton(onClick = { vErrors.clear(); inController.setBatch(listOf(inController.newBatchRow(null))) }) { Text("Tout effacer") }
			}
			Hint("Les virements et ordres de bourse se saisissent avec « Nouvelle opération ».")
		}
	}
}

/*
	Ligne de la grille : date, compte, libellé, catégorie (proposée si vide),
	montant, suppression ; erreur dessous.
	inSpace    : données
	inRow      : ligne
	inError    : erreur de la ligne ; null sinon
	inFocus    : focus de la date
	inOnChange : modification
	inOnEnter  : Entrée : nouvelle ligne
	inOnRemove : suppression ; null pour la dernière ligne restante
*/
@Composable
private fun BatchLine(
	inSpace    : WorkspaceState,
	inRow      : BatchRow,
	inError    : String?,
	inFocus    : FocusRequester,
	inOnChange : (BatchRow) -> Unit,
	inOnEnter  : () -> Unit,
	inOnRemove : (() -> Unit)?
)
{
	val vData     = inSpace.data																// données
	val vProposed = if (inRow.categoryId == null) proposeCategory(inRow.label, vData.transactions)?.let { inId -> vData.categories.firstOrNull { it.id == inId } } else null	// catégorie proposée
	val vEnter    = Modifier.onPreviewKeyEvent { inEvent ->
		if (inEvent.type == KeyEventType.KeyDown && !inEvent.isCtrlPressed && (inEvent.key == Key.Enter || inEvent.key == Key.NumPadEnter)) { inOnEnter(); true } else false
	}																					// Entrée : nouvelle ligne
	Column {
		Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
			CellField(inRow.date, { inOnChange(inRow.copy(date = it)) }, Modifier.weight(1f).padding(end = 12.dp).focusRequester(inFocus).then(vEnter), inPlaceholder = "JJ/MM/AAAA")
			MenuCell(
				inText     = vData.accounts.firstOrNull { it.id == inRow.accountId }?.name ?: "Compte…",
				inOptions  = vData.accounts.filterNot { it.archived || it.type.isProperty }.map { it.id to it.name },
				inOnSelect = { inOnChange(inRow.copy(accountId = it)) },
				inModifier = Modifier.weight(1.4f).padding(end = 12.dp)
			)
			CellField(inRow.label, { inOnChange(inRow.copy(label = it)) }, Modifier.weight(2.6f).padding(end = 12.dp).then(vEnter), inPlaceholder = "Libellé")
			MenuCell(
				inText     = vData.categories.firstOrNull { it.id == inRow.categoryId }?.name ?: vProposed?.let { "${it.name} (proposée)" } ?: "Catégorie…",
				inOptions  = vData.categories.filterNot { it.archived }.sortedWith(compareBy({ it.kind }, { it.sortOrder })).map { it.id to if (it.kind == CategoryKind.INCOME) "${it.name} (revenu)" else it.name },
				inOnSelect = { inOnChange(inRow.copy(categoryId = it)) },
				inModifier = Modifier.weight(1.6f).padding(end = 12.dp),
				inMuted    = inRow.categoryId == null
			)
			CellField(inRow.amount, { inOnChange(inRow.copy(amount = it)) }, Modifier.weight(1f).padding(end = 12.dp).then(vEnter), inPlaceholder = "0,00", inEnd = true, inError = inError != null)
			Box(Modifier.width(40.dp)) {
				if (inOnRemove != null) IconButton(onClick = inOnRemove) { Icon(LineIcons.Close, contentDescription = "Retirer la ligne", modifier = Modifier.size(16.dp)) }
			}
		}
		inError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp)) }
	}
}
