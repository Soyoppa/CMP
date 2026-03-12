package org.example.project.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun createTransactionViewModel(): TransactionViewModel {
    return remember { TransactionViewModel() }
}
