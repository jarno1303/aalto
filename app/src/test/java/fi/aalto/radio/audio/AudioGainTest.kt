package fi.aalto.radio.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGainTest {

    @Test
    fun `no adjustment leaves the volume untouched`() {
        assertEquals(1f, decibelsToVolume(0), 0.001f)
    }

    @Test
    fun `a boost is not done with the player volume`() {
        // Louder is the loudness enhancer's job; the player cannot exceed 1.
        assertEquals(1f, decibelsToVolume(6), 0.001f)
    }

    @Test
    fun `minus six decibels is about half the amplitude`() {
        assertEquals(0.501f, decibelsToVolume(-6), 0.01f)
    }

    @Test
    fun `the quietest setting still makes a sound`() {
        assertTrue(decibelsToVolume(AudioSettings.MIN_GAIN_DB) >= 0.1f)
    }
}
