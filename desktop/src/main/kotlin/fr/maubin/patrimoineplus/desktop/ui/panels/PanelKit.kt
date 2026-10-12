package fr.maubin.patrimoineplus.desktop.ui.panels

import fr.maubin.patrimoineplus.domain.format.formatFrenchDate
import fr.maubin.patrimoineplus.domain.format.parseFrenchDate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import fr.maubin.patrimoineplus.ui.icons.LineIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

val kPanelWidth = 440.dp																// largeur du panneau latéral

/*
	Panneau latéral de saisie, à droite de la page : titre et bouton de
	fermeture, champs défilants, actions en bas. Échap ferme, Entrée lance
	l'action principale.
	inTitle   : titre
	inOnClose : fermeture
	inOnEnter : action principale (Entrée)
	inActions : boutons du bas
	inContent : champs
*/
@Composable
fun SidePanel(
	inTitle   : String,
	inOnClose : () -> Unit,
	inOnEnter : () -> Unit,
	inActions : @Composable () -> Unit,
	inContent : @Composable ColumnScope.() -> Unit
)
{
	Row(Modifier.fillMaxHeight().width(kPanelWidth)) {
		VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
		Column(
			Modifier
				.fillMaxHeight()
				.fillMaxWidth()
				.background(MaterialTheme.colorScheme.surface)
				.onPreviewKeyEvent { inEvent ->
					when
					{
						inEvent.type != KeyEventType.KeyDown -> false
						inEvent.key == Key.Escape            -> { inOnClose(); true }
						inEvent.key == Key.Enter             -> { inOnEnter(); true }
						else                                 -> false
					}
				}
		) {
			Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
				Text(inTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
				IconButton(onClick = inOnClose) { Icon(LineIcons.Close, contentDescription = "Fermer") }
			}
			Column(
				modifier            = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
				content             = inContent
			)
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
				inActions()
			}
		}
	}
}

/*
	Champ texte d'un formulaire.
	inValue    : texte
	inOnChange : modification
	inLabel    : libellé
	inError    : message d'erreur ; null sans erreur
	inSupport  : texte d'aide ; null sans aide
	inNumeric  : clavier numérique
*/
@Composable
fun FormField(
	inValue    : String,
	inOnChange : (String) -> Unit,
	inLabel    : String,
	inError    : String? = null,
	inSupport  : String? = null,
	inNumeric  : Boolean = false
)
{
	OutlinedTextField(
		value           = inValue,
		onValueChange   = inOnChange,
		label           = { Text(inLabel) },
		singleLine      = true,
		isError         = inError != null,
		supportingText  = (inError ?: inSupport)?.let { { Text(it) } },
		keyboardOptions = if (inNumeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
		modifier        = Modifier.fillMaxWidth()
	)
}

/*
	Champ de date au format JJ/MM/AAAA ; la date n'est rendue que si elle est
	valide.
	inDate     : date actuelle
	inOnChange : nouvelle date valide
	inLabel    : libellé
*/
@Composable
fun DateField(
	inDate     : LocalDate,
	inOnChange : (LocalDate) -> Unit,
	inLabel    : String
)
{
	var vText by remember(inDate) { mutableStateOf(formatFrenchDate(inDate)) }	// texte saisi
	val vParsed = parseFrenchDate(vText)										// date lue
	FormField(
		inValue    = vText,
		inOnChange = { inText -> vText = inText; parseFrenchDate(inText)?.let(inOnChange) },
		inLabel    = inLabel,
		inError    = if (vParsed == null) "Date illisible (JJ/MM/AAAA)." else null
	)
}

/*
	Liste déroulante : un champ cliquable qui montre le choix courant.
	inLabel    : libellé
	inOptions  : choix proposés (valeur, texte affiché)
	inSelected : valeur choisie ; null sans choix
	inOnSelect : nouveau choix
	inError    : message d'erreur ; null sans erreur
	inEnabled  : champ modifiable
*/
@Composable
fun <T> Picker(
	inLabel    : String,
	inOptions  : List<Pair<T, String>>,
	inSelected : T?,
	inOnSelect : (T) -> Unit,
	inError    : String? = null,
	inEnabled  : Boolean = true
)
{
	var vOpen by remember { mutableStateOf(false) }								// menu ouvert
	Box {
		OutlinedTextField(
			value          = inOptions.firstOrNull { it.first == inSelected }?.second.orEmpty(),
			onValueChange  = {},
			readOnly       = true,
			enabled        = inEnabled,
			label          = { Text(inLabel) },
			isError        = inError != null,
			supportingText = inError?.let { { Text(it) } },
			trailingIcon   = { Text("▾") },
			modifier       = Modifier.fillMaxWidth()
		)
		Box(Modifier.matchParentSizeOrFill().clickable(enabled = inEnabled) { vOpen = true })
		DropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }) {
			inOptions.forEach { (inValue, inText) ->
				DropdownMenuItem(text = { Text(inText) }, onClick = { vOpen = false; inOnSelect(inValue) })
			}
		}
	}
}

/*
	Zone cliquable couvrant le champ (le champ en lecture seule ne reçoit pas
	les clics de souris).
*/
private fun Modifier.matchParentSizeOrFill(): Modifier = this.fillMaxWidth().height(64.dp)

/*
	Choix segmenté (mode de saisie, type, nature).
	inOptions  : choix (valeur, texte)
	inSelected : valeur choisie
	inOnSelect : nouveau choix
	inEnabled  : choix modifiable
*/
@Composable
fun <T> Choice(
	inOptions  : List<Pair<T, String>>,
	inSelected : T,
	inOnSelect : (T) -> Unit,
	inEnabled  : Boolean = true
)
{
	val vColors = MaterialTheme.colorScheme										// palette courante
	SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
		inOptions.forEachIndexed { inIndex, (inValue, inText) ->
			SegmentedButton(
				selected = inValue == inSelected,
				onClick  = { inOnSelect(inValue) },
				enabled  = inEnabled,
				shape    = SegmentedButtonDefaults.itemShape(index = inIndex, count = inOptions.size),
				icon     = {},
				colors   = SegmentedButtonDefaults.colors(
					activeContainerColor   = vColors.primary,
					activeContentColor     = vColors.onPrimary,
					activeBorderColor      = vColors.primary,
					inactiveContainerColor = vColors.surface,
					inactiveContentColor   = vColors.onSurface,
					inactiveBorderColor    = vColors.outlineVariant
				),
				label    = { Text(inText) }
			)
		}
	}
}

/*
	Message d'erreur général d'un formulaire.
	inMessage : message ; rien sans message
*/
@Composable
fun FormError(inMessage: String?)
{
	if (inMessage != null)
	{
		Text(inMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
		Spacer(Modifier.height(4.dp))
	}
}
