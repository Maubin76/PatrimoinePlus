package fr.maubin.patrimoineplus.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.maubin.patrimoineplus.domain.calc.AccountTotals
import fr.maubin.patrimoineplus.domain.calc.CategoryLine
import fr.maubin.patrimoineplus.domain.calc.SeriesPoint
import fr.maubin.patrimoineplus.domain.calc.MonthSummary
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.ui.accounts.AccountDeletion
import fr.maubin.patrimoineplus.ui.accounts.AccountForm
import fr.maubin.patrimoineplus.ui.accounts.AccountSheet
import fr.maubin.patrimoineplus.ui.accounts.AccountsViewModel
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.BalanceChart
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.ConfirmDialog
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.components.MonthSwitcher
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.entry.EntryForm
import fr.maubin.patrimoineplus.ui.entry.EntryMode
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import java.time.LocalDate
import kotlin.math.abs
import fr.maubin.patrimoineplus.ui.entry.EntryViewModel
import fr.maubin.patrimoineplus.ui.entry.TransactionSheet
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import fr.maubin.patrimoineplus.ui.shell.EntryFab
import fr.maubin.patrimoineplus.ui.shell.PrivacyAction
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.maubin.patrimoineplus.domain.calc.PropertyItem
import fr.maubin.patrimoineplus.ui.property.PurchaseForm
import fr.maubin.patrimoineplus.ui.property.SaleForm
import fr.maubin.patrimoineplus.ui.property.buildPurchase
import fr.maubin.patrimoineplus.ui.property.buildSale
import fr.maubin.patrimoineplus.ui.property.editPurchaseForm
import fr.maubin.patrimoineplus.ui.property.editSaleForm
import fr.maubin.patrimoineplus.ui.property.newPurchaseForm
import fr.maubin.patrimoineplus.ui.property.newSaleForm
import fr.maubin.patrimoineplus.ui.property.propertyNoun
import fr.maubin.patrimoineplus.ui.planning.LoanSection
import fr.maubin.patrimoineplus.domain.model.Frequency

private val kMonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)	// en-tête d'un mois
private val kShortDay   = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)	// date en tête du sous-titre
private val kDayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)	// en-tête d'un jour

/*
	Actions de la vue d'un compte, pour le contenu sans état.
	onBack        : retour à la liste
	onToggleHidden: bascule le mode discret
	onEdit        : ouvre la fiche du compte
	onMonth       : change de mois
	onOpen        : touche une opération
	onLongPress   : appui long sur une opération (sélection)
	onClearSelect : quitte la sélection
	onDeleteSel   : demande la suppression de la sélection
	onAdd         : nouvelle opération
	onAddStatement: saisie d'un relevé
	onAdjust      : opération d'ajustement d'un relevé
	onDelStatement: suppression d'un relevé
	onPrices      : mise à jour des cours
	onAddOpening  : ajout d'une position initiale
	onDelOpening  : retrait d'une position initiale
	onRefresh     : mise à jour des cours à la demande
	onAsset       : ouvre la fiche d'un actif
	onProperty    : ouvre l'achat d'un bien
	onSell        : ouvre la vente d'un bien
*/
data class AccountDetailActions(
	val onBack         : () -> Unit                    = {},
	val onToggleHidden : () -> Unit                    = {},
	val onEdit         : () -> Unit                    = {},
	val onMonth        : (java.time.YearMonth) -> Unit = {},
	val onOpen         : (OperationItem) -> Unit       = {},
	val onLongPress    : (OperationItem) -> Unit       = {},
	val onClearSelect  : () -> Unit                    = {},
	val onDeleteSel    : () -> Unit                    = {},
	val onAdd          : () -> Unit                    = {},
	val onAddStatement : () -> Unit                    = {},
	val onAdjust       : (StatementItem) -> Unit       = {},
	val onDelStatement : (StatementItem) -> Unit       = {},
	val onPrices       : () -> Unit                    = {},
	val onAddOpening   : () -> Unit                    = {},
	val onDelOpening   : (OpeningPositionItem) -> Unit = {},
	val onRefresh      : () -> Unit                    = {},
	val onAsset        : (String) -> Unit              = {},
	val onProperty     : (PropertyItem) -> Unit        = {},
	val onSell         : (PropertyItem) -> Unit        = {},
	val onLinkProperty : (String?) -> Unit             = {}
)

