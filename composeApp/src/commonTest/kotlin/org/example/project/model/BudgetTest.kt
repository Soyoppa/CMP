package org.example.project.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetTest {

    @Test
    fun overallTotalWinsOverCategorySum() {
        val plan = BudgetPlan(total = 20_000.0, byBucket = mapOf("Food" to 5_000.0, "Bills" to 2_500.0))
        assertEquals(20_000.0, plan.effectiveTotal)
        assertEquals(7_500.0, plan.categoryTotal)
    }

    @Test
    fun categorySumIsUsedWhenNoOverallTotal() {
        val plan = BudgetPlan(byBucket = mapOf("Food" to 5_000.0, "Bills" to 2_500.0, "Travel" to 0.0))
        assertEquals(7_500.0, plan.effectiveTotal)
        assertTrue(BudgetPlan().isEmpty)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun halvingAMonthlyPlanGivesACutOffPlan() {
        val monthly = BudgetPlan(total = 40_000.0, byBucket = mapOf("Food" to 10_000.0))
        assertEquals(BudgetPlan(total = 20_000.0, byBucket = mapOf("Food" to 5_000.0)), monthly.scaled(0.5))
    }

    @Test
    fun remainingCountsDownAndGoesNegativeWhenOver() {
        val under = BudgetStatus(spent = 6_000.0, budget = 20_000.0)
        assertEquals(14_000.0, under.remaining)
        assertFalse(under.isOverBudget)
        assertEquals(0.3f, under.usedFraction)

        val over = BudgetStatus(spent = 21_050.0, budget = 20_000.0)
        assertEquals(-1_050.0, over.remaining)
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
