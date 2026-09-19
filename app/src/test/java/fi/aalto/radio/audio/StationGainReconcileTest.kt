package fi.aalto.radio.audio

import fi.aalto.radio.StationGainEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationGainReconcileTest {

    private fun row(stationId: String, gainDb: Int, device: String, version: Long = 1L) =
        StationGainEntity(
            stationId = stationId,
            gainDb = gainDb,
            logicalVersion = version,
            modifiedByDeviceId = device,
            updatedAt = version
        )

    @Test
    fun gainsSetBeforeSyncExistedAreQueuedOnce() {
        val actions = StationGainReconcile.atStart(
            rows = emptyList(),
            localGains = mapOf("radio-rock" to -3, "ylex" to 2),
            localDeviceId = "me"
        )

        assertEquals(mapOf("radio-rock" to -3, "ylex" to 2), actions.toSync)
        assertTrue(actions.toApplyLocally.isEmpty())
    }

    @Test
    fun agreeingStateNeedsNothing() {
        val actions = StationGainReconcile.atStart(
            rows = listOf(row("radio-rock", -3, "me"), row("ylex", 0, "other")),
            localGains = mapOf("radio-rock" to -3),
            localDeviceId = "me"
        )

        assertTrue(actions.toSync.isEmpty())
        assertTrue(actions.toApplyLocally.isEmpty())
    }

    @Test
    fun ownValueThatNeverReachedRoomIsQueued() {
        // The app closed after the slider moved but before Sync got the value.
        val actions = StationGainReconcile.atStart(
            rows = listOf(row("radio-rock", -3, "me")),
            localGains = emptyMap(),
            localDeviceId = "me"
        )

        assertEquals(mapOf("radio-rock" to 0), actions.toSync)
    }

    @Test
    fun valueFromAnotherDeviceIsTakenIntoUse() {
        val remote = row("radio-rock", 4, "other")
        val actions = StationGainReconcile.atStart(
            rows = listOf(remote),
            localGains = mapOf("radio-rock" to -1),
            localDeviceId = "me"
        )

        assertEquals(listOf(remote), actions.toApplyLocally)
        assertTrue(actions.toSync.isEmpty())
    }

    @Test
    fun onlyChangedRowsFromOtherDevicesAreApplied() {
        val unchanged = row("ylex", 2, "other")
        val previous = mapOf(
            "ylex" to unchanged,
            "radio-rock" to row("radio-rock", -2, "other", version = 1L)
        )
        val changedRemote = row("radio-rock", 5, "other", version = 2L)
        val changedHere = row("yle-radio-1", 3, "me", version = 3L)

        val changes = StationGainReconcile.remoteChanges(
            previous = previous,
            current = listOf(unchanged, changedRemote, changedHere),
            localDeviceId = "me"
        )

        assertEquals(listOf(changedRemote), changes)
    }
}
