package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.domain.format.editableEuros
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
	Rapprochement d'un relevé de la banque dans l'interface du PC (sans
	écran) : relevé lu depuis un fichier, lignes retrouvées, lignes absentes
	proposées, refusées tant qu'il manque une catégorie, puis créées en une
	fois ; fichier refusé signalé.
*/
class ReconcileUiTest
{
	@get:Rule
	val fCompose = createComposeRule()											// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()													// réglages et relevés jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

	/*
		Relevé de la banque : trois opérations déjà saisies du compte courant
		(dates décalées d'un jour, comme au débit), plus deux absentes.
	*/
	@Test
	fun statementIsReconciledAndMissingLinesCreated()
	{
		val vApp     = DesktopController(DesktopConfig(fTemp.root.toPath().resolve("desktop.properties")), Workspace(kClock), null, kClock).apply { openDemo() }	// démonstration
		val vData    = vApp.workspace.value.data										// données
		val vAccount = vData.accounts.first { it.name == "Compte courant" }			// compte rapproché
		val vKnown   = vData.transactions
			.filter { inTx -> inTx.kind == TransactionKind.STANDARD && inTx.postings.single().accountId == vAccount.id && !inTx.date.isAfter(kClock.instant().atZone(kClock.zone).toLocalDate()) }
			.sortedByDescending { it.date }.take(3)										// opérations déjà saisies
		val vDay     = DateTimeFormatter.ofPattern("dd/MM/yyyy")						// format de la banque
		val vRows    = vKnown.map { "${it.date.plusDays(1).format(vDay)};\"CB ${it.label.uppercase()}\";${editableEuros(it.postings.single().amountCents)}" } +
			listOf("${vKnown.first().date.format(vDay)};\"PRLV SEPA FREE MOBILE 123\";-19,99", "${vKnown.first().date.format(vDay)};\"VIR INTERETS\";3,20")	// lignes du relevé
		val vFile    = fTemp.root.toPath().resolve("releve.csv")						// fichier du relevé
		Files.write(vFile, ("Relevé du compte;\n\nDate;Libellé;Montant(EUROS)\n" + vRows.joinToString("\n")).toByteArray(Charset.forName("windows-1252")))
		vApp.openAccount(vAccount.id)
		vApp.openBankStatement(vAccount.id, vFile)
		assertNotNull(vApp.bank.value)
		val vScene = ImageComposeScene(width = 1440, height = 1500, density = Density(1f)) { App(vApp) { null } }	// capture pour relecture humaine
		vScene.render()
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/11-rapprochement.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		vScene.close()
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Rapprochement · Compte courant").assertExists()
		fCompose.onNodeWithText("Retrouvées (3)").performScrollTo().assertExists()
		val vBefore = vApp.workspace.value.data.transactions.size						// opérations avant
		fCompose.onNodeWithText("Créer les 2 opération(s) cochée(s)").performClick()
		fCompose.waitForIdle()
		assertEquals(vBefore, vApp.workspace.value.data.transactions.size)			// sans catégorie : rien n'est créé
		fCompose.onNodeWithText("« Free Mobile »", substring = true).assertExists()
		fCompose.onAllNodesWithText("Choisir…")[0].performClick()
		fCompose.onNodeWithText("Abonnements").performClick()
		fCompose.onNodeWithText("Choisir…").performClick()
		fCompose.onNodeWithText("Intérêts (revenu)").performClick()
		fCompose.onNodeWithText("Créer les 2 opération(s) cochée(s)").performClick()
		fCompose.waitForIdle()
		val vAfter  = vApp.workspace.value.data.transactions							// opérations après
		assertEquals(vBefore + 2, vAfter.size)
		assertTrue(vAfter.any { it.label == "Free Mobile" && it.postings.single().amountCents == -1_999L })
		fCompose.onNodeWithText("Retrouvées (5)").performScrollTo().assertExists()
		Files.writeString(vFile, "pas un relevé")
		vApp.openBankStatement(vAccount.id, vFile)
		assertTrue(vApp.notice.value!!.startsWith("Relevé refusé"))
		vApp.closeBankStatement()
		assertNull(vApp.bank.value)
	}
}
