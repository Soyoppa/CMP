package org.example.project.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import org.example.project.voice.VoiceTransactionParser

class FormattingAndParsingTest {

    @Test
    fun formatPesoRoundsToCentsAndGroupsThousands() {
        assertEquals("1.29", FormatUtils.formatPeso(1.29)) // used to render "1.28"
        assertEquals("1,234.50", FormatUtils.formatPeso(1234.5))
        assertEquals("1,000,000.00", FormatUtils.formatPeso(1_000_000.0))
        assertEquals("-12.50", FormatUtils.formatPeso(-12.5)) // used to render "-12.-50"
        assertEquals("0.00", FormatUtils.formatPeso(-0.001))
    }

    @Test
    fun parseDateAcceptsFormDatesOnly() {
        assertEquals(LocalDate(2026, 3, 1), DateUtils.parseDate("3/1/2026"))
        assertNull(DateUtils.parseDate("2/30/2026"))
        assertNull(DateUtils.parseDate("2026-03-01"))
        assertNull(DateUtils.parseDate("a/b/c"))
    }

    @Test
    fun monthNumberFromDateHandlesLedgerFormats() {
        assertEquals(3, DateUtils.monthNumberFromDate("3/1/2026"))
        assertEquals(6, DateUtils.monthNumberFromDate("2026-06-15"))
        assertEquals(6, DateUtils.monthNumberFromDate("June 15, 2026"))
        assertEquals(0, DateUtils.monthNumberFromDate(""))
    }

    @Test
    fun voiceParserExtractsAmountCategoryAndIntent() {
        val parsed = VoiceTransactionParser.parse("spent 1,200 pesos on groceries", listOf("Groceries", "Rent"))
        assertEquals("1200", parsed.amount)
        assertEquals("Groceries", parsed.category)
        assertEquals(false, parsed.isIncome)

        val income = VoiceTransactionParser.parse("received 5000 salary", listOf("Salary"))
        assertEquals(true, income.isIncome)
        assertEquals("5000", income.amount)
    }
}
