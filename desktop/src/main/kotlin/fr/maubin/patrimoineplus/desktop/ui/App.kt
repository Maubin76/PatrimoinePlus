package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.PendingAction
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.LoadStatus
import fr.maubin.patrimoineplus.desktop.data.Source
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.panels.AccountPanel
import fr.maubin.patrimoineplus.desktop.ui.panels.AssetPanel
import fr.maubin.patrimoineplus.desktop.ui.panels.CategoryPanel
import fr.maubin.patrimoineplus.desktop.ui.panels.OpeningPositionPanel
import fr.maubin.patrimoineplus.desktop.ui.panels.OperationPanel
import fr.maubin.patrimoineplus.desktop.ui.panels.PricesPanel
import fr.maubin.patrimoineplus.desktop.ui.panels.StatementPanel
import fr.maubin.patrimoineplus.desktop.ui.screens.AccountScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.CategoriesScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.DashboardScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.ReconcileScreen
import androidx.compose.runtime.staticCompositionLocalOf
import fr.maubin.patrimoineplus.desktop.ui.screens.SettingsScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.WelcomeScreen
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.PatrimoineLogo
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme
import fr.maubin.patrimoineplus.ui.theme.isDark
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val kTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.FRANCE)	// heure de lecture du fichier
// Choix d'un relevé exporté par la banque (vue d'un compte) ; fourni par App.
val LocalBankPicker = staticCompositionLocalOf<() -> Path?> { { null } }

val kContentMaxWidth    = 1600.dp													// largeur maximale du contenu
private const val kPageMillis  = 280													// transition entre écrans
private const val kPanelMillis = 240													// arrivée du panneau de saisie
private val kEase       = CubicBezierEasing(0.2f, 0f, 0f, 1f)						// courbe « accentuée » de Material

/*
	Racine de l'appli PC : thème selon l'apparence choisie, mode discret, puis
	écran d'accueil (aucune source) ou appli complète.
	inController     : contrôleur
	inOnPickSaveFile : choix d'un fichier où enregistrer une copie ; null si annulé
	inOnPickBankFile : choix d'un relevé exporté par la banque ; null si annulé
	inOnPickFolder   : ouvre le sélecteur de dossier ; rend le dossier choisi ou null
*/
@Composable
fun App(
	inController     : DesktopController,
	inOnPickSaveFile : () -> Path? = { null },
	inOnPickBankFile : () -> Path? = { null },
	inOnPickFolder   : () -> Path?
)
{
	val vSettings by inController.settings.collectAsState()					// réglages
	val vSpace    by inController.workspace.collectAsState()				// données
	val vPending  by inController.pending.collectAsState()					// action retenue
	PatrimoineTheme(inDarkTheme = vSettings.appearance.isDark(), inTrendColors = vSettings.trend) {
		CompositionLocalProvider(LocalAmountsHidden provides vSettings.hidden, LocalBankPicker provides inOnPickBankFile) {
			Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
				if (vSpace.source == Source.None)
				{
					WelcomeScreen(
						inOnFolder = { inOnPickFolder()?.let(inController::openFolder) },
						inOnDemo   = inController::openDemo
					)
				}
				else
				{
					Shell(inController, vSpace, inOnPickFolder)
				}
				vPending?.let { inAction -> UnsavedDialog(inAction, inController, inOnPickSaveFile) }
			}
		}
	}
}

