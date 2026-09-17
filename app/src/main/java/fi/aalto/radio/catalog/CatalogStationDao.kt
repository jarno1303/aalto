package fi.aalto.radio.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class CatalogStationDao {
    @Query("SELECT * FROM catalog_stations WHERE source = :source AND countryCode = :countryCode ORDER BY clickCount DESC, votes DESC, canonicalName ASC")
    abstract suspend fun stationsByCountry(source: String, countryCode: String): List<CatalogStationEntity>

    @Query("SELECT * FROM catalog_stations WHERE sourceQualifiedId = :sourceQualifiedId LIMIT 1")
    abstract suspend fun stationBySourceQualifiedId(sourceQualifiedId: String): CatalogStationEntity?

    @Query("SELECT * FROM catalog_country_cache_metadata WHERE source = :source AND countryCode = :countryCode LIMIT 1")
    abstract suspend fun countryMetadata(source: String, countryCode: String): CatalogCountryCacheMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertStations(stations: List<CatalogStationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMetadata(metadata: CatalogCountryCacheMetadataEntity)

    @Query("DELETE FROM catalog_stations WHERE source = :source AND countryCode = :countryCode")
    protected abstract suspend fun deleteCountry(source: String, countryCode: String)

    @Query("DELETE FROM catalog_stations")
    abstract suspend fun deleteAllStations()

    @Query("DELETE FROM catalog_country_cache_metadata")
    abstract suspend fun deleteAllMetadata()

    @Transaction
    open suspend fun replaceCountryCatalog(
        source: String,
        countryCode: String,
        stations: List<CatalogStationEntity>,
        metadata: CatalogCountryCacheMetadataEntity
    ) {
        deleteCountry(source, countryCode)
        insertStations(stations)
        insertMetadata(metadata)
    }

    @Transaction
    open suspend fun markCountryFetchAttempt(source: String, countryCode: String, attemptedAt: Long) {
        val current = countryMetadata(source, countryCode)
        insertMetadata(
            current?.copy(lastAttemptAt = attemptedAt)
                ?: CatalogCountryCacheMetadataEntity(source, countryCode, null, attemptedAt, 0)
        )
    }
}
