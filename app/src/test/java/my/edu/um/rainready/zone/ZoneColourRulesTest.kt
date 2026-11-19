package my.edu.um.rainready.zone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneColourRulesTest {
    private val hour = 60 * 60 * 1000L
    private val now = 100 * hour

    private fun input(
        ground: String? = "low",
        override: String? = null,
        until: Long? = null,
        reports: List<ReportInput> = emptyList(),
        rain: Double? = 0.0
    ) = ZoneInput("Z6", ground, override, until, reports, rain)

    private fun report(
        status: String = "confirmed",
        agoHours: Double = 1.0,
        fixed: Boolean = false
    ) = ReportInput(
        status = status,
        category = "flood",
        createdAtMillis = (now - agoHours * hour).toLong(),
        fixedAtMillis = if (fixed) now - hour / 2 else null
    )

    private fun run(i: ZoneInput) = ZoneColourRules.compute(i, now)

    // ---------- 1. staff override ----------
    @Test fun staffOverride_beatsConfirmedReport() {
        val r = run(input(override = "green", until = now + hour, reports = listOf(report())))
        assertEquals(ZoneColour.GREEN, r.colour)
        assertEquals(ZoneReason.STAFF_OVERRIDE, r.reason)
    }

    @Test fun expiredStaffOverride_isIgnored() {
        val r = run(input(override = "green", until = now - 1, reports = listOf(report())))
        assertEquals(ZoneColour.RED, r.colour)
    }

    @Test fun staffOverrideWithoutEndTime_isIgnored() {
        val r = run(input(override = "red", until = null))
        assertEquals(ZoneColour.GREEN, r.colour)
    }

    // ---------- 2. confirmed report ----------
    @Test fun confirmedReport_makesRed() {
        val r = run(input(reports = listOf(report(agoHours = 3.0))))
        assertEquals(ZoneColour.RED, r.colour)
        assertEquals(ZoneReason.CONFIRMED_REPORT, r.reason)
    }

    @Test fun redMessage_includesCategory() {
        val r = run(input(reports = listOf(report())))
        assertEquals("Flood confirmed", r.message)
        assertEquals("flood", r.reportCategory)
    }

    @Test fun staleReport_afterSevenHours_notRed() {
        val r = run(input(reports = listOf(report(agoHours = 7.0))))
        assertEquals(ZoneColour.GREEN, r.colour)
    }

    @Test fun reportExactlySixHoursOld_isStale() {
        val r = run(input(reports = listOf(report(agoHours = 6.0))))
        assertEquals(ZoneColour.GREEN, r.colour)
    }

    @Test fun fixedReport_notRed() {
        val r = run(input(reports = listOf(report(fixed = true))))
        assertEquals(ZoneColour.GREEN, r.colour)
    }

    @Test fun unconfirmedReport_neverChangesColour() {
        val r = run(input(reports = listOf(report(status = "unconfirmed"))))
        assertEquals(ZoneColour.GREEN, r.colour)
    }

    @Test fun reportWithoutCreatedAt_isIgnored() {
        val bad = ReportInput("confirmed", "flood", null, null)
        val r = run(input(reports = listOf(bad)))
        assertEquals(ZoneColour.GREEN, r.colour)
    }

    // ---------- 3. rain on ground level ----------
    @Test fun confirmedRed_beatsRainYellow() {
        val r = run(input(rain = 20.0, reports = listOf(report())))
        assertEquals(ZoneColour.RED, r.colour)
    }

    @Test fun lowGround_rainBoundaries() {
        assertEquals(ZoneColour.GREEN, run(input(rain = 4.99)).colour)
        assertEquals(ZoneColour.YELLOW, run(input(rain = 5.0)).colour)
        assertEquals(ZoneColour.YELLOW, run(input(rain = 15.0)).colour)
        assertEquals(ZoneColour.YELLOW, run(input(rain = 15.01)).colour)
    }

    @Test fun mediumGround_yellowOnlyAboveFifteen() {
        assertEquals(ZoneColour.GREEN, run(input(ground = "medium", rain = 15.0)).colour)
        assertEquals(ZoneColour.YELLOW, run(input(ground = "medium", rain = 15.01)).colour)
    }

    @Test fun highGround_neverYellow() {
        assertEquals(ZoneColour.GREEN, run(input(ground = "high", rain = 50.0)).colour)
    }

    @Test fun rainYellow_isMarkedForecastOnly() {
        val r = run(input(rain = 20.0))
        assertEquals(ZoneReason.RAIN_ON_GROUND, r.reason)
        assertTrue(r.forecastOnly)
    }

    @Test fun rain_neverMakesRedByItself() {
        assertFalse(run(input(rain = 100.0)).colour == ZoneColour.RED)
    }

    // ---------- 4. missing input ----------
    @Test fun missingRain_doesNotCrash_andIsReported() {
        val r = run(input(rain = null))
        assertEquals(ZoneColour.GREEN, r.colour)
        assertTrue(r.missingInputs.contains("rain"))
    }

    @Test fun missingGroundLevel_noYellow_andIsReported() {
        val r = run(input(ground = null, rain = 20.0))
        assertEquals(ZoneColour.GREEN, r.colour)
        assertTrue(r.missingInputs.contains("groundLevel"))
    }

    @Test fun noDataAtAll_isGreen_withSafeWording() {
        val r = run(input(ground = null, rain = null))
        assertEquals(ZoneColour.GREEN, r.colour)
        assertEquals("No problems reported", r.message)
        assertNull(r.reportCategory)
    }
}
