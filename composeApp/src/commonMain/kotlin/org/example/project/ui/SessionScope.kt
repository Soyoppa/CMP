package org.example.project.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Gives [content] its own [ViewModelStore] for one signed-in session. When [sessionKey] changes
 * (sign-in as someone else) or the scope leaves composition (sign-out), every ViewModel created
 * inside is cleared — so one account's data can never be shown to the next.
 */
@Composable
fun SessionScope(sessionKey: String, content: @Composable () -> Unit) {
    ScopedViewModelStore(key = sessionKey, content = content)
}

/**
 * Gives a full-screen overlay (budgets, list editors, Transactions, …) its own ViewModels for as
 * long as it's shown, so every opening starts from fresh data instead of a stale earlier instance.
 */
@Composable
fun OverlayScope(content: @Composable () -> Unit) {
    ScopedViewModelStore(key = "overlay", content = content)
}

@Composable
private fun ScopedViewModelStore(key: String, content: @Composable () -> Unit) {
    val owner = remember(key) { ScopedViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}

private class ScopedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/** System back (Android back button / gesture) while [enabled]; a no-op on targets without one. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
