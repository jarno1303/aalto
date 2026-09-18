package fi.aalto.radio

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaltoDatabaseMigrationTest {
    private lateinit var context: Context
    private val databaseNamesToDelete = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        databaseNamesToDelete.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun phase2FavoritesSurvivePhase3Migration() = runTest {
        val databaseName = uniqueDatabaseName()
        createPhase2Database(databaseName) { dao ->
            dao.insertStations(phase2Stations("radio-rock", "ylex"))
            dao.insertFavorite(Phase2FavoriteEntity("radio-rock", createdAt = 10L, sortOrder = 1L))
            dao.insertFavorite(Phase2FavoriteEntity("ylex", createdAt = 9L, sortOrder = 0L))
        }

        val database = openPhase3Database(databaseName)
        try {
            val dao = database.localRadioDao()

            assertEquals(listOf("ylex", "radio-rock"), dao.favoriteIds())
            val favorite = requireNotNull(dao.favoriteById("radio-rock"))
            assertFalse(favorite.isDeleted)
            assertEquals(0L, favorite.logicalVersion)
            assertEquals(LocalRadioDao.LEGACY_DEVICE_ID, favorite.modifiedByDeviceId)
        } finally {
            database.close()
        }
    }

    @Test
    fun phase2RecentHistorySurvivesPhase3Migration() = runTest {
        val databaseName = uniqueDatabaseName()
        createPhase2Database(databaseName) { dao ->
            dao.insertStations(phase2Stations("radio-rock", "ylex"))
            dao.insertRecent(Phase2RecentStationEntity("radio-rock", lastPlayedAt = 20L))
            dao.insertRecent(Phase2RecentStationEntity("ylex", lastPlayedAt = 30L))
        }

        val database = openPhase3Database(databaseName)
        try {
            assertEquals(
                listOf("ylex", "radio-rock"),
                database.localRadioDao().recentStationIds(limit = 10)
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun phase2FavoriteOrderingSurvivesPhase3Migration() = runTest {
        val databaseName = uniqueDatabaseName()
        createPhase2Database(databaseName) { dao ->
            dao.insertStations(phase2Stations("radio-rock", "ylex", "radio-helsinki"))
            dao.insertFavorite(Phase2FavoriteEntity("radio-rock", createdAt = 20L, sortOrder = 2L))
            dao.insertFavorite(Phase2FavoriteEntity("ylex", createdAt = 10L, sortOrder = 0L))
            dao.insertFavorite(Phase2FavoriteEntity("radio-helsinki", createdAt = 15L, sortOrder = 1L))
        }

        val database = openPhase3Database(databaseName)
        try {
            assertEquals(
                listOf("ylex", "radio-helsinki", "radio-rock"),
                database.localRadioDao().favoriteIds()
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun phase3MigrationCanBeOpenedAgainSafely() = runTest {
        val databaseName = uniqueDatabaseName()
        createPhase2Database(databaseName) { dao ->
            dao.insertStations(phase2Stations("radio-rock"))
            dao.insertFavorite(Phase2FavoriteEntity("radio-rock", createdAt = 10L, sortOrder = 0L))
        }

        val firstOpen = openPhase3Database(databaseName)
        try {
            assertEquals(listOf("radio-rock"), firstOpen.localRadioDao().favoriteIds())
        } finally {
            firstOpen.close()
        }

        val secondOpen = openPhase3Database(databaseName)
        try {
            assertNotNull(secondOpen.localRadioDao().favoriteById("radio-rock"))
        } finally {
            secondOpen.close()
        }
    }

    private suspend fun createPhase2Database(
        databaseName: String,
        seed: suspend (Phase2Dao) -> Unit
    ) {
        val database = Room.databaseBuilder(context, Phase2AaltoDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            seed(database.phase2Dao())
        } finally {
            database.close()
        }
    }

    private fun openPhase3Database(databaseName: String): AaltoDatabase {
        return Room.databaseBuilder(context, AaltoDatabase::class.java, databaseName)
            .addMigrations(AaltoDatabase.MIGRATION_1_2, AaltoDatabase.MIGRATION_2_3, AaltoDatabase.MIGRATION_3_4, AaltoDatabase.MIGRATION_4_5, AaltoDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
    }

    private fun phase2Stations(vararg stationIds: String): List<Phase2StationEntity> {
        return stationIds.map { stationId ->
            val station = requireNotNull(StationCatalog.stationById(stationId))
            Phase2StationEntity(
                id = station.id,
                radioBrowserStationUuid = station.radioBrowserStationUuid,
                name = station.name,
                description = station.description,
                initials = station.initials,
                logoColorArgb = station.logoColorArgb,
                streamUrl = station.streamUrl,
                preferredStreamUrl = station.preferredStreamUrl,
                lastKnownWorkingStreamUrl = station.lastKnownWorkingStreamUrl,
                faviconUrl = station.faviconUrl,
                countryCode = station.countryCode,
                tags = station.tags.joinToString(separator = "|"),
                category = station.category,
                streamHealthStatus = station.streamHealthStatus,
                updatedAt = 1L
            )
        }
    }

    private fun uniqueDatabaseName(): String {
        return "aalto_migration_test_${UUID.randomUUID()}.db"
            .also { databaseNamesToDelete.add(it) }
    }
}

@Database(
    entities = [
        Phase2StationEntity::class,
        Phase2FavoriteEntity::class,
        Phase2RecentStationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class Phase2AaltoDatabase : RoomDatabase() {
    abstract fun phase2Dao(): Phase2Dao
}

@Dao
interface Phase2Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<Phase2StationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: Phase2FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recentStation: Phase2RecentStationEntity)
}

@Entity(
    tableName = "stations",
    indices = [
        Index(value = ["radioBrowserStationUuid"])
    ]
)
data class Phase2StationEntity(
    @PrimaryKey val id: String,
    val radioBrowserStationUuid: String?,
    val name: String,
    val description: String,
    val initials: String,
    val logoColorArgb: Long,
    val streamUrl: String,
    val preferredStreamUrl: String?,
    val lastKnownWorkingStreamUrl: String?,
    val faviconUrl: String?,
    val countryCode: String?,
    val tags: String?,
    val category: String,
    val streamHealthStatus: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = Phase2StationEntity::class,
            parentColumns = ["id"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["stationId"])
    ]
)
data class Phase2FavoriteEntity(
    @PrimaryKey val stationId: String,
    val createdAt: Long,
    val sortOrder: Long
)

@Entity(
    tableName = "recent_stations",
    foreignKeys = [
        ForeignKey(
            entity = Phase2StationEntity::class,
            parentColumns = ["id"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["lastPlayedAt"])
    ]
)
data class Phase2RecentStationEntity(
    @PrimaryKey val stationId: String,
    val lastPlayedAt: Long
)
