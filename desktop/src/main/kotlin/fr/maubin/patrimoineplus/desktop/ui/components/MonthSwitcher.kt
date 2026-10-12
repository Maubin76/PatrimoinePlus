package fr.maubin.patrimoineplus.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.ui.icons.LineIcons
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val kMonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)	// « octobre 2026 »

/*
	Mois en toutes lettres, avec majuscule : « Octobre 2026 ».
	inMonth : mois
*/
fun monthTitle(inMonth: YearMonth): String = inMonth.format(kMonthYear).replaceFirstChar { it.uppercase() }

/*
	Sélecteur de mois : flèches précédent / suivant autour du mois ; toucher
	le mois ouvre une grille pour sauter directement à un autre mois.
	inMonth    : mois affiché
	inFirst    : premier mois permis
	inLast     : dernier mois permis
	inOnChange : nouveau mois
*/
@Composable
fun MonthSwitcher(
	inMonth    : YearMonth,
	inFirst    : YearMonth,
	inLast     : YearMonth,
	inOnChange : (YearMonth) -> Unit
)
{
	var vPicking by remember { mutableStateOf(false) }						// grille ouverte
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		IconButton(onClick = { inOnChange(inMonth.minusMonths(1)) }, enabled = inMonth.isAfter(inFirst)) {
			Icon(LineIcons.ChevronLeft, contentDescription = "Mois précédent")
		}
		Box(
			modifier         = Modifier
				.weight(1f)
				.heightIn(min = 48.dp)
				.clickable(role = Role.Button, onClickLabel = "Choisir un mois") { vPicking = true },
			contentAlignment = Alignment.Center
		) {
			Text(monthTitle(inMonth), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
		}
		IconButton(onClick = { inOnChange(inMonth.plusMonths(1)) }, enabled = inMonth.isBefore(inLast)) {
			Icon(LineIcons.ChevronRight, contentDescription = "Mois suivant")
		}
	}
	if (vPicking)
	{
		MonthGridDialog(inMonth, inFirst, inLast, { inOnChange(it); vPicking = false }, { vPicking = false })
	}
}

/*
	Grille des douze mois d'une année, année changeable par flèches ; les mois
	hors bornes sont désactivés.
	inMonth     : mois affiché
	inFirst     : premier mois permis
	inLast      : dernier mois permis
	inOnPick    : mois choisi
	inOnDismiss : fermeture
*/
@Composable
private fun MonthGridDialog(
	inMonth     : YearMonth,
	inFirst     : YearMonth,
	inLast      : YearMonth,
	inOnPick    : (YearMonth) -> Unit,
	inOnDismiss : () -> Unit
)
{
	var vYear   by remember { mutableIntStateOf(inMonth.year) }				// année affichée
	val vColors = MaterialTheme.colorScheme									// palette courante
	AlertDialog(
		onDismissRequest = inOnDismiss,
		containerColor   = vColors.surface,
		title            = {
			Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
				IconButton(onClick = { vYear-- }, enabled = vYear > inFirst.year) { Icon(LineIcons.ChevronLeft, contentDescription = "Année précédente") }
				Text("$vYear", textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
				IconButton(onClick = { vYear++ }, enabled = vYear < inLast.year) { Icon(LineIcons.ChevronRight, contentDescription = "Année suivante") }
			}
		},
		text             = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Month.entries.chunked(3).forEach { inRow ->
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						inRow.forEach { inMonthOfYear ->
							val vMonth   = YearMonth.of(vYear, inMonthOfYear)		// mois de la case
							val vChosen  = vMonth == inMonth						// mois affiché
							val vAllowed = !vMonth.isBefore(inFirst) && !vMonth.isAfter(inLast)	// mois permis
							Surface(
								shape    = CircleShape,
								color    = if (vChosen) vColors.onSurface else vColors.surface,
								border   = BorderStroke(1.dp, if (vChosen) vColors.onSurface else vColors.outlineVariant),
								modifier = Modifier.weight(1f)
							) {
								Box(
									modifier         = Modifier
										.height(44.dp)
										.clickable(enabled = vAllowed) { inOnPick(vMonth) },
									contentAlignment = Alignment.Center
								) {
									Text(
										text  = inMonthOfYear.getDisplayName(TextStyle.SHORT, Locale.FRANCE),
										style = MaterialTheme.typography.labelLarge,
										color = when
										{
											vChosen   -> vColors.surface
											vAllowed  -> vColors.onSurface
											else      -> vColors.outlineVariant
										},
										modifier = Modifier.padding(horizontal = 4.dp)
									)
								}
							}
						}
					}
				}
			}
		},
		confirmButton    = { TextButton(onClick = inOnDismiss) { Text("Fermer") } }
	)
}
