package org.example.project.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.example.project.config.LedgerProfile
import org.example.project.data.config.DeviceConfigStore
import org.example.project.data.device.InMemoryDeviceStore
import org.example.project.data.sheets.SheetDataSourceFactory
import org.example.project.model.BudgetCycle
import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetPlan
import org.example.project.model.OptionList

class ConfigRepositoryTest {

    private val deviceStore = InMemoryDeviceStore()
    private val store = DeviceConfigStore(deviceStore)
    private val repository = ConfigRepository(store)

    private val categories = OptionList.EXPENSE_CATEGORIES

    @Test
    fun everythingStartsEmpty() = runTest {
        repository.ensureLoaded()
        assertEquals(emptyList(), repository.config.expenseCategories)
        assertEquals(emptyList(), repository.config.incomeCategories)
        assertEquals(emptyList(), repository.config.paymentModes)
        assertTrue(repository.config.budgets.isEmpty())
    }

    @Test
    fun addTrimsSavesAndRejectsDuplicates() = runTest {
        assertEquals("Wet Market", repository.addOption(categories, "  Wet   Market ").getOrThrow())
        assertEquals(listOf("Wet Market"), store.load().expenseCategories) // persisted, not just in memory

        val duplicate = repository.addOption(categories, "wet market")
        assertEquals("\"wet market\" is already in the list.", duplicate.exceptionOrNull()?.message)
        assertTrue(repository.addOption(categories, "   ").isFailure)
        assertEquals(listOf("Wet Market"), repository.config.expenseCategories)
    }

    @Test
    fun renameKeepsPositionAndMovesBudgets() = runTest {
        repository.addOption(categories, "Food")
        repository.addOption(categories, "Rent")
        val period = BudgetPeriod(2026, 9, 2)
        repository.saveBudget(period, BudgetPlan(total = 10_000.0, byBucket = mapOf("Food" to 4_000.0)))

        repository.renameOption(categories, "Food", "Meals").getOrThrow()

        assertEquals(listOf("Meals", "Rent"), repository.config.expenseCategories)
        assertEquals(mapOf("Meals" to 4_000.0), store.load().budgets.getValue(period.id).byBucket)
        // Changing only the letter case is a rename, not a duplicate.
        repository.renameOption(categories, "Meals", "MEALS").getOrThrow()
        assertTrue(repository.renameOption(categories, "MEALS", "rent").isFailure)
    }

    @Test
    fun deleteRemovesTheOption() = runTest {
        repository.addOption(OptionList.PAYMENT_MODES, "Cash")
        repository.addOption(OptionList.PAYMENT_MODES, "GCash")
        repository.deleteOption(OptionList.PAYMENT_MODES, "Cash").getOrThrow()
        assertEquals(listOf("GCash"), store.load().paymentModes)
    }

    @Test
    fun theBudgetingCycleIsSavedAndKeepsBothSetsOfBudgets() = runTest {
        val cutOff = BudgetPeriod(2026, 10, 1)
        val month = BudgetPeriod(2026, 10, BudgetPeriod.WHOLE_MONTH)
        repository.saveBudget(cutOff, BudgetPlan(total = 5_000.0)).getOrThrow()

        repository.setCycle(BudgetCycle.MONTHLY).getOrThrow()
        assertEquals(BudgetCycle.MONTHLY, repository.config.cycle)
        repository.saveBudget(month, BudgetPlan(total = 12_000.0)).getOrThrow()

        // Both survive: their ids differ, so switching back shows the old figures again.
        val reopened = ConfigRepository(DeviceConfigStore(deviceStore))
        reopened.ensureLoaded()
        assertEquals(BudgetCycle.MONTHLY, reopened.config.cycle)
        assertEquals(5_000.0, reopened.config.planFor(cutOff)?.total)
        assertEquals(12_000.0, reopened.config.planFor(month)?.total)

        reopened.setCycle(BudgetCycle.CUT_OFF).getOrThrow()
        val switchedBack = ConfigRepository(DeviceConfigStore(deviceStore))
        switchedBack.ensureLoaded()
        assertEquals(BudgetCycle.CUT_OFF, switchedBack.config.cycle)
    }

    @Test
    fun aSuggestionNeverCrossesCycles() = runTest {
        repository.saveBudget(BudgetPeriod(2026, 9, 2), BudgetPlan(total = 7_000.0)).getOrThrow()
        val config = repository.config
        // An earlier cut-off's plan suggests the next cut-off…
        assertEquals(7_000.0, config.suggestedPlan(BudgetPeriod(2026, 10, 1))?.total)
        // …but says nothing about a monthly budget.
        assertNull(config.suggestedPlan(BudgetPeriod(2026, 10, BudgetPeriod.WHOLE_MONTH)))
    }

    @Test
    fun aFreshRepositorySeesWhatWasSaved() = runTest {
        repository.addOption(OptionList.INCOME_CATEGORIES, "Salary")
        val reopened = ConfigRepository(DeviceConfigStore(deviceStore))
        reopened.ensureLoaded()
        assertEquals(listOf("Salary"), reopened.config.incomeCategories)
    }

    @Test
    fun householdSheetStartsFromTheSheetsOwnLists() = runTest {
        val sheet = ConfigRepository(store, LedgerProfile.forSheetSchema(SheetDataSourceFactory.SCHEMA_TRACKER_1))
        sheet.ensureLoaded()
        assertTrue("Grocery" in sheet.config.expenseCategories)
        assertTrue("Cash" in sheet.config.paymentModes)
    }
}
