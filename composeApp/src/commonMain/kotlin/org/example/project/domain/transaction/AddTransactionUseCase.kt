package org.example.project.domain.transaction

import org.example.project.data.AddTransactionResult
import org.example.project.model.Transaction
import org.example.project.repository.TransactionRepository

class AddTransactionUseCase(
    private val repository: TransactionRepository = TransactionRepository()
) {
    suspend operator fun invoke(transaction: Transaction): Result<Unit> = runCatching {
        val success = repository.addTransaction(transaction)
        if (!success) {
            throw Exception("Failed to save transaction. Please try again.")
        }
    }

    /**
     * Detailed version — returns rich diagnostic info from the repository.
     */
    suspend fun invokeDetailed(transaction: Transaction): AddTransactionResult {
        return repository.addTransactionDetailed(transaction)
    }
}
