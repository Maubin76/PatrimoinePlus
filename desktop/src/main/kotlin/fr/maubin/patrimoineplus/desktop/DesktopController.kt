package fr.maubin.patrimoineplus.desktop

import fr.maubin.patrimoineplus.data.backup.BackupFile
import fr.maubin.patrimoineplus.data.edit.DataEditor
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.DesktopSettings
import fr.maubin.patrimoineplus.desktop.data.Source
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.domain.model.CategoryKind
import fr.maubin.patrimoineplus.ui.entry.EntryData
import fr.maubin.patrimoineplus.ui.entry.EntryForm
import fr.maubin.patrimoineplus.ui.entry.newEntryForm
import fr.maubin.patrimoineplus.ui.entry.buildEntry
import fr.maubin.patrimoineplus.ui.entry.checkEntry
import fr.maubin.patrimoineplus.ui.entry.withReferences
import fr.maubin.patrimoineplus.data.bank.BankLine
import fr.maubin.patrimoineplus.data.bank.BankRead
import fr.maubin.patrimoineplus.data.bank.readBankCsv
import java.nio.file.Files
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

private const val kDayCheckMillis = 60_000L										// contrôle du changement de jour

/*
	Écran principal de l'appli PC.
	label : libellé de la barre latérale
*/
enum class Screen(val label: String)
{
	DASHBOARD("Tableau de bord"),												// patrimoine, comptes, opérations récentes
	CATEGORIES("Catégories"),													// catégories de dépense et de revenu
	SETTINGS("Réglages")														// source, apparence
}

/*
	Position dans l'appli.
	screen    : écran principal
	accountId : compte ouvert depuis le tableau de bord ; null sinon
	month     : mois affiché dans la vue d'un compte Courant ; null : mois courant
*/
data class Navigation(
	val screen    : Screen     = Screen.DASHBOARD,
	val accountId : String?    = null,
	val month     : YearMonth? = null
)

/*
	Panneau latéral de saisie ouvert. Chaque ouverture porte un numéro : un
	nouveau panneau repart toujours d'une saisie neuve, même identique.
*/
sealed interface Panel
{
	// Numéro d'ouverture (remet la saisie à zéro).
	val token: Long

	/*
		Saisie ou modification d'une opération.
		form  : formulaire de départ
		token : numéro d'ouverture
	*/
	data class Operation(val form: EntryForm, override val token: Long = 0) : Panel

	/*
		Création (accountId null) ou modification d'un compte.
		accountId : compte modifié ; null pour en créer un
		token     : numéro d'ouverture
	*/
	data class AccountSheet(val accountId: String?, override val token: Long = 0) : Panel

	/*
		Création (categoryId null) ou modification d'une catégorie.
		categoryId : catégorie modifiée ; null pour en créer une
		kind       : nature d'une nouvelle catégorie
		token      : numéro d'ouverture
	*/
	data class CategorySheet(
		val categoryId     : String?,
		val kind           : CategoryKind = CategoryKind.EXPENSE,
		override val token : Long         = 0
	) : Panel

	/*
		Nouveau relevé de la banque.
		accountId : compte
		token     : numéro d'ouverture
	*/
	data class Statement(val accountId: String, override val token: Long = 0) : Panel

	/*
		Saisie manuelle des cours des positions d'un compte Bourse.
		accountId : compte Bourse
		token     : numéro d'ouverture
	*/
	data class Prices(val accountId: String, override val token: Long = 0) : Panel

	/*
		Nouvelle position initiale d'un compte Bourse.
		accountId : compte Bourse
		token     : numéro d'ouverture
	*/
	data class OpeningPosition(val accountId: String, override val token: Long = 0) : Panel

	/*
		Fiche d'un actif (nom, ISIN, symbole).
		assetId : actif
		token   : numéro d'ouverture
	*/
	data class AssetSheet(val assetId: String, override val token: Long = 0) : Panel
}

/*
	Action retenue parce que des modifications n'ont pas pu être écrites dans
	le fichier d'échange (fermeture, changement de source) : l'utilisateur
	choisit d'enregistrer une copie, de continuer quand même ou d'annuler.
	label  : action retenue, en clair (« Fermer l'appli »…)
	reason : état de la synchronisation, en clair
	run    : action à exécuter si l'utilisateur continue
*/
data class PendingAction(
	val label  : String,
	val reason : String,
	val run    : () -> Unit
)

/*
	Relevé de la banque ouvert pour le rapprocher d'un compte.
	accountId : compte rapproché
	fileName  : nom du fichier lu
	lines     : lignes du relevé
	skipped   : lignes illisibles ignorées
*/
data class BankSession(
	val accountId : String,
	val fileName  : String,
	val lines     : List<BankLine>,
	val skipped   : Int
)

