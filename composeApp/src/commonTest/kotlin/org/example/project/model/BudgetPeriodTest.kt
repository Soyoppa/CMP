package org.example.project.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import org.example.project.data.ledger.LedgerEntry
import org.example.project.util.DateUtils

class BudgetPeriodTest {

    @Test
    fun splitsTheMonthOnThe15thAnd16th() {
        assertEquals(BudgetPeriod(2026, 9, 1), BudgetPeriod.of(LocalDate(2026, 9, 1)))
        assertEquals(BudgetPeriod(2026, 9, 1), BudgetPeriod.of(LocalDate(2026, 9, 15)))
        assertEquals(BudgetPeriod(2026, 9, 2), BudgetPeriod.of(LocalDate(2026, 9, 16)))
        assertEquals(BudgetPeriod(2026, 9, 2), BudgetPeriod.of(LocalDate(2026, 9, 30)))
    }

    @Test
    fun secondHalfRunsToTheRealEndOfMonth() {
        assertEquals("Sep 16–30", BudgetPeriod(2026, 9, 2).label)
        assertEquals("Oct 16–31", BudgetPeriod(2026, 10, 2).label)
        assertEquals("Feb 16–28", BudgetPeriod(2026, 2, 2).label)
        assertEquals("Feb 16–29", BudgetPeriod(2028, 2, 2).label) // leap year
        assertEquals("Sep 1–15", BudgetPeriod(2026, 9, 1).label)
        assertTrue(LocalDate(2026, 10, 31) in BudgetPeriod(2026, 10, 2))
        assertFalse(LocalDate(2026, 10, 15) in BudgetPeriod(2026, 10, 2))
    }

    @Test
    fun stepsAcrossMonthAndYearBoundaries() {
        assertEquals(BudgetPeriod(2026, 9, 2), BudgetPeriod(2026, 9, 1).next())
        assertEquals(BudgetPeriod(2027, 1, 1), BudgetPeriod(2026, 12, 2).next())
        assertEquals(BudgetPeriod(2025, 12, 2), BudgetPeriod(2026, 1, 1).previous())
        assertEquals(
            listOf("2026-09-2", "2026-09-1", "2026-08-2"),
            generateSequence(BudgetPeriod(2026, 9, 2)) { it.previous() }.take(3).map { it.id }.toList(),
        )
    }

    @Test
    fun monthlyPeriodsCoverTheWholeMonthAndKeepTheirOwnIds() {
        val october = BudgetPeriod.of(LocalDate(2026, 10, 20), BudgetCycle.MONTHLY)
        assertEquals(BudgetPeriod(2026, 10, BudgetPeriod.WHOLE_MONTH), october)
        assertEquals(BudgetCycle.MONTHLY, october.cycle)
        assertEquals("2026-10", october.id)                     // never collides with "2026-10-1"
        assertEquals("Oct", october.label)
        assertEquals("1–31", october.rangeLabel)
        assertTrue(LocalDate(2026, 10, 1) in october)
        assertTrue(LocalDate(2026, 10, 31) in october)
        assertEquals(october, BudgetPeriod.fromId(october.id))
        assertEquals(listOf(october), october.periodsInMonth())

        assertEquals(BudgetPeriod(2026, 11, BudgetPeriod.WHOLE_MONTH), october.next())
        assertEquals(BudgetPeriod(2026, 9, BudgetPeriod.WHOLE_MONTH), october.previous())
        assertEquals(BudgetPeriod(2027, 1, BudgetPeriod.WHOLE_MONTH), BudgetPeriod(2026, 12, BudgetPeriod.WHOLE_MONTH).next())

        assertEquals(12, BudgetPeriod.allIn(2026, BudgetCycle.MONTHLY).size)
        assertEquals(24, BudgetPeriod.allIn(2026, BudgetCycle.CUT_OFF).size)
        // A monthly budget prompts around the 1st, like the first cut-off does.
        assertTrue(october.isBudgetingOpen(LocalDate(2026, 9, 29)))
        assertTrue(october.isBudgetingOpen(LocalDate(2026, 10, 5)))
        assertFalse(october.isBudgetingOpen(LocalDate(2026, 10, 6)))
    }

    @Test
    fun aPeriodCanBeRecutIntoTheOtherCycle() {
        val secondHalf = BudgetPeriod(2026, 10, 2)
        assertEquals(BudgetPeriod(2026, 10, BudgetPeriod.WHOLE_MONTH), secondHalf.inCycle(BudgetCycle.MONTHLY))
        assertEquals(secondHalf, secondHalf.inCycle(BudgetCycle.CUT_OFF))
        assertEquals(
            BudgetPeriod(2026, 10, 1),
            BudgetPeriod(2026, 10, BudgetPeriod.WHOLE_MONTH).inCycle(BudgetCycle.CUT_OFF),
        )
    }

    @Test
    fun idRoundTripsAndSortsChronologically() {
        val period = BudgetPeriod(2026, 3, 2)
        assertEquals("2026-03-2", period.id)
        assertEquals(period, BudgetPeriod.fromId(period.id))
        assertNull(BudgetPeriod.fromId("2026-13-1"))
        assertNull(BudgetPeriod.fromId("budget"))
        assertTrue(BudgetPeriod(2026, 9, 1) < BudgetPeriod(2026, 9, 2))
        assertTrue(BudgetPeriod(2025, 12, 2) < BudgetPeriod(2026, 1, 1))
    }

    @Test
    fun ledgerDatesInEveryStoredFormatLandInTheRightCutOff() {
        fun periodOf(date: String) =
            BudgetPeriod.of(LedgerEntry(description = "x", amount = 1.0, category = "", monthNumber = 0, date = date))

        assertEquals(BudgetPeriod(2026, 9, 2), periodOf("9/16/2026"))      // app form / sheet
        assertEquals(BudgetPeriod(2026, 9, 1), periodOf("2026-09-15"))     // Firestore (ISO)
        assertEquals(BudgetPeriod(2026, 6, 1), periodOf("June 15, 2026"))
        assertEquals(BudgetPeriod(2026, 6, 2), periodOf("20 Jun 2026"))
        assertNull(periodOf(""))
        assertNull(periodOf("sometime"))
        assertEquals(LocalDate(2026, 6, 7), DateUtils.parseLedgerDate("June 7", defaultYear = 2026))
    }
}
