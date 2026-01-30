package org.example.project.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.model.Transaction
import org.example.project.repository.TransactionRepository

class TransactionViewModel : ViewModel() {
    private val repository = TransactionRepository()
    
    var transactions by mutableStateOf<List<Transaction>>(emptyList())
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    fun loadTransactions() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                transactions = repository.getAllTransactions()
            } catch (e: Exception) {
                errorMessage = "Failed to load transactions: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun addTransaction(transaction: Transaction, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val success = repository.addTransaction(transaction)
                if (success) {
                    // Refresh the list
                    loadTransactions()
                    onSuccess()
                } else {
                    errorMessage = "Failed to add transaction"
                }
            } catch (e: Exception) {
                errorMessage = "Failed to add transaction: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}