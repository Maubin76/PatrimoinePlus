package fr.maubin.patrimoineplus.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.maubin.patrimoineplus.data.backup.AutoBackup
import fr.maubin.patrimoineplus.data.backup.BackupOutcome
import fr.maubin.patrimoineplus.data.backup.BackupRepository
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.data.sync.DataStoreSyncStore
import fr.maubin.patrimoineplus.data.sync.SafSyncFolderFactory
import fr.maubin.patrimoineplus.data.sync.SyncFolderFactory
import fr.maubin.patrimoineplus.data.sync.SyncHooks
import fr.maubin.patrimoineplus.data.sync.SyncStore
import fr.maubin.patrimoineplus.data.sync.Syncer
import fr.maubin.patrimoineplus.data.sync.dataChanges
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/*
	Actions de l'appli autour d'un import par synchronisation : sauvegarde de
	sécurité dans Documents/PatrimoinePlus avant, assistant du premier
	lancement marqué comme fait après.
	inAutoBackup : sauvegarde
	inSettings   : réglages
*/
class AppSyncHooks @Inject constructor(
	private val inAutoBackup : AutoBackup,
	private val inSettings   : SettingsRepository
) : SyncHooks
{
	/*
		Sauvegarde les données actuelles avant leur remplacement ; faux si la
		sauvegarde a échoué (une base sans compte n'a rien à sauvegarder).
	*/
	override suspend fun beforeImport(): Boolean = inAutoBackup.backupNow() !is BackupOutcome.Failed

	/*
		Les données importées rendent l'assistant inutile.
	*/
	override suspend fun afterImport()
	{
		inSettings.setOnboardingDone(true)
	}
}

/*
	Synchronisation par fichier : service unique et ses dépendances.
*/
@Module
@InstallIn(SingletonComponent::class)
object SyncModule
{
	/*
		Service de synchronisation, déclenché par les modifications des données.
	*/
	@Provides
	@Singleton
	fun provideSyncer(
		inBackup       : BackupRepository,
		inStore        : SyncStore,
		inFolders      : SyncFolderFactory,
		inHooks        : SyncHooks,
		inAccounts     : AccountRepository,
		inCategories   : CategoryRepository,
		inTransactions : TransactionRepository,
		inMarket       : MarketRepository,
		inClock        : Clock
	): Syncer = Syncer(inBackup, inStore, inFolders, inHooks, dataChanges(inAccounts, inCategories, inTransactions, inMarket), inClock)
}

/*
	Liaisons de la synchronisation.
*/
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindings
{
	/*
		Mémoire dans le DataStore des réglages.
		inImpl : implémentation
	*/
	@Binds
	abstract fun bindStore(inImpl: DataStoreSyncStore): SyncStore

	/*
		Dossiers choisis par le sélecteur du système.
		inImpl : implémentation
	*/
	@Binds
	abstract fun bindFolders(inImpl: SafSyncFolderFactory): SyncFolderFactory

	/*
		Actions de l'appli autour d'un import.
		inImpl : implémentation
	*/
	@Binds
	abstract fun bindHooks(inImpl: AppSyncHooks): SyncHooks
}
