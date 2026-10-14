package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import fr.maubin.patrimoineplus.data.backup.AccountDto
import fr.maubin.patrimoineplus.data.backup.writeBackupXlsx
import fr.maubin.patrimoineplus.data.demoBackupFile
import fr.maubin.patrimoineplus.data.sync.kSyncFileName
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.LoadStatus
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
	Tests de la saisie sur PC, dans l'interface (sans écran) : panneau
	latéral des opérations, des comptes et des catégories, contrôles,
	modification, suppression, raccourci clavier, fenêtre de conflit.
*/
class DesktopEditingTest
{
	@get:Rule
	val fCompose = createComposeRule()											// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()													// réglages et dossier jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Contrôleur en démonstration, affiché.
	*/
	private fun demoApp(): DesktopController
	{
		val vApp = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openDemo() }	// contrôleur
		fCompose.setContent { App(vApp) { null } }
		return vApp
	}

	/*
		Saisit du texte dans le champ portant un libellé.
		inLabel : libellé du champ
		inText  : texte
	*/
	private fun type(inLabel: String, inText: String)
	{
		fCompose.onNode(hasSetTextAction() and hasText(inLabel, substring = true)).performTextInput(inText)
	}

	/*
		Choisit une valeur dans une liste déroulante.
		inLabel  : libellé de la liste
		inChoice : texte du choix
	*/
	private fun pick(inLabel: String, inChoice: String)
	{
		fCompose.onNodeWithText(inLabel).performClick()
		fCompose.onAllNodesWithText(inChoice).onLast().performClick()				// le menu s'ouvre au-dessus du reste
	}

	/*
		Dépense saisie par le panneau : montant, compte (prérempli), libellé,
		catégorie ; enregistrée avec le bon signe, panneau fermé.
	*/
	@Test
	fun expenseThroughPanel()
	{
		val vApp = demoApp()														// contrôleur
		val vBefore = vApp.workspace.value.data.transactions.size					// opérations avant
		fCompose.onNodeWithText("Nouvelle opération").performClick()
		type("Montant (€)", "12,50")
		type("Libellé", "Boulangerie test")
		pick("Catégorie", "Alimentation")
		fCompose.onNodeWithText("Enregistrer").performClick()
		fCompose.waitForIdle()
		assertNull(vApp.panel.value)
		val vData = vApp.workspace.value.data										// données après
		assertEquals(vBefore + 1, vData.transactions.size)
		val vTx = vData.transactions.single { it.label == "Boulangerie test" }		// opération saisie
		assertEquals(-1_250L, vTx.postings.single().amountCents)
		assertEquals(LocalDate.of(2026, 10, 12), vTx.date)
	}

	/*
		Saisie incomplète : erreurs affichées dans le panneau, rien
		d'enregistré, panneau ouvert.
	*/
	@Test
	fun errorsAreShown()
	{
		val vApp = demoApp()														// contrôleur
		val vBefore = vApp.workspace.value.data.transactions.size					// opérations avant
		fCompose.onNodeWithText("Nouvelle opération").performClick()
		fCompose.onNodeWithText("Enregistrer").performClick()
		fCompose.onNodeWithText("Indiquez un montant.").assertExists()
		fCompose.onNodeWithText("Le libellé est obligatoire.").assertExists()
		assertEquals(vBefore, vApp.workspace.value.data.transactions.size)
		assertTrue(vApp.panel.value is Panel.Operation)
	}

	/*
		Modification : clic sur une opération de la vue du compte, libellé
		changé, enregistré à la place de l'ancien (même identifiant).
	*/
	@Test
	fun editOperationFromAccount()
	{
		val vApp = demoApp()														// contrôleur
		val vCourant = vApp.workspace.value.data.accounts.first { it.name == "Compte courant" }	// compte
		val vTx = vApp.workspace.value.data.transactions.first { it.label == "Pass transport" && it.date.monthValue == 10 }	// opération du mois
		vApp.openAccount(vCourant.id)
		fCompose.onNodeWithText("Pass transport").performScrollTo().performClick()
		fCompose.onNodeWithText("Modifier l’opération").assertExists()
		fCompose.onNode(hasSetTextAction() and hasText("Pass transport")).performTextClearance()
		type("Libellé", "Navigo")
		fCompose.onNodeWithText("Enregistrer").performClick()
		fCompose.waitForIdle()
		assertEquals("Navigo", vApp.workspace.value.data.transactions.single { it.id == vTx.id }.label)
	}

	/*
		Compte : créé par le panneau avec un solde initial, il apparaît dans
		la liste et dans les données.
	*/
	@Test
	fun createAccountThroughPanel()
	{
		val vApp = demoApp()														// contrôleur
		fCompose.onNodeWithText("Ajouter un compte").performScrollTo().performClick()
		type("Nom", "Livret jeune")
		fCompose.onNodeWithText("Type").performClick()								// liste des types du panneau
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Épargne (livret)").performClick()
		type("Solde initial (€)", "1 600")
		fCompose.onNodeWithText("Enregistrer").performClick()
		fCompose.waitForIdle()
		val vLj = vApp.workspace.value.data.accounts.single { it.name == "Livret jeune" }	// compte créé
		assertEquals(AccountType.SAVINGS, vLj.type)
		assertTrue(vApp.workspace.value.data.transactions.any { inTx -> inTx.kind == TransactionKind.OPENING && inTx.postings.any { it.accountId == vLj.id && it.amountCents == 160_000L } })
		fCompose.onNodeWithText("Livret jeune").assertExists()
	}

	/*
		Suppression d'un compte avec opérations : confirmation qui propose
		l'archivage ; « Supprimer définitivement » efface le compte et ses
		opérations, retour à la liste.
	*/
	@Test
	fun deleteAccountWithConfirmation()
	{
		val vApp = demoApp()														// contrôleur
		val vLdds = vApp.workspace.value.data.accounts.single { it.name == "LDDS" }	// compte avec opérations
		vApp.openAccount(vLdds.id)
		fCompose.onNodeWithText("Modifier le compte").performClick()
		fCompose.onNodeWithText("Supprimer").performClick()
		fCompose.onAllNodesWithText("Archiver").assertCountEquals(2)
		fCompose.onNodeWithText("Supprimer définitivement").performClick()
		fCompose.waitForIdle()
		assertTrue(vApp.workspace.value.data.accounts.none { it.id == vLdds.id })
		assertTrue(vApp.workspace.value.data.transactions.none { inTx -> inTx.postings.any { it.accountId == vLdds.id } })
		assertNull(vApp.navigation.value.accountId)
	}

	/*
		Catégorie : créée depuis l'écran Catégories, en fin de liste des dépenses.
	*/
	@Test
	fun createCategory()
	{
		val vApp = demoApp()														// contrôleur
		vApp.show(Screen.SETTINGS)
		fCompose.onNodeWithText("Ajouter une catégorie de dépense").performScrollTo().performClick()
		type("Nom", "Cadeaux")
		fCompose.onNodeWithText("Enregistrer").performClick()
		fCompose.waitForIdle()
		val vExpenses = vApp.workspace.value.data.categories.filter { it.kind.name == "EXPENSE" }.sortedBy { it.sortOrder }	// dépenses
		assertEquals("Cadeaux", vExpenses.last().name)
	}

	/*
		Raccourci Ctrl+N : ouvre le panneau de saisie sur le compte affiché.
	*/
	@OptIn(ExperimentalTestApi::class)
	@Test
	fun ctrlNOpensEntry()
	{
		val vApp = demoApp()														// contrôleur
		val vLivret = vApp.workspace.value.data.accounts.single { it.name == "Livret A" }	// compte affiché
		vApp.openAccount(vLivret.id)
		fCompose.waitForIdle()
		fCompose.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.N) } }
		fCompose.waitForIdle()
		assertEquals(vLivret.id, (vApp.panel.value as Panel.Operation).form.accountId)
	}

	/*
		Conflit (PC et fichier modifiés) : fenêtre de choix ; « Le fichier »
		adopte la version du téléphone.
	*/
	@Test
	fun conflictDialog()
	{
		val vFolder = fTemp.newFolder("synchro").toPath()							// dossier synchronisé
		val vPath   = vFolder.resolve(kSyncFileName)								// fichier d'échange
		Files.newOutputStream(vPath).use { writeBackupXlsx(demoBackupFile(), it) }
		Files.setLastModifiedTime(vPath, FileTime.fromMillis(1_000))
		val vApp = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openFolder(vFolder) }	// contrôleur
		fCompose.setContent { App(vApp) { null } }
		vApp.edit { vApp.editor.createAccount(it, "Ouvert sur le PC", AccountType.CHECKING, null, 100, null) }
		Files.newOutputStream(vPath).use { writeBackupXlsx(demoBackupFile().let { inFile -> inFile.copy(accounts = inFile.accounts + AccountDto("tel", "Ouvert sur le téléphone", "SAVINGS", null, 100, 99, false, 1, 2)) }, it) }
		Files.setLastModifiedTime(vPath, FileTime.fromMillis(2_000))
		vApp.flush()
		fCompose.waitForIdle()
		assertTrue(vApp.workspace.value.status is LoadStatus.Conflict)
		fCompose.onNodeWithText("Quelle version garder ?").assertExists()
		fCompose.onNodeWithText("Le fichier").performClick()
		fCompose.waitForIdle()
		assertTrue(vApp.workspace.value.data.accounts.any { it.name == "Ouvert sur le téléphone" })
		fCompose.onNodeWithText("Quelle version garder ?").assertDoesNotExist()
	}
}
