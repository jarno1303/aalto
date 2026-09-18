package fi.aalto.radio

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Where the phone physically is, at country resolution.
 *
 * The mobile network knows this and tells it to every app without a
 * permission: a Finnish phone in Spain reports ES while roaming. The phone's
 * language region does not — it still says FI — which is why station
 * suggestions used to stay Finnish abroad.
 *
 * No location permission is asked for and none is needed. Country resolution
 * is all a radio app can use anyway.
 */
internal object DeviceCountry {

    /** Where the phone is now, or null when there is no mobile network. */
    fun network(context: Context): String? =
        telephony(context)?.networkCountryIso.normalise()

    /** The SIM's home country: where the user is from, not where they are. */
    fun home(context: Context): String? =
        telephony(context)?.simCountryIso.normalise()

    /**
     * The best guess in order: the network the phone is on, the SIM's home
     * country, the phone's language region. Wi-Fi-only tablets fall through to
     * the last one, which is what they had before.
     */
    fun best(context: Context): String =
        network(context)
            ?: home(context)
            ?: Locale.getDefault().country.normalise()
            ?: FALLBACK

    private fun telephony(context: Context): TelephonyManager? = runCatching {
        context.applicationContext.getSystemService(TelephonyManager::class.java)
    }.getOrNull()

    private fun String?.normalise(): String? =
        this?.trim()?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{2}")) }

    /** Only reached on a device with no SIM and no country in its language. */
    private const val FALLBACK = "US"
}
