package my.edu.um.rainready.zone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneColourRulesTest {

    private val now = 1_700_000_000_000L
    private val oneMin = 60_000L
    private val oneHour = 60 * oneMin

    private fun baseInput(
        zoneId: String = "Z6",
        groundLevel: String? = "low",
        staffOverride: String? = null,
        staffOverrideUntilMillis: Long? = null,
        reports: List<ReportInput> = emptyList(),
        rainMmPerHour: Double? = 0.0,
        weatherFetchedAtMillis: Long? = now - 5 * oneMin
    ) = ZoneInput(
        zoneId = zoneId,
        groundLevel = groundLevel,
        staffOverride = staffOverride,
        staffOverrideUntilMillis = staffOverrideUntilMillis,
        reports = reports,
        rainMmPerHour = rainMmPerHour,
        weatherFetchedAtMillis = weatherFetchedAtMillis
    )

    @Test
    fun staffOverrideTakesTopPrecedenceOverConfirmedReport() {
        val input = baseInput(
            staffOverride = "green",
            staffOverrideUntilMillis = now + 2 * oneHour,
            reports = listOf(
                ReportInput("confirmed", "flood", now - 10 * oneMin, null)
            )
        )
        val state = ZoneColourRules.compute(input, now)
        assertEquals(ZoneColour.GREEN, state.colour)
        assertEquals(ZoneReason.STAFF_OVERRIDE, state.reason)
    }

    @Test
    fun expiredStaffOverrideFallsThroughToConfirmedReport() {
        val input = baseInput(
            staffOverride = "green",
            staffOverrideUntilMillis = now - 1,
            reports = listOf(
                ReportInput("confirmed", "flood", now - 10 * oneMin, null)
            )
        )
        val state = ZoneColourRules.compute(input, now)
        assertEquals(ZoneColour.RED, state.colour)
        assertEquals(ZoneReason.CONFIRMED_REPORT, state.reason)
    }

    @Test
    fun confirmedReportMakesZoneRedAndBeatsRain() {
        val input = baseInput(
            groundLevel = "low",
            rainMmPerHour = 10.0,
            reports = listOf(
                ReportInput("confirmed", "flood", now - 2 * oneHour, null)
            )
        )
        val state = ZoneColourRules.compute(input, now)
        assertEquals(ZoneColour.RED, state.colour)
        assertEquals(ZoneReason.CONFIRMED_REPORT, state.reason)
        assertEquals("flood", state.reportCategory)
    }

    @Test
    fun staleConfirmedReportAtSixHoursStopsBeingRed() {
        val input = baseInput(
            rainMmPerHour = 0.0,
            reports = listOf(
                ReportInput("confirmed", "flood", now - 6 * oneHour, null)
            )
        )
        val state = ZoneColourRules.compute(input, now)
        assertEquals(ZoneColour.GREEN, state.colour)
        assertEquals(ZoneReason.NO_PROBLEMS_REPORTED, state.reason)
    }

    @Test
    fun fixedReportStopsAffectingZoneImmediately() {
        val input = baseInput(
            rainMmPerHour = 0.0,
            reports = listOf(
                ReportInput("confirmed", "flood", now - 5 * oneMin, now - 1 * oneMin)
            )
        )
        val state = ZoneColourRules.compute(input, now)
        assertEquals(ZoneColour.GREEN, state.colour)
    }

    @Test
    fun unconfirmedReportNeverMakesZoneRed() {
        val input = baseInput(
            rainMmPerHour = 0.0,
            reports = listOf(
                ReportInput("unconfirmed", "flood", now - 5 * oneMin, null)
            )
        )
        val state = ZoneColourRules.compute(input, now)
        assertEquals(ZoneColour.GREEN, state.colour)
    }

    @Test
    fun freshRainOnLowGroundMakesZoneYellowAtMost() {
        val input = baseInput(
            groundLevel = "low",
            rainMmPerHour = 8.0,
            weatherFetchedAtMillis = now - 10 * oneMin
        )
        val state = ZoneColourRules.compute(input, now)
        assertEquals(ZoneColour.YELLOW, state.colour)
        assertEquals(ZoneReason.RAIN_ON_GROUND, state.reason)
        assertTrue(state.forecastOnly)
        assertFalse(state.staleWeatherBanner)
    }

    @Test
    fun rainOnHighGroundStaysGreen() {
        val input = baseInput(
            groundLevel = "high",
            rainMmPerHour = 25.0,
            weatherFetchedAtMillis = now - 5 * oneMin
        )
        val state = ZoneColourRules.compute(input, now)
        assertEquals(ZoneColour.GREEN, state.colour)
    }

    @Test
    fun staleWeatherOver15MinRetainsPreviousStateAndShowsBanner() {
        val previous = ZoneState(
            zoneId = "Z6",
            colour = ZoneColour.YELLOW,
            reason = ZoneReason.RAIN_ON_GROUND,
            message = "Heavy rain forecast on low ground, forecast only",
            forecastOnly = true,
            computedAtMillis = now - 20 * oneMin
        )
        val input = baseInput(
            groundLevel = "low",
            rainMmPerHour = 0.0,
            weatherFetchedAtMillis = now - 16 * oneMin
        )
        val state = ZoneColourRules.compute(input, now, previousState = previous)
        assertEquals(ZoneColour.YELLOW, state.colour)
        assertTrue(state.staleWeatherBanner)
        assertFalse(state.forecastOnly)
    }

    @Test
    fun missingInputsDoNotCrashAndAreRecorded() {
        val input = baseInput(
            groundLevel = null,
            rainMmPerHour = null,
            weatherFetchedAtMillis = null
        )
        val state = ZoneColourRules.compute(input, now)
        assertEquals(ZoneColour.GREEN, state.colour)
        assertTrue(state.staleWeatherBanner)
        assertTrue(state.missingInputs.containsAll(listOf("rain", "groundLevel", "weatherFetchedAt")))
    }
}