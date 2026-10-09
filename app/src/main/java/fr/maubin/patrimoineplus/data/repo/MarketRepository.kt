package fr.maubin.patrimoineplus.data.repo

import androidx.room.withTransaction
import fr.maubin.patrimoineplus.data.db.PatrimoineDatabase
import fr.maubin.patrimoineplus.data.db.toDomain
import fr.maubin.patrimoineplus.data.db.toEntity
import fr.maubin.patrimoineplus.domain.model.AccountStatement
import fr.maubin.patrimoineplus.domain.model.Asset
import fr.maubin.patrimoineplus.domain.model.AssetPrice
import fr.maubin.patrimoineplus.domain.rules.SetupError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/*
	Dépôt des actifs, de leurs cours et des relevés de solde : tout ce qui
	sert à valoriser et à contrôler.
	inDb    : base locale
	inClock : horloge (horodatages)
*/
@Singleton
class MarketRepository @Inject constructor(
	private val inDb    : PatrimoineDatabase,
	private val inClock : Clock
)
{
	private val fAssets     = inDb.assetDao()									// accès aux actifs
	private val fStatements = inDb.statementDao()								// accès aux relevés

	// Tous les actifs.
	fun observeAssets(): Flow<List<Asset>> = fAssets.observeAll().map { inList -> inList.map { it.toDomain() } }

	// Tous les cours relevés.
	fun observePrices(): Flow<List<AssetPrice>> = fAssets.observePrices().map { inList -> inList.map { it.toDomain() } }

	// Tous les relevés de solde.
	fun observeStatements(): Flow<List<AccountStatement>> = fStatements.observeAll().map { inList -> inList.map { it.toDomain() } }

	/*
		Crée un actif.
		inName : nom
		inIsin : code ISIN, facultatif
	*/
	suspend fun createAsset(
		inName : String,
		inIsin : String? = null
	): SaveResult<SetupError>
	{
		if (inName.isBlank())
		{
			return SaveResult.Rejected(listOf(SetupError.BlankName))
		}
		val vAsset = Asset(UUID.randomUUID().toString(), inName.trim(), inIsin?.trim()?.uppercase()?.ifEmpty { null })	// nouvel actif
		addAssets(listOf(vAsset))
		return SaveResult.Saved(vAsset.id)
	}

	/*
		Ajoute des actifs déjà construits (démo).
		inAssets : actifs
	*/
	suspend fun addAssets(inAssets: List<Asset>)
	{
		val vNow = inClock.millis()												// horodatage
		fAssets.insertAll(inAssets.map { it.toEntity(vNow, vNow) })
	}

	/*
		Enregistre le cours d'un actif à une date (remplace celui du même jour).
		inAssetId   : actif
		inDate      : date
		inUnitPrice : cours, en dix-millièmes d'euro
	*/
	suspend fun putPrice(
		inAssetId   : String,
		inDate      : LocalDate,
		inUnitPrice : Long
	)
	{
		fAssets.putPrice(AssetPrice(UUID.randomUUID().toString(), inAssetId, inDate, inUnitPrice).toEntity())
	}

	/*
		Modifie le nom, l'ISIN et le symbole de cotation d'un actif.
		inId     : actif
		inName   : nom
		inIsin   : code ISIN, facultatif
		inTicker : symbole de cotation, facultatif
	*/
	suspend fun updateAsset(
		inId     : String,
		inName   : String,
		inIsin   : String?,
		inTicker : String?
	): SaveResult<SetupError>
	{
		if (inName.isBlank())
		{
			return SaveResult.Rejected(listOf(SetupError.BlankName))
		}
		val vEntity = fAssets.get(inId) ?: return SaveResult.Rejected(listOf(SetupError.NotFound))	// actif enregistré
		fAssets.update(
			vEntity.copy(
				name      = inName.trim(),
				isin      = inIsin?.trim()?.uppercase()?.ifEmpty { null },
				ticker    = inTicker?.trim()?.uppercase()?.ifEmpty { null },
				updatedAt = inClock.millis()
			)
		)
		return SaveResult.Saved(inId)
	}

	/*
		Enregistre une série de cours d'un actif (un par jour, remplaçant celui
		du même jour), en une seule transaction.
		inAssetId : actif
		inPrices  : cours par jour, en dix-millièmes d'euro
	*/
	suspend fun putPriceSeries(
		inAssetId : String,
		inPrices  : List<Pair<LocalDate, Long>>
	)
	{
		inDb.withTransaction {
			inPrices.forEach { (inDate, inPrice) -> putPrice(inAssetId, inDate, inPrice) }
		}
	}

	/*
		Enregistre un relevé de solde.
		inAccountId    : compte
		inDate         : date du relevé
		inBalanceCents : solde indiqué par la banque
	*/
	suspend fun putStatement(
		inAccountId    : String,
		inDate         : LocalDate,
		inBalanceCents : Long
	)
	{
		fStatements.upsert(AccountStatement(UUID.randomUUID().toString(), inAccountId, inDate, inBalanceCents).toEntity())
	}

	/*
		Enregistre plusieurs cours à une même date (mise à jour groupée).
		inDate   : date des cours
		inPrices : cours par actif, en dix-millièmes d'euro
	*/
	suspend fun putPrices(
		inDate   : LocalDate,
		inPrices : Map<String, Long>
	)
	{
		inDb.withTransaction {
			inPrices.forEach { (inAssetId, inPrice) -> putPrice(inAssetId, inDate, inPrice) }
		}
	}

	/*
		Supprime un relevé de solde.
		inId : relevé
	*/
	suspend fun deleteStatement(inId: String)
	{
		fStatements.deleteById(inId)
	}
}
