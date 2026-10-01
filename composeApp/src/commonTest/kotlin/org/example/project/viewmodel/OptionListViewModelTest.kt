package org.example.project.viewmodel

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.project.data.config.DeviceConfigStore
import org.example.project.data.device.InMemoryDeviceStore
import org.example.project.data.ledger.DeviceLedgerDataSource
import org.example.project.domain.config.RenameOptionUseCase
import org.example.project.model.OptionList
import org.example.project.model.Transaction
import org.example.project.repository.ConfigRepository
import org.example.project.repository.LedgerRepository

@OptIn(ExperimentalCoroutinesApi::class)
class OptionListViewModelTest {

    private val device = InMemoryDeviceStore()
    private val config = ConfigRepository(DeviceConfigStore(device))
    private val ledger = LedgerRepository(DeviceLedgerDataSource(device))

    private fun viewModel() = OptionListViewModel(
        tabs = listOf(OptionList.EXPENSE_CATEGORIES, OptionList.INCOME_CATEGORIES),
        config = config,
        renameOption = RenameOptionUseCase(config, ledger),
        renamesPastTransactions = true,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun addRenameDeleteRoundTrip() = runTest {
        val vm = viewModel()
        assertEquals(emptyList(), vm.uiState.value.items)

        vm.onEvent(OptionListEvent.DraftChanged("Food"))
        vm.onEvent(OptionListEvent.AddClicked)
        assertEquals(listOf("Food"), vm.uiState.value.items)
        assertEquals("", vm.uiState.value.draft)

        ledger.addTransaction(Transaction(date = "9/2/2026", description = "Lunch", outflow = 250.0, category = "Food"))
        vm.onEvent(OptionListEvent.RenameClicked("Food"))
        vm.onEvent(OptionListEvent.RenameDraftChanged("Meals"))
        vm.onEvent(OptionListEvent.RenameConfirmed)
        assertEquals(listOf("Meals"), vm.uiState.value.items)
        assertNull(vm.uiState.value.renaming)
        // Past transactions follow the rename.
        assertEquals("Meals", ledger.readYear(2026).entries.single().category)

        vm.onEvent(OptionListEvent.DeleteClicked("Meals"))
        assertEquals(listOf("Meals"), vm.uiState.value.items) // nothing removed until confirmed
        vm.onEvent(OptionListEvent.DeleteConfirmed)
        assertEquals(emptyList(), vm.uiState.value.items)
    }

    @Test
    fun tabsShowTheirOwnLists() = runTest {
        config.addOption(OptionList.INCOME_CATEGORIES, "Salary")
        val vm = viewModel()
        assertEquals(emptyList(), vm.uiState.value.items)
        vm.onEvent(OptionListEvent.TabSelected(OptionList.INCOME_CATEGORIES))
        assertEquals(listOf("Salary"), vm.uiState.value.items)
    }

    @Test
    fun duplicatesAreRejectedWithAMessage() = runTest {
        config.addOption(OptionList.EXPENSE_CATEGORIES, "Food")
        val vm = viewModel()
        vm.onEvent(OptionListEvent.DraftChanged("food"))
        vm.onEvent(OptionListEvent.AddClicked)
        assertEquals("\"food\" is already in the list.", vm.uiState.value.error)
        assertEquals(listOf("Food"), vm.uiState.value.items)
    }
}
