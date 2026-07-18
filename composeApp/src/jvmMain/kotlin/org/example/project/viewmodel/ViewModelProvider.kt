package org.example.project.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun createTransactionViewModel(): TransactionViewModel {
    return remember { TransactionViewModel() }
}

@Composable
actual fun createAiViewModel(): AiViewModel {
    return remember { AiViewModel() }
}

@Composable
actual fun createSummaryViewModel(): SummaryViewModel {
    return remember { SummaryViewModel() }
}

@Composable
actual fun createBudgetViewModel(): BudgetViewModel {
    return remember { BudgetViewModel() }
}
