package fi.aalto.radio

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The flag for a two-letter country code, as the emoji the system already
 * draws (regional indicator letters): no image files, every country covered.
 * Null for anything that is not a country code.
 */
internal fun countryFlag(countryCode: String): String? {
    val code = countryCode.trim().uppercase()
    if (!code.matches(Regex("[A-Z]{2}"))) return null
    return code.map { letter -> String(Character.toChars(REGIONAL_INDICATOR_A + (letter - 'A'))) }
        .joinToString("")
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6

/**
 * A flag next to a country name, only where countries are chosen (search's
 * country line, the country list). Hidden from TalkBack: the name beside it
 * already says the country.
 */
@Composable
internal fun CountryFlag(countryCode: String, size: TextUnit = 18.sp, modifier: Modifier = Modifier) {
    val flag = countryFlag(countryCode) ?: return
    Text(text = flag, fontSize = size, modifier = modifier.clearAndSetSemantics { })
}

/**
 * The flag of a station's country when it is not the phone's own country
 * (where the phone is, not which country the user browses). Null at home and
 * for stations without a country, such as the user's own streams.
 */
internal fun foreignStationFlag(context: Context, countryCode: String): String? {
    val code = countryCode.trim().uppercase()
    if (code.isEmpty() || code == HomeCountry.code(context)) return null
    return countryFlag(code)
}

/** Read once per process: rows ask for it on every draw. */
private object HomeCountry {
    @Volatile
    private var cached: String? = null

    fun code(context: Context): String =
        cached ?: DeviceCountry.best(context.applicationContext).uppercase().also { cached = it }
}
