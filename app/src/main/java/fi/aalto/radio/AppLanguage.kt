package fi.aalto.radio

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * One source of truth for Aalto's in-app language choice.
 *
 * Android 13+ stores app locales in the platform LocaleManager, which also
 * keeps the system app-language screen in sync. Android 8-12 store the same
 * choice locally and apply it through a localized base context.
 */
internal object AppLanguage {
    private const val PREFS = "aalto_language"
    private const val KEY_TAG = "language_tag"

    val supportedTags = listOf("fi", "en", "sv", "de", "es", "pt")

    fun selectedTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return locales?.takeUnless { it.isEmpty }?.get(0)?.language
                ?.takeIf { it in supportedTags }
                .orEmpty()
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, "")
            ?.takeIf { it in supportedTags }
            .orEmpty()
    }

    fun set(context: Context, tag: String) {
        val normalized = tag.takeIf { it in supportedTags }.orEmpty()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (normalized.isEmpty()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(normalized)
            }
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = locales
            return
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, normalized)
            .apply()

        applyLegacyResources(context.applicationContext, normalized)
        (context as? Activity)?.recreate()
    }

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context

        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, "")
            ?.takeIf { it in supportedTags }
            .orEmpty()
        if (tag.isEmpty()) return context

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(config)
    }

    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        val name = locale.getDisplayLanguage(locale)
        return name.replaceFirstChar { first ->
            if (first.isLowerCase()) first.titlecase(locale) else first.toString()
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyResources(context: Context, tag: String) {
        val locales = if (tag.isEmpty()) {
            Resources.getSystem().configuration.locales
        } else {
            LocaleList(Locale.forLanguageTag(tag))
        }
        locales.get(0)?.let(Locale::setDefault)
        val config = Configuration(context.resources.configuration).apply {
            setLocales(locales)
        }
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
