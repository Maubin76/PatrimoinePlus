package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.backup.DeviceLinkDto
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val kNodeW      = 170.dp																// largeur d'un appareil
private val kNodeH      = 64.dp																	// hauteur d'un appareil
private val kRowH       = 92.dp																	// hauteur d'une ligne de PC
private val kLinkTime   = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm")						// date d'une synchronisation
private const val kStaleDays = 7L																// au-delà, couple affiché en pointillés

/*
	Appareil du schéma.
	id    : identifiant
	name  : nom
	phone : vrai pour un téléphone
	self  : vrai pour ce PC
*/
private data class Node(
	val id    : String,
	val name  : String,
	val phone : Boolean,
	val self  : Boolean
)

/*
	Schéma de l'écosystème, d'après les couples réunis par le fichier
	d'échange : téléphones au centre, PC répartis à gauche et à droite, un
	trait par couple avec la date de la dernière synchronisation (en
	pointillés jamais ou plus d'une semaine sans synchronisation) ; ce PC mis
	en avant. Les couples retirés ne sont pas montrés.
	inLinks  : couples d'appareils
	inSelfId : identifiant de ce PC
	inNow    : instant courant (ms)
*/
@Composable
fun EcosystemDiagram(
	inLinks  : List<DeviceLinkDto>,
	inSelfId : String,
	inNow    : Long
)
{
	val vLinks  = inLinks.filter { !it.removed }													// couples actifs
	if (vLinks.isEmpty())
	{
		Text("Aucun appareil appairé pour l’instant : le schéma apparaîtra après le premier appairage.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		return
	}
	val vPhones = vLinks.distinctBy { it.phoneId }.map { Node(it.phoneId, it.phoneName, true, false) }	// téléphones
	val vPcs    = vLinks.distinctBy { it.pcId }.map { Node(it.pcId, it.pcName, false, it.pcId == inSelfId) }	// PC
	val vLeft   = vPcs.filterIndexed { inIndex, _ -> inIndex % 2 == 0 }								// PC à gauche
	val vRight  = vPcs.filterIndexed { inIndex, _ -> inIndex % 2 == 1 }								// PC à droite
	val vRows   = maxOf(vLeft.size, vRight.size, vPhones.size)										// lignes
	val vHeight = kRowH * vRows																		// hauteur du schéma
	val vColors = MaterialTheme.colorScheme															// palette courante
	BoxWithConstraints(Modifier.fillMaxWidth().height(vHeight).semantics { contentDescription = "Schéma des appareils : " + vLinks.joinToString { "${it.pcName} avec ${it.phoneName}" } }) {
		val vWidth  = maxWidth																		// largeur disponible
		val vPlaces = mutableMapOf<String, Pair<Dp, Dp>>()										// coin haut-gauche de chaque appareil
		/*
			Place une colonne d'appareils, centrée verticalement.
			inNodes : appareils
			inX     : abscisse de la colonne
		*/
		fun column(inNodes: List<Node>, inX: Dp)
		{
			val vTop = (vHeight - kRowH * inNodes.size) / 2										// haut de la colonne
			inNodes.forEachIndexed { inIndex, inNode -> vPlaces[inNode.id] = inX to (vTop + kRowH * inIndex + (kRowH - kNodeH) / 2) }
		}
		column(vLeft, 0.dp)
		column(vPhones, (vWidth - kNodeW) / 2)
		column(vRight, vWidth - kNodeW)
		Canvas(Modifier.fillMaxSize()) {
			vLinks.forEach { inLink ->
				val vPc    = vPlaces[inLink.pcId] ?: return@forEach									// PC
				val vPhone = vPlaces[inLink.phoneId] ?: return@forEach								// téléphone
				val vLeftSide = vPc.first < vPhone.first												// PC à gauche du téléphone
				val vFrom  = Offset((vPc.first + if (vLeftSide) kNodeW else 0.dp).toPx(), (vPc.second + kNodeH / 2).toPx())	// bord du PC
				val vTo    = Offset((vPhone.first + if (vLeftSide) 0.dp else kNodeW).toPx(), (vPhone.second + kNodeH / 2).toPx())	// bord du téléphone
				val vStale = inLink.lastSync.let { it == null || inNow - it > kStaleDays * 86_400_000L }	// ancien ou jamais synchronisé
				drawLine(
					color       = if (vStale) vColors.outline else vColors.primary,
					start       = vFrom,
					end         = vTo,
					strokeWidth = 2.dp.toPx(),
					pathEffect  = if (vStale) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) else null
				)
			}
		}
		vLinks.forEach { inLink ->
			val vPc    = vPlaces[inLink.pcId] ?: return@forEach										// PC
			val vPhone = vPlaces[inLink.phoneId] ?: return@forEach									// téléphone
			val vX     = (minOf(vPc.first, vPhone.first) + kNodeW + maxOf(vPc.first, vPhone.first)) / 2	// milieu du trait
			val vY     = (vPc.second + vPhone.second) / 2 + kNodeH / 2								// hauteur du milieu
			Text(
				text      = inLink.lastSync?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(kLinkTime) } ?: "jamais synchronisés",
				style     = MaterialTheme.typography.labelMedium,
				color     = vColors.onSurfaceVariant,
				textAlign = TextAlign.Center,
				modifier  = Modifier.width(130.dp).offset(vX - 65.dp, vY - 22.dp)
			)
		}
		(vPcs + vPhones).forEach { inNode ->
			val vPlace = vPlaces[inNode.id] ?: return@forEach											// coin haut-gauche
			DeviceNode(inNode, Modifier.offset(vPlace.first, vPlace.second))
		}
	}
}

/*
	Appareil du schéma : nom, nature ; ce PC mis en avant.
	inNode     : appareil
	inModifier : position
*/
@Composable
private fun DeviceNode(
	inNode     : Node,
	inModifier : Modifier
)
{
	val vColors = MaterialTheme.colorScheme															// palette courante
	val vShape  = RoundedCornerShape(18.dp)															// forme
	Row(
		modifier          = inModifier
			.width(kNodeW)
			.height(kNodeH)
			.clip(vShape)
			.background(if (inNode.self) vColors.primary else vColors.surfaceContainerHigh)
			.border(1.dp, if (inNode.phone) vColors.primary else vColors.outlineVariant, vShape)
			.padding(horizontal = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp)
	) {
		Icon(LineIcons.Devices, contentDescription = null, tint = if (inNode.self) vColors.onPrimary else vColors.onSurface, modifier = Modifier.size(22.dp))
		Column {
			Text(inNode.name, style = MaterialTheme.typography.titleSmall, color = if (inNode.self) vColors.onPrimary else vColors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(if (inNode.self) "Ce PC" else if (inNode.phone) "Téléphone (relais)" else "PC", style = MaterialTheme.typography.labelSmall, color = if (inNode.self) vColors.onPrimary else vColors.onSurfaceVariant)
		}
	}
}
