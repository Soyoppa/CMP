package org.example.project.data.ledger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.example.project.data.device.InMemoryDeviceStore
import org.example.project.model.Transaction
import org.example.project.util.UserFacingException

class DeviceLedgerDataSourceTest {

    private val store = InMemoryDeviceStore()
    private val ledger = DeviceLedgerDataSource(store)

    private fun expense(date: String, description: String, amount: Double, category: String = "Food", mode: String = "Cash") =
        Transaction(date = date, description = description, outflow = amount, category = category, modeOfPayment = mode)

    @Test
    fun emptyPhoneReadsAsAnEmptyLedger() = runTest {
        val year = ledger.readYear(2026)
        assertTrue(year.entries.isEmpty())
        assertNull(year.earliestYear)
    }

    @Test
    fun rowsAreStoredOneDocumentPerYear() = runTest {
        ledger.addTransaction(expense("3/2/2026", "Lunch", 250.0))
        ledger.addTransaction(expense("12/30/2025", "Gift", 900.0))
        ledger.addTransaction(Transaction(date = "3/15/2026", description = "Salary", inflow = 50_000.0, category = "Salary"))

        assertEquals(setOf("ledger/2025", "ledger/2026"), store.keys())
        val year = ledger.readYear(2026)
        assertEquals(listOf("Lunch", "Salary"), year.entries.map { it.description })
        assertEquals("2026-03-02", year.entries.first().date)
        assertEquals(listOf("Lunch"), year.expenses.map { it.description })
        assertEquals(2025, year.earliestYear)
    }

    @Test
    fun deleteRemovesOnlyThatRow() = runTest {
        ledger.addTransaction(expense("3/2/2026", "Lunch", 250.0))
        ledger.addTransaction(expense("3/3/2026", "Dinner", 400.0))
        val lunch = ledger.readYear(2026).entries.first { it.description == "Lunch" }

        ledger.deleteEntry(lunch)

        assertEquals(listOf("Dinner"), ledger.readYear(2026).entries.map { it.description })
        assertFailsWith<UserFacingException> { ledger.deleteEntry(lunch) }
    }

    @Test
    fun relabelRenamesEveryYear() = runTest {
        ledger.addTransaction(expense("3/2/2026", "Lunch", 250.0, category = "Food"))
        ledger.addTransaction(expense("1/2/2025", "Snack", 50.0, category = "Food", mode = "GCash"))
        ledger.addTransaction(expense("3/3/2026", "Rent", 9000.0, category = "Rent"))

        assertEquals(2, ledger.relabel(LabelField.CATEGORY, from = "Food", to = "Meals"))
        assertEquals(1, ledger.relabel(LabelField.PAYMENT_MODE, from = "GCash", to = "E-wallet"))

        assertEquals(listOf("Meals", "Rent"), ledger.readYear(2026).entries.map { it.category })
        assertEquals("E-wallet", ledger.readYear(2025).entries.single().modeOfPayment)
    }

    @Test
    fun unreadableDataIsAnErrorNotAnEmptyYear() = runTest {
        store.write("ledger/2026", "{not json")
        assertFailsWith<UserFacingException> { ledger.readYear(2026) }
        // …and a save must not silently overwrite it.
        assertFailsWith<UserFacingException> { ledger.addTransaction(expense("3/2/2026", "Lunch", 250.0)) }
        assertEquals("{not json", store.read("ledger/2026"))
    }

    @Test
    fun clearRemovesTheLedgerOnly() = runTest {
        ledger.addTransaction(expense("3/2/2026", "Lunch", 250.0))
        store.write("config/budgets", "{}")
        ledger.clear()
        assertEquals(setOf("config/budgets"), store.keys())
    }
}
