package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.data.backup.DeviceLinkDto
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

private val kNodeW         = 170.dp																// largeur d'un appareil
private val kNodeH         = 60.dp																// hauteur d'un appareil
private val kLabelW        = 150.dp																// largeur de la date d'un lien
private val kDiagramH      = 420.dp																// hauteur du schéma
private val kLinkTime      = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm")					// date d'une synchronisation
private const val kStaleDays = 7L																// au-delà, lien en pointillés
private const val kFloatMs   = 8_000															// durée d'un cycle de flottement
private const val kFloatY    = 6f																// amplitude verticale du flottement (dp)
private const val kFloatX    = 3f																// amplitude horizontale du flottement (dp)
private const val kBend      = 0.12f															// courbure des liens (part de leur longueur)

/*
	Appareil du schéma.
	id    : identifiant
	name  : nom
	phone : vrai pour un téléphone
	self  : vrai pour ce PC
	x     : centre au repos, abscisse (dp)
	y     : centre au repos, ordonnée (dp)
	phase : décalage du flottement (radians), propre à l'appareil
*/
private data class Node(
	val id    : String,
	val name  : String,
	val phone : Boolean,
	val self  : Boolean,
	val x     : Float,
	val y     : Float,
	val phase : Float
)
{
	/*
		Centre à un instant du flottement (dp) : léger mouvement vertical, et
		plus léger encore à l'horizontale, au double de la fréquence (la boucle
		reste continue).
		inTime : avancement du cycle, de 0 à 1
	*/
	fun at(inTime: Float): Offset
	{
		val vAngle = 2f * PI.toFloat() * inTime + phase											// angle du cycle
		return Offset(x + kFloatX * sin(2f * vAngle + phase), y + kFloatY * sin(vAngle))
	}
}

/*
	Schéma de l'écosystème, d'après les couples réunis par le fichier
	d'échange : téléphone au centre (plusieurs : en petit cercle), PC en
	cercle autour (ellipse à la largeur de la carte, le premier en haut),
	liens en courbes douces avec la date de la dernière synchronisation
	(pointillés jamais ou plus d'une semaine sans synchronisation) ; ce PC mis
	en avant. Chaque appareil flotte doucement, à son rythme, les liens le
	suivent ; l'animation n'est lue qu'au dessin (aucune recomposition par
	image). Les couples retirés ne sont pas montrés.
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
	val vLinks = inLinks.filter { !it.removed }														// couples actifs
	if (vLinks.isEmpty())
	{
		Text("Aucun appareil appairé pour l’instant : le schéma apparaîtra après le premier appairage.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		return
	}
	val vTime   = rememberInfiniteTransition(label = "flottement").animateFloat(0f, 1f, infiniteRepeatable(tween(kFloatMs, easing = LinearEasing), RepeatMode.Restart), label = "cycle")	// avancement du flottement
	val vColors = MaterialTheme.colorScheme															// palette courante
	val vDensity = LocalDensity.current																// densité (dp → px)
	BoxWithConstraints(Modifier.fillMaxWidth().height(kDiagramH).semantics { contentDescription = "Schéma des appareils : " + vLinks.joinToString { "${it.pcName} avec ${it.phoneName}" } }) {
		val vW      = maxWidth.value																	// largeur (dp)
		val vH      = kDiagramH.value																// hauteur (dp)
		val vNodes  = layout(vLinks, inSelfId, vW, vH)												// appareils placés
		val vById   = vNodes.associateBy { it.id }													// appareils par identifiant
		Canvas(Modifier.fillMaxSize()) {
			val vT = vTime.value																		// instant du flottement
			vLinks.forEach { inLink ->
				val vPc    = vById[inLink.pcId]?.at(vT) ?: return@forEach								// PC
				val vPhone = vById[inLink.phoneId]?.at(vT) ?: return@forEach							// téléphone
				val vStale = inLink.lastSync.let { it == null || inNow - it > kStaleDays * 86_400_000L }	// ancien ou jamais synchronisé
				drawPath(
					path  = curve(vPc * density, vPhone * density),
					color = if (vStale) vColors.outline else vColors.primary.copy(alpha = 0.75f),
					style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = if (vStale) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx())) else null)
				)
			}
		}
		vLinks.forEach { inLink ->
			val vPc    = vById[inLink.pcId] ?: return@forEach											// PC
			val vPhone = vById[inLink.phoneId] ?: return@forEach										// téléphone
			Text(
				text      = inLink.lastSync?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(kLinkTime) } ?: "jamais synchronisés",
				style     = MaterialTheme.typography.labelMedium,
				color     = vColors.onSurfaceVariant,
				textAlign = TextAlign.Center,
				modifier  = Modifier.offset {
					val vMid = middle(vPc.at(vTime.value), vPhone.at(vTime.value))						// milieu de la courbe (dp)
					IntOffset(((vMid.x - kLabelW.value / 2) * vDensity.density).roundToInt(), ((vMid.y - 10f) * vDensity.density).roundToInt())
				}.width(kLabelW).wrapContentWidth().clip(RoundedCornerShape(50)).background(vColors.surface).padding(horizontal = 8.dp, vertical = 2.dp)	// pastille : la date reste lisible sur le trait
			)
		}
		vNodes.forEach { inNode ->
			DeviceNode(inNode, vTime)
		}
	}
}

/*
	Place les appareils : téléphones au centre (plusieurs : en petit cercle),
	PC sur une ellipse autour, à intervalles réguliers, le premier en haut
	(nombre pair : décalés d'un demi-pas, en diagonale) ; un seul PC : à gauche. Chaque appareil reçoit son propre décalage de
	flottement.
	inLinks  : couples actifs
	inSelfId : identifiant de ce PC
	inW      : largeur (dp)
	inH      : hauteur (dp)
*/
private fun layout(
	inLinks  : List<DeviceLinkDto>,
	inSelfId : String,
	inW      : Float,
	inH      : Float
): List<Node>
{
	val vCx     = inW / 2f																			// centre, abscisse
	val vCy     = inH / 2f																			// centre, ordonnée
	val vPhones = inLinks.distinctBy { it.phoneId }													// téléphones
	val vPcs    = inLinks.distinctBy { it.pcId }													// PC
	val vRx     = inW / 2f - kNodeW.value / 2f - 8f													// rayon horizontal de l'ellipse
	val vRy     = inH / 2f - kNodeH.value / 2f - 8f													// rayon vertical
	val vPhase  = { inIndex: Int -> inIndex * 2.399f }												// décalages bien répartis (angle d'or)
	val vInner  = if (vPhones.size > 1) min(vRx, vRy) * 0.28f else 0f								// rayon du cercle des téléphones
	val vPhoneNodes = vPhones.mapIndexed { inIndex, inLink ->
		val vA = -PI.toFloat() / 2f + inIndex * 2f * PI.toFloat() / vPhones.size					// angle
		Node(inLink.phoneId, inLink.phoneName, true, false, vCx + vInner * cos(vA), vCy + vInner * sin(vA), vPhase(inIndex))
	}
	val vPcNodes = vPcs.mapIndexed { inIndex, inLink ->
		val vStart = -PI.toFloat() / 2f + if (vPcs.size % 2 == 0) PI.toFloat() / vPcs.size else 0f	// départ : en haut, ou un demi-pas plus loin pour un nombre pair (pas de croix)
		val vA = if (vPcs.size == 1) PI.toFloat() else vStart + inIndex * 2f * PI.toFloat() / vPcs.size	// angle
		Node(inLink.pcId, inLink.pcName, false, inLink.pcId == inSelfId, vCx + vRx * cos(vA), vCy + vRy * sin(vA), vPhase(inIndex + vPhones.size))
	}
	return vPcNodes + vPhoneNodes
}

