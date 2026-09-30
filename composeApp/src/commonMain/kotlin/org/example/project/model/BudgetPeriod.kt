package org.example.project.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import org.example.project.data.ledger.LedgerEntry
import org.example.project.util.DateUtils

/**
 * One budget cut-off: the 1st–15th ([half] = 1) or the 16th–end of month ([half] = 2).
 * Budgets are set, and spending is totalled, per cut-off — matching twice-a-month paydays.
 */
data class BudgetPeriod(val year: Int, val month: Int, val half: Int) : Comparable<BudgetPeriod> {

    init {
        require(month in 1..12) { "month must be 1..12" }
        require(half in 1..2) { "half must be 1 or 2" }
    }

    /** Stable key, e.g. "2026-09-2" — also the Firestore document id for this cut-off's budget. */
    val id: String get() = "$year-${month.toString().padStart(2, '0')}-$half"

    val startDay: Int get() = if (half == 1) 1 else 16

    val endDay: Int get() = if (half == 1) 15 else lastDayOfMonth(year, month)

    /** "Sep" */
    val monthLabel: String get() = DateUtils.monthName(month).take(3)

    /** "16–30" */
    val rangeLabel: String get() = "$startDay–$endDay"

    /** "Sep 16–30" */
    val label: String get() = "$monthLabel $rangeLabel"

    val endDate: LocalDate get() = LocalDate(year, month, endDay)

    operator fun contains(date: LocalDate): Boolean =
        date.year == year && date.month.number == month && date.day in startDay..endDay

    fun previous(): BudgetPeriod = when {
        half == 2 -> copy(half = 1)
        month == 1 -> BudgetPeriod(year - 1, 12, 2)
        else -> BudgetPeriod(year, month - 1, 2)
    }

    fun next(): BudgetPeriod = when {
        half == 1 -> copy(half = 2)
        month == 12 -> BudgetPeriod(year + 1, 1, 1)
        else -> BudgetPeriod(year, month + 1, 1)
    }

    override fun compareTo(other: BudgetPeriod): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.half })

    companion object {
        fun of(date: LocalDate): BudgetPeriod =
            BudgetPeriod(date.year, date.month.number, if (date.day <= 15) 1 else 2)

        /** The cut-off that today falls in (device time zone). */
        fun current(): BudgetPeriod = of(DateUtils.today())

        /** The cut-off [entry] belongs to, or null when its date can't be read. */
        fun of(entry: LedgerEntry): BudgetPeriod? = DateUtils.parseLedgerDate(entry.date)?.let(::of)

        /** Parses an [id] such as "2026-09-2"; null when malformed. */
        fun fromId(id: String): BudgetPeriod? {
            val parts = id.split("-").map { it.toIntOrNull() ?: return null }
            if (parts.size != 3) return null
            return runCatching { BudgetPeriod(parts[0], parts[1], parts[2]) }.getOrNull()
        }

        /** The [count] cut-offs ending at [last], oldest first. */
        fun recent(count: Int, last: BudgetPeriod = current()): List<BudgetPeriod> =
            generateSequence(last) { it.previous() }.take(count).toList().asReversed()

        private fun lastDayOfMonth(year: Int, month: Int): Int =
            LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    }
}
