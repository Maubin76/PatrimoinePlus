package fr.maubin.patrimoineplus.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.maubin.patrimoineplus.data.backup.AutoBackup
import fr.maubin.patrimoineplus.data.backup.BackupOutcome
import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import fr.maubin.patrimoineplus.data.settings.SettingsRepository
import fr.maubin.patrimoineplus.data.sync.DataChanges
import fr.maubin.patrimoineplus.data.sync.DataStoreSyncStore
import fr.maubin.patrimoineplus.data.sync.SyncHooks
import fr.maubin.patrimoineplus.data.sync.SyncStore
import fr.maubin.patrimoineplus.data.sync.dataChanges
import javax.inject.Inject
import javax.inject.Singleton
import fr.maubin.patrimoineplus.data.repo.PlanningRepository

/*
	Actions de l'appli autour d'un import par synchronisation : sauvegarde de
	sécurité dans Documents/PatrimoinePlus avant, assistant du premier
	lancement marqué comme fait après.
	inAutoBackup : sauvegarde
	inSettings   : réglages
*/
class AppSyncHooks @Inject constructor(
	private val inAutoBackup : AutoBackup,
	private val inSettings   : SettingsRepository,
	private val inPlanning   : PlanningRepository
) : SyncHooks
{
	/*
		Sauvegarde les données actuelles avant leur remplacement ; faux si la
		sauvegarde a échoué (une base sans compte n'a rien à sauvegarder).
	*/
	override suspend fun beforeImport(): Boolean = inAutoBackup.backupNow() !is BackupOutcome.Failed

	/*
		Les données importées rendent l'assistant inutile ; les échéances
		arrivées depuis sont créées (les mêmes que sur le PC, à l'identique).
	*/
	override suspend fun afterImport()
	{
		inSettings.setOnboardingDone(true)
		inPlanning.applyDue()
	}
}

/*
	Synchronisation Wi-Fi : signal de modification des données.
*/
@Module
@InstallIn(SingletonComponent::class)
object SyncModule
{
	/*
		Signal de modification des données (comptes, opérations, cours, plans).
		inAccounts     : comptes
		inCategories   : catégories
		inTransactions : opérations
		inMarket       : actifs, cours et relevés
		inPlanning     : opérations récurrentes et crédits
	*/
	@Provides
	@Singleton
	fun provideChanges(
		inAccounts     : AccountRepository,
		inCategories   : CategoryRepository,
		inTransactions : TransactionRepository,
		inMarket       : MarketRepository,
		inPlanning     : PlanningRepository
	): DataChanges = DataChanges(dataChanges(inAccounts, inCategories, inTransactions, inMarket, inPlanning))
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
		Actions de l'appli autour d'un import.
		inImpl : implémentation
	*/
	@Binds
	abstract fun bindHooks(inImpl: AppSyncHooks): SyncHooks
}
