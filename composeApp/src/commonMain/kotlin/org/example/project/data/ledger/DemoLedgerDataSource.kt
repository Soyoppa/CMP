package org.example.project.data.ledger

import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetPlan
import org.example.project.model.Transaction
import org.example.project.util.UserFacingException

/**
 * Hardcoded demo ledger served to guests (anonymous sessions), so they can explore every screen
 * without ever touching real financial data. Writes are accepted and discarded.
 *
 * Entries are generated relative to today — the last [DEMO_PERIODS] cut-offs — so the demo always
 * has a "current cut-off" with spending and a budget to measure it against. That's a year of
 * cut-offs, enough to fill the Summary's 12 monthly bars.
 */
object DemoLedgerDataSource : LedgerDataSource {

    private const val DEMO_PERIODS = 24

    /** category -> (line items, typical spend per cut-off). Uses the standard category names. */
    private val demoSpending = listOf(
        DemoCategory("Bills & Utilities", listOf("Meralco", "Maynilad", "PLDT Fibr"), 9_500.0),
        DemoCategory("Groceries", listOf("SM Supermarket", "Wet Market"), 7_200.0),
        DemoCategory("Food & Dining", listOf("Dining out", "Coffee"), 3_100.0),
        DemoCategory("Transportation", listOf("Grab rides", "Fuel"), 2_800.0),
        DemoCategory("Shopping", listOf("Lazada order", "Hardware store"), 4_600.0),
        DemoCategory("Gifts & Donations", listOf("Sunday offering", "Birthday gift"), 1_900.0),
    )

    /** Payment methods cycled through the demo ledger so the mode filter has options. */
    private val demoModes = listOf("Bank Transfer", "GCash", "Maya", "Cash", "Credit Card")

    /** Per-cut-off demo budgets, matching [org.example.project.model.SpendingBuckets.STANDARD]. */
    private val demoBudget = BudgetPlan(
        total = 32_000.0,
        byBucket = mapOf(
            "Food" to 11_000.0, "Bills" to 10_000.0, "Transport" to 3_000.0,
            "Shopping" to 4_000.0, "Giving" to 2_000.0,
        ),
    )

    /** Swings each cut-off's spend around its typical value so the chart isn't flat. */
    private val swing = listOf(0.92, 1.08, 0.85, 1.21, 1.0, 0.78, 1.14, 0.96, 1.3, 0.88, 1.05, 0.6)

    private fun periods(): List<BudgetPeriod> = BudgetPeriod.recent(DEMO_PERIODS)

    /** The demo budget for every demo cut-off, keyed by [BudgetPeriod.id]. */
    fun budgetPlans(): Map<String, BudgetPlan> = periods().associate { it.id to demoBudget }

    override suspend fun getEntries(): List<LedgerEntry> {
        val periods = periods()
        return periods.flatMapIndexed { periodIndex, period ->
            val factor = swing[periodIndex % swing.size]
            val month = period.month.toString().padStart(2, '0')
            fun date(offset: Int): String {
                val day = (period.startDay + offset).coerceAtMost(period.endDay).toString().padStart(2, '0')
                return "${period.year}-$month-$day"
            }
            val expenses = demoSpending.flatMapIndexed { categoryIndex, category ->
                // Split each category's spend across its line items, biggest first.
                val weights = when (category.items.size) {
                    1 -> listOf(1.0)
                    2 -> listOf(0.62, 0.38)
                    else -> listOf(0.5, 0.32, 0.18)
                }
                category.items.mapIndexed { itemIndex, item ->
                    LedgerEntry(
                        id = "demo-${period.id}-$categoryIndex-$itemIndex",
                        description = item,
                        amount = category.perCutOff * factor * weights[itemIndex],
                        category = category.name,
                        monthNumber = period.month,
                        date = date(offset = categoryIndex * 2 + itemIndex),
                        modeOfPayment = demoModes[(periodIndex + categoryIndex + itemIndex) % demoModes.size],
                        // Everything but the two latest cut-offs is settled, so Paid & Unpaid shows both states.
                        isPaid = periodIndex < periods.size - 2,
                    )
                }.filter { it.amount >= 1.0 }
            }
            val salary = LedgerEntry(
                id = "demo-${period.id}-salary",
                description = "Salary",
                amount = 27_500.0,
                category = "Salary",
                monthNumber = period.month,
                date = date(offset = 0),
                modeOfPayment = "Bank Transfer",
                isPaid = true,
                isIncome = true,
            )
            expenses + salary
        }
    }

    /** Demo data is read-only: report success so the flow completes, but nothing is stored. */
    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        AddTransactionResult(success = true)

    override suspend fun deleteEntry(entry: LedgerEntry) {
        throw UserFacingException("Demo data can't be deleted. Create an account to keep your own ledger.")
    }

    private data class DemoCategory(val name: String, val items: List<String>, val perCutOff: Double)
}
