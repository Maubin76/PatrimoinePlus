package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.domain.format.formatFrenchDate
import fr.maubin.patrimoineplus.domain.format.parseFrenchDate
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.entry.editEntryForm
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import fr.maubin.patrimoineplus.ui.search.SearchHit
import fr.maubin.patrimoineplus.ui.search.SearchQuery
import fr.maubin.patrimoineplus.ui.search.searchTransactions
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.maubin.patrimoineplus.ui.components.operationAccent
import androidx.compose.material3.LocalContentColor
import fr.maubin.patrimoineplus.ui.components.glassWell
import fr.maubin.patrimoineplus.ui.components.glassRaised
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.height
import fr.maubin.patrimoineplus.desktop.ui.tour.tourAnchor
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import fr.maubin.patrimoineplus.desktop.ui.AssistantHint
import fr.maubin.patrimoineplus.desktop.ui.LocalAssistantRoom
import fr.maubin.patrimoineplus.ui.components.accountLabel

private val kRowDate   = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)	// date d'une ligne
private const val kPage = 20														// opérations affichées d'abord, puis à chaque « Afficher 20 de plus »

/*
	Opérations : recherche dans toutes les opérations (texte, compte,
	catégorie, période, montant ; Ctrl+F) et modification groupée. Un clic
	ouvre une opération ; la case à cocher, Ctrl+clic et Maj+clic
	sélectionnent ; la sélection peut changer de catégorie ou être supprimée.
	inSpace      : données
	inController : contrôleur
*/
@Composable
fun SearchScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vQuery    by inController.query.collectAsState()							// critères
	val vFocusAsk by inController.searchFocus.collectAsState()						// demandes de focus (Ctrl+F)
	val vResult   = remember(inSpace.data, vQuery) { searchTransactions(inSpace.data, vQuery) }	// opérations trouvées
	var vSelected by remember { mutableStateOf(emptySet<String>()) }				// opérations sélectionnées
	var vAnchor   by remember { mutableStateOf<Int?>(null) }						// dernière ligne cliquée (Maj+clic)
	var vMessage  by remember { mutableStateOf<String?>(null) }						// refus d'une action groupée
	val vFocus    = remember { FocusRequester() }									// champ de recherche
	val vVisible  = vResult.hits.map { it.transaction.id }.toSet()					// opérations affichées
	var vShown    by remember(vResult) { mutableIntStateOf(kPage) }					// opérations affichées (les plus récentes) ; 20 de plus à la demande
	if (!vVisible.containsAll(vSelected)) vSelected = vSelected intersect vVisible
	LaunchedEffect(vFocusAsk) { if (vFocusAsk > 0) { withFrameNanos {}; runCatching { vFocus.requestFocus() } } }
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp).padding(bottom = LocalAssistantRoom.current),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		Column {
			Text("Recherche", style = MaterialTheme.typography.displaySmall)
		}
		Box(Modifier.tourAnchor("search.filters")) { Filters(inSpace, vQuery, vFocus, inController::setQuery) }
		Tile(
			inModifier = Modifier.tourAnchor("search.results"),
			inTitle  = if (vResult.badQuery != null) "Recherche" else "${vResult.hits.size} opération(s)",
			inAction = {
				if (vResult.hits.isNotEmpty() && vResult.badQuery == null)
				{
					Text("Total ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
					AmountText(inCents = vResult.total, inStyle = MaterialTheme.typography.titleMedium, inShowPlusSign = true)
				}
			}
		) {
			vResult.badQuery?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
			if (vResult.hits.isEmpty() && vResult.badQuery == null) AssistantHint("Aucune opération ne répond à ces critères : essayez un autre mot, ou retirez un filtre.")
			if (vSelected.isNotEmpty())
			{
				SelectionBar(inSpace, vResult.hits.filter { it.transaction.id in vSelected }, inController, vMessage, { vMessage = it }) { vSelected = emptySet() }
			}
			if (vResult.hits.isNotEmpty())
			{
				val vAll = vSelected.size == vResult.hits.size							// tout est sélectionné
				Row(
					modifier          = Modifier.fillMaxWidth().heightIn(min = 40.dp).toggleable(value = vAll, role = Role.Checkbox) { vSelected = if (it) vVisible else emptySet() },
					verticalAlignment = Alignment.CenterVertically
				) {
					Checkbox(checked = vAll, onCheckedChange = null, modifier = Modifier.padding(12.dp))
					Text(if (vSelected.isEmpty()) "Tout sélectionner" else "${vSelected.size} sélectionnée(s)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			}
			vResult.hits.take(vShown).forEachIndexed { inIndex, inHit ->
				val vId = inHit.transaction.id												// opération de la ligne
				HitRow(
					inHit      = inHit,
					inSelected = vId in vSelected,
					inOnToggle = { vSelected = if (vId in vSelected) vSelected - vId else vSelected + vId; vAnchor = inIndex },
					inOnRange  = {
						val vFrom = vAnchor ?: inIndex												// début de la plage
						vSelected = vSelected + vResult.hits.subList(minOf(vFrom, inIndex), maxOf(vFrom, inIndex) + 1).map { it.transaction.id }
						vAnchor = inIndex
					},
					inOnOpen   = {
						if (vSelected.isNotEmpty()) { vSelected = if (vId in vSelected) vSelected - vId else vSelected + vId; vAnchor = inIndex }
						else if (inHit.transaction.kind == TransactionKind.OPENING) Unit
						else inController.openTransaction(inHit.transaction)
					}
				)
			}
			if (vResult.hits.size > vShown)
			{
				Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
					OutlinedButton(onClick = { vShown += kPage }) { Text("Afficher ${minOf(kPage, vResult.hits.size - vShown)} de plus") }
					Spacer(Modifier.width(16.dp))
					Text("${vShown} sur ${vResult.hits.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
		}
	}
}

/*
	Critères de recherche : texte, compte, catégorie, période, montants.
	inSpace   : données (comptes, catégories)
	inQuery   : critères
	inFocus   : focus du champ de texte
	inOnQuery : nouveaux critères
*/
@Composable
private fun Filters(
	inSpace   : WorkspaceState,
	inQuery   : SearchQuery,
	inFocus   : FocusRequester,
	inOnQuery : (SearchQuery) -> Unit
)
{
	var vFrom by remember(inQuery.from) { mutableStateOf(inQuery.from?.let(::formatFrenchDate).orEmpty()) }	// début saisi
	var vTo   by remember(inQuery.to) { mutableStateOf(inQuery.to?.let(::formatFrenchDate).orEmpty()) }		// fin saisie
	Tile {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(LineIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp))
			Spacer(Modifier.width(12.dp))
			OutlinedTextField(
				value         = inQuery.text,
				onValueChange = { inOnQuery(inQuery.copy(text = it)) },
				label         = { Text("Rechercher : libellé, notes, catégorie, compte ou montant") },
				singleLine    = true,
				modifier      = Modifier.weight(1f).focusRequester(inFocus)
			)
			if (!inQuery.isEmpty)
			{
				Spacer(Modifier.width(12.dp))
				TextButton(onClick = { inOnQuery(SearchQuery()) }) { Text("Effacer les filtres") }
			}
		}
		Spacer(Modifier.padding(top = 12.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
			Dropdown("Compte", listOf(null to "Tous les comptes") + inSpace.data.accounts.map { it.id to accountLabel(it) }, inQuery.accountId, Modifier.weight(1.2f)) { inOnQuery(inQuery.copy(accountId = it)) }
			Dropdown("Catégorie", listOf(null to "Toutes les catégories") + inSpace.data.categories.sortedWith(compareBy({ it.kind }, { it.sortOrder })).map { it.id to if (it.kind == CategoryKind.INCOME) "${it.name} (revenu)" else it.name }, inQuery.categoryId, Modifier.weight(1.2f)) { inOnQuery(inQuery.copy(categoryId = it)) }
			OutlinedTextField(
				value         = vFrom,
				onValueChange = { inText -> vFrom = inText; if (inText.isBlank()) inOnQuery(inQuery.copy(from = null)) else parseFrenchDate(inText)?.let { inOnQuery(inQuery.copy(from = it)) } },
				label         = { Text("Du (JJ/MM/AAAA)") },
				isError       = vFrom.isNotBlank() && parseFrenchDate(vFrom) == null,
				singleLine    = true,
				modifier      = Modifier.weight(1f)
			)
			OutlinedTextField(
				value         = vTo,
				onValueChange = { inText -> vTo = inText; if (inText.isBlank()) inOnQuery(inQuery.copy(to = null)) else parseFrenchDate(inText)?.let { inOnQuery(inQuery.copy(to = it)) } },
				label         = { Text("Au") },
				isError       = vTo.isNotBlank() && parseFrenchDate(vTo) == null,
				singleLine    = true,
				modifier      = Modifier.weight(1f)
			)
			OutlinedTextField(value = inQuery.minAmount, onValueChange = { inOnQuery(inQuery.copy(minAmount = it)) }, label = { Text("Montant min. (€)") }, singleLine = true, modifier = Modifier.weight(0.9f))
			OutlinedTextField(value = inQuery.maxAmount, onValueChange = { inOnQuery(inQuery.copy(maxAmount = it)) }, label = { Text("Montant max. (€)") }, singleLine = true, modifier = Modifier.weight(0.9f))
		}
	}
}

/*
	Liste déroulante compacte d'un filtre.
	inLabel    : libellé
	inOptions  : choix (valeur, texte)
	inSelected : valeur choisie
	inModifier : place
	inOnSelect : nouveau choix
*/
@Composable
private fun <T> Dropdown(
	inLabel    : String,
	inOptions  : List<Pair<T, String>>,
	inSelected : T,
	inModifier : Modifier,
	inOnSelect : (T) -> Unit
)
{
	var vOpen by remember { mutableStateOf(false) }									// menu ouvert
	Box(inModifier) {
		OutlinedButton(
			onClick        = { vOpen = true },
			modifier       = Modifier.fillMaxWidth().height(56.dp),
			shape          = MaterialTheme.shapes.extraSmall,
			border         = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
			contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
			colors         = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
		) {																			// même allure que les champs voisins (dates, montants)
			Text(inOptions.firstOrNull { it.first == inSelected }?.second ?: inLabel, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
			Icon(LineIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp))
		}
		DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
			inOptions.forEach { (inValue, inText) -> DropdownMenuItem(text = { Text(inText) }, onClick = { vOpen = false; inOnSelect(inValue) }) }
		}
	}
}

/*
	Ligne d'une opération trouvée : case à cocher, icône, libellé et détail,
	date, montant. Clic : ouvrir (ou cocher pendant une sélection) ;
	Ctrl+clic : cocher ; Maj+clic : cocher la plage depuis la dernière ligne.
	inHit      : opération trouvée
	inSelected : ligne cochée
	inOnToggle : coche ou décoche
	inOnRange  : coche la plage
	inOnOpen   : clic simple
*/
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HitRow(
	inHit      : SearchHit,
	inSelected : Boolean,
	inOnToggle : () -> Unit,
	inOnRange  : () -> Unit,
	inOnOpen   : () -> Unit
)
{
	val vColors = MaterialTheme.colorScheme											// palette courante
	Row(
		modifier          = Modifier
			.fillMaxWidth()
			.heightIn(min = 52.dp)
			.clip(MaterialTheme.shapes.small)
			.background(if (inSelected) glassWell(vColors.surfaceContainerHigh) else glassRaised(vColors.surface))
			.onClick(keyboardModifiers = { isShiftPressed }, onClick = inOnRange)
			.onClick(keyboardModifiers = { isCtrlPressed }, onClick = inOnToggle)
			.onClick(onClick = inOnOpen)
			.padding(end = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Checkbox(checked = inSelected, onCheckedChange = { inOnToggle() })
		val vBadge = operationAccent(inHit.amount)										// entrée ou sortie (thème Coloré)
		Box(Modifier.size(32.dp).clip(CircleShape).background(vBadge?.first ?: glassWell(vColors.surfaceContainer)), contentAlignment = Alignment.Center) {
			Icon(CategoryIcons.of(inHit.icon), contentDescription = null, tint = vBadge?.second ?: LocalContentColor.current, modifier = Modifier.size(18.dp))
		}
		Spacer(Modifier.width(14.dp))
		Column(Modifier.weight(1f)) {
			Text(inHit.transaction.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(inHit.detail + (inHit.transaction.notes?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
		}
		Text(inHit.transaction.date.format(kRowDate), style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant, modifier = Modifier.width(120.dp))
		AmountText(inCents = inHit.amount, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = true)
	}
}

/*
	Actions sur la sélection : changer de catégorie (catégories de la même
	nature que les opérations choisies), supprimer (avec confirmation),
	désélectionner.
	inSpace      : données (catégories)
	inChosen     : opérations sélectionnées
	inController : contrôleur
	inMessage    : refus de la dernière action ; null sinon
	inOnMessage  : nouveau message
	inOnClear    : vide la sélection
*/
@Composable
private fun SelectionBar(
	inSpace      : WorkspaceState,
	inChosen     : List<SearchHit>,
	inController : DesktopController,
	inMessage    : String?,
	inOnMessage  : (String?) -> Unit,
	inOnClear    : () -> Unit
)
{
	val vKinds   = inChosen.map { inHit -> inSpace.data.categories.firstOrNull { it.id == inHit.transaction.categoryId }?.kind }.toSet()	// natures des opérations
	val vKind    = vKinds.singleOrNull()												// nature commune ; null si mélange ou virements
	var vMenu    by remember { mutableStateOf(false) }								// menu des catégories
	var vConfirm by remember { mutableStateOf(false) }								// confirmation de suppression
	Row(
		modifier              = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(glassWell(MaterialTheme.colorScheme.surfaceContainer)).padding(horizontal = 16.dp, vertical = 8.dp),
		verticalAlignment     = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Text("${inChosen.size} sélectionnée(s)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
		Box {
			OutlinedButton(onClick = { vMenu = true }, enabled = vKind != null) { Text("Changer la catégorie…") }
			DropdownMenu(expanded = vMenu, onDismissRequest = { vMenu = false }) {
				inSpace.data.categories.filter { it.kind == vKind && !it.archived }.sortedBy { it.sortOrder }.forEach { inCategory ->
					DropdownMenuItem(text = { Text(inCategory.name) }, onClick = {
						vMenu = false
						inOnMessage(inController.recategorize(inChosen.map { it.transaction.id }, inCategory.id))
					})
				}
			}
		}
		OutlinedButton(onClick = { vConfirm = true }) { Text("Supprimer") }
		TextButton(onClick = inOnClear) { Text("Désélectionner") }
	}
	if (vKind == null) Hint("Pour changer la catégorie, choisissez seulement des dépenses, ou seulement des revenus.")
	inMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
	if (vConfirm)
	{
		val vTransfers = inChosen.count { it.transaction.kind == TransactionKind.TRANSFER || it.transaction.kind == TransactionKind.LOAN_PAYMENT }	// virements et mensualités choisis (deux comptes)
		AlertDialog(
			onDismissRequest = { vConfirm = false },
			title            = { Text("Supprimer ${inChosen.size} opération(s) ?") },
			text             = { Text(if (vTransfers > 0) "Dont $vTransfers virement(s) ou mensualité(s) : supprimés sur les deux comptes." else "Les soldes seront recalculés.") },
			confirmButton    = {
				TextButton(onClick = {
					vConfirm = false
					val vRefusal = inController.deleteMany(inChosen.map { it.transaction.id })	// refus éventuel
					inOnMessage(vRefusal)
					if (vRefusal == null) inOnClear()
				}) { Text("Supprimer") }
			},
			dismissButton    = { TextButton(onClick = { vConfirm = false }) { Text("Annuler") } }
		)
	}
}
