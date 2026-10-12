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
import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.edit.DataEditor
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.entry.EntryData
import fr.maubin.patrimoineplus.ui.entry.EntryForm
import fr.maubin.patrimoineplus.ui.entry.newEntryForm
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/*
	Écran principal de l'appli PC.
*/
enum class Screen(val label: String)
{
	HOME("Accueil"),															// patrimoine
	ACCOUNTS("Comptes"),														// liste des comptes
	CATEGORIES("Catégories"),													// catégories de dépense et de revenu
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
	Panneau latéral de saisie ouvert.
*/
sealed interface Panel
{
	/*
		Saisie ou modification d'une opération.
		form : formulaire de départ
	*/
	data class Operation(val form: EntryForm) : Panel

	/*
		Création (accountId null) ou modification d'un compte.
		accountId : compte modifié ; null pour en créer un
	*/
	data class AccountSheet(val accountId: String?) : Panel

	/*
		Création (categoryId null) ou modification d'une catégorie.
		categoryId : catégorie modifiée ; null pour en créer une
		kind       : nature d'une nouvelle catégorie
	*/
	data class CategorySheet(
		val categoryId : String?,
		val kind       : CategoryKind = CategoryKind.EXPENSE
	) : Panel

	/*
		Nouveau relevé de la banque.
		accountId : compte
	*/
	data class Statement(val accountId: String) : Panel

	/*
		Saisie manuelle des cours des positions d'un compte Bourse.
		accountId : compte Bourse
	*/
	data class Prices(val accountId: String) : Panel

	/*
		Nouvelle position initiale d'un compte Bourse.
		accountId : compte Bourse
	*/
	data class OpeningPosition(val accountId: String) : Panel

	/*
		Fiche d'un actif (nom, ISIN, symbole).
		assetId : actif
	*/
	data class AssetSheet(val assetId: String) : Panel
}

/*
	Contrôleur de l'appli PC : source des données (dossier synchronisé ou
	démonstration), réglages mémorisés (apparence, mode discret) et navigation.
	Sans interface : testable seul.
	inConfig    : fichier des réglages
	inWorkspace : espace de travail (données)
	inScope     : portée de la surveillance du fichier d'échange ; null pour ne pas surveiller
	inClock     : horloge (horodatages des modifications, date du jour)
*/
class DesktopController(
	private val inConfig    : DesktopConfig,
	private val inWorkspace : Workspace,
	private val inScope     : CoroutineScope?,
	private val inClock     : Clock = Clock.systemDefaultZone()
)
{
	private val fSettings   = MutableStateFlow(inConfig.load())					// réglages
	private val fNavigation = MutableStateFlow(Navigation())					// position
	private val fPanel      = MutableStateFlow<Panel?>(null)					// panneau ouvert

	// Moteur de modification des données.
	val editor = DataEditor(inClock)

	// Panneau de saisie ouvert ; null sans panneau.
	val panel: StateFlow<Panel?> = fPanel

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
		Date du jour.
	*/
	fun today(): LocalDate = LocalDate.now(inClock)

	/*
		Ouvre un panneau de saisie (remplace celui qui était ouvert).
		inPanel : panneau
	*/
	fun openPanel(inPanel: Panel)
	{
		fPanel.value = inPanel
	}

	/*
		Ferme le panneau de saisie.
	*/
	fun closePanel()
	{
		fPanel.value = null
	}

	/*
		Panneau de saisie d'une nouvelle opération, sur le compte affiché s'il y
		en a un (raccourci Ctrl+N, bouton « Nouvelle opération »).
	*/
	fun newOperation()
	{
		val vData = workspace.value.data											// données
		val vForm = newEntryForm(EntryData(vData.accounts.filterNot { it.archived }, vData.categories, vData.assets, vData.prices, vData.transactions), fNavigation.value.accountId, today())	// formulaire
		openPanel(Panel.Operation(vForm))
	}

	/*
		Applique une modification des données (voir DataEditor) ; refusée, rien
		ne change.
		inChange : modification
	*/
	fun edit(inChange: (BackupFile) -> EditResult): EditResult = inWorkspace.edit(inChange)

	/*
		Contenu complet courant des données (lectures du moteur : solde initial,
		nombre d'opérations, usage d'une catégorie).
	*/
	fun currentFile(): BackupFile = inWorkspace.file

	/*
		Tranche un conflit de synchronisation.
		inKeepLocal : vrai pour garder les données du PC
	*/
	fun resolveConflict(inKeepLocal: Boolean) = inWorkspace.resolve(inKeepLocal)

	/*
		Écrit tout de suite les modifications en attente (fermeture de l'appli).
	*/
	fun flush() = inWorkspace.flush()

	/*
		Ouvre une source, la mémorise et revient à l'accueil.
		inSource : source
	*/
	private fun openSource(inSource: Source)
	{
		inWorkspace.flush()
		update(fSettings.value.copy(source = inSource))
		inWorkspace.open(inSource, inScope)
		fNavigation.value = Navigation()
		fPanel.value      = null
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
