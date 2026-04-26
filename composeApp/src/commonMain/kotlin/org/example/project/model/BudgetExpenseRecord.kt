package org.example.project.model

/**
 * Represents one row from the "Budget vs Expense" sheet tab.
 *
 * Each row is a sub-category with a monthly budget and actual spend per month.
 *
 * @property category The sub-category name (e.g. Rent, Food, Grab).
 * @property budget The monthly budget allocated for this category.
 * @property monthlyActual Map of month name to actual amount spent.
 */
data class BudgetExpenseRecord(
    val category: String,
    val budget: Double,
    val monthlyActual: Map<String, Double>
) {
    /** Total actual spend across all months. */
    val totalActual: Double get() = monthlyActual.values.sum()

    /** Total budget across all months that have actual data (non-zero months). */
    val totalBudgeted: Double get() = monthlyActual.values.count { it > 0 } * budget

    /** Positive = over budget, Negative = under budget. */
    val variance: Double get() = totalActual - totalBudgeted
}
