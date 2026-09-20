package fi.aalto.radio

import android.content.Context

class LegacyFavoriteStationStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    fun favoriteIdsForMigration(): Set<String> {
        return preferences.getStringSet(KEY_FAVORITE_STATION_IDS, null)?.toSet()
            ?: FavoriteIds.defaultFavorites
    }

    /** Favourites saved by the pre-Room app: this is an existing user. */
    fun hasStoredFavorites(): Boolean = preferences.contains(KEY_FAVORITE_STATION_IDS)

    fun isRoomMigrationComplete(): Boolean {
        return preferences.getBoolean(KEY_ROOM_MIGRATION_COMPLETE, false)
    }

    fun markRoomMigrationComplete() {
        val saved = preferences.edit()
            .putBoolean(KEY_ROOM_MIGRATION_COMPLETE, true)
            .commit()

        if (!saved) {
            error("Could not mark favorite migration complete")
        }
    }

    fun saveLegacyFavoriteIds(favoriteIds: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_FAVORITE_STATION_IDS, favoriteIds)
            .commit()
    }

    fun clearForTests() {
        preferences.edit()
            .clear()
            .commit()
    }

    companion object {
        const val PREFERENCES_NAME = "aalto_radio"
        const val KEY_FAVORITE_STATION_IDS = "favorite_station_ids"
        const val KEY_ROOM_MIGRATION_COMPLETE = "favorite_station_ids_room_migrated_v1"
    }
}
