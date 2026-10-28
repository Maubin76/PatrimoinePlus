package fr.maubin.patrimoineplus.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.maubin.patrimoineplus.ui.components.FormSheet
import fr.maubin.patrimoineplus.ui.components.TextInput
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
	Feuilles de saisie du téléphone (Robolectric) : une feuille intacte se
	ferme d'un glissement ; une saisie modifiée demande « Abandonner la
	saisie ? » ; « Continuer la saisie » la garde, « Abandonner » ferme.
	Capture `abandon-saisie.png`.
*/
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class DiscardSheetTest
{
	@get:Rule
	val fCompose = createComposeRule()											// interface testée

	private var fDismissed = 0													// fermetures demandées

	/*
		Feuille d'essai : un champ, modifiée dès qu'il diffère du départ.
		inStart : texte de départ
	*/
	private fun sheet(inStart: String)
	{
		fCompose.setContent {
			PatrimoineTheme(inDarkTheme = false) {
				var vText by remember { mutableStateOf(inStart) }				// texte saisi
				FormSheet(inTitle = "Nouveau relevé", inOnDismiss = { fDismissed++ }, inDirty = vText != "") {
					TextInput(vText, { vText = it }, "Solde")
					Text("Bas de la feuille")
				}
			}
		}
		fCompose.waitForIdle()
	}

	/*
		Feuille intacte : fermée sans question.
	*/
	@Test
	fun untouchedCloses()
	{
		sheet("")
		fCompose.onNodeWithText("Nouveau relevé").performTouchInput { swipeDown() }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Abandonner la saisie ?").assertDoesNotExist()
		assertEquals(1, fDismissed)
	}

	/*
		Saisie modifiée : question ; continuer garde la feuille, abandonner la
		ferme.
	*/
	@Test
	fun dirtyAsks()
	{
		sheet("1 250,00")
		fCompose.onNodeWithText("Nouveau relevé").performTouchInput { swipeDown() }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Abandonner la saisie ?").assertExists()
		captureScreenRoboImage("build/outputs/roborazzi/abandon-saisie.png")
		assertEquals(0, fDismissed)
		fCompose.onNodeWithText("Continuer la saisie").performClick()
		fCompose.waitForIdle()
		fCompose.onNodeWithText("1 250,00").assertExists()
		assertEquals(0, fDismissed)
		fCompose.onNodeWithText("Nouveau relevé").performTouchInput { swipeDown() }
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Abandonner").performClick()
		fCompose.waitForIdle()
		assertEquals(1, fDismissed)
	}
}
