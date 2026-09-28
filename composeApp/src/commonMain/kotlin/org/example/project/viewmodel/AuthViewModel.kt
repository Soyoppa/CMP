package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.AppContainer
import org.example.project.auth.AuthRepository
import org.example.project.util.toUserMessage

enum class AuthMode { SIGN_IN, SIGN_UP }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val mode: AuthMode = AuthMode.SIGN_IN,
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.length >= 6 && !isSubmitting
}

sealed interface AuthEvent {
    data class EmailChanged(val email: String) : AuthEvent
    data class PasswordChanged(val password: String) : AuthEvent
    data object ModeToggled : AuthEvent
    data object SubmitClicked : AuthEvent
    data object GuestClicked : AuthEvent
    /** A guest tapped "Create account": show sign-up once the guest session ends. */
    data object SignUpRequested : AuthEvent
    data object SignOutClicked : AuthEvent
}

/**
 * Drives the login/sign-up form. On success [AuthRepository] updates [org.example.project.auth.Session],
 * which swaps the gate away from [org.example.project.ui.LoginScreen] — so there are no navigation
 * effects here; the gate reacts to Session state.
 */
class AuthViewModel(
    private val repository: AuthRepository = AppContainer.authRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEvent(event: AuthEvent) {
        when (event) {
            // RFC 5321 max email length; a generous password cap bounds input size.
            is AuthEvent.EmailChanged -> _uiState.update { it.copy(email = event.email.take(254), error = null) }
            is AuthEvent.PasswordChanged -> _uiState.update { it.copy(password = event.password.take(128), error = null) }
            AuthEvent.ModeToggled -> _uiState.update {
                it.copy(mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN, error = null)
            }
            AuthEvent.SubmitClicked -> submit()
            AuthEvent.GuestClicked -> continueAsGuest()
            AuthEvent.SignUpRequested -> {
                _uiState.update { it.copy(mode = AuthMode.SIGN_UP, error = null) }
                signOut()
            }
            AuthEvent.SignOutClicked -> signOut()
        }
    }

    private fun submit() {
        val current = _uiState.value
        if (!current.canSubmit) return
        runAuth("Authentication failed.") {
            when (current.mode) {
                AuthMode.SIGN_IN -> repository.signIn(current.email, current.password)
                AuthMode.SIGN_UP -> repository.signUp(current.email, current.password)
            }
        }
    }

    private fun continueAsGuest() {
        if (_uiState.value.isSubmitting) return
        runAuth("Couldn't start guest mode.") { repository.continueAsGuest() }
    }

    private fun signOut() {
        // Never keep a typed password around once a session ends.
        _uiState.update { it.copy(password = "", isSubmitting = false) }
        viewModelScope.launch { repository.signOut() }
    }

    private fun runAuth(fallbackError: String, action: suspend () -> Result<Unit>) {
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            action()
                .onSuccess { _uiState.update { it.copy(isSubmitting = false, password = "") } }
                .onFailure { e -> _uiState.update { it.copy(isSubmitting = false, error = e.toUserMessage(fallbackError)) } }
        }
    }
}
