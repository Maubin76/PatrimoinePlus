package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/*
	Pile d'écrans du PC : un écran principal quitté garde son état (pas de
	recomposition complète au retour), un écran caché n'est plus lisible
	(lecteurs d'écran, tests), un écran non gardé (compte) est oublié.
*/
class ScreenStackTest
{
	@get:Rule
	val fCompose = createComposeRule()												// interface testée

	/*
		Aller-retour entre écrans gardés et non gardés.
	*/
	@Test
	fun keepsVisitedScreens()
	{
		var vScreen   by mutableStateOf("Tableau")											// écran affiché
		val vCreated  = mutableMapOf<String, Int>()											// compositions initiales par écran
		fCompose.setContent {
			ScreenStack(vScreen, { !it.startsWith("Compte") }, { _, _ -> 0 }, 200, LinearEasing) { inScreen ->
				var vClicks by remember { mutableIntStateOf(0).also { vCreated[inScreen] = (vCreated[inScreen] ?: 0) + 1 } }	// état propre à l'écran
				TextButton(onClick = { vClicks++ }) { Text("$inScreen : $vClicks") }
			}
		}
		fCompose.onNodeWithText("Tableau : 0").performClick()
		fCompose.onNodeWithText("Tableau : 1").assertExists()
		vScreen = "Budget"
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Budget : 0").assertExists()
		fCompose.onNodeWithText("Tableau : 1").assertDoesNotExist()
		vScreen = "Compte A"
		fCompose.waitForIdle()
		vScreen = "Tableau"
		fCompose.waitForIdle()
		fCompose.onNodeWithText("Tableau : 1").assertExists()
		vScreen = "Budget"
		fCompose.waitForIdle()
		vScreen = "Compte A"
		fCompose.waitForIdle()
		assertEquals(mapOf("Tableau" to 1, "Budget" to 1, "Compte A" to 2), vCreated)
	}
}