/*
	Vue d'un compte : mois par mois pour un compte Courant (synthèse, dépenses
	par catégorie, évolution du solde, opérations), globale pour un compte
	Épargne ou Bourse ; saisie, modification, suppression unitaire et
	multiple ; fiche du compte.
	inOnBack         : retour à la liste
	inOnToggleHidden : bascule le mode discret
	inViewModel      : ViewModel de la vue
	inEntry          : ViewModel de la saisie
	inAccounts       : ViewModel des comptes (fiche, suppression)
*/
@Composable
fun AccountDetailScreen(
	inOnBack         : () -> Unit,
	inOnToggleHidden : () -> Unit,
	inViewModel      : AccountDetailViewModel = hiltViewModel(),
	inEntry          : EntryViewModel         = hiltViewModel(),
	inAccounts       : AccountsViewModel      = hiltViewModel()
)
{
	val vState     by inViewModel.uiState.collectAsStateWithLifecycle()		// état du mois
	val vEntryData by inEntry.data.collectAsStateWithLifecycle()				// données de saisie
	val vItems     by inAccounts.uiState.collectAsStateWithLifecycle()		// comptes (fiche, suppression)
	var vSelection by remember { mutableStateOf(emptySet<String>()) }		// opérations sélectionnées
	var vEntry     by remember { mutableStateOf<EntryForm?>(null) }			// saisie ouverte
	var vSheet     by remember { mutableStateOf<AccountForm?>(null) }		// fiche du compte ouverte
	var vDeleting  by remember { mutableStateOf(false) }						// suppression du compte
	var vConfirm   by remember { mutableStateOf(false) }						// suppression de la sélection
	val vExtras    by inViewModel.extras.collectAsStateWithLifecycle()		// relevés ou portefeuille
	var vStatement by remember { mutableStateOf(false) }						// saisie d'un relevé
	var vPrices    by remember { mutableStateOf(false) }						// mise à jour des cours
	var vOpening   by remember { mutableStateOf(false) }						// ajout d'une position initiale
	var vAsset     by remember { mutableStateOf<String?>(null) }				// fiche d'actif ouverte
	var vPurchase  by remember { mutableStateOf<PurchaseForm?>(null) }		// achat d'un bien ouvert
	var vSale      by remember { mutableStateOf<SaleForm?>(null) }			// vente d'un bien ouverte
	val vRefresh   by inViewModel.refreshing.collectAsStateWithLifecycle()	// mise à jour des cours en cours
	val vQuotesAt  by inViewModel.quotesUpdatedAt.collectAsStateWithLifecycle()	// dernière mise à jour des cours
	val vSnackbar  = remember { SnackbarHostState() }						// messages éphémères
	val vScope     = rememberCoroutineScope()									// portée des messages
	val vHaptics   = LocalHapticFeedback.current								// retour haptique
	val vItem      = vItems.active.plus(vItems.archived).firstOrNull { it.account.id == inViewModel.accountId }	// compte avec ses opérations

	LaunchedEffect(vItems.loaded, vItem) {
		if (vItems.loaded && vItem == null)
		{
			inOnBack()
		}
	}
	BackHandler(enabled = vSelection.isNotEmpty()) { vSelection = emptySet() }
	val vNotify = { inMessage: String -> vScope.launch { vSnackbar.showSnackbar(inMessage) }; Unit }	// message éphémère
	LaunchedEffect(vExtras.portfolio != null) {
		if (vExtras.portfolio != null) inViewModel.refreshQuotes(inForce = false, inOnMessage = vNotify)
	}

	AccountDetailContent(
		inState     = vState,
		inExtras    = vExtras,
		inSelection = vSelection,
		inSnackbar  = vSnackbar,
		inRefreshing = vRefresh,
		inQuotesAt  = vQuotesAt,
		inActions   = AccountDetailActions(
			onBack         = inOnBack,
			onToggleHidden = inOnToggleHidden,
			onEdit         = { vState.account?.let { inAccount -> inAccounts.editForm(inAccount) { vSheet = it } } },
			onMonth        = inViewModel::showMonth,
			onOpen         = { inOp ->
				when
				{
					vSelection.isNotEmpty() && inOp.editable -> vSelection = vSelection.toggle(inOp.transaction.id)
					vSelection.isNotEmpty() -> Unit
					inOp.transaction.kind == TransactionKind.ACQUISITION -> inEntry.propertyOf(inOp.transaction)?.let { vPurchase = editPurchaseForm(it) }
					inOp.transaction.kind == TransactionKind.DISPOSAL    -> inEntry.propertyOf(inOp.transaction)?.let { vSale = editSaleForm(it) }
					inOp.editable           -> vEntry = inEntry.editForm(inOp.transaction)
					else                    -> vScope.launch {
						vSnackbar.showSnackbar(
							"Le solde initial se modifie dans la fiche du compte (crayon) ; les positions initiales, dans la carte des positions."
						)
					}
				}
			},
			onLongPress    = { inOp ->
				if (inOp.editable)
				{
					vHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
					vSelection = vSelection.toggle(inOp.transaction.id)
				}
			},
			onClearSelect  = { vSelection = emptySet() },
			onDeleteSel    = { vConfirm = true },
			onAdd          = {
				if (vState.account?.type?.isProperty == true) vPurchase = newPurchaseForm(inViewModel.accountId, inViewModel.today())
				else vEntry = inEntry.newForm(inViewModel.accountId)
			},
			onAddStatement = { vStatement = true },
			onAdjust       = { inItem ->
				val vIncome = inItem.gapCents > 0											// écart en faveur du compte
				vEntry = inEntry.newForm(inViewModel.accountId).copy(
					mode       = if (vIncome) EntryMode.INCOME else EntryMode.EXPENSE,
					amount     = editableEuros(abs(inItem.gapCents)),
					categoryId = if (vIncome) vEntryData.categories.firstOrNull { it.kind == CategoryKind.INCOME && it.name.startsWith("Intérêt") }?.id else null,
					label      = if (vIncome) "Intérêts" else "Ajustement sur relevé",
					date       = inItem.statement.date
				)
			},
			onDelStatement = { inViewModel.deleteStatement(it.statement.id) },
			onPrices       = { vPrices = true },
			onAddOpening   = { vOpening = true },
			onDelOpening   = { inViewModel.removeOpeningPosition(it.trade.id) },
			onRefresh      = { inViewModel.refreshQuotes(inForce = true, inOnMessage = vNotify) },
			onAsset        = { vAsset = it },
			onProperty     = { vPurchase = editPurchaseForm(it) },
			onSell         = { inItem -> vSale = if (inItem.disposal != null) editSaleForm(inItem) else newSaleForm(inItem, inViewModel.today()) },
			onLinkProperty = inViewModel::linkProperty
		)
	)

	vPurchase?.let { inForm ->
		val vSold = vExtras.properties.firstOrNull { it.acquisition.id == inForm.id }?.disposal	// vente du bien
		PurchaseSheet(
			inStart     = inForm,
			inAccounts  = inEntry.allAccounts(),
			inSold      = vSold != null,
			inOnSave    = { inSaved, inOnRefus -> inEntry.saveProperty(buildPurchase(inSaved)) { inMessage -> if (inMessage == null) vPurchase = null else inOnRefus(inMessage) } },
			inOnDelete  = {
				inEntry.delete(listOfNotNull(inForm.id, vSold?.id), vNotify)
				vPurchase = null
			},
			inOnDismiss = { vPurchase = null }
		)
	}

	vSale?.let { inForm ->
		vExtras.properties.firstOrNull { it.acquisition.id == inForm.purchaseId }?.let { inItem ->
			SaleSheet(
				inStart        = inForm,
				inItem         = inItem,
				inAccounts     = inEntry.allAccounts(),
				inOnSave       = { inSaved, inOnRefus -> inEntry.saveProperty(buildSale(inSaved, inItem)) { inMessage -> if (inMessage == null) vSale = null else inOnRefus(inMessage) } },
				inOnCancelSale = {
					inForm.id?.let { inEntry.delete(listOf(it), vNotify) }
					vSale = null
				},
				inOnDismiss    = { vSale = null }
			)
		}
	}

	vEntry?.let { inForm ->
		var vRepeat by remember(inForm) { mutableStateOf<Frequency?>(null) }		// répétition demandée
		TransactionSheet(
			inInitial     = inForm,
			inData        = vEntryData.let { inEntry.dataFor(inForm) },	// relu à chaque changement des données
			inSuggestions = inEntry::suggestions,
			inOnSave      = { inSaved, inOnErrors -> inEntry.save(inSaved, { if (it.isEmpty) vEntry = null else inOnErrors(it) }, vRepeat) },
			inOnRepeat    = { vRepeat = it },
			inOnDelete    = {
				inForm.id?.let { inEntry.delete(listOf(it), vNotify) }
				vEntry = null
			},
			inOnDismiss   = { vEntry = null }
		)
	}

	if (vStatement)
	{
		StatementSheet(inViewModel.today(), { inDate, inCents -> inViewModel.addStatement(inDate, inCents); vStatement = false }, { vStatement = false })
	}
	if (vPrices)
	{
		vExtras.portfolio?.let { inPortfolio ->
			PricesSheet(inPortfolio.valuation.positions, inPortfolio.names, inViewModel.today(), { inDate, inMap -> inViewModel.updatePrices(inDate, inMap); vPrices = false }, { vPrices = false })
		}
	}
	if (vOpening)
	{
		OpeningPositionSheet(
			inAssets    = vEntryData.assets.filterNot { it.archived },
			inOnSave    = { inAsset, inName, inQty, inPru ->
				inViewModel.addOpeningPosition(inAsset, inName, inQty, inPru, inViewModel.today())
				vOpening = false
			},
			inOnDismiss = { vOpening = false }
		)
	}

	vAsset?.let { inId -> vExtras.portfolio?.assets?.get(inId) }?.let { inAsset ->
		AssetSheet(
			inAsset     = inAsset,
			inOnLookup  = inViewModel::lookupListing,
			inOnSave    = { inName, inIsin, inTicker ->
				inViewModel.saveAsset(inAsset.id, inName, inIsin, inTicker, vNotify)
				vAsset = null
			},
			inOnDismiss = { vAsset = null }
		)
	}

	vSheet?.let { inForm ->
		AccountSheet(
			inInitial    = inForm,
			inEditing    = vItem?.account,
			inTypeLocked = (vItem?.operations ?: 0) > 0,
			inOnSave     = { inSaved, inOnErrors -> inAccounts.save(inSaved, vItem?.account) { if (it.isEmpty) vSheet = null else inOnErrors(it) } },
			inOnArchive  = {
				vItem?.let { inAccounts.setArchived(it.account, !it.account.archived) }
				vSheet = null
			},
			inOnDelete   = {
				vSheet    = null
				vDeleting = true
			},
			inOnDismiss  = { vSheet = null }
		)
	}

	if (vDeleting && vItem != null)
	{
		AccountDeletion(
			inItem      = vItem,
			inOnArchive = { inAccounts.setArchived(vItem.account, true) },
			inOnDelete  = { inPermanent -> inAccounts.delete(vItem.account, inPermanent) },
			inOnClose   = { vDeleting = false }
		)
	}

	if (vConfirm)
	{
		val vPicked = vState.allItems.filter { it.transaction.id in vSelection }	// opérations choisies
		val vLinked = vPicked.count { it.transaction.kind == TransactionKind.TRANSFER || it.transaction.kind == TransactionKind.LOAN_PAYMENT }	// virements et mensualités (deux comptes)
		ConfirmDialog(
			inTitle     = "Supprimer ${vPicked.size} opération(s) ?",
			inText      = if (vLinked > 0) "Dont $vLinked virement(s) ou mensualité(s) : supprimés sur les deux comptes, ce qui modifiera aussi l’autre compte." else "Les soldes seront recalculés.",
			inAction    = "Supprimer",
			inOnConfirm = {
				inEntry.delete(vSelection.toList(), vNotify)
				vSelection = emptySet()
				vConfirm   = false
			},
			inOnDismiss = { vConfirm = false }
		)
	}
}

