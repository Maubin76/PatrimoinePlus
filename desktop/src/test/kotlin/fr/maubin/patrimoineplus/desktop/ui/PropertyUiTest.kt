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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.data.DesktopConfig
import fr.maubin.patrimoineplus.desktop.data.Workspace
import fr.maubin.patrimoineplus.domain.calc.propertyItems
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.domain.model.TransactionKind
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/*
	Comptes de biens dans l'interface du PC (sans écran) : tuiles du tableau
	de bord, page d'un compte Collection, ajout d'un objet déjà possédé,
	vente d'un objet payé depuis le compte courant (prix versé dessus) ;
	captures pour relecture.
*/
class PropertyUiTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	@get:Rule
	val fTemp = TemporaryFolder()														// réglages jetables

	private val kClock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Europe/Paris"))	// horloge figée

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
		val vScene = ImageComposeScene(width = 1440, height = 1400, density = Density(1f)) { App(inApp) { null } }	// scène hors écran
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
		Tableau de bord et page du compte Collection ; ajout d'un objet déjà
		possédé (aucun compte débité) ; vente de la montre (payée depuis le
		compte courant) : prix versé sur le compte courant, plus-value.
	*/
	@Test
	fun collectionAccount()
	{
		val vApp     = demo()																// démonstration
		val vData    = vApp.workspace.value.data											// données
		val vObjects = vData.accounts.single { it.type == AccountType.COLLECTIBLE }		// compte Collection
		val vHome    = vData.accounts.single { it.type == AccountType.REAL_ESTATE }		// compte Immobilier
		capture(vApp, "19-tableau-de-bord-biens")
		vApp.openAccount(vHome.id)
		capture(vApp, "19-immobilier")
		vApp.openAccount(vObjects.id)
		capture(vApp, "19-collection")
		vApp.setAppearance(ThemeMode.DARK)
		capture(vApp, "19-collection-sombre")
		vApp.setAppearance(ThemeMode.LIGHT)
		fCompose.setContent { App(vApp) { null } }
		fCompose.onNodeWithText("Ajouter un objet").performScrollTo().performClick()
		fCompose.waitForIdle()
		type("Nom", "Appareil photo argentique")
		type("Prix d’achat", "350")
		File("build/captures-desktop/19-nouvel-objet.png").writeBytes(Image.makeFromBitmap(fCompose.onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)	// panneau ouvert
		fCompose.onAllNodesWithText("Enregistrer").onLast().performClick()
		fCompose.waitForIdle()
		val vCamera = vApp.workspace.value.data.transactions.single { it.label == "Appareil photo argentique" }	// objet ajouté
		assertEquals(TransactionKind.ACQUISITION, vCamera.kind)
		assertEquals(listOf(vObjects.id to 35_000L), vCamera.postings.map { it.accountId to it.amountCents })	// déjà possédé : aucun compte débité
		val vWatch = propertyItems(vApp.workspace.value.data.transactions, vApp.workspace.value.data.accounts).single { it.name == "Montre de collection" }	// montre
		vApp.openPanel(Panel.Sale(fr.maubin.patrimoineplus.ui.property.newSaleForm(vWatch, vApp.today())))
		fCompose.waitForIdle()
		type("Prix de vente", "5000")
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Plus-value : +800,00", substring = true).assertExists()
		File("build/captures-desktop/19-vente.png").writeBytes(Image.makeFromBitmap(fCompose.onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)	// panneau de vente
		fCompose.onAllNodesWithText("Enregistrer").onLast().performClick()
		fCompose.waitForIdle()
		assertNull(vApp.panel.value)
		val vSold = propertyItems(vApp.workspace.value.data.transactions, vApp.workspace.value.data.accounts).single { it.name == "Montre de collection" }	// montre vendue
		assertEquals(500_000L, vSold.salePrice)
		assertEquals(80_000L, vSold.gain)
		assertEquals(vWatch.paidFrom, vSold.soldTo)
		fCompose.onNodeWithText("Objets vendus").performScrollTo().assertExists()
		assertTrue(vApp.workspace.value.data.accounts.none { it.type.isProperty && vApp.workspace.value.data.transactions.any { inTx -> inTx.kind == TransactionKind.STANDARD && inTx.postings.any { p -> p.accountId == it.id } } })
	}
}
