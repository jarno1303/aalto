package fi.aalto.radio

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class StationRepositoryRoomTest {
    private lateinit var context: Context
    private val openDatabases = mutableListOf<AaltoDatabase>()
    private val databaseNamesToDelete = mutableListOf<String>()
    private var now = 1_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        now = 1_000L
    }

    @After
    fun tearDown() {
        openDatabases.forEach { it.close() }
        databaseNamesToDelete.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun favoriteAddPersistsInRoom() = runTest {
        val database = inMemoryDatabase()
        val repository = repository(database)

        repository.prepareLocalData()
        repository.addFavorite("radio-helsinki")

        assertTrue(repository.favoriteIdsSnapshot().contains("radio-helsinki"))
    }

    @Test
    fun favoriteRemovePersistsInRoom() = runTest {
        val database = inMemoryDatabase()
        val repository = repository(database)

        repository.prepareLocalData()
        repository.addFavorite("radio-helsinki")
        repository.removeFavorite("radio-helsinki")

        assertFalse(repository.favoriteIdsSnapshot().contains("radio-helsinki"))
    }

    @Test
    fun duplicateFavoriteIsPrevented() = runTest {
        val database = inMemoryDatabase()
        val repository = repository(database)

        repository.prepareLocalData()
        repository.addFavorite("radio-helsinki")
        repository.addFavorite("radio-helsinki")

        assertEquals(1, database.localRadioDao().activeFavoriteCount("radio-helsinki"))
    }

    @Test
    fun reorderedFavoritesSurviveDatabaseReopen() = runTest {
        val databaseName = uniqueDatabaseName()
        val store = legacyStore()
        store.saveLegacyFavoriteIds(setOf("ylex", "radio-rock", "radio-helsinki", "radio-suomipop"))
        var database = fileDatabase(databaseName)
        var repository = repository(database, store)

        repository.prepareLocalData()
        listOf("ylex", "radio-rock", "radio-helsinki", "radio-suomipop")
            .forEach { repository.addFavorite(it) }
        repository.reorderFavorites(listOf("radio-suomipop", "ylex", "radio-rock", "radio-helsinki"))
        assertEquals(
            listOf("radio-suomipop", "ylex", "radio-rock", "radio-helsinki"),
            database.localRadioDao().favoriteIds()
        )

        database.close()
        openDatabases.remove(database)
        database = fileDatabase(databaseName)
        repository = repository(database, store)

        assertEquals(
            listOf("radio-suomipop", "ylex", "radio-rock", "radio-helsinki"),
            database.localRadioDao().favoriteIds()
        )
    }

    @Test
    fun preparingCatalogDoesNotOverwriteCustomFavoriteOrder() = runTest {
        val database = inMemoryDatabase()
        val store = legacyStore()
        store.saveLegacyFavoriteIds(setOf("ylex", "radio-rock", "radio-helsinki"))
        val repository = repository(database, store)

        repository.prepareLocalData()
        listOf("ylex", "radio-rock", "radio-helsinki")
            .forEach { repository.addFavorite(it) }
        repository.reorderFavorites(listOf("radio-helsinki", "ylex", "radio-rock"))
        repository.prepareLocalData()

        assertEquals(
            listOf("radio-helsinki", "ylex", "radio-rock"),
            database.localRadioDao().favoriteIds()
        )
    }

    @Test
    fun addingFavoriteAppendsWithoutResettingExistingOrder() = runTest {
        val database = inMemoryDatabase()
        val store = legacyStore()
        store.saveLegacyFavoriteIds(setOf("ylex", "radio-rock"))
        val repository = repository(database, store)

        repository.prepareLocalData()
        repository.addFavorite("ylex")
        repository.addFavorite("radio-rock")
        repository.reorderFavorites(listOf("radio-rock", "ylex"))
        repository.addFavorite("radio-helsinki")

        assertEquals(
            listOf("radio-rock", "ylex", "radio-helsinki"),
            database.localRadioDao().favoriteIds()
        )
    }

    @Test
    fun removingFavoritePreservesRelativeOrder() = runTest {
        val database = inMemoryDatabase()
        val store = legacyStore()
        store.saveLegacyFavoriteIds(setOf("ylex", "radio-rock", "radio-helsinki"))
        val repository = repository(database, store)

        repository.prepareLocalData()
        listOf("ylex", "radio-rock", "radio-helsinki")
            .forEach { repository.addFavorite(it) }
        repository.reorderFavorites(listOf("radio-helsinki", "ylex", "radio-rock"))
        repository.removeFavorite("ylex")

        assertEquals(
            listOf("radio-helsinki", "radio-rock"),
            database.localRadioDao().favoriteIds()
        )
    }

    @Test
    fun oneReorderCommitCreatesOneReorderMutation() = runTest {
        val database = inMemoryDatabase()
        val store = legacyStore()
        store.saveLegacyFavoriteIds(setOf("ylex", "radio-rock", "radio-helsinki"))
        val repository = repository(database, store)

        repository.prepareLocalData()
        listOf("ylex", "radio-rock", "radio-helsinki")
            .forEach { repository.addFavorite(it) }
        val before = database.localRadioDao().allSyncMutations().size
        repository.reorderFavorites(listOf("radio-helsinki", "ylex", "radio-rock"))
        val mutations = database.localRadioDao().allSyncMutations()

        assertEquals(before + 1, mutations.size)
        assertEquals(
            1,
            mutations.count { it.operation == SyncOperation.REORDER_FAVORITES.value }
        )
    }

    @Test
    fun favoritePersistsAcrossRepositoryAndDatabaseRecreation() = runTest {
        val databaseName = uniqueDatabaseName()
        val store = legacyStore()
        var database = fileDatabase(databaseName)
        var repository = repository(database, store)

        repository.prepareLocalData()
        repository.addFavorite("radio-helsinki")
        database.close()
        openDatabases.remove(database)

        database = fileDatabase(databaseName)
        repository = repository(database, store)

        assertTrue(repository.favoriteIdsSnapshot().contains("radio-helsinki"))
    }

    @Test
    fun sharedPreferencesFavoritesMigrateToRoom() = runTest {
        val database = inMemoryDatabase()
        val store = legacyStore()
        store.saveLegacyFavoriteIds(setOf("radio-helsinki", "radio-rock"))
        val repository = repository(database, store)

        repository.prepareLocalData()

        assertEquals(
            setOf("radio-helsinki", "radio-rock"),
            repository.favoriteIdsSnapshot()
        )
        assertTrue(store.isRoomMigrationComplete())
    }

    @Test
    fun repeatedSharedPreferencesMigrationIsSafe() = runTest {
        val database = inMemoryDatabase()
        val store = legacyStore()
        store.saveLegacyFavoriteIds(setOf("radio-helsinki"))
        val repository = repository(database, store)

        repository.prepareLocalData()
        repository.prepareLocalData()

        assertEquals(1, database.localRadioDao().activeFavoriteCount("radio-helsinki"))
    }

    @Test
    fun recentStationInsertionPersists() = runTest {
        val database = inMemoryDatabase()
        val repository = repository(database)

        repository.prepareLocalData()
        repository.recordRecentlyPlayed("radio-helsinki")

        assertEquals(listOf("radio-helsinki"), database.localRadioDao().recentStationIds(10))
    }

    @Test
    fun repeatedRecentStationUpdatesOrderingRatherThanDuplicating() = runTest {
        val database = inMemoryDatabase()
        val repository = repository(database)

        repository.prepareLocalData()
        repository.recordRecentlyPlayed("radio-helsinki")
        repository.recordRecentlyPlayed("radio-rock")
        repository.recordRecentlyPlayed("radio-helsinki")

        assertEquals(
            listOf("radio-helsinki", "radio-rock"),
            database.localRadioDao().recentStationIds(10)
        )
        assertEquals(2, database.localRadioDao().recentStationCount())
    }

    @Test
    fun recentListOrderingIsDeterministic() = runTest {
        val database = inMemoryDatabase()
        val repository = repository(database)
        val dao = database.localRadioDao()

        repository.prepareLocalData()
        dao.recordRecentStation("radio-rock", lastPlayedAt = 10L, limit = 10)
        dao.recordRecentStation("radio-helsinki", lastPlayedAt = 10L, limit = 10)

        assertEquals(
            listOf("radio-helsinki", "radio-rock"),
            dao.recentStationIds(10)
        )
    }

    @Test
    fun recentHistoryKeepsConfiguredSizeLimit() = runTest {
        val database = inMemoryDatabase()
        val repository = repository(database, recentLimit = 3)

        repository.prepareLocalData()
        repository.recordRecentlyPlayed("ylex")
        repository.recordRecentlyPlayed("radio-helsinki")
        repository.recordRecentlyPlayed("radio-rock")
        repository.recordRecentlyPlayed("radio-suomipop")

        assertEquals(
            listOf("radio-suomipop", "radio-rock", "radio-helsinki"),
            database.localRadioDao().recentStationIds(10)
        )
        assertEquals(3, database.localRadioDao().recentStationCount())
    }

    private fun inMemoryDatabase(): AaltoDatabase {
        return Room.inMemoryDatabaseBuilder(context, AaltoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openDatabases.add(it) }
    }

    private fun fileDatabase(databaseName: String): AaltoDatabase {
        return Room.databaseBuilder(context, AaltoDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
            .also { openDatabases.add(it) }
    }

    private fun repository(
        database: AaltoDatabase,
        store: LegacyFavoriteStationStore = legacyStore(),
        recentLimit: Int = StationRepository.DEFAULT_RECENT_LIMIT
    ): StationRepository {
        return StationRepository(
            dao = database.localRadioDao(),
            legacyFavoriteStore = store,
            deviceIdProvider = { "device-a" },
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = { now++ },
            mutationIdFactory = { "mutation-${now++}" },
            recentLimit = recentLimit
        )
    }

    private fun legacyStore(): LegacyFavoriteStationStore {
        return LegacyFavoriteStationStore(
            context = context,
            preferencesName = "aalto_test_${UUID.randomUUID()}"
        ).also { it.clearForTests() }
    }

    private fun uniqueDatabaseName(): String {
        return "aalto_test_${UUID.randomUUID()}.db"
            .also { databaseNamesToDelete.add(it) }
    }
}
