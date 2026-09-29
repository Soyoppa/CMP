package org.example.project.model

/**
 * The user's monthly budget: an optional overall [totalMonthly] plus optional per-bucket amounts.
 *
 * The overall figure wins when set; otherwise the per-category budgets add up to the total, so
 * people who only budget by category keep today's behaviour.
 */
data class BudgetPlan(
    /** Explicit overall monthly budget; 0 = not set. */
    val totalMonthly: Double = 0.0,
    /** bucket name -> monthly budget (absent / 0 = none). */
    val byBucket: Map<String, Double> = emptyMap(),
) {
    val categoryTotal: Double get() = byBucket.values.filter { it > 0.0 }.sum()

    /** The monthly budget the app measures spending against; 0 when nothing is set. */
    val effectiveMonthlyTotal: Double get() = if (totalMonthly > 0.0) totalMonthly else categoryTotal
}

/** Spending measured against a budget for one period (a month, or one category in a month). */
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
