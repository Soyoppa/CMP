package org.example.project.domain.transaction

sealed interface TransactionFormEffect {
    data class ShowSuccess(val message: String) : TransactionFormEffect
    data class ShowError(val message: String) : TransactionFormEffect
    data object FormCleared : TransactionFormEffect
}
