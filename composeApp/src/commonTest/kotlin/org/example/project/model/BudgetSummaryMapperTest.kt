package org.example.project.model

import org.example.project.data.ledger.LedgerEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetSummaryMapperTest {

    private fun entry(category: String, amount: Double, month: Int) =
        LedgerEntry(description = "x", amount = amount, category = category, monthNumber = month)

    @Test
    fun rollsCategoriesIntoBucketsPerMonth() {
        val summary = BudgetSummaryMapper.build(
            entries = listOf(
                entry("Groceries", 1000.0, 3),
                entry("Food & Dining", 500.0, 3),
                entry("Rent", 9000.0, 3),
                entry("Groceries", 200.0, 4),
            ),
            budgets = mapOf("Food" to 2000.0),
            buckets = SpendingBuckets.STANDARD,
        )
        val food = summary.first { it.category == "Food" }
        assertEquals(1500.0, food.spentIn("March"))
        assertEquals(200.0, food.spentIn("April"))
        assertEquals(2000.0, food.monthlyBudget)
        assertEquals(9000.0, summary.first { it.category == "Bills" }.spentIn("March"))
    }

    @Test
    fun otherBucketOnlyAppearsWhenItHasSpend() {
        val withoutOther = BudgetSummaryMapper.build(listOf(entry("Rent", 1.0, 1)), emptyMap(), SpendingBuckets.STANDARD)
        assertFalse(withoutOther.any { it.category == SpendingBuckets.OTHER })

        val withOther = BudgetSummaryMapper.build(listOf(entry("Pets", 1.0, 1)), emptyMap(), SpendingBuckets.STANDARD)
        assertTrue(withOther.any { it.category == SpendingBuckets.OTHER })
    }

    @Test
    fun unknownMonthsAreIgnored() {
        val summary = BudgetSummaryMapper.build(listOf(entry("Rent", 50.0, 0)), emptyMap(), SpendingBuckets.STANDARD)
        assertEquals(0.0, summary.sumOf { it.totalSpent })
    }

    @Test
    fun householdBucketsMatchLegacyCategories() {
        assertEquals("Travel", SpendingBuckets.HOUSEHOLD.bucketFor("Grab"))
        assertEquals("Gifts", SpendingBuckets.HOUSEHOLD.bucketFor("balay  kab"))
        assertTrue(SpendingBuckets.HOUSEHOLD.matches("BILLS", "Bills"))
        assertEquals(SpendingBuckets.OTHER, SpendingBuckets.HOUSEHOLD.bucketFor("Unknown"))
    }
}
