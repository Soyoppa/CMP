package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.AppContainer
import org.example.project.data.ledger.LedgerSource
import org.example.project.domain.config.RenameOptionUseCase
import org.example.project.model.OptionList
import org.example.project.repository.ConfigRepository
import org.example.project.util.toUserMessage

data class OptionListUiState(
    /** The lists this editor covers (categories: expenses + income; payment modes: one). */
    val tabs: List<OptionList>,
    val selected: OptionList = tabs.first(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    /** The selected list's items, in the user's order. */
    val items: List<String> = emptyList(),
    val draft: String = "",
    /** The item being renamed, and the text typed for it. */
    val renaming: String? = null,
    val renameDraft: String = "",
    /** The item awaiting delete confirmation. */
    val pendingDelete: String? = null,
    val isSaving: Boolean = false,
    /** The last add / rename / delete failure; cleared by the next edit. */
    val error: String? = null,
    /** Renaming also relabels past transactions (not on the household sheet, which owns its labels). */
    val renamesPastTransactions: Boolean = true,
) {
    val canAdd: Boolean get() = draft.isNotBlank() && !isSaving
}

sealed interface OptionListEvent {
    data class TabSelected(val list: OptionList) : OptionListEvent
    data class DraftChanged(val text: String) : OptionListEvent
    data object AddClicked : OptionListEvent
    data class RenameClicked(val item: String) : OptionListEvent
    data class RenameDraftChanged(val text: String) : OptionListEvent
    data object RenameConfirmed : OptionListEvent
    data object RenameDismissed : OptionListEvent
    data class DeleteClicked(val item: String) : OptionListEvent
    data object DeleteConfirmed : OptionListEvent
    data object DeleteDismissed : OptionListEvent
    data object RetryClicked : OptionListEvent
}

/**
 * Backs the list editors (categories and payment modes): add, rename and delete, each saved the
 * moment it's confirmed — there's no separate save step to forget. Lists start empty; nothing is
 * pre-filled.
 *
 * Items come straight from the session's [ConfigRepository], so the Add form's pickers and the
 * budget sheet see every change immediately.
 */
class OptionListViewModel(
    tabs: List<OptionList>,
    private val config: ConfigRepository = AppContainer.session().config,
    private val renameOption: RenameOptionUseCase =
        AppContainer.session().let { RenameOptionUseCase(it.config, it.ledger) },
    renamesPastTransactions: Boolean = AppContainer.session().user.ledgerSource != LedgerSource.SHEETS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OptionListUiState(tabs = tabs, renamesPastTransactions = renamesPastTransactions)
    )
    val uiState: StateFlow<OptionListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { config.ensureLoaded() }
        viewModelScope.launch {
            config.state.collect { configState ->
                _uiState.update {
                    it.copy(
                        isLoading = configState.isLoading,
                        loadError = configState.error,
                        items = configState.config.items(it.selected),
                    )
                }
            }
        }
    }

    fun onEvent(event: OptionListEvent) {
        when (event) {
            is OptionListEvent.TabSelected -> _uiState.update {
                it.copy(selected = event.list, items = config.config.items(event.list), draft = "", error = null)
            }
            is OptionListEvent.DraftChanged ->
                _uiState.update { it.copy(draft = event.text.take(ConfigRepository.MAX_NAME_LENGTH), error = null) }
            OptionListEvent.AddClicked -> add()
            is OptionListEvent.RenameClicked ->
                _uiState.update { it.copy(renaming = event.item, renameDraft = event.item, error = null) }
            is OptionListEvent.RenameDraftChanged ->
                _uiState.update { it.copy(renameDraft = event.text.take(ConfigRepository.MAX_NAME_LENGTH), error = null) }
            OptionListEvent.RenameConfirmed -> rename()
            OptionListEvent.RenameDismissed -> _uiState.update { it.copy(renaming = null, renameDraft = "") }
            is OptionListEvent.DeleteClicked -> _uiState.update { it.copy(pendingDelete = event.item, error = null) }
            OptionListEvent.DeleteConfirmed -> delete()
            OptionListEvent.DeleteDismissed -> _uiState.update { it.copy(pendingDelete = null) }
            OptionListEvent.RetryClicked -> viewModelScope.launch { config.refresh() }
        }
    }

    private fun add() {
        val state = _uiState.value
        if (!state.canAdd) return
        save(onSuccess = { it.copy(draft = "") }) { config.addOption(state.selected, state.draft) }
    }

    private fun rename() {
        val state = _uiState.value
        val from = state.renaming ?: return
        save(onSuccess = { it.copy(renaming = null, renameDraft = "") }) {
            renameOption(state.selected, from, state.renameDraft)
        }
    }

    private fun delete() {
        val state = _uiState.value
        val item = state.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        save(onSuccess = { it }) { config.deleteOption(state.selected, item) }
    }

    private fun save(onSuccess: (OptionListUiState) -> OptionListUiState, action: suspend () -> Result<*>) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = action()
            _uiState.update { state ->
                if (result.isSuccess) onSuccess(state.copy(isSaving = false))
                else state.copy(isSaving = false, error = result.exceptionOrNull()?.toUserMessage("Couldn't save that change."))
            }
        }
    }
}
