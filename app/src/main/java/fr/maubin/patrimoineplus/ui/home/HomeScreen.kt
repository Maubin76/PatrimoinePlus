package fr.maubin.patrimoineplus.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.BarSegment
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.components.EqualsKey
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.ProportionBar
import fr.maubin.patrimoineplus.ui.components.SeriesDot
import fr.maubin.patrimoineplus.ui.shell.EntryFab
import fr.maubin.patrimoineplus.ui.shell.PrivacyAction
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme

/*
	Écran d'accueil : patrimoine total, sa répartition par type de compte,
	et les comptes qui le composent.
	inOnToggleHidden   : bascule le mode discret
	inOnCreateAccounts : ouvre la gestion des comptes
	inViewModel        : ViewModel fourni par Hilt
*/
@Composable
fun HomeScreen(
	inOnToggleHidden   : () -> Unit,
	inOnCreateAccounts : () -> Unit,
	inViewModel        : HomeViewModel = hiltViewModel()
)
{
	val vState by inViewModel.uiState.collectAsStateWithLifecycle()		// état à afficher
	HomeContent(vState, inOnToggleHidden, inOnCreateAccounts)
}

/*
	Contenu de l'accueil, sans état propre (prévisualisable).
	inState            : état à afficher
	inOnToggleHidden   : bascule le mode discret
	inOnCreateAccounts : ouvre la gestion des comptes
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
	inState            : HomeUiState,
	inOnToggleHidden   : () -> Unit,
	inOnCreateAccounts : () -> Unit
)
{
	val vSnackbar = remember { SnackbarHostState() }						// messages éphémères
	ScreenScaffold(
		inTitle    = "Patrimoine",
		inSnackbar = vSnackbar,
		inActions  = { PrivacyAction(inOnToggleHidden) },
		inFab      = { EntryFab(vSnackbar) }
	) { inPadding ->
		LazyColumn(
			contentPadding = PaddingValues(
				start  = 16.dp,
				end    = 16.dp,
				top    = inPadding.calculateTopPadding() + 8.dp,
				bottom = inPadding.calculateBottomPadding() + 96.dp
			)
		) {
			item { TotalBlock(inState) }
			item { HorizontalDivider(Modifier.padding(top = 24.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant) }
			if (inState.isEmpty)
			{
				item {
					EmptyState(
						inTitle       = "Aucun compte à additionner",
						inBody        = "Le total se calcule à partir des comptes et de leurs opérations. " +
							"Commencez par créer les comptes à suivre.",
						inActionLabel = "Créer mes comptes",
						inOnAction    = inOnCreateAccounts
					)
				}
			}
			else
			{
				inState.groups.forEachIndexed { inIndex, inGroup ->
					if (inIndex > 0)
					{
						item { Spacer(Modifier.height(8.dp)) }
					}
					items(inGroup.lines) { inLine -> AccountRow(inLine, typeColor(inGroup.type)) }
					if (inGroup.lines.size > 1)
					{
						item { SubtotalRow(inGroup) }
					}
				}
			}
		}
	}
}

private const val kClearedDisplay = "– – –,– – €"						// afficheur remis à zéro : aucun compte

/*
	Bloc du total : touche « = », montant à la taille de l'afficheur, date de
	calcul, puis barre de répartition par type de compte. Sans compte,
	l'afficheur montre des tirets, comme une calculatrice remise à zéro.
	inState : état de l'accueil
*/
@Composable
private fun TotalBlock(inState: HomeUiState)
{
	val vColors = MaterialTheme.colorScheme								// palette courante
	val vHidden = LocalAmountsHidden.current								// mode discret actif
	val vShown  = when													// texte réellement affiché, pour le dimensionner
	{
		inState.isEmpty -> kClearedDisplay
		vHidden         -> kMaskedAmount
		else            -> formatEuros(inState.totalCents)
	}
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			EqualsKey(inSize = 36.dp)
			Spacer(Modifier.width(14.dp))
			BoxWithConstraints(Modifier.weight(1f)) {
				val vStyle = fittingStyle(vShown, constraints.maxWidth)		// plus grande taille qui tient
				if (inState.isEmpty)
				{
					Text(
						text     = kClearedDisplay,
						style    = vStyle,
						color    = vColors.onSurfaceVariant,
						maxLines = 1,
						modifier = Modifier.semantics { contentDescription = "Aucun total" }
					)
				}
				else
				{
					AmountText(inCents = inState.totalCents, inStyle = vStyle, inRolling = true)
				}
			}
		}
		Text(
			text  = if (inState.isEmpty) "Rien à calculer pour l'instant" else "Calculé au ${inState.asOf}",
			style = MaterialTheme.typography.bodyMedium,
			color = vColors.onSurfaceVariant
		)
		Spacer(Modifier.height(14.dp))
		ProportionBar(
			inSegments    = inState.groups.map { BarSegment(it.subtotalCents, typeColor(it.type)) },
			inDescription = repartitionDescription(inState, vHidden)
		)
	}
}

