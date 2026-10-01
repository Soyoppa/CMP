package org.example.project.viewmodel

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.number
import org.example.project.config.LedgerProfile
import org.example.project.data.config.DeviceConfigStore
import org.example.project.data.device.InMemoryDeviceStore
import org.example.project.data.ledger.AddTransactionResult
import org.example.project.data.ledger.DeviceLedgerDataSource
import org.example.project.data.ledger.LabelField
import org.example.project.data.ledger.LedgerDataSource
import org.example.project.data.ledger.LedgerEntry
import org.example.project.data.ledger.LedgerYear
import org.example.project.model.BudgetCycle
import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetPlan
import org.example.project.model.OptionList
import org.example.project.model.Transaction
import org.example.project.repository.ConfigRepository
import org.example.project.repository.LedgerRepository
import org.example.project.util.DateUtils
import org.example.project.util.UserFacingException

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {

    private object FailingLedger : LedgerDataSource {
        override suspend fun readYear(year: Int): LedgerYear =
            throw UserFacingException("The shared sheet returned an unexpected response. Please try again.")
        override suspend fun addTransaction(transaction: Transaction) = AddTransactionResult(false)
        override suspend fun deleteEntry(entry: LedgerEntry) = Unit
        override suspend fun relabel(field: LabelField, from: String, to: String) = 0
    }

    private val device = InMemoryDeviceStore()
    private val config = ConfigRepository(DeviceConfigStore(device))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Regression: a failing ledger read used to crash the app (async child failed the launch). */
    @Test
    fun ledgerFailureBecomesErrorStateInsteadOfCrashing() = runTest {
        val vm = SummaryViewModel(LedgerRepository(FailingLedger), config, LedgerProfile.STANDARD)

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(
            "The shared sheet returned an unexpected response. Please try again.",
            vm.uiState.value.error,
        )
    }

    @Test
    fun followsNewTransactionsAndBudgetsWithoutARefresh() = runTest {
        val ledger = LedgerRepository(DeviceLedgerDataSource(device))
        config.addOption(OptionList.EXPENSE_CATEGORIES, "Food")
        val vm = SummaryViewModel(ledger, config, LedgerProfile.STANDARD)
        assertEquals(0.0, vm.uiState.value.thisPeriod?.spent)

        ledger.addTransaction(
            Transaction(date = DateUtils.getCurrentDateFormatted(), description = "Lunch", outflow = 250.0, category = "Food")
        )
        assertEquals(250.0, vm.uiState.value.thisPeriod?.spent)
        assertEquals(listOf("Food"), vm.uiState.value.categories.map { it.category })

        config.saveBudget(BudgetPeriod.current(), BudgetPlan(total = 1_000.0))
        assertEquals(750.0, vm.uiState.value.thisPeriod?.remaining)
    }

    @Test
    fun monthlyCycleChartsOnePeriodPerMonth() = runTest {
        val ledger = LedgerRepository(DeviceLedgerDataSource(device))
        config.setCycle(BudgetCycle.MONTHLY).getOrThrow()
        config.addOption(OptionList.EXPENSE_CATEGORIES, "Food")
        val today = DateUtils.today()
        val vm = SummaryViewModel(ledger, config, LedgerProfile.STANDARD)

        assertEquals(12, vm.uiState.value.periods.size)
        assertEquals(BudgetCycle.MONTHLY, vm.uiState.value.cycle)
        assertEquals(BudgetPeriod.of(today, BudgetCycle.MONTHLY), vm.uiState.value.currentPeriod)

        // Spending from either half of the month lands in the same period.
        ledger.addTransaction(
            Transaction(date = "${today.month.number}/2/${today.year}", description = "Early", outflow = 100.0, category = "Food")
        )
        ledger.addTransaction(
            Transaction(date = "${today.month.number}/20/${today.year}", description = "Late", outflow = 400.0, category = "Food")
        )
        config.saveBudget(BudgetPeriod.current(BudgetCycle.MONTHLY), BudgetPlan(total = 2_000.0))

        assertEquals(500.0, vm.uiState.value.thisPeriod?.spent)
        assertEquals(1_500.0, vm.uiState.value.thisPeriod?.remaining)
    }
}
