package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

private val kWideChart    = 520.dp												// largeur dès laquelle le cercle se place à côté de la légende
private val kPieSide      = 180.dp												// diamètre du cercle à côté de la légende
private val kPieMin       = 140.dp												// diamètre du cercle au-dessus de la légende
private val kToggleSize   = 32.dp												// zone de l'icône de bascule

/*
	Forme de chaque diagramme de répartition, choisie par l'utilisateur et
	mémorisée par l'appli : chaque diagramme a sa clé (tableau de bord,
	répartition des titres d'un compte…) et sa forme, indépendante des autres.
	pies     : clés des diagrammes affichés en cercle ; les autres sont en barre
	onChange : enregistre le choix d'un diagramme (clé, vrai pour le cercle)
*/
data class ProportionStyle(
	val pies     : Set<String>               = emptySet(),
	val onChange : (String, Boolean) -> Unit = { _, _ -> }
)
{
	/*
		Vrai si le diagramme est affiché en cercle.
		inKey : clé du diagramme
	*/
	fun isPie(inKey: String): Boolean = inKey in pies
}

// Forme des diagrammes de répartition, fournie par l'appli.
val LocalProportionStyle = staticCompositionLocalOf { ProportionStyle() }

/*
	Diagramme de répartition basculable : barre horizontale ou cercle
	(anneau), mêmes segments dans le même ordre et les mêmes teintes, donc la
	même légende ; l'icône ProportionToggle, placée sur la ligne du titre de
	la tuile ou de la section, bascule d'une forme à l'autre pour tous les
	diagrammes de l'appli. Le cercle se place à côté de la
	légende quand la largeur le permet, au-dessus sinon. Stable : les deux
	formes sont mesurées et la plus haute fixe la hauteur, si bien que
	basculer ne change pas la taille de la tuile.
	inKey         : clé du diagramme (sa forme est mémorisée à part)
	inSegments    : parts, dans l'ordre d'affichage (barre de gauche à droite, cercle dans le sens horaire depuis midi)
	inDescription : description pour les lecteurs d'écran
	inModifier    : place du diagramme
	inStable      : hauteur commune aux deux formes (tuiles) ; faux : chaque forme prend sa hauteur
	inLegend      : légende placée avec le diagramme ; null si elle est ailleurs
*/
@Composable
fun ProportionChart(
	inKey         : String,
	inSegments    : List<BarSegment>,
	inDescription : String,
	inModifier    : Modifier                  = Modifier,
	inStable      : Boolean                   = true,
	inLegend      : (@Composable () -> Unit)? = null
)
{
	val vPie   = LocalProportionStyle.current.isPie(inKey)							// forme choisie pour ce diagramme
	if (!inStable)
	{
		Box(inModifier.fillMaxWidth()) {
			if (vPie) PieForm(inSegments, inDescription, false, kPieMin, inLegend) else BarForm(inSegments, inDescription, inLegend)
		}
		return
	}
	var vWide by remember { mutableStateOf(false) }									// cercle à côté de la légende (largeur mesurée)
	val vSideBy = inLegend != null																// légende avec le diagramme
	Layout(
		content  = {
			Box(if (vPie) Modifier.clearAndSetSemantics {} else Modifier) { BarForm(inSegments, inDescription, inLegend) }
			Box(if (vPie && vWide) Modifier else Modifier.clearAndSetSemantics {}) { PieForm(inSegments, inDescription, true, kPieSide, inLegend) }
			Box(if (vPie && !vWide) Modifier else Modifier.clearAndSetSemantics {}) { PieForm(inSegments, inDescription, false, kPieMin, inLegend) }
		},
		modifier = inModifier.fillMaxWidth()
	) { inMeasurables, inConstraints ->
		val vFree   = inConstraints.copy(minHeight = 0)								// hauteur libre
		val vIsWide = vSideBy && inConstraints.maxWidth >= kWideChart.roundToPx()		// place pour le cercle à côté
		if (vIsWide != vWide) vWide = vIsWide
		val vBar    = inMeasurables[0].measure(vFree)								// forme barre
		val vRing   = inMeasurables[if (vIsWide) 1 else 2].measure(vFree)			// forme cercle retenue
		val vHeight = maxOf(vBar.height, vRing.height)								// hauteur commune : la plus haute des deux formes
		layout(inConstraints.maxWidth, vHeight) {
			(if (vPie) vRing else vBar).place(0, 0)
		}
	}
}

/*
	Forme barre : la barre, puis la légende.
	inSegments    : parts
	inDescription : description
	inLegend      : légende ; null sans légende
*/
@Composable
private fun BarForm(
	inSegments    : List<BarSegment>,
	inDescription : String,
	inLegend      : (@Composable () -> Unit)?
)
{
	Column(Modifier.fillMaxWidth()) {
		ProportionBar(inSegments, inDescription)
		if (inLegend != null)
		{
			Spacer(Modifier.height(12.dp))
			inLegend()
		}
	}
}

