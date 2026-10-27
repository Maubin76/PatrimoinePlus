package fr.maubin.patrimoineplus.desktop.ui

import fr.maubin.patrimoineplus.desktop.data.LoadStatus
import fr.maubin.patrimoineplus.ui.assistant.Mood
import fr.maubin.patrimoineplus.ui.components.AvatarReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Réactions de l'assistant aux changements d'état : conflit, fichier
	illisible, écriture impossible (sursaut, bulle ouverte d'elle-même) ;
	modifications à enregistrer et nouvelle version (regard vers la fenêtre
	du centre) ; objectif atteint et retour au vert (joie) ; enregistrement
	réussi (hochement) ; rien sinon ; phrases sans détail technique.
*/
class AssistantEventTest
{
	private val kCalm = Snapshot(LoadStatus.Synced(1L), false, false, Mood.NEUTRAL, emptySet())	// état de départ

	/*
		Chaque changement et sa réaction.
	*/
	@Test
	fun events()
	{
		val vConflict = assistantEvent(kCalm, kCalm.copy(status = LoadStatus.Conflict("PC", "fichier")))!!	// conflit
		assertEquals(AvatarReaction.STARTLE, vConflict.reaction)
		assertTrue(vConflict.urgent)
		assertEquals(LookTarget.CENTER, vConflict.look)
		val vInvalid = assistantEvent(kCalm, kCalm.copy(status = LoadStatus.Invalid("ligne 12 : date illisible")))!!	// fichier illisible
		assertTrue(vInvalid.urgent)
		assertFalse("détail technique : ${vInvalid.text}", vInvalid.text!!.contains("ligne 12"))
		assertTrue(assistantEvent(kCalm, kCalm.copy(status = LoadStatus.WriteFailed("AccessDenied")))!!.urgent)
		val vPending = assistantEvent(kCalm, kCalm.copy(pending = true))!!					// modifications à enregistrer
		assertEquals(AvatarReaction.STARTLE, vPending.reaction)
		assertFalse(vPending.urgent)
		assertEquals(LookTarget.CENTER, vPending.look)
		assertEquals(AvatarReaction.LOOK_UP, assistantEvent(kCalm, kCalm.copy(update = true))!!.reaction)
		val vGoal = assistantEvent(kCalm, kCalm.copy(reached = setOf("Matelas")))!!			// objectif atteint
		assertEquals(AvatarReaction.JOY, vGoal.reaction)
		assertTrue(vGoal.text!!.contains("« Matelas »"))
		assertEquals(AssistantEvent(AvatarReaction.JOY), assistantEvent(kCalm.copy(mood = Mood.WORRIED), kCalm.copy(mood = Mood.HAPPY)))
		assertEquals(AssistantEvent(AvatarReaction.NOD), assistantEvent(kCalm.copy(status = LoadStatus.Pending), kCalm))
		assertNull(assistantEvent(kCalm, kCalm))
		assertNull(assistantEvent(kCalm.copy(status = LoadStatus.Conflict("a", "b")), kCalm.copy(status = LoadStatus.Conflict("a", "b"))))	// conflit déjà signalé
		assertNull(assistantEvent(kCalm.copy(reached = setOf("Matelas")), kCalm))			// objectif qui n'est plus atteint : rien
		assertEquals(AvatarReaction.STARTLE, assistantEvent(kCalm, kCalm.copy(status = LoadStatus.Conflict("a", "b"), update = true))!!.reaction)	// le plus important d'abord
	}
}
