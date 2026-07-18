package org.example.project.model

import org.example.project.data.CategoryTransaction
import org.example.project.util.DateUtils

/**
 * Builds the Summary screen's [CategorySummary] rows from the raw 'Data Dump' ledger instead of
 * the hand-maintained 'Summary Trend' tab. Each transaction is rolled up into its display bucket
 * ([CategoryGroups.bucketFor]) and summed per month; budgets come from the cloud budget store.
 *
 * Pure and deterministic so it stays trivially testable and cheap to run off the ledger.
 */
object BudgetSummaryMapper {

    /** Full month names Jan..Dec, the fixed x-axis the chart plots. */
    private val monthNames: List<String> = (1..12).map { DateUtils.monthName(it) }

    /**
     * @param transactions expense rows from `getTransactions()` (income already excluded upstream).
     * @param budgets bucket display name -> monthly budget (from [org.example.project.repository.BudgetRepository]).
     */
    fun build(
        transactions: List<CategoryTransaction>,
        budgets: Map<String, Double>,
    ): List<CategorySummary> {
        // Seed the six canonical buckets so they always appear (even at zero) in a stable order.
        val spendByBucket = LinkedHashMap<String, MutableMap<String, Double>>()
        CategoryGroups.buckets.forEach { bucket ->
            spendByBucket[bucket] = zeroedMonths()
        }

        transactions.forEach { txn ->
            if (txn.monthNumber !in 1..12) return@forEach
            val bucket = CategoryGroups.bucketFor(txn.category)
            val month = DateUtils.monthName(txn.monthNumber)
            val months = spendByBucket.getOrPut(bucket) { zeroedMonths() }
            months[month] = (months[month] ?: 0.0) + txn.amount
        }

        return spendByBucket.entries
            // "Other" only earns a row when it actually holds spend (or a budget) — never as noise.
            .filter { (bucket, spend) ->
                bucket != CategoryGroups.OTHER ||
                    spend.values.any { it > 0.0 } ||
                    (budgets[bucket] ?: 0.0) > 0.0
            }
            .map { (bucket, spend) ->
                CategorySummary(
                    category = bucket,
                    monthlyBudget = budgets[bucket] ?: 0.0,
                    monthlySpend = spend,
                )
            }
    }

    private fun zeroedMonths(): MutableMap<String, Double> =
        monthNames.associateWithTo(LinkedHashMap()) { 0.0 }
}
