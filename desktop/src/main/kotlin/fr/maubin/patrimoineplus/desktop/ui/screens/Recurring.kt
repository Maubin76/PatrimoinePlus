package fr.maubin.patrimoineplus.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.desktop.Panel
import fr.maubin.patrimoineplus.desktop.data.WorkspaceState
import fr.maubin.patrimoineplus.desktop.ui.components.Hint
import fr.maubin.patrimoineplus.domain.calc.amortization
import fr.maubin.patrimoineplus.domain.model.Recurrence
import fr.maubin.patrimoineplus.ui.components.AmountText
import fr.maubin.patrimoineplus.ui.components.Tile
import fr.maubin.patrimoineplus.ui.icons.CategoryIcons
import fr.maubin.patrimoineplus.ui.planning.RecurrenceForm
import fr.maubin.patrimoineplus.ui.planning.editRecurrenceForm
import fr.maubin.patrimoineplus.ui.planning.nextOccurrence
import java.time.format.DateTimeFormatter
import java.util.Locale

private val kDay = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)			// date d'une échéance

/*
	Opérations récurrentes : liste (libellé, comptes, fréquence, prochaine
	échéance, montant), création et modification dans le panneau ;
	mensualités des crédits, rappelées en lecture seule. Chaque échéance est
	créée automatiquement à sa date, sur le PC comme sur le téléphone.
	inSpace      : données
	inController : contrôleur
*/
@Composable
fun RecurringScreen(
	inSpace      : WorkspaceState,
	inController : DesktopController
)
{
	val vData  = inSpace.data																// données
	val vNames = vData.accounts.associate { it.id to it.name }								// noms des comptes
	val vToday = inSpace.today																// date du jour
	val vRecs  = vData.recurrences.sortedWith(compareBy<Recurrence> { nextOccurrence(it, vToday) ?: java.time.LocalDate.MAX }.thenBy { it.label })	// par prochaine échéance
	Column(
		modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
		verticalArrangement = Arrangement.spacedBy(20.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Récurrences", style = MaterialTheme.typography.headlineMedium)
				Text("Salaire, loyer, abonnements, versements : chaque échéance crée l’opération toute seule, à sa date. Une opération créée se modifie ou se supprime comme les autres ; supprimée, elle n’est pas recréée.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			Spacer(Modifier.width(24.dp))
			Button(onClick = { inController.openPanel(Panel.Recurring(RecurrenceForm(start = vToday.plusDays(1), accountId = vData.accounts.firstOrNull { !it.archived && !it.type.isProperty }?.id))) }) { Text("Nouvelle récurrence") }
		}
		Tile(inTitle = "Opérations récurrentes") {
			if (vRecs.isEmpty()) Hint("Aucune récurrence. Créez-en une ici, ou choisissez « Répéter » en saisissant une opération.")
			vRecs.forEachIndexed { inIndex, inRec ->
				if (inIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
				val vNext = nextOccurrence(inRec, vToday)											// prochaine échéance
				val vCat  = vData.categories.firstOrNull { it.id == inRec.categoryId }			// catégorie
				Row(
					modifier          = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = "Modifier") { inController.openPanel(Panel.Recurring(editRecurrenceForm(inRec, vData.categories))) }.padding(horizontal = 8.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(CategoryIcons.of(vCat?.icon ?: "repeat"), contentDescription = null, modifier = Modifier.size(20.dp))
					Spacer(Modifier.width(14.dp))
					Column(Modifier.weight(1f)) {
						Text(inRec.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
						Text(
							text     = listOfNotNull(
								vNames[inRec.accountId].orEmpty() + (inRec.toAccountId?.let { " → ${vNames[it].orEmpty()}" } ?: vCat?.let { " · ${it.name}" }.orEmpty()),
								inRec.frequency.label,
								vNext?.let { "prochaine le ${it.format(kDay)}" } ?: "terminée"
							).joinToString(" · "),
							style    = MaterialTheme.typography.bodyMedium,
							color    = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
					AmountText(inCents = inRec.amountCents, inStyle = MaterialTheme.typography.bodyLarge, inShowPlusSign = inRec.toAccountId == null)
				}
			}
		}
		if (vData.loans.isNotEmpty())
		{
			Tile(inTitle = "Mensualités de crédit") {
				vData.loans.forEach { inLoan ->
					val vNext = amortization(inLoan).firstOrNull { it.date.isAfter(vToday) }			// prochaine mensualité
					Row(
						modifier          = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClickLabel = "Ouvrir le crédit") { inController.openAccount(inLoan.accountId) }.padding(horizontal = 8.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(Modifier.weight(1f)) {
							Text(vNames[inLoan.accountId].orEmpty(), style = MaterialTheme.typography.bodyLarge)
							Text(
								"Depuis ${vNames[inLoan.payFrom].orEmpty()} · " + (vNext?.let { "mensualité ${it.index}/${inLoan.months} le ${it.date.format(kDay)}" } ?: "remboursé"),
								style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						vNext?.let { AmountText(inCents = -it.total, inStyle = MaterialTheme.typography.bodyLarge) }
					}
				}
			}
		}
	}
}
