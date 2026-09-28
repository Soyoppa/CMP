package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.repository.UserListRepository
import org.example.project.util.toUserMessage

data class ManagedListUiState(
    val isLoading: Boolean = true,
    val items: List<String> = emptyList(),
    val draftInput: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

sealed interface ManagedListEvent {
    data class DraftChanged(val text: String) : ManagedListEvent
    data object AddClicked : ManagedListEvent
    data class DeleteClicked(val item: String) : ManagedListEvent
}

/**
 * Backs a "Manage …" list editor (categories or payment modes). Loads the user's saved list,
 * falling back to [defaults] on first use, and persists on every add/delete — list membership
 * has no separate save step to forget. Failed saves roll the list back.
 *
 * @param itemNoun singular noun used in messages, e.g. "category".
 */
class ManagedListViewModel(
    private val repository: UserListRepository,
    private val defaults: List<String>,
    private val itemNoun: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManagedListUiState())
    val uiState: StateFlow<ManagedListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val items = repository.getItems(defaults)
            _uiState.update { it.copy(isLoading = false, items = items) }
        }
    }

    fun onEvent(event: ManagedListEvent) {
        when (event) {
            is ManagedListEvent.DraftChanged -> _uiState.update { it.copy(draftInput = event.text.take(MAX_ITEM_LENGTH)) }
            ManagedListEvent.AddClicked -> addItem()
            is ManagedListEvent.DeleteClicked -> deleteItem(event.item)
        }
    }

    private fun addItem() {
        val name = _uiState.value.draftInput.trim()
        if (name.isEmpty()) return
        val current = _uiState.value.items
        if (current.any { it.equals(name, ignoreCase = true) }) {
            _uiState.update { it.copy(draftInput = "", error = "\"$name\" is already in the list.") }
            return
        }
        persist(current + name, clearDraft = true)
    }

    private fun deleteItem(name: String) {
        val current = _uiState.value.items
        // Keep at least one option — an empty list would leave the Add Transaction picker empty.
        if (current.size <= 1) {
            _uiState.update { it.copy(error = "At least one $itemNoun is required.") }
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
                    error = result.exceptionOrNull()?.toUserMessage("Couldn't save changes.") ?: "Couldn't save changes.",
                )
            }
        }
    }

    private companion object {
        /** Mirrors the transaction field cap in firestore.rules. */
        const val MAX_ITEM_LENGTH = 60
    }
}
