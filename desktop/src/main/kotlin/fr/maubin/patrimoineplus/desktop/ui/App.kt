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
import fr.maubin.patrimoineplus.desktop.ui.components.FloatingSurface
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Path as DrawPath
import fr.maubin.patrimoineplus.ui.components.isGlass

private val kTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.FRANCE)	// heure de lecture du fichier
// Choix d'un relevé exporté par la banque (vue d'un compte) ; fourni par App.
val LocalBankPicker = staticCompositionLocalOf<() -> Path?> { { null } }

// Choix d'un fichier à écrire d'après un nom proposé (export PDF) ; fourni par App.
val LocalSavePicker = staticCompositionLocalOf<(String) -> Path?> { { null } }

val kContentMaxWidth    = 1600.dp													// largeur maximale du contenu
private const val kPageMillis  = 280													// transition entre écrans
private const val kPanelMillis = 240													// arrivée du panneau de saisie
private const val kRailMillis  = 260													// glissement du disque du rail
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
	inOnPickFolder   : ouvre le sélecteur de dossier ; rend le dossier choisi ou null
*/
@Composable
fun App(
	inController     : DesktopController,
	inOnPickSaveFile : () -> Path? = { null },
	inOnPickBankFile : () -> Path? = { null },
	inOnPickSave     : (String) -> Path? = { null },
	inOnPickFolder   : () -> Path?
)
{
	val vSettings by inController.settings.collectAsState()					// réglages
	val vSpace    by inController.workspace.collectAsState()				// données
	val vPending  by inController.pending.collectAsState()					// action retenue
	val vDark     = vSettings.appearance.isDark()									// mode sombre effectif
	DeskTheme(inDarkTheme = vDark, inColorTheme = vSettings.colors, inGlass = vSettings.glass) {
		CompositionLocalProvider(LocalAmountsHidden provides vSettings.hidden, LocalBankPicker provides inOnPickBankFile, LocalSavePicker provides inOnPickSave, LocalProportionStyle provides ProportionStyle(vSettings.pies, inController::setPie)) {
			if (vSettings.glass) GlassBackdrop(vDark)										// formes floues derrière le verre
			Surface(if (vSettings.glass) Modifier.fillMaxSize() else Modifier.fillMaxSize().deskGround(vDark), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
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
				}
				vPending?.let { inAction -> UnsavedDialog(inAction, inController, inOnPickSaveFile) }
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
	val vFocus  = remember { FocusRequester() }									// focus clavier de secours (raccourcis)
	LaunchedEffect(Unit) { vFocus.requestFocus() }
	LaunchedEffect(inSpace.data) { inController.dropStalePanel() }
	val vFrame  = RoundedCornerShape(36.dp)										// forme du cadre
	Row(
		Modifier
			.fillMaxSize()
			.padding(14.dp)
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
		Rail(vNav.screen, inController::show, inDark, inController::setAppearance)
		Column(Modifier.weight(1f).fillMaxHeight()) {
			StatusBar(inSpace, vNotice, inController)
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
							inScreen == Screen.SEARCH        -> SearchScreen(inSpace, inController)
							inScreen == Screen.OPERATIONS    -> EntriesScreen(inSpace, inController)
							inScreen == Screen.REPORT        -> ReportScreen(inSpace, inController)
							inScreen == Screen.PROJECTION    -> ProjectionScreen(inSpace, inController)
							inScreen == Screen.BUDGET        -> BudgetScreen(inSpace, inController)
							inScreen == Screen.GOALS         -> GoalsScreen(inSpace, inController)
							else                             -> SettingsScreen(inSpace, inController, inOnPickFolder)
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
		FloatingSurface(RoundedCornerShape(50)) {
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
	Fond de l'effet verre : formes floues que les panneaux translucides
	laissent deviner. Clair : dégradé pastel et grands halos (lavande, rose,
	bleu ciel, pêche). Sombre : noir profond, facettes fumées et lueurs
	violette et indigo.
	inDark : mode sombre
*/
@Composable
private fun GlassBackdrop(inDark: Boolean)
{
	Box(Modifier.fillMaxSize().background(if (inDark) Color(0xFF07070B) else Color(0xFFE9E4FA))) {
		Canvas(Modifier.fillMaxSize().blur(if (inDark) 18.dp else 70.dp, BlurredEdgeTreatment.Rectangle)) {
			val vW = size.width															// largeur
			val vH = size.height															// hauteur
			val vM = maxOf(vW, vH)															// plus grande dimension
			if (inDark)
			{
				val vFacet = { inColor: Color, inPoints: List<Pair<Float, Float>> ->
					drawPath(DrawPath().apply {
						moveTo(inPoints[0].first * vW, inPoints[0].second * vH)
						inPoints.drop(1).forEach { (inX, inY) -> lineTo(inX * vW, inY * vH) }
						close()
					}, inColor)
				}																		// facette, en fractions de l'écran
				vFacet(Color(0xFF14141C), listOf(0f to 0f, 0.42f to 0f, 0.18f to 0.55f, 0f to 0.4f))
				vFacet(Color(0xFF1C1C26), listOf(0.42f to 0f, 0.78f to 0f, 0.5f to 0.38f, 0.18f to 0.55f))
				vFacet(Color(0xFF101017), listOf(0.78f to 0f, 1f to 0f, 1f to 0.48f, 0.5f to 0.38f))
				vFacet(Color(0xFF191922), listOf(0f to 0.4f, 0.18f to 0.55f, 0.36f to 1f, 0f to 1f))
				vFacet(Color(0xFF0C0C12), listOf(0.18f to 0.55f, 0.5f to 0.38f, 0.7f to 1f, 0.36f to 1f))
				vFacet(Color(0xFF1F1F2A), listOf(0.5f to 0.38f, 1f to 0.48f, 1f to 1f, 0.7f to 1f))
				drawCircle(Brush.radialGradient(listOf(Color(0xFF5B4BE0).copy(alpha = 0.42f), Color.Transparent), center = Offset(0.82f * vW, 0.12f * vH), radius = 0.45f * vM), radius = 0.45f * vM, center = Offset(0.82f * vW, 0.12f * vH))
				drawCircle(Brush.radialGradient(listOf(Color(0xFF2A3A8C).copy(alpha = 0.30f), Color.Transparent), center = Offset(0.12f * vW, 0.92f * vH), radius = 0.4f * vM), radius = 0.4f * vM, center = Offset(0.12f * vW, 0.92f * vH))
			}
			else
			{
				drawRect(Brush.linearGradient(listOf(Color(0xFFE6E0FA), Color(0xFFDDE6F8), Color(0xFFE5E0F7)), start = Offset.Zero, end = Offset(vW, vH)))
				drawCircle(Color(0xFFC3B2FF).copy(alpha = 0.75f), radius = 0.26f * vM, center = Offset(0.2f * vW, 0.18f * vH))
				drawCircle(Color(0xFFFFBFD8).copy(alpha = 0.70f), radius = 0.28f * vM, center = Offset(0.06f * vW, 0.86f * vH))
				drawCircle(Color(0xFF9FCBFF).copy(alpha = 0.65f), radius = 0.27f * vM, center = Offset(0.86f * vW, 0.34f * vH))
				drawCircle(Color(0xFFFFD9B8).copy(alpha = 0.50f), radius = 0.2f * vM, center = Offset(0.6f * vW, 0.98f * vH))
			}
		}
	}
}

/*
	Fond de l'appli : dégradé pastel lavande et bleu brume, rose poudré en bas
	à gauche, halo bleu en haut à droite (sombre : nuit indigo et prune).
	inDark : mode sombre
*/
private fun Modifier.deskGround(inDark: Boolean): Modifier =
	drawBehind {
		val vBase = if (inDark) listOf(Color(0xFF16162B), Color(0xFF121729), Color(0xFF181427)) else listOf(Color(0xFFE6E0FA), Color(0xFFDDE6F8), Color(0xFFE5E0F7))	// dégradé de fond
		val vLow  = if (inDark) Color(0xFF3A2142).copy(alpha = 0.55f) else Color(0xFFF7DCE9)	// halo bas gauche
		val vHigh = if (inDark) Color(0xFF223259).copy(alpha = 0.45f) else Color(0xFFD3E4FB)	// halo haut droit
		val vSpan = maxOf(size.width, size.height)										// plus grande dimension
		drawRect(Brush.linearGradient(vBase, start = Offset.Zero, end = Offset(size.width, size.height)))
		drawRect(Brush.radialGradient(listOf(vLow, vLow.copy(alpha = 0f)), center = Offset(0f, size.height), radius = vSpan * 0.62f))
		drawRect(Brush.radialGradient(listOf(vHigh, vHigh.copy(alpha = 0f)), center = Offset(size.width, 0f), radius = vSpan * 0.5f))
	}

/*
	Bandeau d'état : source des données (démonstration, dossier et dernière
	lecture, attente, fichier refusé), message ponctuel, puis, en pilules,
	nouvelle opération, recherche (Ctrl+F) et mode discret.
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
		FloatingSurface(RoundedCornerShape(50)) {
			Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				Button(onClick = inController::newOperation, contentPadding = PaddingValues(start = 18.dp, end = 22.dp), modifier = Modifier.height(44.dp)) {
					Icon(LineIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
					Spacer(Modifier.width(8.dp))
					Text("Nouvelle opération")
				}
				RailButton(LineIcons.Search, "Rechercher (Ctrl+F)", false, Role.Button) { inController.focusSearch() }
				RailButton(if (vHidden) LineIcons.EyeOff else LineIcons.Eye, if (vHidden) "Afficher les montants" else "Masquer les montants", false, Role.Button) { inController.toggleHidden() }
			}
		}
	}
}
