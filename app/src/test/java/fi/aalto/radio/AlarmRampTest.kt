package fi.aalto.radio

import fi.aalto.radio.alarm.AlarmService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRampTest {

    @Test
    fun startsVeryQuietAndEndsAtFullVolume() {
        assertEquals(0.0158f, AlarmService.rampGain(0f), 0.001f)
        assertEquals(1f, AlarmService.rampGain(1f), 0.0001f)
    }

    @Test
    fun halfwayIsStillClearlyQuieter() {
        // -18 dB at the midpoint, about 13 % of full gain.
        assertEquals(0.126f, AlarmService.rampGain(0.5f), 0.002f)
    }

    @Test
    fun rampNeverDecreases() {
        var previous = 0f
        for (step in 0..100) {
            val gain = AlarmService.rampGain(step / 100f)
            assertTrue(gain >= previous)
            previous = gain
        }
    }
}
