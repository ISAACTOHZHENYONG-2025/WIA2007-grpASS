package my.edu.um.rainready

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.flowOf
import my.edu.um.rainready.data.CampusData
import my.edu.um.rainready.data.SampleMapRepository
import my.edu.um.rainready.data.SampleRouteChecker
import my.edu.um.rainready.ui.MapRoute
import my.edu.um.rainready.ui.RouteCheckScreen
import my.edu.um.rainready.ui.theme.RainReadyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre

/**
 * The route check from picking a start and a destination to the verdict (FR2.3), with the sample
 * data: one trip for each of the four verdicts. Location is not asked for, so the start is picked by hand.
 */
@RunWith(AndroidJUnit4::class)
class RouteCheckScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var shownOnMap: MapRoute? = null
    private fun text(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    @Before
    fun showRouteCheck() {
        rule.runOnUiThread { MapLibre.getInstance(rule.activity.applicationContext) }
        val map = SampleMapRepository.rainyDay()
        val checker = SampleRouteChecker(CampusData.load(rule.activity), flowOf(map))
        rule.setContent {
            RainReadyTheme {
                RouteCheckScreen(
                    checker = checker,
                    mapState = map,
                    onBack = {},
                    onShowOnMap = { shownOnMap = it },
                    askForLocation = false,
                )
            }
        }
    }

    /** Picks the start by searching for it, as a user would in a long list, then the destination. */
    private fun trip(start: String, destination: String) {
        rule.onNodeWithText(text(R.string.route_location_off)).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput(start.substringAfterLast('(').substringBefore(')').ifEmpty { start })
        rule.onNode(hasText(start) and !hasSetTextAction()).performClick() // the list row, not the search box
        rule.onNodeWithText(text(R.string.route_from_building, start)).assertIsDisplayed()
        rule.onNodeWithText(destination).performClick()
        rule.waitForIdle()
    }

    @Test
    fun usualPathClear_saysGo() {
        trip("Kinabalu Residential College (KK8)", "Faculty of Computer Science and IT (FSKTM)")
        rule.onNodeWithText(text(R.string.verdict_go_title), substring = true).assertIsDisplayed()
        rule.onNodeWithText(
            text(R.string.verdict_go_body, "Faculty of Computer Science and IT (FSKTM)", 7), substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun cautionOnLowGround_saysGoTakeCareAndLeaveEarly() {
        trip("LRT Universiti station", "Main Library")
        rule.onNodeWithText(text(R.string.verdict_care_title), substring = true).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.verdict_leave_early), substring = true).assertIsDisplayed()
    }

    @Test
    fun usualPathFlooded_showsSaferRouteAndOpensItOnTheMap() {
        trip("Tuanku Kurshiah Residential College (KK3)", "Faculty of Arts and Social Sciences")
        rule.onNodeWithText(text(R.string.verdict_safer_title), substring = true).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.verdict_safer_minutes, 4, 13), substring = true).assertIsDisplayed()
        rule.onNodeWithText("Fallen tree confirmed by 3 people in Science and APM", substring = true).assertIsDisplayed()

        rule.onNodeWithText(text(R.string.route_show_on_map)).performScrollTo().performClick()
        val route = shownOnMap
        assertNotNull(route)
        assertEquals(text(R.string.map_route_to, "Faculty of Arts and Social Sciences"), route!!.title)
        assertNotNull(route.main)
        assertEquals(1, route.others.size)
    }

    @Test
    fun everyPathFlooded_saysNoSafeRoute() {
        trip("Raja Dr. Nazrin Shah Residential College (KK12)", "Faculty of Science")
        rule.onNodeWithText(text(R.string.verdict_none_title), substring = true).assertIsDisplayed()
    }

    @Test
    fun checkAnother_goesBackToTheBuildingList() {
        trip("Kinabalu Residential College (KK8)", "Faculty of Computer Science and IT (FSKTM)")
        rule.onNodeWithText(text(R.string.route_check_another)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.route_buildings)).assertIsDisplayed()
    }
}
