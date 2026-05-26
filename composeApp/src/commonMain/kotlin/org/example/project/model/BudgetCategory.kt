package org.example.project.model

/**
 * One category row from the 'Budget vs Expense' sheet tab.
 *
 * CSV layout (one row per category):
 *   Category, Budget, January, February, … December
 *
 * [monthlyBudget] is the fixed monthly allocation; [monthlyActual] holds the
 * actual spend keyed by full month name ("January" -> 9580.0). Months with no
 * data are stored as 0.0.
 *
 * @property category     The category name (e.g. Food, Rent, Grab).
 * @property monthlyBudget The budget allocated per month.
 * @property monthlyActual Actual spend per month, keyed by full month name.
 */
data class BudgetCategory(
    val category: String,
    val monthlyBudget: Double,
    val monthlyActual: Map<String, Double>,
) {
    /** Total actual spend across all months. */
    val totalSpent: Double get() = monthlyActual.values.sum()

    /** Actual spend for a given month name (0.0 if absent). */
    fun spentIn(month: String): Double = monthlyActual[month] ?: 0.0

    /**
     * Budget left for [month] = monthly budget − actual spent that month.
     * Negative means the category is over budget for that month.
     */
    fun remainingIn(month: String): Double = monthlyBudget - spentIn(month)
}
