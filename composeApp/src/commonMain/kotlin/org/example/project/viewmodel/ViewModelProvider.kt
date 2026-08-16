package org.example.project.viewmodel

import androidx.compose.runtime.Composable

@Composable
expect fun createTransactionViewModel(): TransactionViewModel

@Composable
expect fun createAiViewModel(): AiViewModel

@Composable
expect fun createSummaryViewModel(): SummaryViewModel

@Composable
expect fun createBudgetViewModel(): BudgetViewModel

@Composable
expect fun createCategoryListViewModel(): CategoryListViewModel

@Composable
expect fun createPaymentModeListViewModel(): PaymentModeListViewModel
