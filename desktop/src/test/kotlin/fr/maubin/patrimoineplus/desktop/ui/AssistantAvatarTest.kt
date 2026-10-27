package fr.maubin.patrimoineplus.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.assistant.Mood
import fr.maubin.patrimoineplus.ui.components.AssistantAvatar
import fr.maubin.patrimoineplus.ui.components.AvatarReaction
import fr.maubin.patrimoineplus.ui.components.LocalPointer
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import fr.maubin.patrimoineplus.ui.assistant.assistantMood
import fr.maubin.patrimoineplus.ui.assistant.Remark
import fr.maubin.patrimoineplus.ui.assistant.AssistantTarget
import fr.maubin.patrimoineplus.ui.theme.DeskTheme
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.components.AvatarBadge

/*
	Assistant (dessin) : planche des expressions pour relecture (humeurs,
	regard qui suit le pointeur à gauche et à droite, yeux fermés du mode
	discret, sursaut et joie en cours) ; le regard change bien l'image.
	Capture `34-assistant.png`.
*/
class AssistantAvatarTest
{
	/*
		Rend un contenu dans une image, à un instant.
		inWidth   : largeur
		inMillis  : instant du rendu
		inHeight  : hauteur
		inContent : contenu
	*/
	private fun render(
		inWidth   : Int,
		inMillis  : Long,
		inHeight  : Int = 200,
		inContent : @Composable () -> Unit
	): ByteArray
	{
		val vScene = ImageComposeScene(width = inWidth, height = inHeight, density = Density(1f)) { inContent() }	// scène hors écran
		vScene.render(0)
		val vBytes = vScene.render(inMillis * 1_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes	// image
		vScene.close()
		return vBytes
	}

	/*
		Planche des expressions.
	*/
	@Test
	fun expressions()
	{
		val vStates = listOf<@Composable () -> Unit>(
			{ AssistantAvatar(150.dp, Mood.HAPPY, inBadge = AvatarBadge.DOTS) },
			{ AssistantAvatar(150.dp, Mood.NEUTRAL) },
			{ AssistantAvatar(150.dp, Mood.WORRIED, inBadge = AvatarBadge.ALERT) },
			{ CompositionLocalProvider(LocalPointer provides { Offset(0f, 100f) }) { AssistantAvatar(150.dp, Mood.NEUTRAL) } },
			{ CompositionLocalProvider(LocalPointer provides { Offset(5_000f, 0f) }) { AssistantAvatar(150.dp, Mood.HAPPY) } },
			{ AssistantAvatar(150.dp, Mood.HAPPY, inEyesClosed = true) }
		)																				// états fixes
		File("build/captures-desktop").mkdirs()
		val vBoard = render(6 * 170 + 20, 0) {
			Row(Modifier.background(Color(0xFFEEEAF9)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
				vStates.forEach { inState -> Box(Modifier.padding(top = 10.dp)) { inState() } }
			}
		}
		File("build/captures-desktop/34-assistant.png").writeBytes(vBoard)
		File("build/captures-desktop/34-assistant-sombre.png").writeBytes(render(6 * 170 + 20, 0) {
			Row(Modifier.background(Color(0xFF16162B)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
				vStates.forEach { inState -> Box(Modifier.padding(top = 10.dp)) { inState() } }
			}
		})
		val vStartle = render(200, 140) { Box(Modifier.background(Color(0xFFEEEAF9)).padding(20.dp)) { AssistantAvatar(150.dp, Mood.NEUTRAL, inReaction = AvatarReaction.STARTLE to 1L) } }	// sursaut en cours
		val vJoy     = render(200, 200) { Box(Modifier.background(Color(0xFFEEEAF9)).padding(20.dp)) { AssistantAvatar(150.dp, Mood.HAPPY, inReaction = AvatarReaction.JOY to 1L) } }		// joie en cours
		File("build/captures-desktop/34-assistant-sursaut.png").writeBytes(vStartle)
		File("build/captures-desktop/34-assistant-joie.png").writeBytes(vJoy)
		val vLeft  = render(200, 0) { CompositionLocalProvider(LocalPointer provides { Offset(0f, 100f) }) { AssistantAvatar(150.dp, Mood.NEUTRAL) } }	// regard à gauche
		val vRight = render(200, 0) { CompositionLocalProvider(LocalPointer provides { Offset(5_000f, 100f) }) { AssistantAvatar(150.dp, Mood.NEUTRAL) } }	// regard à droite
		assertNotEquals(vLeft.toList(), vRight.toList())
		val vMood   = assistantMood(-12_000, null, emptyList())								// humeur inquiète
		val vBubble = render(420, 0, 300) {
			DeskTheme(inDarkTheme = false, inColorTheme = ColorTheme.MONOCHROME) {
				Box(Modifier.background(Color(0xFFEEEAF9)).padding(20.dp)) {
					AssistantBubble(vMood.copy(remarks = vMood.remarks + Remark("Budget « Plaisirs » dépassé de 150,00 €.", Mood.WORRIED, AssistantTarget.BUDGET) + Remark("Objectif « Matelas » atteint.", Mood.HAPPY, AssistantTarget.GOALS)), "Une nouvelle version de PatrimoinePlus est disponible.") {}
				}
			}
		}																					// bulle
		File("build/captures-desktop/34-assistant-bulle.png").writeBytes(vBubble)
	}
}