/*
	Ajoute ou retire un identifiant d'un ensemble.
	inId : identifiant
*/
private fun Set<String>.toggle(inId: String): Set<String> = if (inId in this) this - inId else this + inId

/*
	Contenu de la vue d'un compte, sans état propre (prévisualisable).
	inState     : état du mois
	inExtras    : relevés ou portefeuille
	inSelection : opérations sélectionnées
	inSnackbar  : hôte des messages éphémères
	inActions   : actions
	inRefreshing : vrai pendant une mise à jour des cours
	inQuotesAt  : instant de la dernière mise à jour des cours (ms) ; null avant la première
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailContent(
	inState      : AccountDetailState,
	inExtras     : AccountExtras,
	inSelection  : Set<String>,
	inSnackbar   : SnackbarHostState,
	inActions    : AccountDetailActions,
	inRefreshing : Boolean = false,
	inQuotesAt   : Long?   = null
)
{
	val vColors   = MaterialTheme.colorScheme									// palette courante
	val vSelecting = inSelection.isNotEmpty()									// mode sélection
	Scaffold(
		containerColor       = vColors.surface,
		snackbarHost         = { SnackbarHost(inSnackbar) },
		topBar               = {
			TopAppBar(
				navigationIcon = {
					if (vSelecting)
					{
						IconButton(onClick = inActions.onClearSelect) { Icon(LineIcons.Close, contentDescription = "Quitter la sélection") }
					}
					else
					{
						IconButton(onClick = inActions.onBack) { Icon(LineIcons.Back, contentDescription = "Retour aux comptes") }
					}
				},
				title          = {
					Text(
						text     = if (vSelecting) "${inSelection.size} sélectionnée(s)" else inState.account?.name.orEmpty(),
						style    = MaterialTheme.typography.titleMedium,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				},
				actions        = {
					if (vSelecting)
					{
						IconButton(onClick = inActions.onDeleteSel) { Icon(LineIcons.Trash, contentDescription = "Supprimer la sélection") }
					}
					else
					{
						PrivacyAction(inActions.onToggleHidden)
						IconButton(onClick = inActions.onEdit) { Icon(LineIcons.Edit, contentDescription = "Modifier le compte") }
					}
				},
				colors         = TopAppBarDefaults.topAppBarColors(containerColor = vColors.surface, scrolledContainerColor = vColors.surface)
			)
		},
		floatingActionButton = {
			AnimatedVisibility(visible = !vSelecting, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f)) {
				EntryFab(inActions.onAdd)
			}
		}
	) { inPadding ->
		RefreshableBox(
			inEnabled    = inExtras.portfolio != null,
			inRefreshing = inRefreshing,
			inOnRefresh  = inActions.onRefresh,
			inModifier   = Modifier.padding(top = inPadding.calculateTopPadding())
		) {
			LazyColumn(
				contentPadding = PaddingValues(
					start  = kScreenMargin,
					end    = kScreenMargin,
					top    = 0.dp,
					bottom = inPadding.calculateBottomPadding() + 96.dp
				)
			) {
				if (inState.isGlobal)
				{
					globalContent(inState, inExtras, inSelection, inActions, inQuotesAt)
					return@LazyColumn
				}
				item { MonthSwitcher(inState.month, inState.firstMonth, inState.lastMonth, inActions.onMonth) }
				item { Spacer(Modifier.height(8.dp)) }
				inState.summary?.let { inSummary -> item { SummaryCard(inSummary, inState.account?.type) } }
				item { Spacer(Modifier.height(28.dp)) }
				if (inState.account?.type == AccountType.CHECKING || inState.expenses.isNotEmpty())
				{
					item { SectionHeader(inTitle = "Dépenses par catégorie") }
					item { Spacer(Modifier.height(8.dp)) }
					item { ExpensesCard(inState.expenses) }
				}
				if (inState.series.size > 1)
				{
					item { Spacer(Modifier.height(28.dp)) }
					item { SectionHeader(inTitle = "Évolution du solde") }
					item { Spacer(Modifier.height(4.dp)) }
					item { BalanceChart(inState.series, inState.month) }
				}
				if (inState.account?.let { it.type != AccountType.BROKERAGE } == true)
				{
					item { Spacer(Modifier.height(28.dp)) }
					item { StatementsSection(inExtras.statements, inActions.onAddStatement, inActions.onAdjust, inActions.onDelStatement) }
				}
				item { Spacer(Modifier.height(28.dp)) }
				item { SectionHeader(inTitle = "Opérations") }
				if (inState.upcoming.isEmpty() && inState.days.isEmpty())
				{
					item {
						Text(
							text     = "Aucune opération ce mois-ci.",
							style    = MaterialTheme.typography.bodyMedium,
							color    = vColors.onSurfaceVariant,
							modifier = Modifier.padding(vertical = 8.dp)
						)
					}
				}
				if (inState.upcoming.isNotEmpty())
				{
					item { DayHeader("À venir") }
					item { OperationsCard(inState.upcoming, inSelection, inActions) }
				}
				inState.days.forEach { inDay ->
					item(key = inDay.date.toString()) { DayHeader(inDay.date.format(kDayFormat).replaceFirstChar { it.uppercase() }) }
					item(key = "ops-${inDay.date}") { OperationsCard(inDay.items, inSelection, inActions) }
				}
			}
		}
	}
}

/*
	Zone qu'on peut tirer vers le bas pour rafraîchir, si inEnabled ; sinon le
	contenu seul.
	inEnabled    : geste actif
	inRefreshing : rafraîchissement en cours
	inOnRefresh  : lance le rafraîchissement
	inModifier   : modificateur appliqué
	inContent    : contenu
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableBox(
	inEnabled    : Boolean,
	inRefreshing : Boolean,
	inOnRefresh  : () -> Unit,
	inModifier   : Modifier,
	inContent    : @Composable () -> Unit
)
{
	if (inEnabled)
	{
		PullToRefreshBox(isRefreshing = inRefreshing, onRefresh = inOnRefresh, modifier = inModifier) { inContent() }
	}
	else
	{
		Box(inModifier) { inContent() }
	}
}

/*
	Vue globale d'un compte Épargne ou Bourse, depuis son ouverture.
	Épargne : solde et totaux, évolution du solde, relevés, opérations.
	Bourse : valeur et plus-values, évolution de la valeur, positions, opérations.
	inState     : état de la vue
	inExtras    : relevés ou portefeuille
	inSelection : opérations sélectionnées
	inActions   : actions
	inQuotesAt  : instant de la dernière mise à jour des cours (ms)
*/
private fun LazyListScope.globalContent(
	inState     : AccountDetailState,
	inExtras    : AccountExtras,
	inSelection : Set<String>,
	inActions   : AccountDetailActions,
	inQuotesAt  : Long?
)
{
	val vPortfolio = inExtras.portfolio											// portefeuille (Bourse)
	val vAccount   = inState.account												// compte affiché
	if (vAccount != null && vAccount.type == AccountType.LOAN)
	{
		item { LoanSection(inExtras.loan, inExtras.schedule, -(inState.totals?.balanceCents ?: 0L), inExtras.asOf ?: inState.month.atEndOfMonth(), inExtras, inActions.onLinkProperty) }
		chartSection("Évolution du capital restant dû", inState.series.map { it.copy(valueCents = -it.valueCents) })
	}
	else if (vAccount != null && vAccount.type.isProperty)
	{
		item { PropertyHeader(inExtras.properties, inExtras.asOf ?: inState.month.atEndOfMonth(), vAccount.sharePercent, inExtras.nets) }
		chartSection("Évolution de la valeur", inState.series)
		item { Spacer(Modifier.height(28.dp)) }
		item { PropertySection(inExtras.properties, inExtras.asOf ?: inState.month.atEndOfMonth(), propertyNoun(vAccount.type), inExtras.accountNames, inActions.onAdd, inActions.onProperty, inActions.onSell, inExtras.nets) }
	}
	else if (vPortfolio != null)
	{
		item { PortfolioHeader(vPortfolio) }
		chartSection("Évolution de la valeur", inExtras.valueSeries)
		item { Spacer(Modifier.height(28.dp)) }
		item { PositionsSection(vPortfolio, inExtras.openingPositions, inActions.onPrices, inActions.onAddOpening, inActions.onDelOpening, inActions.onRefresh, inActions.onAsset, inQuotesAt, "titres:${vAccount?.id}") }
	}
	else
	{
		inState.totals?.let { inTotals -> item { SavingsHeader(inTotals) } }
		chartSection("Évolution du solde", inState.series)
		item { Spacer(Modifier.height(28.dp)) }
		item { StatementsSection(inExtras.statements, inActions.onAddStatement, inActions.onAdjust, inActions.onDelStatement) }
	}
	item { Spacer(Modifier.height(28.dp)) }
	item { SectionHeader(inTitle = "Opérations") }
	if (inState.upcoming.isEmpty() && inState.months.isEmpty())
	{
		item {
			Text(
				text     = "Aucune opération.",
				style    = MaterialTheme.typography.bodyMedium,
				color    = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(vertical = 8.dp)
			)
		}
	}
	if (inState.upcoming.isNotEmpty())
	{
		item { DayHeader("À venir") }
		item { OperationsCard(inState.upcoming, inSelection, inActions) }
	}
	inState.months.forEach { inGroup ->
		item(key = "m-${inGroup.month}") { DayHeader(inGroup.month.format(kMonthTitle).replaceFirstChar { it.uppercase() }) }
		item(key = "mops-${inGroup.month}") { OperationsCard(inGroup.items, inSelection, inActions, inWithDate = true) }
	}
}

