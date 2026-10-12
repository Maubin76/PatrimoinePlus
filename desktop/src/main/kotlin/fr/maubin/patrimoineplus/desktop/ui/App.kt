package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.Appearance
import fr.maubin.patrimoineplus.desktop.data.LoadStatus
import fr.maubin.patrimoineplus.desktop.data.Source
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.desktop.ui.components.PatrimoineLogo
import fr.maubin.patrimoineplus.desktop.ui.icons.LineIcons
import fr.maubin.patrimoineplus.desktop.ui.screens.AccountScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.AccountsScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.HomeScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.SettingsScreen
import fr.maubin.patrimoineplus.desktop.ui.screens.WelcomeScreen
import fr.maubin.patrimoineplus.desktop.ui.theme.PatrimoineTheme
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val kTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.FRANCE)	// heure de lecture du fichier
val kContentMaxWidth    = 1180.dp													// largeur maximale du contenu

/*
	Racine de l'appli PC : thème selon l'apparence choisie, mode discret, puis
	écran d'accueil (aucune source) ou appli complète.
	inController   : contrôleur
	inOnPickFolder : ouvre le sélecteur de dossier ; rend le dossier choisi ou null
*/
@Composable
fun App(
	inController   : DesktopController,
	inOnPickFolder : () -> Path?
)
{
	val vSettings by inController.settings.collectAsState()					// réglages
	val vSpace    by inController.workspace.collectAsState()				// données
	val vDark     = when (vSettings.appearance)									// mode sombre effectif
	{
		Appearance.SYSTEM -> isSystemInDarkTheme()
		Appearance.LIGHT  -> false
		Appearance.DARK   -> true
	}
	PatrimoineTheme(inDarkTheme = vDark) {
		CompositionLocalProvider(LocalAmountsHidden provides vSettings.hidden) {
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
			}
		}
	}
}

/*
	Appli complète : barre latérale de navigation à gauche, bandeau d'état en
	haut, écran courant au centre (largeur limitée sur grand écran).
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
	val vNav by inController.navigation.collectAsState()					// position
	Row(Modifier.fillMaxSize()) {
		Rail(vNav.screen, inController::show)
		VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
		Column(Modifier.fillMaxSize()) {
			StatusBar(inSpace, inController::toggleHidden)
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
				Box(Modifier.widthIn(max = kContentMaxWidth).fillMaxSize()) {
					when
					{
						vNav.accountId != null       -> AccountScreen(inSpace, vNav.accountId!!, vNav.month, inController::showMonth, inController::back)
						vNav.screen == Screen.HOME     -> HomeScreen(inSpace) { inController.show(Screen.ACCOUNTS) }
						vNav.screen == Screen.ACCOUNTS -> AccountsScreen(inSpace, inController::openAccount)
						else                           -> SettingsScreen(inController, inOnPickFolder)
					}
				}
			}
		}
	}
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
		modifier            = Modifier.fillMaxHeight().width(208.dp).padding(16.dp),
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
					imageVector        = when (inEntry) { Screen.HOME -> LineIcons.Home; Screen.ACCOUNTS -> LineIcons.Wallet; Screen.SETTINGS -> LineIcons.Sliders },
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
	lecture, attente, fichier refusé) et bouton du mode discret.
	inSpace          : données et état de la lecture
	inOnToggleHidden : bascule le mode discret
*/
@Composable
private fun StatusBar(
	inSpace          : WorkspaceState,
	inOnToggleHidden : () -> Unit
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	val vText   = when (val vStatus = inSpace.status)							// état, en clair
	{
		LoadStatus.Demo           -> "Démonstration : données fictives, modifiables, jamais enregistrées."
		LoadStatus.Waiting        -> "Dossier ${(inSpace.source as? Source.Folder)?.path} : fichier d’échange pas encore présent. Activez la synchronisation sur le téléphone, ou saisissez ici."
		is LoadStatus.Synced      -> "Synchronisé avec ${(inSpace.source as? Source.Folder)?.path} à ${Instant.ofEpochMilli(vStatus.at).atZone(ZoneId.systemDefault()).format(kTimeFormat)}."
		LoadStatus.Pending        -> "Modifications en cours d’enregistrement…"
		is LoadStatus.Invalid     -> "Fichier d’échange refusé (${vStatus.message}) : rien n’est enregistré, dernières données valides affichées."
		is LoadStatus.Conflict    -> "Conflit : choisissez la version à garder."
		is LoadStatus.WriteFailed -> "Enregistrement impossible : ${vStatus.message}."
		LoadStatus.Idle           -> ""
	}
	Row(
		modifier          = Modifier.fillMaxWidth().height(56.dp).padding(start = 24.dp, end = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text     = vText,
			style    = MaterialTheme.typography.bodyMedium,
			color    = if (inSpace.status is LoadStatus.Invalid || inSpace.status is LoadStatus.WriteFailed || inSpace.status is LoadStatus.Conflict) vColors.error else vColors.onSurfaceVariant,
			modifier = Modifier.weight(1f)
		)
		val vHidden = LocalAmountsHidden.current								// mode discret actif
		TextButton(onClick = inOnToggleHidden) {
			Text(if (vHidden) "Afficher les montants" else "Masquer les montants", style = MaterialTheme.typography.labelLarge)
		}
	}
}
