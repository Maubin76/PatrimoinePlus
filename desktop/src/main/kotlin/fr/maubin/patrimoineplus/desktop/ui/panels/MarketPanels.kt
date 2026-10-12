package fr.maubin.patrimoineplus.desktop.ui.panels

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.maubin.patrimoineplus.data.edit.EditResult
import fr.maubin.patrimoineplus.desktop.DesktopController
import fr.maubin.patrimoineplus.domain.calc.Ledger
import fr.maubin.patrimoineplus.domain.calc.valuePortfolio
import fr.maubin.patrimoineplus.domain.format.editableScaled
import fr.maubin.patrimoineplus.domain.format.parseEuros
import fr.maubin.patrimoineplus.domain.format.parseScaled

private const val kNewAssetChoice = "__nouvel_actif__"							// choix « Nouvel actif… »

/*
	Nouveau relevé de la banque : date et solde indiqué (remplace celui du même
	jour).
	inController : contrôleur
	inAccountId  : compte
*/
@Composable
fun StatementPanel(
	inController : DesktopController,
	inAccountId  : String
)
{
	var vDate   by remember(inAccountId) { mutableStateOf(inController.today()) }	// date du relevé
	var vAmount by remember(inAccountId) { mutableStateOf("") }					// solde saisi
	var vError  by remember(inAccountId) { mutableStateOf<String?>(null) }		// erreur affichée

	/*
		Enregistre le relevé.
	*/
	fun save()
	{
		val vCents = parseEuros(vAmount) ?: run { vError = "Montant illisible (ex. 12 038,20)."; return }	// solde lu
		finish(inController, inController.edit { inController.editor.putStatement(it, inAccountId, vDate, vCents) }) { vError = it }
	}

	SidePanel("Nouveau relevé", inController::closePanel, ::save, { Button(onClick = ::save) { Text("Enregistrer") } }) {
		FormError(vError)
		FormField(vAmount, { vAmount = it }, "Solde indiqué par la banque (€)", inNumeric = true)
		DateField(vDate, { vDate = it }, "Date du relevé")
	}
}

/*
	Saisie manuelle des cours des positions d'un compte Bourse, tous à une même
	date ; chaque champ est prérempli avec le cours utilisé aujourd'hui (les
	cours automatiques viennent du téléphone).
	inController : contrôleur
	inAccountId  : compte Bourse
*/
@Composable
fun PricesPanel(
	inController : DesktopController,
	inAccountId  : String
)
{
	val vSpace     by inController.workspace.collectAsState()				// données
	val vPositions = remember(vSpace.data, inAccountId) {
		valuePortfolio(Ledger(vSpace.data.transactions), inAccountId, vSpace.today, vSpace.data.prices).positions
	}																			// positions détenues
	val vNames     = vSpace.data.assets.associate { it.id to it.name }		// noms des actifs
	var vDate      by remember(inAccountId) { mutableStateOf(inController.today()) }	// date des cours
	val vInputs    = remember(inAccountId) { mutableStateMapOf<String, String>().apply { vPositions.forEach { put(it.assetId, editableScaled(it.unitPrice)) } } }	// cours saisis
	var vError     by remember(inAccountId) { mutableStateOf<String?>(null) }	// erreur affichée

	/*
		Enregistre les cours lisibles.
	*/
	fun save()
	{
		val vPrices = vInputs.mapNotNull { (inAsset, inText) -> parseScaled(inText)?.takeIf { it > 0 }?.let { inAsset to it } }.toMap()	// cours lus
		if (vPrices.size != vInputs.size)
		{
			vError = "Un cours est illisible ou nul (ex. 144,91)."
			return
		}
		finish(inController, inController.edit { inController.editor.putPrices(it, vDate, vPrices) }) { vError = it }
	}

	SidePanel("Saisir des cours", inController::closePanel, ::save, { Button(onClick = ::save) { Text("Enregistrer") } }) {
		FormError(vError)
		Text("Les cours automatiques sont récupérés par le téléphone ; saisissez ici un cours manquant ou corrigé.")
		DateField(vDate, { vDate = it }, "Date des cours")
		vPositions.forEach { inPosition ->
			FormField(vInputs[inPosition.assetId].orEmpty(), { vInputs[inPosition.assetId] = it }, "${vNames[inPosition.assetId] ?: "Actif"} (€)", inNumeric = true)
		}
	}
}

