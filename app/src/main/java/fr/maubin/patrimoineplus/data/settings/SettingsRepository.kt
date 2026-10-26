package fr.maubin.patrimoineplus.data.settings

import fr.maubin.patrimoineplus.ui.theme.ThemeMode
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.core.stringSetPreferencesKey
import fr.maubin.patrimoineplus.ui.theme.ColorTheme

/*
	Réglages de l'application, mémorisés entre les sessions.
*/
interface SettingsRepository
{
	// Vrai quand les montants sont masqués (mode discret).
	val amountsHidden: Flow<Boolean>

	/*
		Active ou désactive le mode discret.
		inHidden : vrai pour masquer les montants
	*/
	suspend fun setAmountsHidden(inHidden: Boolean)

	// Apparence choisie : suivre le système, clair ou sombre.
	val themeMode: Flow<ThemeMode>

	// Thème de couleurs (Monochrome par défaut ; ancien réglage vert et rouge : Minimal).
	val colorTheme: Flow<ColorTheme>

	/*
		Choisit le thème de couleurs.
		inTheme : thème
	*/
	suspend fun setColorTheme(inTheme: ColorTheme)

	// Clés des diagrammes de répartition affichés en cercle (barre par défaut).
	val pieCharts: Flow<Set<String>>

	// Effet verre (désactivé par défaut).
	val glassEffect: Flow<Boolean>

	// Vue affichée : "" premier profil, "*" famille, sinon identifiant du profil.
	val viewProfile: Flow<String>

	/*
		Mémorise la vue affichée.
		inView : "" premier profil, "*" famille, sinon identifiant du profil
	*/
	suspend fun setViewProfile(inView: String)

	/*
		Active ou retire l'effet verre.
		inGlass : vrai pour l'effet verre
	*/
	suspend fun setGlassEffect(inGlass: Boolean)

	/*
		Choisit la forme d'un diagramme de répartition (mémorisée pour lui seul).
		inKey : clé du diagramme
		inPie : vrai pour le cercle
	*/
	suspend fun setPieChart(inKey: String, inPie: Boolean)

	// Vrai une fois l'assistant du premier lancement terminé ou passé.
	val onboardingDone: Flow<Boolean>

	/*
		Marque l'assistant du premier lancement comme terminé (ou à refaire).
		inDone : vrai si terminé
	*/
	suspend fun setOnboardingDone(inDone: Boolean)

	/*
		Fixe l'apparence de l'application.
		inMode : apparence choisie
	*/
	suspend fun setThemeMode(inMode: ThemeMode)

	// Compte de la dernière opération saisie ; null au départ.
	val lastAccountId: Flow<String?>

	/*
		Mémorise le compte de la dernière opération saisie.
		inId : compte
	*/
	suspend fun setLastAccountId(inId: String)

	// Vrai si le verrou biométrique est activé.
	val lockEnabled: Flow<Boolean>

	/*
		Active ou désactive le verrou biométrique.
		inEnabled : vrai pour activer
	*/
	suspend fun setLockEnabled(inEnabled: Boolean)

	// Instant du dernier export (ms) ; null s'il n'y en a jamais eu.
	val lastExportAt: Flow<Long?>

	/*
		Mémorise l'instant du dernier export.
		inAt : instant (ms)
	*/
	suspend fun setLastExportAt(inAt: Long)

	// Instant de la dernière mise à jour automatique des cours (ms) ; null avant la première.
	val lastQuotesAt: Flow<Long?>

	/*
		Mémorise l'instant de la dernière mise à jour des cours.
		inAt : instant (ms)
	*/
	suspend fun setLastQuotesAt(inAt: Long)
}

