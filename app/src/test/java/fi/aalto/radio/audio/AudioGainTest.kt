package fi.aalto.radio.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGainTest {

    @Test
    fun `no adjustment leaves the volume untouched`() {
        assertEquals(1.0, decibelsToVolume(0).toDouble(), 0.001)
    }

    @Test
    fun `a boost is not done with the player volume`() {
        // Louder is the loudness enhancer's job; the player cannot exceed 1.
        assertEquals(1.0, decibelsToVolume(6).toDouble(), 0.001)
    }

    @Test
    fun `minus six decibels is about half the amplitude`() {
        assertEquals(0.501, decibelsToVolume(-6).toDouble(), 0.01)
    }

    @Test
    fun `the quietest setting still makes a sound`() {
        assertTrue(decibelsToVolume(AudioSettings.MIN_GAIN_DB) >= 0.1f)
    }
}
