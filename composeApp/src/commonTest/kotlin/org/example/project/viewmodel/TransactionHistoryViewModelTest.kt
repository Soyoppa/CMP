package org.example.project.viewmodel

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.example.project.data.ledger.AddTransactionResult
import org.example.project.data.ledger.LabelField
import org.example.project.data.ledger.LedgerDataSource
import org.example.project.data.ledger.LedgerEntry
import org.example.project.data.ledger.LedgerYear
import org.example.project.model.Transaction
import org.example.project.repository.LedgerRepository
import org.example.project.util.DateUtils
import org.example.project.util.UserFacingException

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionHistoryViewModelTest {

    private class FakeLedger(entries: List<LedgerEntry>) : LedgerDataSource {
        val stored = entries.toMutableList()
        var failDeletes = false
        override suspend fun readYear(year: Int) = LedgerYear(
            year = year,
            entries = stored.filter { it.date.startsWith("$year-") },
            earliestYear = stored.minOfOrNull { it.date.take(4).toInt() },
        )
        override suspend fun addTransaction(transaction: Transaction) = AddTransactionResult(true)
        override suspend fun deleteEntry(entry: LedgerEntry) {
            if (failDeletes) throw UserFacingException("The sheet changed since it was loaded. Refresh and try again.")
            stored.removeAll { it.id == entry.id }
        }
        override suspend fun relabel(field: LabelField, from: String, to: String) = 0
    }

    private val year = DateUtils.today().year
    private val lunch = LedgerEntry(id = "a", description = "Lunch", amount = 250.0, category = "Food & Dining", monthNumber = 3, date = "$year-03-02")
    private val salary = LedgerEntry(id = "b", description = "Salary", amount = 50000.0, category = "Salary", monthNumber = 3, date = "$year-03-15", isIncome = true)
    private val rent = LedgerEntry(id = "c", description = "Rent", amount = 9000.0, category = "Rent", monthNumber = 4, date = "$year-04-01")

    private lateinit var ledger: FakeLedger

    private fun viewModel() = TransactionHistoryViewModel(LedgerRepository(ledger))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ledger = FakeLedger(listOf(lunch, salary, rent))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun listsNewestFirstAndFilters() {
        val vm = viewModel()
        assertEquals(listOf("c", "b", "a"), vm.uiState.value.entries.map { it.id })

        vm.onEvent(TransactionHistoryEvent.FilterSelected(HistoryFilter.INCOME))
        assertEquals(listOf("b"), vm.uiState.value.visibleEntries.map { it.id })
        vm.onEvent(TransactionHistoryEvent.FilterSelected(HistoryFilter.EXPENSES))
        assertEquals(listOf("c", "a"), vm.uiState.value.visibleEntries.map { it.id })
    }

    @Test
    fun deleteNeedsConfirmationThenRemovesEntry() {
        val vm = viewModel()
        vm.onEvent(TransactionHistoryEvent.DeleteClicked(lunch))
        assertEquals(lunch, vm.uiState.value.pendingDelete)
        assertEquals(3, ledger.stored.size) // nothing deleted until confirmed

        vm.onEvent(TransactionHistoryEvent.DeleteConfirmed)
        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(listOf("c", "b"), vm.uiState.value.entries.map { it.id })
        assertEquals(listOf("b", "c"), ledger.stored.map { it.id })
    }

    @Test
    fun dismissKeepsEntry() {
        val vm = viewModel()
        vm.onEvent(TransactionHistoryEvent.DeleteClicked(rent))
        vm.onEvent(TransactionHistoryEvent.DeleteDismissed)
        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(3, ledger.stored.size)
    }

    @Test
    fun failedDeleteRestoresEntryInPlaceWithError() {
        val vm = viewModel()
        ledger.failDeletes = true
        vm.onEvent(TransactionHistoryEvent.DeleteClicked(salary))
        vm.onEvent(TransactionHistoryEvent.DeleteConfirmed)

        assertEquals(listOf("c", "b", "a"), vm.uiState.value.entries.map { it.id })
        assertNotNull(vm.uiState.value.deleteError)
        assertFalse(vm.uiState.value.isDeleting)

        vm.onEvent(TransactionHistoryEvent.DeleteErrorShown)
        assertNull(vm.uiState.value.deleteError)
    }
}
