package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.config.SchemaFeatures
import org.example.project.repository.UserListRepository

data class ManagedListUiState(
    val isLoading: Boolean = true,
    val items: List<String> = emptyList(),
    val draftInput: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * Backs the "Manage categories" editor (tracker_1's expense categories, or tracker_2's
 * care-of list — whichever [SchemaFeatures.categoryLabel] names). Loads the signed-in user's
 * saved list, falling back to the schema's built-in defaults on first use, and persists on every
 * add/delete — unlike [BudgetViewModel] there's no numeric field to commit, just list membership,
 * so there's no separate save step to forget.
 */
class CategoryListViewModel(
    private val repository: UserListRepository = UserListRepository(listId = "categories"),
) : ViewModel() {

    private val defaults: List<String> get() = SchemaFeatures.current().categoryOptions

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
            _uiState.update { it.copy(error = "At least one category is required.") }
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
