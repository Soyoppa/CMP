package org.example.project.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.example.project.data.config.DeviceConfigStore
import org.example.project.data.device.DeviceData
import org.example.project.data.device.InMemoryDeviceStore
import org.example.project.data.ledger.LedgerRecord
import org.example.project.model.BudgetPlan
import org.example.project.model.OptionList
import org.example.project.model.Transaction

class DeviceDataUploaderTest {

    private val phone = DeviceData(InMemoryDeviceStore())
    // The "account" side, backed by an in-memory store too.
    private val cloudConfig = DeviceConfigStore(InMemoryDeviceStore())
    private val cloudRows = linkedMapOf<String, LedgerRecord>()
    private var failImports = false

    private val uploader = DeviceDataUploader(
        device = phone,
        cloudConfig = { cloudConfig },
        cloudLedger = {
            RecordImporter { record ->
                if (failImports) error("offline")
                cloudRows[record.id] = record
            }
        },
    )

    private suspend fun seedPhone() {
        phone.config.saveList(OptionList.EXPENSE_CATEGORIES, listOf("Food", "Pets"))
        phone.config.saveBudget("2026-09-2", BudgetPlan(total = 8_000.0))
        phone.config.saveBudget("2026-10-1", BudgetPlan(total = 9_000.0))
        phone.ledger.addTransaction(Transaction(date = "9/20/2026", description = "Lunch", outflow = 250.0, category = "Food"))
        phone.ledger.addTransaction(Transaction(date = "9/21/2026", description = "Kibble", outflow = 900.0, category = "Pets"))
    }

    @Test
    fun movesEverythingThenClearsThePhone() = runTest {
        seedPhone()
        cloudConfig.saveList(OptionList.EXPENSE_CATEGORIES, listOf("Rent", "food"))
        cloudConfig.saveBudget("2026-09-2", BudgetPlan(total = 20_000.0))

        assertEquals(2, uploader.upload("u1"))

        val account = cloudConfig.load()
        // The account's order first, then what only the phone had (case-insensitive match).
        assertEquals(listOf("Rent", "food", "Pets"), account.expenseCategories)
        // An existing budget wins; a cut-off the account never budgeted is copied.
        assertEquals(20_000.0, account.budgets.getValue("2026-09-2").total)
        assertEquals(9_000.0, account.budgets.getValue("2026-10-1").total)
        assertEquals(listOf("Lunch", "Kibble"), cloudRows.values.map { it.description })
        assertFalse(phone.hasData())
    }

    @Test
    fun aFailedUploadKeepsThePhoneDataAndRetriesWithoutDuplicates() = runTest {
        seedPhone()
        failImports = true
        assertTrue(runCatching { uploader.upload("u1") }.isFailure)
        assertTrue(phone.hasData())

        failImports = false
        uploader.upload("u1")
        uploader.upload("u1") // nothing left: a no-op
        assertEquals(2, cloudRows.size)
    }
}
