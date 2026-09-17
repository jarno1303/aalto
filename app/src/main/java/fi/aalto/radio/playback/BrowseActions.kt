package fi.aalto.radio.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import fi.aalto.radio.R

/**
 * Android Auto "custom browse actions": small buttons on a station tile in
 * the car's lists. Used for the heart (add to / remove from own stations).
 * Keys are the platform's documented extras (androidx.media MediaConstants).
 */
internal object BrowseActions {
    const val ADD_FAVORITE = "fi.aalto.radio.BROWSE_ADD_FAVORITE"
    const val REMOVE_FAVORITE = "fi.aalto.radio.BROWSE_REMOVE_FAVORITE"

    private const val KEY_ROOT_LIST = "android.media.extras.CUSTOM_BROWSER_ACTION_ROOT_LIST"
    private const val KEY_ACTION_ID = "android.media.extras.KEY_CUSTOM_BROWSER_ACTION_ID"
    private const val KEY_ACTION_LABEL = "android.media.extras.KEY_CUSTOM_BROWSER_ACTION_LABEL"
    private const val KEY_ACTION_ICON_URI = "android.media.extras.KEY_CUSTOM_BROWSER_ACTION_ICON_URI"
    private const val KEY_ACTION_EXTRAS = "android.media.extras.KEY_CUSTOM_BROWSER_ACTION_EXTRAS"
    const val KEY_ITEM_ACTION_IDS = "android.media.extras.CUSTOM_BROWSER_ACTION_ID_LIST"
    const val KEY_MEDIA_ITEM_ID = "android.media.extras.KEY_CUSTOM_BROWSER_ACTION_MEDIA_ITEM_ID"
    private const val KEY_RESULT_MESSAGE = "android.media.extras.CUSTOM_BROWSER_ACTION_RESULT_MESSAGE"
    private const val KEY_RESULT_REFRESH_ITEM = "android.media.extras.CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM"

    fun forStation(isFavorite: Boolean): List<String> =
        listOf(if (isFavorite) REMOVE_FAVORITE else ADD_FAVORITE)

    /** Declared once in the library root extras. */
    fun putRootActions(context: Context, extras: Bundle) {
        extras.putParcelableArrayList(
            KEY_ROOT_LIST,
            arrayListOf(
                action(context, ADD_FAVORITE, R.string.favorite_add, R.drawable.ic_browse_heart_outline),
                action(context, REMOVE_FAVORITE, R.string.favorite_remove, R.drawable.ic_browse_heart_filled)
            )
        )
    }

    fun result(message: String): Bundle = Bundle().apply {
        putString(KEY_RESULT_MESSAGE, message)
        putBoolean(KEY_RESULT_REFRESH_ITEM, true)
    }

    private fun action(context: Context, id: String, labelRes: Int, iconRes: Int): Bundle =
        Bundle().apply {
            putString(KEY_ACTION_ID, id)
            putString(KEY_ACTION_LABEL, context.getString(labelRes))
            putString(
                KEY_ACTION_ICON_URI,
                Uri.Builder()
                    .scheme("android.resource")
                    .authority(context.packageName)
                    .appendPath("drawable")
                    .appendPath(context.resources.getResourceEntryName(iconRes))
                    .build()
                    .toString()
            )
            putBundle(KEY_ACTION_EXTRAS, Bundle())
        }
}
