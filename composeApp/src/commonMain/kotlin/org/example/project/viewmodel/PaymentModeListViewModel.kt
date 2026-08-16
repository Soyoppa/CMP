package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.model.PaymentMode
import org.example.project.repository.UserListRepository

/**
 * Backs the "Manage payment modes" editor. Same shape as [CategoryListViewModel] — loads the
 * signed-in user's saved list (falling back to [PaymentMode]'s built-in entries on first use)
 * and persists on every add/delete. Shares [ManagedListUiState] since the two editors are
 * identical in structure, just different lists.
 */
class PaymentModeListViewModel(
    private val repository: UserListRepository = UserListRepository(listId = "paymentModes"),
) : ViewModel() {

    private val defaults: List<String> get() = PaymentMode.entries.map { it.displayName }

    private val _uiState = MutableStateFlow(ManagedListUiState())
    val uiState: StateFlow<ManagedListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val items = repository.getItems(defaults)
            _uiState.update { it.copy(isLoading = false, items = items) }
        }
    }

    fun updateDraft(text: String) {
        _uiState.update { it.copy(draftInput = text) }
    }

    fun addItem() {
        val name = _uiState.value.draftInput.trim()
        if (name.isEmpty()) return
        val current = _uiState.value.items
        if (current.any { it.equals(name, ignoreCase = true) }) {
            _uiState.update { it.copy(draftInput = "", error = "\"$name\" is already in the list.") }
            return
        }
        persist(current + name, clearDraft = true)
    }

    fun deleteItem(name: String) {
        val current = _uiState.value.items
        // Keep at least one option — an empty list would leave the Add Transaction picker empty.
        if (current.size <= 1) {
            _uiState.update { it.copy(error = "At least one payment mode is required.") }
            return
        }
        persist(current - name, clearDraft = false)
    }

    private fun persist(items: List<String>, clearDraft: Boolean) {
        val previous = _uiState.value.items
        _uiState.update {
            it.copy(
                items = items,
                draftInput = if (clearDraft) "" else it.draftInput,
                isSaving = true,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = repository.saveItems(items)
            _uiState.update {
                if (result.isSuccess) it.copy(isSaving = false)
                else it.copy(
                    isSaving = false,
                    items = previous,
                    error = result.exceptionOrNull()?.message ?: "Couldn't save changes.",
                )
            }
        }
    }
}
