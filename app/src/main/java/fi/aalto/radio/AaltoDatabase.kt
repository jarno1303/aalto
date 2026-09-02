package fi.aalto.radio

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StationEntity::class,
        FavoriteEntity::class,
        RecentStationEntity::class,
        FavoriteOrderStateEntity::class,
        SyncMutationEntity::class,
        AppliedSyncMutationEntity::class,
        SyncMetadataEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AaltoDatabase : RoomDatabase() {
    abstract fun localRadioDao(): LocalRadioDao

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

        @Volatile
        private var instance: AaltoDatabase? = null

        fun getInstance(context: Context): AaltoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AaltoDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
