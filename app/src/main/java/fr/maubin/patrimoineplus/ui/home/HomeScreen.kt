package fr.maubin.patrimoineplus.ui.home

import fr.maubin.patrimoineplus.ui.components.typeColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.maubin.patrimoineplus.domain.format.formatEuros
import fr.maubin.patrimoineplus.domain.format.kMaskedAmount
import fr.maubin.patrimoineplus.domain.model.AccountType
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.BarSegment
import fr.maubin.patrimoineplus.ui.components.BalanceChart
import fr.maubin.patrimoineplus.ui.components.BorderedCard
import fr.maubin.patrimoineplus.ui.components.EmptyState
import fr.maubin.patrimoineplus.ui.components.InfoPill
import fr.maubin.patrimoineplus.ui.components.LocalAmountsHidden
import fr.maubin.patrimoineplus.ui.components.ProportionBar
import fr.maubin.patrimoineplus.ui.components.SectionHeader
import fr.maubin.patrimoineplus.ui.components.SeriesDot
import fr.maubin.patrimoineplus.ui.shell.PrivacyAction
import fr.maubin.patrimoineplus.ui.shell.ScreenScaffold
import fr.maubin.patrimoineplus.ui.shell.kScreenMargin
import fr.maubin.patrimoineplus.ui.theme.PatrimoineTheme
import fr.maubin.patrimoineplus.ui.components.ProportionChart
import fr.maubin.patrimoineplus.ui.components.ProportionToggle

private const val kClearedDisplay = "– – –,– – €"						// afficheur vide : aucun compte

/*
	Écran d'accueil : patrimoine total et sa répartition par type de compte
	(la liste des comptes est dans l'onglet Comptes).
	inOnToggleHidden   : bascule le mode discret
	inOnManageAccounts : ouvre la gestion des comptes
	inViewModel        : ViewModel fourni par Hilt
*/
@Composable
fun HomeScreen(
	inOnToggleHidden   : () -> Unit,
	inOnManageAccounts : () -> Unit,
	inViewModel        : HomeViewModel = hiltViewModel()
)
{
	val vState by inViewModel.uiState.collectAsStateWithLifecycle()		// état à afficher
	HomeContent(vState, inOnToggleHidden, inOnManageAccounts)
}

/*
	Contenu de l'accueil, sans état propre (prévisualisable).
	inState            : état à afficher
	inOnToggleHidden   : bascule le mode discret
	inOnManageAccounts : ouvre la gestion des comptes
*/
@Composable
fun HomeContent(
	inState            : HomeUiState,
	inOnToggleHidden   : () -> Unit,
	inOnManageAccounts : () -> Unit
)
{
	ScreenScaffold(
		inTitle   = "Patrimoine",
		inActions = { PrivacyAction(inOnToggleHidden) }
	) { inPadding ->
		LazyColumn(
			contentPadding = PaddingValues(
				start  = kScreenMargin,
				end    = kScreenMargin,
				top    = inPadding.calculateTopPadding() + 16.dp,
				bottom = inPadding.calculateBottomPadding() + 16.dp
			)
		) {
			item { TotalBlock(inState) }
			item { Spacer(Modifier.height(36.dp)) }
			if (inState.isEmpty)
			{
				item {
					BorderedCard {
						EmptyState(
							inTitle       = "Aucun compte",
							inBody        = "Le total se calcule à partir de vos comptes et de leurs opérations. Commencez par les créer.",
							inActionLabel = "Ajouter mes comptes",
							inOnAction    = inOnManageAccounts
						)
					}
				}
			}
			else
			{
				if (inState.series.size > 1)
				{
					item { SectionHeader(inTitle = "Évolution") }
					item { Spacer(Modifier.height(4.dp)) }
					item { BalanceChart(inState.series, inState.series.last().month) }
					item { Spacer(Modifier.height(32.dp)) }
				}
				item { SectionHeader(inTitle = "Répartition", inAction = { ProportionToggle("accueil") }) }
				item { Spacer(Modifier.height(4.dp)) }
				item { RepartitionBlock(inState, inOnManageAccounts) }
			}
		}
	}
}

