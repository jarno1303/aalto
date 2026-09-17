package fi.aalto.radio

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fi.aalto.radio.catalog.CatalogCountryCacheMetadataEntity
import fi.aalto.radio.catalog.CatalogStationDao
import fi.aalto.radio.catalog.CatalogStationEntity

@Database(
    entities = [
        StationEntity::class,
        FavoriteEntity::class,
        RecentStationEntity::class,
        FavoriteOrderStateEntity::class,
        SyncMutationEntity::class,
        AppliedSyncMutationEntity::class,
        SyncMetadataEntity::class,
        CatalogStationEntity::class,
        CatalogCountryCacheMetadataEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AaltoDatabase : RoomDatabase() {
    abstract fun localRadioDao(): LocalRadioDao
    abstract fun catalogStationDao(): CatalogStationDao

    companion object {
        private const val DATABASE_NAME = "aalto_local.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE favorites ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE favorites ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE favorites ADD COLUMN logicalVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE favorites ADD COLUMN modifiedByDeviceId TEXT NOT NULL DEFAULT 'legacy'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_order_state (
                        id TEXT NOT NULL,
                        orderedStationIds TEXT NOT NULL,
                        logicalVersion INTEGER NOT NULL,
                        modifiedByDeviceId TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_mutations (
                        mutationId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payload TEXT,
                        deviceId TEXT NOT NULL,
                        logicalVersion INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL,
                        nextAttemptAt INTEGER,
                        state TEXT NOT NULL,
                        lastError TEXT,
                        PRIMARY KEY(mutationId)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_mutations_state_nextAttemptAt ON sync_mutations(state, nextAttemptAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_mutations_entityType_entityId ON sync_mutations(entityType, entityId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_applied_mutations (
                        mutationId TEXT NOT NULL,
                        appliedAt INTEGER NOT NULL,
                        PRIMARY KEY(mutationId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_metadata (
                        `key` TEXT NOT NULL,
                        longValue INTEGER NOT NULL,
                        stringValue TEXT,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN serverRevision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE favorites ADD COLUMN baseServerRevision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sync_mutations ADD COLUMN baseServerRevision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sync_mutations ADD COLUMN serverRevision INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS catalog_stations (
                        source TEXT NOT NULL,
                        sourceStationId TEXT NOT NULL,
                        sourceQualifiedId TEXT NOT NULL,
                        canonicalName TEXT NOT NULL,
                        streamUrl TEXT,
                        resolvedStreamUrl TEXT,
                        homepageUrl TEXT,
                        faviconUrl TEXT,
                        countryCode TEXT,
                        countryName TEXT,
                        region TEXT,
                        languages TEXT NOT NULL,
                        rawTags TEXT NOT NULL,
                        codec TEXT,
                        bitrateKbps INTEGER,
                        votes INTEGER,
                        clickCount INTEGER,
                        clickTrend INTEGER,
                        lastCheckOk INTEGER,
                        lastCheckAt INTEGER,
                        latitude REAL,
                        longitude REAL,
                        cachedAt INTEGER NOT NULL,
                        PRIMARY KEY(sourceQualifiedId)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_stations_source_countryCode ON catalog_stations(source, countryCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_stations_countryCode ON catalog_stations(countryCode)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS catalog_country_cache_metadata (
                        source TEXT NOT NULL,
                        countryCode TEXT NOT NULL,
                        lastSuccessfulRefreshAt INTEGER,
                        lastAttemptAt INTEGER,
                        stationCount INTEGER NOT NULL,
                        PRIMARY KEY(source, countryCode)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catalog_stations ADD COLUMN canonicalId TEXT")
                db.execSQL("ALTER TABLE catalog_stations ADD COLUMN logoCandidates TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE catalog_stations ADD COLUMN streamAlternatives TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE catalog_stations ADD COLUMN broadcaster TEXT")
                db.execSQL("ALTER TABLE catalog_stations ADD COLUMN network TEXT")
            }
        }

        @Volatile
        private var instance: AaltoDatabase? = null

        fun getInstance(context: Context): AaltoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AaltoDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
