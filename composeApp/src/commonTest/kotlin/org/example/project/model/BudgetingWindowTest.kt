package org.example.project.model

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
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.example.project.config.LedgerProfile
import org.example.project.data.config.DeviceConfigStore
import org.example.project.data.device.InMemoryDeviceStore
import org.example.project.repository.ConfigRepository
import kotlinx.datetime.LocalDate
import org.example.project.viewmodel.BudgetEvent
import org.example.project.viewmodel.BudgetViewModel

/** The app only asks for a cut-off's budget around payday — these pin the exact days. */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetingWindowTest {

    private val sepSecondHalf = BudgetPeriod(2026, 9, 2)
    private val octFirstHalf = BudgetPeriod(2026, 10, 1)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun secondCutOffPromptsFromThe14thToThe20th() {
        assertFalse(sepSecondHalf.isBudgetingOpen(LocalDate(2026, 9, 13)))
        assertTrue(sepSecondHalf.isBudgetingOpen(LocalDate(2026, 9, 14)))
        assertTrue(sepSecondHalf.isBudgetingOpen(LocalDate(2026, 9, 16)))
        assertTrue(sepSecondHalf.isBudgetingOpen(LocalDate(2026, 9, 20)))
        assertFalse(sepSecondHalf.isBudgetingOpen(LocalDate(2026, 9, 21)))
    }

    @Test
    fun firstCutOffPromptsFromTwoDaysBeforeThe1stToThe5th() {
        assertFalse(octFirstHalf.isBudgetingOpen(LocalDate(2026, 9, 28)))
        assertTrue(octFirstHalf.isBudgetingOpen(LocalDate(2026, 9, 29)))
        assertTrue(octFirstHalf.isBudgetingOpen(LocalDate(2026, 9, 30)))
        assertTrue(octFirstHalf.isBudgetingOpen(LocalDate(2026, 10, 1)))
        assertTrue(octFirstHalf.isBudgetingOpen(LocalDate(2026, 10, 5)))
        assertFalse(octFirstHalf.isBudgetingOpen(LocalDate(2026, 10, 6)))
    }

    @Test
    fun budgetingNowPicksTheUpcomingCutOffJustBeforeItStarts() {
        assertEquals(sepSecondHalf, BudgetPeriod.budgetingNow(LocalDate(2026, 9, 14)))  // still in 1–15
        assertEquals(sepSecondHalf, BudgetPeriod.budgetingNow(LocalDate(2026, 9, 18)))
        assertEquals(octFirstHalf, BudgetPeriod.budgetingNow(LocalDate(2026, 9, 30)))   // still in 16–30
        assertEquals(octFirstHalf, BudgetPeriod.budgetingNow(LocalDate(2026, 10, 3)))
        assertEquals(BudgetPeriod(2027, 1, 1), BudgetPeriod.budgetingNow(LocalDate(2026, 12, 30)))
        assertEquals(BudgetPeriod(2026, 3, 1), BudgetPeriod.budgetingNow(LocalDate(2026, 2, 27))) // 28-day month
    }

    @Test
    fun noPromptBetweenWindows() {
        assertNull(BudgetPeriod.budgetingNow(LocalDate(2026, 9, 6)))
        assertNull(BudgetPeriod.budgetingNow(LocalDate(2026, 9, 13)))
        assertNull(BudgetPeriod.budgetingNow(LocalDate(2026, 9, 21)))
        assertNull(BudgetPeriod.budgetingNow(LocalDate(2026, 9, 28)))
    }

    @Test
    fun editorOpensOnTheUpcomingCutOffDuringItsLeadDaysAndCanSwitchBack() {
        val vm = budgetViewModel(LocalDate(2026, 9, 14))
        val state = vm.uiState.value
        assertEquals(sepSecondHalf, state.period)
        assertTrue(state.isUpcoming)
        assertEquals(listOf(BudgetPeriod(2026, 9, 1), sepSecondHalf), state.selectablePeriods)

        vm.onEvent(BudgetEvent.PeriodSelected(BudgetPeriod(2026, 9, 1)))
        assertEquals(BudgetPeriod(2026, 9, 1), vm.uiState.value.period)
        assertFalse(vm.uiState.value.isUpcoming)
        assertEquals(1, vm.uiState.value.daysLeft) // the 14th → ends on the 15th
    }

    @Test
    fun editorOpensOnTheCurrentCutOffOutsideTheWindowsAndStillOffersBoth() {
        val state = budgetViewModel(LocalDate(2026, 9, 10)).uiState.value
        assertEquals(BudgetPeriod(2026, 9, 1), state.period)
        assertFalse(state.isUpcoming)
        // Both halves stay reachable: a budget can be set ahead, or corrected afterwards,
        // whatever today's date is (see BudgetViewModelTest).
        assertEquals(listOf(BudgetPeriod(2026, 9, 1), sepSecondHalf), state.selectablePeriods)
    }

    @Test
    fun editorBudgetsTheUsersOwnCategoriesAndSuggestsTheLastPlan() = runTest {
        val config = ConfigRepository(DeviceConfigStore(InMemoryDeviceStore()))
        config.addOption(OptionList.EXPENSE_CATEGORIES, "Food")
        config.addOption(OptionList.EXPENSE_CATEGORIES, "Rent")
        config.saveBudget(BudgetPeriod(2026, 9, 1), BudgetPlan(total = 9_000.0, byBucket = mapOf("Rent" to 5_000.0)))

        val state = BudgetViewModel(config, LedgerProfile.STANDARD, today = LocalDate(2026, 9, 17)).uiState.value

        assertEquals(sepSecondHalf, state.period)
        assertEquals(listOf("Food", "Rent"), state.buckets)
        assertTrue(state.isSuggestion)
        assertEquals("9000", state.totalInput)
        assertEquals(mapOf("Food" to "", "Rent" to "5000"), state.amounts)
    }

    private fun budgetViewModel(today: LocalDate) = BudgetViewModel(
        config = ConfigRepository(DeviceConfigStore(InMemoryDeviceStore())),
        profile = LedgerProfile.STANDARD,
        today = today,
    )
}
