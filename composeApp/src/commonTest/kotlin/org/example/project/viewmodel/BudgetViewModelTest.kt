package org.example.project.viewmodel

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.example.project.config.LedgerProfile
import org.example.project.data.config.DeviceConfigStore
import org.example.project.data.device.InMemoryDeviceStore
import org.example.project.model.BudgetCycle
import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetPlan
import org.example.project.repository.ConfigRepository

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    private val device = InMemoryDeviceStore()
    private val config = ConfigRepository(DeviceConfigStore(device))

    /** The 5th: inside the Oct 1–15 cut-off, well before the 16th–31st starts. */
    private val earlyOctober = LocalDate(2026, 10, 5)
    private val firstHalf = BudgetPeriod(2026, 10, 1)
    private val secondHalf = BudgetPeriod(2026, 10, 2)

    private fun viewModel(period: BudgetPeriod? = null, today: LocalDate = earlyOctober) =
        BudgetViewModel(config = config, profile = LedgerProfile.STANDARD, initialPeriod = period, today = today)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Regression: setting a budget for the cut-off showing on the Summary used to edit (and save
     * to) whichever cut-off today fell in, so the upcoming one never got a budget.
     */
    @Test
    fun savesToThePeriodItWasOpenedOnEvenWhenItHasntStarted() = runTest {
        val vm = viewModel(period = secondHalf)
        assertEquals(secondHalf, vm.uiState.value.period)
        assertTrue(vm.uiState.value.isUpcoming)

        vm.onEvent(BudgetEvent.TotalChanged("8000"))
        vm.onEvent(BudgetEvent.SaveClicked)

        assertTrue(vm.uiState.value.saved)
        assertEquals(8_000.0, config.config.planFor(secondHalf)?.total)
        assertNull(config.config.planFor(firstHalf))
    }

    @Test
    fun eitherCutOffOfTheMonthCanBeSwitchedToInTheSheet() = runTest {
        config.saveBudget(firstHalf, BudgetPlan(total = 5_000.0))
        val vm = viewModel(period = secondHalf)
        assertEquals(listOf(firstHalf, secondHalf), vm.uiState.value.selectablePeriods)

        vm.onEvent(BudgetEvent.PeriodSelected(firstHalf))

        assertEquals(firstHalf, vm.uiState.value.period)
        assertEquals("5000", vm.uiState.value.totalInput)   // its saved budget, not a suggestion
        assertFalse(vm.uiState.value.isSuggestion)
        assertFalse(vm.uiState.value.isUpcoming)
        assertEquals(10, vm.uiState.value.daysLeft)         // the 5th → ends on the 15th
    }

    @Test
    fun withNoPeriodGivenItOpensOnTheOneBeingAskedFor() = runTest {
        // The 14th is in the first cut-off, but the 16th–31st's budgeting window is already open.
        val vm = viewModel(today = LocalDate(2026, 10, 14))
        assertEquals(secondHalf, vm.uiState.value.period)
    }

    @Test
    fun monthlyCycleEditsTheWholeMonth() = runTest {
        config.setCycle(BudgetCycle.MONTHLY).getOrThrow()
        // A caller handing over a cut-off (e.g. a stale selection) still edits the month.
        val vm = viewModel(period = secondHalf)

        val month = BudgetPeriod(2026, 10, BudgetPeriod.WHOLE_MONTH)
        assertEquals(month, vm.uiState.value.period)
        assertEquals(listOf(month), vm.uiState.value.selectablePeriods)
        assertEquals("month", vm.uiState.value.periodNoun)

        vm.onEvent(BudgetEvent.TotalChanged("30000"))
        vm.onEvent(BudgetEvent.SaveClicked)
        assertEquals(30_000.0, config.config.planFor(month)?.total)
    }

    @Test
    fun aPastPeriodCanStillBeCorrected() = runTest {
        val september = BudgetPeriod(2026, 9, 2)
        val vm = viewModel(period = september)
        assertTrue(vm.uiState.value.hasEnded)
        assertFalse(vm.uiState.value.isUpcoming)

        vm.onEvent(BudgetEvent.TotalChanged("1200"))
        vm.onEvent(BudgetEvent.SaveClicked)
        assertEquals(1_200.0, config.config.planFor(september)?.total)
    }
}
