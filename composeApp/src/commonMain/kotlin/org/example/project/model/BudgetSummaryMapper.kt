package org.example.project.model

import org.example.project.data.ledger.LedgerEntry

/**
 * Builds the Summary screen's [CategorySummary] rows from the raw ledger. Each expense is rolled
 * up into its display bucket ([SpendingBuckets.bucketFor]) and summed per cut-off; budgets come
 * from each cut-off's saved [BudgetPlan].
 *
 * Pure and deterministic so it stays trivially testable and cheap to run off the ledger.
 */
object BudgetSummaryMapper {

    /**
     * @param entries expense rows (income already excluded upstream).
     * @param periods the cut-offs to chart, oldest first; spend outside them is ignored.
     * @param plans saved budgets keyed by [BudgetPeriod.id].
     * @param buckets the roll-up used by the current ledger profile.
     */
    fun build(
        entries: List<LedgerEntry>,
        periods: List<BudgetPeriod>,
        plans: Map<String, BudgetPlan>,
        buckets: SpendingBuckets,
    ): List<CategorySummary> {
        val periodIds = periods.map { it.id }
        fun zeroed(): MutableMap<String, Double> = periodIds.associateWithTo(LinkedHashMap()) { 0.0 }

        // Seed the canonical buckets so they always appear (even at zero) in a stable order.
        val spendByBucket = LinkedHashMap<String, MutableMap<String, Double>>()
        buckets.names.forEach { bucket -> spendByBucket[bucket] = zeroed() }

        entries.forEach { entry ->
            val periodId = BudgetPeriod.of(entry)?.id ?: return@forEach
            if (periodId !in periodIds) return@forEach
            val spend = spendByBucket.getOrPut(buckets.bucketFor(entry.category)) { zeroed() }
            spend[periodId] = (spend[periodId] ?: 0.0) + entry.amount
        }

        return spendByBucket.entries
            .map { (bucket, spend) ->
                CategorySummary(
                    category = bucket,
                    spendByPeriod = spend,
                    budgetByPeriod = periodIds
                        .mapNotNull { id -> plans[id]?.byBucket?.get(bucket)?.takeIf { it > 0.0 }?.let { id to it } }
                        .toMap(),
                )
            }
            // "Other" only earns a row when it actually holds spend (or a budget) — never as noise.
            .filter { it.category != SpendingBuckets.OTHER || it.totalSpent > 0.0 || it.budgetByPeriod.isNotEmpty() }
    }
}
