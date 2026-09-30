package fi.aalto.radio

import android.app.Application
import android.content.Context

/**
 * Applies the saved in-app language before services, receivers or UI read
 * resources on Android versions that predate the platform app-locale API.
 */
class AaltoApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }
}
