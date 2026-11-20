package my.edu.um.rainready

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import my.edu.um.rainready.data.SampleMapRepository
import my.edu.um.rainready.model.STALE_AFTER_MILLIS
import my.edu.um.rainready.ui.MapScreen
import my.edu.um.rainready.ui.theme.RainReadyTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre

/**
 * The map's grey "Can't get new reports" banner must appear 15 minutes after the last update
 * even if nobody touches the screen (FR2.1). Time is simulated, so the test takes seconds.
 * (Coming back to the front after the phone slept is covered by HomeStaleBannerTest; both
 * screens use the same rememberIsStale().)
 */
@RunWith(AndroidJUnit4::class)
class MapStaleBannerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val updatedAt = 1_700_000_000_000L
    private var startTestTime = 0L
    private val clock = { updatedAt + (rule.mainClock.currentTime - startTestTime) }
    private val offlineTitle get() = rule.activity.getString(R.string.banner_offline_title)

    @Before
    fun showMap() {
        rule.runOnUiThread { MapLibre.getInstance(rule.activity.applicationContext) }
        rule.mainClock.autoAdvance = false
        startTestTime = rule.mainClock.currentTime
        val state = SampleMapRepository.rainyDay(now = updatedAt)
        rule.setContent { RainReadyTheme { MapScreen(state, clock = clock) } }
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
}
