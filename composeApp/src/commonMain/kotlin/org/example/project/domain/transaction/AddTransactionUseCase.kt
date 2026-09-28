package org.example.project.domain.transaction

import org.example.project.data.ledger.AddTransactionResult
import org.example.project.model.Transaction
import org.example.project.repository.LedgerRepository

class AddTransactionUseCase(
    private val repository: LedgerRepository = LedgerRepository()
) {
    suspend operator fun invoke(transaction: Transaction): AddTransactionResult =
        repository.addTransaction(transaction)
}
