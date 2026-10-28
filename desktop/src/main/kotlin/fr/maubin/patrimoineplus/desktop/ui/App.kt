package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import fr.maubin.patrimoineplus.desktop.ui.screens.EntriesScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.DashboardScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.SearchScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.BudgetScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.GoalsScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.ProjectionScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.ReportScreen
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
import fr.maubin.patrimoineplus.desktop.ui.panels.PurchasePanel
import fr.maubin.patrimoineplus.desktop.ui.panels.SalePanel
import fr.maubin.patrimoineplus.desktop.ui.panels.RecurrencePanel
import fr.maubin.patrimoineplus.ui.components.LocalProportionStyle
import fr.maubin.patrimoineplus.ui.components.ProportionStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.DpOffset
import fr.maubin.patrimoineplus.ui.theme.DeskTheme
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Path as DrawPath
import fr.maubin.patrimoineplus.ui.components.isGlass
import fr.maubin.patrimoineplus.ui.components.AppGround
import fr.maubin.patrimoineplus.ui.components.FloatingSurface
import fr.maubin.patrimoineplus.domain.calc.scoped
import fr.maubin.patrimoineplus.domain.calc.scopeOf
import fr.maubin.patrimoineplus.domain.model.Profile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import fr.maubin.patrimoineplus.desktop.ui.tour.TourOverlay
import fr.maubin.patrimoineplus.desktop.ui.tour.Tours
import fr.maubin.patrimoineplus.desktop.ui.tour.TourAnchors
import fr.maubin.patrimoineplus.desktop.ui.tour.LocalTourAnchors
import fr.maubin.patrimoineplus.desktop.ui.tour.tourAnchor
import fr.maubin.patrimoineplus.desktop.ui.tour.kTourAccount
import kotlinx.coroutines.delay
import fr.maubin.patrimoineplus.desktop.ActiveTour
import fr.maubin.patrimoineplus.ui.theme.Palette
import fr.maubin.patrimoineplus.desktop.ui.panels.ThemePanel
import fr.maubin.patrimoineplus.ui.components.UpdateDialog
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import fr.maubin.patrimoineplus.ui.components.LocalPointer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import fr.maubin.patrimoineplus.ui.components.LocalProfiles
import fr.maubin.patrimoineplus.desktop.ui.panels.LocalPanelGuard
import fr.maubin.patrimoineplus.desktop.ui.panels.PanelGuard
import fr.maubin.patrimoineplus.data.backup.newerFormatText

private val kTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.FRANCE)	// heure de lecture du fichier
// Choix d'un relevé exporté par la banque (vue d'un compte) ; fourni par App.
val LocalBankPicker = staticCompositionLocalOf<() -> Path?> { { null } }

// Choix d'un fichier à écrire d'après un nom proposé (export PDF) ; fourni par App.
val LocalSavePicker = staticCompositionLocalOf<(String) -> Path?> { { null } }

val kContentMaxWidth    = 1600.dp													// largeur maximale du contenu
private const val kPageMillis  = 280													// transition entre écrans
private const val kPanelMillis = 240													// arrivée du panneau de saisie
private const val kRailMillis  = 260													// glissement du disque du rail
private const val kTourDelay   = 450L													// attente avant la visite d'une page (transition, mise en place)
private val kRailButton = 48.dp														// diamètre d'un bouton du rail
private val kRailGap    = 6.dp														// écart entre deux boutons du rail
private val kRailStep   = kRailButton + kRailGap										// pas du rail
private val kEase       = CubicBezierEasing(0.2f, 0f, 0f, 1f)						// courbe « accentuée » de Material

