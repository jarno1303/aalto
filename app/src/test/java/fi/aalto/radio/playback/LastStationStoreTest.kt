package fi.aalto.radio.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LastStationStoreTest {

    @Test
    fun savedStationIdIsAvailableForStartupRestore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val item = MediaItem.Builder()
            .setMediaId("station.test.last")
            .setUri("https://example.com/test-stream")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Last station")
                    .build()
            )
            .build()

        LastStationStore.save(context, item)

        assertEquals("station.test.last", LastStationStore.id(context))
        assertEquals("station.test.last", LastStationStore.load(context)?.mediaId)
    }
}
