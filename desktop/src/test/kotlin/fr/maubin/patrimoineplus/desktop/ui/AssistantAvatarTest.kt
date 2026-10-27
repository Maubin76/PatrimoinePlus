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
import fr.maubin.patrimoineplus.ui.assistant.AssistantTarget
import fr.maubin.patrimoineplus.ui.assistant.Mood
import fr.maubin.patrimoineplus.ui.assistant.Remark
import fr.maubin.patrimoineplus.ui.assistant.assistantMood
import fr.maubin.patrimoineplus.ui.components.AssistantAvatar
import fr.maubin.patrimoineplus.ui.components.AvatarReaction
import fr.maubin.patrimoineplus.ui.components.LocalPointer
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.ui.theme.DeskTheme
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/*
	Assistant (dessin) : planches pour relecture, sur fond clair et sombre
	(humeurs, regard à gauche et à droite, yeux fermés du mode discret,
	survolé), images prises en pleine animation (sursaut, joie, hochement,
	regard vers le haut) et bulle ; le regard change bien l'image.
	Captures `34-assistant*.png`.
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
		var vTime  = 0L																	// instant courant
		while (vTime < inMillis) { vScene.render(vTime * 1_000_000L); vTime += 16 }
		val vBytes = vScene.render(inMillis * 1_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes	// image
		vScene.close()
		return vBytes
	}

	/*
		Planche d'états, sur un fond.
		inName   : nom de la capture
		inBack   : fond
		inMillis : instant du rendu
		inStates : états
	*/
	private fun board(
		inName   : String,
		inBack   : Color,
		inMillis : Long,
		inStates : List<@Composable () -> Unit>
	)
	{
		File("build/captures-desktop").mkdirs()
		File("build/captures-desktop/$inName.png").writeBytes(render(inStates.size * 190 + 20, inMillis) {
			Row(Modifier.background(inBack).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
				inStates.forEach { inState -> Box(Modifier.padding(top = 10.dp)) { inState() } }
			}
		})
	}

	/*
		Planches des expressions et des animations.
	*/
	@Test
	fun expressions()
	{
		val vStates = listOf<@Composable () -> Unit>(
			{ AssistantAvatar(170.dp, Mood.HAPPY) },
			{ AssistantAvatar(170.dp, Mood.NEUTRAL) },
			{ AssistantAvatar(170.dp, Mood.WORRIED) },
			{ CompositionLocalProvider(LocalPointer provides { Offset(0f, 100f) }) { AssistantAvatar(170.dp, Mood.NEUTRAL) } },
			{ CompositionLocalProvider(LocalPointer provides { Offset(5_000f, 0f) }) { AssistantAvatar(170.dp, Mood.HAPPY) } },
			{ AssistantAvatar(170.dp, Mood.HAPPY, inEyesClosed = true) }
		)																				// états fixes
		board("34-assistant", Color(0xFFEEEAF9), 0, vStates)
		board("34-assistant-sombre", Color(0xFF16162B), 0, vStates)
		board("34-assistant-survol", Color(0xFF16162B), 400, listOf({ AssistantAvatar(170.dp, Mood.NEUTRAL, inHovered = true) }))
		val vMoves = listOf(
			AvatarReaction.STARTLE to 60L, AvatarReaction.STARTLE to 260L, AvatarReaction.STARTLE to 660L,
			AvatarReaction.JOY to 240L, AvatarReaction.JOY to 700L, AvatarReaction.LOOK_UP to 500L
		)																				// réactions et instants (prise d'élan, envol, atterrissage, joie, regard en haut)
		File("build/captures-desktop").mkdirs()
		vMoves.forEachIndexed { inIndex, (inKind, inMillis) ->
			File("build/captures-desktop/34-assistant-mouvement-$inIndex.png").writeBytes(render(210, inMillis, 220) {
				Box(Modifier.background(Color(0xFF16162B)).padding(20.dp)) { AssistantAvatar(170.dp, Mood.HAPPY, inReaction = inKind to 1L) }
			})
		}
		val vLeft  = render(200, 0) { CompositionLocalProvider(LocalPointer provides { Offset(0f, 100f) }) { AssistantAvatar(150.dp, Mood.NEUTRAL) } }	// regard à gauche
		val vRight = render(200, 0) { CompositionLocalProvider(LocalPointer provides { Offset(5_000f, 100f) }) { AssistantAvatar(150.dp, Mood.NEUTRAL) } }	// regard à droite
		assertNotEquals(vLeft.toList(), vRight.toList())
		val vMood   = assistantMood(-12_000, null, emptyList())								// humeur inquiète
		File("build/captures-desktop/34-assistant-bulle.png").writeBytes(render(420, 700, 360) {
			DeskTheme(inDarkTheme = false, inColorTheme = ColorTheme.MONOCHROME) {
				Box(Modifier.background(Color(0xFFEEEAF9)).padding(20.dp)) {
					AssistantBubble(vMood.copy(remarks = listOf(Remark("Vos petits plaisirs ont un peu débordé du budget ce mois-ci.", Mood.WORRIED, AssistantTarget.BUDGET)) + vMood.remarks + Remark("L’objectif « Matelas » est atteint, bravo !", Mood.HAPPY, AssistantTarget.GOALS)), "Psst… une nouvelle version de PatrimoinePlus est arrivée !") {}
				}
			}
		})
	}
}
