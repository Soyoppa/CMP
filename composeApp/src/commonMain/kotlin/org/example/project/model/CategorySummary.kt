package org.example.project.model

/**
 * One category row from the 'Summary' sheet tab.
 *
 * CSV layout: Category | January | February | … (no Budget column)
 *
 * [monthlySpend] is keyed by full month name ("January" -> amount).
 * Months with no data are stored as 0.0.
 */
data class CategorySummary(
    val category: String,
    val monthlySpend: Map<String, Double>,
) {
    val months: List<String> get() = monthlySpend.keys.toList()
    val totalSpent: Double get() = monthlySpend.values.sum()
    fun spentIn(month: String): Double = monthlySpend[month] ?: 0.0
}
