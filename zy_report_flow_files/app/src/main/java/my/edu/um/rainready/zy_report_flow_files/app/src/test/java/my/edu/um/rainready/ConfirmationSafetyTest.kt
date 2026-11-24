package my.edu.um.rainready

import my.edu.um.rainready.data.ConfirmationRules
import my.edu.um.rainready.data.ReportRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for FR3.2 Safe Report Confirmations & NFR2 GeoJSON bounds (zy).
 */
class ConfirmationSafetyTest {

    @Test
    fun secondStudentConfirmation_incrementsToTwo_andStaysUnconfirmed() {
        val result = ConfirmationRules.nextState(
            currentCount = 1,
            currentStatus = "unconfirmed",
            alreadyConfirmedByUser = false,
            isStaff = false
        )
        assertTrue(result.isSuccess)
        assertEquals(2 to "unconfirmed", result.getOrNull())
    }

    @Test
    fun thirdStudentConfirmation_incrementsToThree_andFlipsToConfirmed() {
        val result = ConfirmationRules.nextState(
            currentCount = 2,
            currentStatus = "unconfirmed",
            alreadyConfirmedByUser = false,
            isStaff = false
        )
        assertTrue(result.isSuccess)
        assertEquals(3 to "confirmed", result.getOrNull())
    }

    @Test
    fun duplicateConfirmationBySameUser_isRejected() {
        val result = ConfirmationRules.nextState(
            currentCount = 2,
            currentStatus = "unconfirmed",
            alreadyConfirmedByUser = true,
            isStaff = false
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun staffConfirmation_flipsToConfirmedImmediately() {
        val result = ConfirmationRules.nextState(
            currentCount = 1,
            currentStatus = "unconfirmed",
            alreadyConfirmedByUser = false,
            isStaff = true
        )
        assertTrue(result.isSuccess)
        assertEquals(2 to "confirmed", result.getOrNull())
    }

    @Test
    fun confirmationOnFixedReport_isRejected() {
        val result = ConfirmationRules.nextState(
            currentCount = 3,
            currentStatus = "fixed",
            alreadyConfirmedByUser = false,
            isStaff = false
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun confirmationOnStaleReport_overSixHours_isRejected() {
        val result = ConfirmationRules.nextState(
            currentCount = 1,
            currentStatus = "unconfirmed",
            alreadyConfirmedByUser = false,
            isStaff = false,
            isStale = true
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun campusBounds_includeKK13_andRejectOutsideUM() {
        // KK13 (Z3) from Emmanuel_zones-draft_2026-11-10.geojson
        assertTrue(3.11900 in ReportRepository.MIN_LAT..ReportRepository.MAX_LAT)
        assertTrue(101.63868 in ReportRepository.MIN_LNG..ReportRepository.MAX_LNG)
        // >200 m south of UM
        assertFalse(3.1000 in ReportRepository.MIN_LAT..ReportRepository.MAX_LAT)
    }
}
