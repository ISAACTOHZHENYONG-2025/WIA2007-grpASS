package my.edu.um.rainready.zone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cases from the PR #12 review: what happens when the weather data is more than 15 minutes old
 * and the rules are given the zone's previous state. Kept in its own file so it does not clash
 * with changes to ZoneColourRulesTest.
 */
class ZoneColourRulesStaleWeatherTest {
    private val now = 1_700_000_000_000L
    private val oneMin = 60_000L
    private val oneHour = 60 * oneMin

    /** Weather fetched 30 minutes ago, so it is stale. */
    private fun staleWeatherInput(
        reports: List<ReportInput> = emptyList(),
        staffOverride: String? = null,
        staffOverrideUntilMillis: Long? = null,
        groundLevel: String = "high",
        zoneId: String = "Z7",
    ) = ZoneInput(
        zoneId = zoneId,
        groundLevel = groundLevel,
        staffOverride = staffOverride,
        staffOverrideUntilMillis = staffOverrideUntilMillis,
        reports = reports,
        rainMmPerHour = 0.0,
        weatherFetchedAtMillis = now - 30 * oneMin,
    )

    @Test
    fun reportFixedWhileWeatherIsStale_zoneNoLongerRed() {
        val confirmed = ReportInput("confirmed", "flood", createdAtMillis = now - oneHour, fixedAtMillis = null)
        val wasRed = ZoneColourRules.compute(staleWeatherInput(listOf(confirmed)), now - 5 * oneMin)
        assertEquals(ZoneColour.RED, wasRed.colour)

        val fixed = confirmed.copy(status = "fixed", fixedAtMillis = now - oneMin)
        val state = ZoneColourRules.compute(staleWeatherInput(listOf(fixed)), now, previousState = wasRed)

        assertEquals(ZoneColour.GREEN, state.colour)
        assertEquals(ZoneReason.NO_PROBLEMS_REPORTED, state.reason)
        assertTrue(state.staleWeatherBanner)
    }

    @Test
    fun overrideEndsWhileWeatherIsStale_zoneNoLongerRed() {
        val until = now - oneMin
        val wasRed = ZoneColourRules.compute(staleWeatherInput(staffOverride = "red", staffOverrideUntilMillis = until), now - 5 * oneMin)
        assertEquals(ZoneReason.STAFF_OVERRIDE, wasRed.reason)

        val state = ZoneColourRules.compute(
            staleWeatherInput(staffOverride = "red", staffOverrideUntilMillis = until), now, previousState = wasRed,
        )

        assertEquals(ZoneColour.GREEN, state.colour)
    }

    @Test
    fun rainYellowIsStillKeptWhileWeatherIsStale() {
        val wasYellow = ZoneState(
            zoneId = "Z6",
            colour = ZoneColour.YELLOW,
            reason = ZoneReason.RAIN_ON_GROUND,
            message = "Heavy rain forecast on low ground, forecast only",
            forecastOnly = true,
            computedAtMillis = now - 20 * oneMin,
        )
        val state = ZoneColourRules.compute(staleWeatherInput(groundLevel = "low", zoneId = "Z6"), now, previousState = wasYellow)

        assertEquals(ZoneColour.YELLOW, state.colour)
        assertTrue(state.staleWeatherBanner)
    }

    @Test
    fun messageDoesNotGrowOnRepeatedRecomputes() {
        val wasYellow = ZoneState(
            zoneId = "Z6",
            colour = ZoneColour.YELLOW,
            reason = ZoneReason.RAIN_ON_GROUND,
            message = "Heavy rain forecast on low ground, forecast only",
            forecastOnly = true,
            computedAtMillis = now - 20 * oneMin,
        )
        var state = wasYellow
        repeat(3) { i ->
            state = ZoneColourRules.compute(staleWeatherInput(groundLevel = "low", zoneId = "Z6"), now + i * oneMin, previousState = state)
        }

        assertEquals(wasYellow.message, state.message)
    }

    @Test
    fun previousStateOfAnotherZoneIsNotKept() {
        val otherZoneYellow = ZoneState(
            zoneId = "Z6",
            colour = ZoneColour.YELLOW,
            reason = ZoneReason.RAIN_ON_GROUND,
            message = "Heavy rain forecast on low ground, forecast only",
            computedAtMillis = now - 20 * oneMin,
        )
        val state = ZoneColourRules.compute(staleWeatherInput(zoneId = "Z7"), now, previousState = otherZoneYellow)

        assertEquals("Z7", state.zoneId)
        assertEquals(ZoneColour.GREEN, state.colour)
    }

    @Test
    fun overrideEndingMoreThanTwelveHoursAwayIsIgnored() {
        val tooLong = ZoneColourRules.compute(
            staleWeatherInput(staffOverride = "red", staffOverrideUntilMillis = now + 13 * oneHour), now,
        )
        val withinCap = ZoneColourRules.compute(
            staleWeatherInput(staffOverride = "red", staffOverrideUntilMillis = now + 12 * oneHour), now,
        )

        assertEquals(ZoneColour.GREEN, tooLong.colour)
        assertEquals(ZoneReason.STAFF_OVERRIDE, withinCap.reason)
    }
}
