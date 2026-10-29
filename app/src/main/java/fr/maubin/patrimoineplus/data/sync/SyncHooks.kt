package fr.maubin.patrimoineplus.data.sync

import fr.maubin.patrimoineplus.data.repo.AccountRepository
import fr.maubin.patrimoineplus.data.repo.CategoryRepository
import fr.maubin.patrimoineplus.data.repo.MarketRepository
import fr.maubin.patrimoineplus.data.repo.PlanningRepository
import fr.maubin.patrimoineplus.data.repo.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/*
	Actions autour d'un import (sauvegarde de sécurité avant, assistant du
	premier lancement marqué comme fait après).
*/
interface SyncHooks
{
	/*
		Avant de remplacer les données locales ; faux si la sauvegarde de
		sécurité a échoué (l'import n'a alors pas lieu).
	*/
	suspend fun beforeImport(): Boolean

	/*
		Après avoir remplacé les données locales.
	*/
	suspend fun afterImport()
}

/*
	Signal de modification des données, qui déclenche la synchronisation Wi-Fi
	quelques secondes après chaque saisie.
	flow : une valeur à chaque changement
*/
class DataChanges(val flow: Flow<Any>)

/*
	Signal de modification des données : une valeur à chaque changement des
	comptes, catégories, actifs, opérations, cours ou relevés.
	inAccounts     : comptes
	inCategories   : catégories
	inTransactions : opérations
	inMarket       : actifs, cours et relevés
	inPlanning     : opérations récurrentes et crédits
*/
fun dataChanges(
	inAccounts     : AccountRepository,
	inCategories   : CategoryRepository,
	inTransactions : TransactionRepository,
	inMarket       : MarketRepository,
	inPlanning     : PlanningRepository
): Flow<Any> =
	combine(
		combine(
			inAccounts.observeAccounts(), inCategories.observeCategories(), inTransactions.observeTransactions(),
			inMarket.observeAssets(), inMarket.observePrices(), inMarket.observeStatements()
		) { inValues -> inValues.toList() },
		inPlanning.observeRecurrences(),
		inPlanning.observeLoans()
	) { inData, inRecs, inLoans -> listOf(inData, inRecs, inLoans) }