/*
	Courbe douce entre deux centres (px) : point de contrôle écarté du milieu,
	perpendiculairement, d'une part de la longueur.
	inFrom : départ
	inTo   : arrivée
*/
private fun curve(
	inFrom : Offset,
	inTo   : Offset
): Path
{
	val vControl = control(inFrom, inTo)																// point de contrôle
	return Path().apply {
		moveTo(inFrom.x, inFrom.y)
		quadraticTo(vControl.x, vControl.y, inTo.x, inTo.y)
	}
}

/*
	Point de contrôle d'une courbe : milieu écarté perpendiculairement.
	inFrom : départ
	inTo   : arrivée
*/
private fun control(
	inFrom : Offset,
	inTo   : Offset
): Offset
{
	val vDx  = inTo.x - inFrom.x																		// écart horizontal
	val vDy  = inTo.y - inFrom.y																		// écart vertical
	val vLen = sqrt(vDx * vDx + vDy * vDy).coerceAtLeast(1f)										// longueur
	return Offset((inFrom.x + inTo.x) / 2f - vDy / vLen * vLen * kBend, (inFrom.y + inTo.y) / 2f + vDx / vLen * vLen * kBend)
}

/*
	Milieu d'une courbe (pour sa date), dans la même unité que ses bouts.
	inFrom : départ
	inTo   : arrivée
*/
private fun middle(
	inFrom : Offset,
	inTo   : Offset
): Offset
{
	val vControl = control(inFrom, inTo)																// point de contrôle
	return Offset(0.25f * inFrom.x + 0.5f * vControl.x + 0.25f * inTo.x, 0.25f * inFrom.y + 0.5f * vControl.y + 0.25f * inTo.y)
}

/*
	Appareil du schéma, à sa place du moment (flottement) : nom, nature ; ce
	PC mis en avant, le téléphone bordé de la couleur principale.
	inNode : appareil
	inTime : avancement du flottement
*/
@Composable
private fun DeviceNode(
	inNode : Node,
	inTime : State<Float>
)
{
	val vColors  = MaterialTheme.colorScheme															// palette courante
	val vShape   = RoundedCornerShape(20.dp)															// forme
	val vDensity = LocalDensity.current																	// densité (dp → px)
	Row(
		modifier              = Modifier
			.offset {
				val vAt = inNode.at(inTime.value)															// centre du moment (dp)
				IntOffset(((vAt.x - kNodeW.value / 2) * vDensity.density).roundToInt(), ((vAt.y - kNodeH.value / 2) * vDensity.density).roundToInt())
			}
			.width(kNodeW)
			.height(kNodeH)
			.shadow(if (inNode.self || inNode.phone) 10.dp else 4.dp, vShape, clip = false)
			.clip(vShape)
			.background(if (inNode.self) vColors.primary else vColors.surfaceContainerHigh)
			.border(1.5.dp, if (inNode.phone) vColors.primary else vColors.outlineVariant, vShape)
			.padding(horizontal = 12.dp),
		verticalAlignment     = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp)
	) {
		Icon(LineIcons.Devices, contentDescription = null, tint = if (inNode.self) vColors.onPrimary else vColors.onSurface, modifier = Modifier.size(22.dp))
		Column {
			Text(inNode.name, style = MaterialTheme.typography.titleSmall, color = if (inNode.self) vColors.onPrimary else vColors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(if (inNode.self) "Ce PC" else if (inNode.phone) "Téléphone (relais)" else "PC", style = MaterialTheme.typography.labelSmall, color = if (inNode.self) vColors.onPrimary else vColors.onSurfaceVariant)
		}
	}
}