/*
	Racine de l'appli PC : thème selon l'apparence choisie, fond pastel, mode
	discret, puis écran d'accueil (aucune source) ou appli complète.
	inController     : contrôleur
	inOnPickSaveFile : choix d'un fichier où enregistrer une copie ; null si annulé
	inOnPickBankFile : choix d'un relevé exporté par la banque ; null si annulé
	inOnPickSave     : choix d'un fichier à écrire, d'après un nom proposé ; null si annulé
	inWindowShown    : vrai quand la fenêtre est à l'écran (l'animation du lancement l'attend) ; null sans fenêtre (tests)
	inOnPickFolder   : ouvre le sélecteur de dossier ; rend le dossier choisi ou null
*/
@Composable
fun App(
	inController     : DesktopController,
	inOnPickSaveFile : () -> Path? = { null },
	inOnPickBankFile : () -> Path? = { null },
	inOnPickSave     : (String) -> Path? = { null },
	inWindowShown    : (() -> Boolean)? = null,
	inOnPickFolder   : () -> Path?
)
{
	val vSettings by inController.settings.collectAsState()					// réglages
	val vSpace    by inController.workspace.collectAsState()				// données
	val vPending  by inController.pending.collectAsState()					// action retenue
	val vDark     = vSettings.appearance.isDark()									// mode sombre effectif
	val vPalette  = vSpace.data.themes.firstOrNull { it.id == vSettings.palette }?.let { Palette.of(it.colors) }	// thème personnalisé choisi ; null sans
	val vLaunch   = rememberLaunch(inWindowShown)												// lancement : fond seul, puis arrivée des éléments
	val vPointer  = remember { mutableStateOf<Offset?>(null) }									// pointeur dans la fenêtre (regard de l'assistant)
	DeskTheme(inDarkTheme = vDark, inColorTheme = vSettings.colors, inGlass = vSettings.glass, inPalette = vPalette) {
		CompositionLocalProvider(LocalAmountsHidden provides vSettings.hidden, LocalBankPicker provides inOnPickBankFile, LocalSavePicker provides inOnPickSave, LocalProportionStyle provides ProportionStyle(vSettings.pies, inController::setPie), LocalTourAnchors provides remember { TourAnchors() }, LocalLaunch provides vLaunch, LocalPointer provides remember { { vPointer.value } }, LocalAssistant provides (if (vSettings.assistant) vSettings.calm else null), LocalAssistantRoom provides (if (vSettings.assistant) kAssistantRoom else 0.dp), LocalProfiles provides vSpace.data.profiles, LocalPanelGuard provides remember(inController) { PanelGuard(inController::setPanelDirty, inController::whenDiscarded) }) {
			AppGround(vDark, vSettings.glass)												// fond pastel, ou formes floues derrière le verre
			Surface(
				modifier     = Modifier.fillMaxSize().pointerInput(Unit) {
					awaitPointerEventScope {
						while (true)
						{
							val vEvent = awaitPointerEvent(PointerEventPass.Initial)				// mouvement, sans le consommer
							vPointer.value = if (vEvent.type == PointerEventType.Exit) null else vEvent.changes.firstOrNull()?.position
						}
					}
				},
				color        = Color.Transparent,
				contentColor = MaterialTheme.colorScheme.onSurface
			) {
				if (vSpace.source == Source.None)
				{
					WelcomeScreen(
						inOnFolder = { inOnPickFolder()?.let(inController::openFolder) },
						inOnDemo   = inController::openDemo
					)
				}
				else
				{
					Shell(inController, vSpace, inOnPickFolder, vDark)
					if (vSettings.assistant) FloatingAssistant(inController, vSpace, vSettings.hidden, vSettings.calm)
				}
				vPending?.let { inAction -> UnsavedDialog(inAction, inController, inOnPickSaveFile) }
				val vNewer   = (vSpace.status as? LoadStatus.Invalid)?.newerFormat				// format plus récent que celui de l'appli ; null sinon
				var vSeen    by remember { mutableStateOf<Int?>(null) }						// format dont la fenêtre a été fermée
				if (vNewer != null && vNewer != vSeen) NewerFormatDialog(vNewer, { vSeen = vNewer; inController.checkUpdatesNow() }, { vSeen = vNewer })
				val vDiscard by inController.discard.collectAsState()						// abandon de la saisie à confirmer
				if (vDiscard != null) DiscardDialog(inController::confirmDiscard, inController::cancelDiscard)
				val vUpdate by inController.update.collectAsState()						// fenêtre de mise à jour
				vUpdate?.let { inState ->
					UpdateDialog(inState, inController::startUpdate, inController::postponeUpdate, inController::ignoreUpdate, inController::cancelUpdate)
				}
				val vTour by inController.tour.collectAsState()							// visite guidée affichée
				val vLastTour = remember { mutableStateOf<ActiveTour?>(null) }			// dernière visite (gardée pendant sa disparition)
				vTour?.let { vLastTour.value = it }
				AnimatedVisibility(visible = vTour != null, enter = fadeIn(tween(260)), exit = fadeOut(tween(220))) {
					vLastTour.value?.let { inTour ->
						TourOverlay(
							inSteps     = Tours.steps(inTour.key),
							inStep      = inTour.step,
							inOnStep    = inController::setTourStep,
							inOnFinish  = inController::finishTour,
							inOnStopAll = inController::stopAllTours,
							inGuide     = if (vSettings.assistant) vSettings.calm else null
						)
					}
				}
			}
		}
	}
}

