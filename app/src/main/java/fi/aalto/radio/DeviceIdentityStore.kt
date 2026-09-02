package fi.aalto.radio

import android.content.Context
import java.util.UUID

class DeviceIdentityStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun deviceId(): String {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val generated = UUID.randomUUID().toString()
        val saved = preferences.edit()
            .putString(KEY_DEVICE_ID, generated)
            .commit()

        if (!saved) {
            error("Could not persist Aalto device id")
        }

        return generated
    }

    fun clearForTests() {
        preferences.edit()
            .clear()
            .commit()
    }

    companion object {
        const val PREFERENCES_NAME = "aalto_device_identity"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
