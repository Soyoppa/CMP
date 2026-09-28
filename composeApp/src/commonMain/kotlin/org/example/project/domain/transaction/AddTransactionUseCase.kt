package org.example.project.domain.transaction

import org.example.project.data.AddTransactionResult
import org.example.project.model.Transaction
import org.example.project.repository.TransactionRepository

class AddTransactionUseCase(
    private val repository: TransactionRepository = TransactionRepository()
) {
    suspend operator fun invoke(transaction: Transaction): AddTransactionResult =
        repository.addTransaction(transaction)
}
