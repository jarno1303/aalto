package fi.aalto.radio.playback

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamQualityTest {

    @Test
    fun measuredStreamWins() {
        val measured = StreamFormat(MimeTypes.AUDIO_MPEG, null, bitrateKbps = 192, sampleRateHz = 44_100)
        assertEquals("MP3 · 192 kbps", StreamQuality.label(measured, declaredCodec = "AAC", declaredBitrateKbps = 64))
    }

    @Test
    fun heAacIsShownAsAacPlus() {
        val measured = StreamFormat(MimeTypes.AUDIO_AAC, "mp4a.40.5", bitrateKbps = null, sampleRateHz = 48_000)
        assertEquals("AAC+ · 64 kbps", StreamQuality.label(measured, declaredCodec = "AAC+", declaredBitrateKbps = 64))
    }

    @Test
    fun listedBitrateIsIgnoredWhenTheListingIsAnotherKindOfStream() {
        val measured = StreamFormat(MimeTypes.AUDIO_AAC, "mp4a.40.2", bitrateKbps = null, sampleRateHz = 48_000)
        assertEquals("AAC · 48 kHz", StreamQuality.label(measured, declaredCodec = "MP3", declaredBitrateKbps = 128))
    }

    @Test
    fun sampleRateWithDecimals() {
        val measured = StreamFormat(MimeTypes.AUDIO_MPEG, null, bitrateKbps = null, sampleRateHz = 44_100)
        val label = StreamQuality.label(measured, declaredCodec = null, declaredBitrateKbps = null)!!
        // "44,1 kHz" or "44.1 kHz" depending on the phone's language.
        assert(label.startsWith("MP3 · 44") && label.endsWith("1 kHz")) { label }
    }

    @Test
    fun directoryAloneBeforeTheStreamHasStarted() {
        assertEquals("MP3 · 128 kbps", StreamQuality.label(null, declaredCodec = "MP3", declaredBitrateKbps = 128))
    }

    @Test
    fun nothingKnownMeansNoBadge() {
        assertNull(StreamQuality.label(null, declaredCodec = "UNKNOWN", declaredBitrateKbps = 0))
        assertNull(StreamQuality.label(null, declaredCodec = null, declaredBitrateKbps = null))
    }
}
