package fi.aalto.radio

import android.content.Context

/**
 * Whether the first-launch station picker is shown. Only a brand-new install
 * sees it, and only until it is finished or skipped: an existing user (Room
 * favourites already migrated, or favourites from the pre-Room app) never
 * does, not even once after an update.
 */
internal enum class FirstLaunchState { PENDING, DONE }

internal object FirstLaunchDecision {
    const val STORED_PENDING = "pending"
    const val STORED_DONE = "done"

    fun decide(stored: String?, favoritesMigrated: Boolean, hasLegacyFavorites: Boolean): FirstLaunchState =
        when (stored) {
            STORED_DONE -> FirstLaunchState.DONE
            STORED_PENDING -> FirstLaunchState.PENDING
            else -> if (favoritesMigrated || hasLegacyFavorites) FirstLaunchState.DONE else FirstLaunchState.PENDING
        }

    /** The one-time "keep your stations on an account" nudge. */
    fun shouldNudgeSync(
        signedIn: Boolean,
        favoriteCount: Int,
        firstSeenAt: Long,
        now: Long,
        alreadyShown: Boolean
    ): Boolean = !signedIn && !alreadyShown && favoriteCount >= SYNC_NUDGE_MIN_FAVORITES &&
        firstSeenAt > 0L && now - firstSeenAt >= SYNC_NUDGE_AFTER_MS

    /** Tap order is kept: the first station picked becomes the first preset. */
    fun togglePick(picks: List<String>, stationId: String): List<String> =
        if (stationId in picks) picks - stationId else picks + stationId

    const val PICKER_SIZE = 12
    const val SYNC_NUDGE_MIN_FAVORITES = 3
    const val SYNC_NUDGE_AFTER_MS = 7L * 24 * 60 * 60 * 1000
}

internal object FirstLaunchPreference {
    private const val PREFS = "aalto_first_launch"
    private const val KEY_STATE = "state"
    private const val KEY_FIRST_SEEN = "first_seen_at"
    private const val KEY_SYNC_NUDGE = "sync_nudge_shown"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Decided once and stored at once, before the favourites migration runs,
     * so an interrupted first launch shows the picker again next time.
     */
    fun state(context: Context): FirstLaunchState {
        val prefs = prefs(context)
        val legacy = LegacyFavoriteStationStore(context)
        val state = FirstLaunchDecision.decide(
            stored = prefs.getString(KEY_STATE, null),
            favoritesMigrated = legacy.isRoomMigrationComplete(),
            hasLegacyFavorites = legacy.hasStoredFavorites()
        )
        val editor = prefs.edit()
        if (!prefs.contains(KEY_STATE)) {
            editor.putString(
                KEY_STATE,
                if (state == FirstLaunchState.PENDING) FirstLaunchDecision.STORED_PENDING else FirstLaunchDecision.STORED_DONE
            )
        }
        if (!prefs.contains(KEY_FIRST_SEEN)) editor.putLong(KEY_FIRST_SEEN, System.currentTimeMillis())
        editor.apply()
        return state
    }

    fun markDone(context: Context) {
        prefs(context).edit().putString(KEY_STATE, FirstLaunchDecision.STORED_DONE).apply()
    }

    fun firstSeenAt(context: Context): Long = prefs(context).getLong(KEY_FIRST_SEEN, 0L)

    fun syncNudgeShown(context: Context): Boolean = prefs(context).getBoolean(KEY_SYNC_NUDGE, false)

    fun markSyncNudgeShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_SYNC_NUDGE, true).apply()
    }
}
