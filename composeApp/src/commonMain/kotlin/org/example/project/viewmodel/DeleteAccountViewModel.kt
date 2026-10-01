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

data class DeleteAccountUiState(
    val password: String = "",
    val isDeleting: Boolean = false,
    val error: String? = null,
)

sealed interface DeleteAccountEvent {
    data class PasswordChanged(val password: String) : DeleteAccountEvent
    data object ConfirmClicked : DeleteAccountEvent
}

/**
 * Backs the delete-account confirmation. On success [SessionRepository] ends the session, which
 * swaps the whole signed-in scope (and this ViewModel) away — so there's no success state here.
 */
class DeleteAccountViewModel(
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    fun onEvent(event: DeleteAccountEvent) {
        when (event) {
            is DeleteAccountEvent.PasswordChanged ->
                _uiState.update { it.copy(password = event.password.take(128), error = null) }
            DeleteAccountEvent.ConfirmClicked -> deleteAccount()
        }
    }

    private fun deleteAccount() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            sessionRepository.deleteAccount(password = _uiState.value.password).onFailure { e ->
                _uiState.update {
                    it.copy(isDeleting = false, error = e.toUserMessage("Couldn't delete your account. Please try again."))
                }
            }
        }
    }
}
