package fr.maubin.patrimoineplus.desktop

import fr.maubin.patrimoineplus.desktop.data.Appearance
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.DesktopSettings
import fr.maubin.patrimoineplus.desktop.data.Source
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path
import java.time.YearMonth

/*
	Écran principal de l'appli PC.
*/
enum class Screen(val label: String)
{
	HOME("Accueil"),															// patrimoine
	ACCOUNTS("Comptes"),														// liste des comptes
	SETTINGS("Réglages")														// source, apparence
}

/*
	Position dans l'appli.
	screen    : écran principal
	accountId : compte ouvert depuis l'écran des comptes ; null sinon
	month     : mois affiché dans la vue d'un compte Courant ; null : mois courant
*/
data class Navigation(
	val screen    : Screen     = Screen.HOME,
	val accountId : String?    = null,
	val month     : YearMonth? = null
)

/*
	Contrôleur de l'appli PC : source des données (dossier synchronisé ou
	démonstration), réglages mémorisés (apparence, mode discret) et navigation.
	Sans interface : testable seul.
	inConfig    : fichier des réglages
	inWorkspace : espace de travail (données)
	inScope     : portée de la surveillance du fichier d'échange ; null pour ne pas surveiller
*/
class DesktopController(
	private val inConfig    : DesktopConfig,
	private val inWorkspace : Workspace,
	private val inScope     : CoroutineScope?
)
{
	private val fSettings   = MutableStateFlow(inConfig.load())					// réglages
	private val fNavigation = MutableStateFlow(Navigation())					// position

	// Réglages courants.
	val settings: StateFlow<DesktopSettings> = fSettings

	// Position courante.
	val navigation: StateFlow<Navigation> = fNavigation

	// Données et état de la lecture.
	val workspace: StateFlow<WorkspaceState> = inWorkspace.state

	init
	{
		inWorkspace.open(fSettings.value.source, inScope)
	}

	/*
		Ouvre la démonstration (sans synchronisation).
	*/
	fun openDemo() = openSource(Source.Demo)

	/*
		Ouvre un dossier synchronisé.
		inPath : dossier
	*/
	fun openFolder(inPath: Path) = openSource(Source.Folder(inPath))

	/*
		Ferme la source : retour à l'écran d'accueil.
	*/
	fun closeSource() = openSource(Source.None)

	/*
		Change d'apparence.
		inAppearance : apparence
	*/
	fun setAppearance(inAppearance: Appearance) = update(fSettings.value.copy(appearance = inAppearance))

	/*
		Bascule le mode discret.
	*/
	fun toggleHidden() = update(fSettings.value.copy(hidden = !fSettings.value.hidden))

	/*
		Affiche un écran principal (ferme la vue d'un compte).
		inScreen : écran
	*/
	fun show(inScreen: Screen)
	{
		fNavigation.value = Navigation(inScreen)
	}

	/*
		Ouvre la vue d'un compte, sur le mois courant.
		inAccountId : compte
	*/
	fun openAccount(inAccountId: String)
	{
		fNavigation.value = Navigation(Screen.ACCOUNTS, inAccountId)
	}

	/*
		Change le mois affiché dans la vue d'un compte.
		inMonth : mois
	*/
	fun showMonth(inMonth: YearMonth)
	{
		fNavigation.value = fNavigation.value.copy(month = inMonth)
	}

	/*
		Retour : de la vue d'un compte vers la liste des comptes.
	*/
	fun back()
	{
		fNavigation.value = Navigation(fNavigation.value.screen)
	}

	/*
		Ouvre une source, la mémorise et revient à l'accueil.
		inSource : source
	*/
	private fun openSource(inSource: Source)
	{
		update(fSettings.value.copy(source = inSource))
		inWorkspace.open(inSource, inScope)
		fNavigation.value = Navigation()
	}

	/*
		Applique et mémorise des réglages.
		inSettings : réglages
	*/
	private fun update(inSettings: DesktopSettings)
	{
		fSettings.value = inSettings
		runCatching { inConfig.save(inSettings) }
	}
}
