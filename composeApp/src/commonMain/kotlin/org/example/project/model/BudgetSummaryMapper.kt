package org.example.project.model

import org.example.project.data.ledger.LedgerEntry
import org.example.project.util.DateUtils

/**
 * Builds the Summary screen's [CategorySummary] rows from the raw ledger. Each entry is rolled up
 * into its display bucket ([SpendingBuckets.bucketFor]) and summed per month; budgets come from
 * the user's saved budgets.
 *
 * Pure and deterministic so it stays trivially testable and cheap to run off the ledger.
 */
object BudgetSummaryMapper {

    /** Full month names Jan..Dec, the fixed x-axis the chart plots. */
    private val monthNames: List<String> = (1..12).map { DateUtils.monthName(it) }

    /**
     * @param entries expense rows (income already excluded upstream).
     * @param budgets bucket name -> monthly budget.
     * @param buckets the roll-up used by the current ledger profile.
     */
    fun build(
        entries: List<LedgerEntry>,
        budgets: Map<String, Double>,
        buckets: SpendingBuckets,
    ): List<CategorySummary> {
        // Seed the canonical buckets so they always appear (even at zero) in a stable order.
        val spendByBucket = LinkedHashMap<String, MutableMap<String, Double>>()
        buckets.names.forEach { bucket -> spendByBucket[bucket] = zeroedMonths() }

        entries.forEach { entry ->
            if (entry.monthNumber !in 1..12) return@forEach
            val bucket = buckets.bucketFor(entry.category)
            val month = DateUtils.monthName(entry.monthNumber)
            val months = spendByBucket.getOrPut(bucket) { zeroedMonths() }
            months[month] = (months[month] ?: 0.0) + entry.amount
        }

        return spendByBucket.entries
            // "Other" only earns a row when it actually holds spend (or a budget) — never as noise.
            .filter { (bucket, spend) ->
                bucket != SpendingBuckets.OTHER ||
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
