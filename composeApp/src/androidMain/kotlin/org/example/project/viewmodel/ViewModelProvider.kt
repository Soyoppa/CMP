package org.example.project.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
actual fun createTransactionViewModel(): TransactionViewModel {
    return viewModel()
}

@Composable
actual fun createAiViewModel(): AiViewModel {
    return viewModel()
}

@Composable
actual fun createSummaryViewModel(): SummaryViewModel {
    return viewModel()
}

@Composable
actual fun createBudgetViewModel(): BudgetViewModel {
    return viewModel()
}

@Composable
actual fun createCategoryListViewModel(): CategoryListViewModel {
    return viewModel()
}

@Composable
actual fun createPaymentModeListViewModel(): PaymentModeListViewModel {
    return viewModel()
}
