package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.AppContainer
import org.example.project.auth.SessionRepository
import org.example.project.util.toUserMessage

enum class AuthMode { SIGN_IN, SIGN_UP }

data class AuthUiState(
    /** Mobile can be used without an account; the web can't. */
    val deviceModeAvailable: Boolean = false,
    /** Mobile welcome step: the choice screen before the email form. Always false on the web. */
    val showChoice: Boolean = deviceModeAvailable,
    /** This phone holds data that signing in will move into the account. */
    val hasDeviceData: Boolean = false,
    val email: String = "",
    val password: String = "",
    val mode: AuthMode = AuthMode.SIGN_IN,
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.length >= 6 && !isSubmitting
}

sealed interface AuthEvent {
    data object UseWithoutAccountClicked : AuthEvent
    /** From the welcome choice (or an in-app "Sign in" / "Create account" button). */
    data class FormRequested(val mode: AuthMode) : AuthEvent
    data object BackToChoiceClicked : AuthEvent
    data class EmailChanged(val email: String) : AuthEvent
    data class PasswordChanged(val password: String) : AuthEvent
    data object ModeToggled : AuthEvent
    data object SubmitClicked : AuthEvent
}

/**
 * Drives the welcome screen and the email sign-in / sign-up form (also shown as a sheet when
 * someone using the app without an account adds one). On success [SessionRepository] starts a new
 * session and the App gate swaps screens — so there are no navigation effects here.
 *
 * @param startWithForm skip the welcome choice and open the form in [initialMode].
 */
class AuthViewModel(
    private val repository: SessionRepository = AppContainer.sessionRepository,
    startWithForm: Boolean = false,
    initialMode: AuthMode = AuthMode.SIGN_IN,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            deviceModeAvailable = repository.deviceModeAvailable,
            showChoice = repository.deviceModeAvailable && !startWithForm,
            mode = initialMode,
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val hasData = repository.hasDeviceData()
            _uiState.update { it.copy(hasDeviceData = hasData) }
        }
    }

    fun onEvent(event: AuthEvent) {
        when (event) {
            AuthEvent.UseWithoutAccountClicked -> runAuth("Couldn't start.") { repository.useWithoutAccount() }
            is AuthEvent.FormRequested -> _uiState.update { it.copy(showChoice = false, mode = event.mode, error = null) }
            AuthEvent.BackToChoiceClicked ->
                _uiState.update { it.copy(showChoice = it.deviceModeAvailable, password = "", error = null) }
            // RFC 5321 max email length; a generous password cap bounds input size.
            is AuthEvent.EmailChanged -> _uiState.update { it.copy(email = event.email.take(254), error = null) }
            is AuthEvent.PasswordChanged -> _uiState.update { it.copy(password = event.password.take(128), error = null) }
            AuthEvent.ModeToggled -> _uiState.update {
                it.copy(mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN, error = null)
            }
            AuthEvent.SubmitClicked -> submit()
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

    private fun runAuth(fallbackError: String, action: suspend () -> Result<Unit>) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            action()
                // Never keep a typed password around once it has been used.
                .onSuccess { _uiState.update { it.copy(isSubmitting = false, password = "") } }
                .onFailure { e -> _uiState.update { it.copy(isSubmitting = false, error = e.toUserMessage(fallbackError)) } }
        }
    }
}
