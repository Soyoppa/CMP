package org.example.project.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

// ViewModels are scoped to the nearest LocalViewModelStoreOwner — the per-session store set up by
// SessionScope — so they survive recomposition and configuration changes, and are cleared on
// sign-out on every platform.

@Composable
fun createTransactionViewModel(): TransactionViewModel = viewModel { TransactionViewModel() }

@Composable
fun createAiViewModel(): AiViewModel = viewModel { AiViewModel() }

@Composable
fun createSummaryViewModel(): SummaryViewModel = viewModel { SummaryViewModel() }

@Composable
fun createBudgetViewModel(): BudgetViewModel = viewModel { BudgetViewModel() }

@Composable
fun createCategoryListViewModel(): CategoryListViewModel = viewModel { CategoryListViewModel() }

@Composable
fun createPaymentModeListViewModel(): PaymentModeListViewModel = viewModel { PaymentModeListViewModel() }

@Composable
fun createPaymentStatusViewModel(): PaymentStatusViewModel = viewModel { PaymentStatusViewModel() }