/*
	Nouvelle position initiale d'un compte Bourse (titres détenus avant le
	début du suivi) : actif existant ou nouveau, quantité, prix de revient.
	inController : contrôleur
	inAccountId  : compte Bourse
*/
@Composable
fun OpeningPositionPanel(
	inController : DesktopController,
	inAccountId  : String
)
{
	val vSpace by inController.workspace.collectAsState()					// données
	var vAsset by remember(inAccountId) { mutableStateOf<String?>(null) }	// actif choisi
	var vName  by remember(inAccountId) { mutableStateOf("") }				// nom d'un nouvel actif
	var vIsin  by remember(inAccountId) { mutableStateOf("") }				// ISIN d'un nouvel actif
	var vQty   by remember(inAccountId) { mutableStateOf("") }				// quantité saisie
	var vPru   by remember(inAccountId) { mutableStateOf("") }				// prix de revient saisi
	var vError by remember(inAccountId) { mutableStateOf<String?>(null) }	// erreur affichée

	/*
		Enregistre la position (crée l'actif s'il est nouveau, en une seule modification).
	*/
	fun save()
	{
		val vQuantity = parseScaled(vQty)?.takeIf { it > 0 }					// quantité lue
		val vPrice    = parseScaled(vPru)?.takeIf { it > 0 }					// prix de revient lu
		vError = when
		{
			vAsset == null                                   -> "Choisissez un actif."
			vAsset == kNewAssetChoice && vName.isBlank()     -> "Nommez le nouvel actif."
			vQuantity == null                                -> "Quantité illisible ou nulle (ex. 12,5)."
			vPrice == null                                   -> "Prix de revient illisible ou nul (ex. 85,20)."
			else                                             -> null
		}
		if (vError != null) return
		finish(inController, inController.edit { inFile ->
			if (vAsset == kNewAssetChoice)
			{
				when (val vNew = inController.editor.createAsset(inFile, vName, vIsin, null))
				{
					is EditResult.Done    -> inController.editor.addOpeningPosition(vNew.file, inAccountId, vNew.id!!, vQuantity!!, vPrice!!, inController.today())
					is EditResult.Refused -> vNew
				}
			}
			else
			{
				inController.editor.addOpeningPosition(inFile, inAccountId, vAsset!!, vQuantity!!, vPrice!!, inController.today())
			}
		}) { vError = it }
	}

	SidePanel("Position initiale", inController::closePanel, ::save, { Button(onClick = ::save) { Text("Ajouter") } }) {
		FormError(vError)
		Picker("Actif", vSpace.data.assets.filterNot { it.archived }.map { it.id to it.name } + (kNewAssetChoice to "Nouvel actif…"), vAsset, { vAsset = it })
		if (vAsset == kNewAssetChoice)
		{
			FormField(vName, { vName = it }, "Nom du nouvel actif")
			FormField(vIsin, { vIsin = it }, "ISIN (facultatif)")
		}
		FormField(vQty, { vQty = it }, "Quantité", inNumeric = true)
		FormField(vPru, { vPru = it }, "Prix de revient unitaire (€)", inSupport = "PRU indiqué par le courtier, frais compris.", inNumeric = true)
	}
}

/*
	Fiche d'un actif : nom, ISIN, symbole de cotation (saisi à la main sur PC ;
	le téléphone sait le chercher automatiquement à partir de l'ISIN).
	inController : contrôleur
	inAssetId    : actif
*/
@Composable
fun AssetPanel(
	inController : DesktopController,
	inAssetId    : String
)
{
	val vSpace  by inController.workspace.collectAsState()					// données
	val vAsset  = vSpace.data.assets.firstOrNull { it.id == inAssetId }		// actif
	var vName   by remember(inAssetId) { mutableStateOf(vAsset?.name.orEmpty()) }	// nom
	var vIsin   by remember(inAssetId) { mutableStateOf(vAsset?.isin.orEmpty()) }	// ISIN
	var vTicker by remember(inAssetId) { mutableStateOf(vAsset?.ticker.orEmpty()) }	// symbole
	var vError  by remember(inAssetId) { mutableStateOf<String?>(null) }		// erreur affichée

	/*
		Enregistre la fiche.
	*/
	fun save()
	{
		finish(inController, inController.edit { inController.editor.updateAsset(it, inAssetId, vName, vIsin, vTicker) }) { vError = it }
	}

	SidePanel("Actif", inController::closePanel, ::save, { Button(onClick = ::save) { Text("Enregistrer") } }) {
		FormError(vError)
		FormField(vName, { vName = it }, "Nom")
		FormField(vIsin, { vIsin = it }, "ISIN", inSupport = "Sur vos relevés, ex. LU1681043599.")
		FormField(vTicker, { vTicker = it }, "Symbole de cotation", inSupport = "Cotation en euros, ex. CW8.PA. Le téléphone le trouve seul à partir de l’ISIN.")
	}
}

/*
	Fin d'une saisie : ferme le panneau si la modification est acceptée,
	affiche le refus sinon.
	inController : contrôleur
	inResult     : résultat de la modification
	inOnRefused  : affiche le motif du refus
*/
internal fun finish(
	inController : DesktopController,
	inResult     : EditResult,
	inOnRefused  : (String) -> Unit
)
{
	when (inResult)
	{
		is EditResult.Done    -> inController.closePanel()
		is EditResult.Refused -> inOnRefused(inResult.messages.joinToString(" "))
	}
}
