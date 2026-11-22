package my.edu.um.rainready

import my.edu.um.rainready.data.ConfirmationRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for FR3.2 Safe Report Confirmations (zy).
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
}