/*
	Appli complète, dans un grand cadre arrondi posé sur le fond : logo, rail
	d'icônes et bascule clair / sombre à gauche, bandeau d'état en haut, écran
	courant au centre (largeur limitée sur très grand écran), panneau de
	saisie flottant à droite.
	inController   : contrôleur
	inSpace        : données et état de la lecture
	inOnPickFolder : sélecteur de dossier
	inDark         : mode sombre effectif
*/
@Composable
private fun Shell(
	inController   : DesktopController,
	inSpace        : WorkspaceState,
	inOnPickFolder : () -> Path?,
	inDark         : Boolean
)
{
	val vNav    by inController.navigation.collectAsState()					// position
	val vPanel  by inController.panel.collectAsState()						// panneau de saisie ouvert
	val vNotice by inController.notice.collectAsState()						// message ponctuel
	val vBank   by inController.bank.collectAsState()							// relevé en cours de rapprochement
	val vSet    by inController.settings.collectAsState()						// réglages (vue affichée)
	val vView   = remember(inSpace, vSet.view) { inSpace.copy(data = inSpace.data.scoped(scopeOf(vSet.view, inSpace.data.profiles))) }	// données de la vue (profil ou famille)
	val vFocus  = remember { FocusRequester() }									// focus clavier de secours (raccourcis)
	LaunchedEffect(Unit) { vFocus.requestFocus() }
	LaunchedEffect(inSpace.data) { inController.dropStalePanel() }
	val vLaunch = LocalLaunch.current														// animation du lancement
	LaunchedEffect(vNav.screen, vNav.accountId) {
		snapshotFlow { vLaunch() }.first { it >= 1f }										// lancement fini : pas de visite pendant l'arrivée des éléments
		delay(kTourDelay)																// page arrivée et éléments placés
		inController.showTourIfNew(if (vNav.accountId != null) kTourAccount else vNav.screen.name)
	}
	val vFrame  = RoundedCornerShape(36.dp)										// forme du cadre
	Row(
		Modifier
			.fillMaxSize()
			.padding(14.dp)
			.launchReveal(0f)																	// lancement : le cadre d'abord (fondu seul : un zoom redessinerait tout le cadre à chaque image)
			.clip(vFrame)
			.background(Color.White.copy(alpha = if (inDark) 0.025f else if (isGlass()) 0.18f else 0.38f))
			.border(1.dp, Color.White.copy(alpha = if (inDark) 0.07f else 0.7f), vFrame)
			.focusRequester(vFocus).focusable().onPreviewKeyEvent { inEvent ->
			when
			{
				inEvent.type != KeyEventType.KeyDown || !inEvent.isCtrlPressed -> false
				inEvent.key == Key.N -> { inController.newOperation(); true }
				inEvent.key == Key.F -> { inController.focusSearch(); true }
				else                 -> false
			}
		}
	) {
		Box(Modifier.launchReveal(0.15f, inDx = (-20).dp)) { Rail(vNav.screen, inController::show, inDark, inController::setAppearance) }	// puis le rail
		Column(Modifier.weight(1f).fillMaxHeight()) {
			Box(Modifier.launchReveal(0.25f, inDy = (-12).dp)) { StatusBar(inSpace, vNotice, inController, scopeOf(vSet.view, inSpace.data.profiles)) }	// puis la barre du haut
			Box(Modifier.fillMaxSize().launchReveal(0.4f, inDy = 18.dp), contentAlignment = Alignment.TopCenter) {	// enfin l'écran
				Box(Modifier.widthIn(max = kContentMaxWidth).fillMaxSize()) {
					ScreenStack(
						inKey    = vNav.screen to (vNav.accountId?.let { if (vBank?.accountId == it) "$it#relevé" else it }),
						inKeep   = { it.second == null },												// écrans principaux gardés en vie ; comptes et relevés oubliés
						inShift  = { inFrom, inTo -> pageShift(inFrom.second, inTo.second) },
						inMillis = kPageMillis,
						inEasing = kEase
					) { (inScreen, inAccountId) ->
						when
						{
							inAccountId?.endsWith("#relevé") == true -> vBank?.let { ReconcileScreen(vView, inController, it) }
							inAccountId != null              -> AccountScreen(vView, inController, inAccountId, vNav.month)
							inScreen == Screen.DASHBOARD     -> DashboardScreen(vView, inController)
							inScreen == Screen.SEARCH        -> SearchScreen(vView, inController)
							inScreen == Screen.OPERATIONS    -> EntriesScreen(vView, inController)
							inScreen == Screen.REPORT        -> ReportScreen(vView, inController)
							inScreen == Screen.PROJECTION    -> ProjectionScreen(vView, inController)
							inScreen == Screen.BUDGET        -> BudgetScreen(vView, inController, inSpace.data)
							inScreen == Screen.GOALS         -> GoalsScreen(vView, inController)
							else                             -> SettingsScreen(vView, inController, inOnPickFolder)
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
						is Panel.Purchase        -> PurchasePanel(inController, inPanel.form)
						is Panel.Sale            -> SalePanel(inController, inPanel.form)
						is Panel.Recurring       -> RecurrencePanel(inController, inPanel.form)
						is Panel.ThemeSheet      -> ThemePanel(inController, inPanel.themeId)
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
	Sens du glissement entre deux écrans (voir ScreenStack) : vers la vue
	d'un compte, la page arrive de la droite ; au retour, de la gauche ; entre
	écrans principaux, simple fondu.
	inFrom : compte affiché avant ; null sur un écran principal
	inTo   : compte affiché après ; null sur un écran principal
*/
private fun pageShift(
	inFrom : String?,
	inTo   : String?
): Int = when
{
	inFrom == null && inTo != null -> 1											// ouverture d'un compte
	inFrom != null && inTo == null -> -1										// retour au tableau de bord
	else                           -> 0
}

/*
	Fichier d'échange écrit par une version plus récente (le téléphone a été
	mis à jour, pas le PC) : explication et invitation à mettre à jour. Rien
	n'est écrit dans le fichier en attendant ; la fenêtre revient au
	lancement suivant tant que le problème demeure.
	inFormat   : format du fichier
	inOnUpdate : recherche de la mise à jour
	inOnLater  : fermeture
*/
@Composable
internal fun NewerFormatDialog(
	inFormat   : Int,
	inOnUpdate : () -> Unit,
	inOnLater  : () -> Unit
)
{
	AlertDialog(
		onDismissRequest = inOnLater,
		title            = { Text("Mise à jour nécessaire") },
		text             = { Text(newerFormatText(inFormat, "le téléphone")) },
		confirmButton    = { TextButton(onClick = inOnUpdate) { Text("Rechercher la mise à jour") } },
		dismissButton    = { TextButton(onClick = inOnLater) { Text("Plus tard") } }
	)
}

/*
	Confirmation avant de perdre la saisie d'un panneau (croix, Échap, autre
	panneau, autre écran, fermeture de l'appli).
	inOnDiscard  : abandonne la saisie et poursuit
	inOnContinue : reprend la saisie
*/
@Composable
internal fun DiscardDialog(
	inOnDiscard  : () -> Unit,
	inOnContinue : () -> Unit
)
{
	AlertDialog(
		onDismissRequest = inOnContinue,
		title            = { Text("Abandonner la saisie ?") },
		text             = { Text("Les modifications de ce panneau ne sont pas encore enregistrées. Si vous continuez, elles seront perdues.") },
		confirmButton    = { TextButton(onClick = inOnDiscard) { Text("Abandonner", color = MaterialTheme.colorScheme.error) } },
		dismissButton    = { TextButton(onClick = inOnContinue) { Text("Continuer la saisie") } }
	)
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
	Colonne de gauche : logo, rail flottant d'une icône ronde par écran
	principal (l'écran actif sur un disque plein, qui glisse d'une icône à
	l'autre ; nom de l'écran en info-bulle et pour les lecteurs d'écran),
	bascule clair / sombre en bas.
	inScreen     : écran actif
	inOnScreen   : change d'écran
	inDark       : mode sombre effectif
	inOnAppearance : choisit l'apparence claire ou sombre
*/
@Composable
private fun Rail(
	inScreen       : Screen,
	inOnScreen     : (Screen) -> Unit,
	inDark         : Boolean,
	inOnAppearance : (ThemeMode) -> Unit
)
{
	val vIndex  = Screen.entries.indexOf(inScreen)								// rang de l'écran actif
	val vOffset by animateDpAsState(kRailStep * vIndex, tween(kRailMillis, easing = kEase), label = "disque")	// position du disque
	Column(
		modifier            = Modifier.fillMaxHeight().width(92.dp).padding(vertical = 22.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		PatrimoineLogo(48.dp)
		Spacer(Modifier.weight(1f))
		FloatingSurface(RoundedCornerShape(50), Modifier.tourAnchor("shell.rail")) {
			Box(Modifier.padding(6.dp)) {
				Box(Modifier.offset(y = vOffset).size(kRailButton).clip(CircleShape).background(MaterialTheme.colorScheme.primary))	// disque de l'écran actif
				Column(verticalArrangement = Arrangement.spacedBy(kRailGap)) {
					Screen.entries.forEach { inEntry ->
						RailButton(
							inIcon   = screenIcon(inEntry),
							inLabel  = inEntry.label,
							inActive = inEntry == inScreen,
							inRole   = Role.Tab,
							inOnClick = { inOnScreen(inEntry) }
						)
					}
				}
			}
		}
		Spacer(Modifier.weight(1f))
		FloatingSurface(RoundedCornerShape(50)) {
			Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(kRailGap)) {
				RailButton(LineIcons.Sun, "Apparence claire", !inDark, Role.RadioButton, inShowDisc = true) { inOnAppearance(ThemeMode.LIGHT) }
				RailButton(LineIcons.Moon, "Apparence sombre", inDark, Role.RadioButton, inShowDisc = true) { inOnAppearance(ThemeMode.DARK) }
			}
		}
	}
}

/*
	Icône d'un écran principal.
	inScreen : écran
*/
private fun screenIcon(inScreen: Screen): ImageVector =
	when (inScreen)
	{
		Screen.DASHBOARD  -> LineIcons.Home
		Screen.GOALS      -> LineIcons.Target
		Screen.SEARCH     -> LineIcons.Search
		Screen.OPERATIONS -> LineIcons.Edit
		Screen.BUDGET     -> LineIcons.Wallet
		Screen.PROJECTION -> LineIcons.Trend
		Screen.REPORT     -> LineIcons.Document
		Screen.SETTINGS   -> LineIcons.Sliders
	}

/*
	Bouton rond du rail : icône seule, nom en info-bulle à droite et pour les
	lecteurs d'écran ; teinte inversée quand il est actif (le disque est
	dessiné par le rail, ou par le bouton avec inShowDisc).
	inIcon     : icône
	inLabel    : nom de l'action
	inActive   : bouton actif
	inRole     : rôle (onglet, choix)
	inShowDisc : le bouton dessine lui-même son disque actif
	inOnClick  : action
*/
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailButton(
	inIcon     : ImageVector,
	inLabel    : String,
	inActive   : Boolean,
	inRole     : Role,
	inShowDisc : Boolean = false,
	inOnClick  : () -> Unit
)
{
	val vColors  = MaterialTheme.colorScheme										// palette courante
	val vSource  = remember { MutableInteractionSource() }						// survol
	val vHovered by vSource.collectIsHoveredAsState()							// souris au-dessus
	val vTint    by animateColorAsState(if (inActive) vColors.onPrimary else if (vHovered) vColors.onSurface else vColors.onSurfaceVariant, tween(kRailMillis), label = "teinte")	// teinte de l'icône
	val vDisc    by animateColorAsState(
		when
		{
			inActive && inShowDisc -> vColors.primary
			!inActive && vHovered  -> vColors.surfaceContainer
			else                   -> Color.Transparent
		},
		tween(kRailMillis / 2), label = "disque")										// fond du bouton
	TooltipArea(
		tooltip          = {
			Surface(shape = RoundedCornerShape(50), color = vColors.inverseSurface, contentColor = vColors.inverseOnSurface, shadowElevation = 4.dp) {
				Text(inLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
			}
		},
		delayMillis      = 350,
		tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.CenterEnd, alignment = Alignment.CenterEnd, offset = DpOffset(12.dp, 0.dp))
	) {
		Box(
			modifier         = Modifier
				.size(kRailButton)
				.clip(CircleShape)
				.background(vDisc)
				.hoverable(vSource)
				.clickable(interactionSource = vSource, indication = null, role = inRole, onClick = inOnClick)
				.semantics { contentDescription = inLabel; selected = inActive },
			contentAlignment = Alignment.Center
		) {
			Icon(inIcon, contentDescription = null, tint = vTint, modifier = Modifier.size(22.dp))
		}
	}
}



/*
	Bandeau d'état : source des données (démonstration, dossier et dernière
	lecture, attente, fichier refusé), message ponctuel, puis, en pilules,
	nouvelle opération, recherche (Ctrl+F) et mode discret.
	inSpace      : données et état de la lecture
	inNotice     : message ponctuel ; null sans message
	inController : contrôleur
	inProfile    : profil affiché ; null pour la famille
*/
@Composable
private fun StatusBar(
	inSpace      : WorkspaceState,
	inNotice     : String?,
	inController : DesktopController,
	inProfile    : String?
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
	val vHidden = LocalAmountsHidden.current									// mode discret actif
	Row(
		modifier          = Modifier.fillMaxWidth().height(84.dp).padding(start = 32.dp, end = 22.dp),
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
		Spacer(Modifier.width(16.dp))
		if (inSpace.data.profiles.size > 1)
		{
			ViewSwitcher(inSpace.data.profiles, inProfile, inController::setView)
			Spacer(Modifier.width(12.dp))
		}
		FloatingSurface(RoundedCornerShape(50)) {
			Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				Button(onClick = inController::newOperation, contentPadding = PaddingValues(start = 18.dp, end = 22.dp), modifier = Modifier.height(44.dp).tourAnchor("shell.newop")) {
					Icon(LineIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
					Spacer(Modifier.width(8.dp))
					Text("Nouvelle opération")
				}
				Row(Modifier.tourAnchor("shell.privacy"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					RailButton(LineIcons.Search, "Rechercher (Ctrl+F)", false, Role.Button) { inController.focusSearch() }
					RailButton(if (vHidden) LineIcons.EyeOff else LineIcons.Eye, if (vHidden) "Afficher les montants" else "Masquer les montants", false, Role.Button) { inController.toggleHidden() }
				}
			}
		}
	}
}

/*
	Sélecteur de vue : pilule flottante avec l'initiale et le nom du profil
	affiché (ou « Famille ») ; un clic ouvre la liste des profils, puis la
	famille, qui rassemble tous les comptes.
	inProfiles : profils
	inProfile  : profil affiché ; null pour la famille
	inOnView   : change de vue
*/
@Composable
private fun ViewSwitcher(
	inProfiles : List<Profile>,
	inProfile  : String?,
	inOnView   : (String?) -> Unit
)
{
	var vOpen  by remember { mutableStateOf(false) }								// liste ouverte
	val vName  = inProfiles.firstOrNull { it.id == inProfile }?.name ?: "Famille"	// vue affichée
	Box {
		FloatingSurface(RoundedCornerShape(50)) {
			Row(
				modifier          = Modifier
					.clip(RoundedCornerShape(50))
					.clickable(onClickLabel = "Changer de vue") { vOpen = true }
					.semantics { contentDescription = "Vue : $vName" }
					.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Initial(vName, inProfile == null)
				Spacer(Modifier.width(10.dp))
				Text(vName, style = MaterialTheme.typography.labelLarge)
				Spacer(Modifier.width(6.dp))
				Icon(LineIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp))
			}
		}
		DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
			inProfiles.forEach { inEntry ->
				DropdownMenuItem(
					text        = { Text(inEntry.name) },
					leadingIcon = { Initial(inEntry.name) },
					onClick     = { vOpen = false; inOnView(inEntry.id) }
				)
			}
			HorizontalDivider()
			DropdownMenuItem(
				text        = { Text("Famille : tous les comptes") },
				leadingIcon = { Initial("Famille", true) },
				onClick     = { vOpen = false; inOnView(null) }
			)
		}
	}
}

/*
	Pastille ronde à l'encre de la coque avec l'initiale d'un nom, ou
	l'icône de la famille.
	inName   : nom
	inFamily : vue de la famille
*/
@Composable
private fun Initial(
	inName   : String,
	inFamily : Boolean = false
)
{
	Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
		if (inFamily) Icon(LineIcons.Family, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
		else Text(inName.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
	}
}

