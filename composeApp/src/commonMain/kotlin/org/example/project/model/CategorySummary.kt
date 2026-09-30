package org.example.project.model

/**
 * One spending bucket's row in the Summary: what was spent, and what was budgeted, in each
 * cut-off. Both maps are keyed by [BudgetPeriod.id]; periods with no data read as 0.
 */
data class CategorySummary(
    val category: String,
    val spendByPeriod: Map<String, Double>,
    val budgetByPeriod: Map<String, Double> = emptyMap(),
) {
    /** Spend across every charted cut-off. */
    val totalSpent: Double get() = spendByPeriod.values.sum()

    fun spentIn(periodId: String): Double = spendByPeriod[periodId] ?: 0.0

    /** This bucket's budget for the cut-off; 0 = none set. */
    fun budgetFor(periodId: String): Double = budgetByPeriod[periodId] ?: 0.0
}
