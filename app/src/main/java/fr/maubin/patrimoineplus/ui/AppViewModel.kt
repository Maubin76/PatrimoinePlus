package fr.maubin.patrimoineplus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import fr.maubin.patrimoineplus.ui.theme.ColorTheme
import fr.maubin.patrimoineplus.data.repo.ThemeRepository
import fr.maubin.patrimoineplus.domain.model.CustomTheme
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import fr.maubin.patrimoineplus.ui.components.Backdrop
import fr.maubin.patrimoineplus.ui.components.parseBackdropColor
import fr.maubin.patrimoineplus.ui.components.backdropCode

/*
	État partagé par toute l'interface : mode discret, apparence, couleurs
	des hausses et des baisses, fond choisi.
	inSettings : réglages mémorisés
	inThemes   : thèmes personnalisés
	inContext  : contexte de l'application (copie de la photo de fond)
*/
@HiltViewModel
class AppViewModel @Inject constructor(
	private val inSettings : SettingsRepository,
	private val inThemes   : ThemeRepository,
	@ApplicationContext private val inContext : Context
) : ViewModel()
{
	private val fPhotoFile = java.io.File(inContext.filesDir, "fond-ecran.img")		// copie de la photo de fond
	private val fPhoto     = MutableStateFlow<ImageBitmap?>(null)						// photo de fond décodée ; null sans

	init
	{
		viewModelScope.launch(Dispatchers.IO) { if (fPhotoFile.isFile) fPhoto.value = decodePhoto(fPhotoFile) }
	}

	// Fond choisi : vide (défaut), « #RRGGBB » ou « photo ».
	val background: StateFlow<String> = inSettings.background.stateIn(viewModelScope, SharingStarted.Eagerly, "")

	// Flou de la photo de fond, de 0 à 100.
	val backgroundBlur: StateFlow<Int> = inSettings.backgroundBlur.stateIn(viewModelScope, SharingStarted.Eagerly, 60)

	// Voile de la photo de fond, de 0 à 100.
	val backgroundVeil: StateFlow<Int> = inSettings.backgroundVeil.stateIn(viewModelScope, SharingStarted.Eagerly, 40)

	// Fond prêt à dessiner (couleur, ou photo floutée et voilée ; par défaut sinon).
	val backdrop: StateFlow<Backdrop> = combine(background, backgroundBlur, backgroundVeil, fPhoto) { inCode, inBlur, inVeil, inPhoto ->
		Backdrop(
			color = if (inCode.startsWith("#")) parseBackdropColor(inCode) else null,
			photo = if (inCode == "photo") inPhoto else null,
			blur  = inBlur / 100f,
			veil  = inVeil / 100f
		)
	}.stateIn(viewModelScope, SharingStarted.Eagerly, Backdrop())

	/*
		Choisit la couleur du fond ; null revient au fond par défaut. La photo
		éventuelle est oubliée.
		inCode : code « #RRGGBB » ; null pour le fond par défaut
	*/
	fun setBackgroundColor(inCode: String?)
	{
		val vColor = inCode?.let(::parseBackdropColor)													// couleur lue
		if (inCode != null && vColor == null) return
		viewModelScope.launch {
			withContext(Dispatchers.IO) { fPhotoFile.delete() }
			fPhoto.value = null
			inSettings.setBackground(vColor?.let(::backdropCode).orEmpty())
		}
	}

	/*
		Met une photo en fond : copiée dans le dossier privé de l'appli (la
		photo d'origine peut ensuite disparaître de la galerie), puis floutée
		et voilée selon les réglages ; une image illisible est ignorée.
		inUri    : photo choisie
		inOnFail : appelé si la photo est illisible
	*/
	fun setBackgroundPhoto(
		inUri    : Uri,
		inOnFail : () -> Unit = {}
	)
	{
		viewModelScope.launch {
			val vImage = withContext(Dispatchers.IO) {
				runCatching {
					inContext.contentResolver.openInputStream(inUri)?.use { inIn -> fPhotoFile.outputStream().use { inIn.copyTo(it) } }
					decodePhoto(fPhotoFile)
				}.getOrNull()
			}																							// photo décodée
			if (vImage == null) { inOnFail(); return@launch }
			fPhoto.value = vImage
			inSettings.setBackground("photo")
		}
	}

	/*
		Retire la photo de fond : retour au fond par défaut.
	*/
	fun removeBackgroundPhoto() = setBackgroundColor(null)

	/*
		Règle le flou de la photo de fond.
		inBlur : de 0 à 100
	*/
	fun setBackgroundBlur(inBlur: Int)
	{
		viewModelScope.launch { inSettings.setBackgroundBlur(inBlur) }
	}

	/*
		Règle le voile de la photo de fond.
		inVeil : de 0 à 100
	*/
	fun setBackgroundVeil(inVeil: Int)
	{
		viewModelScope.launch { inSettings.setBackgroundVeil(inVeil) }
	}

	// Thèmes de couleurs créés (partagés par le fichier d'échange).
	val themes: StateFlow<List<CustomTheme>> =
		inThemes.observeThemes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

	// Thème personnalisé choisi sur ce téléphone (identifiant) ; vide sans.
	val customTheme: StateFlow<String> =
		inSettings.customTheme.stateIn(viewModelScope, SharingStarted.Eagerly, "")

	/*
		Choisit un thème personnalisé.
		inId : thème
	*/
	fun setCustomTheme(inId: String)
	{
		viewModelScope.launch { inSettings.setCustomTheme(inId) }
	}

	/*
		Crée ou modifie un thème ; un thème créé devient le thème choisi.
		inId     : thème modifié ; null pour en créer un
		inName   : nom
		inColors : couleurs tapées
		inOnDone : reçoit le refus ; null si enregistré
	*/
	fun saveTheme(
		inId     : String?,
		inName   : String,
		inColors : List<String>,
		inOnDone : (String?) -> Unit
	)
	{
		viewModelScope.launch {
			inThemes.save(inId, inName, inColors)
				.onSuccess { inSaved -> if (inId == null) inSettings.setCustomTheme(inSaved); inOnDone(null) }
				.onFailure { inOnDone(it.message) }
		}
	}

	/*
		Supprime un thème ; s'il était choisi, le téléphone revient au thème Coloré.
		inId : thème
	*/
	fun deleteTheme(inId: String)
	{
		viewModelScope.launch {
			inThemes.delete(inId)
			if (customTheme.value == inId) { inSettings.setCustomTheme(""); inSettings.setColorTheme(ColorTheme.COLORFUL) }
		}
	}
	// Vrai quand les montants sont masqués.
	val amountsHidden: StateFlow<Boolean> =
		inSettings.amountsHidden.stateIn(viewModelScope, SharingStarted.Eagerly, false)

	/*
		Bascule le mode discret.
	*/
	fun toggleAmountsHidden()
	{
		setAmountsHidden(!amountsHidden.value)
	}

	/*
		Fixe le mode discret.
		inHidden : vrai pour masquer les montants
	*/
	fun setAmountsHidden(inHidden: Boolean)
	{
		viewModelScope.launch { inSettings.setAmountsHidden(inHidden) }
	}

	// Thème de couleurs choisi.
	val colorTheme: StateFlow<ColorTheme> =
		inSettings.colorTheme.stateIn(viewModelScope, SharingStarted.Eagerly, ColorTheme.MONOCHROME)

	/*
		Choisit le thème de couleurs.
		inTheme : thème
	*/
	fun setColorTheme(inTheme: ColorTheme)
	{
		viewModelScope.launch { inSettings.setColorTheme(inTheme); inSettings.setCustomTheme("") }
	}

	// Effet verre choisi.
	val glassEffect: StateFlow<Boolean> =
		inSettings.glassEffect.stateIn(viewModelScope, SharingStarted.Eagerly, false)

	/*
		Active ou retire l'effet verre.
		inGlass : vrai pour l'effet verre
	*/
	fun setGlassEffect(inGlass: Boolean)
	{
		viewModelScope.launch { inSettings.setGlassEffect(inGlass) }
	}

	// Clés des diagrammes de répartition affichés en cercle.
	val pieCharts: StateFlow<Set<String>> =
		inSettings.pieCharts.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

	/*
		Choisit la forme d'un diagramme de répartition.
		inKey : clé du diagramme
		inPie : vrai pour le cercle
	*/
	fun setPieChart(
		inKey : String,
		inPie : Boolean
	)
	{
		viewModelScope.launch { inSettings.setPieChart(inKey, inPie) }
	}

	// Apparence choisie ; « Système » tant que le réglage n'est pas lu.
	val themeMode: StateFlow<ThemeMode> =
		inSettings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

	/*
		Fixe l'apparence de l'application.
		inMode : apparence choisie
	*/
	fun setThemeMode(inMode: ThemeMode)
	{
		viewModelScope.launch { inSettings.setThemeMode(inMode) }
	}
}

private const val kPhotoMax = 1600													// plus grand côté gardé d'une photo de fond (mémoire)

/*
	Décode une photo réduite à 1 600 pixels environ de côté (le fond est
	flouté : la pleine définition ne servirait à rien) ; null si elle est
	illisible.
	inFile : fichier de la photo
*/
private fun decodePhoto(inFile: java.io.File): ImageBitmap?
{
	val vBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }		// taille seule
	BitmapFactory.decodeFile(inFile.path, vBounds)
	if (vBounds.outWidth <= 0) return null
	var vSample = 1																	// réduction au décodage
	while (maxOf(vBounds.outWidth, vBounds.outHeight) / (vSample * 2) >= kPhotoMax) vSample *= 2
	return BitmapFactory.decodeFile(inFile.path, BitmapFactory.Options().apply { inSampleSize = vSample })?.asImageBitmap()
}