/*
	Choisit la plus grande taille d'afficheur pour laquelle le texte tient sur
	une ligne. La mesure tient compte de la taille de police du système.
	inText     : texte à afficher
	inMaxWidth : largeur disponible, en pixels
*/
@Composable
private fun fittingStyle(
	inText     : String,
	inMaxWidth : Int
): TextStyle
{
	val vMeasurer   = rememberTextMeasurer()								// mesure du texte
	val vTypography = MaterialTheme.typography								// échelle typographique
	val vCandidates = listOf(												// tailles, de la plus grande à la plus petite
		vTypography.displayLarge,
		vTypography.displayMedium,
		vTypography.displaySmall,
		vTypography.headlineLarge,
		vTypography.headlineMedium
	)
	return vCandidates.firstOrNull { inStyle ->
		vMeasurer.measure(inText, inStyle, maxLines = 1, softWrap = false).size.width <= inMaxWidth
	} ?: vCandidates.last()
}

/*
	Décrit la répartition pour TalkBack ; ne cite aucun montant en mode discret.
	inState  : état de l'accueil
	inHidden : vrai si le mode discret est actif
*/
private fun repartitionDescription(
	inState  : HomeUiState,
	inHidden : Boolean
): String
{
	if (inState.isEmpty || inHidden)
	{
		return "Répartition par type de compte"
	}
	val vTotal = inState.groups.sumOf { maxOf(it.subtotalCents, 0L) }	// base des pourcentages
	return "Répartition : " + inState.groups.joinToString(", ") { inGroup ->
		val vPercent = if (vTotal > 0) inGroup.subtotalCents.coerceAtLeast(0L) * 100 / vTotal else 0L	// part arrondie
		"${inGroup.type.label} $vPercent %"
	}
}

/*
	Couleur de série d'un type de compte : anthracite, olive, ocre.
	inType : type de compte
*/
@Composable
fun typeColor(inType: AccountType): Color =
	when (inType)
	{
		AccountType.CHECKING  -> MaterialTheme.colorScheme.onSurface
		AccountType.SAVINGS   -> MaterialTheme.colorScheme.secondary
		AccountType.BROKERAGE -> MaterialTheme.colorScheme.tertiary
	}

/*
	Ligne d'un compte, opérande de l'addition : point de type, nom, montant à droite.
	inLine  : compte affiché
	inColor : couleur de série du type de compte
*/
@Composable
private fun AccountRow(
	inLine  : HomeAccountLine,
	inColor : Color
)
{
	Row(
		modifier          = Modifier.fillMaxWidth().heightIn(min = 52.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		SeriesDot(inColor)
		Spacer(Modifier.width(14.dp))
		Text(
			text     = inLine.name,
			style    = MaterialTheme.typography.titleMedium,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f)
		)
		Spacer(Modifier.width(16.dp))
		AmountText(
			inCents = inLine.cents,
			inStyle = MaterialTheme.typography.titleMedium,
			inColor = if (inLine.cents < 0) MaterialTheme.colorScheme.error else Color.Unspecified
		)
	}
}

/*
	Sous-total d'un groupe de plusieurs comptes, en retrait et plus petit que
	les opérandes ; absent pour un groupe d'un seul compte, qui ferait doublon.
	inGroup : groupe concerné
*/
@Composable
private fun SubtotalRow(inGroup: HomeGroup)
{
	val vMuted = MaterialTheme.colorScheme.onSurfaceVariant				// texte secondaire
	Row(
		modifier          = Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(start = 24.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text     = "Total ${inGroup.type.label.lowercase()}",
			style    = MaterialTheme.typography.labelLarge,
			color    = vMuted,
			modifier = Modifier.weight(1f)
		)
		AmountText(inCents = inGroup.subtotalCents, inStyle = MaterialTheme.typography.labelLarge, inColor = vMuted)
	}
}

// Données de démonstration, uniquement pour les aperçus d'Android Studio (synthétiques).
internal val kPreviewState = HomeUiState(
	asOf   = "7 octobre 2026",
	groups = listOf(
		HomeGroup(AccountType.CHECKING, listOf(HomeAccountLine("Compte courant", 284_312))),
		HomeGroup(AccountType.SAVINGS, listOf(HomeAccountLine("Livret A", 2_295_000), HomeAccountLine("LDDS", 1_204_118))),
		HomeGroup(AccountType.BROKERAGE, listOf(HomeAccountLine("PEA", 3_871_540)))
	)
)

/*
	Aperçu de l'accueil avec des comptes (données synthétiques).
*/
@Preview(name = "Accueil — clair", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun HomePreview()
{
	PatrimoineTheme(inDarkTheme = false) { HomeContent(kPreviewState, {}, {}) }
}

/*
	Aperçu de l'accueil en mode sombre (données synthétiques).
*/
@Preview(name = "Accueil — sombre", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun HomeDarkPreview()
{
	PatrimoineTheme(inDarkTheme = true) { HomeContent(kPreviewState, {}, {}) }
}

/*
	Aperçu de l'accueil en mode discret.
*/
@Preview(name = "Accueil — discret", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun HomeHiddenPreview()
{
	PatrimoineTheme(inDarkTheme = false) {
		CompositionLocalProvider(LocalAmountsHidden provides true) { HomeContent(kPreviewState, {}, {}) }
	}
}

/*
	Aperçu de l'accueil sans compte : l'état réel tant que la base est vide.
*/
@Preview(name = "Accueil — vide", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun HomeEmptyPreview()
{
	PatrimoineTheme(inDarkTheme = false) { HomeContent(HomeUiState("7 octobre 2026", emptyList()), {}, {}) }
}
