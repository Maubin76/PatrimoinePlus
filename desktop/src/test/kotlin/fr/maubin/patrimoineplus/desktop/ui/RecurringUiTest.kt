package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.Screen
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.remainingAt
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.Frequency
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.ui.planning.RecurrenceForm
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
	Récurrences et crédits dans l'interface du PC (sans écran) : écran des
	récurrences et page d'un crédit (captures), récurrence créée par le
	panneau, échéances passées créées dès l'enregistrement, « Répéter » à la
	saisie d'une opération, crédit créé par le panneau du compte.
*/
class RecurringUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée
	private val kToday = LocalDate.of(2026, 10, 12)									// date du jour

	/*
		Contrôleur en démonstration, en clair, sur les réglages jetables.
	*/
	private fun demo(): DesktopController =
		DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openDemo(); setAppearance(ThemeMode.LIGHT) }

	/*
		Rend l'appli dans un PNG (relecture humaine).
		inApp  : contrôleur
		inName : nom du fichier
	*/
	private fun capture(inApp: DesktopController, inName: String)
	{
		val vScene = ImageComposeScene(width = 1440, height = 1500, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
		vScene.render()
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
	}

	/*
		Saisit du texte dans le champ portant un libellé.
		inLabel : libellé du champ
		inText  : texte
	*/
	private fun type(inLabel: String, inText: String)
	{
		fCompose.onAllNodes(hasSetTextAction() and hasText(inLabel, substring = true)).onFirst().performTextInput(inText)
	}

	/*
		Choisit une valeur dans une liste déroulante du panneau.
		inLabel  : libellé de la liste
		inChoice : texte du choix
	*/
	private fun pick(inLabel: String, inChoice: String)
	{
		fCompose.onAllNodesWithText(inLabel).onLast().performClick()
		fCompose.waitForIdle()
		fCompose.onAllNodesWithText(inChoice).onLast().performClick()
		fCompose.waitForIdle()
	}

	/*
		Écran des récurrences et page du crédit auto de la démonstration ;
		récurrence créée par le panneau (rien de créé avant sa première
		échéance) ; récurrence commencée dans le passé : échéances créées dès
		l'enregistrement, une seule fois.
	*/
	@Test
	fun recurrences()
	{
		val vApp = demo()																	// démonstration
		vApp.show(Screen.RECURRING)
		capture(vApp, "20-recurrences")
		vApp.openAccount(vApp.workspace.value.data.accounts.single { it.type == AccountType.LOAN }.id)
		capture(vApp, "20-credit")
		vApp.show(Screen.RECURRING)
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Nouvelle récurrence").performClick()
		fCompose.waitForIdle()
		type("Libellé", "Netflix")
		type("Montant (€)", "13,49")
		pick("Catégorie", "Abonnements")
		File("build/captures-desktop/20-nouvelle-recurrence.png").writeBytes(Image.makeFromBitmap(fCompose.onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		val vBefore = vApp.workspace.value.data.transactions.size							// opérations avant
		fCompose.onAllNodesWithText("Enregistrer").onLast().performClick()
		fCompose.waitForIdle()
		val vNetflix = vApp.workspace.value.data.recurrences.single { it.label == "Netflix" }	// récurrence créée
		assertEquals(-1_349L, vNetflix.amountCents)
		assertEquals(Frequency.MONTHLY, vNetflix.frequency)
		assertEquals(vBefore, vApp.workspace.value.data.transactions.size)				// première échéance demain : rien encore
		val vPast = Recurrence("cantine", "Cantine", vApp.workspace.value.data.accounts.first { it.name == "Compte courant" }.id, null,
			vApp.workspace.value.data.categories.first { it.name == "Alimentation" }.id, -6_000, Frequency.MONTHLY, LocalDate.of(2026, 9, 1))	// commencée le 1er septembre
		vApp.edit { vApp.editor.saveRecurrence(it, vPast) }
		assertEquals(2, vApp.workspace.value.data.transactions.count { it.label == "Cantine" })	// 1er septembre et 1er octobre
		vApp.edit { vApp.editor.saveRecurrence(it, vPast.copy(label = "Cantine", notes = "école")) }
		assertEquals(2, vApp.workspace.value.data.transactions.count { it.label == "Cantine" })	// pas recréées
	}

	/*
		« Répéter » à la saisie d'une opération : l'opération et sa récurrence
		(première échéance un mois plus tard) ; crédit créé par le panneau du
		compte : compte Crédit, capital restant dû, mensualités passées tenues
		pour payées.
	*/
	@Test
	fun repeatAndLoan()
	{
		val vApp = demo()																	// démonstration
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Nouvelle opération").performClick()
		fCompose.waitForIdle()
		type("Montant (€)", "45")
		type("Libellé", "Box internet")
		pick("Catégorie", "Abonnements")
		pick("Répéter", "Chaque mois")
		fCompose.onAllNodesWithText("Enregistrer").onLast().performClick()
		fCompose.waitForIdle()
		assertTrue(vApp.workspace.value.data.transactions.any { it.label == "Box internet" && it.date == kToday })
		val vBox = vApp.workspace.value.data.recurrences.single { it.label == "Box internet" }	// récurrence créée
		assertEquals(kToday.plusMonths(1), vBox.start)
		assertEquals(-4_500L, vBox.amountCents)
		vApp.openPanel(Panel.AccountSheet(null))
		fCompose.waitForIdle()
		type("Nom", "Prêt immobilier")
		pick("Type", "Crédit (prêt immobilier, auto…)")
		type("Montant emprunté", "200000")
		type("Taux annuel", "3,2")
		type("Durée (mois)", "240")
		fCompose.onAllNodes(hasSetTextAction() and hasText("Date de la première mensualité", substring = true)).onFirst().performTextClearance()
		type("Date de la première mensualité", "05/01/2024")
		File("build/captures-desktop/20-nouveau-credit.png").writeBytes(Image.makeFromBitmap(fCompose.onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		fCompose.onAllNodesWithText("Enregistrer").onLast().performClick()
		fCompose.waitForIdle()
		val vData   = vApp.workspace.value.data													// données
		val vLoan   = vData.loans.single { inLoan -> vData.accounts.single { it.id == inLoan.accountId }.name == "Prêt immobilier" }	// crédit créé
		assertEquals(20_000_000L, vLoan.principal)
		assertEquals(320, vLoan.rateBp)
		assertEquals(LocalDate.of(2026, 10, 5), vLoan.last)										// 34 mensualités déjà payées
		assertEquals(-remainingAt(vLoan, kToday), Ledger(vData.transactions).balanceAt(vLoan.accountId, kToday))
	}
}