/*
	Appli complète : barre latérale de navigation à gauche, bandeau d'état en
	haut, écran courant au centre (largeur limitée sur très grand écran),
	panneau de saisie à droite.
	inController   : contrôleur
	inSpace        : données et état de la lecture
	inOnPickFolder : sélecteur de dossier
*/
@Composable
private fun Shell(
	inController   : DesktopController,
	inSpace        : WorkspaceState,
	inOnPickFolder : () -> Path?
)
{
	val vNav    by inController.navigation.collectAsState()					// position
	val vPanel  by inController.panel.collectAsState()						// panneau de saisie ouvert
	val vNotice by inController.notice.collectAsState()						// message ponctuel
	val vBank   by inController.bank.collectAsState()							// relevé en cours de rapprochement
	val vFocus  = remember { FocusRequester() }									// focus clavier de secours (raccourcis)
	LaunchedEffect(Unit) { vFocus.requestFocus() }
	LaunchedEffect(inSpace.data) { inController.dropStalePanel() }
	Row(
		Modifier.fillMaxSize().focusRequester(vFocus).focusable().onPreviewKeyEvent { inEvent ->
			if (inEvent.type == KeyEventType.KeyDown && inEvent.isCtrlPressed && inEvent.key == Key.N)
			{
				inController.newOperation()
				true
			}
			else false
		}
	) {
		Rail(vNav.screen, inController::show)
		VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
		Column(Modifier.weight(1f).fillMaxHeight()) {
			StatusBar(inSpace, vNotice, inController)
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
				Box(Modifier.widthIn(max = kContentMaxWidth).fillMaxSize()) {
					AnimatedContent(
						targetState    = vNav.screen to (vNav.accountId?.let { if (vBank?.accountId == it) "$it#relevé" else it }),
						transitionSpec = { pageTransition(initialState.second, targetState.second) },
						label          = "écran"
					) { (inScreen, inAccountId) ->
						when
						{
							inAccountId?.endsWith("#relevé") == true -> vBank?.let { ReconcileScreen(inSpace, inController, it) }
							inAccountId != null              -> AccountScreen(inSpace, inController, inAccountId, vNav.month)
							inScreen == Screen.DASHBOARD     -> DashboardScreen(inSpace, inController)
							inScreen == Screen.CATEGORIES    -> CategoriesScreen(inSpace, inController)
							else                             -> SettingsScreen(inController, inOnPickFolder)
						}
					}
				}
			}
		}
		AnimatedVisibility(
			visible = vPanel != null,
			enter   = slideInHorizontally(tween(kPanelMillis, easing = kEase)) { it / 3 } + fadeIn(tween(kPanelMillis)),
			exit    = slideOutHorizontally(tween(kPanelMillis * 2 / 3, easing = kEase)) { it / 3 } + fadeOut(tween(kPanelMillis * 2 / 3))
		) {
			val vShown = remember { mutableStateOf(vPanel) }							// panneau affiché (gardé pendant sa sortie)
			vPanel?.let { vShown.value = it }
			vShown.value?.let { inOpen ->
				AnimatedContent(targetState = inOpen, transitionSpec = { fadeIn(tween(kPanelMillis)) togetherWith fadeOut(tween(kPanelMillis / 2)) }, contentKey = { it.token }, label = "panneau") { inPanel ->
					when (inPanel)
					{
						is Panel.Operation       -> OperationPanel(inController, inPanel.form)
						is Panel.AccountSheet    -> AccountPanel(inController, inPanel.accountId)
						is Panel.CategorySheet   -> CategoryPanel(inController, inPanel.categoryId, inPanel.kind)
						is Panel.Statement       -> StatementPanel(inController, inPanel.accountId)
						is Panel.Prices          -> PricesPanel(inController, inPanel.accountId)
						is Panel.OpeningPosition -> OpeningPositionPanel(inController, inPanel.accountId)
						is Panel.AssetSheet      -> AssetPanel(inController, inPanel.assetId)
					}
				}
			}
		}
	}
	(inSpace.status as? LoadStatus.Conflict)?.let { inConflict ->
		AlertDialog(
			onDismissRequest = {},
			title            = { Text("Quelle version garder ?") },
			text             = {
				Text(
					"Les données ont changé à la fois sur ce PC et dans le fichier synchronisé (téléphone).\n\n" +
					"Ce PC : ${inConflict.localSummary}.\n\nFichier : ${inConflict.fileSummary}.\n\n" +
					"La version écartée est remplacée. Le téléphone garde ses sauvegardes quotidiennes dans le même dossier."
				)
			},
			confirmButton    = { TextButton(onClick = { inController.resolveConflict(inKeepLocal = true) }) { Text("Ce PC") } },
			dismissButton    = { TextButton(onClick = { inController.resolveConflict(inKeepLocal = false) }) { Text("Le fichier") } }
		)
	}
}

/*
	Transition entre deux écrans : vers la vue d'un compte, la page arrive de
	la droite ; au retour, de la gauche ; entre écrans principaux, simple
	fondu. Courte (moins d'un tiers de seconde) pour ne jamais faire attendre.
	inFrom : compte affiché avant ; null sur un écran principal
	inTo   : compte affiché après ; null sur un écran principal
*/
private fun pageTransition(
	inFrom : String?,
	inTo   : String?
): ContentTransform
{
	val vShift = when
	{
		inFrom == null && inTo != null -> 1										// ouverture d'un compte
		inFrom != null && inTo == null -> -1									// retour au tableau de bord
		else                           -> 0
	}																			// sens du glissement
	val vIn  = fadeIn(tween(kPageMillis, delayMillis = kPageMillis / 4, easing = kEase)) +
		slideInHorizontally(tween(kPageMillis, easing = kEase)) { inWidth -> vShift * inWidth / 12 }	// arrivée
	val vOut = fadeOut(tween(kPageMillis / 2)) +
		slideOutHorizontally(tween(kPageMillis, easing = kEase)) { inWidth -> -vShift * inWidth / 24 }	// départ
	return vIn togetherWith vOut
}

