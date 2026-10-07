package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/*
	Colonne dont on réordonne les lignes par glisser-déposer : appui long sur
	une ligne, puis déplacement du doigt ; l'ordre est remis à l'appelant au
	lâcher. Pour TalkBack, chaque ligne propose aussi « Monter » et
	« Descendre ». Destinée aux listes courtes (comptes, catégories).
	inItems     : éléments, dans l'ordre courant
	inKey       : clé stable d'un élément
	inOnReorder : reçoit le nouvel ordre après un déplacement
	inRow       : contenu d'une ligne
*/
@Composable
fun <T> ReorderableColumn(
	inItems     : List<T>,
	inKey       : (T) -> String,
	inOnReorder : (List<T>) -> Unit,
	inRow       : @Composable (T) -> Unit
)
{
	val vHaptics  = LocalHapticFeedback.current								// retour haptique
	var vOrder    by remember { mutableStateOf(inItems) }					// ordre affiché
	var vDragging by remember { mutableStateOf<String?>(null) }			// clé de la ligne déplacée
	val vOffset   = remember { mutableFloatStateOf(0f) }					// décalage vertical du doigt
	val vHeights  = remember { mutableStateMapOf<String, Int>() }			// hauteur de chaque ligne
	val vItems    by rememberUpdatedState(inItems)							// éléments les plus récents
	if (vDragging == null && vOrder != inItems)
	{
		vOrder = inItems														// hors glissement, suit les données
	}

	/*
		Déplace un élément d'une position et renvoie le nouvel ordre.
		inFrom : position de départ
		inTo   : position d'arrivée
	*/
	fun moved(inFrom: Int, inTo: Int): List<T> =
		vOrder.toMutableList().apply { add(inTo, removeAt(inFrom)) }

	Column {
		vOrder.forEachIndexed { inIndex, inItem ->
			val vKey      = inKey(inItem)										// clé de la ligne
			val vIsMoving = vKey == vDragging									// ligne en cours de déplacement
			key(vKey) {
				if (inIndex > 0)
				{
					HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
				}
				Surface(
					color           = MaterialTheme.colorScheme.surface,
					shadowElevation = if (vIsMoving) 6.dp else 0.dp,
					modifier        = Modifier
						.zIndex(if (vIsMoving) 1f else 0f)
						.graphicsLayer { translationY = if (vIsMoving) vOffset.floatValue else 0f }
						.onSizeChanged { vHeights[vKey] = it.height }
						.semantics {
							customActions = listOfNotNull(
								if (inIndex > 0) CustomAccessibilityAction("Monter") { inOnReorder(moved(inIndex, inIndex - 1)); true } else null,
								if (inIndex < vOrder.size - 1) CustomAccessibilityAction("Descendre") { inOnReorder(moved(inIndex, inIndex + 1)); true } else null
							)
						}
						.pointerInput(vKey) {
							detectDragGesturesAfterLongPress(
								onDragStart  = {
									vDragging = vKey
									vOffset.floatValue = 0f
									vHaptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
								},
								onDrag       = { inChange, inAmount ->
									inChange.consume()
									vOffset.floatValue += inAmount.y
									val vAt = vOrder.indexOfFirst { inKey(it) == vKey }	// position courante
									if (vOffset.floatValue > 0 && vAt < vOrder.size - 1)
									{
										val vNext = vHeights[inKey(vOrder[vAt + 1])] ?: 0	// hauteur de la suivante
										if (vOffset.floatValue > vNext / 2f)
										{
											vOrder = moved(vAt, vAt + 1)
											vOffset.floatValue -= vNext
											vHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
										}
									}
									else if (vOffset.floatValue < 0 && vAt > 0)
									{
										val vPrevious = vHeights[inKey(vOrder[vAt - 1])] ?: 0	// hauteur de la précédente
										if (-vOffset.floatValue > vPrevious / 2f)
										{
											vOrder = moved(vAt, vAt - 1)
											vOffset.floatValue += vPrevious
											vHaptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
										}
									}
								},
								onDragEnd    = {
									vDragging = null
									vOffset.floatValue = 0f
									if (vOrder.map(inKey) != vItems.map(inKey))
									{
										inOnReorder(vOrder)
									}
								},
								onDragCancel = {
									vDragging = null
									vOffset.floatValue = 0f
									vOrder = vItems
								}
							)
						}
				) {
					inRow(inItem)
				}
			}
		}
	}
}
