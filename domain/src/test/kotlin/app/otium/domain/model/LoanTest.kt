package app.otium.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanTest {

    @Test
    fun `a name is trimmed`() {
        assertEquals("Sam", Loan.cleanName("  Sam \n"))
    }

    @Test
    fun `a blank name or one over the limit is refused`() {
        assertNull(Loan.cleanName("  \t"))
        assertNull(Loan.cleanName("x".repeat(Loan.MAX_NAME_LENGTH + 1)))
        assertEquals("x".repeat(Loan.MAX_NAME_LENGTH), Loan.cleanName("x".repeat(100)))
    }

    @Test
    fun `a reminder must come after the loan began and a return not before`() {
        assertTrue(Loan.areDatesValid(10L, remindOn = 11L, returnedOn = 10L))
        assertTrue(Loan.areDatesValid(10L, remindOn = null, returnedOn = null))
        assertFalse(Loan.areDatesValid(10L, remindOn = 10L, returnedOn = null))
        assertFalse(Loan.areDatesValid(10L, remindOn = null, returnedOn = 9L))
    }

    @Test
    fun `a loan is open until it is returned`() {
        val loan = Loan(1L, 7L, "Sam", LoanDirection.LENT, lentOn = 10L)

        assertTrue(loan.isOpen)
        assertFalse(loan.copy(returnedOn = 12L).isOpen)
    }

    @Test
    fun `loans are listed newest first`() {
        val older = Loan(1L, 7L, "Sam", LoanDirection.LENT, lentOn = 5L, returnedOn = 6L)
        val newer = Loan(2L, 7L, "Ana", LoanDirection.LENT, lentOn = 9L)

        assertEquals(listOf(newer, older), listOf(older, newer).sortedWith(Loan.NEWEST_FIRST))
    }
}