/*
	Réglages stockés dans un DataStore de préférences, sur le téléphone.
	inDataStore : magasin de préférences de l'application
*/
@Singleton
class DataStoreSettingsRepository @Inject constructor(
	private val inDataStore : DataStore<Preferences>
) : SettingsRepository
{
	private val kAmountsHiddenKey = booleanPreferencesKey("amounts_hidden")	// clé du mode discret
	private val kThemeModeKey     = stringPreferencesKey("theme_mode")		// clé de l'apparence
	private val kTrendColorsKey   = booleanPreferencesKey("trend_colors")	// ancienne clé des couleurs de hausse et de baisse (lue pour la reprise)
	private val kColorThemeKey    = stringPreferencesKey("color_theme")		// clé du thème de couleurs
	private val kGlassKey         = booleanPreferencesKey("glass_effect")	// clé de l'effet verre
	private val kViewKey          = stringPreferencesKey("view_profile")	// clé de la vue affichée
	private val kPieChartsKey     = stringSetPreferencesKey("pie_chart_keys")	// clé des répartitions affichées en cercle
	private val kOnboardingKey    = booleanPreferencesKey("onboarding_done")	// clé du premier lancement
	private val kLastAccountKey   = stringPreferencesKey("last_account")	// clé du dernier compte utilisé
	private val kLockKey          = booleanPreferencesKey("lock_enabled")	// clé du verrou biométrique
	private val kLastExportKey    = longPreferencesKey("last_export")		// clé du dernier export
	private val kLastQuotesKey    = longPreferencesKey("last_quotes")		// clé de la dernière mise à jour des cours

	override val amountsHidden: Flow<Boolean> =
		inDataStore.data.map { inPreferences -> inPreferences[kAmountsHiddenKey] ?: false }

	/*
		Enregistre le mode discret.
		inHidden : vrai pour masquer les montants
	*/
	override suspend fun setAmountsHidden(inHidden: Boolean)
	{
		inDataStore.edit { inPreferences -> inPreferences[kAmountsHiddenKey] = inHidden }
	}

	override val colorTheme: Flow<ColorTheme> =
		inDataStore.data.map { inPreferences ->
			ColorTheme.entries.firstOrNull { it.name == inPreferences[kColorThemeKey] }
				?: if (inPreferences[kTrendColorsKey] == true) ColorTheme.MINIMAL else ColorTheme.MONOCHROME
		}

	/*
		Enregistre le thème de couleurs.
		inTheme : thème
	*/
	override suspend fun setColorTheme(inTheme: ColorTheme)
	{
		inDataStore.edit { inPreferences -> inPreferences[kColorThemeKey] = inTheme.name }
	}

	override val glassEffect: Flow<Boolean> =
		inDataStore.data.map { inPreferences -> inPreferences[kGlassKey] ?: false }

	/*
		Enregistre l'effet verre.
		inGlass : vrai pour l'effet verre
	*/
	override suspend fun setGlassEffect(inGlass: Boolean)
	{
		inDataStore.edit { inPreferences -> inPreferences[kGlassKey] = inGlass }
	}

	override val viewProfile: Flow<String> =
		inDataStore.data.map { inPreferences -> inPreferences[kViewKey].orEmpty() }

	/*
		Enregistre la vue affichée.
		inView : vue
	*/
	override suspend fun setViewProfile(inView: String)
	{
		inDataStore.edit { inPreferences -> inPreferences[kViewKey] = inView }
	}

	override val pieCharts: Flow<Set<String>> =
		inDataStore.data.map { inPreferences -> inPreferences[kPieChartsKey] ?: emptySet() }

	/*
		Enregistre la forme d'un diagramme de répartition.
		inKey : clé du diagramme
		inPie : vrai pour le cercle
	*/
	override suspend fun setPieChart(inKey: String, inPie: Boolean)
	{
		inDataStore.edit { inPreferences ->
			val vKeys = inPreferences[kPieChartsKey] ?: emptySet()					// répartitions en cercle
			inPreferences[kPieChartsKey] = if (inPie) vKeys + inKey else vKeys - inKey
		}
	}

	override val themeMode: Flow<ThemeMode> =
		inDataStore.data.map { inPreferences ->
			ThemeMode.entries.firstOrNull { it.name == inPreferences[kThemeModeKey] } ?: ThemeMode.SYSTEM
		}

	/*
		Enregistre l'apparence choisie.
		inMode : apparence choisie
	*/
	override suspend fun setThemeMode(inMode: ThemeMode)
	{
		inDataStore.edit { inPreferences -> inPreferences[kThemeModeKey] = inMode.name }
	}

	override val onboardingDone: Flow<Boolean> =
		inDataStore.data.map { inPreferences -> inPreferences[kOnboardingKey] ?: false }

	/*
		Enregistre la fin (ou la remise à zéro) de l'assistant du premier lancement.
		inDone : vrai si terminé
	*/
	override suspend fun setOnboardingDone(inDone: Boolean)
	{
		inDataStore.edit { inPreferences -> inPreferences[kOnboardingKey] = inDone }
	}

	override val lastAccountId: Flow<String?> =
		inDataStore.data.map { inPreferences -> inPreferences[kLastAccountKey] }

	/*
		Mémorise le compte de la dernière opération saisie.
		inId : compte
	*/
	override suspend fun setLastAccountId(inId: String)
	{
		inDataStore.edit { inPreferences -> inPreferences[kLastAccountKey] = inId }
	}

	override val lockEnabled: Flow<Boolean> =
		inDataStore.data.map { inPreferences -> inPreferences[kLockKey] ?: false }

	/*
		Enregistre l'activation du verrou biométrique.
		inEnabled : vrai pour activer
	*/
	override suspend fun setLockEnabled(inEnabled: Boolean)
	{
		inDataStore.edit { inPreferences -> inPreferences[kLockKey] = inEnabled }
	}

	override val lastExportAt: Flow<Long?> =
		inDataStore.data.map { inPreferences -> inPreferences[kLastExportKey] }

	/*
		Enregistre l'instant du dernier export.
		inAt : instant (ms)
	*/
	override suspend fun setLastExportAt(inAt: Long)
	{
		inDataStore.edit { inPreferences -> inPreferences[kLastExportKey] = inAt }
	}

	override val lastQuotesAt: Flow<Long?> =
		inDataStore.data.map { inPreferences -> inPreferences[kLastQuotesKey] }

	/*
		Enregistre l'instant de la dernière mise à jour des cours.
		inAt : instant (ms)
	*/
	override suspend fun setLastQuotesAt(inAt: Long)
	{
		inDataStore.edit { inPreferences -> inPreferences[kLastQuotesKey] = inAt }
	}
}