/*
	Contrôleur de l'appli PC : source des données (dossier synchronisé ou
	démonstration), réglages mémorisés (apparence, mode discret, couleurs),
	navigation et panneau de saisie. Sans interface : testable seul.
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
	private val fPending    = MutableStateFlow<PendingAction?>(null)			// action retenue (modifications non écrites)
	private val fNotice     = MutableStateFlow<String?>(null)					// message ponctuel du bandeau
	private val fBank       = MutableStateFlow<BankSession?>(null)				// relevé en cours de rapprochement
	private var fTokens     = 0L												// numéros d'ouverture des panneaux

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

	// Action retenue faute d'avoir pu écrire les modifications ; null sinon.
	val pending: StateFlow<PendingAction?> = fPending

	// Message ponctuel à montrer dans le bandeau ; null sans message.
	val notice: StateFlow<String?> = fNotice

	// Relevé de la banque en cours de rapprochement ; null sinon.
	val bank: StateFlow<BankSession?> = fBank

	init
	{
		inWorkspace.open(fSettings.value.source, inScope)
		inScope?.launch {
			while (isActive)
			{
				delay(kDayCheckMillis)
				inWorkspace.refreshDay()
			}
		}
	}

	/*
		Ouvre la démonstration (sans synchronisation).
	*/
	fun openDemo() = guard("Passer en démonstration") { openSource(Source.Demo) }

	/*
		Ouvre un dossier synchronisé.
		inPath : dossier
	*/
	fun openFolder(inPath: Path) = guard("Ouvrir un autre dossier") { openSource(Source.Folder(inPath)) }

	/*
		Ferme la source : retour à l'écran d'accueil.
	*/
	fun closeSource() = guard("Fermer la source") { openSource(Source.None) }

	/*
		Demande de fermeture de l'appli : les modifications en attente sont
		écrites ; si c'est impossible, la fermeture attend le choix de
		l'utilisateur (copie, fermer quand même, annuler).
		inOnExit : ferme l'appli
	*/
	fun requestExit(inOnExit: () -> Unit) = guard("Fermer l’appli", inOnExit)

	/*
		Continue l'action retenue sans enregistrer les modifications.
	*/
	fun confirmPending()
	{
		val vAction = fPending.value ?: return										// action retenue
		fPending.value = null
		vAction.run()
	}

	/*
		Abandonne l'action retenue (les modifications restent à écrire).
	*/
	fun cancelPending()
	{
		fPending.value = null
	}

	/*
		Enregistre une copie des données du PC dans un classeur choisi.
		inPath : fichier
		Retour : null si écrit ; sinon la raison de l'échec
	*/
	fun saveCopy(inPath: Path): String? = inWorkspace.saveCopy(inPath)

	/*
		Change d'apparence.
		inAppearance : apparence
	*/
	fun setAppearance(inAppearance: ThemeMode) = update(fSettings.value.copy(appearance = inAppearance))

	/*
		Active ou désactive les couleurs des hausses et des baisses.
		inEnabled : vrai pour colorer
	*/
	fun setTrend(inEnabled: Boolean) = update(fSettings.value.copy(trend = inEnabled))

	/*
		Bascule le mode discret.
	*/
	fun toggleHidden() = update(fSettings.value.copy(hidden = !fSettings.value.hidden))

	/*
		Affiche un écran principal (ferme la vue d'un compte et le panneau).
		inScreen : écran
	*/
	fun show(inScreen: Screen)
	{
		fBank.value = null
		fNavigation.value = Navigation(inScreen)
		fPanel.value      = null
	}

	/*
		Ouvre la vue d'un compte, sur le mois courant.
		inAccountId : compte
	*/
	fun openAccount(inAccountId: String)
	{
		fBank.value = null
		fNavigation.value = Navigation(Screen.DASHBOARD, inAccountId)
		fPanel.value      = null
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
		Retour : de la vue d'un compte vers le tableau de bord.
	*/
	fun back()
	{
		fBank.value = null
		fNavigation.value = Navigation(fNavigation.value.screen)
		fPanel.value      = null
	}

	/*
		Date du jour.
	*/
	fun today(): LocalDate = LocalDate.now(inClock)

	/*
		Ouvre un panneau de saisie (remplace celui qui était ouvert), avec une
		saisie neuve.
		inPanel : panneau
	*/
	fun openPanel(inPanel: Panel)
	{
		val vToken = ++fTokens														// nouveau numéro d'ouverture
		fPanel.value = when (inPanel)
		{
			is Panel.Operation       -> inPanel.copy(token = vToken)
			is Panel.AccountSheet    -> inPanel.copy(token = vToken)
			is Panel.CategorySheet   -> inPanel.copy(token = vToken)
			is Panel.Statement       -> inPanel.copy(token = vToken)
			is Panel.Prices          -> inPanel.copy(token = vToken)
			is Panel.OpeningPosition -> inPanel.copy(token = vToken)
			is Panel.AssetSheet      -> inPanel.copy(token = vToken)
		}
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
		Ferme le panneau ouvert si ce qu'il modifie n'existe plus (supprimé sur
		le téléphone puis relu) : enregistrer recréerait l'élément. Un message
		le signale. À appeler après chaque changement des données.
	*/
	fun dropStalePanel()
	{
		val vData  = workspace.value.data											// données courantes
		val vStale = when (val vPanel = fPanel.value)								// élément disparu
		{
			null                     -> false
			is Panel.Operation       -> vPanel.form.id != null && vData.transactions.none { it.id == vPanel.form.id }
			is Panel.AccountSheet    -> vPanel.accountId != null && vData.accounts.none { it.id == vPanel.accountId }
			is Panel.CategorySheet   -> vPanel.categoryId != null && vData.categories.none { it.id == vPanel.categoryId }
			is Panel.Statement       -> vData.accounts.none { it.id == vPanel.accountId }
			is Panel.Prices          -> vData.accounts.none { it.id == vPanel.accountId }
			is Panel.OpeningPosition -> vData.accounts.none { it.id == vPanel.accountId }
			is Panel.AssetSheet      -> vData.assets.none { it.id == vPanel.assetId }
		}
		if (vStale)
		{
			fPanel.value  = null
			fNotice.value = "Saisie fermée : l’élément a été supprimé sur l’autre appareil."
		}
	}

	/*
		Montre un message ponctuel dans le bandeau (refus d'une action…).
		inMessage : message
	*/
	fun showNotice(inMessage: String)
	{
		fNotice.value = inMessage
	}

	/*
		Lit un relevé exporté par la banque et ouvre son rapprochement avec un
		compte ; un fichier refusé est signalé dans le bandeau.
		inAccountId : compte rapproché
		inPath      : fichier du relevé
	*/
	fun openBankStatement(
		inAccountId : String,
		inPath      : Path
	)
	{
		val vRead = runCatching { readBankCsv(Files.readAllBytes(inPath)) }
			.getOrElse { BankRead.Invalid("Lecture impossible : ${it.message}") }	// relevé lu
		when (vRead)
		{
			is BankRead.Valid   ->
			{
				fBank.value       = BankSession(inAccountId, inPath.fileName.toString(), vRead.lines, vRead.skipped)
				fNavigation.value = Navigation(Screen.DASHBOARD, inAccountId)
				fPanel.value      = null
			}
			is BankRead.Invalid -> fNotice.value = "Relevé refusé : ${vRead.message}"
		}
	}

	/*
		Ferme le rapprochement (retour à la vue du compte).
	*/
	fun closeBankStatement()
	{
		fBank.value = null
	}

	/*
		Crée en une fois les opérations retenues d'un rapprochement ; si l'une
		est refusée, rien n'est créé.
		inForms : saisies à créer
		Retour  : null si tout est créé ; sinon le motif du refus
	*/
	fun createFromBank(inForms: List<EntryForm>): String?
	{
		val vData  = workspace.value.data											// données
		val vEntry = EntryData(vData.accounts.filterNot { it.archived }, vData.categories, vData.assets, vData.prices, vData.transactions)	// données de saisie
		inForms.firstNotNullOfOrNull { inForm -> checkEntry(inForm, vEntry.withReferences(inForm, vData.accounts, vData.categories)).let { if (it.isEmpty) null else "« ${inForm.label} » : ${listOfNotNull(it.general, it.amount, it.account, it.category, it.label).joinToString(" ")}" } }
			?.let { return it }
		val vResult = edit { inFile ->
			inForms.fold<EntryForm, EditResult>(EditResult.Done(inFile)) { inSoFar, inForm ->
				when (inSoFar)
				{
					is EditResult.Done    -> editor.saveTransaction(inSoFar.file, buildEntry(inForm, vEntry.withReferences(inForm, vData.accounts, vData.categories)))
					is EditResult.Refused -> inSoFar
				}
			}
		}																			// résultat
		return (vResult as? EditResult.Refused)?.messages?.joinToString(" ")
	}

	/*
		Efface le message ponctuel du bandeau.
	*/
	fun clearNotice()
	{
		fNotice.value = null
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
		Écrit tout de suite les modifications en attente ; vrai si plus rien
		n'est en attente.
	*/
	fun flush(): Boolean = inWorkspace.flush()

	/*
		Exécute une action qui quitte les données courantes (fermeture,
		changement de source) si plus aucune modification n'est en attente ;
		sinon la retient et demande à l'utilisateur.
		inLabel  : action, en clair
		inAction : action
	*/
	private fun guard(
		inLabel  : String,
		inAction : () -> Unit
	)
	{
		if (inWorkspace.flush())
		{
			inAction()
			return
		}
		fPending.value = PendingAction(inLabel, statusText(), inAction)
	}

	/*
		État de la synchronisation, en clair (fenêtre des modifications non écrites).
	*/
	private fun statusText(): String =
		when (val vStatus = workspace.value.status)
		{
			is fr.maubin.patrimoineplus.desktop.data.LoadStatus.Conflict    -> "Un conflit attend votre choix."
			is fr.maubin.patrimoineplus.desktop.data.LoadStatus.Invalid     -> "Le fichier d’échange est refusé : ${vStatus.message}"
			is fr.maubin.patrimoineplus.desktop.data.LoadStatus.WriteFailed -> "L’écriture a échoué : ${vStatus.message}"
			else                                                             -> "Le fichier d’échange n’a pas pu être écrit."
		}

	/*
		Ouvre une source, la mémorise et revient au tableau de bord.
		inSource : source
	*/
	private fun openSource(inSource: Source)
	{
		fBank.value = null
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
