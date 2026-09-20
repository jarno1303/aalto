package fi.aalto.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstLaunchDecisionTest {

    @Test
    fun brandNewInstallSeesThePicker() {
        assertEquals(
            FirstLaunchState.PENDING,
            FirstLaunchDecision.decide(stored = null, favoritesMigrated = false, hasLegacyFavorites = false)
        )
    }

    @Test
    fun existingUserNeverSeesThePicker() {
        assertEquals(
            FirstLaunchState.DONE,
            FirstLaunchDecision.decide(stored = null, favoritesMigrated = true, hasLegacyFavorites = false)
        )
        assertEquals(
            FirstLaunchState.DONE,
            FirstLaunchDecision.decide(stored = null, favoritesMigrated = false, hasLegacyFavorites = true)
        )
    }

    @Test
    fun interruptedFirstLaunchShowsThePickerAgain() {
        // The migration has run by then, but the stored decision wins.
        assertEquals(
            FirstLaunchState.PENDING,
            FirstLaunchDecision.decide(stored = "pending", favoritesMigrated = true, hasLegacyFavorites = false)
        )
    }

    @Test
    fun finishedPickerIsNeverShownTwice() {
        assertEquals(
            FirstLaunchState.DONE,
            FirstLaunchDecision.decide(stored = "done", favoritesMigrated = false, hasLegacyFavorites = false)
        )
    }

    @Test
    fun picksKeepTapOrderAndToggle() {
        var picks = emptyList<String>()
        picks = FirstLaunchDecision.togglePick(picks, "radio-nova")
        picks = FirstLaunchDecision.togglePick(picks, "yle-radio-suomi")
        picks = FirstLaunchDecision.togglePick(picks, "radio-rock")
        assertEquals(listOf("radio-nova", "yle-radio-suomi", "radio-rock"), picks)
        picks = FirstLaunchDecision.togglePick(picks, "yle-radio-suomi")
        assertEquals(listOf("radio-nova", "radio-rock"), picks)
    }

    @Test
    fun syncNudgeWaitsForAWeekAndThreeStations() {
        val day = 24L * 60 * 60 * 1000
        fun nudge(signedIn: Boolean = false, count: Int = 3, age: Long = 7 * day, shown: Boolean = false) =
            FirstLaunchDecision.shouldNudgeSync(signedIn, count, firstSeenAt = 1_000L, now = 1_000L + age, alreadyShown = shown)

        assertTrue(nudge())
        assertFalse(nudge(age = 6 * day))
        assertFalse(nudge(count = 2))
        assertFalse(nudge(signedIn = true))
        assertFalse(nudge(shown = true))
    }
}