/*
	Section de courbe : titre et courbe, mois le plus récent mis en avant ;
	rien tant que la série a moins de deux points.
	inTitle  : titre de la section
	inSeries : valeurs de fin de mois
*/
private fun LazyListScope.chartSection(
	inTitle  : String,
	inSeries : List<SeriesPoint>
)
{
	if (inSeries.size < 2)
	{
		return
	}
	item { Spacer(Modifier.height(28.dp)) }
	item { SectionHeader(inTitle = inTitle) }
	item { Spacer(Modifier.height(4.dp)) }
	item { BalanceChart(inSeries, inSeries.last().month) }
}

/*
	En-tête d'un compte Épargne : solde du jour en grand, puis ce qui l'a
	constitué depuis l'ouverture (lignes nulles masquées).
	inTotals : mouvements depuis l'ouverture
*/
@Composable
private fun SavingsHeader(inTotals: AccountTotals)
{
	val vColors = MaterialTheme.colorScheme									// palette courante
	Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
		AmountText(inCents = inTotals.balanceCents, inStyle = MaterialTheme.typography.displaySmall)
		Spacer(Modifier.height(8.dp))
		Text("Solde au jour", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
	}
	Spacer(Modifier.height(12.dp))
	BorderedCard {
		Column(Modifier.padding(vertical = 8.dp)) {
			SummaryRow("Solde initial", inTotals.opening, inMuted = true)
			SummaryRow("Versements nets", inTotals.transfers, inSigned = true)
			SummaryRow("Intérêts et revenus", inTotals.income, inSigned = true)
			if (inTotals.expenses != 0L) SummaryRow("Frais et retraits", inTotals.expenses, inSigned = true)
			if (inTotals.trades != 0L) SummaryRow("Ordres de bourse", inTotals.trades, inSigned = true)
		}
	}
}

