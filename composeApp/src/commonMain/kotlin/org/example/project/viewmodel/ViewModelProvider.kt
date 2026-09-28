package org.example.project.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.config.LedgerProfile
import org.example.project.data.settings.UserSettingsStore
import org.example.project.repository.UserListRepository

// ViewModels are scoped to the nearest LocalViewModelStoreOwner — the per-session store set up by
// SessionScope — so they survive recomposition and configuration changes, and are cleared on
// sign-out on every platform.

@Composable
fun createTransactionFormViewModel(): TransactionFormViewModel = viewModel { TransactionFormViewModel() }

@Composable
fun createChatViewModel(): ChatViewModel = viewModel { ChatViewModel() }

@Composable
fun createSummaryViewModel(): SummaryViewModel = viewModel { SummaryViewModel() }

@Composable
fun createBudgetViewModel(): BudgetViewModel = viewModel { BudgetViewModel() }

@Composable
fun createPaymentStatusViewModel(): PaymentStatusViewModel = viewModel { PaymentStatusViewModel() }

@Composable
fun createCategoryListViewModel(): ManagedListViewModel = viewModel(key = UserSettingsStore.CATEGORIES_LIST) {
    val profile = LedgerProfile.current()
    ManagedListViewModel(
        repository = UserListRepository(UserSettingsStore.CATEGORIES_LIST),
        defaults = profile.categoryOptions,
        itemNoun = profile.categoryLabel.lowercase(),
    )
}

@Composable
fun createPaymentModeListViewModel(): ManagedListViewModel = viewModel(key = UserSettingsStore.PAYMENT_MODES_LIST) {
    ManagedListViewModel(
        repository = UserListRepository(UserSettingsStore.PAYMENT_MODES_LIST),
        defaults = LedgerProfile.current().paymentModeOptions,
        itemNoun = "payment mode",
    )
}
