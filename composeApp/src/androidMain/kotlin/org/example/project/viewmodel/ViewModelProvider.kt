package org.example.project.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
actual fun createTransactionViewModel(): TransactionViewModel {
    return viewModel()
}