/*
	Synthèse du mois : ouverture, mouvements ventilés, clôture.
	inSummary : synthèse
	inType    : type du compte (libellés adaptés)
*/
@Composable
private fun SummaryCard(
	inSummary : MonthSummary,
	inType    : AccountType?
)
{
	val vSavingsLabel = if (inType == AccountType.CHECKING) "Épargne" else "Versements nets"	// libellé de l'épargne
	BorderedCard {
		Column(Modifier.padding(vertical = 8.dp)) {
			SummaryRow("Ouverture", inSummary.opening, inMuted = true)
			if (inSummary.openings != 0L) SummaryRow("Solde initial", inSummary.openings, inSigned = true)
			SummaryRow("Entrées", inSummary.income, inSigned = true)
			SummaryRow("Dépenses", inSummary.expenses, inSigned = true)
			if (inSummary.savings != 0L) SummaryRow(vSavingsLabel, inSummary.savings, inSigned = true)
			if (inSummary.transfers != 0L) SummaryRow("Virements internes", inSummary.transfers, inSigned = true)
			if (inSummary.trades != 0L) SummaryRow("Ordres de bourse", inSummary.trades, inSigned = true)
			HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
			SummaryRow("Clôture", inSummary.closing, inStrong = true)
		}
	}
}

/*
	Ligne de la synthèse.
	inLabel  : libellé
	inCents  : montant
	inSigned : affiche le signe « + » des montants positifs
	inMuted  : libellé et montant en gris
	inStrong : ligne mise en avant (clôture)
*/
@Composable
private fun SummaryRow(
	inLabel  : String,
	inCents  : Long,
	inSigned : Boolean = false,
	inMuted  : Boolean = false,
	inStrong : Boolean = false
)
{
	val vColors = MaterialTheme.colorScheme									// palette courante
	val vStyle  = if (inStrong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge	// style de la ligne
	val vColor  = when																// couleur de la ligne
	{
		inStrong && inCents < 0 -> vColors.error
		inMuted                 -> vColors.onSurfaceVariant
		else                    -> vColors.onSurface
	}
	Row(
		modifier          = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 20.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(inLabel, style = vStyle, color = if (inMuted) vColors.onSurfaceVariant else vColors.onSurface, modifier = Modifier.weight(1f))
		AmountText(inCents = inCents, inStyle = vStyle, inColor = vColor, inShowPlusSign = inSigned)
	}
}

/*
	Dépenses du mois par catégorie : icône, nom, barre (rapportée à la plus
	grosse dépense, de même longueur pour toutes), montant et part.
	inLines : dépenses nettes par catégorie, triées
*/
@Composable
private fun ExpensesCard(inLines: List<CategoryLine>)
{
	val vColors = MaterialTheme.colorScheme									// palette courante
	BorderedCard {
		if (inLines.isEmpty())
		{
			EmptyState(inTitle = "Aucune dépense", inBody = "Aucune dépense n’est enregistrée sur ce compte pour ce mois.")
			return@BorderedCard
		}
		val vMax = inLines.maxOf { it.netCents }.coerceAtLeast(1L)				// plus grosse dépense (échelle des barres)
		Column(Modifier.padding(vertical = 8.dp)) {
			inLines.forEach { inLine ->
				Row(
					modifier          = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(CategoryIcons.of(inLine.category.icon), contentDescription = null, modifier = Modifier.size(22.dp))
					Spacer(Modifier.width(14.dp))
					Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
						Text(inLine.category.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
						Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(vColors.surfaceContainerHigh)) {
							Box(
								Modifier
									.fillMaxWidth((inLine.netCents.coerceAtLeast(0L).toFloat() / vMax).coerceIn(0f, 1f))
									.height(4.dp)
									.clip(CircleShape)
									.background(vColors.onSurface)
							)
						}
					}
					Spacer(Modifier.width(16.dp))
					Column(Modifier.widthIn(min = 104.dp), horizontalAlignment = Alignment.End) {
						AmountText(inCents = inLine.netCents, inStyle = MaterialTheme.typography.bodyLarge)
						Text("${inLine.percent}\u202F%", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
					}
				}
			}
		}
	}
}

/*
	En-tête d'un groupe d'opérations (jour, « À venir »).
	inText : texte
*/
@Composable
private fun DayHeader(inText: String)
{
	Text(
		text     = inText,
		style    = MaterialTheme.typography.labelLarge,
		color    = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
	)
}

/*
	Carte des opérations d'un groupe.
	inItems     : opérations
	inSelection : opérations sélectionnées
	inActions   : actions
	inWithDate  : affiche la date de chaque opération (vue globale)
*/
@Composable
private fun OperationsCard(
	inItems     : List<OperationItem>,
	inSelection : Set<String>,
	inActions   : AccountDetailActions,
	inWithDate  : Boolean = false
)
{
	BorderedCard {
		inItems.forEachIndexed { inIndex, inItem ->
			if (inIndex > 0)
			{
				HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
			}
			OperationRow(inItem, inItem.transaction.id in inSelection, inSelection.isNotEmpty(), inActions, inWithDate)
		}
	}
}

/*
	Ligne d'une opération : icône (ou coche en sélection), libellé, catégorie
	ou nature, montant signé à l'encre. Toucher ouvre ; appui long sélectionne.
	inItem      : opération
	inSelected  : opération sélectionnée
	inSelecting : mode sélection actif
	inActions   : actions
	inWithDate  : affiche la date même hors « À venir »
*/
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OperationRow(
	inItem      : OperationItem,
	inSelected  : Boolean,
	inSelecting : Boolean,
	inActions   : AccountDetailActions,
	inWithDate  : Boolean
)
{
	val vColors = MaterialTheme.colorScheme									// palette courante
	Row(
		modifier          = Modifier
			.fillMaxWidth()
			.background(if (inSelected) vColors.secondaryContainer else vColors.surface)
			.combinedClickable(
				onClickLabel     = if (inSelecting) "Sélectionner" else "Ouvrir",
				onLongClickLabel = "Sélectionner",
				onClick          = { inActions.onOpen(inItem) },
				onLongClick      = { inActions.onLongPress(inItem) }
			)
			.semantics { selected = inSelected }
			.heightIn(min = 64.dp)
			.padding(horizontal = 20.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Box(
			modifier         = Modifier
				.size(36.dp)
				.clip(CircleShape)
				.background(if (inSelected) vColors.onSurface else vColors.surfaceContainer),
			contentAlignment = Alignment.Center
		) {
			Icon(
				imageVector        = if (inSelected) LineIcons.Check else CategoryIcons.of(inItem.icon),
				contentDescription = null,
				tint               = if (inSelected) vColors.surface else vColors.onSurface,
				modifier           = Modifier.size(20.dp)
			)
		}
		Spacer(Modifier.width(14.dp))
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(inItem.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(
				text     = if (inItem.upcoming || inWithDate) "${inItem.transaction.date.format(kShortDay)} · ${inItem.subtitle}" else inItem.subtitle,
				style    = MaterialTheme.typography.bodyMedium,
				color    = vColors.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		Spacer(Modifier.width(12.dp))
		AmountText(inCents = inItem.amount, inStyle = MaterialTheme.typography.titleMedium, inShowPlusSign = true)
	}
}

