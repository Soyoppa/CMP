package org.example.project.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import org.example.project.data.ledger.LedgerEntry
import org.example.project.util.DateUtils

/**
 * How often the user budgets, chosen in Settings:
 *  - [CUT_OFF]: twice a month (1st–15th and 16th–end), matching twice-a-month paydays.
 *  - [MONTHLY]: once per calendar month.
 *
 * The choice decides which [BudgetPeriod]s exist, so budgets of the two cycles never mix: their
 * ids differ ("2026-10-1" vs "2026-10"), and switching back leaves the old ones untouched.
 */
enum class BudgetCycle(val noun: String) {
    CUT_OFF("cut-off"),
    MONTHLY("month"),
}

/**
 * One budgeting period: a whole calendar month ([half] = [WHOLE_MONTH]) or one of its cut-offs —
 * the 1st–15th ([half] = 1) or the 16th–end of month ([half] = 2). Budgets are set, and spending
 * is totalled, per period.
 */
data class BudgetPeriod(val year: Int, val month: Int, val half: Int) : Comparable<BudgetPeriod> {

    init {
        require(month in 1..12) { "month must be 1..12" }
        require(half in WHOLE_MONTH..2) { "half must be 0 (whole month), 1 or 2" }
    }

    val cycle: BudgetCycle get() = if (half == WHOLE_MONTH) BudgetCycle.MONTHLY else BudgetCycle.CUT_OFF

    /** Stable key — also the Firestore document id: "2026-09-2" per cut-off, "2026-09" per month. */
    val id: String get() = if (half == WHOLE_MONTH) monthKey else "$monthKey-$half"

    val startDay: Int get() = if (half == 2) 16 else 1

    val endDay: Int get() = if (half == 1) 15 else lastDayOfMonth(year, month)

    /** "Sep" */
    val monthLabel: String get() = DateUtils.monthName(month).take(3)

    /** Calendar month this period belongs to, e.g. "2026-09" — the chart groups by this. */
    val monthKey: String get() = "$year-${month.toString().padStart(2, '0')}"

    /** "16–30" */
    val rangeLabel: String get() = "$startDay–$endDay"

    /** "Sep 16–30", or just "Sep" for a whole month. */
    val label: String get() = if (half == WHOLE_MONTH) monthLabel else "$monthLabel $rangeLabel"

    val startDate: LocalDate get() = LocalDate(year, month, startDay)

    val endDate: LocalDate get() = LocalDate(year, month, endDay)

    /**
     * Whether [date] falls in this period's budgeting window — the days the app asks for its
     * budget. A budget is set once the salary for the period has arrived, which happens around
     * its start: from [BUDGETING_LEAD_DAYS] days before it begins through its
     * [BUDGETING_DAYS_INTO_PERIOD]th day. So the 16th–end cut-off prompts on the 14th–20th, and a
     * month (or the 1st–15th cut-off) from the last two days of the previous month through the 5th.
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
        month == 1 -> BudgetPeriod(year - 1, 12, if (half == WHOLE_MONTH) WHOLE_MONTH else 2)
        else -> BudgetPeriod(year, month - 1, if (half == WHOLE_MONTH) WHOLE_MONTH else 2)
    }

    fun next(): BudgetPeriod = when {
        half == 1 -> copy(half = 2)
        month == 12 -> BudgetPeriod(year + 1, 1, half.firstOfMonth())
        else -> BudgetPeriod(year, month + 1, half.firstOfMonth())
    }

    /** The periods of this period's month, oldest first — what the cut-off chips offer. */
    fun periodsInMonth(): List<BudgetPeriod> =
        if (half == WHOLE_MONTH) listOf(this) else listOf(copy(half = 1), copy(half = 2))

    /** This period in [cycle]: the same calendar month, re-cut. */
    fun inCycle(cycle: BudgetCycle): BudgetPeriod = when {
        cycle == this.cycle -> this
        cycle == BudgetCycle.MONTHLY -> copy(half = WHOLE_MONTH)
        else -> copy(half = 1)
    }

    private fun Int.firstOfMonth(): Int = if (this == WHOLE_MONTH) WHOLE_MONTH else 1

    override fun compareTo(other: BudgetPeriod): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.half })

    companion object {
        /** [half] value for a whole calendar month. */
        const val WHOLE_MONTH = 0

        fun of(date: LocalDate, cycle: BudgetCycle = BudgetCycle.CUT_OFF): BudgetPeriod = BudgetPeriod(
            year = date.year,
            month = date.month.number,
            half = if (cycle == BudgetCycle.MONTHLY) WHOLE_MONTH else if (date.day <= 15) 1 else 2,
        )

        /** The period that today falls in (device time zone). */
        fun current(cycle: BudgetCycle = BudgetCycle.CUT_OFF): BudgetPeriod = of(DateUtils.today(), cycle)

        /** Days before a period starts that its budgeting window opens (salary can land early). */
        const val BUDGETING_LEAD_DAYS = 2

        /** The budgeting window stays open through this day of the period (5 → the 5th / the 20th). */
        const val BUDGETING_DAYS_INTO_PERIOD = 5

        /**
         * The period whose budgeting window contains [today]: the current one early on, or the
         * upcoming one in the last days before it starts. Null in between — no prompting then.
         */
        fun budgetingNow(
            today: LocalDate = DateUtils.today(),
            cycle: BudgetCycle = BudgetCycle.CUT_OFF,
        ): BudgetPeriod? {
            val current = of(today, cycle)
            return listOf(current, current.next()).firstOrNull { it.isBudgetingOpen(today) }
        }

        /** The period [entry] belongs to, or null when its date can't be read. */
        fun of(entry: LedgerEntry, cycle: BudgetCycle = BudgetCycle.CUT_OFF): BudgetPeriod? =
            DateUtils.parseLedgerDate(entry.date)?.let { of(it, cycle) }

        /** Parses an id such as "2026-09-2" (cut-off) or "2026-09" (month); null when malformed. */
        fun fromId(id: String): BudgetPeriod? {
            val parts = id.split("-").map { it.toIntOrNull() ?: return null }
            return when (parts.size) {
                2 -> runCatching { BudgetPeriod(parts[0], parts[1], WHOLE_MONTH) }.getOrNull()
                3 -> runCatching { BudgetPeriod(parts[0], parts[1], parts[2]) }.getOrNull()
                else -> null
            }
        }

        /** Every period of [year], oldest first: 12 months, or 24 cut-offs. */
        fun allIn(year: Int, cycle: BudgetCycle = BudgetCycle.CUT_OFF): List<BudgetPeriod> =
            (1..12).flatMap { month ->
                if (cycle == BudgetCycle.MONTHLY) listOf(BudgetPeriod(year, month, WHOLE_MONTH))
                else listOf(BudgetPeriod(year, month, 1), BudgetPeriod(year, month, 2))
            }

        private fun lastDayOfMonth(year: Int, month: Int): Int =
            LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    }
}
