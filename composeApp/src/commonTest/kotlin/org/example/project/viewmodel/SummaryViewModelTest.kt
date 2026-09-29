package org.example.project.viewmodel

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.project.auth.AppUser
import org.example.project.auth.Session
import org.example.project.data.ledger.AddTransactionResult
import org.example.project.data.ledger.LedgerDataSource
import org.example.project.data.ledger.LedgerEntry
import org.example.project.model.Transaction
import org.example.project.repository.LedgerRepository
import org.example.project.util.UserFacingException

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {

    private object FailingLedger : LedgerDataSource {
        override suspend fun getEntries(): List<LedgerEntry> =
            throw UserFacingException("The shared sheet returned an unexpected response. Please try again.")
        override suspend fun addTransaction(transaction: Transaction) = AddTransactionResult(false)
        override suspend fun deleteEntry(entry: LedgerEntry) = Unit
    }

    @AfterTest
    fun tearDown() {
        Session.setSignedOut()
        Dispatchers.resetMain()
    }

    /** Regression: a failing ledger read used to crash the app (async child failed the launch). */
    @Test
    fun ledgerFailureBecomesErrorStateInsteadOfCrashing() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        Session.setAuthenticated(AppUser(email = "a@b.co", isGuest = false, uid = "u1"))

        val vm = SummaryViewModel(repository = LedgerRepository(cloud = FailingLedger, sheets = { FailingLedger }))

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(
            "The shared sheet returned an unexpected response. Please try again.",
            vm.uiState.value.error,
        )
    }
}
