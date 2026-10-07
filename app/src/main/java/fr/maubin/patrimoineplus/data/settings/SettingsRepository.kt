package fr.maubin.patrimoineplus.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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
	private val kOnboardingKey    = booleanPreferencesKey("onboarding_done")	// clé du premier lancement
	private val kLastAccountKey   = stringPreferencesKey("last_account")	// clé du dernier compte utilisé

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
}
