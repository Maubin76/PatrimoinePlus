package fr.maubin.patrimoineplus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.foundation.layout.PaddingValues
import fr.maubin.patrimoineplus.ui.components.PillChoice

private val kDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)	// date affichée dans les champs

/*
	Feuille du bas modale : titre, champs défilants, au-dessus du clavier.
	inTitle     : titre de la feuille
	inOnDismiss : fermeture (glissement vers le bas, toucher hors de la feuille)
	inContent   : champs et actions
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
	inTitle     : String,
	inOnDismiss : () -> Unit,
	inContent   : @Composable ColumnScope.() -> Unit
)
{
	ModalBottomSheet(
		onDismissRequest = inOnDismiss,
		sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		containerColor   = MaterialTheme.colorScheme.surface
	) {
		Column(
			modifier            = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.imePadding()
				.navigationBarsPadding()
				.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Text(inTitle, style = MaterialTheme.typography.headlineSmall)
			inContent()
		}
	}
}

/*
	Champ texte d'un formulaire, pleine largeur.
	inValue    : texte
	inOnChange : modification
	inLabel    : libellé
	inError    : message d'erreur ; null sans erreur
	inSupport  : texte d'aide ; null sans aide
*/
@Composable
fun TextInput(
	inValue    : String,
	inOnChange : (String) -> Unit,
	inLabel    : String,
	inError    : String? = null,
	inSupport  : String? = null
)
{
	OutlinedTextField(
		value           = inValue,
		onValueChange   = inOnChange,
		label           = { Text(inLabel) },
		singleLine      = true,
		isError         = inError != null,
		supportingText  = (inError ?: inSupport)?.let { { Text(it) } },
		keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
		modifier        = Modifier.fillMaxWidth()
	)
}

/*
	Champ de montant en euros : clavier numérique, signe moins accepté.
	inValue    : texte saisi
	inOnChange : modification
	inLabel    : libellé
	inError    : message d'erreur ; null sans erreur
	inSupport  : texte d'aide ; null sans aide
*/
@Composable
fun AmountInput(
	inValue    : String,
	inOnChange : (String) -> Unit,
	inLabel    : String,
	inError    : String? = null,
	inSupport  : String? = null
)
{
	OutlinedTextField(
		value           = inValue,
		onValueChange   = { inText -> inOnChange(inText.filter { it.isDigit() || it in ",.- " }) },
		label           = { Text(inLabel) },
		suffix          = { Text("€") },
		singleLine      = true,
		isError         = inError != null,
		supportingText  = (inError ?: inSupport)?.let { { Text(it) } },
		keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
		modifier        = Modifier.fillMaxWidth()
	)
}

/*
	Champ de date : affiche la date en toutes lettres, ouvre un calendrier.
	inDate     : date choisie
	inOnChange : nouvelle date
	inLabel    : libellé
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInput(
	inDate     : LocalDate,
	inOnChange : (LocalDate) -> Unit,
	inLabel    : String
)
{
	val vOpen = remember { mutableStateOf(false) }								// calendrier ouvert
	Box {
		OutlinedTextField(
			value         = inDate.format(kDateFormat),
			onValueChange = {},
			label         = { Text(inLabel) },
			readOnly      = true,
			singleLine    = true,
			modifier      = Modifier.fillMaxWidth()
		)
		Box(Modifier.matchParentSize().clickable { vOpen.value = true })
	}
	if (vOpen.value)
	{
		val vState = rememberDatePickerState(initialSelectedDateMillis = inDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())	// calendrier
		DatePickerDialog(
			onDismissRequest = { vOpen.value = false },
			confirmButton    = {
				TextButton(onClick = {
					vState.selectedDateMillis?.let { inOnChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
					vOpen.value = false
				}) { Text("Valider") }
			},
			dismissButton    = { TextButton(onClick = { vOpen.value = false }) { Text("Annuler") } }
		) {
			DatePicker(state = vState)
		}
	}
}

/*
	Choix exclusif en pilules (composant partagé PillChoice, comme sur le PC).
	inOptions  : options, dans l'ordre
	inSelected : option choisie
	inLabel    : libellé d'une option
	inOnSelect : choix d'une option
	inEnabled  : modification permise
*/
@Composable
fun <T> SegmentedChoice(
	inOptions  : List<T>,
	inSelected : T,
	inLabel    : (T) -> String,
	inOnSelect : (T) -> Unit,
	inEnabled  : Boolean = true
)
{
	PillChoice(inOptions, inSelected, inLabel, inOnSelect, inEnabled)
}

/*
	Boîte de confirmation d'une action ; le bouton d'action porte son nom.
	inTitle     : question
	inText      : conséquences
	inAction    : libellé du bouton d'action
	inOnConfirm : action confirmée
	inOnDismiss : annulation
*/
@Composable
fun ConfirmDialog(
	inTitle     : String,
	inText      : String,
	inAction    : String,
	inOnConfirm : () -> Unit,
	inOnDismiss : () -> Unit
)
{
	AlertDialog(
		onDismissRequest = inOnDismiss,
		title            = { Text(inTitle) },
		text             = { Text(inText) },
		confirmButton    = { TextButton(onClick = inOnConfirm) { Text(inAction) } },
		dismissButton    = { TextButton(onClick = inOnDismiss) { Text("Annuler") } }
	)
}

/*
	Liste déroulante générique : options (identifiant, texte).
	inLabel    : libellé du champ
	inOptions  : options proposées
	inSelected : identifiant choisi
	inError    : message d'erreur ; null sans erreur
	inOnSelect : choix d'une option
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Picker(
	inLabel    : String,
	inOptions  : List<Pair<String?, String>>,
	inSelected : String?,
	inError    : String?,
	inOnSelect : (String?) -> Unit
)
{
	var vOpen by remember { mutableStateOf(false) }							// liste ouverte
	ExposedDropdownMenuBox(expanded = vOpen, onExpandedChange = { vOpen = it }) {
		OutlinedTextField(
			value          = inOptions.firstOrNull { it.first == inSelected }?.second.orEmpty(),
			onValueChange  = {},
			readOnly       = true,
			label          = { Text(inLabel) },
			isError        = inError != null,
			supportingText = inError?.let { { Text(it) } },
			trailingIcon   = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vOpen) },
			singleLine     = true,
			modifier       = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
		)
		ExposedDropdownMenu(expanded = vOpen, onDismissRequest = { vOpen = false }, containerColor = MaterialTheme.colorScheme.surface) {
			inOptions.forEach { (inId, inText) ->
				DropdownMenuItem(
					text    = { Text(inText) },
					onClick = {
						inOnSelect(inId)
						vOpen = false
					}
				)
			}
		}
	}
}