/*
	Forme cercle : l'anneau à gauche et la légende à droite (large), ou
	l'anneau centré au-dessus de la légende (étroit).
	inSegments    : parts
	inDescription : description
	inWide        : place pour l'anneau à côté de la légende
	inSide        : diamètre de l'anneau
	inLegend      : légende ; null sans légende
*/
@Composable
private fun PieForm(
	inSegments    : List<BarSegment>,
	inDescription : String,
	inWide        : Boolean,
	inSide        : Dp,
	inLegend      : (@Composable () -> Unit)?
)
{
	if (inWide && inLegend != null)
	{
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
			PieChart(inSegments, inDescription, Modifier.size(inSide))
			Box(Modifier.weight(1f)) { inLegend() }
		}
		return
	}
	Column(Modifier.fillMaxWidth()) {
		Box(Modifier.fillMaxWidth()) {
			PieChart(inSegments, inDescription, Modifier.size(inSide).align(Alignment.Center))
		}
		if (inLegend != null)
		{
			Spacer(Modifier.height(12.dp))
			inLegend()
		}
	}
}

/*
	Anneau de répartition : un arc par part, dans le sens horaire depuis midi,
	séparés par un petit espace ; rail neutre sans part positive.
	inSegments    : parts
	inDescription : description
	inModifier    : taille de l'anneau
*/
@Composable
fun PieChart(
	inSegments    : List<BarSegment>,
	inDescription : String,
	inModifier    : Modifier
)
{
	val vTrack = MaterialTheme.colorScheme.surfaceContainerHigh						// couleur du rail
	Canvas(inModifier.semantics { contentDescription = inDescription }) {
		val vStroke   = size.minDimension * 0.18f										// épaisseur de l'anneau
		val vInset    = vStroke / 2f													// marge pour le trait
		val vBox      = Size(size.minDimension - vStroke, size.minDimension - vStroke)	// carré du cercle
		val vTopLeft  = Offset((size.width - size.minDimension) / 2f + vInset, (size.height - size.minDimension) / 2f + vInset)	// coin du cercle
		val vPositive = inSegments.filter { it.value > 0 }							// parts dessinables
		val vTotal    = vPositive.sumOf { it.value }.toFloat()						// somme des poids
		if (vTotal <= 0f)
		{
			drawArc(vTrack, 0f, 360f, false, vTopLeft, vBox, style = Stroke(vStroke))
			return@Canvas
		}
		val vGap   = if (vPositive.size > 1) 2f else 0f								// espace entre arcs, en degrés
		var vStart = -90f																// midi
		vPositive.forEach { inSegment ->
			val vSweep = 360f * inSegment.value / vTotal								// angle de la part
			drawArc(inSegment.color, vStart + vGap / 2f, (vSweep - vGap).coerceAtLeast(0.5f), false, vTopLeft, vBox, style = Stroke(vStroke))
			vStart += vSweep
		}
	}
}

/*
	Icône de bascule d'un diagramme de répartition, à placer sur la ligne du
	titre de la tuile ou de la section : montre la forme vers laquelle on
	bascule (petit anneau en mode barre, petite barre en mode cercle).
	inKey : clé du diagramme
*/
@Composable
fun ProportionToggle(inKey: String)
{
	val vStyle = LocalProportionStyle.current										// formes choisies
	val vPie   = vStyle.isPie(inKey)												// forme de ce diagramme
	val vInk = MaterialTheme.colorScheme.onSurfaceVariant							// teinte de l'icône
	IconButton(
		onClick  = { vStyle.onChange(inKey, !vPie) },
		modifier = Modifier.size(kToggleSize).semantics { contentDescription = if (vPie) "Afficher en barre" else "Afficher en diagramme circulaire" }
	) {
		Canvas(Modifier.size(18.dp)) {
			if (vPie)
			{
				val vHeight = size.height * 0.36f										// épaisseur de la barre
				drawRoundRect(vInk, Offset(0f, (size.height - vHeight) / 2f), Size(size.width * 0.58f, vHeight), CornerRadius(vHeight / 2f))
				drawRoundRect(vInk.copy(alpha = 0.45f), Offset(size.width * 0.66f, (size.height - vHeight) / 2f), Size(size.width * 0.34f, vHeight), CornerRadius(vHeight / 2f))
			}
			else
			{
				val vStroke = size.minDimension * 0.24f									// épaisseur de l'anneau
				val vBox    = Size(size.width - vStroke, size.height - vStroke)		// carré du cercle
				drawArc(vInk, -90f, 230f, false, Offset(vStroke / 2f, vStroke / 2f), vBox, style = Stroke(vStroke))
				drawArc(vInk.copy(alpha = 0.45f), 150f, 120f, false, Offset(vStroke / 2f, vStroke / 2f), vBox, style = Stroke(vStroke))
			}
		}
	}
}