/*
	Total centré à la taille de l'afficheur, puis pastille de la date de
	calcul. Sans compte, l'afficheur montre des tirets atténués.
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
	Column(
		modifier            = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
			val vStyle = fittingStyle(vShown, constraints.maxWidth)			// plus grande taille qui tient
			if (inState.isEmpty)
			{
				Text(
					text      = kClearedDisplay,
					style     = vStyle,
					color     = vColors.onSurfaceVariant,
					maxLines  = 1,
					textAlign = TextAlign.Center,
					modifier  = Modifier.semantics { contentDescription = "Aucun total" }
				)
			}
			else
			{
				AmountText(inCents = inState.totalCents, inStyle = vStyle, inRolling = true)
			}
		}
		if (!inState.isEmpty && inState.debtCents != 0L)
		{
			Spacer(Modifier.height(4.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text("Brut ", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				AmountText(inCents = inState.grossCents, inStyle = MaterialTheme.typography.bodyMedium, inColor = vColors.onSurfaceVariant)
				Text(" · crédits ", style = MaterialTheme.typography.bodyMedium, color = vColors.onSurfaceVariant)
				AmountText(inCents = inState.debtCents, inStyle = MaterialTheme.typography.bodyMedium, inColor = vColors.onSurfaceVariant)
			}
		}
		Spacer(Modifier.height(16.dp))
		val vChange = inState.monthChange											// variation du mois ; null le premier mois
		if (!inState.isEmpty && vChange != null)
		{
			Row(verticalAlignment = Alignment.CenterVertically) {
				AmountText(inCents = vChange, inStyle = MaterialTheme.typography.titleMedium, inShowPlusSign = true, inTrend = true)
				Text(" ce mois-ci", style = MaterialTheme.typography.titleMedium, color = vColors.onSurfaceVariant)
			}
			Spacer(Modifier.height(16.dp))
		}
		InfoPill(if (inState.isEmpty) "Rien à calculer pour l’instant" else "Calculé au ${inState.asOf}")
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
	Répartition par type de compte : barre en niveaux de gris, puis une
	légende par type avec sa part et son sous-total.
	Toucher une ligne ouvre l'onglet Comptes.
	inState  : état de l'accueil
	inOnOpen : ouvre l'onglet Comptes
*/
@Composable
private fun RepartitionBlock(
	inState  : HomeUiState,
	inOnOpen : () -> Unit
)
{
	val vHidden = LocalAmountsHidden.current								// mode discret actif
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		ProportionChart(
			inKey         = "accueil",
			inSegments    = inState.assetGroups.map { BarSegment(it.subtotalCents, typeColor(it.type)) },
			inDescription = repartitionDescription(inState, vHidden),
			inStable      = false
		)
		Spacer(Modifier.height(12.dp))
		inState.assetGroups.forEach { inGroup ->
			val vPercent = inGroup.percent										// part entière
			Row(
				modifier          = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Voir les comptes", onClick = inOnOpen),
				verticalAlignment = Alignment.CenterVertically
			) {
				SeriesDot(typeColor(inGroup.type))
				Spacer(Modifier.width(12.dp))
				Text(
					text     = inGroup.type.label,
					style    = MaterialTheme.typography.bodyLarge,
					modifier = Modifier.weight(1f)
				)
				Text(
					text  = "$vPercent\u202F%",
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Spacer(Modifier.width(16.dp))
				AmountText(inCents = inGroup.subtotalCents, inStyle = MaterialTheme.typography.bodyLarge)
			}
		}
		if (inState.unlinkedDebtCents != 0L)
		{
			Row(
				modifier          = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Voir les comptes", onClick = inOnOpen),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text("Crédits à déduire", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
				AmountText(inCents = inState.unlinkedDebtCents, inStyle = MaterialTheme.typography.bodyLarge)
			}
		}
	}
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
	return "Répartition nette : " + inState.assetGroups.joinToString(", ") { inGroup ->
		val vPercent = inGroup.percent										// part entière
		"${inGroup.type.label} $vPercent\u202F%"
	}
}


// Données de démonstration, uniquement pour les aperçus et les captures (synthétiques).
internal val kPreviewState = HomeUiState(
	asOf   = "7 octobre 2026",
	groups = listOf(
		HomeGroup(AccountType.CHECKING, listOf(
			HomeAccountLine("Compte principal", "Courant · Banque démo", 284_312),
			HomeAccountLine("Compte joint", "Courant · Banque démo · 50 %", 160_000, 80_000)
		)),
		HomeGroup(AccountType.SAVINGS, listOf(HomeAccountLine("Livret A", "Épargne", 2_295_000), HomeAccountLine("LDDS", "Épargne", 1_204_118))),
		HomeGroup(AccountType.BROKERAGE, listOf(HomeAccountLine("PEA", "Bourse · Courtier démo", 3_871_540)))
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
