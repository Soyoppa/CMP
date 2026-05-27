package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.auth.AuthRepository
import org.example.project.auth.createAuthRepository

/**
 * Drives the login/sign-up form. On success the [AuthRepository] flips [org.example.project.auth.Session],
 * which swaps the gate away from [org.example.project.ui.LoginScreen] — so there are no navigation
 * effects here; the gate reacts to Session state.
 */
class AuthViewModel(
    private val repository: AuthRepository = createAuthRepository(),
) : ViewModel() {

    enum class Mode { SIGN_IN, SIGN_UP }

    data class State(
        val email: String = "",
        val password: String = "",
        val mode: Mode = Mode.SIGN_IN,
        val isSubmitting: Boolean = false,
        val error: String? = null,
    ) {
        val canSubmit: Boolean get() = email.isNotBlank() && password.length >= 6 && !isSubmitting
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun toggleMode() = _state.update {
        it.copy(mode = if (it.mode == Mode.SIGN_IN) Mode.SIGN_UP else Mode.SIGN_IN, error = null)
    }

    fun setMode(mode: Mode) = _state.update { it.copy(mode = mode, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = when (current.mode) {
                Mode.SIGN_IN -> repository.signIn(current.email, current.password)
                Mode.SIGN_UP -> repository.signUp(current.email, current.password)
            }
            result.onFailure { e ->
                _state.update { it.copy(isSubmitting = false, error = e.message ?: "Authentication failed.") }
            }
            // On success the gate reacts to Session; nothing to do here.
        }
    }

    fun continueAsGuest() {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            repository.continueAsGuest().onFailure { e ->
                _state.update { it.copy(isSubmitting = false, error = e.message ?: "Couldn't start guest mode.") }
            }
        }
    }
}
