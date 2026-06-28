package org.example.project.data

import org.example.project.model.CategorySummary
import org.example.project.model.Transaction

/**
 * Hardcoded demo data — used when DemoMode is enabled so recordings and
 * screenshots never expose real financial information.
 *
 * Data is inspired by the tracker_1 sample spreadsheet layout.
 */
object DemoRepository : SheetRepository {

    private val months = listOf("January", "February", "March", "April", "May", "June")

    private val summaryData = mapOf(
        "BILLS"  to listOf(19211.0, 17620.0, 18175.0, 54407.0, 22471.0, 32314.0),
        "FOOD"   to listOf(10000.0, 10000.0, 10000.0, 29546.0, 24905.0,     0.0),
        "THING"  to listOf(10486.0, 48937.0, 49627.0, 26244.0, 27421.0,  6723.0),
        "TRAVEL" to listOf( 5693.0, 18459.0, 10079.0,  6410.0, 11765.0,     0.0),
        "CHURCH" to listOf(   400.0,   725.0,  3100.0,  3400.0,  1400.0,   400.0),
        "GIFTS"  to listOf( 3368.0,  5383.0,  9339.0, 17468.0, 13669.0,  3888.0),
    )

    private val budgetPerCategory = mapOf(
        "BILLS"  to 25000.0,
        "FOOD"   to 30000.0,
        "THING"  to 35000.0,
        "TRAVEL" to 15000.0,
        "CHURCH" to  2000.0,
        "GIFTS"  to 10000.0,
    )

    override suspend fun getSummary(): List<CategorySummary> =
        summaryData.map { (category, values) ->
            CategorySummary(
                category = category,
                monthlyBudget = budgetPerCategory[category] ?: 0.0,
                monthlySpend = months.zip(values).toMap(),
            )
        }

    // Plausible line-item names per category, used to fan each monthly total into a few
    // individual transactions so the per-category drill-down has believable data.
    private val demoLineItems = mapOf(
        "BILLS"  to listOf("Meralco", "Maynilad", "PLDT Fibr"),
        "FOOD"   to listOf("SM Supermarket", "Wet Market", "Dining out"),
        "THING"  to listOf("Lazada order", "Hardware store", "Gadget"),
        "TRAVEL" to listOf("Grab rides", "Domestic flight", "Hotel"),
        "CHURCH" to listOf("Sunday offering", "Tithe"),
        "GIFTS"  to listOf("Birthday gift", "Anniversary"),
    )

    /** Descending split weights so each fanned-out total reads high→low. */
    private fun splitWeights(n: Int): List<Double> = when (n) {
        1 -> listOf(1.0)
        2 -> listOf(0.62, 0.38)
        else -> listOf(0.5, 0.32, 0.18)
    }

    override suspend fun getTransactions(): List<CategoryTransaction> =
        summaryData.flatMap { (category, values) ->
            val names = demoLineItems[category] ?: listOf(category)
            values.flatMapIndexed { monthIndex, total ->
                if (total <= 0.0) return@flatMapIndexed emptyList()
                val weights = splitWeights(names.size)
                names.mapIndexedNotNull { i, name ->
                    val amount = (total * weights[i])
                    if (amount < 1.0) null
                    else CategoryTransaction(
                        description = name,
                        amount = amount,
                        category = category,
                        monthNumber = monthIndex + 1,
                    )
                }
            }
        }

    private val allRecentTransactions = listOf(
        RecentTransaction("Monthly Salary",        55000.0, isInflow = true),
        RecentTransaction("SM Supermarket",         3250.0, isInflow = false),
        RecentTransaction("Meralco Bill",           4500.0, isInflow = false),
        RecentTransaction("Jollibee Family Meal",    850.0, isInflow = false),
        RecentTransaction("Grab — Airport",         1200.0, isInflow = false),
        RecentTransaction("Church Offering",         400.0, isInflow = false),
        RecentTransaction("Birthday Gift",          2500.0, isInflow = false),
    )

    override suspend fun getRecentTransactions(limit: Int): List<RecentTransaction> =
        allRecentTransactions.take(limit)

    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        AddTransactionResult(success = true, responseBody = "Demo mode — transaction not saved.")

    override suspend fun testWriteConnection(): String = "Demo mode — write disabled."
}
