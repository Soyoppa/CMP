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
    val owner = remember(sessionKey) { SessionViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}

private class SessionViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/** System back (Android back button / gesture) while [enabled]; a no-op on targets without one. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
