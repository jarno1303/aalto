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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {
    private lateinit var context: Context
    private val openDatabases = mutableListOf<AaltoDatabase>()
    private val databaseNamesToDelete = mutableListOf<String>()
    private var now = 10_000L
    private var mutationCounter = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        now = 10_000L
        mutationCounter = 0
    }

    @After
    fun tearDown() {
        openDatabases.forEach { it.close() }
        databaseNamesToDelete.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun deviceIdGenerated() {
        val store = deviceStore()

        assertTrue(UUID.fromString(store.deviceId()).toString().isNotBlank())
    }

    @Test
    fun deviceIdPersists() {
        val preferencesName = "device_${UUID.randomUUID()}"
        val first = DeviceIdentityStore(context, preferencesName).deviceId()
        val second = DeviceIdentityStore(context, preferencesName).deviceId()

        assertEquals(first, second)
    }

    @Test
    fun twoInstallationsProduceDifferentDeviceIds() {
        val first = deviceStore().deviceId()
        val second = deviceStore().deviceId()

        assertNotEquals(first, second)
    }

    @Test
    fun favoriteAddCreatesOneOutboxMutation() = runTest {
        val app = testApp("device-a")

        app.repository.addFavorite("radio-rock")

        val mutation = app.dao.allSyncMutations().single()
        assertEquals(SyncOperation.UPSERT_FAVORITE.value, mutation.operation)
        assertEquals("radio-rock", mutation.entityId)
        assertEquals(SyncMutationState.PENDING.value, mutation.state)
    }

    @Test
    fun favoriteDeleteCreatesOneDeleteTombstoneMutation() = runTest {
        val app = testApp("device-a")

        app.repository.addFavorite("radio-rock")
        app.repository.removeFavorite("radio-rock")

        val mutations = app.dao.allSyncMutations()
        val favorite = app.dao.favoriteById("radio-rock")

        assertEquals(SyncOperation.DELETE_FAVORITE.value, mutations.last().operation)
        assertTrue(requireNotNull(favorite).isDeleted)
        assertFalse(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
    }

    @Test
    fun reorderCreatesMutationWithOrderedPayload() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.repository.addFavorite("ylex")

        app.repository.reorderFavorites(listOf("ylex", "radio-rock"))

        val mutation = app.dao.allSyncMutations().last()
        assertEquals(SyncOperation.REORDER_FAVORITES.value, mutation.operation)
        assertEquals(listOf("ylex", "radio-rock"), FavoriteOrderPayload.decode(mutation.payload))
    }

    @Test
    fun localDataAndMutationCommitAtomically() = runTest {
        val app = testApp("device-a")

        app.repository.addFavorite("radio-rock")

        assertEquals(1, app.dao.activeFavoriteCount("radio-rock"))
        assertEquals(1, app.dao.syncMutationCount())
    }

    @Test
    fun duplicateLogicalUserActionDoesNotCreateCorruptState() = runTest {
        val app = testApp("device-a")

        val first = app.repository.addFavorite("radio-rock")
        val second = app.repository.addFavorite("radio-rock")

        assertTrue(first)
        assertFalse(second)
        assertEquals(1, app.dao.activeFavoriteCount("radio-rock"))
        assertEquals(1, app.dao.syncMutationCount())
    }

    @Test
    fun sameIncomingMutationProcessedTwiceChangesLocalStateOnce() = runTest {
        val app = testApp("device-a")
        val mutation = remoteAdd("remote-1", "radio-rock", version = 4, deviceId = "device-b")
        app.transport.queueIncoming(mutation, count = 2)

        app.engine.syncOnce()

        assertEquals(setOf("radio-rock"), app.repository.favoriteIdsSnapshot())
        assertEquals(1, app.dao.appliedMutationCount("remote-1"))
    }

    @Test
    fun sameAddDeliveredTwiceCreatesOneFavorite() = runTest {
        val app = testApp("device-a")
        val mutation = remoteAdd("remote-1", "radio-rock", version = 4, deviceId = "device-b")
        app.transport.queueIncoming(mutation, count = 2)

        app.engine.syncOnce()

        assertEquals(1, app.dao.activeFavoriteCount("radio-rock"))
    }

    @Test
    fun sameDeleteDeliveredTwiceLeavesFavoriteDeleted() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        val mutation = remoteDelete("remote-delete", "radio-rock", version = 10, deviceId = "device-b")
        app.transport.queueIncoming(mutation, count = 2)

        app.engine.syncOnce()

        assertFalse(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
        assertTrue(requireNotNull(app.dao.favoriteById("radio-rock")).isDeleted)
    }

    @Test
    fun sameReorderDeliveredTwiceLeavesSameOrder() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.repository.addFavorite("ylex")
        val mutation = remoteReorder("remote-reorder", listOf("ylex", "radio-rock"), version = 7, deviceId = "device-b")
        app.transport.queueIncoming(mutation, count = 2)

        app.engine.syncOnce()

        assertEquals(listOf("ylex", "radio-rock"), app.dao.favoriteIds())
    }

    @Test
    fun olderAddLosesToNewerDelete() = runTest {
        val app = testApp("device-a")
        app.transport.queueIncoming(remoteDelete("delete-new", "radio-rock", version = 5, deviceId = "device-b"))
        app.engine.syncOnce()
        app.transport.queueIncoming(remoteAdd("add-old", "radio-rock", version = 4, deviceId = "device-c"))

        app.engine.syncOnce()

        assertFalse(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
    }

    @Test
    fun newerAddWinsOverOlderDelete() = runTest {
        val app = testApp("device-a")
        app.transport.queueIncoming(remoteDelete("delete-old", "radio-rock", version = 4, deviceId = "device-b"))
        app.engine.syncOnce()
        app.transport.queueIncoming(remoteAdd("add-new", "radio-rock", version = 5, deviceId = "device-c"))

        app.engine.syncOnce()

        assertTrue(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
    }

    @Test
    fun staleOfflineAddDoesNotResurrectNewerDeletion() = runTest {
        val app = testApp("device-a")
        app.transport.queueIncoming(remoteAdd("add-old", "radio-rock", version = 2, deviceId = "device-b"))
        app.engine.syncOnce()
        app.transport.queueIncoming(remoteDelete("delete-new", "radio-rock", version = 3, deviceId = "device-c"))
        app.engine.syncOnce()
        app.transport.queueIncoming(remoteAdd("add-stale", "radio-rock", version = 2, deviceId = "device-b"))

        app.engine.syncOnce()

        assertFalse(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
    }

    @Test
    fun simultaneousReorderConflictResolvesByDeviceIdTieBreak() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.repository.addFavorite("ylex")
        val lowerDevice = remoteReorder("reorder-low", listOf("radio-rock", "ylex"), version = 7, deviceId = "device-b")
        val higherDevice = remoteReorder("reorder-high", listOf("ylex", "radio-rock"), version = 7, deviceId = "device-c")

        app.transport.queueIncoming(lowerDevice)
        app.transport.queueIncoming(higherDevice)
        app.engine.syncOnce()

        assertEquals(listOf("ylex", "radio-rock"), app.dao.favoriteIds())
    }

    @Test
    fun deletionRemovesItemFromResolvedOrder() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.repository.addFavorite("ylex")
        app.transport.queueIncoming(remoteDelete("delete-rock", "radio-rock", version = 9, deviceId = "device-b"))
        app.transport.queueIncoming(remoteReorder("reorder-with-deleted", listOf("radio-rock", "ylex"), version = 10, deviceId = "device-b"))

        app.engine.syncOnce()

        assertEquals(listOf("ylex"), app.dao.favoriteIds())
    }

    @Test
    fun remoteAddAppearsLocally() = runTest {
        val app = testApp("device-a")
        app.transport.queueIncoming(remoteAdd("remote-add", "radio-rock", version = 4, deviceId = "device-b"))

        app.engine.syncOnce()

        assertTrue(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
    }

    @Test
    fun remoteDeleteDisappearsFromNormalFavoriteUi() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.transport.queueIncoming(remoteDelete("remote-delete", "radio-rock", version = 4, deviceId = "device-b"))

        app.engine.syncOnce()

        assertFalse(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
        assertEquals(1, app.dao.favoriteRowCount("radio-rock"))
    }

    @Test
    fun remoteReorderAppliesLocally() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.repository.addFavorite("ylex")
        app.transport.queueIncoming(remoteReorder("remote-reorder", listOf("ylex", "radio-rock"), version = 10, deviceId = "device-b"))

        app.engine.syncOnce()

        assertEquals(listOf("ylex", "radio-rock"), app.dao.favoriteIds())
    }

    @Test
    fun remoteApplyDoesNotCreateOutgoingEchoMutation() = runTest {
        val app = testApp("device-a")
        app.transport.queueIncoming(remoteAdd("remote-add", "radio-rock", version = 4, deviceId = "device-b"))

        app.engine.syncOnce()

        assertEquals(0, app.dao.syncMutationCount())
    }

    @Test
    fun temporaryFailureRemainsRetryable() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.transport.enqueueSendResult(SyncTransportSendResult.RetryableFailure("offline"))

        app.engine.syncOnce()

        val mutation = app.dao.allSyncMutations().single()
        assertEquals(SyncMutationState.FAILED_RETRYABLE.value, mutation.state)
    }

    @Test
    fun retryFailureIncreasesAttemptCount() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.transport.enqueueSendResult(SyncTransportSendResult.RetryableFailure("offline"))

        app.engine.syncOnce()

        assertEquals(1, app.dao.allSyncMutations().single().attemptCount)
    }

    @Test
    fun retryFailureAdvancesNextAttemptAt() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.transport.enqueueSendResult(SyncTransportSendResult.RetryableFailure("offline"))

        app.engine.syncOnce()

        assertTrue(requireNotNull(app.dao.allSyncMutations().single().nextAttemptAt) > now)
    }

    @Test
    fun successfulLaterRetryAcknowledgesMutation() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.transport.enqueueSendResult(SyncTransportSendResult.RetryableFailure("offline"))
        app.engine.syncOnce()
        now += 5_000L

        app.engine.syncOnce()

        assertEquals(SyncMutationState.ACKNOWLEDGED.value, app.dao.allSyncMutations().single().state)
    }

    @Test
    fun permanentFailureIsRepresented() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.transport.enqueueSendResult(SyncTransportSendResult.PermanentFailure("bad request"))

        app.engine.syncOnce()

        val mutation = app.dao.allSyncMutations().single()
        assertEquals(SyncMutationState.FAILED_PERMANENT.value, mutation.state)
        assertEquals("bad request", mutation.lastError)
    }

    @Test
    fun restartRecoversUnfinishedInFlightMutation() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        val mutationId = app.dao.allSyncMutations().single().mutationId
        app.dao.markSyncMutationState(mutationId, SyncMutationState.IN_FLIGHT.value, updatedAt = 0L)
        now = 60_000L
        val restartedEngine = SyncEngine(
            dao = app.dao,
            deviceId = "device-a",
            transport = app.transport,
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = { now++ }
        )

        restartedEngine.syncOnce()

        assertEquals(SyncMutationState.ACKNOWLEDGED.value, app.dao.allSyncMutations().single().state)
    }

    @Test
    fun manyPendingMutationsDoNotAffectVisibleStationPlaybackObject() = runTest {
        val app = testApp("device-a")
        repeat(1_000) { index ->
            app.dao.insertSyncMutation(
                SyncMutationEntity(
                    mutationId = "bulk-$index",
                    entityType = SyncEntityType.FAVORITE.value,
                    entityId = "radio-rock",
                    operation = SyncOperation.UPSERT_FAVORITE.value,
                    payload = null,
                    deviceId = "device-a",
                    logicalVersion = index.toLong(),
                    createdAt = index.toLong(),
                    updatedAt = index.toLong(),
                    attemptCount = 0,
                    nextAttemptAt = null,
                    state = SyncMutationState.PENDING.value,
                    lastError = null
                )
            )
        }

        val visibleStation = StationCatalog.stationById("radio-rock")

        assertNotNull(visibleStation)
        assertEquals("https://aud-stream-radiorock.nm-elemental.nelonenmedia.fi/playlist.m3u8", visibleStation?.preferredStreamUrl)
    }

    @Test
    fun remoteTransportExceptionDoesNotChangeLocalFavoriteMutation() = runTest {
        val app = testApp("device-a")
        app.repository.addFavorite("radio-rock")
        app.transport.throwOnSend = true

        app.engine.syncOnce()

        assertTrue(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
        assertEquals(SyncMutationState.FAILED_RETRYABLE.value, app.dao.allSyncMutations().single().state)
    }

    @Test
    fun scenarioADeviceBSeesDeviceAFavorites() = runTest {
        val remote = InMemoryRemoteSyncState()
        val deviceA = testApp("device-a", remote)
        val deviceB = testApp("device-b", remote)

        deviceA.repository.addFavorite("radio-rock")
        deviceA.repository.addFavorite("radio-suomipop")
        deviceA.repository.addFavorite("ylex")
        deviceA.engine.syncOnce()
        deviceB.engine.syncOnce()

        assertEquals(
            setOf("radio-rock", "radio-suomipop", "ylex"),
            deviceB.repository.favoriteIdsSnapshot()
        )
    }

    @Test
    fun scenarioBOfflineDeletePreventsNovaResurrection() = runTest {
        val remote = InMemoryRemoteSyncState()
        val deviceA = testApp("device-a", remote)
        val deviceB = testApp("device-b", remote)

        deviceA.repository.addFavorite("radio-rock")
        deviceA.repository.addFavorite("radio-suomipop")
        deviceA.repository.addFavorite("ylex")
        deviceA.engine.syncOnce()
        deviceB.engine.syncOnce()

        deviceA.repository.removeFavorite("radio-suomipop")
        deviceB.repository.addFavorite("radio-helsinki")
        deviceA.engine.syncOnce()
        deviceB.engine.syncOnce()
        deviceA.engine.syncOnce()

        val expected = setOf("radio-rock", "ylex", "radio-helsinki")
        assertEquals(expected, deviceA.repository.favoriteIdsSnapshot())
        assertEquals(expected, deviceB.repository.favoriteIdsSnapshot())
    }

    @Test
    fun scenarioCSeparatedReordersConvergeDeterministically() = runTest {
        val remote = InMemoryRemoteSyncState()
        val deviceA = testApp("device-a", remote)
        val deviceB = testApp("device-b", remote)
        seedThreeFavorites(deviceA, deviceB)

        deviceA.repository.reorderFavorites(listOf("ylex", "radio-rock", "radio-helsinki"))
        deviceB.repository.reorderFavorites(listOf("radio-helsinki", "ylex", "radio-rock"))
        deviceA.engine.syncOnce()
        deviceB.engine.syncOnce()
        deviceA.engine.syncOnce()

        val expected = listOf("ylex", "radio-rock", "radio-helsinki")
        assertEquals(expected, deviceA.dao.favoriteIds())
        assertEquals(expected, deviceB.dao.favoriteIds())
    }

    @Test
    fun scenarioDSameMutationDeliveredThreeTimesMatchesSingleDelivery() = runTest {
        val app = testApp("device-a")
        val mutation = remoteAdd("remote-add", "radio-rock", version = 4, deviceId = "device-b")

        app.transport.queueIncoming(mutation, count = 3)
        app.engine.syncOnce()

        assertEquals(setOf("radio-rock"), app.repository.favoriteIdsSnapshot())
        assertEquals(1, app.dao.activeFavoriteCount("radio-rock"))
    }

    @Test
    fun scenarioERepeatedFailureRestartThenSuccessAppliesMutations() = runTest {
        val remote = InMemoryRemoteSyncState()
        val app = testApp("device-a", remote)
        app.repository.addFavorite("radio-rock")
        app.transport.enqueueSendResult(SyncTransportSendResult.RetryableFailure("offline"))
        app.transport.enqueueSendResult(SyncTransportSendResult.RetryableFailure("offline"))

        app.engine.syncOnce()
        now += 2_000L
        val restartedEngine = SyncEngine(
            dao = app.dao,
            deviceId = "device-a",
            transport = app.transport,
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = { now++ }
        )
        restartedEngine.syncOnce()
        now += 4_000L
        restartedEngine.syncOnce()

        assertEquals(SyncMutationState.ACKNOWLEDGED.value, app.dao.allSyncMutations().single().state)
        assertEquals(listOf("radio-rock"), app.transport.activeRemoteFavoriteIds())
    }

    @Test
    fun offlineSequentialReordersAfterRestartPushLatestLocalIntent() = runTest {
        val remote = InMemoryRemoteSyncState()
        val initialOrder = listOf(A, B, C, D)
        val firstIntent = listOf(B, A, C, D)
        val secondIntent = listOf(B, C, A, D)
        val latestIntent = listOf(D, B, C, A)
        remote.seedFavoriteOrder(initialOrder, revision = 10L)
        val databaseName = uniqueDatabaseName()
        val store = legacyStore().also { it.markRoomMigrationComplete() }
        var app = fileTestApp("device-b", remote, databaseName, store)
        seedLocalFavoritesAtRevision(app, initialOrder, revision = 10L)

        app.repository.reorderFavorites(firstIntent)
        app.repository.reorderFavorites(secondIntent)
        app.repository.reorderFavorites(latestIntent)

        val offlineReorders = app.dao.allSyncMutations()
            .filter { it.operation == SyncOperation.REORDER_FAVORITES.value }
        assertEquals(listOf(10L, 10L, 10L), offlineReorders.map { it.baseServerRevision })
        assertEquals(latestIntent, app.dao.favoriteIds())

        app.database.close()
        openDatabases.remove(app.database)
        app = fileTestApp("device-b", remote, databaseName, store)

        assertEquals(latestIntent, app.dao.favoriteIds())

        syncUntilNoDueMutations(app)

        assertEquals(latestIntent, app.transport.activeRemoteFavoriteIds())
        assertEquals(latestIntent, app.dao.favoriteIds())
        assertFalse(
            app.dao.allSyncMutations().any {
                it.operation == SyncOperation.REORDER_FAVORITES.value &&
                    it.state == SyncMutationState.FAILED_PERMANENT.value
            }
        )
        assertEquals(
            app.transport.appliedRemoteMutationIds().distinct(),
            app.transport.appliedRemoteMutationIds()
        )
        assertEquals(
            app.transport.remoteLedgerServerRevisions().sorted(),
            app.transport.remoteLedgerServerRevisions()
        )
    }

    @Test
    fun previouslyRejectedLatestSameDeviceReorderIsRecoveredAndConverges() = runTest {
        val remote = InMemoryRemoteSyncState()
        val initialOrder = listOf(A, B, C, D)
        val firstIntent = listOf(B, A, C, D)
        val latestIntent = listOf(D, B, C, A)
        remote.seedFavoriteOrder(initialOrder, revision = 10L)
        val app = testApp("device-b", remote)
        seedLocalFavoritesAtRevision(app, initialOrder, revision = 10L)

        app.repository.reorderFavorites(firstIntent)
        app.repository.reorderFavorites(latestIntent)
        remote.apply(
            remoteReorder(
                mutationId = "already-applied-same-device-order",
                orderedStationIds = firstIntent,
                version = 11L,
                deviceId = "device-b",
                baseServerRevision = 10L
            )
        )
        val latestMutation = app.dao.allSyncMutations()
            .filter { it.operation == SyncOperation.REORDER_FAVORITES.value }
            .maxBy { it.logicalVersion }
        app.dao.markPermanentFailure(
            mutationId = latestMutation.mutationId,
            state = SyncMutationState.FAILED_PERMANENT.value,
            updatedAt = now++,
            lastError = InMemoryRemoteSyncState.RESULT_REJECTED_STALE
        )

        app.engine.syncOnce()

        assertEquals(latestIntent, app.transport.activeRemoteFavoriteIds())
        assertEquals(latestIntent, app.dao.favoriteIds())
        assertEquals(
            SyncMutationState.ACKNOWLEDGED.value,
            requireNotNull(app.dao.syncMutationById(latestMutation.mutationId)).state
        )
    }

    @Test
    fun crossDeviceStaleReorderDoesNotOverwriteNewerCanonicalOrder() = runTest {
        val remote = InMemoryRemoteSyncState()
        val initialOrder = listOf(A, B, C, D)
        val deviceAIntent = listOf(D, C, B, A)
        val deviceBStaleIntent = listOf(B, A, C, D)
        remote.seedFavoriteOrder(initialOrder, revision = 10L)
        val app = testApp("device-b", remote)
        seedLocalFavoritesAtRevision(app, initialOrder, revision = 10L)

        val interveningResult = remote.apply(
            remoteAdd(
                mutationId = "device-a-intervening-favorite",
                stationId = A,
                version = 11L,
                deviceId = "device-a",
                baseServerRevision = 10L
            )
        )
        assertTrue(interveningResult is SyncTransportSendResult.Success)
        val deviceAResult = remote.apply(
            remoteReorder(
                mutationId = "device-a-canonical-reorder",
                orderedStationIds = deviceAIntent,
                version = 12L,
                deviceId = "device-a",
                baseServerRevision = 11L
            )
        )
        assertTrue(deviceAResult is SyncTransportSendResult.Success)
        assertEquals(12L, app.transport.currentRemoteRevision())

        app.repository.reorderFavorites(deviceBStaleIntent)
        val deviceBMutation = app.dao.allSyncMutations()
            .single { it.operation == SyncOperation.REORDER_FAVORITES.value }

        app.engine.syncOnce()

        assertEquals(deviceAIntent, app.transport.activeRemoteFavoriteIds())
        assertEquals(deviceAIntent, app.dao.favoriteIds())
        assertEquals(InMemoryRemoteSyncState.RESULT_REJECTED_STALE, app.transport.remoteResultFor(deviceBMutation.mutationId))
        assertEquals(
            SyncMutationState.FAILED_PERMANENT.value,
            requireNotNull(app.dao.syncMutationById(deviceBMutation.mutationId)).state
        )
        assertEquals(
            app.transport.remoteLedgerServerRevisions().sorted(),
            app.transport.remoteLedgerServerRevisions()
        )
    }

    @Test
    fun sameDeviceOfflineAddThenDeleteConvergesToLatestIntent() = runTest {
        val remote = InMemoryRemoteSyncState()
        remote.seedFavoriteOrder(emptyList(), revision = 10L)
        val app = testApp("device-b", remote)
        app.dao.upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION,
                longValue = 10L,
                stringValue = null
            )
        )
        app.dao.upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.LOCAL_LOGICAL_VERSION,
                longValue = 10L,
                stringValue = null
            )
        )

        app.repository.addFavorite("radio-rock")
        app.repository.removeFavorite("radio-rock")

        val favoriteMutations = app.dao.allSyncMutations()
            .filter { it.entityId == "radio-rock" }
        assertEquals(listOf(10L, 10L), favoriteMutations.map { it.baseServerRevision })

        app.engine.syncOnce()

        assertFalse(app.transport.activeRemoteFavoriteIds().contains("radio-rock"))
        assertFalse(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
        assertFalse(
            app.dao.allSyncMutations().any {
                it.entityId == "radio-rock" &&
                    it.state == SyncMutationState.FAILED_PERMANENT.value
            }
        )
    }

    @Test
    fun pulledCanonicalOtherDeviceUpsertAppliesDespiteLowerClientLogicalVersion() = runTest {
        val app = testApp("device-b")
        seedLocalFavoriteState(
            app = app,
            stationId = "radio-rock",
            isDeleted = true,
            logicalVersion = 100L,
            serverRevision = 34L,
            deviceId = "device-b"
        )
        seedLocalRevision(app, revision = 34L)
        app.transport.queueIncoming(
            remoteAdd(
                mutationId = "device-a-upsert-35",
                stationId = "radio-rock",
                version = 1L,
                deviceId = "device-a",
                baseServerRevision = 34L,
                serverRevision = 35L
            )
        )

        val result = app.engine.syncOnce()

        assertEquals(1, result.appliedIncoming)
        assertTrue(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
        assertEquals(1, app.dao.appliedMutationCount("device-a-upsert-35"))
        assertEquals(0, app.dao.syncMutationCount())
    }

    @Test
    fun pulledCanonicalOtherDeviceDeleteAppliesDespiteLowerClientLogicalVersion() = runTest {
        val app = testApp("device-b")
        seedLocalFavoriteState(
            app = app,
            stationId = "radio-rock",
            isDeleted = false,
            logicalVersion = 100L,
            serverRevision = 34L,
            deviceId = "device-b"
        )
        seedLocalRevision(app, revision = 34L)
        app.transport.queueIncoming(
            remoteDelete(
                mutationId = "device-a-delete-35",
                stationId = "radio-rock",
                version = 1L,
                deviceId = "device-a",
                baseServerRevision = 34L,
                serverRevision = 35L
            )
        )

        val result = app.engine.syncOnce()

        assertEquals(1, result.appliedIncoming)
        assertFalse(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
        assertTrue(requireNotNull(app.dao.favoriteById("radio-rock")).isDeleted)
        assertEquals(1, app.dao.appliedMutationCount("device-a-delete-35"))
        assertEquals(0, app.dao.syncMutationCount())
    }

    @Test
    fun pulledCanonicalOtherDeviceReorderAppliesDespiteLowerClientLogicalVersion() = runTest {
        val app = testApp("device-b")
        val initialOrder = listOf(A, B, C)
        val remoteOrder = listOf(C, B, A)
        seedLocalFavoritesAtRevision(
            app = app,
            orderedStationIds = initialOrder,
            revision = 34L,
            deviceId = "device-b"
        )
        app.dao.upsertFavoriteOrderState(
            FavoriteOrderStateEntity(
                id = FavoriteOrderStateEntity.DEFAULT_ID,
                orderedStationIds = FavoriteOrderPayload.encode(initialOrder),
                logicalVersion = 100L,
                modifiedByDeviceId = "device-b",
                updatedAt = now++
            )
        )
        app.transport.queueIncoming(
            remoteReorder(
                mutationId = "device-a-reorder-35",
                orderedStationIds = remoteOrder,
                version = 1L,
                deviceId = "device-a",
                baseServerRevision = 34L,
                serverRevision = 35L
            )
        )

        val result = app.engine.syncOnce()

        assertEquals(1, result.appliedIncoming)
        assertEquals(remoteOrder, app.dao.favoriteIds())
        assertEquals(1, app.dao.appliedMutationCount("device-a-reorder-35"))
        assertEquals(0, app.dao.syncMutationCount())
    }

    @Test
    fun sameDeviceIdWithoutLocalOutboxMutationIsNotSkippedAsEcho() = runTest {
        val app = testApp("device-b")
        seedLocalRevision(app, revision = 34L)
        app.transport.queueIncoming(
            remoteAdd(
                mutationId = "unknown-same-device-upsert-35",
                stationId = "radio-rock",
                version = 1L,
                deviceId = "device-b",
                baseServerRevision = 34L,
                serverRevision = 35L
            )
        )

        val result = app.engine.syncOnce()

        assertEquals(1, result.appliedIncoming)
        assertTrue(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
        assertEquals(1, app.dao.appliedMutationCount("unknown-same-device-upsert-35"))
        assertEquals(0, app.dao.syncMutationCount())
    }

    @Test
    fun favoriteAddRemoveAfterProcessRecreationSyncKeepsRemoteDeleted() = runTest {
        val remote = InMemoryRemoteSyncState()
        remote.seedFavoriteOrder(emptyList(), revision = 10L)
        val databaseName = uniqueDatabaseName()
        val store = legacyStore().also { it.markRoomMigrationComplete() }
        var app = fileTestApp("device-b", remote, databaseName, store)
        app.dao.upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION,
                longValue = 10L,
                stringValue = null
            )
        )
        app.dao.upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.LOCAL_LOGICAL_VERSION,
                longValue = 10L,
                stringValue = null
            )
        )

        app.repository.addFavorite("radio-rock")
        app.repository.removeFavorite("radio-rock")

        val deleteMutations = app.dao.allSyncMutations()
            .filter { it.operation == SyncOperation.DELETE_FAVORITE.value }
        assertEquals(1, deleteMutations.size)
        assertEquals(SyncMutationState.PENDING.value, deleteMutations.single().state)
        assertTrue(requireNotNull(app.dao.favoriteById("radio-rock")).isDeleted)
        assertFalse(app.repository.favoriteIdsSnapshot().contains("radio-rock"))

        val deleteMutationId = deleteMutations.single().mutationId
        app.database.close()
        openDatabases.remove(app.database)
        app = fileTestApp("device-b", remote, databaseName, store)

        assertTrue(requireNotNull(app.dao.favoriteById("radio-rock")).isDeleted)
        assertFalse(app.repository.favoriteIdsSnapshot().contains("radio-rock"))

        syncUntilNoDueMutations(app)

        assertFalse(app.transport.activeRemoteFavoriteIds().contains("radio-rock"))
        assertFalse(app.repository.favoriteIdsSnapshot().contains("radio-rock"))
        assertEquals(InMemoryRemoteSyncState.RESULT_APPLIED, app.transport.remoteResultFor(deleteMutationId))
        assertFalse(
            app.dao.allSyncMutations().any {
                it.entityId == "radio-rock" &&
                    it.state == SyncMutationState.FAILED_PERMANENT.value
            }
        )
    }

    @Test
    fun stationGainChangeCreatesOneOutboxMutation() = runTest {
        val app = testApp("device-a")

        assertTrue(app.repository.setStationGain("radio-rock", -4))

        val mutation = app.dao.allSyncMutations().single()
        assertEquals(SyncEntityType.STATION_GAIN.value, mutation.entityType)
        assertEquals(SyncOperation.SET_STATION_GAIN.value, mutation.operation)
        assertEquals("radio-rock", mutation.entityId)
        assertEquals("-4", mutation.payload)
        assertEquals(-4, requireNotNull(app.dao.stationGain("radio-rock")).gainDb)
    }

    @Test
    fun unchangedOrNeverAdjustedStationGainCreatesNoMutation() = runTest {
        val app = testApp("device-a")

        assertFalse(app.repository.setStationGain("radio-rock", 0))
        app.repository.setStationGain("ylex", 3)
        assertFalse(app.repository.setStationGain("ylex", 3))

        assertEquals(1, app.dao.allSyncMutations().size)
    }

    @Test
    fun stationGainIsClampedToSliderRange() = runTest {
        val app = testApp("device-a")

        app.repository.setStationGain("radio-rock", 40)

        assertEquals(8, requireNotNull(app.dao.stationGain("radio-rock")).gainDb)
        assertEquals("8", app.dao.allSyncMutations().single().payload)
    }

    @Test
    fun onlyLatestUnsentStationGainIsSent() = runTest {
        val app = testApp("device-a")

        app.repository.setStationGain("radio-rock", -2)
        app.repository.setStationGain("radio-rock", -5)
        app.repository.setStationGain("ylex", 2)
        app.engine.syncOnce()

        assertEquals(mapOf("radio-rock" to -5, "ylex" to 2), app.transport.remoteStationGains())
        assertEquals(2, app.transport.appliedRemoteMutationIds().size)
    }

    @Test
    fun stationGainSetOnOneDeviceAppearsOnTheOther() = runTest {
        val remote = InMemoryRemoteSyncState()
        val deviceA = testApp("device-a", remote)
        val deviceB = testApp("device-b", remote)

        deviceA.repository.setStationGain("radio-rock", -4)
        deviceA.repository.setStationGain("yle-radio-1", 6)
        deviceA.engine.syncOnce()
        deviceB.engine.syncOnce()

        assertEquals(-4, requireNotNull(deviceB.dao.stationGain("radio-rock")).gainDb)
        assertEquals(6, requireNotNull(deviceB.dao.stationGain("yle-radio-1")).gainDb)
        assertEquals("device-a", requireNotNull(deviceB.dao.stationGain("radio-rock")).modifiedByDeviceId)
        assertTrue(
            "applying a remote gain must not echo back",
            deviceB.dao.allSyncMutations().isEmpty()
        )
    }

    @Test
    fun stationGainResetOnOneDeviceResetsTheOther() = runTest {
        val remote = InMemoryRemoteSyncState()
        val deviceA = testApp("device-a", remote)
        val deviceB = testApp("device-b", remote)
        deviceA.repository.setStationGain("radio-rock", -4)
        deviceA.engine.syncOnce()
        deviceB.engine.syncOnce()

        deviceB.repository.setStationGain("radio-rock", 0)
        deviceB.engine.syncOnce()
        deviceA.engine.syncOnce()

        assertEquals(0, requireNotNull(deviceA.dao.stationGain("radio-rock")).gainDb)
        assertEquals(mapOf("radio-rock" to 0), remote.stationGains())
    }

    @Test
    fun staleStationGainFromOtherDeviceConvergesToNewerValue() = runTest {
        val remote = InMemoryRemoteSyncState()
        val deviceA = testApp("device-a", remote)
        val deviceB = testApp("device-b", remote)

        deviceB.repository.setStationGain("radio-rock", -2)
        deviceB.engine.syncOnce()
        // Device A has not heard of B's value and changes the same station.
        deviceA.repository.setStationGain("radio-rock", 5)
        deviceA.engine.syncOnce()
        deviceB.engine.syncOnce()

        val onA = requireNotNull(deviceA.dao.stationGain("radio-rock")).gainDb
        val onB = requireNotNull(deviceB.dao.stationGain("radio-rock")).gainDb
        val onServer = requireNotNull(remote.stationGains()["radio-rock"])
        assertEquals(onServer, onA)
        assertEquals(onServer, onB)
    }

    @Test
    fun stationGainSyncLeavesFavoritesUntouched() = runTest {
        val remote = InMemoryRemoteSyncState()
        val deviceA = testApp("device-a", remote)
        val deviceB = testApp("device-b", remote)
        seedThreeFavorites(deviceA, deviceB)
        val orderBefore = deviceB.dao.favoriteIds()

        deviceA.repository.setStationGain("radio-rock", 3)
        deviceA.engine.syncOnce()
        deviceB.engine.syncOnce()

        assertEquals(orderBefore, deviceB.dao.favoriteIds())
        assertEquals(3, requireNotNull(deviceB.dao.stationGain("radio-rock")).gainDb)
    }

    @Test
    fun newDeviceCatchesUpOnLongHistoryInOneSync() = runTest {
        val remote = InMemoryRemoteSyncState()
        val deviceA = testApp("device-a", remote)
        // 30 station gains, each changed 10 times: 300 changes, four full pages.
        repeat(10) { round ->
            repeat(30) { station ->
                deviceA.repository.setStationGain("station-$station", (round % 8) + 1)
            }
            deviceA.engine.syncOnce()
        }
        assertTrue(remote.currentServerRevision() > 3 * PAGE_SIZE)

        val deviceB = testApp("device-b", remote)
        val pagedTransport = FakeRemoteSyncTransport(
            state = remote,
            pageSize = PAGE_SIZE,
            cursor = {
                deviceB.dao.metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
            }
        )
        val engineB = SyncEngine(
            dao = deviceB.dao,
            deviceId = "device-b",
            transport = pagedTransport,
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = { now++ }
        )

        engineB.syncOnce()

        assertEquals(remote.currentServerRevision(), deviceB.dao.metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION))
        assertEquals(remote.stationGains(), deviceB.dao.allStationGains().associate { it.stationId to it.gainDb })
        assertTrue("pulled more than one page", pagedTransport.receiveCalls > 1)
    }

    @Test
    fun caughtUpDeviceMakesOnlyOnePullPerSync() = runTest {
        val remote = InMemoryRemoteSyncState()
        val deviceA = testApp("device-a", remote)
        deviceA.repository.setStationGain("radio-rock", 2)
        deviceA.engine.syncOnce()
        val deviceB = testApp("device-b", remote)
        val pagedTransport = FakeRemoteSyncTransport(
            state = remote,
            pageSize = PAGE_SIZE,
            cursor = {
                deviceB.dao.metadataLongValue(SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION) ?: 0L
            }
        )
        val engineB = SyncEngine(
            dao = deviceB.dao,
            deviceId = "device-b",
            transport = pagedTransport,
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = { now++ }
        )

        engineB.syncOnce()

        assertEquals(1, pagedTransport.receiveCalls)
        assertEquals(2, requireNotNull(deviceB.dao.stationGain("radio-rock")).gainDb)
    }

    private suspend fun seedThreeFavorites(deviceA: TestApp, deviceB: TestApp) {
        deviceA.repository.addFavorite("radio-rock")
        deviceA.repository.addFavorite("ylex")
        deviceA.repository.addFavorite("radio-helsinki")
        deviceA.engine.syncOnce()
        deviceB.engine.syncOnce()
    }

    private suspend fun testApp(
        deviceId: String,
        remote: InMemoryRemoteSyncState = InMemoryRemoteSyncState()
    ): TestApp {
        return testApp(
            deviceId = deviceId,
            remote = remote,
            database = inMemoryDatabase(),
            legacyStore = legacyStore().also { it.markRoomMigrationComplete() }
        )
    }

    private suspend fun fileTestApp(
        deviceId: String,
        remote: InMemoryRemoteSyncState,
        databaseName: String,
        legacyStore: LegacyFavoriteStationStore
    ): TestApp {
        return testApp(
            deviceId = deviceId,
            remote = remote,
            database = fileDatabase(databaseName),
            legacyStore = legacyStore
        )
    }

    private suspend fun testApp(
        deviceId: String,
        remote: InMemoryRemoteSyncState,
        database: AaltoDatabase,
        legacyStore: LegacyFavoriteStationStore
    ): TestApp {
        val transport = FakeRemoteSyncTransport(remote)
        val repository = StationRepository(
            dao = database.localRadioDao(),
            legacyFavoriteStore = legacyStore,
            deviceIdProvider = { deviceId },
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = { now++ },
            mutationIdFactory = { "$deviceId-mutation-${mutationCounter++}" }
        )
        val engine = SyncEngine(
            dao = database.localRadioDao(),
            deviceId = deviceId,
            transport = transport,
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = { now++ }
        )

        return TestApp(
            database = database,
            dao = database.localRadioDao(),
            repository = repository,
            engine = engine,
            transport = transport
        ).also {
            repository.prepareLocalData()
        }
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

    private fun legacyStore(): LegacyFavoriteStationStore {
        return LegacyFavoriteStationStore(
            context = context,
            preferencesName = "sync_test_${UUID.randomUUID()}"
        ).also { it.clearForTests() }
    }

    private fun deviceStore(): DeviceIdentityStore {
        return DeviceIdentityStore(
            context = context,
            preferencesName = "device_test_${UUID.randomUUID()}"
        ).also { it.clearForTests() }
    }

    private suspend fun seedLocalFavoriteState(
        app: TestApp,
        stationId: String,
        isDeleted: Boolean,
        logicalVersion: Long,
        serverRevision: Long,
        deviceId: String
    ) {
        val station = requireNotNull(StationCatalog.stationById(stationId))
        app.dao.upsertStation(station.toEntity(updatedAt = now++))
        app.dao.upsertFavorite(
            FavoriteEntity(
                stationId = stationId,
                createdAt = now++,
                sortOrder = 0L,
                isDeleted = isDeleted,
                deletedAt = if (isDeleted) now++ else null,
                updatedAt = now++,
                logicalVersion = logicalVersion,
                modifiedByDeviceId = deviceId,
                serverRevision = serverRevision,
                baseServerRevision = serverRevision
            )
        )
    }

    private suspend fun seedLocalFavoritesAtRevision(
        app: TestApp,
        orderedStationIds: List<String>,
        revision: Long,
        deviceId: String = "seed"
    ) {
        orderedStationIds.forEachIndexed { index, stationId ->
            val station = requireNotNull(StationCatalog.stationById(stationId))
            app.dao.upsertStation(station.toEntity(updatedAt = now++))
            app.dao.upsertFavorite(
                FavoriteEntity(
                    stationId = stationId,
                    createdAt = now++,
                    sortOrder = index.toLong(),
                    isDeleted = false,
                    deletedAt = null,
                    updatedAt = now++,
                    logicalVersion = revision,
                    modifiedByDeviceId = deviceId,
                    serverRevision = revision,
                    baseServerRevision = revision
                )
            )
        }
        app.dao.upsertFavoriteOrderState(
            FavoriteOrderStateEntity(
                id = FavoriteOrderStateEntity.DEFAULT_ID,
                orderedStationIds = FavoriteOrderPayload.encode(orderedStationIds),
                logicalVersion = revision,
                modifiedByDeviceId = deviceId,
                updatedAt = now++
            )
        )
        app.dao.upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION,
                longValue = revision,
                stringValue = null
            )
        )
        app.dao.upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.LOCAL_LOGICAL_VERSION,
                longValue = revision,
                stringValue = null
            )
        )
    }

    private suspend fun seedLocalRevision(app: TestApp, revision: Long) {
        app.dao.upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.LAST_APPLIED_SERVER_REVISION,
                longValue = revision,
                stringValue = null
            )
        )
        app.dao.upsertSyncMetadata(
            SyncMetadataEntity(
                key = SyncMetadataEntity.LOCAL_LOGICAL_VERSION,
                longValue = revision,
                stringValue = null
            )
        )
    }

    private suspend fun syncUntilNoDueMutations(app: TestApp, maxPasses: Int = 5) {
        repeat(maxPasses) {
            app.engine.syncOnce()
            val due = app.dao.dueSyncMutations(
                states = listOf(
                    SyncMutationState.PENDING.value,
                    SyncMutationState.FAILED_RETRYABLE.value
                ),
                now = Long.MAX_VALUE,
                limit = 1
            )
            if (due.isEmpty()) {
                return
            }
            now += 60_000L
        }
    }

    private fun remoteAdd(
        mutationId: String,
        stationId: String,
        version: Long,
        deviceId: String,
        baseServerRevision: Long = 0L,
        serverRevision: Long = 0L
    ): SyncMutation {
        return remoteMutation(
            mutationId = mutationId,
            stationId = stationId,
            operation = SyncOperation.UPSERT_FAVORITE,
            version = version,
            deviceId = deviceId,
            baseServerRevision = baseServerRevision,
            serverRevision = serverRevision
        )
    }

    private fun remoteDelete(
        mutationId: String,
        stationId: String,
        version: Long,
        deviceId: String,
        baseServerRevision: Long = 0L,
        serverRevision: Long = 0L
    ): SyncMutation {
        return remoteMutation(
            mutationId = mutationId,
            stationId = stationId,
            operation = SyncOperation.DELETE_FAVORITE,
            version = version,
            deviceId = deviceId,
            baseServerRevision = baseServerRevision,
            serverRevision = serverRevision
        )
    }

    private fun remoteReorder(
        mutationId: String,
        orderedStationIds: List<String>,
        version: Long,
        deviceId: String,
        baseServerRevision: Long = 0L,
        serverRevision: Long = 0L
    ): SyncMutation {
        return SyncMutation(
            mutationId = mutationId,
            entityType = SyncEntityType.FAVORITE_ORDER.value,
            entityId = FavoriteOrderStateEntity.DEFAULT_ID,
            operation = SyncOperation.REORDER_FAVORITES.value,
            payload = FavoriteOrderPayload.encode(orderedStationIds),
            deviceId = deviceId,
            logicalVersion = version,
            createdAt = version,
            updatedAt = version,
            baseServerRevision = baseServerRevision,
            serverRevision = serverRevision
        )
    }

    private fun remoteMutation(
        mutationId: String,
        stationId: String,
        operation: SyncOperation,
        version: Long,
        deviceId: String,
        baseServerRevision: Long = 0L,
        serverRevision: Long = 0L
    ): SyncMutation {
        return SyncMutation(
            mutationId = mutationId,
            entityType = SyncEntityType.FAVORITE.value,
            entityId = stationId,
            operation = operation.value,
            payload = null,
            deviceId = deviceId,
            logicalVersion = version,
            createdAt = version,
            updatedAt = version,
            baseServerRevision = baseServerRevision,
            serverRevision = serverRevision
        )
    }

    private data class TestApp(
        val database: AaltoDatabase,
        val dao: LocalRadioDao,
        val repository: StationRepository,
        val engine: SyncEngine,
        val transport: FakeRemoteSyncTransport
    )

    private fun uniqueDatabaseName(): String {
        return "aalto_sync_test_${UUID.randomUUID()}.db"
            .also { databaseNamesToDelete.add(it) }
    }

    private companion object {
        private const val A = "yle-klassinen"
        private const val B = "ylex"
        private const val C = "yle-radio-suomi"
        private const val D = "yle-radio-1"
        private const val PAGE_SIZE = 75
    }
}
