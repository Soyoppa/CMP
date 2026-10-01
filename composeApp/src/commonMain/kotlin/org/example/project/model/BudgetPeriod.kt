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

    /** Calendar month this cut-off belongs to, e.g. "2026-09" — the chart groups by this. */
    val monthKey: String get() = "$year-${month.toString().padStart(2, '0')}"

    /** "16–30" */
    val rangeLabel: String get() = "$startDay–$endDay"

    /** "Sep 16–30" */
    val label: String get() = "$monthLabel $rangeLabel"

    val startDate: LocalDate get() = LocalDate(year, month, startDay)

    val endDate: LocalDate get() = LocalDate(year, month, endDay)

    /**
     * Whether [date] falls in this cut-off's budgeting window — the days the app asks for its
     * budget. A budget is set once the salary for the cut-off has arrived, which happens around
     * its start: from [BUDGETING_LEAD_DAYS] days before it begins through its
     * [BUDGETING_DAYS_INTO_PERIOD]th day. So the 16th–end cut-off prompts on the 14th–20th, and the
     * 1st–15th cut-off from the last two days of the previous month through the 5th.
     */
    fun isBudgetingOpen(date: LocalDate): Boolean {
        val opens = startDate.minus(BUDGETING_LEAD_DAYS, DateTimeUnit.DAY)
        val closes = startDate.plus(BUDGETING_DAYS_INTO_PERIOD - 1, DateTimeUnit.DAY)
        return date >= opens && date <= closes
    }

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

        /** Days before a cut-off starts that its budgeting window opens (salary can land early). */
        const val BUDGETING_LEAD_DAYS = 2

        /** The budgeting window stays open through this day of the cut-off (5 → the 5th / the 20th). */
        const val BUDGETING_DAYS_INTO_PERIOD = 5

        /**
         * The cut-off whose budgeting window contains [today]: the current one early on, or the
         * upcoming one in the last days before it starts. Null in between — no prompting then.
         */
        fun budgetingNow(today: LocalDate = DateUtils.today()): BudgetPeriod? {
            val current = of(today)
            return listOf(current, current.next()).firstOrNull { it.isBudgetingOpen(today) }
        }

        /** The cut-off [entry] belongs to, or null when its date can't be read. */
        fun of(entry: LedgerEntry): BudgetPeriod? = DateUtils.parseLedgerDate(entry.date)?.let(::of)

        /** Parses an [id] such as "2026-09-2"; null when malformed. */
        fun fromId(id: String): BudgetPeriod? {
            val parts = id.split("-").map { it.toIntOrNull() ?: return null }
            if (parts.size != 3) return null
            return runCatching { BudgetPeriod(parts[0], parts[1], parts[2]) }.getOrNull()
        }

        /** Every cut-off of [year], oldest first: Jan 1–15 through Dec 16–31 (24 of them). */
        fun allIn(year: Int): List<BudgetPeriod> =
            (1..12).flatMap { month -> listOf(BudgetPeriod(year, month, 1), BudgetPeriod(year, month, 2)) }

        /** The [count] cut-offs ending at [last], oldest first. */
        fun recent(count: Int, last: BudgetPeriod = current()): List<BudgetPeriod> =
            generateSequence(last) { it.previous() }.take(count).toList().asReversed()

        private fun lastDayOfMonth(year: Int, month: Int): Int =
            LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    }
}
