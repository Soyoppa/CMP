package org.example.project.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetTest {

    @Test
    fun overallTotalWinsOverCategorySum() {
        val plan = BudgetPlan(totalMonthly = 40_000.0, byBucket = mapOf("Food" to 10_000.0, "Bills" to 5_000.0))
        assertEquals(40_000.0, plan.effectiveMonthlyTotal)
        assertEquals(15_000.0, plan.categoryTotal)
    }

    @Test
    fun categorySumIsUsedWhenNoOverallTotal() {
        val plan = BudgetPlan(byBucket = mapOf("Food" to 10_000.0, "Bills" to 5_000.0, "Travel" to 0.0))
        assertEquals(15_000.0, plan.effectiveMonthlyTotal)
        assertEquals(0.0, BudgetPlan().effectiveMonthlyTotal)
    }

    @Test
    fun remainingCountsDownAndGoesNegativeWhenOver() {
        val under = BudgetStatus(spent = 12_000.0, budget = 40_000.0)
        assertEquals(28_000.0, under.remaining)
        assertFalse(under.isOverBudget)
        assertEquals(0.3f, under.usedFraction)

        val over = BudgetStatus(spent = 42_100.0, budget = 40_000.0)
        assertEquals(-2_100.0, over.remaining)
        assertTrue(over.isOverBudget)
        assertEquals(1f, over.usedFraction)
    }

    @Test
    fun noBudgetMeansNothingToCountDown() {
        val status = BudgetStatus(spent = 500.0, budget = 0.0)
        assertFalse(status.hasBudget)
        assertFalse(status.isOverBudget)
        assertEquals(0f, status.usedFraction)
    }
}
