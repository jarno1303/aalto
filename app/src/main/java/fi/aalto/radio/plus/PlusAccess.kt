package fi.aalto.radio.plus

import android.content.Context
import fi.aalto.radio.BuildConfig

/**
 * The one place that knows whether the user has Aalto Plus right now.
 * See docs/AALTO_PLUS.md: one check per feature, at its entry point, and the
 * check fails open once real billing exists.
 */
interface PlusAccess {
    /** Onko käyttäjällä voimassa oleva oikeus juuri nyt. */
    fun isActive(): Boolean
}

/**
 * Before Play Billing exists nobody has Plus, so release builds answer false.
 * Debug builds can switch it on in Settings to test both sides of the line.
 *
 * When billing arrives, it replaces this class; callers do not change.
 */
internal class LocalPlusAccess(context: Context) : PlusAccess {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isActive(): Boolean =
        BuildConfig.DEBUG && prefs.getBoolean(KEY_DEBUG_ACTIVE, false)

    /** Debug builds only; ignored in release. */
    fun setDebugActive(active: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit().putBoolean(KEY_DEBUG_ACTIVE, active).apply()
    }

    private companion object {
        const val PREFS = "aalto_plus"
        const val KEY_DEBUG_ACTIVE = "debug_active"
    }
}

internal object Plus {
    fun access(context: Context): LocalPlusAccess = LocalPlusAccess(context)
}
