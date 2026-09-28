package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.repository.LedgerRepository
import org.example.project.util.FormatUtils
import org.example.project.util.toUserMessage

enum class DiagnosticKind { IDLE, SUCCESS, WARNING, ERROR }

data class DiagnosticResult(val kind: DiagnosticKind, val message: String) {
    companion object {
        val Idle = DiagnosticResult(DiagnosticKind.IDLE, "Not tested yet")
    }
}

data class SettingsUiState(
    val isTestingRead: Boolean = false,
    val readResult: DiagnosticResult = DiagnosticResult.Idle,
)

sealed interface SettingsEvent {
    data object TestReadClicked : SettingsEvent
}

/** Settings' stateful bits: currently the ledger read diagnostic (signed-in users only). */
class SettingsViewModel(
    private val ledgerRepository: LedgerRepository = LedgerRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.TestReadClicked -> testRead()
        }
    }

    private fun testRead() {
        if (_uiState.value.isTestingRead) return
        _uiState.update { it.copy(isTestingRead = true) }
        viewModelScope.launch {
            val result = try {
                val recent = ledgerRepository.getEntries().takeLast(3).reversed()
                if (recent.isEmpty()) {
                    DiagnosticResult(DiagnosticKind.WARNING, "Read succeeded but your ledger has no transactions yet.")
                } else {
                    val lines = recent.joinToString("\n") { entry ->
                        val sign = if (entry.isIncome) "+" else "-"
                        "• ${entry.description} — ${sign}PHP ${FormatUtils.formatPeso(entry.amount)}"
                    }
                    DiagnosticResult(DiagnosticKind.SUCCESS, "Last ${recent.size} transactions:\n$lines")
                }
            } catch (e: Exception) {
                DiagnosticResult(DiagnosticKind.ERROR, e.toUserMessage("Read failed. Check your connection and try again."))
            }
            _uiState.update { it.copy(isTestingRead = false, readResult = result) }
        }
    }
}
