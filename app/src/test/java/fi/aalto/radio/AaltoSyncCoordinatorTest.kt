package fi.aalto.radio

import android.app.Activity
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class AaltoSyncCoordinatorTest {
    private lateinit var context: Context
    private val openDatabases = mutableListOf<AaltoDatabase>()
    private val openCoordinators = mutableListOf<AaltoSyncCoordinator>()
    private var now = 50_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        now = 50_000L
    }

    @After
    fun tearDown() {
        openCoordinators.forEach { it.close() }
        openDatabases.forEach { it.close() }
    }

    @Test
    fun newerRemoteRevisionTriggersSyncWhileForeground() = runTest {
        val fixture = coordinatorFixture(initialLocalRevision = 5L)

        fixture.coordinator.onForeground()
        awaitCompletedSyncCalls(fixture, 1)
        fixture.resetSyncCalls()

        fixture.liveRevisionListener.emit(remoteRevision = 6L, modifiedByDeviceId = "device-a")
        awaitCompletedSyncCalls(fixture, 1)

        assertEquals(1, fixture.syncCalls)
        assertEquals(6L, fixture.localRevision())
    }

    @Test
    fun currentAndDuplicateRevisionDoNotTriggerSyncStorm() = runTest {
        val fixture = coordinatorFixture(initialLocalRevision = 6L)

        fixture.coordinator.onForeground()
        awaitCompletedSyncCalls(fixture, 1)
        fixture.resetSyncCalls()

        fixture.liveRevisionListener.emit(remoteRevision = 6L, modifiedByDeviceId = "device-a")
        fixture.liveRevisionListener.emit(remoteRevision = 6L, modifiedByDeviceId = "device-a")
        awaitNoAdditionalSync(fixture)

        assertEquals(0, fixture.syncCalls)

        fixture.liveRevisionListener.emit(remoteRevision = 7L, modifiedByDeviceId = "device-a")
        fixture.liveRevisionListener.emit(remoteRevision = 7L, modifiedByDeviceId = "device-a")
        awaitCompletedSyncCalls(fixture, 1)

        assertEquals(1, fixture.syncCalls)
        assertEquals(7L, fixture.localRevision())
    }

    @Test
    fun ownRevisionEventDoesNotTriggerLiveSyncLoop() = runTest {
        val fixture = coordinatorFixture(initialLocalRevision = 6L, deviceId = "device-b")

        fixture.coordinator.onForeground()
        awaitCompletedSyncCalls(fixture, 1)
        fixture.resetSyncCalls()

        fixture.liveRevisionListener.emit(remoteRevision = 7L, modifiedByDeviceId = "device-b")
        awaitNoAdditionalSync(fixture)

        assertEquals(0, fixture.syncCalls)
        assertEquals(6L, fixture.localRevision())
    }

    @Test
    fun listenerRemovedOnBackgroundAndSignOut() = runTest {
        val fixture = coordinatorFixture(initialLocalRevision = 5L)

        fixture.coordinator.onForeground()
        advanceUntilIdle()
        val foregroundRegistration = requireNotNull(fixture.liveRevisionListener.activeRegistration)

        fixture.coordinator.onBackground()

        assertTrue(foregroundRegistration.removed)
        assertNull(fixture.liveRevisionListener.activeRegistration)

        fixture.coordinator.onForeground()
        advanceUntilIdle()
        val restoredRegistration = requireNotNull(fixture.liveRevisionListener.activeRegistration)

        fixture.coordinator.signOut()
        advanceUntilIdle()

        assertTrue(restoredRegistration.removed)
        assertNull(fixture.liveRevisionListener.activeRegistration)
        assertTrue(fixture.auth.signOutCalled)
    }

    @Test
    fun listenerRestoredOnForegroundAndSignInWhenAppropriate() = runTest {
        val fixture = coordinatorFixture(
            initialAccount = null,
            initialLocalRevision = 5L
        )

        fixture.coordinator.onForeground()
        advanceUntilIdle()

        assertNull(fixture.liveRevisionListener.activeRegistration)

        fixture.auth.setAccount(SyncAccount(uid = "uid-1", email = "one@example.com"))
        advanceUntilIdle()

        assertEquals(1, fixture.liveRevisionListener.startCount)
        assertFalse(requireNotNull(fixture.liveRevisionListener.activeRegistration).removed)

        fixture.coordinator.onBackground()
        assertNull(fixture.liveRevisionListener.activeRegistration)

        fixture.coordinator.onForeground()
        advanceUntilIdle()

        assertEquals(2, fixture.liveRevisionListener.startCount)
        assertFalse(requireNotNull(fixture.liveRevisionListener.activeRegistration).removed)
    }

    private fun TestScope.coordinatorFixture(
        initialAccount: SyncAccount? = SyncAccount(uid = "uid-1", email = "one@example.com"),
        initialLocalRevision: Long,
        deviceId: String = "device-b"
    ): CoordinatorFixture {
        val database = inMemoryDatabase()
        val dao = database.localRadioDao()
        val auth = FakeSyncAuthSession(initialAccount)
        val liveRevisionListener = FakeLiveRevisionListenerFactory()
        val syncRecorder = SyncRecorder(dao, liveRevisionListener)
        val fixture = CoordinatorFixture(
            coordinator = AaltoSyncCoordinator(
                authSession = auth,
                dao = dao,
                deviceIdProvider = { deviceId },
                liveRevisionListenerFactory = liveRevisionListener,
                syncOnce = { _, _ -> syncRecorder.syncOnce() },
                scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
                clock = { now++ }
            ),
            dao = dao,
            auth = auth,
            liveRevisionListener = liveRevisionListener,
            syncRecorder = syncRecorder
        )
        openCoordinators.add(fixture.coordinator)
        runTestDatabaseSeed(dao, initialLocalRevision)
        return fixture
    }

    private fun inMemoryDatabase(): AaltoDatabase {
        return Room.inMemoryDatabaseBuilder(context, AaltoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { openDatabases.add(it) }
    }

    private fun runTestDatabaseSeed(dao: LocalRadioDao, localRevision: Long) {
        kotlinx.coroutines.runBlocking {
            dao.upsertSyncMetadata(
                SyncMetadataEntity(
                    key = SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION,
                    longValue = localRevision,
                    stringValue = null
                )
            )
        }
    }

    private fun awaitCompletedSyncCalls(fixture: CoordinatorFixture, expected: Int) {
        repeat(50) {
            if (fixture.completedSyncCalls == expected) return
            Thread.sleep(10)
        }
    }

    private fun awaitNoAdditionalSync(fixture: CoordinatorFixture) {
        Thread.sleep(50)
        assertEquals(0, fixture.syncCalls)
    }

    private data class CoordinatorFixture(
        val coordinator: AaltoSyncCoordinator,
        val dao: LocalRadioDao,
        val auth: FakeSyncAuthSession,
        val liveRevisionListener: FakeLiveRevisionListenerFactory,
        val syncRecorder: SyncRecorder
    ) {
        var syncCalls: Int
            get() = syncRecorder.syncCalls
            set(value) {
                syncRecorder.syncCalls = value
            }

        val completedSyncCalls: Int
            get() = syncRecorder.completedSyncCalls

        fun resetSyncCalls() {
            syncRecorder.syncCalls = 0
            syncRecorder.completedSyncCalls = 0
        }

        suspend fun localRevision(): Long {
            return dao.metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
        }
    }

    private class SyncRecorder(
        private val dao: LocalRadioDao,
        private val liveRevisionListener: FakeLiveRevisionListenerFactory
    ) {
        var syncCalls = 0
        var completedSyncCalls = 0

        suspend fun syncOnce(): SyncOnceResult {
            syncCalls += 1
            val localRevision = dao.metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
            val nextRevision = maxOf(localRevision, liveRevisionListener.latestRemoteRevision)
            dao.upsertSyncMetadata(
                SyncMetadataEntity(
                    key = SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION,
                    longValue = nextRevision,
                    stringValue = null
                )
            )
            completedSyncCalls += 1
            return SyncOnceResult(sent = 0, appliedIncoming = 0, retryableFailures = 0, permanentFailures = 0)
        }
    }

    private class FakeSyncAuthSession(
        initialAccount: SyncAccount?
    ) : SyncAuthSession {
        private val mutableAccounts = MutableStateFlow(initialAccount)
        override val accounts: Flow<SyncAccount?> = mutableAccounts
        var signOutCalled = false

        override suspend fun signIn(activity: Activity): Result<Unit> = Result.success(Unit)

        override fun signOut() {
            signOutCalled = true
            mutableAccounts.value = null
        }

        fun setAccount(account: SyncAccount?) {
            mutableAccounts.value = account
        }
    }

    private class FakeLiveRevisionListenerFactory : LiveRevisionListenerFactory {
        private val registrations = mutableListOf<FakeLiveRevisionListenerRegistration>()
        var latestRemoteRevision = 0L
        var startCount = 0

        val activeRegistration: FakeLiveRevisionListenerRegistration?
            get() = registrations.lastOrNull { !it.removed }

        override fun start(
            uid: String,
            onRevision: (LiveRevisionEvent) -> Unit,
            onError: (Throwable) -> Unit
        ): LiveRevisionListenerRegistration {
            startCount += 1
            return FakeLiveRevisionListenerRegistration(uid, onRevision).also { registration ->
                registrations += registration
            }
        }

        fun emit(remoteRevision: Long, modifiedByDeviceId: String?) {
            latestRemoteRevision = maxOf(latestRemoteRevision, remoteRevision)
            activeRegistration?.emit(remoteRevision, modifiedByDeviceId)
        }
    }

    private class FakeLiveRevisionListenerRegistration(
        val uid: String,
        private val onRevision: (LiveRevisionEvent) -> Unit
    ) : LiveRevisionListenerRegistration {
        var removed = false

        override fun remove() {
            removed = true
        }

        fun emit(remoteRevision: Long, modifiedByDeviceId: String?) {
            if (!removed) {
                onRevision(
                    LiveRevisionEvent(
                        remoteRevision = remoteRevision,
                        modifiedByDeviceId = modifiedByDeviceId
                    )
                )
            }
        }
    }
}
