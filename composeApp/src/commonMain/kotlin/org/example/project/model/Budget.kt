package org.example.project.model

/**
 * The budget for one cut-off ([BudgetPeriod]): an optional overall [total] plus optional
 * per-bucket amounts.
 *
 * The overall figure wins when set; otherwise the per-category budgets add up to the total, so
 * people who only budget by category are still covered.
 */
data class BudgetPlan(
    /** Explicit overall budget for the cut-off; 0 = not set. */
    val total: Double = 0.0,
    /** bucket name -> budget for the cut-off (absent / 0 = none). */
    val byBucket: Map<String, Double> = emptyMap(),
) {
    val categoryTotal: Double get() = byBucket.values.filter { it > 0.0 }.sum()

    /** The budget spending is measured against; 0 when nothing is set. */
    val effectiveTotal: Double get() = if (total > 0.0) total else categoryTotal

    /** True when neither an overall nor any category budget is set. */
    val isEmpty: Boolean get() = effectiveTotal <= 0.0

    /** Every amount multiplied by [factor] — e.g. 0.5 to turn a monthly plan into a cut-off one. */
    fun scaled(factor: Double): BudgetPlan =
        BudgetPlan(total = total * factor, byBucket = byBucket.mapValues { it.value * factor })
}

/** Spending measured against a budget for one period (a cut-off, or one category in it). */
data class BudgetStatus(
    val spent: Double,
    /** 0 = no budget set. */
    val budget: Double,
) {
    val hasBudget: Boolean get() = budget > 0.0

    /** Budget left; negative when overspent. */
    val remaining: Double get() = budget - spent

    val isOverBudget: Boolean get() = hasBudget && spent > budget

    /** Share of the budget used, clamped to 0..1 for progress bars. */
    val usedFraction: Float get() = if (hasBudget) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f
}
