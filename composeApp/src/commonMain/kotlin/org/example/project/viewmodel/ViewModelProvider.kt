package org.example.project.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.project.model.OptionList

// ViewModels are scoped to the nearest LocalViewModelStoreOwner — the per-session store set up by
// SessionScope, or an overlay's own store (OverlayScope) — so they survive recomposition and
// configuration changes, and are cleared when the session or overlay ends.

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
fun createTransactionHistoryViewModel(): TransactionHistoryViewModel = viewModel { TransactionHistoryViewModel() }

@Composable
fun createSettingsViewModel(): SettingsViewModel = viewModel { SettingsViewModel() }

@Composable
fun createDeleteAccountViewModel(): DeleteAccountViewModel = viewModel { DeleteAccountViewModel() }

/** The editor for [lists] — tabs when there's more than one (expense categories + income sources). */
@Composable
fun createOptionListViewModel(lists: List<OptionList>): OptionListViewModel =
    viewModel(key = lists.joinToString { it.id }) { OptionListViewModel(lists) }

/** The welcome / sign-in flow; [startWithForm] opens straight on the email form (in-app sheet). */
@Composable
fun createAuthViewModel(startWithForm: Boolean = false, initialMode: AuthMode = AuthMode.SIGN_IN): AuthViewModel =
    viewModel(key = "auth-$startWithForm-$initialMode") { AuthViewModel(startWithForm = startWithForm, initialMode = initialMode) }