/*
	Fenêtre des modifications non écrites : avant de fermer l'appli ou de
	changer de source, l'utilisateur peut enregistrer une copie de ses
	données, continuer quand même, ou annuler.
	inAction         : action retenue
	inController     : contrôleur
	inOnPickSaveFile : choix du fichier de la copie
*/
@Composable
private fun UnsavedDialog(
	inAction         : PendingAction,
	inController     : DesktopController,
	inOnPickSaveFile : () -> Path?
)
{
	var vMessage by remember { mutableStateOf<String?>(null) }						// résultat de la copie
	AlertDialog(
		onDismissRequest = inController::cancelPending,
		title            = { Text("Modifications non enregistrées") },
		text             = {
			Text(
				"Des modifications faites sur ce PC n’ont pas pu être écrites dans le fichier synchronisé. ${inAction.reason}\n\n" +
				"« ${inAction.label} » les ferait perdre. Enregistrez d’abord une copie (classeur Excel) pour les garder." +
				(vMessage?.let { "\n\n$it" } ?: "")
			)
		},
		confirmButton    = {
			Row {
				TextButton(onClick = {
					inOnPickSaveFile()?.let { inPath ->
						vMessage = inController.saveCopy(inPath)?.let { "Copie impossible : $it" } ?: "Copie enregistrée : $inPath"
					}
				}) { Text("Enregistrer une copie…") }
				TextButton(onClick = inController::confirmPending) { Text("${inAction.label} quand même") }
			}
		},
		dismissButton    = { TextButton(onClick = inController::cancelPending) { Text("Annuler") } }
	)
}

/*
	Barre latérale : logo, puis un bouton par écran principal (l'écran actif est
	plein).
	inScreen   : écran actif
	inOnScreen : change d'écran
*/
@Composable
private fun Rail(
	inScreen   : Screen,
	inOnScreen : (Screen) -> Unit
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	Column(
		modifier            = Modifier.fillMaxHeight().width(220.dp).padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp)
	) {
		Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
			PatrimoineLogo(32.dp)
			Spacer(Modifier.width(12.dp))
			Text("PatrimoinePlus", style = MaterialTheme.typography.titleMedium)
		}
		Screen.entries.forEach { inEntry ->
			val vActive = inEntry == inScreen										// écran actif
			Row(
				modifier          = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(50))
					.background(if (vActive) vColors.primary else vColors.surface)
					.clickable(role = Role.Tab) { inOnScreen(inEntry) }
					.semantics { selected = vActive }
					.padding(horizontal = 16.dp, vertical = 10.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(
					imageVector        = when (inEntry) { Screen.DASHBOARD -> LineIcons.Home; Screen.CATEGORIES -> LineIcons.Grid; Screen.SETTINGS -> LineIcons.Sliders },
					contentDescription = null,
					tint               = if (vActive) vColors.onPrimary else vColors.onSurface,
					modifier           = Modifier.size(20.dp)
				)
				Spacer(Modifier.width(12.dp))
				Text(inEntry.label, style = MaterialTheme.typography.labelLarge, color = if (vActive) vColors.onPrimary else vColors.onSurface)
			}
		}
	}
}

/*
	Bandeau d'état : source des données (démonstration, dossier et dernière
	lecture, attente, fichier refusé), message ponctuel, nouvelle opération
	et mode discret.
	inSpace      : données et état de la lecture
	inNotice     : message ponctuel ; null sans message
	inController : contrôleur
*/
@Composable
private fun StatusBar(
	inSpace      : WorkspaceState,
	inNotice     : String?,
	inController : DesktopController
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	val vText   = inNotice ?: when (val vStatus = inSpace.status)				// état, en clair
	{
		LoadStatus.Demo           -> "Démonstration : données fictives, modifiables, jamais enregistrées."
		LoadStatus.Waiting        -> "Dossier ${(inSpace.source as? Source.Folder)?.path} : fichier d’échange pas encore présent. Activez la synchronisation sur le téléphone, ou saisissez ici."
		is LoadStatus.Synced      -> "Synchronisé avec ${(inSpace.source as? Source.Folder)?.path} à ${Instant.ofEpochMilli(vStatus.at).atZone(ZoneId.systemDefault()).format(kTimeFormat)}."
		LoadStatus.Pending        -> "Modifications en cours d’enregistrement…"
		is LoadStatus.Invalid     -> "Fichier d’échange refusé (${vStatus.message}) : rien n’est enregistré, dernières données valides affichées."
		is LoadStatus.Conflict    -> "Conflit : choisissez la version à garder."
		is LoadStatus.WriteFailed -> "Enregistrement impossible : ${vStatus.message}. Nouvel essai automatique."
		LoadStatus.Idle           -> ""
	}
	val vAlert  = inNotice == null && (inSpace.status is LoadStatus.Invalid || inSpace.status is LoadStatus.WriteFailed || inSpace.status is LoadStatus.Conflict)	// état anormal
	Row(
		modifier          = Modifier.fillMaxWidth().height(60.dp).padding(start = 32.dp, end = 16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text     = vText,
			style    = MaterialTheme.typography.bodyMedium,
			color    = if (vAlert) vColors.error else vColors.onSurfaceVariant,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f)
		)
		if (inNotice != null)
		{
			TextButton(onClick = inController::clearNotice) { Text("OK") }
		}
		val vHidden = LocalAmountsHidden.current								// mode discret actif
		Spacer(Modifier.width(8.dp))
		Button(onClick = inController::newOperation) { Text("Nouvelle opération") }
		Spacer(Modifier.width(8.dp))
		TextButton(onClick = inController::toggleHidden) {
			Text(if (vHidden) "Afficher les montants" else "Masquer les montants", style = MaterialTheme.typography.labelLarge)
		}
	}
}
