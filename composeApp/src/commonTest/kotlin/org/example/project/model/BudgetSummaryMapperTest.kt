package org.example.project.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.example.project.data.ledger.LedgerEntry

class BudgetSummaryMapperTest {

    private val firstHalf = BudgetPeriod(2026, 9, 1)
    private val secondHalf = BudgetPeriod(2026, 9, 2)
    private val periods = listOf(firstHalf, secondHalf)

    private fun entry(category: String, amount: Double, date: String) =
        LedgerEntry(description = "x", amount = amount, category = category, monthNumber = 9, date = date)

    /** Every user budgets exactly the categories they created. */
    private val userBuckets = SpendingBuckets.of(listOf("Food", "Rent"))

    @Test
    fun spendIsSplitByCutOffNotByMonth() {
        val summary = BudgetSummaryMapper.build(
            entries = listOf(
                entry("Food", 1000.0, "9/3/2026"),
                entry("food", 500.0, "9/15/2026"),
                entry("Food", 200.0, "9/16/2026"),
                entry("Rent", 9000.0, "2026-09-30"),
            ),
            periods = periods,
            plans = mapOf(secondHalf.id to BudgetPlan(byBucket = mapOf("Food" to 2000.0))),
            buckets = userBuckets,
        )
        val food = summary.first { it.category == "Food" }
        assertEquals(1500.0, food.spentIn(firstHalf.id))
        assertEquals(200.0, food.spentIn(secondHalf.id))
        assertEquals(0.0, food.budgetFor(firstHalf.id))       // no budget saved for that cut-off
        assertEquals(2000.0, food.budgetFor(secondHalf.id))
        assertEquals(9000.0, summary.first { it.category == "Rent" }.spentIn(secondHalf.id))
    }

    @Test
    fun withNoCategoriesEverythingIsOther() {
        val summary = BudgetSummaryMapper.build(
            listOf(entry("", 40.0, "9/2/2026")), periods, emptyMap(), SpendingBuckets.of(emptyList()),
        )
        assertEquals(listOf(SpendingBuckets.OTHER), summary.map { it.category })
        assertEquals(40.0, summary.single().spentIn(firstHalf.id))
    }

    @Test
    fun spendOutsideTheChartedCutOffsIsIgnored() {
        val summary = BudgetSummaryMapper.build(
            entries = listOf(entry("Rent", 50.0, "8/31/2026"), entry("Rent", 70.0, "not a date")),
            periods = periods,
            plans = emptyMap(),
            buckets = userBuckets,
        )
        assertEquals(0.0, summary.sumOf { it.totalSpent })
    }

    @Test
    fun otherBucketOnlyAppearsWhenItHasSpend() {
        val without = BudgetSummaryMapper.build(listOf(entry("Rent", 1.0, "9/1/2026")), periods, emptyMap(), userBuckets)
        assertFalse(without.any { it.category == SpendingBuckets.OTHER })

        val with = BudgetSummaryMapper.build(listOf(entry("Pets", 1.0, "9/1/2026")), periods, emptyMap(), userBuckets)
        assertTrue(with.any { it.category == SpendingBuckets.OTHER })
    }

    @Test
    fun householdBucketsMatchLegacyCategories() {
        assertEquals("Travel", SpendingBuckets.HOUSEHOLD.bucketFor("Grab"))
        assertEquals("Gifts", SpendingBuckets.HOUSEHOLD.bucketFor("balay  kab"))
        assertTrue(SpendingBuckets.HOUSEHOLD.matches("BILLS", "Bills"))
        assertEquals(SpendingBuckets.OTHER, SpendingBuckets.HOUSEHOLD.bucketFor("Unknown"))
    }
}
