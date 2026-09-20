package fi.aalto.radio.audio

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessMeterTest {

    private fun sine(amplitude: Double, channels: Int, onlyFirst: Boolean = false, seconds: Double = 5.0): LoudnessMeter {
        val rate = 48_000
        val meter = LoudnessMeter(rate, channels)
        val frames = (rate * seconds).toInt()
        for (i in 0 until frames) {
            val v = amplitude * sin(2 * PI * 997.0 * i / rate)
            for (c in 0 until channels) meter.add(if (onlyFirst && c > 0) 0.0 else v)
        }
        return meter
    }

    @Test
    fun fullScaleToneInOneChannelIsMinus3Lufs() {
        // BS.1770's own calibration: 997 Hz at 0 dBFS in one channel = −3.01 LKFS.
        assertEquals(-3.01, sine(1.0, channels = 2, onlyFirst = true).integratedLufs()!!, 0.1)
    }

    @Test
    fun halfAmplitudeIsSixDecibelsQuieter() {
        val full = sine(1.0, 2).integratedLufs()!!
        val half = sine(0.5, 2).integratedLufs()!!
        assertEquals(6.02, full - half, 0.1)
    }

    @Test
    fun silenceIsNotMeasured() {
        val meter = LoudnessMeter(48_000, 2)
        repeat(48_000 * 2 * 2) { meter.add(0.0) }
        assertNull(meter.integratedLufs())
        assertEquals(0.0, meter.gatedSeconds, 0.001)
    }

    @Test
    fun levellingTargetsTheReference() {
        assertEquals(-7.0, AutoLevel.gainFor(-9.0), 0.001)
        assertEquals(2.0, AutoLevel.gainFor(-18.0), 0.001)
        assertEquals(AutoLevel.MAX_BOOST_DB, AutoLevel.gainFor(-40.0), 0.001)
        assertEquals(AutoLevel.MAX_CUT_DB, AutoLevel.gainFor(0.0), 0.001)
    }

    @Test
    fun newListeningIsCombinedWithTheOld() {
        val old = MeasuredLoudness(lufs = -10.0, seconds = 600)
        val new = MeasuredLoudness(lufs = -10.0, seconds = 60)
        assertEquals(-10.0, AutoLevel.combine(old, new).lufs, 0.001)
        val louder = AutoLevel.combine(MeasuredLoudness(-20.0, 60), MeasuredLoudness(-10.0, 60))
        assertTrue(louder.lufs > -15.0 && louder.lufs < -10.0)
        assertEquals(new, AutoLevel.combine(null, new))
    }

    @Test
    fun aChosenReferenceStationSetsTheLevelWithinLimits() {
        // A reference at -12 LUFS: a -18 station comes up 6 dB.
        assertEquals(6.0, AutoLevel.gainFor(-18.0, target = -12.0), 0.001)
        // Too loud or too quiet a reference is held to the limits.
        assertEquals(AutoLevel.MAX_TARGET_LUFS, AutoLevel.clampTarget(-8.0)!!, 0.001)
        assertEquals(AutoLevel.MIN_TARGET_LUFS, AutoLevel.clampTarget(-30.0)!!, 0.001)
        assertNull(AutoLevel.clampTarget(null))
    }
}
