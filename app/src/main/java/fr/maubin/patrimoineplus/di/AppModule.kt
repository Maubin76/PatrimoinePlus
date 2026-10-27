package fr.maubin.patrimoineplus.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import fr.maubin.patrimoineplus.data.market.QuoteSource
import fr.maubin.patrimoineplus.data.market.YahooQuoteSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.maubin.patrimoineplus.data.settings.DataStoreSettingsRepository
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import java.time.Clock
import javax.inject.Singleton
import fr.maubin.patrimoineplus.data.update.ReleaseSource
import fr.maubin.patrimoineplus.data.update.GitHubReleaseSource
import fr.maubin.patrimoineplus.data.update.ApkInstaller
import fr.maubin.patrimoineplus.data.update.PackageApkInstaller
import fr.maubin.patrimoineplus.ui.update.AppBuild
import javax.inject.Named

/*
	Dépendances transverses de l'application.
*/
@Module
@InstallIn(SingletonComponent::class)
object AppModule
{
	/*
		Horloge système : injectée pour que la logique ne lise jamais la date
		courante directement, ce qui la rend testable.
	*/
	@Provides
	@Singleton
	fun provideClock(): Clock = Clock.systemDefaultZone()

	/*
		Magasin de préférences unique de l'application (fichier « settings »).
		inContext : contexte de l'application
	*/
	@Provides
	@Singleton
	fun provideDataStore(@ApplicationContext inContext: Context): DataStore<Preferences> =
		PreferenceDataStoreFactory.create { inContext.preferencesDataStoreFile("settings") }

	/*
		Cours publics de Yahoo Finance (classe du module commun, sans injection).
	*/
	@Provides
	@Singleton
	fun provideQuoteSource(): QuoteSource = YahooQuoteSource()

	/*
		Dernière release publiée sur GitHub (mises à jour).
	*/
	@Provides
	@Singleton
	fun provideReleaseSource(): ReleaseSource = GitHubReleaseSource()

	/*
		Version installée de l'appli.
	*/
	@Provides
	@Named("appVersion")
	fun provideAppVersion(): String = AppBuild.version

	/*
		Vérification des mises à jour au lancement (en release seulement).
	*/
	@Provides
	@Named("autoUpdateCheck")
	fun provideAutoUpdateCheck(): Boolean = AppBuild.autoCheck
}

/*
	Liaisons entre interfaces et implémentations.
*/
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule
{
	/*
		Réglages stockés en DataStore.
		inImpl : implémentation DataStore
	*/
	@Binds
	abstract fun bindSettings(inImpl: DataStoreSettingsRepository): SettingsRepository

	/*
		Installation des mises à jour par l'installeur du système.
		inImpl : implémentation PackageInstaller
	*/
	@Binds
	abstract fun bindInstaller(inImpl: PackageApkInstaller): ApkInstaller

}
