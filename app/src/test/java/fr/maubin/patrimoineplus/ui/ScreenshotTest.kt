package fr.maubin.patrimoineplus.ui

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import fr.maubin.patrimoineplus.ui.accounts.AccountsScreen
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.home.HomeAccountLine
import fr.maubin.patrimoineplus.ui.home.HomeContent
import fr.maubin.patrimoineplus.ui.home.HomeGroup
import fr.maubin.patrimoineplus.ui.home.HomeUiState
import fr.maubin.patrimoineplus.ui.home.kPreviewState
import fr.maubin.patrimoineplus.ui.navigation.TopLevelDestination
import fr.maubin.patrimoineplus.ui.settings.SettingsScreen
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val kOutDir = "build/outputs/roborazzi"						// dossier des captures

/*
	Captures d'écran rendues sur la JVM (Robolectric), sans émulateur : elles
	servent à relire l'interface. Les données affichées sont synthétiques.
	Lancement : ./gradlew :app:recordRoborazziDebug
*/
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class ScreenshotTest
{
	/*
		Enveloppe un écran dans le thème et la barre de navigation réelle.
		inDark     : mode sombre
		inHidden   : mode discret
		inSelected : onglet courant
		inScreen   : écran à afficher
	*/
	private fun shell(
		inDark     : Boolean,
		inHidden   : Boolean,
		inSelected : TopLevelDestination,
		inScreen   : @Composable () -> Unit
	): @Composable () -> Unit = {
		PatrimoineTheme(inDarkTheme = inDark) {
			CompositionLocalProvider(LocalAmountsHidden provides inHidden) {
				Scaffold(bottomBar = { PatrimoineBottomBar({ it == inSelected }, {}) }) { inPadding ->
					androidx.compose.foundation.layout.Box(Modifier.padding(inPadding).consumeWindowInsets(inPadding)) {
						inScreen()
					}
				}
			}
		}
	}

	/*
		Accueil avec des comptes, en clair.
	*/
	@Test
	fun homeLight() = captureRoboImage("$kOutDir/home-light.png", content = shell(false, false, TopLevelDestination.Home) {
		HomeContent(kPreviewState, {}, {})
	})

	/*
		Accueil avec des comptes, en sombre.
	*/
	@Test
	fun homeDark() = captureRoboImage("$kOutDir/home-dark.png", content = shell(true, false, TopLevelDestination.Home) {
		HomeContent(kPreviewState, {}, {})
	})

	/*
		Accueil en mode discret.
	*/
	@Test
	fun homeHidden() = captureRoboImage("$kOutDir/home-hidden.png", content = shell(false, true, TopLevelDestination.Home) {
		HomeContent(kPreviewState, {}, {})
	})

	/*
		Accueil sans compte : l'état réel tant que la base est vide.
	*/
	@Test
	fun homeEmpty() = captureRoboImage("$kOutDir/home-empty.png", content = shell(false, false, TopLevelDestination.Home) {
		HomeContent(HomeUiState("7 octobre 2026", emptyList()), {}, {})
	})

	/*
		Onglet des comptes, vide.
	*/
	@Test
	fun accounts() = captureRoboImage("$kOutDir/accounts.png", content = shell(false, false, TopLevelDestination.Accounts) {
		AccountsScreen({})
	})

	/*
		Réglages, en sombre.
	*/
	@Test
	fun settingsDark() = captureRoboImage("$kOutDir/settings-dark.png", content = shell(true, true, TopLevelDestination.Settings) {
		SettingsScreen(true, {})
	})

	/*
		Accueil avec un total à sept chiffres et une police système agrandie à
		130 % : le total doit tenir sur une ligne sans être coupé.
	*/
	@Test
	@Config(fontScale = 1.3f)
	fun homeLargeFont() = captureRoboImage("$kOutDir/home-fontscale-130.png", content = shell(false, false, TopLevelDestination.Home) {
		HomeContent(
			HomeUiState(
				asOf   = "7 octobre 2026",
				groups = listOf(
					HomeGroup(AccountType.CHECKING, listOf(HomeAccountLine("Compte joint du ménage", 1_284_312))),
					HomeGroup(AccountType.BROKERAGE, listOf(HomeAccountLine("PEA", 123_456_789)))
				)
			),
			{},
			{}
		)
	})
}
