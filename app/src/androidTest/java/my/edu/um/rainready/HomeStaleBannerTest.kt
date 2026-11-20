package my.edu.um.rainready

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.edu.um.rainready.data.SampleHomeRepository
import my.edu.um.rainready.model.STALE_AFTER_MILLIS
import my.edu.um.rainready.ui.HomeScreen
import my.edu.um.rainready.ui.theme.RainReadyTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The grey "Can't get new reports" banner on Home must appear 15 minutes after the last update
 * even if nobody touches the screen (FR2.1). Time is simulated with the test's clock, so the
 * test takes seconds, not 15 minutes.
 */
@RunWith(AndroidJUnit4::class)
class HomeStaleBannerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val updatedAt = 1_700_000_000_000L
    private var startTestTime = 0L
    /** Extra wall-clock time that passes without the app's timer running (the phone asleep). */
    private var asleepMillis = 0L
    private val clock = { updatedAt + (rule.mainClock.currentTime - startTestTime) + asleepMillis }
    private val offlineTitle get() = rule.activity.getString(R.string.banner_offline_title)

    @Before
    fun showHome() {
        rule.mainClock.autoAdvance = false
        startTestTime = rule.mainClock.currentTime
        val state = SampleHomeRepository.rainyDay(now = updatedAt)
        rule.setContent { RainReadyTheme { HomeScreen(state, onChooseBuilding = {}, clock = clock) } }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun bannerTurnsGreyAfterFifteenMinutesWithoutTouching() {
        rule.onNodeWithText(offlineTitle).assertDoesNotExist()

        rule.mainClock.advanceTimeBy(STALE_AFTER_MILLIS - 60_000) // 14 minutes, untouched
        rule.onNodeWithText(offlineTitle).assertDoesNotExist()

        rule.mainClock.advanceTimeBy(2 * 60_000) // 16 minutes, still untouched
        rule.onNodeWithText(offlineTitle).assertIsDisplayed()
    }

    @Test
    fun bannerTurnsGreyWhenTheAppComesBackAfterTheTimerWasPaused() {
        rule.onNodeWithText(offlineTitle).assertDoesNotExist()

        // The phone sleeps for 20 minutes: wall-clock time moves on, the app's timer does not.
        asleepMillis = 20 * 60_000L
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        // The test clock is frozen, so keep drawing frames (16 ms each, far less than 15 minutes)
        // until the app has handled coming back to the front. Some phones take a moment.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodesWithText(offlineTitle).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(offlineTitle).assertIsDisplayed()
    }
}
